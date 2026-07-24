package com.v2ray.ang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalAppSnackbar
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TV_DRAWER_MOTION_DURATION_MILLIS = 160

@Suppress("DEPRECATION")
private suspend fun DrawerState.animateForTelevision(targetValue: DrawerValue) {
    animateTo(targetValue, tween(TV_DRAWER_MOTION_DURATION_MILLIS))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val isTelevision = isTelevisionDevice()
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
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

    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(mainViewModel, context) {
        mainViewModel.serviceStatusMessages.collect { message ->
            val text = context.getString(
                message.stringRes,
                *message.formatArgs.toTypedArray()
            )
            if (message.isError) snackbar.showError(text) else snackbar.showSuccess(text)
        }
    }

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String) -> Unit = { guid ->
        if (confirmRemove) showRemoveConfirm = guid else onAction(MainAction.RemoveServer(guid))
    }
    var mainFocusToRestore by remember { mutableStateOf<FocusRequester?>(null) }

    val topBarFocus = rememberMainTopBarFocusRequesters(showSearch)
    val openDrawerFrom: (FocusRequester?) -> Unit = { focusRequester ->
        mainFocusToRestore = focusRequester
        scope.launch {
            if (isTelevision) {
                drawerState.animateForTelevision(DrawerValue.Open)
            } else {
                drawerState.open()
            }
        }
    }
    val closeDrawerAndRestore: () -> Unit = {
        val focusRequester = mainFocusToRestore
        scope.launch {
            if (isTelevision) {
                drawerState.animateForTelevision(DrawerValue.Closed)
            } else {
                drawerState.close()
            }
            focusRequester?.requestFocus()
        }
    }

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
    LaunchedEffect(drawerState.targetValue, resumeFocusGeneration, isTelevision) {
        if (!isTelevision || drawerState.targetValue != DrawerValue.Closed) return@LaunchedEffect
        val requester = mainFocusToRestore ?: topBarFocus.start
        requestFocusWhenReady(requester, topBarFocus.start)
    }

    BackHandler(enabled = isTelevision && showSearch) {
        searchQuery = ""
        onAction(MainAction.Search(""))
        showSearch = false
    }
    BackHandler(enabled = isTelevision && drawerState.isOpen) {
        closeDrawerAndRestore()
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )
    val groupTabFocusRequesters = remember(groups.map { it.id }) {
        List(groups.size) { FocusRequester() }
    }

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    val selectedGroupIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
        .takeIf { it >= 0 } ?: 0
    LaunchedEffect(groups, uiState.selectedGroupId, isTelevision) {
        if (groups.isEmpty()) return@LaunchedEffect
        if (pagerState.settledPage != selectedGroupIndex) {
            pagerState.navigateToPageOptimized(
                targetPage = selectedGroupIndex,
                animateAdjacentPage = !isTelevision
            )
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                isOpen = drawerState.targetValue == DrawerValue.Open,
                focusGeneration = resumeFocusGeneration,
                onClose = closeDrawerAndRestore,
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
                        searchQuery = searchQuery,
                        focusRequesters = topBarFocus,
                        onSearchQueryChange = { query ->
                            searchQuery = query
                            onAction(MainAction.Search(query))
                        },
                        onSearchClose = {
                            searchQuery = ""
                            onAction(MainAction.Search(""))
                            showSearch = false
                        },
                        onSearchToggle = { showSearch = it },
                        onOpenDrawer = openDrawerFrom,
                        onMoveDown = {
                            if (groups.size <= 1) {
                                false
                            } else {
                                groupTabFocusRequesters
                                    .getOrNull(pagerState.currentPage)
                                     ?.requestFocus()
                                     ?: false
                             }
                        },
                        onAction = onAction,
                        onMoreMenuAction = { action ->
                            when (action) {
                                MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                                MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                                MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                                MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
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
                bottomBar = {
                    MainBottomBar(
                        displayText = displayText,
                        isRunning = isRunning,
                        isDarkTheme = isDarkTheme,
                        onAction = onAction
                    )
                },
                floatingActionButton = {}
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (groups.size > 1) {
                            GroupTabBar(
                                groups = groups,
                                selectedTabIndex = selectedGroupIndex.coerceIn(0, groups.lastIndex),
                                mainViewModel = mainViewModel,
                                tabFocusRequesters = groupTabFocusRequesters,
                                onOpenDrawer = { openDrawerFrom(it) },
                                onMoveUp = { topBarFocus.start.requestFocus() },
                                onTabClick = { targetIndex ->
                                    groups.getOrNull(targetIndex)?.let { targetGroup ->
                                        onAction(MainAction.SelectGroup(targetGroup.id))
                                    }
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
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
                                searchQuery = searchQuery,
                                lazyListStates = lazyListStates,
                                lazyGridStates = lazyGridStates,
                                onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                                onEditServer = { guid, profile ->
                                    onAction(MainAction.EditServer(guid, profile))
                                },
                                onShareServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, false)
                                },
                                onMoreServer = { guid, profile ->
                                    shareTarget = Triple(guid, profile, true)
                                },
                                onRemoveServer = removeServer,
                                onOpenDrawer = { openDrawerFrom(it) },
                                onMoveUpFromFirstRow = if (groups.size > 1) {
                                    {
                                        groupTabFocusRequesters
                                            .getOrNull(page)
                                            ?.requestFocus()
                                    }
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
