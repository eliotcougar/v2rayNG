package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.DpadReorderDirection
import com.v2ray.ang.ui.compose.DpadReorderItem
import com.v2ray.ang.ui.compose.DpadReorderState
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.dpadBackNavigation
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.keepDpadReorderItemVisible
import com.v2ray.ang.ui.compose.rememberSyncedDpadReorderState
import com.v2ray.ang.ui.compose.reorderIndicesForKeys
import com.v2ray.ang.ui.compose.twoColumnDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalDpadReorderTarget
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.yield
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private class ServerRowFocusRequesters {
    val row = FocusRequester()
    val more = FocusRequester()
    val share = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
}

private enum class ServerRowLayout { SingleColumn, TwoColumn }

private data class ServerRowFocusTargets(
    val current: ServerRowFocusRequesters,
    val previous: ServerRowFocusRequesters?,
    val next: ServerRowFocusRequesters?,
    val adjacentColumn: ServerRowFocusRequesters? = null,
    val layout: ServerRowLayout
)

private data class ServerRowActions(
    val select: () -> Unit,
    val edit: () -> Unit,
    val share: () -> Unit,
    val remove: () -> Unit,
    val more: () -> Unit,
    val movePrevious: ((FocusRequester) -> Unit)?,
    val moveUp: (() -> Unit)?,
    val reorderItem: DpadReorderItem?
)

private data class ServerCollectionActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem, FocusRequester) -> Unit,
    val more: (String, ProfileItem, FocusRequester) -> Unit,
    val remove: (String) -> Unit,
    val move: (Int, Int) -> Unit,
    val movePrevious: (FocusRequester) -> Unit,
    val moveUpFromFirstRow: (() -> Unit)?
)

@Composable
private fun ServerCollectionItem(
    row: ServerRowUiModel,
    index: Int,
    rows: List<ServerRowUiModel>,
    focusRequesters: Map<String, ServerRowFocusRequesters>,
    layout: ServerRowLayout,
    reorderState: DpadReorderState?,
    reorderTarget: (Int, DpadReorderDirection) -> Int,
    collectionActions: ServerCollectionActions,
    selectedGuid: String?
) {
    val stride = if (layout == ServerRowLayout.TwoColumn) 2 else 1
    val current = focusRequesters.getValue(row.guid)
    val previous = rows.getOrNull(index - stride)?.let { focusRequesters[it.guid] }
    val next = rows.getOrNull(index + stride)?.let { focusRequesters[it.guid] }
    val previousColumn = if (layout == ServerRowLayout.TwoColumn && index % 2 == 1) {
        rows.getOrNull(index - 1)?.let { focusRequesters[it.guid] }
    } else null
    val adjacentColumn = if (layout == ServerRowLayout.TwoColumn && index % 2 == 0) {
        rows.getOrNull(index + 1)?.let { focusRequesters[it.guid] }
    } else null
    val reorderItem = reorderState?.let {
        DpadReorderItem(it, row.guid, index, rows.size, reorderTarget, collectionActions.move)
    }
    Column {
        ServerListItem(
            model = row,
            isSelected = row.guid == selectedGuid,
            actions = ServerRowActions(
                select = { collectionActions.select(row.guid) },
                edit = { collectionActions.edit(row.guid, row.profile) },
                share = { collectionActions.share(row.guid, row.profile, current.share) },
                remove = { collectionActions.remove(row.guid) },
                more = { collectionActions.more(row.guid, row.profile, current.row) },
                movePrevious = previousColumn?.let { target -> { _: FocusRequester -> target.more.requestFocus() } }
                    ?: collectionActions.movePrevious,
                moveUp = if (index < stride) collectionActions.moveUpFromFirstRow else null,
                reorderItem = reorderItem
            ),
            focusTargets = ServerRowFocusTargets(current, previous, next, adjacentColumn, layout)
        )
        ServerItemDivider()
    }
}

@Composable
private fun RevealSelectedServerEffect(
    isTelevision: Boolean,
    generation: Int,
    selectedGuid: String?,
    serverGuids: Set<String>,
    selectedIndex: Int,
    reveal: suspend (Int) -> Unit
) {
    LaunchedEffect(isTelevision, generation, selectedGuid, serverGuids) {
        if (isTelevision && selectedIndex >= 0) reveal(selectedIndex)
    }
}

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    revealSelectedGeneration: Int,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem, FocusRequester) -> Unit,
    onMoreServer: (String, ProfileItem, FocusRequester) -> Unit,
    onRemoveServer: (String) -> Unit,
    onOpenDrawer: (FocusRequester) -> Unit,
    onBackFromList: () -> Unit,
    onMoveUpFromFirstRow: (() -> Unit)?,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(mainViewModel, groupId) { mainViewModel.serverGroupState(groupId) }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    ServerListPage(
        rows = groupState.rows,
        selectedGuid = selectedGuid,
        locateTarget = locateTarget?.takeIf { it.groupId == groupId },
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        revealSelectedGeneration = revealSelectedGeneration,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        collectionActions = ServerCollectionActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
            move = { from, to -> mainViewModel.moveServer(groupId, from, to) },
            movePrevious = onOpenDrawer,
            moveUpFromFirstRow = onMoveUpFromFirstRow
        ),
        onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled(it)) },
        onBackFromList = onBackFromList,
        contentPadding = contentPadding
    )
}

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    revealSelectedGeneration: Int,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    collectionActions: ServerCollectionActions,
    onLocateHandled: (LocateTarget) -> Unit,
    onBackFromList: () -> Unit,
    contentPadding: PaddingValues
) {
    val isTelevision = isTelevisionDevice()
    val selectedServerIndex = rows.indexOfFirst { it.guid == selectedGuid }
    val serverGuids = rows.map { it.guid }
    val serverGuidSet = serverGuids.toSet()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rowFocusTargets = remember(groupId, serverGuidSet, doubleColumnDisplay) {
        rows.associate { it.guid to ServerRowFocusRequesters() }
    }
    val gridState = remember(groupId) {
        lazyGridStates.getOrPut(groupId) { LazyGridState() }
    }
    val listState = remember(groupId) {
        lazyListStates.getOrPut(groupId) { LazyListState() }
    }
    val dpadReorderState = rememberSyncedDpadReorderState(
        keys = serverGuids,
        enabled = isTelevision && canReorder,
        stateKey = groupId
    ) { key, index ->
        val guid = key as? String ?: return@rememberSyncedDpadReorderState
        if (index >= 0) {
            if (doubleColumnDisplay) {
                gridState.keepDpadReorderItemVisible(guid, index)
            } else {
                listState.keepDpadReorderItemVisible(guid, index)
            }
        }
        rowFocusTargets[guid]?.row?.requestFocus()
    }

    if (doubleColumnDisplay) {
        RevealSelectedServerEffect(
            isTelevision,
            revealSelectedGeneration,
            selectedGuid,
            serverGuidSet,
            selectedServerIndex
        ) { index ->
            gridState.scrollToItem(index, -gridState.layoutInfo.viewportSize.height / 3)
        }
        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                reorderIndicesForKeys(serverGuids, from.key, to.key)?.let { (fromIndex, toIndex) ->
                    collectionActions.move(fromIndex, toIndex)
                }
            }
        } else null

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .dpadBackNavigation(enabled = !dpadReorderState.isMoving, onBack = onBackFromList)
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val isMoving = dpadReorderState.isMoving(row.guid)
                val content: @Composable () -> Unit = {
                    ServerCollectionItem(
                        row, index, rows, rowFocusTargets, ServerRowLayout.TwoColumn,
                        dpadReorderState.takeIf { canReorder },
                        { currentIndex, direction -> twoColumnDpadReorderTarget(currentIndex, direction, isRtl) },
                        collectionActions, selectedGuid
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(reorderableGridState, key = row.guid, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                        ReorderableGridItem(this, isDragging, isMoving, moveModeCornerRadius = 12.dp) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        RevealSelectedServerEffect(
            isTelevision,
            revealSelectedGeneration,
            selectedGuid,
            serverGuidSet,
            selectedServerIndex
        ) { index ->
            listState.scrollToItem(index, -listState.layoutInfo.viewportSize.height / 3)
        }
        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                reorderIndicesForKeys(serverGuids, from.key, to.key)?.let { (fromIndex, toIndex) ->
                    collectionActions.move(fromIndex, toIndex)
                }
            }
        } else null

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .dpadBackNavigation(enabled = !dpadReorderState.isMoving, onBack = onBackFromList)
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val isMoving = dpadReorderState.isMoving(row.guid)
                val content: @Composable () -> Unit = {
                    ServerCollectionItem(
                        row, index, rows, rowFocusTargets, ServerRowLayout.SingleColumn,
                        dpadReorderState.takeIf { canReorder }, ::verticalDpadReorderTarget,
                        collectionActions, selectedGuid
                    )
                }
                if (canReorder && reorderableState != null) {
                    ReorderableItem(reorderableState, key = row.guid, modifier = Modifier.zIndex(if (isMoving) 1f else 0f)) { isDragging ->
                        ReorderableListItem(this, isDragging, isMoving, moveModeCornerRadius = 12.dp) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?, rows: List<ServerRowUiModel>, state: LazyListState,
    onHandled: (LocateTarget) -> Unit
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled(target)
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?, rows: List<ServerRowUiModel>, state: LazyGridState,
    onHandled: (LocateTarget) -> Unit
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled(target)
    }
}

@Composable
private fun ServerItemDivider() {
    AppDivider(Modifier.padding(horizontal = 12.dp, vertical = if (isTelevisionDevice()) 2.dp else 0.dp))
}

@Composable
private fun ServerListItem(
    model: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions,
    focusTargets: ServerRowFocusTargets
) {
    val isTelevision = isTelevisionDevice()
    val currentFocus = focusTargets.current
    val previousFocus = focusTargets.previous
    val nextFocus = focusTargets.next
    val isTwoColumn = focusTargets.layout == ServerRowLayout.TwoColumn
    val reorderItem = actions.reorderItem
    val isMoving = reorderItem?.let { it.state.isMoving(it.key) } == true
    val layoutDirection = LocalLayoutDirection.current
    val moveIndicatorColor = MaterialTheme.colorScheme.secondary
    val compactActionModifier = if (isTelevision) Modifier else Modifier.size(36.dp)
    val actionFocusOrder = remember(currentFocus, isTwoColumn) {
        if (isTwoColumn) listOf(currentFocus.row, currentFocus.more)
        else listOf(currentFocus.row, currentFocus.share, currentFocus.edit, currentFocus.delete)
    }
    val selectedStateDescription = if (isSelected) stringResource(R.string.acc_selected_server) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (selectedStateDescription != null) stateDescription = selectedStateDescription }
            .dpadFocusOutline(
                focusRequester = currentFocus.row,
                showFocus = !isMoving
            )
            .drawBehind {
                val stripWidth = if (isMoving) 8.dp.toPx() else 4.dp.toPx()
                val verticalInset = if (isMoving) 6.dp.toPx() else 10.dp.toPx()
                val startInset = if (isMoving) 0f else 6.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - startInset - stripWidth
                } else {
                    startInset
                }
                val stripHeight = (size.height - verticalInset * 2).coerceAtLeast(0f)
                when {
                    isMoving -> drawRoundRect(
                        color = moveIndicatorColor,
                        topLeft = Offset(x, verticalInset),
                        size = Size(stripWidth, stripHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    isSelected -> drawRect(
                        color = colorFabActive,
                        topLeft = Offset(x, verticalInset),
                        size = Size(stripWidth, stripHeight)
                    )
                }
            }
            .dpadOrderedFocusNavigation(
                current = currentFocus.row,
                order = actionFocusOrder,
                onBeforeFirst = {
                    actions.movePrevious?.invoke(currentFocus.row) ?: currentFocus.row.requestFocus()
                }
            )
            .dpadVerticalFocusNavigation(
                onMoveUp = {
                    previousFocus?.row?.requestFocus()
                        ?: actions.moveUp?.let { it(); true }
                        ?: false
                },
                onMoveDown = { nextFocus?.row?.requestFocus() ?: true }
            )
            .then(
                if (!isTelevision) {
                    Modifier.clickable(onClick = actions.select)
                } else if (reorderItem != null) {
                    Modifier.dpadLongPressToMove(enabled = true, item = reorderItem, onClick = actions.select)
                } else {
                    Modifier.dpadClickable(onClick = actions.select)
                }
            )
    ) {
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.remarks,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isTwoColumn) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_more_vert_24dp),
                        label = stringResource(R.string.action_more),
                        onClick = actions.more,
                        focusRequester = currentFocus.more,
                        modifier = compactActionModifier
                            .dpadOrderedFocusNavigation(
                                current = currentFocus.more,
                                order = actionFocusOrder,
                                onAfterLast = {
                                    focusTargets.adjacentColumn?.row?.requestFocus()
                                        ?: currentFocus.more.requestFocus()
                                }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocus?.more?.requestFocus() ?: false },
                                onMoveDown = { nextFocus?.more?.requestFocus() ?: true }
                            )
                    )
                } else {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_share_24dp),
                        label = stringResource(R.string.title_configuration_share),
                        onClick = actions.share,
                        focusRequester = currentFocus.share,
                        modifier = compactActionModifier.dpadRowActionNavigation(
                            currentFocus.share, actionFocusOrder, previousFocus?.share, nextFocus?.share
                        )
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_edit_24dp),
                        label = stringResource(R.string.menu_item_edit_config),
                        onClick = actions.edit,
                        focusRequester = currentFocus.edit,
                        modifier = compactActionModifier.dpadRowActionNavigation(
                            currentFocus.edit, actionFocusOrder, previousFocus?.edit, nextFocus?.edit
                        )
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_delete_24dp),
                        label = stringResource(R.string.menu_item_del_config),
                        onClick = actions.remove,
                        focusRequester = currentFocus.delete,
                        modifier = compactActionModifier.dpadRowActionNavigation(
                            currentFocus.delete, actionFocusOrder, previousFocus?.delete, nextFocus?.delete
                        )
                    )
                }
            }

            if (isTelevision) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SubscriptionBadge(model.subscriptionBadge)
                    Text(
                        model.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        model.typeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorConfigType,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(16.dp))
                    TestResult(model.testDelayMillis)
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SubscriptionBadge(model.subscriptionBadge)
                    Text(
                        model.statistics,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        model.typeDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorConfigType,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TestResult(model.testDelayMillis)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(text: String) {
    if (text.isBlank()) return
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun TestResult(delayMillis: Long) {
    Text(
        if (delayMillis == 0L) "" else stringResource(R.string.server_test_delay_value, delayMillis),
        style = MaterialTheme.typography.bodySmall,
        color = if (delayMillis < 0L) colorPingRed else colorPing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal suspend fun PagerState.navigateToPageOptimized(targetPage: Int, animateAdjacentPage: Boolean = true) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return
    val distance = abs(target - current)
    when {
        distance == 1 && animateAdjacentPage -> animateScrollToPage(target)
        animateAdjacentPage -> {
            val adjacent = if (target > current) target - 1 else target + 1
            scrollToPage(adjacent)
            yield()
            animateScrollToPage(target)
        }
        else -> scrollToPage(target)
    }
}
