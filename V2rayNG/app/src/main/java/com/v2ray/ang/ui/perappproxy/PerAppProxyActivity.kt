package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadHorizontalFocusNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

private enum class PerAppMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection),
    SelectProxyApps(R.string.menu_item_select_proxy_app),
    ImportSelection(R.string.menu_item_import_proxy_app),
    ExportSelection(R.string.menu_item_export_proxy_app)
}

class PerAppProxyActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
        val perAppProxyEnabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypassApps by viewModel.bypassApps.collectAsStateWithLifecycle()

        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            blacklist = blacklist,
            perAppProxyEnabled = perAppProxyEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onPerAppProxyChanged = { viewModel.setPerAppProxyEnabled(it) },
            onBypassAppsChanged = { viewModel.setBypassAppsEnabled(it) },
            onInfoClick = {
                toastInfo(R.string.summary_pref_per_app_proxy)
            },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(this) },
            onImportProxyApp = {
                val content = Utils.getClipboard(applicationContext)
                viewModel.importProxyApp(content, this)
            },
            onExportProxyApp = {
                val export = viewModel.exportProxyApp()
                Utils.setClipboard(applicationContext, export)
                toastSuccess(R.string.toast_success)
            }
        )
    }
}

@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    blacklist: Set<String>,
    perAppProxyEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showInfoPopup by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isTelevision = isTelevisionDevice()
    val backFocusRequester = rememberDpadFocusRequester(requestFocus = !showSearch, requestKey = showSearch)
    val searchFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }
    val perAppFocusRequester = remember { FocusRequester() }
    val bypassFocusRequester = remember { FocusRequester() }
    val infoFocusRequester = remember { FocusRequester() }
    val modeFocusOrder = remember {
        listOf(perAppFocusRequester, bypassFocusRequester, infoFocusRequester)
    }
    val packageNames = apps.map { it.packageName }
    val rowFocusRequesters = remember(packageNames) {
        packageNames.associateWith { FocusRequester() }
    }
    val focusFirstApp = {
        apps.firstOrNull()?.let { rowFocusRequesters[it.packageName]?.requestFocus() } ?: true
    }
    val topBarFocusOrder = remember(backFocusRequester, searchFocusRequester, moreFocusRequester, showSearch) {
        if (showSearch) {
            listOf(backFocusRequester, moreFocusRequester)
        } else {
            listOf(backFocusRequester, searchFocusRequester, moreFocusRequester)
        }
    }

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
                onBackClick = onBackClick,
                initialFocus = false,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    onSearch(query)
                },
                onSearchClose = {
                    searchQuery = ""
                    onSearch("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                navigationFocusRequester = backFocusRequester,
                customActionFocusRequesters = if (showSearch) {
                    listOf(moreFocusRequester)
                } else {
                    listOf(searchFocusRequester, moreFocusRequester)
                },
                onMoveDown = perAppFocusRequester::requestFocus,
                navigationIcon = {
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_arrow_back_24dp),
                        label = stringResource(R.string.action_back),
                        focusRequester = backFocusRequester,
                        modifier = Modifier.dpadTopBarFocusNavigation(
                            backFocusRequester,
                            topBarFocusOrder,
                            perAppFocusRequester::requestFocus
                        ),
                        onClick = if (showSearch) {
                            {
                                searchQuery = ""
                                showSearch = false
                            }
                        } else {
                            onBackClick
                        }
                    )
                },
                actions = {
                    if (!showSearch) {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_search_24dp),
                            label = stringResource(R.string.menu_item_search),
                            focusRequester = searchFocusRequester,
                            modifier = Modifier.dpadTopBarFocusNavigation(
                                searchFocusRequester,
                                topBarFocusOrder,
                                perAppFocusRequester::requestFocus
                            ),
                            onClick = { showSearch = true }
                        )
                    }
                    Box {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_more_vert_24dp),
                            label = stringResource(R.string.action_more),
                            contentDescription = if (isTelevision) {
                                stringResource(R.string.action_more)
                            } else {
                                null
                            },
                            focusRequester = moreFocusRequester,
                            modifier = Modifier.dpadTopBarFocusNavigation(
                                moreFocusRequester,
                                topBarFocusOrder,
                                perAppFocusRequester::requestFocus
                            ),
                            onClick = { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.dpadPopupHorizontalNavigation(onMovePrevious = {
                                showMenu = false
                                if (showSearch) {
                                    backFocusRequester.requestFocus()
                                } else {
                                    searchFocusRequester.requestFocus()
                                }
                            })
                        ) {
                            AppDropdownMenuItems(PerAppMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    PerAppMenuAction.SelectAll -> onSelectAll()
                                    PerAppMenuAction.InvertSelection -> onInvertSelection()
                                    PerAppMenuAction.SelectProxyApps -> onSelectProxyAuto()
                                    PerAppMenuAction.ImportSelection -> onImportProxyApp()
                                    PerAppMenuAction.ExportSelection -> onExportProxyApp()
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isTelevision) 48.dp else 16.dp,
                            vertical = if (isTelevision) 12.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PerAppModeToggle(
                        label = stringResource(R.string.per_app_proxy_settings_enable),
                        checked = perAppProxyEnabled,
                        isTelevision = isTelevision,
                        onCheckedChange = onPerAppProxyChanged,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(perAppFocusRequester)
                            .dpadOrderedFocusNavigation(perAppFocusRequester, modeFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { false },
                                onMoveDown = focusFirstApp
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    PerAppModeToggle(
                        label = stringResource(R.string.switch_bypass_apps_mode),
                        checked = bypassApps,
                        isTelevision = isTelevision,
                        onCheckedChange = onBypassAppsChanged,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(bypassFocusRequester)
                            .dpadOrderedFocusNavigation(bypassFocusRequester, modeFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { false },
                                onMoveDown = focusFirstApp
                            )
                    )
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_about_24dp),
                        label = stringResource(R.string.action_info),
                        onClick = {
                            if (isTelevision) showInfoPopup = true else onInfoClick()
                        },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(R.string.acc_per_app_proxy_information),
                        focusRequester = infoFocusRequester,
                        modifier = Modifier
                            .dpadOrderedFocusNavigation(infoFocusRequester, modeFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { false },
                                onMoveDown = focusFirstApp
                            )
                    )
                }
            }
            AppDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                contentPadding = if (isTelevision) {
                    PaddingValues(horizontal = 48.dp, vertical = 8.dp)
                } else {
                    NavigationBarsBottomPadding()
                }
            ) {
                itemsIndexed(items = apps, key = { _, app -> app.packageName }) { index, app ->
                    val checked = blacklist.contains(app.packageName)
                    val focusRequester = rowFocusRequesters.getValue(app.packageName)
                    AppListItem(
                        appName = app.appName,
                        packageName = app.packageName,
                        icon = null,
                        checked = checked,
                        onCheckedChange = { onToggleApp(app.packageName) },
                        focusRequester = focusRequester,
                        modifier = Modifier.dpadVerticalFocusNavigation(
                            onMoveUp = {
                                apps.getOrNull(index - 1)?.let {
                                    rowFocusRequesters[it.packageName]?.requestFocus()
                                } ?: perAppFocusRequester.requestFocus()
                            },
                            onMoveDown = {
                                apps.getOrNull(index + 1)?.let {
                                    rowFocusRequesters[it.packageName]?.requestFocus()
                                } ?: true
                            }
                        )
                    )
                    ItemDivider()
                }
            }
        }
    }

    if (showInfoPopup) {
        TvPerAppInfoPopup(
            message = stringResource(R.string.summary_pref_per_app_proxy),
            onDismiss = { showInfoPopup = false }
        )
    }
}

@Composable
private fun PerAppModeToggle(
    label: String,
    checked: Boolean,
    isTelevision: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(
            if (isTelevision) {
                Modifier
                    .dpadFocusOutline(cornerRadius = 16.dp)
                    .dpadClickable { onCheckedChange(!checked) }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            } else {
                Modifier
            }
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            modifier = Modifier.scale(if (isTelevision) 0.75f else 0.65f),
            onCheckedChange = if (isTelevision) null else onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun TvPerAppInfoPopup(message: String, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        requestFocusWhenReady(focusRequester)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        },
        confirmButton = {},
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) onDismiss()
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}
