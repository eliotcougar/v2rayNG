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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadHorizontalFocusNavigation
import com.v2ray.ang.ui.compose.dpadLongPressToMove
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

private class ServerRowFocusTargets {
    val row = FocusRequester()
    val more = FocusRequester()
    val share = FocusRequester()
    val edit = FocusRequester()
    val delete = FocusRequester()
}

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
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
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
        )
    }
    ServerListPage(
        rows = groupState.rows,
        selectedGuid = selectedGuid,
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
)

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
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
    val isTelevision = isTelevisionDevice()
    val serverGuids = rows.map { it.guid }
    val serverGuidSet = serverGuids.toSet()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var movingGuid by remember(groupId) { mutableStateOf<String?>(null) }
    var movingIndex by remember(groupId) { mutableStateOf(-1) }
    val rowFocusTargets = remember(groupId, serverGuidSet, doubleColumnDisplay) {
        rows.associate { it.guid to ServerRowFocusTargets() }
    }

    LaunchedEffect(canReorder, serverGuidSet) {
        if (!canReorder || movingGuid == null || movingGuid !in serverGuidSet) {
            movingGuid = null
            movingIndex = -1
        }
    }
    LaunchedEffect(movingGuid, movingIndex) {
        val guid = movingGuid ?: return@LaunchedEffect
        withFrameNanos { }
        rowFocusTargets[guid]?.row?.requestFocus()
    }

    fun startMovement(guid: String, index: Int) {
        if (!isTelevision || !canReorder) return
        movingGuid = guid
        movingIndex = index
    }

    fun finishMovement() {
        movingGuid = null
        movingIndex = -1
    }

    fun handleMovementKey(event: KeyEvent, grid: Boolean): Boolean {
        if (movingGuid == null) return false

        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
            return true
        }

        val isDirection = event.key == Key.DirectionUp ||
                event.key == Key.DirectionDown ||
                event.key == Key.DirectionLeft ||
                event.key == Key.DirectionRight
        if (!isDirection) return false
        if (event.type != KeyEventType.KeyDown) return true

        val targetIndex = when (event.key) {
            Key.DirectionUp -> movingIndex - if (grid) 2 else 1
            Key.DirectionDown -> movingIndex + if (grid) 2 else 1
            Key.DirectionLeft -> when {
                !grid -> movingIndex
                isRtl && movingIndex % 2 == 0 -> movingIndex + 1
                !isRtl && movingIndex % 2 == 1 -> movingIndex - 1
                else -> movingIndex
            }
            Key.DirectionRight -> when {
                !grid -> movingIndex
                isRtl && movingIndex % 2 == 1 -> movingIndex - 1
                !isRtl && movingIndex % 2 == 0 -> movingIndex + 1
                else -> movingIndex
            }
            else -> movingIndex
        }
        if (targetIndex in rows.indices && targetIndex != movingIndex) {
            val fromIndex = movingIndex
            movingIndex = targetIndex
            onMoveServer(fromIndex, targetIndex)
        }
        return true
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
                val focusTargets = rowFocusTargets.getValue(row.guid)
                val previousTargets = rows.getOrNull(index - 2)?.let { rowFocusTargets[it.guid] }
                val nextTargets = rows.getOrNull(index + 2)?.let { rowFocusTargets[it.guid] }
                val previousColumnTargets = if (index % 2 == 1) {
                    rows.getOrNull(index - 1)?.let { rowFocusTargets[it.guid] }
                } else null
                val nextColumnTargets = if (index % 2 == 0) {
                    rows.getOrNull(index + 1)?.let { rowFocusTargets[it.guid] }
                } else null
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        actions = actions,
                        focusTargets = focusTargets,
                        previousFocusTargets = previousTargets,
                        nextFocusTargets = nextTargets,
                        nextColumnFocusTargets = nextColumnTargets,
                        previousColumnFocusTargets = previousColumnTargets,
                        isMoving = movingGuid == row.guid,
                        onStartMoving = { startMovement(row.guid, index) },
                        onFinishMoving = ::finishMovement,
                        onMovementKeyEvent = { handleMovementKey(it, grid = true) }
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(reorderableGridState, key = row.guid) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging,
                            isMoving = movingGuid == row.guid
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
                val focusTargets = rowFocusTargets.getValue(row.guid)
                val previousTargets = rows.getOrNull(index - 1)?.let { rowFocusTargets[it.guid] }
                val nextTargets = rows.getOrNull(index + 1)?.let { rowFocusTargets[it.guid] }
                val content: @Composable () -> Unit = {
                    ServerItemRow(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        actions = actions,
                        focusTargets = focusTargets,
                        previousFocusTargets = previousTargets,
                        nextFocusTargets = nextTargets,
                        isMoving = movingGuid == row.guid,
                        onStartMoving = { startMovement(row.guid, index) },
                        onFinishMoving = ::finishMovement,
                        onMovementKeyEvent = { handleMovementKey(it, grid = false) }
                    )
                }
                if (canReorder && reorderableState != null) {
                    ReorderableItem(reorderableState, key = row.guid) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging,
                            isMoving = movingGuid == row.guid
                        ) { content() }
                        ServerItemDivider()
                    }
                } else {
                    content()
                    ServerItemDivider()
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
    focusTargets: ServerRowFocusTargets,
    previousFocusTargets: ServerRowFocusTargets?,
    nextFocusTargets: ServerRowFocusTargets?,
    isMoving: Boolean,
    onStartMoving: () -> Unit,
    onFinishMoving: () -> Unit,
    onMovementKeyEvent: (KeyEvent) -> Boolean
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        doubleColumnDisplay = false,
        actions = actions,
        focusTargets = focusTargets,
        previousFocusTargets = previousFocusTargets,
        nextFocusTargets = nextFocusTargets,
        isMoving = isMoving,
        onStartMoving = onStartMoving,
        onFinishMoving = onFinishMoving,
        onMovementKeyEvent = onMovementKeyEvent
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions,
    focusTargets: ServerRowFocusTargets,
    previousFocusTargets: ServerRowFocusTargets?,
    nextFocusTargets: ServerRowFocusTargets?,
    nextColumnFocusTargets: ServerRowFocusTargets?,
    previousColumnFocusTargets: ServerRowFocusTargets?,
    isMoving: Boolean,
    onStartMoving: () -> Unit,
    onFinishMoving: () -> Unit,
    onMovementKeyEvent: (KeyEvent) -> Boolean
) {
    Column {
        ServerListItem(
            row = row,
            isSelected = isSelected,
            doubleColumnDisplay = true,
            actions = actions,
            focusTargets = focusTargets,
            previousFocusTargets = previousFocusTargets,
            nextFocusTargets = nextFocusTargets,
            nextColumnFocusTargets = nextColumnFocusTargets,
            previousColumnFocusTargets = previousColumnFocusTargets,
            isMoving = isMoving,
            onStartMoving = onStartMoving,
            onFinishMoving = onFinishMoving,
            onMovementKeyEvent = onMovementKeyEvent
        )
        ServerItemDivider()
    }
}

@Composable
private fun ServerItemDivider() {
    AppDivider(
        modifier = Modifier.padding(
            horizontal = 12.dp,
            vertical = if (isTelevisionDevice()) 1.dp else 0.dp
        )
    )
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions,
    focusTargets: ServerRowFocusTargets,
    previousFocusTargets: ServerRowFocusTargets?,
    nextFocusTargets: ServerRowFocusTargets?,
    nextColumnFocusTargets: ServerRowFocusTargets? = null,
    previousColumnFocusTargets: ServerRowFocusTargets? = null,
    isMoving: Boolean,
    onStartMoving: () -> Unit,
    onFinishMoving: () -> Unit,
    onMovementKeyEvent: (KeyEvent) -> Boolean
) {
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
    val isTelevision = isTelevisionDevice()
    val compactActionModifier = if (isTelevision) Modifier else Modifier.size(36.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .dpadFocusOutline(focusRequester = focusTargets.row)
            .dpadHorizontalFocusNavigation(
                onMoveLeft = {
                    previousColumnFocusTargets?.more?.requestFocus()
                        ?: focusTargets.row.requestFocus()
                },
                onMoveRight = {
                    if (doubleColumnDisplay) {
                        focusTargets.more.requestFocus()
                    } else {
                        focusTargets.share.requestFocus()
                    }
                }
            )
            .dpadVerticalFocusNavigation(
                onMoveUp = {
                    previousFocusTargets?.row?.requestFocus() ?: false
                },
                onMoveDown = { nextFocusTargets?.row?.requestFocus() ?: true }
            )
            .then(
                if (isTelevision) Modifier else Modifier.clickable { actions.select(row.guid) }
            )
            .dpadLongPressToMove(
                enabled = isTelevision,
                onClick = { if (isMoving) onFinishMoving() else actions.select(row.guid) },
                onLongPress = onStartMoving,
                onDrop = onFinishMoving,
                onMovementKeyEvent = onMovementKeyEvent
            )
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
                        modifier = compactActionModifier
                            .dpadFocusOutline(focusRequester = focusTargets.more)
                            .dpadHorizontalFocusNavigation(
                                onMoveLeft = { focusTargets.row.requestFocus() },
                                onMoveRight = {
                                    nextColumnFocusTargets?.row?.requestFocus()
                                        ?: focusTargets.more.requestFocus()
                                }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocusTargets?.more?.requestFocus() ?: false },
                                onMoveDown = { nextFocusTargets?.more?.requestFocus() ?: true }
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
                        modifier = compactActionModifier
                            .dpadFocusOutline(focusRequester = focusTargets.share)
                            .dpadHorizontalFocusNavigation(
                                onMoveLeft = { focusTargets.row.requestFocus() },
                                onMoveRight = { focusTargets.edit.requestFocus() }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocusTargets?.share?.requestFocus() ?: false },
                                onMoveDown = { nextFocusTargets?.share?.requestFocus() ?: true }
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
                        modifier = compactActionModifier
                            .dpadFocusOutline(focusRequester = focusTargets.edit)
                            .dpadHorizontalFocusNavigation(
                                onMoveLeft = { focusTargets.share.requestFocus() },
                                onMoveRight = { focusTargets.delete.requestFocus() }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocusTargets?.edit?.requestFocus() ?: false },
                                onMoveDown = { nextFocusTargets?.edit?.requestFocus() ?: true }
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
                        modifier = compactActionModifier
                            .dpadFocusOutline(focusRequester = focusTargets.delete)
                            .dpadHorizontalFocusNavigation(
                                onMoveLeft = { focusTargets.edit.requestFocus() },
                                onMoveRight = { focusTargets.delete.requestFocus() }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { previousFocusTargets?.delete?.requestFocus() ?: false },
                                onMoveDown = { nextFocusTargets?.delete?.requestFocus() ?: true }
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
