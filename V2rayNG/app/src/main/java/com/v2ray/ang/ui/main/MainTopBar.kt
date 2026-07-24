package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.dpadHorizontalFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.verticalScrollbar

internal class MainTopBarFocusRequesters(val start: FocusRequester) {
    val test = FocusRequester()
    val search = FocusRequester()
    val add = FocusRequester()
    val restart = FocusRequester()
    val more = FocusRequester()
}

private fun List<FocusRequester>.adjacentTo(
    current: FocusRequester,
    offset: Int
): FocusRequester? {
    return getOrNull(indexOf(current) + offset)
}

@Composable
private fun Modifier.inMainTopBarFocusOrder(
    current: FocusRequester,
    order: List<FocusRequester>,
    onBeforeFirst: (() -> Unit)? = null,
    onAfterLast: (() -> Unit)? = null
): Modifier {
    return dpadHorizontalFocusNavigation(
        onMoveLeft = {
            order.adjacentTo(current, -1)?.requestFocus()
                ?: onBeforeFirst?.invoke()
                ?: current.requestFocus()
        },
        onMoveRight = {
            order.adjacentTo(current, 1)?.requestFocus()
                ?: onAfterLast?.invoke()
                ?: current.requestFocus()
        }
    )
}

@Composable
internal fun rememberMainTopBarFocusRequesters(showSearch: Boolean): MainTopBarFocusRequesters {
    val start = rememberDpadFocusRequester(
        requestFocus = !showSearch,
        requestKey = showSearch
    )
    return remember(start) { MainTopBarFocusRequesters(start) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(
    isLoading: Boolean,
    isRunning: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    focusRequesters: MainTopBarFocusRequesters,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onOpenDrawer: (FocusRequester?) -> Unit,
    onMoveDown: () -> Boolean,
    onAction: (MainAction) -> Unit,
    onMoreMenuAction: (MainMoreMenuAction) -> Unit
) {
    val isTelevision = isTelevisionDevice()
    var showImportMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val moreMenuScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight =
        LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp
    val moveDownModifier = Modifier.dpadVerticalFocusNavigation(
        onMoveUp = { false },
        onMoveDown = onMoveDown
    )
    val focusOrder = remember(isRunning, focusRequesters) {
        buildList {
            add(focusRequesters.start)
            if (isRunning) add(focusRequesters.test)
            add(focusRequesters.search)
            add(focusRequesters.add)
            if (isRunning) add(focusRequesters.restart)
            add(focusRequesters.more)
        }
    }

    AppTopBar(
        title = stringResource(R.string.title_server),
        onBackClick = {},
        initialFocus = !isTelevision,
        isLoading = isLoading,
        isSearchActive = showSearch,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        titleContent = if (isTelevision) {
            {
                Box(
                    modifier = Modifier.height(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontFamily = FontFamily(Font(R.font.montserrat_thin)),
                        fontWeight = FontWeight.Thin,
                        fontSize = 40.sp,
                        lineHeight = 48.sp,
                        maxLines = 1
                    )
                }
            }
        } else null,
        navigationIcon = { navigationFocusRequester ->
            when {
                showSearch -> AppIconButton(
                    icon = painterResource(R.drawable.ic_arrow_back_24dp),
                    label = stringResource(R.string.action_back),
                    onClick = onSearchClose,
                    focusRequester = navigationFocusRequester,
                    modifier = moveDownModifier
                )

                !isTelevision -> AppIconButton(
                    icon = painterResource(R.drawable.ic_menu_24dp),
                    label = stringResource(R.string.action_menu),
                    onClick = { onOpenDrawer(null) },
                    focusRequester = navigationFocusRequester,
                    modifier = moveDownModifier
                )
            }
        },
        actions = {
            if (isTelevision && !showSearch) {
                AppIconButton(
                    icon = if (isRunning) {
                        painterResource(R.drawable.ic_stop_24dp)
                    } else {
                        painterResource(R.drawable.ic_play_24dp)
                    },
                    label = if (isRunning) {
                        stringResource(R.string.action_stop_service)
                    } else {
                        stringResource(R.string.tasker_start_service)
                    },
                    onClick = { onAction(MainAction.ToggleService) },
                    focusRequester = focusRequesters.start,
                    modifier = moveDownModifier.inMainTopBarFocusOrder(
                        current = focusRequesters.start,
                        order = focusOrder,
                        onBeforeFirst = { onOpenDrawer(focusRequesters.start) }
                    ),
                    containerColor = if (isRunning) colorFabActive else Color.Transparent,
                    contentColor = if (isRunning) Color.White else null
                )
                if (isRunning) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_stopwatch_24dp),
                        label = stringResource(R.string.connection_test_pending),
                        onClick = { onAction(MainAction.TestCurrentServer) },
                        focusRequester = focusRequesters.test,
                        modifier = moveDownModifier.inMainTopBarFocusOrder(
                            current = focusRequesters.test,
                            order = focusOrder
                        )
                    )
                }
                AppIconButton(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    label = stringResource(R.string.menu_item_search),
                    onClick = { onSearchToggle(true) },
                    focusRequester = focusRequesters.search,
                    modifier = moveDownModifier.inMainTopBarFocusOrder(
                        current = focusRequesters.search,
                        order = focusOrder
                    )
                )
            } else if (!showSearch) {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    label = stringResource(R.string.menu_item_search),
                    onClick = { onSearchToggle(true) },
                    modifier = moveDownModifier
                )
            }

            if (!isTelevision || !showSearch) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_add_24dp),
                        label = stringResource(R.string.menu_item_add_config),
                        onClick = { showImportMenu = true },
                        focusRequester = focusRequesters.add,
                        modifier = moveDownModifier.inMainTopBarFocusOrder(
                            current = focusRequesters.add,
                            order = focusOrder
                        )
                    )
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
                            onAction = { action ->
                                showImportMenu = false
                                onAction(action)
                            }
                        )
                    }
                }
                if (isTelevision && isRunning) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_restore_24dp),
                        label = stringResource(R.string.title_service_restart),
                        onClick = { onAction(MainAction.RestartService) },
                        focusRequester = focusRequesters.restart,
                        modifier = moveDownModifier.inMainTopBarFocusOrder(
                            current = focusRequesters.restart,
                            order = focusOrder
                        )
                    )
                }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_more_vert_24dp),
                        label = stringResource(R.string.action_more),
                        onClick = { showMenu = true },
                        focusRequester = focusRequesters.more,
                        modifier = moveDownModifier.inMainTopBarFocusOrder(
                            current = focusRequesters.more,
                            order = focusOrder
                        )
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        scrollState = moreMenuScrollState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .heightIn(max = maxMenuHeight)
                            .dpadPopupHorizontalNavigation(onMovePrevious = {
                                showMenu = false
                                focusOrder.adjacentTo(focusRequesters.more, -1)?.requestFocus()
                            })
                            .verticalScrollbar(moreMenuScrollState)
                    ) {
                        MoreMenuContent(
                            isRunning = isRunning,
                            isTelevision = isTelevision,
                            onSelected = { action ->
                                showMenu = false
                                onMoreMenuAction(action)
                            }
                        )
                    }
                }
            }
        }
    )
}
