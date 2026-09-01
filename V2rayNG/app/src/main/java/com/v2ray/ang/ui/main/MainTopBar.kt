package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.DpadHorizontalDirection
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.adjacentDpadFocusTarget
import com.v2ray.ang.ui.compose.afterDpadPopupDismiss
import com.v2ray.ang.ui.compose.dpadIconButtonFocusOutline
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.launch

internal class MainTopBarFocusRequesters(val navigation: FocusRequester) {
    val searchInput = FocusRequester()
    val searchClear = FocusRequester()
    val search = FocusRequester()
    val add = FocusRequester()
    val more = FocusRequester()
}

@Composable
internal fun rememberMainTopBarFocusRequesters(showSearch: Boolean): MainTopBarFocusRequesters {
    val navigation = rememberDpadFocusRequester(requestFocus = false, requestKey = showSearch)
    return remember(navigation) { MainTopBarFocusRequesters(navigation) }
}

@Composable
internal fun MainTopBar(
    isLoading: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    focusRequesters: MainTopBarFocusRequesters,
    onOpenDrawer: (FocusRequester) -> Unit,
    onMoveDown: () -> Boolean,
    onMoveToService: () -> Unit,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    var showImportMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp
    val hasSearchQuery = searchQuery.isNotEmpty()
    val focusOrder = remember(showSearch, hasSearchQuery, focusRequesters) {
        buildList {
            add(focusRequesters.navigation)
            if (showSearch) {
                add(focusRequesters.searchInput)
                if (hasSearchQuery) add(focusRequesters.searchClear)
            } else add(focusRequesters.search)
            add(focusRequesters.add)
            add(focusRequesters.more)
        }
    }
    fun closeMenuAndMove(current: FocusRequester, direction: DpadHorizontalDirection) {
        showImportMenu = false
        showMenu = false
        val target = adjacentDpadFocusTarget(current, focusOrder, direction)
        scope.launch {
            afterDpadPopupDismiss {
                target?.requestFocus()
                    ?: if (direction == DpadHorizontalDirection.Next && current === focusRequesters.more) {
                        onMoveToService()
                    } else current.requestFocus()
            }
        }
    }
    @Composable
    fun Modifier.topBarNavigation(
        current: FocusRequester,
        onBeforeFirst: (() -> Unit)? = null,
        onAfterLast: (() -> Unit)? = null
    ): Modifier = dpadIconButtonFocusOutline(current)
        .dpadOrderedFocusNavigation(current, focusOrder, onBeforeFirst, onAfterLast)
        .dpadVerticalFocusNavigation(onMoveUp = { true }, onMoveDown = onMoveDown)

    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        searchInputFocusRequester = focusRequesters.searchInput,
        searchClearFocusRequester = focusRequesters.searchClear,
        navigationFocusRequester = focusRequesters.navigation,
        customActionFocusRequesters = if (showSearch) listOf(focusRequesters.add, focusRequesters.more)
        else listOf(focusRequesters.search, focusRequesters.add, focusRequesters.more),
        onMoveDown = onMoveDown,
        navigationIcon = { navigationFocusRequester ->
            if (showSearch) {
                IconButton(
                    onClick = onSearchClose,
                    modifier = Modifier.topBarNavigation(navigationFocusRequester)
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back))
                }
            } else {
                IconButton(
                    onClick = { onOpenDrawer(navigationFocusRequester) },
                    modifier = Modifier.topBarNavigation(
                        navigationFocusRequester,
                        onBeforeFirst = { onOpenDrawer(navigationFocusRequester) }
                    )
                ) {
                    Icon(painterResource(R.drawable.ic_menu_24dp), contentDescription = stringResource(R.string.acc_open_menu))
                }
            }
        },
        actions = {
            if (!showSearch) {
                IconButton(
                    onClick = { onSearchToggle(true) },
                    modifier = Modifier.topBarNavigation(focusRequesters.search)
                ) {
                    Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = stringResource(R.string.acc_search))
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showImportMenu = true },
                    modifier = Modifier.topBarNavigation(focusRequesters.add)
                ) {
                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add))
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    scrollState = importMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(importMenuScrollState)
                ) {
                    ImportMenuContent(
                        itemModifier = Modifier.dpadPopupHorizontalNavigation(
                            onMovePrevious = {
                                closeMenuAndMove(focusRequesters.add, DpadHorizontalDirection.Previous)
                            },
                            onMoveNext = {
                                closeMenuAndMove(focusRequesters.add, DpadHorizontalDirection.Next)
                            }
                        ),
                        onAction = { action ->
                            showImportMenu = false
                            onAction(action)
                        }
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.topBarNavigation(
                        focusRequesters.more,
                        onAfterLast = onMoveToService
                    )
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.acc_more))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    scrollState = moreMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(moreMenuScrollState)
                ) {
                    MoreMenuContent(
                        itemModifier = Modifier.dpadPopupHorizontalNavigation(
                            onMovePrevious = {
                                closeMenuAndMove(focusRequesters.more, DpadHorizontalDirection.Previous)
                            },
                            onMoveNext = {
                                closeMenuAndMove(focusRequesters.more, DpadHorizontalDirection.Next)
                            }
                        )
                    ) { action ->
                        showMenu = false
                        onMoreMenuAction(action)
                    }
                }
            }
        }
    )
}
