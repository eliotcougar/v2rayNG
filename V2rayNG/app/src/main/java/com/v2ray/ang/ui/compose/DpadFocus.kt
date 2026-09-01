package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val FocusAttachmentRetryFrames = 30

@Composable
internal fun isTelevisionDevice(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/** Gives a non-touch screen a deterministic initial focus target without affecting phone UI. */
@Composable
internal fun rememberDpadFocusRequester(requestFocus: Boolean = true, requestKey: Any? = Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    if (isTelevisionDevice() && requestFocus) {
        LaunchedEffect(requester, requestKey) { requestFocusWhenReady(requester) }
    }
    return requester
}

/**
 * A FocusRequester can exist before its lazy-list or popup target is attached. Retry for a bounded
 * number of frames so initial D-pad focus survives that Compose attachment delay. This can be
 * removed when Compose offers an await-attached focus API or every caller owns an attachment signal.
 */
internal suspend fun requestFocusWhenReady(vararg requesters: FocusRequester): Boolean {
    repeat(FocusAttachmentRetryFrames) {
        if (requesters.any { it.requestFocus() }) return true
        withFrameNanos { }
    }
    return false
}

/**
 * Popup disposal and Material's focus restoration finish asynchronously. Keep this two-frame bridge
 * until Compose exposes popup-dismiss completion; requesting immediately can focus the screen's Back button.
 */
internal suspend fun afterDpadPopupDismiss(action: () -> Unit) {
    repeat(2) { withFrameNanos { } }
    action()
}

/** Adds focus visibility to existing controls without changing their size or touch behavior. */
@Composable
internal fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 12.dp
): Modifier = dpadFocusOutline(focusRequester, RoundedCornerShape(cornerRadius))

@Composable
internal fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester?,
    shape: Shape,
    clipContent: Boolean = true
): Modifier {
    if (!isTelevisionDevice()) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .then(if (clipContent) Modifier.clip(shape) else Modifier)
        .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
}

/** Uses the same shape as Material's focus tint instead of approximating it with a radius. */
@Composable
internal fun Modifier.dpadIconButtonFocusOutline(focusRequester: FocusRequester? = null): Modifier =
    dpadFocusOutline(focusRequester, IconButtonDefaults.standardShape, clipContent = false)

/** Uses the same shape as the Material FAB container without changing the button itself. */
@Composable
internal fun Modifier.dpadFloatingActionButtonFocusOutline(focusRequester: FocusRequester? = null): Modifier =
    dpadFocusOutline(focusRequester, FloatingActionButtonDefaults.shape, clipContent = false)

/** Uses Material's text-button shape for dialog actions. */
@Composable
internal fun Modifier.dpadTextButtonFocusOutline(focusRequester: FocusRequester? = null): Modifier =
    dpadFocusOutline(focusRequester, ButtonDefaults.textShape, clipContent = false)

/** Keeps the TV focus border outside the field's native outline and floating label. */
@Composable
internal fun Modifier.dpadTextFieldFocusOutline(focusRequester: FocusRequester? = null): Modifier {
    if (!isTelevisionDevice()) return this
    return dpadFocusOutline(focusRequester, OutlinedTextFieldDefaults.shape).padding(4.dp)
}

internal enum class DpadHorizontalDirection { Previous, Next }

internal fun logicalHorizontalDirection(key: Key, isRtl: Boolean): DpadHorizontalDirection? = when (key) {
    Key.DirectionLeft -> if (isRtl) DpadHorizontalDirection.Next else DpadHorizontalDirection.Previous
    Key.DirectionRight -> if (isRtl) DpadHorizontalDirection.Previous else DpadHorizontalDirection.Next
    else -> null
}

internal fun adjacentDpadFocusIndex(currentIndex: Int, itemCount: Int, direction: DpadHorizontalDirection): Int? {
    if (currentIndex !in 0 until itemCount) return null
    val targetIndex = when (direction) {
        DpadHorizontalDirection.Previous -> currentIndex - 1
        DpadHorizontalDirection.Next -> currentIndex + 1
    }
    return targetIndex.takeIf { it in 0 until itemCount }
}

internal fun adjacentDpadFocusTarget(
    current: FocusRequester,
    order: List<FocusRequester>,
    direction: DpadHorizontalDirection
): FocusRequester? {
    val currentIndex = order.indexOfFirst { it === current }
    return adjacentDpadFocusIndex(currentIndex, order.size, direction)?.let(order::get)
}

/** Leaves a focused row through its logical leading edge; child actions receive the event first. */
@Composable
internal fun Modifier.dpadMovePreviousNavigation(enabled: Boolean = true, onMovePrevious: () -> Unit): Modifier {
    if (!isTelevisionDevice() || !enabled) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            logicalHorizontalDirection(event.key, isRtl) == DpadHorizontalDirection.Previous
        ) {
            onMovePrevious()
            true
        } else false
    }
}

/** Handles Back inside a focused TV subtree before the activity fallback. */
@Composable
internal fun Modifier.dpadBackNavigation(enabled: Boolean = true, onBack: () -> Unit): Modifier {
    if (!isTelevisionDevice() || !enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            onBack()
            true
        } else false
    }
}

/** Adds explicit vertical exits where spatial focus search has no stable target. */
@Composable
internal fun Modifier.dpadVerticalFocusNavigation(onMoveUp: () -> Boolean, onMoveDown: () -> Boolean): Modifier {
    if (!isTelevisionDevice()) return this
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionUp -> onMoveUp()
            Key.DirectionDown -> onMoveDown()
            else -> false
        }
    }
}

/** Handles logical horizontal actions without making callers mirror their behavior for RTL. */
@Composable
internal fun Modifier.dpadLogicalHorizontalNavigation(
    onMovePrevious: (() -> Boolean)? = null,
    onMoveNext: (() -> Boolean)? = null
): Modifier {
    if (!isTelevisionDevice()) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (logicalHorizontalDirection(event.key, isRtl)) {
            DpadHorizontalDirection.Previous -> onMovePrevious?.invoke() ?: false
            DpadHorizontalDirection.Next -> onMoveNext?.invoke() ?: false
            null -> false
        }
    }
}

/** Moves within one logical TV focus chain without pairwise neighbor wiring. */
@Composable
internal fun Modifier.dpadOrderedFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onBeforeFirst: (() -> Unit)? = null,
    onAfterLast: (() -> Unit)? = null
): Modifier {
    fun move(direction: DpadHorizontalDirection, onEdge: (() -> Unit)?): Boolean {
        val target = adjacentDpadFocusTarget(current, order, direction)
        if (target != null) target.requestFocus() else onEdge?.invoke() ?: current.requestFocus()
        return true
    }
    return dpadLogicalHorizontalNavigation(
        onMovePrevious = { move(DpadHorizontalDirection.Previous, onBeforeFirst) },
        onMoveNext = { move(DpadHorizontalDirection.Next, onAfterLast) }
    )
}

/** Popup contents consume D-pad events, so handle their horizontal exits during preview. */
@Composable
internal fun Modifier.dpadPopupHorizontalNavigation(
    onMovePrevious: () -> Unit,
    onMoveNext: (() -> Unit)? = null
): Modifier {
    if (!isTelevisionDevice()) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (logicalHorizontalDirection(event.key, isRtl)) {
            DpadHorizontalDirection.Previous -> {
                onMovePrevious()
                true
            }
            DpadHorizontalDirection.Next -> onMoveNext?.let { it(); true } ?: false
            null -> false
        }
    }
}

/** Applies the shared logical and vertical navigation policy to one top-bar control. */
@Composable
internal fun Modifier.dpadTopBarFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onMoveDown: () -> Boolean
): Modifier = dpadOrderedFocusNavigation(current, order)
    .dpadVerticalFocusNavigation(onMoveUp = { true }, onMoveDown = onMoveDown)

/** Keeps a list action in its horizontal row and vertical action column. */
@Composable
internal fun Modifier.dpadRowActionNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    previousRow: FocusRequester?,
    nextRow: FocusRequester?,
    onAfterLastRow: (() -> Boolean)? = null
): Modifier = dpadOrderedFocusNavigation(current, order)
    .dpadVerticalFocusNavigation(
        onMoveUp = { previousRow?.requestFocus() ?: false },
        // Consume Down at the final row so Compose cannot escape into an unrelated focus subtree.
        onMoveDown = { nextRow?.requestFocus() ?: onAfterLastRow?.invoke() ?: true }
    )
