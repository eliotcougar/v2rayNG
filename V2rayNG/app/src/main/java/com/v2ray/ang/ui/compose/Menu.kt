package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun <T> AppDropdownMenuItems(
    items: List<T>,
    labelRes: (T) -> Int,
    itemModifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    val firstFocusRequester = rememberDpadFocusRequester(requestFocus = items.isNotEmpty())
    items.forEachIndexed { index, item ->
        Box(modifier = itemModifier.fillMaxWidth()) {
            DropdownMenuItem(
                text = { Text(stringResource(labelRes(item))) },
                onClick = { onSelected(item) },
                modifier = Modifier.dpadFocusOutline(if (index == 0) firstFocusRequester else null)
            )
        }
    }
}
