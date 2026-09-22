package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit, onNavigate: (MainDestination) -> Unit) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isTelevision = isTelevisionDevice()
    val isRunning = uiState.isRunning
    val displayText = if (isTelevision && uiState.status == MainStatus.Connected) {
        stringResource(R.string.connection_connected_tv)
    } else {
        mainViewModel.formatStatus(uiState.status)
    }
    val selectedGuid = uiState.selectedGuid
    val testDisplayText = uiState.testStatus
        ?.let(mainViewModel::formatStatus)
        ?.replace('\n', ' ')
        .orEmpty()
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = isTelevision || uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = if (!isTelevision) rememberDrawerState(DrawerValue.Closed) else null
    val drawerScope = rememberCoroutineScope()
    val tvDrawerCoordinator = if (isTelevision) rememberMainTvDrawerCoordinator() else null
    val openDrawer: (FocusRequester?) -> Unit = { requester ->
        if (tvDrawerCoordinator != null) tvDrawerCoordinator.openFrom { requester?.requestFocus() ?: false }
        else drawerScope.launch { drawerState?.open() }
    }
    var dialog by remember { mutableStateOf<MainDialog?>(null) }
    var dialogFocusToRestore by remember { mutableStateOf<(() -> Boolean)?>(null) }
    LaunchedEffect(dialogFocusToRestore, isTelevision) {
        val restore = dialogFocusToRestore ?: return@LaunchedEffect
        if (isTelevision) restore()
        dialogFocusToRestore = null
    }
    val requestRemoveServer: (String) -> Unit = { guid ->
        if (confirmRemove) {
            dialog = MainDialog.DeleteServer(guid)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }
    var showSearch by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    val topBarFocus = rememberMainTopBarFocusRequesters(showSearch)

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeFocusGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, isTelevision) {
        if (!isTelevision) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeFocusGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect((tvDrawerCoordinator?.isOpen == true), resumeFocusGeneration, isTelevision) {
        if (!isTelevision || (tvDrawerCoordinator?.isOpen == true)) return@LaunchedEffect
        if (tvDrawerCoordinator?.focusToRestore?.invoke() != true) requestFocusWhenReady(topBarFocus.start)
    }

    BackHandler(enabled = isTelevision && showSearch) {
        onAction(MainAction.Search(""))
        showSearch = false
    }
    BackHandler(enabled = isTelevision && (tvDrawerCoordinator?.isOpen == true)) {
        tvDrawerCoordinator?.closeAndRestore()
    }

    val groupTabTargets = rememberDpadFocusTargets(groups.map { it.id }) { FocusRequester() }
    val groupTabFocusRequesters = groups.map { groupTabTargets.getValue(it.id) }
    val pagerCoordinator = rememberMainPagerCoordinator(
        groups = groups,
        selectedGroupId = uiState.selectedGroupId,
        isTelevision = isTelevision,
        onSelectGroup = { onAction(MainAction.SelectGroup(it)) }
    )
    val selectedGroupIndex = mainSelectedGroupIndex(groups, uiState.selectedGroupId)

    MainDialogs(dialog = dialog, onDismiss = { dialog = null }, onConfirm = { confirmed ->
        dialog = null
        when (confirmed) {
            MainDialog.DeleteAll -> onAction(MainAction.RemoveAllServers)
            MainDialog.DeleteDuplicate -> onAction(MainAction.RemoveDuplicateServers)
            MainDialog.DeleteInvalid -> onAction(MainAction.RemoveInvalidServers)
            is MainDialog.DeleteServer -> onAction(MainAction.RemoveServer(confirmed.guid))
            is MainDialog.Share -> Unit
        }
    })

    (dialog as? MainDialog.Share)?.target?.let { target ->
        ShareMethodDialog(
            guid = target.guid,
            profile = target.profile,
            more = target.more,
            onDismiss = {
                dialog = null
                dialogFocusToRestore = target.restoreFocus
            },
            onActionSelected = { dialog = null },
            onAction = onAction,
            onRemove = requestRemoveServer
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    val mainContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    MainTopBar(
                        isLoading = isLoading,
                        isRunning = isRunning,
                        showSearch = showSearch,
                        searchQuery = uiState.searchQuery,
                        focusRequesters = topBarFocus,
                        onSearchQueryChange = { query ->
                            onAction(MainAction.Search(query))
                        },
                        onSearchClose = {
                            onAction(MainAction.Search(""))
                            showSearch = false
                        },
                        onSearchToggle = { showSearch = it },
                        onOpenDrawer = openDrawer,
                        onMoveDown = {
                            if (groups.size <= 1) false else {
                                groupTabFocusRequesters.getOrNull(selectedGroupIndex)?.requestFocus() ?: false
                            }
                        },
                        onAction = onAction,
                        onBulkDelete = { target ->
                            dialog = when (target) {
                                BulkDeleteTarget.All -> MainDialog.DeleteAll
                                BulkDeleteTarget.Duplicate -> MainDialog.DeleteDuplicate
                                BulkDeleteTarget.Invalid -> MainDialog.DeleteInvalid
                            }
                        }
                    )
                },
                bottomBar = {
                    MainBottomBar(displayText, testDisplayText, isRunning, isDarkTheme, onAction)
                }
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = selectedGroupIndex.coerceIn(0, groups.lastIndex),
                                tabFocusRequesters = groupTabFocusRequesters,
                                onOpenDrawer = openDrawer,
                                onMoveUp = { topBarFocus.start.requestFocus() },
                                onTabClick = { targetIndex ->
                                    groups.getOrNull(targetIndex)?.let { onAction(MainAction.SelectGroup(it.id)) }
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerCoordinator.pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !isTelevision,
                            beyondViewportPageCount = if (isTelevision) 0 else 1,
                            key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager
                            GroupPagerPage(
                                groupId = group.id,
                                mainViewModel = mainViewModel,
                                selectedGuid = selectedGuid,
                                locateTarget = uiState.locateTarget,
                                doubleColumnDisplay = doubleColumnDisplay,
                                revealSelectedGeneration = resumeFocusGeneration,
                                searchQuery = uiState.searchQuery,
                                lazyListStates = pagerCoordinator.lazyListStates,
                                lazyGridStates = pagerCoordinator.lazyGridStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile ->
                                    onAction(MainAction.EditServer(guid, profile))
                                },
                                onShareServer = { guid, profile, more, restore ->
                                    dialog = MainDialog.Share(MainShareTarget(guid, profile, more, restore))
                                },
                                onRemoveServer = requestRemoveServer,
                                onOpenDrawer = { restore -> tvDrawerCoordinator?.openFrom(restore) },
                                onBackFromList = { topBarFocus.start.requestFocus() },
                                onMoveUpFromFirstRow = if (groups.size > 1) {
                                    { groupTabFocusRequesters.getOrNull(page)?.requestFocus() }
                                } else null,
                                contentPadding = PaddingValues(
                                    start = if (isTelevision) 48.dp else 0.dp,
                                    top = if (isTelevision) 16.dp else 0.dp,
                                    end = if (isTelevision) 48.dp else 0.dp,
                                    bottom = 80.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (tvDrawerCoordinator != null) {
        TvMainNavigationDrawer(
            drawerState = tvDrawerCoordinator.state,
            focusGeneration = resumeFocusGeneration,
            onClose = tvDrawerCoordinator::closeAndRestore,
            onNavigate = onNavigate,
            content = mainContent
        )
    } else {
        ModalNavigationDrawer(
            drawerState = requireNotNull(drawerState),
            drawerContent = {
                MainDrawerContent(
                    drawerState = requireNotNull(drawerState),
                    onNavigate = onNavigate
                )
            },
            content = mainContent
        )
    }
}
