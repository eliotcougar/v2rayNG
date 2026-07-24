@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val TvImeBottomClearance = 32.dp

/**
 * Owns the passive-row/editor handoff and IME positioning for one TV text field. Keeping that
 * behavior here prevents every form and dialog from implementing subtly different state machines.
 */
internal class TvTextFieldState(
    val passiveFocusRequester: FocusRequester,
    private val hideKeyboard: () -> Unit
) {
    val editorFocusRequester = FocusRequester()
    val interactionSource = MutableInteractionSource()
    val bringIntoViewRequester = BringIntoViewRequester()

    var isEditing by mutableStateOf(false)
        private set
    internal var editorHasFocus by mutableStateOf(false)
    internal var imeWasVisible by mutableStateOf(false)
    internal var restorePassiveFocus by mutableStateOf(false)
    internal var measuredSize by mutableStateOf(IntSize.Zero)

    fun beginEditing() {
        editorHasFocus = false
        imeWasVisible = false
        restorePassiveFocus = false
        isEditing = true
    }

    fun finishEditing(restoreFocus: Boolean = false) {
        val wasEditing = isEditing || editorHasFocus
        isEditing = false
        editorHasFocus = false
        imeWasVisible = false
        restorePassiveFocus = restoreFocus
        if (wasEditing) hideKeyboard()
    }

    internal fun onEditorFocusChanged(isFocused: Boolean) {
        if (isFocused) {
            editorHasFocus = true
        } else if (editorHasFocus) {
            finishEditing()
        }
    }

    internal suspend fun bringEditorAboveIme() {
        bringIntoViewRequester.bringIntoView()
    }
}

@Composable
internal fun rememberTvTextFieldState(
    passiveFocusRequester: FocusRequester? = null
): TvTextFieldState {
    val defaultPassiveFocusRequester = remember { FocusRequester() }
    val resolvedPassiveFocusRequester = passiveFocusRequester ?: defaultPassiveFocusRequester
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentKeyboardController = rememberUpdatedState(keyboardController)
    val state = remember(resolvedPassiveFocusRequester) {
        TvTextFieldState(
            passiveFocusRequester = resolvedPassiveFocusRequester,
            hideKeyboard = { currentKeyboardController.value?.hide() }
        )
    }
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(
        state.isEditing,
        state.editorHasFocus,
        isImeVisible,
        state.measuredSize
    ) {
        when {
            state.isEditing &&
                state.editorHasFocus &&
                state.imeWasVisible &&
                !isImeVisible -> state.finishEditing(restoreFocus = true)

            state.isEditing && state.editorHasFocus -> {
                if (isImeVisible) {
                    state.imeWasVisible = true
                    repeat(3) { withFrameNanos { } }
                    state.bringEditorAboveIme()
                }
                keyboardController?.show()
            }

            state.isEditing -> state.editorFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.isEditing, state.restorePassiveFocus) {
        if (!state.isEditing && state.restorePassiveFocus) {
            state.passiveFocusRequester.requestFocus()
            state.restorePassiveFocus = false
        }
    }
    return state
}

@Composable
internal fun Modifier.tvPassiveTextFieldFocus(
    state: TvTextFieldState,
    enabled: Boolean,
    onActivate: () -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean
): Modifier {
    return bringIntoViewRequester(state.bringIntoViewRequester)
        .onSizeChanged { state.measuredSize = it }
        .padding(bottom = if (state.isEditing) TvImeBottomClearance else 0.dp)
        .onPreviewKeyEvent { event ->
            if (
                event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                !state.isEditing
            ) {
                onActivate()
                true
            } else {
                false
            }
        }
        .dpadTextFieldNavigation(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
        .focusRequester(state.passiveFocusRequester)
        .focusable(
            enabled = enabled && !state.isEditing,
            interactionSource = state.interactionSource
        )
}

internal fun Modifier.tvTextFieldEditorFocus(
    state: TvTextFieldState,
    enabled: Boolean = true
): Modifier {
    return focusRequester(state.editorFocusRequester)
        .focusProperties { canFocus = enabled && state.isEditing }
        .onFocusChanged { state.onEditorFocusChanged(it.isFocused) }
}
