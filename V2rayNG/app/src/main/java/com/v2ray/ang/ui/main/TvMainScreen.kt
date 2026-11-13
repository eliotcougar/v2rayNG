package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.requestFocusWhenReady

@Composable
internal fun TvMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val groupIds = groups.map { it.id }
    val selectedGroupIndex = mainSelectedGroupIndex(groups, uiState.selectedGroupId)
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val scrollStates = rememberMainGroupScrollStates(groupIds)
    val drawer = rememberMainTvDrawerCoordinator()
    var showSearch by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    fun closeSearch() {
        onAction(MainAction.Search(""))
        showSearch = false
    }

    val topBarFocus = rememberMainTopBarFocusRequesters(showSearch)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeFocusGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeFocusGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDrawerOpen = drawer.isOpen
    LaunchedEffect(drawer, isDrawerOpen) {
        if (isDrawerOpen) return@LaunchedEffect
        val restore = drawer.takeFocusToRestore() ?: return@LaunchedEffect
        if (!restore()) requestFocusWhenReady(topBarFocus.start)
    }
    var dialogFocusToRestore by remember { mutableStateOf<(() -> Boolean)?>(null) }
    LaunchedEffect(dialogFocusToRestore) {
        val restore = dialogFocusToRestore ?: return@LaunchedEffect
        restore()
        dialogFocusToRestore = null
    }

    BackHandler(enabled = showSearch, onBack = ::closeSearch)
    BackHandler(enabled = isDrawerOpen, onBack = drawer::close)

    val tabState = if (groups.size > 1) rememberLazyListState(selectedGroupIndex) else null
    val tabTargets = if (tabState != null) rememberDpadFocusTargets(groupIds) { FocusRequester() } else emptyMap()
    val tabOrder = if (tabState != null) groupIds.map(tabTargets::getValue) else emptyList()
    val requestTabFocus = if (tabState != null) {
        rememberLazyDpadFocus(tabOrder.map { listOf(it) }) { tabState.scrollToItem(it) }
    } else null
    fun focusGroupTab(groupId: String): Boolean = tabTargets[groupId]?.let { requestTabFocus?.invoke(it) } ?: false

    MainDialogs(
        confirmRemove = true,
        shareQRCodeBitmap = uiState.shareQRCodeBitmap,
        onAction = onAction,
        onRestoreFocus = { dialogFocusToRestore = it }
    ) { dialogs ->
        TvMainNavigationDrawer(
            drawerState = drawer.state,
            focusGeneration = resumeFocusGeneration,
            onClose = drawer::close,
            onNavigate = onNavigate
        ) {
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    TvMainTopBar(
                        isLoading = isLoading,
                        isRunning = uiState.isRunning,
                        showSearch = showSearch,
                        searchQuery = uiState.searchQuery,
                        focusRequesters = topBarFocus,
                        onSearchQueryChange = { onAction(MainAction.Search(it)) },
                        onSearchClose = ::closeSearch,
                        onSearchOpen = { showSearch = true },
                        onOpenDrawer = { drawer.openFrom { topBarFocus.start.requestFocus() } },
                        onMoveDown = { selectedGroup?.let { focusGroupTab(it.id) } ?: false },
                        onAction = onAction,
                        onBulkDelete = dialogs.bulkDelete
                    )
                },
                bottomBar = {
                    val displayText = if (uiState.status == MainStatus.Connected) {
                        stringResource(R.string.connection_connected_tv)
                    } else {
                        mainViewModel.formatStatus(uiState.status)
                    }
                    val testDisplayText = uiState.testStatus?.let(mainViewModel::formatStatus).orEmpty()
                    MainTvStatusBar(displayText, testDisplayText.replace('\n', ' '))
                }
            ) { innerPadding ->
                if (selectedGroup != null) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (tabState != null && requestTabFocus != null) {
                            TvGroupTabBar(
                                groups, selectedGroupIndex, tabState, tabOrder, requestTabFocus,
                                onOpenDrawer = { target -> drawer.openFrom { requestTabFocus(target) } },
                                onMoveUp = { topBarFocus.start.requestFocus() },
                                onSelectGroup = { onAction(MainAction.SelectGroup(it)) }
                            )
                        }
                        key(selectedGroup.id) {
                            GroupPagerPage(
                                group = selectedGroup,
                                mainViewModel = mainViewModel,
                                uiState = uiState,
                                scrollStates = scrollStates,
                                onAction = onAction,
                                dialogs = dialogs,
                                contentPadding = PaddingValues(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 80.dp),
                                tvNavigation = TvProfileNavigation(
                                    revealSelectedGeneration = resumeFocusGeneration,
                                    openDrawer = drawer::openFrom,
                                    backFromList = { topBarFocus.start.requestFocus() },
                                    moveUpFromFirstRow = if (tabState != null) {
                                        { focusGroupTab(selectedGroup.id) }
                                    } else null
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
