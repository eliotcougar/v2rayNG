package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppSelectionMenuAction
import com.v2ray.ang.ui.compose.AppSelectionTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.dpadIconButtonFocusOutline
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

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
    val listState = rememberLazyListState()
    val backFocusRequester = rememberDpadFocusRequester(requestFocus = !showSearch, requestKey = showSearch)
    val isTelevision = isTelevisionDevice()
    val perAppFocusRequester = remember { FocusRequester() }
    val bypassFocusRequester = remember { FocusRequester() }
    val infoFocusRequester = remember { FocusRequester() }
    val modeFocusOrder = remember { listOf(perAppFocusRequester, bypassFocusRequester, infoFocusRequester) }
    val packageNames = apps.map { it.packageName }
    val rowFocusRequesters = remember(packageNames) { packageNames.associateWith { FocusRequester() } }
    val focusFirstApp = { apps.firstOrNull()?.let { rowFocusRequesters[it.packageName]?.requestFocus() } ?: true }

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppSelectionTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
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
                onSearchOpen = { showSearch = true },
                onBackClick = onBackClick,
                backFocusRequester = backFocusRequester,
                onMoveDown = { perAppFocusRequester.requestFocus() },
                menuActions = listOf(
                    AppSelectionMenuAction(stringResource(R.string.menu_item_select_all), onSelectAll),
                    AppSelectionMenuAction(stringResource(R.string.menu_item_invert_selection), onInvertSelection),
                    AppSelectionMenuAction(stringResource(R.string.menu_item_select_proxy_app), onSelectProxyAuto),
                    AppSelectionMenuAction(stringResource(R.string.menu_item_import_proxy_app), onImportProxyApp),
                    AppSelectionMenuAction(stringResource(R.string.menu_item_export_proxy_app), onExportProxyApp)
                )
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            .dpadFocusOutline(perAppFocusRequester, 16.dp)
                            .dpadOrderedFocusNavigation(
                                perAppFocusRequester,
                                modeFocusOrder,
                                onBeforeFirst = { backFocusRequester.requestFocus() }
                            )
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { backFocusRequester.requestFocus() },
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
                            .dpadFocusOutline(bypassFocusRequester, 16.dp)
                            .dpadOrderedFocusNavigation(bypassFocusRequester, modeFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { backFocusRequester.requestFocus() },
                                onMoveDown = focusFirstApp
                            )
                    )
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .dpadIconButtonFocusOutline(infoFocusRequester)
                            .dpadOrderedFocusNavigation(infoFocusRequester, modeFocusOrder)
                            .dpadVerticalFocusNavigation(
                                onMoveUp = { backFocusRequester.requestFocus() },
                                onMoveDown = focusFirstApp
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_about_24dp),
                            contentDescription = stringResource(R.string.acc_per_app_proxy_information),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AppDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .dpadMovePreviousNavigation { backFocusRequester.requestFocus() }
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
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
                                apps.getOrNull(index - 1)?.let { rowFocusRequesters[it.packageName]?.requestFocus() }
                                    ?: perAppFocusRequester.requestFocus()
                            },
                            onMoveDown = {
                                apps.getOrNull(index + 1)?.let { rowFocusRequesters[it.packageName]?.requestFocus() } ?: true
                            }
                        )
                    )
                    ItemDivider()
                }
            }
        }
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
        modifier = modifier
            .then(if (isTelevision) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            modifier = Modifier.scale(0.65f),
            onCheckedChange = if (isTelevision) null else onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}
