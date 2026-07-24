@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLines: Int = 5,
    tvFocusRequester: FocusRequester? = null,
    tvOnMoveUp: (() -> Boolean)? = null,
    tvOnMoveDown: (() -> Boolean)? = null,
) {
    val isTelevision = isTelevisionDevice()
    val focusManager = LocalFocusManager.current
    val tvFieldState = if (isTelevision) {
        rememberTvTextFieldState(tvFocusRequester)
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                tvFieldState?.let { state ->
                    Modifier.tvPassiveTextFieldFocus(
                        state = state,
                        enabled = enabled,
                        onActivate = state::beginEditing,
                        onMoveUp = {
                            state.finishEditing()
                            tvOnMoveUp?.invoke()
                                ?: focusManager.moveFocus(FocusDirection.Up)
                        },
                        onMoveDown = {
                            state.finishEditing()
                            tvOnMoveDown?.invoke()
                                ?: focusManager.moveFocus(FocusDirection.Down)
                        }
                    )
                } ?: Modifier
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = false,
            maxLines = maxLines,
            enabled = enabled,
            readOnly = tvFieldState != null && !tvFieldState.isEditing,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = tvFieldState?.interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = if (isTelevision) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                focusedLabelColor = if (isTelevision) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                cursorColor = MaterialTheme.colorScheme.secondary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    tvFocusRequester: FocusRequester? = null,
    tvOnMoveUp: (() -> Boolean)? = null,
    tvOnMoveDown: (() -> Boolean)? = null,
    tvOnLongPress: (() -> Unit)? = null,
    tvOnDrop: () -> Unit = {},
    tvOnMovementKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var restoreFieldFocus by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = isTelevisionDevice()
    val tvFieldState = if (isTelevision) {
        rememberTvTextFieldState(tvFocusRequester)
    } else {
        null
    }
    val selectedOptionFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val selectedOptionIndex = options.indexOf(value).takeIf { it >= 0 } ?: 0

    fun dismissMenu() {
        expanded = false
        restoreFieldFocus = isTelevision
    }

    fun activateTvField() {
        val state = tvFieldState ?: return
        if (editable) {
            state.beginEditing()
        } else {
            keyboardController?.hide()
            if (expanded) dismissMenu() else expanded = true
        }
    }

    LaunchedEffect(expanded, restoreFieldFocus, selectedOptionIndex) {
        when {
            expanded && options.isNotEmpty() && selectedOptionFocusRequester != null ->
                requestFocusWhenReady(selectedOptionFocusRequester)

            !expanded && restoreFieldFocus && tvFieldState != null -> {
                tvFieldState.finishEditing()
                requestFocusWhenReady(tvFieldState.passiveFocusRequester)
                restoreFieldFocus = false
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { newExpanded ->
            if (!enabled) return@ExposedDropdownMenuBox
            if (!editable && newExpanded) {
                keyboardController?.hide()
            }
            if (newExpanded) expanded = true else dismissMenu()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (isTelevision && tvOnLongPress != null) {
                    Modifier.dpadLongPressToMove(
                        enabled = true,
                        onClick = ::activateTvField,
                        onLongPress = tvOnLongPress,
                        onDrop = tvOnDrop,
                        onMovementKeyEvent = tvOnMovementKeyEvent,
                        addFocusTarget = false
                    )
                } else {
                    Modifier
                }
            )
            .then(
                tvFieldState?.let { state ->
                    Modifier.tvPassiveTextFieldFocus(
                        state = state,
                        enabled = enabled,
                        onActivate = ::activateTvField,
                        onMoveUp = {
                            state.finishEditing()
                            tvOnMoveUp?.invoke()
                                ?: focusManager.moveFocus(FocusDirection.Up)
                        },
                        onMoveDown = {
                            state.finishEditing()
                            tvOnMoveDown?.invoke()
                                ?: focusManager.moveFocus(FocusDirection.Down)
                        }
                    )
                } ?: Modifier
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable || (tvFieldState != null && !tvFieldState.isEditing),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { if (placeholder != null) Text(placeholder) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            interactionSource = tvFieldState?.interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = if (isTelevision) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                focusedLabelColor = if (isTelevision) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                cursorColor = MaterialTheme.colorScheme.secondary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ),
            modifier = Modifier
                .then(
                    tvFieldState?.let {
                        Modifier.tvTextFieldEditorFocus(it, enabled = editable)
                    } ?: Modifier
                )
                .menuAnchor(
                    type = if (editable) ExposedDropdownMenuAnchorType.PrimaryEditable
                    else ExposedDropdownMenuAnchorType.PrimaryNotEditable
                )
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!editable && focusState.isFocused) {
                        keyboardController?.hide()
                    }
                }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = ::dismissMenu,
            modifier = Modifier.verticalScrollbar(menuScrollState),
            scrollState = menuScrollState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEachIndexed { index, option ->
                AppDropdownMenuItem(
                    text = option,
                    modifier = if (
                        index == selectedOptionIndex && selectedOptionFocusRequester != null
                    ) {
                        Modifier.focusRequester(selectedOptionFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = {
                        onValueChange(option)
                        dismissMenu()
                        if (!isTelevision) focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
