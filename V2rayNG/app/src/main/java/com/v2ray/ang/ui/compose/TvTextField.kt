package com.v2ray.ang.ui.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

data class TvTextFieldNavigation(
    val focusRequester: FocusRequester? = null,
    val onMoveUp: (() -> Boolean)? = null,
    val onMoveDown: (() -> Boolean)? = null,
    val onMovePrevious: (() -> Boolean)? = null,
    val onMoveNext: (() -> Boolean)? = null
)

internal class TvTextFieldState(
    val passiveFocusRequester: FocusRequester,
    private val hideKeyboard: () -> Unit
) {
    val editorFocusRequester = FocusRequester()
    val interactionSource = MutableInteractionSource()
    var isEditing by mutableStateOf(false)
        private set
    internal var editorHadFocus = false
    internal var imeWasVisible = false
    internal var restorePassiveFocus by mutableStateOf(false)

    fun beginEditing() {
        editorHadFocus = false
        imeWasVisible = false
        restorePassiveFocus = false
        isEditing = true
    }

    fun finishEditing(restoreFocus: Boolean = false) {
        isEditing = false
        editorHadFocus = false
        imeWasVisible = false
        restorePassiveFocus = restoreFocus
        hideKeyboard()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun rememberTvTextFieldState(passiveFocusRequester: FocusRequester? = null): TvTextFieldState {
    val defaultRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentKeyboardController = rememberUpdatedState(keyboardController)
    val state = remember(passiveFocusRequester) {
        TvTextFieldState(
            passiveFocusRequester = passiveFocusRequester ?: defaultRequester,
            hideKeyboard = { currentKeyboardController.value?.hide() }
        )
    }
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(state.isEditing, state.editorHadFocus, isImeVisible) {
        if (state.isEditing && state.editorHadFocus) {
            if (isImeVisible) state.imeWasVisible = true
            else if (state.imeWasVisible) state.finishEditing(restoreFocus = true)
        }
    }
    LaunchedEffect(state.isEditing, state.restorePassiveFocus) {
        when {
            state.isEditing -> {
                requestFocusWhenReady(state.editorFocusRequester)
                keyboardController?.show()
            }
            state.restorePassiveFocus -> {
                requestFocusWhenReady(state.passiveFocusRequester)
                state.restorePassiveFocus = false
            }
        }
    }
    return state
}

@Composable
internal fun Modifier.tvAwareTextFieldFocus(
    state: TvTextFieldState?,
    enabled: Boolean,
    navigation: TvTextFieldNavigation = TvTextFieldNavigation(),
    onActivate: () -> Unit
): Modifier {
    if (state == null) return this
    val focusManager = LocalFocusManager.current
    fun move(direction: FocusDirection, callback: (() -> Boolean)?): Boolean {
        state.finishEditing()
        return callback?.invoke() ?: focusManager.moveFocus(direction)
    }
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (!state.isEditing) {
                onActivate()
                true
            } else false
            Key.Back -> if (state.isEditing) {
                state.finishEditing(restoreFocus = true)
                true
            } else false
            Key.DirectionUp -> move(FocusDirection.Up, navigation.onMoveUp)
            Key.DirectionDown -> move(FocusDirection.Down, navigation.onMoveDown)
            else -> false
        }
    }
        .dpadLogicalHorizontalNavigation(
            onMovePrevious = navigation.onMovePrevious?.let { callback ->
                { !state.isEditing && callback() }
            },
            onMoveNext = navigation.onMoveNext?.let { callback ->
                { !state.isEditing && callback() }
            }
        )
        .focusRequester(state.passiveFocusRequester)
        .focusProperties { canFocus = enabled }
        .focusable(enabled = enabled, interactionSource = state.interactionSource)
}

internal fun Modifier.tvTextFieldEditorFocus(state: TvTextFieldState, enabled: Boolean = true): Modifier =
    focusRequester(state.editorFocusRequester)
        .focusProperties { canFocus = enabled && state.isEditing }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) state.editorHadFocus = true
            else if (state.editorHadFocus) state.finishEditing()
        }
