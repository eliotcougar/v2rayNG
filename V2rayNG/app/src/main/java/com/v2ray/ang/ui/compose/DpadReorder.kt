package com.v2ray.ang.ui.compose

import android.view.ViewConfiguration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Do not replace this quiet-period check with repeatCount-only detection.
 * Android TV emulators and some keyboard/input translation paths can report a held
 * Enter/DPAD_CENTER as discrete ACTION_UP/ACTION_DOWN pairs whose repeatCount is zero.
 * Without a quiet period, one hold can start movement, drop the item, and then click it.
 * Keep the proven 160 ms floor, while allowing slower configured key-repeat delays to
 * extend it.
 */
private const val MIN_KEY_REPEAT_QUIET_PERIOD_MS = 160L

enum class DpadReorderDirection { Up, Down, Left, Right }

internal enum class DpadReorderPhase { Idle, Pressed, MovingAwaitRelease, MovingReady }

internal enum class DpadReorderActivation { None, Click, StartedMoving, Dropped }

@Stable
class DpadReorderState internal constructor() {
    internal var phase by mutableStateOf(DpadReorderPhase.Idle)
        private set

    private var activeKey by mutableStateOf<Any?>(null)
    private var dropAllowedAfterMillis = Long.MIN_VALUE
    var movingIndex by mutableIntStateOf(-1)
        private set

    val movingKey: Any?
        get() = activeKey.takeIf { isMoving }

    val isMoving: Boolean
        get() = phase == DpadReorderPhase.MovingAwaitRelease ||
                phase == DpadReorderPhase.MovingReady

    fun isMoving(key: Any): Boolean = isMoving && activeKey == key

    fun syncItems(keys: Collection<*>, enabled: Boolean = true) {
        val key = activeKey ?: return
        val index = keys.indexOfFirst { it == key }
        if (!enabled || index < 0) {
            reset()
        } else {
            movingIndex = index
        }
    }

    internal fun onActivationKeyDown(
        key: Any,
        index: Int,
        isLongPress: Boolean,
        eventTimeMillis: Long,
        repeatReleaseGuardMillis: Long
    ): DpadReorderActivation {
        return when (phase) {
            DpadReorderPhase.Idle -> {
                activeKey = key
                movingIndex = index
                phase = if (isLongPress) {
                    DpadReorderPhase.MovingAwaitRelease
                } else {
                    DpadReorderPhase.Pressed
                }
                if (isLongPress) {
                    DpadReorderActivation.StartedMoving
                } else {
                    DpadReorderActivation.None
                }
            }

            DpadReorderPhase.Pressed -> {
                if (activeKey == key && isLongPress) {
                    phase = DpadReorderPhase.MovingAwaitRelease
                    DpadReorderActivation.StartedMoving
                } else {
                    DpadReorderActivation.None
                }
            }

            DpadReorderPhase.MovingAwaitRelease -> DpadReorderActivation.None

            DpadReorderPhase.MovingReady -> {
                if (activeKey == key) {
                    if (isLongPress || eventTimeMillis <= dropAllowedAfterMillis) {
                        dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                        DpadReorderActivation.None
                    } else {
                        reset()
                        DpadReorderActivation.Dropped
                    }
                } else {
                    DpadReorderActivation.None
                }
            }
        }
    }

    internal fun onActivationKeyUp(
        key: Any,
        eventTimeMillis: Long,
        repeatReleaseGuardMillis: Long
    ): DpadReorderActivation {
        if (activeKey != key) return DpadReorderActivation.None

        return when (phase) {
            DpadReorderPhase.Idle -> DpadReorderActivation.None
            DpadReorderPhase.Pressed -> {
                reset()
                DpadReorderActivation.Click
            }

            DpadReorderPhase.MovingAwaitRelease -> {
                phase = DpadReorderPhase.MovingReady
                dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                DpadReorderActivation.None
            }

            DpadReorderPhase.MovingReady -> {
                dropAllowedAfterMillis = eventTimeMillis + repeatReleaseGuardMillis
                DpadReorderActivation.None
            }
        }
    }

    internal fun onLongPressTimeout(key: Any): DpadReorderActivation {
        if (phase != DpadReorderPhase.Pressed || activeKey != key) {
            return DpadReorderActivation.None
        }
        phase = DpadReorderPhase.MovingAwaitRelease
        return DpadReorderActivation.StartedMoving
    }

    internal fun onPointerClick(key: Any, index: Int): DpadReorderActivation {
        return if (isMoving && activeKey == key) {
            reset()
            DpadReorderActivation.Dropped
        } else if (phase == DpadReorderPhase.Idle) {
            activeKey = key
            movingIndex = index
            reset()
            DpadReorderActivation.Click
        } else {
            DpadReorderActivation.None
        }
    }

    internal fun onPointerLongPress(key: Any, index: Int): DpadReorderActivation {
        if (isMoving && activeKey != key) return DpadReorderActivation.None
        activeKey = key
        movingIndex = index
        phase = DpadReorderPhase.MovingReady
        return DpadReorderActivation.StartedMoving
    }

    internal fun cancelPress(key: Any) {
        if (phase == DpadReorderPhase.Pressed && activeKey == key) reset()
    }

    internal fun move(
        direction: DpadReorderDirection,
        itemCount: Int,
        targetIndex: (Int, DpadReorderDirection) -> Int,
        onMove: (Int, Int) -> Unit
    ): Boolean {
        if (!isMoving) return false

        val fromIndex = movingIndex
        val toIndex = targetIndex(fromIndex, direction)
        if (toIndex in 0 until itemCount && toIndex != fromIndex) {
            movingIndex = toIndex
            onMove(fromIndex, toIndex)
        }
        return true
    }

    private fun reset() {
        phase = DpadReorderPhase.Idle
        activeKey = null
        dropAllowedAfterMillis = Long.MIN_VALUE
        movingIndex = -1
    }
}

class DpadReorderItem(
    val state: DpadReorderState,
    val key: Any,
    val index: Int,
    val itemCount: Int,
    val targetIndex: (Int, DpadReorderDirection) -> Int,
    val onMove: (Int, Int) -> Unit
)

@Composable
fun rememberDpadReorderState(key: Any? = Unit): DpadReorderState =
    remember(key) { DpadReorderState() }

fun verticalDpadReorderTarget(index: Int, direction: DpadReorderDirection): Int = when (direction) {
    DpadReorderDirection.Up -> index - 1
    DpadReorderDirection.Down -> index + 1
    DpadReorderDirection.Left,
    DpadReorderDirection.Right -> index
}

fun twoColumnDpadReorderTarget(
    index: Int,
    direction: DpadReorderDirection,
    isRtl: Boolean
): Int = when (direction) {
    DpadReorderDirection.Up -> index - 2
    DpadReorderDirection.Down -> index + 2
    DpadReorderDirection.Left -> when {
        isRtl && index % 2 == 0 -> index + 1
        !isRtl && index % 2 == 1 -> index - 1
        else -> index
    }

    DpadReorderDirection.Right -> when {
        isRtl && index % 2 == 1 -> index - 1
        !isRtl && index % 2 == 0 -> index + 1
        else -> index
    }
}

internal fun <T> reorderIndicesForKeys(keys: List<T>, fromKey: Any?, toKey: Any?): Pair<Int, Int>? {
    val fromIndex = keys.indexOfFirst { it == fromKey }
    val toIndex = keys.indexOfFirst { it == toKey }
    return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
        fromIndex to toIndex
    } else {
        null
    }
}

internal fun dpadRepeatReleaseGuardMillis(keyRepeatDelayMillis: Int): Long =
    maxOf(MIN_KEY_REPEAT_QUIET_PERIOD_MS, keyRepeatDelayMillis.toLong() * 2L)

@Composable
fun Modifier.dpadLongPressToMove(
    enabled: Boolean,
    item: DpadReorderItem,
    onClick: () -> Unit,
    addFocusTarget: Boolean = true
): Modifier {
    if (!enabled) return this

    val coroutineScope = rememberCoroutineScope()
    val repeatReleaseGuardMillis = remember {
        dpadRepeatReleaseGuardMillis(ViewConfiguration.getKeyRepeatDelay())
    }
    var isItemFocused by remember { mutableStateOf(false) }
    var suppressNextCenterUp by remember { mutableStateOf(false) }
    var longPressJob by remember(item.state, item.key) { mutableStateOf<Job?>(null) }

    fun startLongPressTimer() {
        if (longPressJob != null) return
        longPressJob = coroutineScope.launch {
            delay(ViewConfiguration.getLongPressTimeout().toLong())
            item.state.onLongPressTimeout(item.key)
            longPressJob = null
        }
    }

    fun cancelLongPressTimer() {
        longPressJob?.cancel()
        longPressJob = null
    }

    fun handleActivation(action: DpadReorderActivation) {
        when (action) {
            DpadReorderActivation.Click -> onClick()
            DpadReorderActivation.Dropped -> suppressNextCenterUp = true
            DpadReorderActivation.StartedMoving,
            DpadReorderActivation.None -> Unit
        }
    }

    DisposableEffect(item.state, item.key) {
        onDispose {
            cancelLongPressTimer()
            item.state.cancelPress(item.key)
        }
    }

    val movementModifier = pointerInput(item.state, item.key, item.index, onClick) {
        detectTapGestures(
            onTap = {
                handleActivation(item.state.onPointerClick(item.key, item.index))
            },
            onLongPress = {
                handleActivation(item.state.onPointerLongPress(item.key, item.index))
            }
        )
    }
        .semantics {
            semanticsOnClick {
                handleActivation(item.state.onPointerClick(item.key, item.index))
                true
            }
            semanticsOnLongClick {
                handleActivation(item.state.onPointerLongPress(item.key, item.index))
                true
            }
        }
        .onFocusChanged {
            isItemFocused = it.isFocused
            if (!it.isFocused) {
                cancelLongPressTimer()
                item.state.cancelPress(item.key)
            }
        }
        .onPreviewKeyEvent { event ->
            if (!isItemFocused) return@onPreviewKeyEvent false

            val isActivationKey = event.key == Key.DirectionCenter || event.key == Key.Enter
            if (!isActivationKey) {
                val direction = when (event.key) {
                    Key.DirectionUp -> DpadReorderDirection.Up
                    Key.DirectionDown -> DpadReorderDirection.Down
                    Key.DirectionLeft -> DpadReorderDirection.Left
                    Key.DirectionRight -> DpadReorderDirection.Right
                    else -> null
                }
                if (direction == null || !item.state.isMoving(item.key)) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    item.state.move(
                        direction = direction,
                        itemCount = item.itemCount,
                        targetIndex = item.targetIndex,
                        onMove = item.onMove
                    )
                }
                true
            } else if (suppressNextCenterUp) {
                if (event.type == KeyEventType.KeyUp) suppressNextCenterUp = false
                true
            } else {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val isLongPress = event.nativeKeyEvent.repeatCount > 0 ||
                                event.nativeKeyEvent.isLongPress
                        val action = item.state.onActivationKeyDown(
                            key = item.key,
                            index = item.index,
                            isLongPress = isLongPress,
                            eventTimeMillis = event.nativeKeyEvent.eventTime,
                            repeatReleaseGuardMillis = repeatReleaseGuardMillis
                        )
                        if (action == DpadReorderActivation.None &&
                            item.state.phase == DpadReorderPhase.Pressed
                        ) {
                            startLongPressTimer()
                        } else {
                            cancelLongPressTimer()
                        }
                        handleActivation(action)
                        true
                    }

                    KeyEventType.KeyUp -> {
                        cancelLongPressTimer()
                        handleActivation(
                            item.state.onActivationKeyUp(
                                key = item.key,
                                eventTimeMillis = event.nativeKeyEvent.eventTime,
                                repeatReleaseGuardMillis = repeatReleaseGuardMillis
                            )
                        )
                        true
                    }

                    else -> true
                }
            }
        }

    return if (addFocusTarget) movementModifier.focusable() else movementModifier
}
