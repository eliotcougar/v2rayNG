package com.v2ray.ang.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
fun rememberDpadFocusRequester(
    requestFocus: Boolean = true,
    requestKey: Any? = Unit
): FocusRequester {
    val isTelevision = isTelevisionDevice()
    val requester = remember { FocusRequester() }
    LaunchedEffect(isTelevision, requestFocus, requestKey) {
        if (isTelevision && requestFocus) {
            repeat(30) {
                withFrameNanos { }
                if (requester.requestFocus()) return@LaunchedEffect
            }
        }
    }
    return requester
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
    cornerRadius: Dp = 12.dp
): Modifier {
    if (!isTelevisionDevice()) return this

    var isFocused by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val focusContainerColor = MaterialTheme.colorScheme.primaryContainer
    val shape = RoundedCornerShape(cornerRadius)
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    val focusDecoration = if (isFocused) {
        Modifier
            .background(focusContainerColor, shape)
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

/** Applies the same television safe-area inset to screen content without changing touch UIs. */
@Composable
fun Modifier.tvContentPadding(horizontal: Dp = 48.dp, vertical: Dp = 0.dp): Modifier {
    if (!isTelevisionDevice()) return this
    return padding(horizontal = horizontal, vertical = vertical)
}

/** Gives TV rows an explicit left/right focus chain while leaving touch devices unchanged. */
@Composable
fun Modifier.dpadHorizontalFocusNavigation(
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
): Modifier {
    if (!isTelevisionDevice()) return this
    return onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> {
                onMoveLeft()
                true
            }
            Key.DirectionRight -> {
                onMoveRight()
                true
            }
            else -> false
        }
    }
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
