package com.v2ray.ang.ui.compose

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLines: Int = 5,
    tvNavigation: TvTextFieldNavigation = TvTextFieldNavigation(),
) {
    val tvFieldState = if (isTelevisionDevice()) rememberTvTextFieldState(tvNavigation.focusRequester) else null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .dpadTextFieldFocusOutline()
            .tvAwareTextFieldFocus(tvFieldState, enabled, tvNavigation) { tvFieldState?.beginEditing() }
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
            interactionSource = tvFieldState?.interactionSource,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    emptyOptionLabel: String? = null,
    tvNavigation: TvTextFieldNavigation = TvTextFieldNavigation(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var restoreFieldFocus by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = isTelevisionDevice()
    val tvFieldState = if (isTelevision) rememberTvTextFieldState(tvNavigation.focusRequester) else null
    val selectedOptionFocusRequester = remember { FocusRequester() }
    val menuOptions = if (emptyOptionLabel == null) options else listOf("") + options
    val selectedOptionIndex = menuOptions.indexOf(value).takeIf { it >= 0 } ?: 0
    val displayedValue = if (!editable && value.isEmpty()) emptyOptionLabel.orEmpty() else value

    fun openDropdown() {
        restoreFieldFocus = false
        keyboardController?.hide()
        expanded = true
    }

    fun closeDropdown() {
        expanded = false
        restoreFieldFocus = isTelevision
    }

    LaunchedEffect(expanded, restoreFieldFocus, selectedOptionIndex) {
        when {
            expanded && isTelevision && menuOptions.isNotEmpty() -> requestFocusWhenReady(selectedOptionFocusRequester)
            !expanded && restoreFieldFocus && tvFieldState != null -> {
                requestFocusWhenReady(tvFieldState.passiveFocusRequester)
                restoreFieldFocus = false
            }
        }
    }

    val resolvedTvNavigation = if (editable && isTelevision) {
        tvNavigation.copy(onMoveNext = {
            openDropdown()
            true
        })
    } else tvNavigation

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { newExpanded ->
            if (!enabled) return@ExposedDropdownMenuBox
            if (!editable && newExpanded) {
                keyboardController?.hide()
            }
            if (newExpanded) openDropdown() else closeDropdown()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .dpadTextFieldFocusOutline()
            .tvAwareTextFieldFocus(tvFieldState, enabled, resolvedTvNavigation) {
                if (editable) tvFieldState?.beginEditing()
                else if (expanded) closeDropdown() else openDropdown()
            }
    ) {
        OutlinedTextField(
            value = displayedValue,
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
                cursorColor = MaterialTheme.colorScheme.secondary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondary,
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            ),
            modifier = Modifier
                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it, enabled = editable) } ?: Modifier)
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
            onDismissRequest = ::closeDropdown,
            modifier = Modifier.verticalScrollbar(menuScrollState),
            scrollState = menuScrollState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            menuOptions.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(if (option.isEmpty() && emptyOptionLabel != null) emptyOptionLabel else option) },
                    onClick = {
                        onValueChange(option)
                        closeDropdown()
                        if (!isTelevision) focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .then(if (index == selectedOptionIndex) Modifier.focusRequester(selectedOptionFocusRequester) else Modifier)
                        .dpadFocusOutline()
                )
            }
        }
    }
}
