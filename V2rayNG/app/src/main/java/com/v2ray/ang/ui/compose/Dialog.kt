@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    confirmIcon: @Composable (() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissFocusRequester = rememberDpadFocusRequester()
    val isTelevision = isTelevisionDevice()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (isTelevision) {
                TvConfirmDialogButton(
                    text = stringResource(R.string.action_delete),
                    icon = painterResource(R.drawable.ic_delete_24dp),
                    onClick = { onConfirm(); onDismiss() }
                )
            } else {
                TextButton(onClick = { onConfirm(); onDismiss() }) {
                    confirmIcon?.invoke()
                    if (confirmIcon != null) Spacer(Modifier.width(8.dp))
                    Text(confirmText)
                }
            }
        },
        dismissButton = dismissText?.let { text ->
            {
                if (isTelevision) {
                    TvConfirmDialogButton(
                        text = text,
                        onClick = onDismiss,
                        focusRequester = dismissFocusRequester
                    )
                } else {
                    TextButton(onClick = onDismiss) { Text(text) }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = message,
        confirmText = stringResource(R.string.action_delete),
        confirmIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun TvConfirmDialogButton(
    text: String,
    icon: Painter? = null,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    // Android's en-XC test locale surrounds platform button labels with more than one hundred
    // LRM/RLM controls. They have no visible content but skew Compose's native text measurement.
    // Keep legitimate bidi marks intact and discard only the pseudo-locale's marker flood.
    val directionMarkCount = text.count { it == '\u200e' || it == '\u200f' }
    val displayText = if (directionMarkCount > text.length / 2) {
        text.filterNot { it == '\u200e' || it == '\u200f' }
    } else {
        text
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 96.dp)
            .dpadFocusOutline(
                focusRequester = focusRequester,
                cornerRadius = 24.dp
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = displayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

@Composable
fun InputDialog(
    title: String,
    fields: List<InputField>,
    onFieldChange: (Int, String) -> Unit,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFieldFocusRequester = rememberDpadFocusRequester(requestFocus = fields.isNotEmpty())
    val isTelevision = isTelevisionDevice()
    var editingIndex by remember { mutableIntStateOf(-1) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible

    if (isTelevision) {
        LaunchedEffect(Unit) {
            keyboardController?.hide()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fields.forEachIndexed { index, field ->
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    val rowFocusRequester = remember(index) { FocusRequester() }
                    val passiveFocusRequester =
                        if (index == 0) firstFieldFocusRequester else rowFocusRequester
                    val editorFocusRequester = remember(index) { FocusRequester() }
                    var editorHasFocus by remember(index) { mutableStateOf(false) }
                    var restorePassiveFocus by remember(index) { mutableStateOf(false) }

                    fun beginEditing() {
                        editorHasFocus = false
                        imeWasVisible = false
                        restorePassiveFocus = false
                        editingIndex = index
                    }

                    fun finishEditing(restoreFocus: Boolean = false) {
                        val wasEditing = editingIndex == index || editorHasFocus
                        editorHasFocus = false
                        imeWasVisible = false
                        restorePassiveFocus = restoreFocus
                        if (editingIndex == index) editingIndex = -1
                        if (wasEditing) keyboardController?.hide()
                    }

                    if (isTelevision) {
                        LaunchedEffect(editingIndex, editorHasFocus, isImeVisible) {
                            if (editingIndex == index) {
                                if (editorHasFocus && imeWasVisible && !isImeVisible) {
                                    finishEditing(restoreFocus = true)
                                } else if (editorHasFocus) {
                                    if (isImeVisible) imeWasVisible = true
                                    keyboardController?.show()
                                } else {
                                    editorFocusRequester.requestFocus()
                                }
                            }
                        }
                        LaunchedEffect(editingIndex, restorePassiveFocus) {
                            if (editingIndex != index && restorePassiveFocus) {
                                passiveFocusRequester.requestFocus()
                                restorePassiveFocus = false
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTelevision) {
                                    Modifier
                                        .onPreviewKeyEvent { event ->
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                                                editingIndex != index
                                            ) {
                                                beginEditing()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .dpadTextFieldNavigation(
                                            onMoveUp = {
                                                finishEditing()
                                                focusManager.moveFocus(FocusDirection.Up)
                                            },
                                            onMoveDown = {
                                                finishEditing()
                                                focusManager.moveFocus(FocusDirection.Down)
                                            }
                                        )
                                        .focusRequester(passiveFocusRequester)
                                        .focusable(
                                            enabled = editingIndex != index,
                                            interactionSource = interactionSource
                                        )
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        OutlinedTextField(
                            value = field.value,
                            onValueChange = { onFieldChange(index, it) },
                            label = { Text(field.label) },
                            singleLine = false,
                            maxLines = 5,
                            readOnly = isTelevision && editingIndex != index,
                            visualTransformation = field.visualTransformation,
                            interactionSource = interactionSource,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.secondary,
                                selectionColors = TextSelectionColors(
                                    handleColor = MaterialTheme.colorScheme.secondary,
                                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(
                                        alpha = 0.4f
                                    )
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTelevision) {
                                        Modifier
                                            .focusRequester(editorFocusRequester)
                                            .focusProperties { canFocus = editingIndex == index }
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    editorHasFocus = true
                                                } else if (editorHasFocus) {
                                                    finishEditing()
                                                }
                                            }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.dpadFocusOutline()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.dpadFocusOutline()
            ) { Text(dismissText) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun QRCodeDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return
    val isTelevision = isTelevisionDevice()
    val closeFocusRequester = rememberDpadFocusRequester()
    val closeText = stringResource(R.string.action_close)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.acc_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        },
        confirmButton = {
            if (isTelevision) {
                TvConfirmDialogButton(
                    text = closeText,
                    onClick = onDismiss,
                    focusRequester = closeFocusRequester
                )
            } else {
                TextButton(onClick = onDismiss) { Text(closeText) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * When showRadio is true, displays RadioButton (single selection mode);
 * otherwise, plain clickable list mode.
 * The selectedOption parameter is used to highlight the selected item only when showRadio is true.
 */
@Composable
fun <T> SelectListDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: T? = null,
    showRadio: Boolean = false
) {
    val isTelevision = isTelevisionDevice()
    val selectedIndex = if (showRadio) options.indexOf(selectedOption) else -1
    val initialIndex = selectedIndex.takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val firstOptionFocusRequester = rememberDpadFocusRequester(requestFocus = options.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            LazyColumn(
                state = listState,
                verticalArrangement = if (isTelevision && !showRadio) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Top
                }
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    val isSelected = option == selectedOption
                    if (isTelevision && !showRadio) {
                        TvConfirmDialogButton(
                            text = optionText(option),
                            onClick = { onSelected(option) },
                            focusRequester = if (index == initialIndex) {
                                firstOptionFocusRequester
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusOutline(
                                    focusRequester = if (index == initialIndex) {
                                        firstOptionFocusRequester
                                    } else {
                                        null
                                    }
                                )
                                .then(
                                    if (showRadio) {
                                        Modifier.selectable(
                                            selected = isSelected,
                                            onClick = { onSelected(option) },
                                            role = Role.RadioButton
                                        )
                                    } else {
                                        Modifier.clickable { onSelected(option) }
                                    }
                                )
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showRadio) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = optionText(option),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (isTelevision) {
                TvConfirmDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss
                )
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
