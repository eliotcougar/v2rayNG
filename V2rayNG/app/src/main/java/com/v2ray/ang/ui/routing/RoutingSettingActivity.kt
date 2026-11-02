package com.v2ray.ang.ui.routing

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppRowSwitch
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.AppSelectionMenuAction
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.ReorderCommand
import com.v2ray.ang.ui.compose.reorderAccessibilityActions
import com.v2ray.ang.ui.compose.rememberAccessibilityActionFeedback
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class RoutingMenuAction(@StringRes val labelRes: Int) {
    ImportPredefined(R.string.routing_settings_import_predefined_rulesets),
    ImportClipboard(R.string.routing_settings_import_rulesets_from_clipboard),
    ImportQRCode(R.string.routing_settings_import_rulesets_from_qrcode),
    ExportClipboard(R.string.routing_settings_export_rulesets_to_clipboard)
}

private enum class RoutingPreset(val type: RoutingType, @StringRes val labelRes: Int) {
    ChinaWhitelist(RoutingType.WHITE, R.string.routing_preset_china_whitelist),
    ChinaBlacklist(RoutingType.BLACK, R.string.routing_preset_china_blacklist),
    Global(RoutingType.GLOBAL, R.string.routing_preset_global),
    IranWhitelist(RoutingType.WHITE_IRAN, R.string.routing_preset_iran_whitelist),
    RussiaWhitelist(RoutingType.WHITE_RUSSIA, R.string.routing_preset_russia_whitelist)
}
private const val ROUTING_LIST_HEADER_COUNT = 1

class RoutingSettingActivity : HelperBaseComponentActivity() {
    private val viewModel: RoutingSettingsViewModel by viewModels()
    private val domainStrategyState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainStrategyState.value = getDomainStrategy()
    }

    @Composable
    override fun ScreenContent() {
        RoutingSettingScreen(
            viewModel = viewModel,
            domainStrategyState = domainStrategyState,
            onBackClick = { finish() },
            onAddRule = { startActivity(Intent(this, RoutingEditActivity::class.java)) },
            onEditRule = { ruleId ->
                startActivity(Intent(this, RoutingEditActivity::class.java).putExtra(RoutingEditActivity.EXTRA_RULE_ID, ruleId))
            },
            onUpdateRule = { ruleId, item ->
                changeRules("update", ruleId) { check(viewModel.update(ruleId, item)) }
            },
            onDeleteRule = { ruleId -> changeRules("delete", ruleId) { viewModel.remove(ruleId) } },
            onMoveRule = { ruleId, targetId -> changeRules("move", ruleId) { viewModel.move(ruleId, targetId) } },
            onMoveRuleCommand = { ruleId, command, onMoved ->
                changeRules("move", ruleId) { if (viewModel.move(ruleId, command)) onMoved() }
            },
            onDomainStrategySelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, value)
                domainStrategyState.value = value
            },
            onImportPredefined = { type -> importPredefined(type) },
            onImportClipboard = { importFromClipboard() },
            onImportQRcode = { importQRcode() },
            onExportClipboard = { export2Clipboard() }
        )
    }

    override fun onResume() {
        super.onResume()
        changeRules("reload") { viewModel.reload() }
    }

    private fun changeRules(operation: String, ruleId: String? = null, change: suspend () -> Unit): Boolean {
        lifecycleScope.launch {
            try {
                change()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "RoutingSettingActivity: failed to $operation rule $ruleId", error)
                toastError(R.string.toast_failure)
            }
        }
        return true
    }

    private fun getDomainStrategy(): String {
        val strategies = resources.getStringArray(R.array.routing_domain_strategy)
        return MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY) ?: strategies.first()
    }

    private fun importPredefined(type: RoutingType) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SettingsManager.resetRoutingRulesetsFromPresets(this@RoutingSettingActivity, type)
                withContext(Dispatchers.Main) {
                    if (result) {
                        viewModel.reload()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import predefined ruleset", e)
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = try {
            Utils.getClipboard(this)
        } catch (e: Exception) {
            toastError(R.string.toast_failure)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = SettingsManager.resetRoutingRulesets(clipboard)
            withContext(Dispatchers.Main) {
                if (result) {
                    viewModel.reload()
                    toastSuccess(R.string.toast_success)
                } else {
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = SettingsManager.resetRoutingRulesets(scanResult)
                    withContext(Dispatchers.Main) {
                        if (result) {
                            viewModel.reload()
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
        }
    }

    private fun export2Clipboard() {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) {
            toastError(R.string.toast_failure)
        } else {
            Utils.setClipboard(this, JsonUtil.toJson(rulesetList))
            toastSuccess(R.string.toast_success)
        }
    }
}
private class RoutingRowFocusTargets {
    val row = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
    val toggle = FocusRequester()
}

@Composable
internal fun RoutingSettingScreen(
    viewModel: RoutingSettingsViewModel,
    domainStrategyState: MutableStateFlow<String>,
    onBackClick: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onUpdateRule: (String, RulesetItem) -> Unit,
    onDeleteRule: (String) -> Unit,
    onMoveRule: (String, String) -> Unit,
    onMoveRuleCommand: (String, ReorderCommand, onMoved: () -> Unit) -> Boolean,
    onDomainStrategySelected: (String) -> Unit,
    onImportPredefined: (RoutingType) -> Unit,
    onImportClipboard: () -> Unit,
    onImportQRcode: () -> Unit,
    onExportClipboard: () -> Unit
) {
    val isTelevision = isTelevisionDevice()
    // Keep lazy content and its focus map on one snapshot: TalkBack can measure before recomposition.
    val rulesets = viewModel.rulesetsFlow.collectAsStateWithLifecycle().value
    val rulesetIds = rulesets.map { it.id }
    val rowFocusTargets = rememberDpadFocusTargets(rulesetIds) { RoutingRowFocusTargets() }
    val domainStrategy by domainStrategyState.collectAsState()
    var showPresetDialog by remember { mutableStateOf(false) }
    var deleteRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val actionFeedback = rememberAccessibilityActionFeedback()
    val navigationFocusRequester = rememberDpadFocusRequester()
    val domainStrategyFocusRequester = remember { FocusRequester() }
    val domainStrategies = stringArrayResource(R.array.routing_domain_strategy).toList()
    val lazyListState = rememberLazyListState()
    val requestRowFocus = rememberLazyDpadFocus(
        listOf(listOf(domainStrategyFocusRequester)) + rulesets.map {
            with(rowFocusTargets.getValue(it.id)) { listOf(row, edit, delete, toggle) }
        }
    ) { lazyListState.scrollToItem(it) }
    val focusFirstRule = {
        rulesets.firstOrNull()?.let { rowFocusTargets[it.id]?.row?.let(requestRowFocus) } ?: false
    }
    val dpadReorderState = rememberSyncedDpadReorderState(rulesetIds, isTelevision) { key, index ->
        val id = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) {
            lazyListState.keepDpadReorderItemVisible(id, index + ROUTING_LIST_HEADER_COUNT)
        }
        rowFocusTargets[id]?.row?.requestFocus()
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderIndicesForKeys(rulesetIds, from.key, to.key)?.let { (fromIndex, toIndex) ->
            onMoveRule(rulesetIds[fromIndex], rulesetIds[toIndex])
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routing_settings_title),
                onBackClick = onBackClick,
                navigationFocusRequester = navigationFocusRequester,
                onMoveDown = { requestRowFocus(domainStrategyFocusRequester) },
                actionItems = listOf(
                    AppTopBarAction(painterResource(R.drawable.ic_add_24dp),
                        stringResource(R.string.routing_settings_add_rule), onAddRule),
                    AppTopBarAction(
                        painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.action_more),
                        menuActions = RoutingMenuAction.entries.map { action ->
                            AppSelectionMenuAction(stringResource(action.labelRes)) {
                                when (action) {
                                    RoutingMenuAction.ImportPredefined -> showPresetDialog = true
                                    RoutingMenuAction.ImportClipboard -> onImportClipboard()
                                    RoutingMenuAction.ImportQRCode -> onImportQRcode()
                                    RoutingMenuAction.ExportClipboard -> onExportClipboard()
                                }
                            }
                        }
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
                .tvSafeAreaPadding()
                .dpadMovePreviousNavigation(enabled = !dpadReorderState.isMoving) {
                    navigationFocusRequester.requestFocus()
                }
                .verticalScrollbar(lazyListState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            item(key = "domain_strategy") {
                SettingsListItem(
                    title = stringResource(R.string.routing_settings_domain_strategy),
                    entries = domainStrategies,
                    values = domainStrategies,
                    selectedValue = domainStrategy,
                    onSelected = { onDomainStrategySelected(it) },
                    focusRequester = domainStrategyFocusRequester,
                    modifier = Modifier.dpadVerticalFocusNavigation(
                        onMoveUp = { navigationFocusRequester.requestFocus() },
                        onMoveDown = focusFirstRule
                    )
                )
            }

            itemsIndexed(items = rulesets, key = { _, ruleset -> ruleset.id }) { index, ruleset ->
                val focusTargets = rowFocusTargets.getValue(ruleset.id)
                val previousTargets = rulesets.getOrNull(index - 1)?.let { rowFocusTargets[it.id] }
                val nextTargets = rulesets.getOrNull(index + 1)?.let { rowFocusTargets[it.id] }
                val dpadReorderItem = DpadReorderItem(
                    state = dpadReorderState,
                    key = ruleset.id,
                    index = index,
                    itemCount = rulesets.size,
                    targetIndex = ::verticalDpadReorderTarget,
                    onMove = { fromIndex, toIndex ->
                        val fromId = rulesetIds.getOrNull(fromIndex)
                        val toId = rulesetIds.getOrNull(toIndex)
                        if (fromId != null && toId != null) onMoveRule(fromId, toId)
                    }
                )
                val isMoving = dpadReorderState.isMoving(ruleset.id)
                val actionFocusOrder = remember(focusTargets) {
                    listOf(focusTargets.row, focusTargets.edit, focusTargets.delete, focusTargets.toggle)
                }
                val ruleName = ruleset.remarks.orEmpty()
                val toggleLabel = stringResource(
                    if (ruleset.enabled) R.string.acc_disable_routing_rule else R.string.acc_enable_routing_rule
                )
                val editLabel = stringResource(R.string.acc_edit_routing_rule_named, ruleName)
                val deleteLabel = stringResource(R.string.acc_delete_routing_rule_named, ruleName)
                val outbound = ruleset.outboundTag.ifBlank { AppConfig.TAG_PROXY }
                val routeDescription = when (outbound) {
                    AppConfig.TAG_BLOCKED -> stringResource(R.string.acc_routing_rule_blocked)
                    AppConfig.TAG_DIRECT -> stringResource(R.string.acc_routing_rule_routed_directly)
                    else -> stringResource(R.string.acc_routing_rule_routed_through, outbound)
                }
                val summary = stringResource(R.string.acc_routing_rule_summary, ruleName, routeDescription)
                val accessibilitySummary = if (ruleset.locked == true) {
                    stringResource(R.string.acc_routing_rule_locked_summary, summary)
                } else summary
                val ruleState = stringResource(
                    if (ruleset.enabled) R.string.acc_routing_rule_enabled else R.string.acc_routing_rule_disabled
                )
                val actions = listOf(
                    CustomAccessibilityAction(editLabel) { onEditRule(ruleset.id); true },
                    CustomAccessibilityAction(deleteLabel) { deleteRuleId = ruleset.id; true },
                ) + reorderAccessibilityActions(index, rulesets.size, actionFeedback) { command, onMoved ->
                    onMoveRuleCommand(ruleset.id, command, onMoved)
                }
                ReorderableItem(reorderableState, key = ruleset.id, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                    ReorderableListItem(scope = this, isDragging = isDragging, isMoving = isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = accessibilitySummary
                                    stateDescription = ruleState
                                    customActions = actions
                                    onClick(label = toggleLabel, action = null)
                                }
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
                                            .dpadOrderedFocusNavigation(
                                                current = focusTargets.row,
                                                order = actionFocusOrder,
                                                onBeforeFirst = { navigationFocusRequester.requestFocus() }
                                            )
                                            .dpadVerticalFocusNavigation(
                                                onMoveUp = {
                                                    previousTargets?.row?.let(requestRowFocus)
                                                        ?: requestRowFocus(domainStrategyFocusRequester)
                                                },
                                                onMoveDown = {
                                                    nextTargets?.row?.let(requestRowFocus) ?: true
                                                }
                                            )
                                            .dpadLongPressToMove(
                                                enabled = isTelevision,
                                                item = dpadReorderItem,
                                                onClick = { onUpdateRule(ruleset.id, ruleset.copy(enabled = !ruleset.enabled)) },
                                                addFocusTarget = false,
                                            )
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                    } else {
                                        Modifier.padding(horizontal = 16.dp)
                                    }
                                )
                                .toggleable(
                                    value = ruleset.enabled,
                                    role = Role.Switch,
                                    onValueChange = { onUpdateRule(ruleset.id, ruleset.copy(enabled = it)) },
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).clearAndSetSemantics {}) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ruleset.remarks ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ruleset.locked == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lock_24dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                val domainIpInfo =
                                    listOfNotNull(ruleset.domain, ruleset.ip, ruleset.process, ruleset.port).joinToString(" • ")
                                if (domainIpInfo.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = domainIpInfo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!ruleset.outboundTag.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(ruleset.outboundTag, style = MaterialTheme.typography.labelMedium, color = colorConfigType)
                                }
                            }

                            if (isTelevision) {
                                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_edit_24dp),
                                        label = editLabel,
                                        focusRequester = focusTargets.edit,
                                        modifier = Modifier.clearAndSetSemantics {}.dpadRowActionNavigation(
                                            current = focusTargets.edit,
                                            order = actionFocusOrder,
                                            previousRow = previousTargets?.edit,
                                            nextRow = nextTargets?.edit,
                                        requestFocus = requestRowFocus
                                        ),
                                        onClick = { onEditRule(ruleset.id) }
                                    )
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_delete_24dp),
                                        label = deleteLabel,
                                        focusRequester = focusTargets.delete,
                                        modifier = Modifier.clearAndSetSemantics {}.dpadRowActionNavigation(
                                            current = focusTargets.delete,
                                            order = actionFocusOrder,
                                            previousRow = previousTargets?.delete,
                                            nextRow = nextTargets?.delete,
                                        requestFocus = requestRowFocus
                                        ),
                                        onClick = { deleteRuleId = ruleset.id }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppRowSwitch(
                                        checked = ruleset.enabled,
                                        onCheckedChange = { checked ->
                                            val updated = ruleset.copy(enabled = checked)
                                            onUpdateRule(ruleset.id, updated)
                                        },
                                        label = stringResource(R.string.routing_settings_enable_rule),
                                        focusRequester = focusTargets.toggle,
                                        modifier = Modifier.clearAndSetSemantics {}.dpadRowActionNavigation(
                                            current = focusTargets.toggle,
                                            order = actionFocusOrder,
                                            previousRow = previousTargets?.toggle,
                                            nextRow = nextTargets?.toggle,
                                        requestFocus = requestRowFocus
                                        )
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                                Row {
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_edit_24dp),
                                        label = editLabel,
                                        modifier = Modifier.clearAndSetSemantics {},
                                        onClick = { onEditRule(ruleset.id) }
                                    )
                                    AppIconButton(
                                        icon = painterResource(R.drawable.ic_delete_24dp),
                                        label = deleteLabel,
                                        modifier = Modifier.clearAndSetSemantics {},
                                        onClick = { deleteRuleId = ruleset.id }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Switch(
                                    checked = ruleset.enabled,
                                    onCheckedChange = null,
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


    rulesets.firstOrNull { it.id == deleteRuleId }?.let { rule ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_routing_rule),
            itemName = rule.remarks,
            onConfirm = {
                deleteRuleId = null
                onDeleteRule(rule.id)
            },
            onDismiss = { deleteRuleId = null }
        )
    }

    if (showPresetDialog) {
        SelectListDialog(
            title = stringResource(R.string.routing_settings_import_predefined_rulesets),
            options = RoutingPreset.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { preset ->
                showPresetDialog = false
                onImportPredefined(preset.type)
            },
            onDismiss = { showPresetDialog = false }
        )
    }
}
