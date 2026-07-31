package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

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
    onMoveDown: () -> Boolean,
    menuActions: List<AppSelectionMenuAction>
) {
    var showMenu by remember { mutableStateOf(false) }
    val backFocusRequester = rememberDpadFocusRequester(requestFocus = !isSearchActive, requestKey = isSearchActive)
    val searchFocusRequester = remember { FocusRequester() }
    val moreFocusRequester = remember { FocusRequester() }
    val topBarFocusOrder = remember(backFocusRequester, searchFocusRequester, moreFocusRequester, isSearchActive) {
        if (isSearchActive) listOf(backFocusRequester, moreFocusRequester)
        else listOf(backFocusRequester, searchFocusRequester, moreFocusRequester)
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
        navigationFocusRequester = backFocusRequester,
        customActionFocusRequesters = if (isSearchActive) listOf(moreFocusRequester)
        else listOf(searchFocusRequester, moreFocusRequester),
        onMoveDown = onMoveDown,
        actions = {
            if (!isSearchActive) {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_search_24dp),
                    label = stringResource(R.string.menu_item_search),
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.dpadTopBarFocusNavigation(searchFocusRequester, topBarFocusOrder, onMoveDown),
                    onClick = onSearchOpen
                )
            }
            Box {
                AppIconButton(
                    icon = painterResource(R.drawable.ic_more_vert_24dp),
                    label = stringResource(R.string.action_more),
                    focusRequester = moreFocusRequester,
                    modifier = Modifier.dpadTopBarFocusNavigation(moreFocusRequester, topBarFocusOrder, onMoveDown),
                    onClick = { showMenu = true }
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.dpadPopupHorizontalNavigation(onMovePrevious = {
                        showMenu = false
                        (if (isSearchActive) backFocusRequester else searchFocusRequester).requestFocus()
                    })
                ) {
                    menuActions.forEach { action ->
                        AppDropdownMenuItem(text = action.label, onClick = { showMenu = false; action.onClick() })
                    }
                }
            }
        }
    )
}
