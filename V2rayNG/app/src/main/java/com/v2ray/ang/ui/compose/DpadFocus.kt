package com.v2ray.ang.ui.compose

import android.content.res.Configuration
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
    cornerRadius: Dp = 12.dp,
    focusedScale: Float = 1.025f
): Modifier {
    if (!isTelevisionDevice()) return this

    var hasFocus by remember { mutableStateOf(false) }
    val focusColor = MaterialTheme.colorScheme.primary
    val focusContainerColor = MaterialTheme.colorScheme.primaryContainer
    val focusScale by animateFloatAsState(
        targetValue = if (hasFocus) focusedScale else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
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
            .background(focusContainerColor, shape)
            .border(width = 2.dp, color = focusColor, shape = shape)
    } else {
        Modifier
    }

    return this
        .then(requesterModifier)
        .onFocusChanged { hasFocus = it.hasFocus }
        .graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
            shadowElevation = if (hasFocus) 8.dp.toPx() else 0f
            this.shape = shape
            clip = false
            ambientShadowColor = focusColor.copy(alpha = 0.28f)
            spotShadowColor = focusColor.copy(alpha = 0.28f)
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
