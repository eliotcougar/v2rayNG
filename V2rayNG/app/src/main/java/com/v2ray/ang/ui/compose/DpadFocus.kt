package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

/** Lazy and popup content can attach after its surrounding composition, so retry by frame. */
internal suspend fun requestFocusWhenReady(vararg requesters: FocusRequester): Boolean {
    repeat(FocusAttachmentRetryFrames) {
        if (requesters.any { it.requestFocus() }) return true
        withFrameNanos { }
    }
    return false
}

/** Popup disposal restores focus asynchronously, so wait for it before selecting an adjacent control. */
internal suspend fun afterDpadPopupDismiss(action: () -> Unit) {
    repeat(2) { withFrameNanos { } }
    action()
}

/** Adds focus visibility to existing controls without changing their size or touch behavior. */
@Composable
internal fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 12.dp
): Modifier {
    if (!isTelevisionDevice()) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius))
            else Modifier
        )
}

internal enum class DpadHorizontalDirection { Previous, Next }

internal fun logicalHorizontalDirection(key: Key, isRtl: Boolean): DpadHorizontalDirection? = when (key) {
    Key.DirectionLeft -> if (isRtl) DpadHorizontalDirection.Next else DpadHorizontalDirection.Previous
    Key.DirectionRight -> if (isRtl) DpadHorizontalDirection.Previous else DpadHorizontalDirection.Next
    else -> null
}

internal fun adjacentDpadFocusIndex(
    currentIndex: Int,
    itemCount: Int,
    direction: DpadHorizontalDirection
): Int? {
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

/** Makes a TV list item's primary area focusable without intercepting sibling action controls. */
@Composable
internal fun Modifier.dpadListItemNavigation(
    onMovePrevious: () -> Unit,
    onClick: (() -> Unit)? = null
): Modifier {
    if (!isTelevisionDevice()) return this
    var rowFocused by remember { mutableStateOf(false) }
    return this
        .dpadFocusOutline()
        .onFocusChanged { rowFocused = it.isFocused }
        .dpadMovePreviousNavigation(enabled = rowFocused, onMovePrevious = onMovePrevious)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable())
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
internal fun Modifier.dpadVerticalFocusNavigation(
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean
): Modifier {
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
        onMoveDown = { nextRow?.requestFocus() ?: onAfterLastRow?.invoke() ?: true }
    )
