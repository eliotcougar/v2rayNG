@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class FormDropdownConfig(
    val editable: Boolean = false,
    val placeholder: String? = null,
    val supportingText: String? = null,
    val emptyOptionLabel: String? = null
)

@Stable
class FormDropdownState internal constructor(initialExpanded: Boolean = false) {
    var expanded by mutableStateOf(initialExpanded)
        internal set
    internal var restoreFieldFocus by mutableStateOf(false)
    internal var activationHandler: (() -> Unit)? = null

    fun activate() {
        activationHandler?.invoke()
    }

    companion object {
        internal val Saver = Saver<FormDropdownState, Boolean>(
            save = { it.expanded },
            restore = { FormDropdownState(initialExpanded = it) }
        )
    }
}

@Composable
fun rememberFormDropdownState(): FormDropdownState =
    rememberSaveable(saver = FormDropdownState.Saver) { FormDropdownState() }

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
        TvAwareOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            spec = TvAwareTextFieldSpec(
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
    val ownedState = rememberFormDropdownState()
    val dropdownState = state ?: ownedState
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
    val menuOptions = formDropdownMenuOptions(options, config.emptyOptionLabel)
    val selectedOptionIndex = formDropdownSelectedOptionIndex(menuOptions, value)
    val displayedValue = if (!config.editable && value.isEmpty()) {
        config.emptyOptionLabel.orEmpty()
    } else {
        value
    }

    fun dismissMenu() {
        dropdownState.expanded = false
        dropdownState.restoreFieldFocus = isTelevision
    }

    fun activateTvField() {
        val textFieldState = tvFieldState ?: return
        if (config.editable) {
            textFieldState.beginEditing()
        } else {
            keyboardController?.hide()
            if (dropdownState.expanded) dismissMenu() else dropdownState.expanded = true
        }
    }

    SideEffect {
        dropdownState.activationHandler = ::activateTvField
    }
    DisposableEffect(dropdownState) {
        onDispose { dropdownState.activationHandler = null }
    }

    LaunchedEffect(dropdownState.expanded, dropdownState.restoreFieldFocus, selectedOptionIndex) {
        when {
            dropdownState.expanded &&
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

    ExposedDropdownMenuBox(
        expanded = dropdownState.expanded,
        onExpandedChange = { newExpanded ->
            if (!enabled) return@ExposedDropdownMenuBox
            if (!config.editable && newExpanded) {
                keyboardController?.hide()
            }
            if (newExpanded) dropdownState.expanded = true else dismissMenu()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .tvAwareTextFieldFocus(
                state = tvFieldState,
                enabled = enabled,
                navigation = tvNavigation,
                onActivate = ::activateTvField
            )
    ) {
        TvAwareOutlinedTextField(
            value = displayedValue,
            onValueChange = { if (config.editable) onValueChange(it) },
            spec = TvAwareTextFieldSpec(
                label = label,
                placeholder = config.placeholder,
                supportingText = config.supportingText,
                enabled = enabled,
                readOnly = !config.editable
            ),
            tvFieldState = tvFieldState,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownState.expanded)
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
                    if (!config.editable && focusState.isFocused) {
                        keyboardController?.hide()
                    }
                }
        )
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
