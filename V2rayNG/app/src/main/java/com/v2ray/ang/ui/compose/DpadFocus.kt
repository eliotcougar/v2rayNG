package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val TvFocusAttachmentRetryFrames = 30

@Composable
fun isTelevisionDevice(): Boolean {
    return LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Requests focus after the target has entered the composition. Android TV does not have a
 * touch event to bootstrap Compose focus, so every screen needs a deterministic first target.
 * On phones and tablets this deliberately does nothing, preserving the existing touch UI.
 */
@Composable
fun rememberDpadFocusRequester(requestFocus: Boolean = true, requestKey: Any? = Unit): FocusRequester {
    val isTelevision = isTelevisionDevice()
    val requester = remember { FocusRequester() }
    if (isTelevision && requestFocus) {
        LaunchedEffect(requester, requestKey) {
            requestFocusWhenReady(requester)
        }
    }
    return requester
}

/**
 * Focus nodes in lazy lists, dialogs, drawers, and popup menus can be attached several frames
 * after their surrounding composition. The immediate attempt keeps ordinary navigation fast;
 * the bounded frame retries recover only while that real focus target is being attached.
 *
 * This is deliberately frame-based rather than a fixed delay: fixed delays made drawer focus
 * feel sluggish and were still unreliable under a busy TV renderer. Thirty frames is a generous
 * upper bound, not animation timing. Do not remove or shorten it without testing newly composed
 * lazy-list items and popup focus on a real/emulated TV.
 */
suspend fun requestFocusWhenReady(vararg requesters: FocusRequester): Boolean {
    repeat(TvFocusAttachmentRetryFrames) {
        if (requesters.any { it.requestFocus() }) return true
        withFrameNanos { }
    }
    return false
}

/**
 * Adds a TV-style focus treatment while keeping the component's existing click/focus node.
 * Unfocused controls are left completely untouched; focused controls receive a restrained
 * tonal lift and thin accent edge that read clearly at TV distance without changing layout size.
 * The same rounded shape clips descendant indications so Material focus/ripple layers cannot
 * escape the border as square corners.
 * Apply this before clickable/focusable modifiers so it observes their focus state.
 */
@Composable
fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 12.dp,
    focusContainerColor: Color? = null,
    showFocus: Boolean = true
): Modifier {
    if (!isTelevisionDevice()) return this

    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    if (!showFocus) return then(requesterModifier)

    var isFocused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val resolvedFocusContainerColor = focusContainerColor ?: MaterialTheme.colorScheme.primaryContainer
    val shape = RoundedCornerShape(cornerRadius)
    val focusDecoration = if (isFocused) {
        Modifier
            .background(resolvedFocusContainerColor, shape)
            .border(width = 2.dp, color = focusColor, shape = shape)
    } else {
        Modifier
    }

    return this
        .then(requesterModifier)
        .onFocusChanged { isFocused = it.isFocused }
        .then(focusDecoration)
        .clip(shape)
}

/** Keeps TV popup-menu focus borders inside the popup's clipped bounds. */
@Composable
fun Modifier.tvMenuItemFocus(): Modifier {
    if (!isTelevisionDevice()) return this
    val shape = RoundedCornerShape(10.dp)
    return padding(horizontal = 8.dp, vertical = 2.dp)
        .dpadFocusOutline(cornerRadius = 10.dp)
        .clip(shape)
}

/** Applies the fixed television safe-area inset without changing touch UIs. */
@Composable
fun Modifier.tvSafeAreaPadding(horizontal: Dp = 48.dp, vertical: Dp = 0.dp): Modifier {
    if (!isTelevisionDevice()) return this
    return padding(horizontal = horizontal, vertical = vertical)
}

/**
 * Applies the platform IME inset everywhere, and reserves layout space for TV keyboards that
 * are rendered as overlays while reporting a zero-height IME inset. Because the fallback is
 * layout padding outside the scroll container, even a short form gains enough scroll range for
 * BringIntoViewRequester to reveal its final field.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.tvAwareImePadding(overlayFallbackHeight: Dp = 240.dp): Modifier {
    val isTelevision = isTelevisionDevice()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.isImeVisible
    val reportedImeBottomInset = if (isTelevision && isImeVisible) {
        WindowInsets.ime.getBottom(density)
    } else {
        0
    }
    val overlayPadding = if (isTelevision && isImeVisible && reportedImeBottomInset == 0) {
        overlayFallbackHeight
    } else {
        0.dp
    }

    return imePadding().padding(bottom = overlayPadding)
}

/**
 * Uses the normal Material indication on touch devices and the explicit focus outline on TV.
 * This keeps the platform distinction in one place instead of duplicating clickable branches.
 */
@Composable
fun Modifier.dpadClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier {
    if (!isTelevisionDevice()) {
        return clickable(enabled = enabled, role = role, onClick = onClick)
    }
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = role, onClick = onClick)
}

internal enum class DpadHorizontalDirection {
    Previous,
    Next
}

internal fun logicalHorizontalDirection(key: Key, isRtl: Boolean): DpadHorizontalDirection? = when (key) {
    Key.DirectionLeft -> if (isRtl) {
        DpadHorizontalDirection.Next
    } else {
        DpadHorizontalDirection.Previous
    }

    Key.DirectionRight -> if (isRtl) {
        DpadHorizontalDirection.Previous
    } else {
        DpadHorizontalDirection.Next
    }

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

/**
 * Handles horizontal TV navigation in logical composition order. Previous is physically Left in
 * LTR and Right in RTL; Next is the opposite. Callers must never mirror these callbacks themselves.
 */
@Composable
fun Modifier.dpadLogicalHorizontalNavigation(onMovePrevious: () -> Unit, onMoveNext: () -> Unit): Modifier {
    if (!isTelevisionDevice()) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (logicalHorizontalDirection(event.key, isRtl)) {
            DpadHorizontalDirection.Previous -> {
                onMovePrevious()
                true
            }

            DpadHorizontalDirection.Next -> {
                onMoveNext()
                true
            }

            null -> false
        }
    }
}

/**
 * Moves within one ordered TV focus chain. A row or toolbar supplies its targets once in logical
 * composition order; individual controls no longer wire their left/right neighbors pair-by-pair.
 * RTL conversion stays exclusively in [dpadLogicalHorizontalNavigation].
 */
@Composable
fun Modifier.dpadOrderedFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onBeforeFirst: (() -> Unit)? = null,
    onAfterLast: (() -> Unit)? = null
): Modifier {
    fun move(direction: DpadHorizontalDirection, onEdge: (() -> Unit)?) {
        val target = adjacentDpadFocusTarget(current, order, direction)
        if (target == null) {
            onEdge?.invoke() ?: current.requestFocus()
        } else {
            target.requestFocus()
        }
    }

    return dpadLogicalHorizontalNavigation(
        onMovePrevious = {
            move(DpadHorizontalDirection.Previous, onBeforeFirst)
        },
        onMoveNext = {
            move(DpadHorizontalDirection.Next, onAfterLast)
        }
    )
}

/**
 * Handles popup exits using the same logical ordering convention as ordinary action rows. Preview
 * events are required here because popup content otherwise consumes the physical D-pad event first.
 */
@Composable
fun Modifier.dpadPopupHorizontalNavigation(
    onMovePrevious: () -> Unit, onMoveNext: (() -> Unit)? = null
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

            DpadHorizontalDirection.Next -> {
                if (onMoveNext == null) false else {
                    onMoveNext()
                    true
                }
            }

            null -> false
        }
    }
}

/** Lets an editor leave through its logical leading edge before it consumes the D-pad key. */
@Composable
fun Modifier.dpadMovePreviousNavigation(onMovePrevious: () -> Unit): Modifier {
    if (!isTelevisionDevice()) return this
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            logicalHorizontalDirection(event.key, isRtl) == DpadHorizontalDirection.Previous
        ) {
            onMovePrevious()
            true
        } else {
            false
        }
    }
}

/** Gives TV controls an explicit up/down focus chain while preserving spatial fallback. */
@Composable
fun Modifier.dpadVerticalFocusNavigation(onMoveUp: () -> Boolean, onMoveDown: () -> Boolean): Modifier {
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

/** Applies the shared logical and vertical navigation policy to one top-bar control. */
@Composable
fun Modifier.dpadTopBarFocusNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    onMoveDown: () -> Boolean
): Modifier = dpadOrderedFocusNavigation(current, order)
    .dpadVerticalFocusNavigation(onMoveUp = { true }, onMoveDown = onMoveDown)

/**
 * Applies the uniform action-column navigation used by list rows. Down is consumed at the final
 * row so focus cannot escape beyond the list; Up may fall back to the surrounding screen.
 */
@Composable
fun Modifier.dpadRowActionNavigation(
    current: FocusRequester,
    order: List<FocusRequester>,
    previousRow: FocusRequester?,
    nextRow: FocusRequester?
): Modifier {
    return dpadOrderedFocusNavigation(
        current = current,
        order = order
    ).dpadVerticalFocusNavigation(
        onMoveUp = { previousRow?.requestFocus() ?: false },
        onMoveDown = { nextRow?.requestFocus() ?: true }
    )
}

/**
 * Lets a remote leave a focused text field after the on-screen keyboard is dismissed.
 * Compose text fields otherwise consume D-pad Up/Down as cursor movement, trapping TV focus.
 */
@Composable
fun Modifier.dpadTextFieldNavigation(
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null
): Modifier {
    if (!isTelevisionDevice()) return this

    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

        when (event.key) {
            Key.DirectionUp -> onMoveUp?.invoke() ?: focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionDown -> onMoveDown?.invoke() ?: focusManager.moveFocus(FocusDirection.Down)
            else -> false
        }
    }
}
