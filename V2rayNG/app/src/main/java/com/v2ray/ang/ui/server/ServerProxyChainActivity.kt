package com.v2ray.ang.ui.server

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.text.BidiFormatter
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.FormDropdownConfig
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.TvTextFieldNavigation
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberFormDropdownState
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.tvAwareImePadding
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val PROXY_CHAIN_LIST_HEADER_COUNT = 2

private data class ProxyChainMemberFocusTargets(
    val field: FocusRequester = FocusRequester(),
    val remove: FocusRequester = FocusRequester()
)

class ServerProxyChainActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var allRemarks: List<String>
    private lateinit var initialRemarks: String
    private lateinit var initialMembers: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        allRemarks = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
        )
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks ?: ""
        initialMembers = config?.proxyChainProfiles?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("", "")
    }

    @Composable
    override fun ScreenContent() {
        ProxyChainScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            initialRemarks = initialRemarks,
            initialMembers = initialMembers,
            allRemarks = allRemarks,
            onBackClick = { finish() },
            onSave = { remarks, members -> saveServer(remarks, members) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(remarks: String, members: List<String>): Boolean {
        if (remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }

        val chainMembers = members
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (chainMembers.size != members.size) {
            toast(R.string.server_proxy_chain_members_unselected)
            return false
        }

        if (chainMembers.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return false
        }

        val invalidMembers = chainMembers.filter { member ->
            val profile = SettingsManager.getServerViaRemarks(member)
            profile == null || profile.configType.isComplexType()
        }

        if (invalidMembers.isNotEmpty()) {
            toast(getString(R.string.server_proxy_chain_members_invalid, invalidMembers.joinToString(", ")))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.PROXYCHAIN)

        config.remarks = remarks.trim()
        config.proxyChainProfiles = chainMembers.joinToString(",")
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val bidiFormatter = BidiFormatter.getInstance(isRtl)
        val chainSeparator = if (isRtl) " ← " else " → "
        config.description = chainMembers.joinToString(chainSeparator) { bidiFormatter.unicodeWrap(it) }

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(guid = savedGuid, restartService = isRunning)
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }
}

@Composable
fun ProxyChainScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialMembers: List<String>,
    allRemarks: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, List<String>) -> Boolean,
    onDelete: () -> Unit
) {
    val isTelevision = isTelevisionDevice()
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var members by rememberSaveable { mutableStateOf(initialMembers.toList()) }
    var memberIds by rememberSaveable { mutableStateOf(initialMembers.indices.map(Int::toLong)) }
    var nextMemberId by rememberSaveable { mutableLongStateOf(initialMembers.size.toLong()) }
    var showProfileDeleteConfirm by remember { mutableStateOf(false) }
    var memberToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingMemberFocusId by remember { mutableStateOf<Long?>(null) }
    var pendingAddFocus by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    val backFocusRequester = rememberDpadFocusRequester()
    val remarksFocusRequester = remember { FocusRequester() }
    val deleteConfigFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val addFocusRequester = remember { FocusRequester() }
    val topBarFocusOrder = remember(backFocusRequester, deleteConfigFocusRequester, saveFocusRequester, showDelete) {
        buildList {
            add(backFocusRequester)
            if (showDelete) add(deleteConfigFocusRequester)
            add(saveFocusRequester)
        }
    }
    val memberFocusTargetStore = remember {
        mutableMapOf<Long, ProxyChainMemberFocusTargets>()
    }
    val memberFocusTargets = memberIds.associateWith { memberId ->
        memberFocusTargetStore.getOrPut(memberId) {
            ProxyChainMemberFocusTargets()
        }
    }

    val lazyListState = rememberLazyListState()
    val dpadReorderState = rememberSyncedDpadReorderState(memberIds, isTelevision) { key, index ->
        val memberId = key as? Long ?: return@rememberSyncedDpadReorderState
        val focusTargets = memberFocusTargets[memberId]
        if (index >= 0 && focusTargets != null) {
            lazyListState.keepDpadReorderItemVisible(memberId, index + PROXY_CHAIN_LIST_HEADER_COUNT)
            requestFocusWhenReady(focusTargets.field)
        }
    }

    fun moveMember(fromIdx: Int, toIdx: Int) {
        val movedMembers = members.toMutableList()
        val movedIds = memberIds.toMutableList()
        if (!movedMembers.moveItem(fromIdx, toIdx)) return
        movedIds.moveItem(fromIdx, toIdx)
        members = movedMembers
        memberIds = movedIds
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        reorderIndicesForKeys(memberIds, from.key, to.key)?.let { (fromIndex, toIndex) ->
            moveMember(fromIndex, toIndex)
        }
    }

    LaunchedEffect(memberIds) {
        memberFocusTargetStore.keys.retainAll(memberIds.toSet())
    }

    LaunchedEffect(pendingMemberFocusId, memberIds) {
        val memberId = pendingMemberFocusId ?: return@LaunchedEffect
        val index = memberIds.indexOf(memberId)
        val focusTargets = memberFocusTargets[memberId]
        if (index >= 0 && focusTargets != null) {
            lazyListState.animateScrollToItem(index + PROXY_CHAIN_LIST_HEADER_COUNT)
            requestFocusWhenReady(focusTargets.field)
        }
        pendingMemberFocusId = null
    }

    LaunchedEffect(pendingAddFocus, memberIds) {
        if (pendingAddFocus) {
            requestFocusWhenReady(addFocusRequester)
            pendingAddFocus = false
        }
    }

    fun addMember() {
        val memberId = nextMemberId++
        members = members.toMutableList().also { it.add("") }
        memberIds = memberIds.toMutableList().also { it.add(memberId) }
        pendingMemberFocusId = memberId
        pendingAddFocus = false
    }

    fun removeMember(memberId: Long) {
        val index = memberIds.indexOf(memberId)
        if (index !in members.indices || index !in memberIds.indices) return
        val nextFocusId = memberIds.getOrNull(index + 1)
            ?: memberIds.getOrNull(index - 1)
        members = members.toMutableList().also { it.removeAt(index) }
        memberIds = memberIds.toMutableList().also { it.removeAt(index) }
        pendingMemberFocusId = nextFocusId
        pendingAddFocus = nextFocusId == null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = EConfigType.PROXYCHAIN.toString(),
                onBackClick = onBackClick,
                navigationFocusRequester = backFocusRequester,
                customActionFocusRequesters = topBarFocusOrder.drop(1),
                onMoveDown = remarksFocusRequester::requestFocus,
                navigationIcon = { requester ->
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_arrow_back_24dp),
                        label = stringResource(R.string.action_back),
                        focusRequester = requester,
                        modifier = Modifier.dpadTopBarFocusNavigation(
                            backFocusRequester,
                            topBarFocusOrder,
                            remarksFocusRequester::requestFocus
                        ),
                        onClick = onBackClick
                    )
                },
                actions = {
                    if (showDelete) {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_delete_24dp),
                            label = stringResource(R.string.menu_item_del_config),
                            focusRequester = deleteConfigFocusRequester,
                            modifier = Modifier.dpadTopBarFocusNavigation(
                                deleteConfigFocusRequester,
                                topBarFocusOrder,
                                remarksFocusRequester::requestFocus
                            ),
                            onClick = { showProfileDeleteConfirm = true }
                        )
                    }
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_fab_check),
                        label = stringResource(R.string.menu_item_save_config),
                        focusRequester = saveFocusRequester,
                        modifier = Modifier.dpadTopBarFocusNavigation(
                            saveFocusRequester,
                            topBarFocusOrder,
                            remarksFocusRequester::requestFocus
                        ),
                        onClick = { onSave(remarks, members) }
                    )
                }
            )
        },
        floatingActionButton = {
            if (isTelevision) {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_add_24dp),
                    label = stringResource(R.string.action_add_member),
                    focusRequester = addFocusRequester,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .offset(y = -20.dp)
                        .navigationBarsPadding()
                        .dpadOrderedFocusNavigation(addFocusRequester, listOf(addFocusRequester))
                        .dpadVerticalFocusNavigation(
                            onMoveUp = {
                                memberIds.lastOrNull()
                                    ?.let { memberFocusTargets[it]?.field?.requestFocus() }
                                    ?: remarksFocusRequester.requestFocus()
                            },
                            onMoveDown = { true }
                        ),
                    onClick = ::addMember
                )
            } else {
                FloatingActionButton(onClick = ::addMember, modifier = Modifier.offset(y = -20.dp).navigationBarsPadding()) {
                    Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.action_add_member))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .tvAwareImePadding()
                .verticalScrollbar(lazyListState)
                .tvSafeAreaPadding(),
            contentPadding = PaddingValues(
                top = 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 36.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item(key = "remarks_field") {
                FormTextField(
                    label = stringResource(R.string.server_lab_remarks),
                    value = remarks,
                    onValueChange = { remarks = it },
                    tvNavigation = TvTextFieldNavigation(
                        focusRequester = remarksFocusRequester,
                        onMoveUp = { backFocusRequester.requestFocus() },
                        onMoveDown = {
                            memberIds.firstOrNull()
                                ?.let { memberFocusTargets[it]?.field?.requestFocus() }
                                ?: addFocusRequester.requestFocus()
                        }
                    )
                )
            }

            item(key = "members_header") {
                Text(
                    text = stringResource(R.string.server_proxy_chain_members),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(items = memberIds, key = { _, memberId -> memberId }) { index, memberId ->
                val member = members.getOrElse(index) { "" }
                val focusTargets = memberFocusTargets.getValue(memberId)
                val previousTargets = memberIds.getOrNull(index - 1)
                    ?.let(memberFocusTargets::get)
                val nextTargets = memberIds.getOrNull(index + 1)
                    ?.let(memberFocusTargets::get)
                val dpadReorderItem = DpadReorderItem(
                    state = dpadReorderState,
                    key = memberId,
                    index = index,
                    itemCount = memberIds.size,
                    targetIndex = ::verticalDpadReorderTarget,
                    onMove = ::moveMember
                )
                val isMoving = dpadReorderState.isMoving(memberId)
                val dropdownState = rememberFormDropdownState()
                val actionFocusOrder = remember(focusTargets) {
                    listOf(focusTargets.field, focusTargets.remove)
                }

                ReorderableItem(reorderableState, key = memberId, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                    ReorderableListItem(scope = this, isDragging = isDragging, isMoving = isMoving) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadMovePreviousNavigation(enabled = !dpadReorderState.isMoving) {
                                    backFocusRequester.requestFocus()
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                            FormDropdownField(
                                label = stringResource(R.string.server_proxy_chain_member),
                                value = member,
                                options = allRemarks,
                                onValueChange = { newVal ->
                                    members = members.toMutableList().also { it[index] = newVal }
                                },
                                config = FormDropdownConfig(
                                    editable = !isTelevision,
                                    placeholder = stringResource(R.string.server_proxy_chain_member_unselected)
                                ),
                                tvNavigation = TvTextFieldNavigation(
                                    focusRequester = focusTargets.field,
                                    onMoveUp = {
                                        previousTargets?.field?.requestFocus()
                                            ?: remarksFocusRequester.requestFocus()
                                    },
                                    onMoveDown = {
                                        nextTargets?.field?.requestFocus()
                                            ?: addFocusRequester.requestFocus()
                                    }
                                ),
                                state = dropdownState,
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadOrderedFocusNavigation(
                                        current = focusTargets.field,
                                        order = actionFocusOrder,
                                        onBeforeFirst = { backFocusRequester.requestFocus() }
                                    )
                                    .dpadLongPressToMove(
                                        enabled = isTelevision,
                                        item = dpadReorderItem,
                                        onClick = dropdownState::toggle,
                                        addFocusTarget = false
                                    )
                            )
                            AppIconButton(
                                icon = painterResource(R.drawable.ic_delete_24dp),
                                label = stringResource(R.string.action_remove),
                                focusRequester = focusTargets.remove,
                                modifier = Modifier
                                    .dpadOrderedFocusNavigation(focusTargets.remove, actionFocusOrder)
                                    .dpadVerticalFocusNavigation(
                                        onMoveUp = {
                                            previousTargets?.remove?.requestFocus()
                                                ?: saveFocusRequester.requestFocus()
                                        },
                                        onMoveDown = {
                                            nextTargets?.remove?.requestFocus()
                                                ?: addFocusRequester.requestFocus()
                                        }
                                    ),
                                onClick = {
                                    if (member.isBlank()) removeMember(memberId)
                                    else memberToDeleteId = memberId
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfileDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            onConfirm = { showProfileDeleteConfirm = false; onDelete() },
            onDismiss = { showProfileDeleteConfirm = false }
        )
    }
    memberToDeleteId?.let { memberId ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_proxy_chain_member),
            onConfirm = {
                removeMember(memberId)
                memberToDeleteId = null
            },
            onDismiss = { memberToDeleteId = null }
        )
    }
}
