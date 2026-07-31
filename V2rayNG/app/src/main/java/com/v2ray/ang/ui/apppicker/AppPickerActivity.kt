package com.v2ray.ang.ui.apppicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDropdownMenuItem
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.dpadPopupHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadTopBarFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppListItem
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar

private enum class AppPickerMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection)
}

class AppPickerActivity : BaseComponentActivity() {

    companion object {
        private const val EXTRA_SELECTED_PACKAGES = "selected_packages"
        private const val EXTRA_PICKER_TITLE = "picker_title"

        fun createIntent(
            context: Context,
            selectedPackages: Collection<String> = emptyList(),
            title: String? = null
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(selectedPackages))
            title?.let { putExtra(EXTRA_PICKER_TITLE, it) }
        }

        fun getSelectedPackages(intent: Intent?): List<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        }
    }

    private val viewModel: AppPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES).orEmpty()
        viewModel.initialize(initial)
        viewModel.loadApps(this)
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, ArrayList(viewModel.getSelectedPackages()))
            }
        )
        super.finish()
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

        AppPickerScreen(
            title = resolveScreenTitle(),
            apps = apps,
            isLoading = isLoading,
            selectedPackages = selectedPackages,
            onBackClick = { finish() },
            onToggleApp = { viewModel.toggleApp(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() }
        )
    }

    private fun resolveScreenTitle(): String {
        return intent.getStringExtra(EXTRA_PICKER_TITLE) ?: getString(R.string.per_app_proxy_settings)
    }
}

@Composable
fun AppPickerScreen(
    title: String,
    apps: List<AppInfo>,
    isLoading: Boolean,
    selectedPackages: Set<String>,
    onBackClick: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val backFocusRequester = rememberDpadFocusRequester(
        requestFocus = !showSearch,
        requestKey = showSearch
    )
    val searchFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }
    val packageNames = apps.map { it.packageName }
    val rowFocusRequesters = remember(packageNames) {
        packageNames.associateWith { FocusRequester() }
    }
    val focusFirstApp = {
        apps.firstOrNull()?.let { rowFocusRequesters[it.packageName]?.requestFocus() } ?: false
    }
    val topBarFocusOrder = remember(
        backFocusRequester,
        searchFocusRequester,
        moreFocusRequester,
        showSearch
    ) {
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
                title = title,
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
                navigationFocusRequester = backFocusRequester,
                customActionFocusRequesters = if (showSearch) {
                    listOf(moreFocusRequester)
                } else {
                    listOf(searchFocusRequester, moreFocusRequester)
                },
                onMoveDown = focusFirstApp,
                actions = {
                    if (!showSearch) {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_search_24dp),
                            label = stringResource(R.string.menu_item_search),
                            focusRequester = searchFocusRequester,
                            modifier = Modifier.dpadTopBarFocusNavigation(
                                searchFocusRequester,
                                topBarFocusOrder,
                                focusFirstApp
                            ),
                            onClick = { showSearch = true }
                        )
                    }
                    Box {
                        AppIconButton(
                            icon = painterResource(R.drawable.ic_more_vert_24dp),
                            label = stringResource(R.string.action_more),
                            focusRequester = moreFocusRequester,
                            modifier = Modifier.dpadTopBarFocusNavigation(
                                moreFocusRequester,
                                topBarFocusOrder,
                                focusFirstApp
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
                            AppDropdownMenuItems(AppPickerMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    AppPickerMenuAction.SelectAll -> onSelectAll()
                                    AppPickerMenuAction.InvertSelection -> onInvertSelection()
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            itemsIndexed(items = apps, key = { _, app -> app.packageName }) { index, app ->
                val checked = selectedPackages.contains(app.packageName)
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
                            } ?: false
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
