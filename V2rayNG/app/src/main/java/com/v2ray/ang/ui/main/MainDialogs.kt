package com.v2ray.ang.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DeleteConfirmDialog

@Composable
internal fun MainDialogs(dialog: MainDialog?, onConfirm: (MainDialog) -> Unit, onDismiss: () -> Unit) {
    val message = when (dialog) {
        MainDialog.DeleteAll -> stringResource(R.string.confirm_delete_visible_profiles)
        MainDialog.DeleteDuplicate -> stringResource(R.string.confirm_delete_duplicate_profiles)
        MainDialog.DeleteInvalid -> stringResource(R.string.confirm_delete_invalid_profiles)
        is MainDialog.DeleteServer -> stringResource(R.string.confirm_delete_profile)
        is MainDialog.Share, null -> return
    }
    DeleteConfirmDialog(message = message, onConfirm = { onConfirm(dialog) }, onDismiss = onDismiss)
}
