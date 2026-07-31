package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalAppSnackbar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.requestFocusWhenReady

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
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = isTelevision || uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val resources = LocalResources.current
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(mainViewModel, resources) {
        mainViewModel.serviceStatusMessages.collect { message ->
            val text = resources.getString(message.stringRes, *message.formatArgs.toTypedArray())
            snackbar.show(text, if (message.isError) ToastType.ERROR else ToastType.SUCCESS)
        }
    }

    val isDarkTheme = LocalDarkTheme.current
    val drawerCoordinator = rememberMainDrawerCoordinator(isTelevision)
    val dialogState = rememberMainDialogState()
    var dialogFocusToRestore by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(dialogFocusToRestore, isTelevision) {
        val requester = dialogFocusToRestore ?: return@LaunchedEffect
        if (isTelevision) requestFocusWhenReady(requester)
        dialogFocusToRestore = null
    }
    val requestRemoveServer: (String) -> Unit = { guid ->
        if (confirmRemove) {
            dialogState.show(MainDialog.DeleteServer(guid))
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
    LaunchedEffect(drawerCoordinator.state.targetValue, resumeFocusGeneration, isTelevision) {
        if (!isTelevision || drawerCoordinator.state.targetValue != DrawerValue.Closed) {
            return@LaunchedEffect
        }
        val requester = drawerCoordinator.focusToRestore ?: topBarFocus.start
        requestFocusWhenReady(requester, topBarFocus.start)
    }

    BackHandler(enabled = isTelevision && showSearch) {
        onAction(MainAction.Search(""))
        showSearch = false
    }
    BackHandler(enabled = isTelevision && drawerCoordinator.isOpen) {
        drawerCoordinator.closeAndRestore()
    }

    val groupTabFocusRequesters = remember(groups.map { it.id }) { List(groups.size) { FocusRequester() } }
    val pagerCoordinator = rememberMainPagerCoordinator(
        groups = groups,
        selectedGroupId = uiState.selectedGroupId,
        isTelevision = isTelevision,
        onSelectGroup = { onAction(MainAction.SelectGroup(it)) }
    )
    val selectedGroupIndex = mainSelectedGroupIndex(groups, uiState.selectedGroupId)

    MainDialogs(dialog = dialogState.current, onDismiss = dialogState::dismiss, onConfirm = { dialog ->
        dialogState.dismiss()
        when (dialog) {
            MainDialog.DeleteAll -> onAction(MainAction.RemoveAllServers)
            MainDialog.DeleteDuplicate -> onAction(MainAction.RemoveDuplicateServers)
            MainDialog.DeleteInvalid -> onAction(MainAction.RemoveInvalidServers)
            is MainDialog.DeleteServer -> onAction(MainAction.RemoveServer(dialog.guid))
            is MainDialog.Share -> Unit
        }
    })

    (dialogState.current as? MainDialog.Share)?.target?.let { target ->
        ShareMethodDialog(
            guid = target.guid,
            profile = target.profile,
            more = target.more,
            onDismiss = {
                dialogState.dismiss()
                dialogFocusToRestore = target.restoreFocusRequester
            },
            onActionSelected = dialogState::dismiss,
            onAction = onAction,
            onRemove = requestRemoveServer
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerCoordinator.state,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerCoordinator.state,
                isOpen = drawerCoordinator.isTargetOpen,
                focusGeneration = resumeFocusGeneration,
                onClose = drawerCoordinator::closeAndRestore,
                onNavigate = onNavigate
            )
        }
    ) {
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
                        onOpenDrawer = drawerCoordinator::openFrom,
                        onMoveDown = {
                            if (groups.size <= 1) false else {
                                groupTabFocusRequesters.getOrNull(selectedGroupIndex)?.requestFocus() ?: false
                            }
                        },
                        onAction = onAction,
                        onMoreMenuAction = { action ->
                            when (action) {
                                MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                                MainMoreMenuAction.DeleteAll -> dialogState.show(MainDialog.DeleteAll)
                                MainMoreMenuAction.DeleteDuplicate -> dialogState.show(MainDialog.DeleteDuplicate)
                                MainMoreMenuAction.DeleteInvalid -> dialogState.show(MainDialog.DeleteInvalid)
                                MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                                MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                                MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                                MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                                MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                                MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                                MainMoreMenuAction.Exit -> onAction(MainAction.Exit)
                            }
                        }
                    )
                },
                bottomBar = { MainBottomBar(displayText, isRunning, isDarkTheme, onAction) },
                floatingActionButton = {}
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = selectedGroupIndex.coerceIn(0, groups.lastIndex),
                                tabFocusRequesters = groupTabFocusRequesters,
                                onOpenDrawer = drawerCoordinator::openFrom,
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
                                onShareServer = { guid, profile, restoreFocusRequester ->
                                    dialogState.show(
                                        MainDialog.Share(
                                            MainShareTarget(
                                                guid,
                                                profile,
                                                more = false,
                                                restoreFocusRequester
                                            )
                                        )
                                    )
                                },
                                onMoreServer = { guid, profile, restoreFocusRequester ->
                                    dialogState.show(
                                        MainDialog.Share(
                                            MainShareTarget(
                                                guid,
                                                profile,
                                                more = true,
                                                restoreFocusRequester
                                            )
                                        )
                                    )
                                },
                                onRemoveServer = requestRemoveServer,
                                onOpenDrawer = drawerCoordinator::openFrom,
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
            if (isTelevision && !showSearch) {
                TvDrawerEdgePeek(modifier = Modifier.align(Alignment.CenterStart))
            }
        }
    }
}
