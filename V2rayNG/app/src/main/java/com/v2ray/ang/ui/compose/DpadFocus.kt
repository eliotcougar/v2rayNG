package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Requests focus after the target has entered the composition. Android TV does not have a
 * touch event to bootstrap Compose focus, so every screen needs a deterministic first target.
 */
@Composable
fun rememberDpadFocusRequester(
    requestFocus: Boolean = true,
    requestKey: Any? = Unit
): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus, requestKey) {
        if (requestFocus) {
            repeat(6) {
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
 * tonal lift, thin accent edge, and a small scale change that reads clearly at TV distance.
 * Apply this before clickable/focusable modifiers so it observes their focus state.
 */
@Composable
fun Modifier.dpadFocusOutline(
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 8.dp
): Modifier {
    var hasFocus by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.secondary
    val focusScale by animateFloatAsState(
        targetValue = if (hasFocus) 1.018f else 1f,
        animationSpec = spring(stiffness = 600f, dampingRatio = 0.78f),
        label = "dpadFocusScale"
    )
    val shape = RoundedCornerShape(cornerRadius)
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    val focusDecoration = if (hasFocus) {
        Modifier
            .background(focusColor.copy(alpha = 0.12f), shape)
            .border(width = 1.5.dp, color = focusColor, shape = shape)
    } else {
        Modifier
    }

    return this
        .then(requesterModifier)
        .onFocusChanged { hasFocus = it.hasFocus }
        .graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
        }
        .then(focusDecoration)
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
