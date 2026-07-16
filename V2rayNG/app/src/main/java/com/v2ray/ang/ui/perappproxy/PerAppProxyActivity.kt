package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
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
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppIconButton
import com.v2ray.ang.compose.AppListItem
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ItemDivider
import com.v2ray.ang.compose.colorFabActive
import com.v2ray.ang.compose.dpadFocusOutline
import com.v2ray.ang.compose.dpadHorizontalFocusNavigation
import com.v2ray.ang.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.compose.isTelevisionDevice
import com.v2ray.ang.compose.tvMenuItemFocus
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
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
    val enableInteractionSource = remember { MutableInteractionSource() }
    val bypassInteractionSource = remember { MutableInteractionSource() }
    val searchFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
                onBackClick = onBackClick,
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
                actions = {
                    if (!showSearch) {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_search_24dp),
                            label = stringResource(R.string.menu_item_search),
                            focusRequester = searchFocusRequester,
                            modifier = Modifier.dpadHorizontalFocusNavigation(
                                onMoveLeft = { searchFocusRequester.requestFocus() },
                                onMoveRight = { moreFocusRequester.requestFocus() }
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
                            modifier = Modifier.dpadHorizontalFocusNavigation(
                                onMoveLeft = { searchFocusRequester.requestFocus() },
                                onMoveRight = { moreFocusRequester.requestFocus() }
                            ),
                            onClick = { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.dpadPopupHorizontalNavigation(onMovePrevious = {
                                showMenu = false
                                searchFocusRequester.requestFocus()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isTelevision) {
                                    Modifier
                                        .dpadFocusOutline(cornerRadius = 16.dp)
                                        .clickable(
                                            interactionSource = enableInteractionSource,
                                            indication = null
                                        ) { onPerAppProxyChanged(!perAppProxyEnabled) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.per_app_proxy_settings_enable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = perAppProxyEnabled,
                            modifier = Modifier.scale(if (isTelevision) 0.75f else 0.65f),
                            onCheckedChange = if (isTelevision) null else onPerAppProxyChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isTelevision) {
                                    Modifier
                                        .dpadFocusOutline(cornerRadius = 16.dp)
                                        .clickable(
                                            interactionSource = bypassInteractionSource,
                                            indication = null
                                        ) { onBypassAppsChanged(!bypassApps) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.switch_bypass_apps_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = bypassApps,
                            modifier = Modifier.scale(if (isTelevision) 0.75f else 0.65f),
                            onCheckedChange = if (isTelevision) null else onBypassAppsChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                    AppIconButton(
                        icon = painterResource(R.drawable.ic_about_24dp),
                        label = stringResource(R.string.action_info),
                        onClick = {
                            if (isTelevision) showInfoPopup = true else onInfoClick()
                        },
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(R.string.acc_per_app_proxy_information)
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
                items(items = apps, key = { it.packageName }) { app ->
                    val checked = blacklist.contains(app.packageName)
                    AppListItem(
                        appName = app.appName,
                        packageName = app.packageName,
                        icon = null,
                        checked = checked,
                        onCheckedChange = { onToggleApp(app.packageName) }
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
private fun TvPerAppInfoPopup(
    message: String,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
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
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    )
}
