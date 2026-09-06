package com.v2ray.ang.ui.main

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.compose.AppDivider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice

@Composable
fun GroupTabBar(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>, selectedTabIndex: Int,
    tabFocusRequesters: List<FocusRequester>, onOpenDrawer: (FocusRequester) -> Unit,
    onMoveUp: () -> Unit, onTabClick: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val isTelevision = isTelevisionDevice()
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    if (!isTelevision) {
        MobileGroupTabBar(groups, selectedTabIndex, mainViewModel, onTabClick, modifier)
        return
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(items = groups, key = { _, group -> group.id }) { index, group ->
            val focusRequester = tabFocusRequesters[index]
            val tabModifier = if (isTelevision) {
                Modifier
                    .dpadFocusOutline(focusRequester = focusRequester, cornerRadius = 20.dp)
                    .onFocusChanged { if (it.isFocused && index != selectedIndex) onTabClick(index) }
                    .dpadOrderedFocusNavigation(
                        current = focusRequester,
                        order = tabFocusRequesters,
                        onBeforeFirst = { onOpenDrawer(focusRequester) }
                    )
                    .dpadVerticalFocusNavigation(
                        onMoveUp = { onMoveUp(); true },
                        onMoveDown = { false }
                    )
            } else {
                Modifier
            }
            GroupTabItem(
                group = group,
                selected = index == selectedIndex,
                showInactiveIndicator = isTelevision,
                modifier = tabModifier,
                onClick = { onTabClick(index) }
            )
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem, selected: Boolean,
    showInactiveIndicator: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val text = if (group.id.isEmpty()) stringResource(R.string.filter_config_all)
    else "${group.remarks} (${group.serverCount})"

    Box(Modifier.widthIn(min = 56.dp)) {
        Tab(
            selected = selected,
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            text = { Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
        )
        if (selected || showInactiveIndicator) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun MobileGroupTabBar(
    groups: List<GroupMapItem>,
    selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val selectedGroupId = groups[selectedIndex].id
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    // React to selection, not scroll changes: browsing other tabs must not snap back.
    LaunchedEffect(selectedGroupId, selectedIndex, groups.size) {
        val layout = snapshotFlow { listState.layoutInfo }.first {
            it.totalItemsCount == groups.size && it.viewportSize.width > 0
        }
        val item = layout.visibleItemsInfo.firstOrNull { it.key == selectedGroupId }
        if (item == null) {
            listState.animateScrollToItem(selectedIndex)
        } else {
            listState.animateScrollBy(
                groupTabScrollDistance(item.offset, item.size, layout.viewportStartOffset, layout.viewportEndOffset).toFloat()
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        AppDivider(Modifier.align(Alignment.BottomCenter))
        // Keep the collection semantics so TalkBack can reach groups beyond the viewport.
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = groups,
                key = { _, group -> group.id },
            ) { index, group ->
                val serverFlow = remember(group.id, mainViewModel) {
                    mainViewModel.serversForGroup(group.id)
                }
                MobileGroupTabItem(
                    group = group,
                    selected = index == selectedIndex,
                    serverFlow = serverFlow,
                    onClick = { onTabClick(index) }
                )
            }
        }
    }
}

/** Reveal a clipped tab; align an oversized tab's start instead of hiding its name. */
internal fun groupTabScrollDistance(start: Int, size: Int, viewportStart: Int, viewportEnd: Int): Int = when {
    start < viewportStart -> start - viewportStart
    start + size > viewportEnd -> minOf(start - viewportStart, start + size - viewportEnd)
    else -> 0
}

@Composable
private fun MobileGroupTabItem(
    group: GroupMapItem,
    selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>,
    onClick: () -> Unit
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val accessibilityLabel = pluralStringResource(
        R.plurals.acc_group_tab,
        servers.size,
        group.remarks,
        servers.size,
    )
    val text = if (group.id.isEmpty()) {
        group.remarks
    } else {
        "${group.remarks} (${servers.size})"
    }
    val indicatorColor = MaterialTheme.colorScheme.secondary

    Box(
        Modifier
            .widthIn(min = 56.dp)
            .drawWithContent {
                drawContent()
                if (selected) {
                    val indicatorHeight = 3.dp.toPx()
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(0f, size.height - indicatorHeight),
                        size = Size(size.width, indicatorHeight),
                        cornerRadius = CornerRadius(indicatorHeight / 2f)
                    )
                }
            }
    ) {
        Tab(
            selected = selected,
            onClick = onClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = accessibilityLabel },
            text = {
                Text(
                    text = text,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}
