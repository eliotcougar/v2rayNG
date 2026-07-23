package com.v2ray.ang.ui.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
) {
    val isTelevision = isTelevisionDevice()
    var isEditing by remember { mutableStateOf(!isTelevision) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val editorFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(isEditing) {
            if (isEditing) {
                editorFocusRequester.requestFocus()
                keyboardController?.show()
            } else {
                keyboardController?.hide()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (isTelevision) {
                    Modifier
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                                !isEditing
                            ) {
                                isEditing = true
                                true
                            } else {
                                false
                            }
                        }
                        .dpadTextFieldNavigation(
                            onMoveUp = {
                                isEditing = false
                                focusManager.moveFocus(FocusDirection.Up)
                            },
                            onMoveDown = {
                                isEditing = false
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        )
                        .focusable(
                            enabled = enabled && !isEditing,
                            interactionSource = interactionSource
                        )
                } else {
                    Modifier
                }
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
            readOnly = isTelevision && !isEditing,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.secondary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTelevision) {
                        Modifier
                            .focusRequester(editorFocusRequester)
                            .focusProperties { canFocus = isEditing }
                            .onFocusChanged {
                                if (isEditing && !it.isFocused) {
                                    isEditing = false
                                }
                            }
                    } else {
                        Modifier
                    }
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
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = isTelevisionDevice()
    var isEditing by remember { mutableStateOf(!isTelevision) }
    val interactionSource = remember { MutableInteractionSource() }
    val editorFocusRequester = remember { FocusRequester() }

    if (isTelevision) {
        LaunchedEffect(isEditing) {
            if (isEditing) {
                editorFocusRequester.requestFocus()
                keyboardController?.show()
            } else {
                keyboardController?.hide()
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
            expanded = newExpanded
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (isTelevision) {
                    Modifier
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type != KeyEventType.KeyDown ||
                                (event.key != Key.DirectionCenter && event.key != Key.Enter)
                            ) {
                                return@onPreviewKeyEvent false
                            }
                            if (editable && !isEditing) {
                                isEditing = true
                            } else if (!editable) {
                                keyboardController?.hide()
                                expanded = !expanded
                            } else {
                                return@onPreviewKeyEvent false
                            }
                            true
                        }
                        .dpadTextFieldNavigation(
                            onMoveUp = {
                                isEditing = false
                                focusManager.moveFocus(FocusDirection.Up)
                            },
                            onMoveDown = {
                                isEditing = false
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        )
                        .focusable(
                            enabled = enabled && !isEditing,
                            interactionSource = interactionSource
                        )
                } else {
                    Modifier
                }
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable || (isTelevision && !isEditing),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { if (placeholder != null) Text(placeholder) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.secondary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ),
            modifier = Modifier
                .then(
                    if (isTelevision) {
                        Modifier
                            .focusRequester(editorFocusRequester)
                            .focusProperties { canFocus = editable && isEditing }
                            .onFocusChanged {
                                if (isEditing && !it.isFocused) {
                                    isEditing = false
                                }
                            }
                    } else {
                        Modifier
                    }
                )
                .menuAnchor(
                    type = if (editable) ExposedDropdownMenuAnchorType.PrimaryEditable
                    else ExposedDropdownMenuAnchorType.PrimaryNotEditable
                )
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && isTelevision) {
                        isEditing = false
                    }
                    if (!editable && focusState.isFocused) {
                        keyboardController?.hide()
                    }
                }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.verticalScrollbar(menuScrollState),
            scrollState = menuScrollState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    modifier = Modifier.tvMenuItemFocus(),
                    onClick = {
                        onValueChange(option)
                        expanded = false
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
