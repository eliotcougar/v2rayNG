package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.dpadBackNavigation
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    isActivePage: Boolean,
    serverFocusRequester: FocusRequester,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    onOpenDrawer: (FocusRequester) -> Unit,
    onMoveToService: () -> Unit,
    contentPadding: PaddingValues
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty()
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
        onOpenDrawer,
        onMoveToService,
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
            openDrawer = onOpenDrawer,
            moveToService = onMoveToService,
        )
    }
    ServerListPage(
        rows = groupState.rows,
        selectedGuid = selectedGuid,
        isActivePage = isActivePage,
        serverFocusRequester = serverFocusRequester,
        locateTarget = locateTarget?.takeIf { it.groupId == groupId },
        canReorder = canReorder,
        doubleColumnDisplay = doubleColumnDisplay,
        groupId = groupId,
        lazyListStates = lazyListStates,
        lazyGridStates = lazyGridStates,
        actions = actions,
        onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
        onMoveServer = { fromIndex, toIndex ->
            mainViewModel.moveServer(groupId, fromIndex, toIndex)
        },
        contentPadding = contentPadding
    )
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String) -> Unit,
    val openDrawer: (FocusRequester) -> Unit,
    val moveToService: () -> Unit,
)

private class ServerRowFocusRequesters {
    val row = FocusRequester()
    val more = FocusRequester()
    val share = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
}

private data class ServerRowFocusTargets(
    val current: ServerRowFocusRequesters,
    val previous: ServerRowFocusRequesters?,
    val next: ServerRowFocusRequesters?,
    val previousColumn: ServerRowFocusRequesters?,
    val adjacentColumn: ServerRowFocusRequesters?,
    val twoColumn: Boolean
)

private fun serverRowFocusTargets(
    index: Int,
    rows: List<ServerRowUiModel>,
    requesters: Map<String, ServerRowFocusRequesters>,
    twoColumn: Boolean
): ServerRowFocusTargets {
    val stride = if (twoColumn) 2 else 1
    return ServerRowFocusTargets(
        current = requesters.getValue(rows[index].guid),
        previous = rows.getOrNull(index - stride)?.let { requesters[it.guid] },
        next = rows.getOrNull(index + stride)?.let { requesters[it.guid] },
        previousColumn = if (twoColumn && index % 2 == 1) {
            rows.getOrNull(index - 1)?.let { requesters[it.guid] }
        } else null,
        adjacentColumn = if (twoColumn && index % 2 == 0) {
            rows.getOrNull(index + 1)?.let { requesters[it.guid] }
        } else null,
        twoColumn = twoColumn
    )
}

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    isActivePage: Boolean,
    serverFocusRequester: FocusRequester,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ServerRowActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    val focusGuid = if (isActivePage) {
        selectedGuid?.takeIf { selected -> rows.any { it.guid == selected } } ?: rows.firstOrNull()?.guid
    } else null
    val serverGuids = rows.map { it.guid }.toSet()
    val rowFocusRequesters = remember(groupId, serverGuids) {
        rows.associate { it.guid to ServerRowFocusRequesters() }
    }
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        doubleColumnDisplay = true,
                        actions = actions,
                        externalFocusRequester = serverFocusRequester.takeIf { row.guid == focusGuid },
                        focusTargets = serverRowFocusTargets(index, rows, rowFocusRequesters, true)
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { index, row ->
                val focusTargets = serverRowFocusTargets(index, rows, rowFocusRequesters, false)
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                row = row,
                                isSelected = row.guid == selectedGuid,
                                actions = actions,
                                externalFocusRequester = serverFocusRequester.takeIf { row.guid == focusGuid },
                                focusTargets = focusTargets
                            )
                        }
                        ItemDivider()
                    }
                } else {
                    ServerItemRow(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        actions = actions,
                        externalFocusRequester = serverFocusRequester.takeIf { row.guid == focusGuid },
                        focusTargets = focusTargets
                    )
                    ItemDivider()
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun ServerItemRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions,
    externalFocusRequester: FocusRequester?,
    focusTargets: ServerRowFocusTargets
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        doubleColumnDisplay = false,
        actions = actions,
        externalFocusRequester = externalFocusRequester,
        focusTargets = focusTargets
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions,
    externalFocusRequester: FocusRequester?,
    focusTargets: ServerRowFocusTargets
) {
    Column {
        ServerListItem(
            row = row,
            isSelected = isSelected,
            doubleColumnDisplay = doubleColumnDisplay,
            actions = actions,
            externalFocusRequester = externalFocusRequester,
            focusTargets = focusTargets
        )
        ItemDivider()
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions,
    externalFocusRequester: FocusRequester?,
    focusTargets: ServerRowFocusTargets
) {
    val currentFocus = focusTargets.current
    val previousFocus = focusTargets.previous
    val nextFocus = focusTargets.next
    val actionFocusOrder = remember(currentFocus, focusTargets.twoColumn) {
        if (focusTargets.twoColumn) listOf(currentFocus.row, currentFocus.more)
        else listOf(currentFocus.row, currentFocus.share, currentFocus.edit, currentFocus.delete)
    }
    val testResult = if (row.testDelayMillis == 0L) {
        ""
    } else {
        stringResource(R.string.server_test_delay_value, row.testDelayMillis)
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (externalFocusRequester != null) Modifier.focusRequester(externalFocusRequester) else Modifier)
            .dpadFocusOutline(currentFocus.row)
            .dpadOrderedFocusNavigation(
                current = currentFocus.row,
                order = actionFocusOrder,
                onBeforeFirst = {
                    focusTargets.previousColumn?.more?.requestFocus()
                        ?: actions.openDrawer(currentFocus.row)
                }
            )
            .dpadVerticalFocusNavigation(
                onMoveUp = { previousFocus?.row?.requestFocus() ?: false },
                onMoveDown = { nextFocus?.row?.requestFocus() ?: true }
            )
            .dpadBackNavigation(onBack = actions.moveToService)
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .clickable { actions.select(row.guid) }
    ) {
        Box(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
        ) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doubleColumnDisplay) {
                    IconButton(
                        onClick = { actions.more(row.guid, row.profile) },
                        modifier = Modifier
                            .size(36.dp)
                            .dpadFocusOutline(currentFocus.more, 18.dp)
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
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(R.string.acc_more),
                            Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { actions.share(row.guid, row.profile) },
                        modifier = Modifier.size(36.dp)
                            .dpadFocusOutline(currentFocus.share, 18.dp)
                            .dpadRowActionNavigation(
                                currentFocus.share, actionFocusOrder,
                                previousFocus?.share, nextFocus?.share
                            )
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { actions.edit(row.guid, row.profile) },
                        modifier = Modifier.size(36.dp)
                            .dpadFocusOutline(currentFocus.edit, 18.dp)
                            .dpadRowActionNavigation(
                                currentFocus.edit, actionFocusOrder,
                                previousFocus?.edit, nextFocus?.edit
                            )
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { actions.remove(row.guid) },
                        modifier = Modifier.size(36.dp)
                            .dpadFocusOutline(currentFocus.delete, 18.dp)
                            .dpadRowActionNavigation(
                                currentFocus.delete, actionFocusOrder,
                                previousFocus?.delete, nextFocus?.delete
                            )
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.subscriptionBadge.isNotBlank()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center
                    ) {
                        Text(row.subscriptionBadge.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    row.statistics,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.typeDescription, style = MaterialTheme.typography.bodySmall, color = colorConfigType, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(testResult, style = MaterialTheme.typography.bodySmall, color = if (row.testDelayMillis < 0L) colorPingRed else colorPing, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}
