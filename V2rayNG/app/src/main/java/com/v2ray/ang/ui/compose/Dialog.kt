@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun DeleteConfirmDialog(title: String? = null, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val dismissFocusRequester = rememberDpadFocusRequester()
    val isTelevision = isTelevisionDevice()
    val deleteText = stringResource(R.string.action_delete)
    val cancelText = stringResource(android.R.string.cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (isTelevision) {
                TvDialogButton(
                    text = deleteText,
                    icon = painterResource(R.drawable.ic_delete_24dp),
                    onClick = { onConfirm(); onDismiss() }
                )
            } else {
                TextButton(onClick = { onConfirm(); onDismiss() }) { Text(deleteText) }
            }
        },
        dismissButton = {
            if (isTelevision) {
                TvDialogButton(text = cancelText, onClick = onDismiss, focusRequester = dismissFocusRequester)
            } else {
                TextButton(onClick = onDismiss) { Text(cancelText) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TvDialogButton(
    text: String,
    icon: Painter? = null,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 96.dp)
            .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 24.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val isTelevision = isTelevisionDevice()
    val firstFieldFocusRequester = rememberDpadFocusRequester(requestFocus = isTelevision && fields.isNotEmpty())
    val remainingFieldFocusRequesters = remember(fields.size) {
        List((fields.size - 1).coerceAtLeast(0)) { FocusRequester() }
    }
    val fieldFocusRequesters = remember(firstFieldFocusRequester, remainingFieldFocusRequesters) {
        if (fields.isEmpty()) {
            emptyList()
        } else {
            listOf(firstFieldFocusRequester) + remainingFieldFocusRequesters
        }
    }
    val dismissFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    val buttonFocusOrder = remember(dismissFocusRequester, confirmFocusRequester) {
        listOf(dismissFocusRequester, confirmFocusRequester)
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isTelevision) {
        LaunchedEffect(Unit) {
            keyboardController?.hide()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { index, field ->
                    val tvFieldState = if (isTelevision) {
                        rememberTvTextFieldState(passiveFocusRequester = fieldFocusRequesters[index])
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvAwareTextFieldFocus(
                                state = tvFieldState,
                                enabled = true,
                                navigation = TvTextFieldNavigation(
                                    onMoveUp = {
                                        if (index > 0) {
                                            fieldFocusRequesters[index - 1].requestFocus()
                                        } else {
                                            true
                                        }
                                    },
                                    onMoveDown = {
                                        fieldFocusRequesters.getOrNull(index + 1)
                                            ?.requestFocus()
                                            ?: dismissFocusRequester.requestFocus()
                                    }
                                ),
                                onActivate = { tvFieldState?.beginEditing() }
                            )
                    ) {
                        TvAwareOutlinedTextField(
                            value = field.value,
                            onValueChange = { onFieldChange(index, it) },
                            spec = TvAwareTextFieldSpec(
                                label = field.label,
                                singleLine = field.singleLine,
                                maxLines = if (field.singleLine) 1 else 5,
                                visualTransformation = field.visualTransformation
                            ),
                            tvFieldState = tvFieldState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) } ?: Modifier)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .dpadFocusOutline(focusRequester = confirmFocusRequester)
                    .dpadOrderedFocusNavigation(current = confirmFocusRequester, order = buttonFocusOrder)
                    .dpadVerticalFocusNavigation(
                        onMoveUp = {
                            fieldFocusRequesters.lastOrNull()?.requestFocus() ?: true
                        },
                        onMoveDown = { true }
                    )
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .dpadFocusOutline(focusRequester = dismissFocusRequester)
                    .dpadOrderedFocusNavigation(current = dismissFocusRequester, order = buttonFocusOrder)
                    .dpadVerticalFocusNavigation(
                        onMoveUp = {
                            fieldFocusRequesters.lastOrNull()?.requestFocus() ?: true
                        },
                        onMoveDown = { true }
                    )
            ) { Text(dismissText) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun QRCodeDialog(bitmap: Bitmap?, onDismiss: () -> Unit) {
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
                TvDialogButton(text = closeText, onClick = onDismiss, focusRequester = closeFocusRequester)
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
                        TvDialogButton(
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
                        val selectionModifier = if (showRadio) {
                            Modifier.selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelected(option) }
                            )
                        } else {
                            Modifier.clickable { onSelected(option) }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusOutline(
                                    focusRequester = if (index == initialIndex) firstOptionFocusRequester else null
                                )
                                .then(selectionModifier)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showRadio) {
                                RadioButton(selected = isSelected, onClick = null)
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
                TvDialogButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
