@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

data class FormDropdownConfig(
    val editable: Boolean = false,
    val placeholder: String? = null,
    val supportingText: String? = null,
    val emptyOptionLabel: String? = null
)

@Stable
class FormDropdownState internal constructor() {
    var expanded by mutableStateOf(false)
        internal set
    internal var restoreFieldFocus by mutableStateOf(false)

    fun open() {
        restoreFieldFocus = false
        expanded = true
    }

    fun close(restoreFocus: Boolean = true) {
        expanded = false
        restoreFieldFocus = restoreFocus
    }

    fun toggle() {
        if (expanded) close() else open()
    }
}

@Composable
fun rememberFormDropdownState(): FormDropdownState = remember { FormDropdownState() }

internal fun formDropdownMenuOptions(
    options: List<String>,
    emptyOptionLabel: String?
): List<String> = if (emptyOptionLabel == null) options else listOf("") + options

internal fun formDropdownSelectedOptionIndex(
    menuOptions: List<String>,
    value: String
): Int = menuOptions.indexOf(value).takeIf { it >= 0 } ?: 0

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
    tvNavigation: TvTextFieldNavigation = TvTextFieldNavigation()
) {
    val isTelevision = isTelevisionDevice()
    val tvFieldState = if (isTelevision) {
        rememberTvTextFieldState(tvNavigation.focusRequester)
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .tvAwareTextFieldFocus(
                state = tvFieldState,
                enabled = enabled,
                navigation = tvNavigation,
                onActivate = { tvFieldState?.beginEditing() }
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            spec = OutlinedTextFieldSpec(
                label = label,
                placeholder = placeholder,
                enabled = enabled,
                maxLines = maxLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            ),
            tvFieldState = tvFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier)
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
    enabled: Boolean = true,
    config: FormDropdownConfig = FormDropdownConfig(),
    tvNavigation: TvTextFieldNavigation = TvTextFieldNavigation(),
    state: FormDropdownState? = null
) {
    val dropdownState = state ?: remember { FormDropdownState() }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = isTelevisionDevice()
    val tvFieldState = if (isTelevision) {
        rememberTvTextFieldState(tvNavigation.focusRequester)
    } else {
        null
    }
    val selectedOptionFocusRequester = if (isTelevision) remember { FocusRequester() } else null
    val dropdownButtonFocusRequester = if (isTelevision && config.editable) remember { FocusRequester() } else null
    val usesTvSelectionDialog = isTelevision && config.editable
    val menuOptions = formDropdownMenuOptions(options, config.emptyOptionLabel)
    val selectedOptionIndex = formDropdownSelectedOptionIndex(menuOptions, value)
    val displayedValue = if (!config.editable && value.isEmpty()) {
        config.emptyOptionLabel.orEmpty()
    } else {
        value
    }

    fun dismissMenu() {
        dropdownState.close(restoreFocus = isTelevision)
    }

    fun activateTvField() {
        val textFieldState = tvFieldState ?: return
        if (config.editable) {
            textFieldState.beginEditing()
        } else {
            keyboardController?.hide()
            if (dropdownState.expanded) dismissMenu() else dropdownState.open()
        }
    }

    fun openTvDropdown() {
        keyboardController?.hide()
        tvFieldState?.finishEditing()
        dropdownState.open()
    }

    val resolvedTvNavigation = if (dropdownButtonFocusRequester == null) {
        tvNavigation
    } else {
        tvNavigation.copy(onMoveNext = dropdownButtonFocusRequester::requestFocus)
    }

    LaunchedEffect(dropdownState.expanded, dropdownState.restoreFieldFocus, selectedOptionIndex) {
        when {
            dropdownState.expanded &&
                !usesTvSelectionDialog &&
                menuOptions.isNotEmpty() &&
                selectedOptionFocusRequester != null ->
                requestFocusWhenReady(selectedOptionFocusRequester)

            !dropdownState.expanded &&
                dropdownState.restoreFieldFocus &&
                tvFieldState != null -> {
                tvFieldState.finishEditing()
                requestFocusWhenReady(tvFieldState.passiveFocusRequester)
                dropdownState.restoreFieldFocus = false
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = dropdownState.expanded && !usesTvSelectionDialog,
            onExpandedChange = { newExpanded ->
                if (!enabled) return@ExposedDropdownMenuBox
                if (!config.editable && newExpanded) keyboardController?.hide()
                if (newExpanded) dropdownState.open() else dismissMenu()
            },
            modifier = Modifier
                .weight(1f)
                .tvAwareTextFieldFocus(
                state = tvFieldState,
                enabled = enabled,
                navigation = resolvedTvNavigation,
                onActivate = ::activateTvField
            )
        ) {
            OutlinedTextField(
                value = displayedValue,
                onValueChange = { if (config.editable) onValueChange(it) },
                spec = OutlinedTextFieldSpec(
                    label = label,
                    placeholder = config.placeholder,
                    supportingText = config.supportingText,
                    enabled = enabled,
                    readOnly = !config.editable
                ),
                tvFieldState = tvFieldState,
                trailingIcon = if (dropdownButtonFocusRequester == null) {
                    {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.expanded)
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .then(tvFieldState?.let {
                        Modifier.tvTextFieldEditorFocus(it, enabled = config.editable)
                    } ?: Modifier)
                    .menuAnchor(
                        type = if (config.editable) ExposedDropdownMenuAnchorType.PrimaryEditable
                        else ExposedDropdownMenuAnchorType.PrimaryNotEditable
                    )
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!config.editable && focusState.isFocused) keyboardController?.hide()
                    }
            )
            if (!usesTvSelectionDialog) {
                ExposedDropdownMenu(
                    expanded = dropdownState.expanded,
                    onDismissRequest = ::dismissMenu,
                    modifier = Modifier.verticalScrollbar(menuScrollState),
                    scrollState = menuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    menuOptions.forEachIndexed { index, option ->
                        AppDropdownMenuItem(
                            text = if (option.isEmpty() && config.emptyOptionLabel != null) {
                                config.emptyOptionLabel
                            } else {
                                option
                            },
                            modifier = if (index == selectedOptionIndex && selectedOptionFocusRequester != null) {
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
        if (dropdownButtonFocusRequester != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(48.dp)
                    .dpadFocusOutline(focusRequester = dropdownButtonFocusRequester, cornerRadius = 24.dp)
                    .dpadLogicalHorizontalNavigation(
                        onMovePrevious = { tvFieldState?.passiveFocusRequester?.requestFocus() },
                        onMoveNext = {
                            tvNavigation.onMoveNext?.invoke() ?: dropdownButtonFocusRequester.requestFocus()
                        }
                    )
                    .dpadVerticalFocusNavigation(
                        onMoveUp = {
                            tvNavigation.onMoveUp?.invoke() ?: focusManager.moveFocus(FocusDirection.Up)
                        },
                        onMoveDown = {
                            tvNavigation.onMoveDown?.invoke() ?: focusManager.moveFocus(FocusDirection.Down)
                        }
                    )
                    .dpadClickable(role = Role.Button, onClick = ::openTvDropdown),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down_24dp),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = if (dropdownState.expanded) 180f else 0f }
                )
            }
        }
    }
    if (usesTvSelectionDialog && dropdownState.expanded) {
        val displayOptions = menuOptions.map {
            if (it.isEmpty() && config.emptyOptionLabel != null) config.emptyOptionLabel else it
        }
        SelectListDialog(
            title = label,
            options = displayOptions,
            selectedOption = displayOptions.getOrElse(selectedOptionIndex) { "" },
            showRadio = true,
            onSelected = { index, _ ->
                onValueChange(menuOptions[index])
                dismissMenu()
            },
            onDismiss = ::dismissMenu
        )
    }
}
