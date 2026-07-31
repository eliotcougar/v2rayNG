package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDialogButton
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppRowSwitch
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.TvExpandableSwitch
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadHorizontalFocusNavigation
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> removeSub(subId) },
            onShareQRCode = { url -> QRCodeDecoder.createQRCode(url) },
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
    }
}

private class SubscriptionRowFocusTargets {
    val row = FocusRequester()
    val share = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
    val toggle = FocusRequester()
}

@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Unit
) {
    val isTelevision = isTelevisionDevice()
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    val subscriptionIds = subscriptions.map { it.guid }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val rowFocusTargets = remember(subscriptionIds.toSet()) {
        subscriptions.associate { it.guid to SubscriptionRowFocusTargets() }
    }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    val confirmRemove = isTelevision ||
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderIndicesForKeys(subscriptionIds, from.key, to.key)?.let { (fromIndex, toIndex) ->
            viewModel.move(fromIndex, toIndex)
        }
    }

    val dpadReorderState = rememberSyncedDpadReorderState(subscriptionIds, isTelevision) { key, index ->
        val id = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) lazyListState.keepDpadReorderItemVisible(id, index)
        rowFocusTargets[id]?.row?.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_sub_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                onMoveDown = {
                    subscriptions.firstOrNull()?.let {
                        rowFocusTargets[it.guid]?.row?.requestFocus()
                    } ?: false
                },
                actionItems = listOf(
                    AppTopBarAction(
                        icon = painterResource(R.drawable.ic_add_24dp),
                        label = stringResource(R.string.acc_add_subscription),
                        onClick = onAddClick
                    ),
                    AppTopBarAction(
                        icon = painterResource(R.drawable.ic_cloud_download_24dp),
                        label = stringResource(R.string.title_sub_update),
                        onClick = { showUpdateDialog = true }
                    )
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState),
            contentPadding = if (isTelevision) {
                PaddingValues(horizontal = 48.dp, vertical = 12.dp)
            } else {
                NavigationBarsBottomPadding()
            }
        ) {
            itemsIndexed(items = subscriptions, key = { _, item -> item.guid }) { index, subCache ->
                val focusTargets = rowFocusTargets.getValue(subCache.guid)
                val previousSub = subscriptions.getOrNull(index - 1)
                val previousTargets = previousSub?.let { rowFocusTargets[it.guid] }
                val nextSub = subscriptions.getOrNull(index + 1)
                val nextTargets = nextSub?.let { rowFocusTargets[it.guid] }
                val dpadReorderItem = DpadReorderItem(
                    state = dpadReorderState,
                    key = subCache.guid,
                    index = index,
                    itemCount = subscriptions.size,
                    targetIndex = ::verticalDpadReorderTarget,
                    onMove = viewModel::move
                )
                val isMoving = dpadReorderState.isMoving(subCache.guid)
                val actionFocusOrder = remember(focusTargets, subCache.subscription.url) {
                    buildList {
                        add(focusTargets.row)
                        if (subCache.subscription.url.isNotEmpty()) {
                            add(focusTargets.share)
                        }
                        add(focusTargets.edit)
                        add(focusTargets.delete)
                        add(focusTargets.toggle)
                    }
                }
                ReorderableItem(reorderableState, key = subCache.guid, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                    ReorderableListItem(scope = this, isDragging = isDragging, isMoving = isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTelevision) {
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                                            .dpadFocusOutline(
                                                focusRequester = focusTargets.row,
                                                cornerRadius = 16.dp,
                                                showFocus = !isMoving
                                            )
                                            .dpadOrderedFocusNavigation(focusTargets.row, actionFocusOrder)
                                            .dpadVerticalFocusNavigation(
                                                onMoveUp = {
                                                    previousTargets?.row?.requestFocus() ?: false
                                                },
                                                onMoveDown = {
                                                    nextTargets?.row?.requestFocus() ?: true
                                                }
                                            )
                                            .dpadLongPressToMove(
                                                enabled = true,
                                                item = dpadReorderItem,
                                                onClick = { onEditSub(subCache.guid) }
                                            )
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                    } else {
                                        Modifier.padding(horizontal = 14.dp)
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subCache.subscription.remarks,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subCache.subscription.url.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subCache.subscription.url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Utils.formatTimestamp(subCache.subscription.lastUpdated),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isTelevision) {
                                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (subCache.subscription.url.isNotEmpty()) {
                                        AppIconButton(
                                            icon = painterResource(R.drawable.ic_share_24dp),
                                            label = stringResource(R.string.action_share),
                                            focusRequester = focusTargets.share,
                                            modifier = Modifier.dpadOrderedFocusNavigation(
                                                current = focusTargets.share,
                                                order = actionFocusOrder
                                            ).dpadVerticalFocusNavigation(
                                                onMoveUp = {
                                                    if (previousSub?.subscription?.url?.isNotEmpty() == true) {
                                                        previousTargets?.share?.requestFocus() ?: false
                                                    } else {
                                                        previousTargets?.edit?.requestFocus() ?: false
                                                    }
                                                },
                                                onMoveDown = {
                                                    if (nextSub?.subscription?.url?.isNotEmpty() == true) {
                                                        nextTargets?.share?.requestFocus() ?: true
                                                    } else {
                                                        nextTargets?.edit?.requestFocus() ?: true
                                                    }
                                                }
                                            ),
                                            onClick = {
                                                shareTarget = Pair(subCache.guid, subCache.subscription.url)
                                            }
                                        )
                                    }
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_edit_24dp),
                                        label = stringResource(R.string.action_edit),
                                        focusRequester = focusTargets.edit,
                                        modifier = Modifier.dpadOrderedFocusNavigation(
                                            current = focusTargets.edit,
                                            order = actionFocusOrder
                                        ).dpadVerticalFocusNavigation(
                                            onMoveUp = { previousTargets?.edit?.requestFocus() ?: false },
                                            onMoveDown = { nextTargets?.edit?.requestFocus() ?: true }
                                        ),
                                        onClick = { onEditSub(subCache.guid) }
                                    )
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_delete_24dp),
                                        label = stringResource(R.string.action_delete),
                                        focusRequester = focusTargets.delete,
                                        modifier = Modifier.dpadRowActionNavigation(
                                            current = focusTargets.delete,
                                            order = actionFocusOrder,
                                            previousRow = previousTargets?.delete,
                                            nextRow = nextTargets?.delete
                                        ),
                                        onClick = {
                                            if (confirmRemove) removeTarget = subCache.guid
                                            else onRemoveSub(subCache.guid)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppRowSwitch(
                                        checked = subCache.subscription.enabled,
                                        onCheckedChange = { checked ->
                                            val updated = subCache.subscription.copy()
                                            updated.enabled = checked
                                            viewModel.update(subCache.guid, updated)
                                        },
                                        label = stringResource(R.string.sub_setting_enable),
                                        focusRequester = focusTargets.toggle,
                                        modifier = Modifier.dpadRowActionNavigation(
                                            current = focusTargets.toggle,
                                            order = actionFocusOrder,
                                            previousRow = previousTargets?.toggle,
                                            nextRow = nextTargets?.toggle
                                        )
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                                    Row {
                                        if (subCache.subscription.url.isNotEmpty()) {
                                            AppIconButton(
                                                icon = painterResource(R.drawable.ic_share_24dp),
                                                label = stringResource(R.string.action_share),
                                                onClick = {
                                                    shareTarget = Pair(subCache.guid, subCache.subscription.url)
                                                }
                                            )
                                        }
                                        AppIconButton(
                                            icon = painterResource(R.drawable.ic_edit_24dp),
                                            label = stringResource(R.string.action_edit),
                                            onClick = { onEditSub(subCache.guid) }
                                        )
                                        AppIconButton(
                                            icon = painterResource(R.drawable.ic_delete_24dp),
                                            label = stringResource(R.string.action_delete),
                                            onClick = {
                                                if (confirmRemove) removeTarget = subCache.guid
                                                else onRemoveSub(subCache.guid)
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Switch(
                                        checked = subCache.subscription.enabled,
                                        onCheckedChange = { checked ->
                                            val updated = subCache.subscription.copy()
                                            updated.enabled = checked
                                            viewModel.update(subCache.guid, updated)
                                        },
                                        modifier = Modifier.scale(0.7f),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (!isTelevision) {
                        ItemDivider()
                    }
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareTarget = null
                when (action) {
                    SubscriptionShareAction.QRCode -> showQRCodeBitmap = onShareQRCode(url)
                    SubscriptionShareAction.Clipboard -> onShareClipboard(url)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    // QR Code Dialog
    if (showQRCodeBitmap != null) {
        QRCodeDialog(bitmap = showQRCodeBitmap, onDismiss = { showQRCodeBitmap = null })
    }

    if (removeTarget != null) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            onConfirm = {
                onRemoveSub(removeTarget!!)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {

        var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        var autoTestAfterUpdateSubscription by rememberMmkvBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        var autoRemoveInvalidAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)
        val dismissFocusRequester = rememberDpadFocusRequester()
        val confirmFocusRequester = remember { FocusRequester() }
        val buttonFocusOrder = remember { listOf(dismissFocusRequester, confirmFocusRequester) }

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_sub_update),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                        summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                        checked = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoTestAfterUpdateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                        summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                        checked = autoRemoveInvalidAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoRemoveInvalidAfterTest = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_sort_after_test),
                        summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                        checked = autoSortAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoSortAfterTest = it }
                    )
                }
            },
            confirmButton = {
                AppDialogButton(
                    text = stringResource(R.string.action_ok),
                    onClick = {
                        showUpdateDialog = false
                        onSubUpdate()
                    },
                    focusRequester = confirmFocusRequester,
                    modifier = Modifier.dpadOrderedFocusNavigation(confirmFocusRequester, buttonFocusOrder)
                )
            },
            dismissButton = {
                AppDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showUpdateDialog = false },
                    focusRequester = dismissFocusRequester,
                    modifier = Modifier.dpadOrderedFocusNavigation(dismissFocusRequester, buttonFocusOrder)
                )
            }
        )
    }
}

