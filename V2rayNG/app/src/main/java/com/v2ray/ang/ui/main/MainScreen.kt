package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
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
import com.v2ray.ang.ui.compose.requestFocusWhenReady

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit, onNavigate: (MainDestination) -> Unit) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isTelevision = isTelevisionDevice()
    val isRunning = uiState.isRunning
    val displayText = if (isRunning && uiState.connectionTargetText.isNotBlank()) {
        stringResource(R.string.connection_connected_via, uiState.connectionTargetText)
    } else if (isTelevision && isRunning) {
        stringResource(R.string.connection_connected_tv)
    } else {
        mainViewModel.formatStatus(accessibilityConnectionStatus(isRunning))
    }
    val accessibilityText = mainViewModel.formatConnectionStatusForAccessibility(isRunning)
    val selectedGuid = uiState.selectedGuid
    val testDisplayText = uiState.testStatus
        ?.let(mainViewModel::formatStatus)
        ?.replace('\n', ' ')
        .orEmpty()
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = isTelevision || uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val drawerCoordinator = rememberMainDrawerCoordinator()
    val tvDrawerCoordinator = rememberMainTvDrawerCoordinator()
    val openDrawer: (FocusRequester?) -> Unit = if (isTelevision) {
        tvDrawerCoordinator::openFrom
    } else {
        { drawerCoordinator.open() }
    }
    val dialogState = rememberMainDialogState()
    var dialogFocusToRestore by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(dialogFocusToRestore, isTelevision) {
        val requester = dialogFocusToRestore ?: return@LaunchedEffect
        if (isTelevision) requestFocusWhenReady(requester)
        dialogFocusToRestore = null
    }
    val requestRemoveServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            dialogState.show(MainDialog.DeleteServer(guid, profileName))
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
    LaunchedEffect(tvDrawerCoordinator.isOpen, resumeFocusGeneration, isTelevision) {
        if (!isTelevision || tvDrawerCoordinator.isOpen) return@LaunchedEffect
        val requester = tvDrawerCoordinator.focusToRestore ?: topBarFocus.start
        requestFocusWhenReady(requester, topBarFocus.start)
    }

    val latestOnAction by rememberUpdatedState(onAction)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> latestOnAction(MainAction.MainUiVisibilityChanged(true))
                Lifecycle.Event.ON_STOP -> latestOnAction(MainAction.MainUiVisibilityChanged(false))
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            latestOnAction(MainAction.MainUiVisibilityChanged(true))
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestOnAction(MainAction.MainUiVisibilityChanged(false))
        }
    }

    BackHandler(enabled = isTelevision && showSearch) {
        onAction(MainAction.Search(""))
        showSearch = false
    }
    BackHandler(enabled = isTelevision && tvDrawerCoordinator.isOpen) {
        tvDrawerCoordinator.closeAndRestore()
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
                            dialogState.show(when (target) {
                                BulkDeleteTarget.All -> MainDialog.DeleteAll
                                BulkDeleteTarget.Duplicate -> MainDialog.DeleteDuplicate
                                BulkDeleteTarget.Invalid -> MainDialog.DeleteInvalid
                            })
                        }
                    )
                },
                bottomBar = {
                    MainBottomBar(
                        displayText = displayText,
                        testDisplayText = testDisplayText,
                        accessibilityText = accessibilityText,
                        testAnnouncements = mainViewModel.testAnnouncements,
                        formatTestAnnouncement = mainViewModel::formatTestAnnouncement,
                        isRunning = isRunning,
                        isDarkTheme = isDarkTheme,
                        onAction = onAction
                    )
                }
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                mainViewModel = mainViewModel,
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
                                onOpenDrawer = openDrawer,
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

    if (isTelevision) {
        TvMainNavigationDrawer(
            drawerState = tvDrawerCoordinator.state,
            focusGeneration = resumeFocusGeneration,
            onClose = tvDrawerCoordinator::closeAndRestore,
            onNavigate = onNavigate,
            content = mainContent
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerCoordinator.state,
            drawerContent = {
                MainDrawerContent(
                    drawerState = drawerCoordinator.state,
                    onNavigate = onNavigate
                )
            },
            content = mainContent
        )
    }
}
