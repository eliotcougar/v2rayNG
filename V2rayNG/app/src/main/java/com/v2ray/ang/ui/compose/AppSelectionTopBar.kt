package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlinx.coroutines.launch

internal data class AppSelectionMenuAction(val label: String, val onClick: () -> Unit)

@Composable
internal fun AppSelectionTopBar(
    title: String,
    isLoading: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    onBackClick: () -> Unit,
    backFocusRequester: FocusRequester,
    onMoveDown: () -> Boolean,
    menuActions: List<AppSelectionMenuAction>
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }
    val searchClearFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }
    val hasSearchQuery = searchQuery.isNotEmpty()
    val focusOrder = remember(isSearchActive, hasSearchQuery) {
        if (isSearchActive) buildList {
            add(backFocusRequester)
            add(searchInputFocusRequester)
            if (hasSearchQuery) add(searchClearFocusRequester)
            add(moreFocusRequester)
        } else listOf(backFocusRequester, searchFocusRequester, moreFocusRequester)
    }

    AppTopBar(
        title = title,
        onBackClick = onBackClick,
        isLoading = isLoading,
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        searchPlaceholder = stringResource(R.string.menu_item_search),
        searchInputFocusRequester = searchInputFocusRequester,
        searchClearFocusRequester = searchClearFocusRequester,
        navigationFocusRequester = backFocusRequester,
        customActionFocusRequesters = if (isSearchActive) listOf(moreFocusRequester)
        else listOf(searchFocusRequester, moreFocusRequester),
        onMoveDown = onMoveDown,
        actions = {
            if (!isSearchActive) {
                IconButton(
                    onClick = onSearchOpen,
                    modifier = Modifier
                        .dpadFocusOutline(searchFocusRequester, 20.dp)
                        .dpadTopBarFocusNavigation(searchFocusRequester, focusOrder, onMoveDown)
                ) {
                    Icon(painterResource(R.drawable.ic_search_24dp), stringResource(R.string.acc_search))
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .dpadFocusOutline(moreFocusRequester, 20.dp)
                        .dpadTopBarFocusNavigation(moreFocusRequester, focusOrder, onMoveDown)
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.acc_more))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                ) {
                    menuActions.forEach { action ->
                        Box(modifier = Modifier.dpadPopupHorizontalNavigation(onMovePrevious = {
                            showMenu = false
                            val target = when {
                                !isSearchActive -> searchFocusRequester
                                hasSearchQuery -> searchClearFocusRequester
                                else -> searchInputFocusRequester
                            }
                            scope.launch { afterDpadPopupDismiss { target.requestFocus() } }
                        })) {
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = { showMenu = false; action.onClick() }
                            )
                        }
                    }
                }
            }
        }
    )
}
