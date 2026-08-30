package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.compose.AppDivider
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice

@Composable
fun GroupTabBar(
    groups: List<GroupMapItem>, selectedTabIndex: Int,
    mainViewModel: MainViewModel,
    tabFocusRequesters: List<FocusRequester>, onOpenDrawer: (FocusRequester) -> Unit,
    onMoveUp: () -> Unit, onTabClick: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val isTelevision = isTelevisionDevice()
    val selectedIndex = selectedTabIndex.coerceIn(0, groups.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
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
                val serverFlow = remember(group.id, mainViewModel) { mainViewModel.serversForGroup(group.id) }
                GroupTabItem(
                    group = group,
                    serverFlow = serverFlow,
                    selected = index == selectedIndex,
                    modifier = tabModifier,
                    onClick = { onTabClick(index) }
                )
            }
        }
    }
}

@Composable
private fun GroupTabItem(
    group: GroupMapItem, selected: Boolean,
    serverFlow: StateFlow<List<ServersCache>>, modifier: Modifier = Modifier, onClick: () -> Unit
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
            modifier = modifier
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
