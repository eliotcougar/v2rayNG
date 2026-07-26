package com.v2ray.ang.ui.main

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
    groups: List<GroupMapItem>, selectedTabIndex: Int,
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
