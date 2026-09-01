package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.util.AppIconFetcher
import sh.calvin.reorderable.ReorderableCollectionItemScope

data class AppTopBarAction(
    val icon: Painter,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    initialFocus: Boolean = true,
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String? = null,
    searchInputFocusRequester: FocusRequester? = null,
    searchClearFocusRequester: FocusRequester? = null,
    navigationFocusRequester: FocusRequester? = null,
    navigationIcon: @Composable ((FocusRequester) -> Unit)? = null,
    actionItems: List<AppTopBarAction> = emptyList(),
    customActionFocusRequesters: List<FocusRequester> = emptyList(),
    onMoveDown: (() -> Boolean)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val defaultNavigationFocusRequester = rememberDpadFocusRequester(
        requestFocus = navigationFocusRequester == null && initialFocus && !isSearchActive,
        requestKey = isSearchActive
    )
    val resolvedNavigationFocusRequester = navigationFocusRequester ?: defaultNavigationFocusRequester
    val actionFocusRequesters = remember(actionItems.size) { List(actionItems.size) { FocusRequester() } }
    val defaultSearchInputFocusRequester = remember { FocusRequester() }
    val defaultSearchClearFocusRequester = remember { FocusRequester() }
    val resolvedSearchInputFocusRequester = searchInputFocusRequester ?: defaultSearchInputFocusRequester
    val resolvedSearchClearFocusRequester = searchClearFocusRequester ?: defaultSearchClearFocusRequester
    val resolvedActionFocusRequesters = actionFocusRequesters + customActionFocusRequesters
    val hasSearchQuery = searchQuery.isNotEmpty()
    val topBarFocusOrder = remember(
        resolvedNavigationFocusRequester,
        resolvedSearchInputFocusRequester,
        resolvedSearchClearFocusRequester,
        resolvedActionFocusRequesters,
        isSearchActive,
        hasSearchQuery
    ) {
        buildList {
            add(resolvedNavigationFocusRequester)
            if (isSearchActive) {
                add(resolvedSearchInputFocusRequester)
                if (hasSearchQuery) add(resolvedSearchClearFocusRequester)
            }
            addAll(resolvedActionFocusRequesters)
        }
    }
    val navigationModifier = Modifier.dpadTopBarFocusNavigation(
        resolvedNavigationFocusRequester,
        topBarFocusOrder,
        onMoveDown = { onMoveDown?.invoke() ?: false }
    )
    Column {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = searchPlaceholder,
                        focusRequester = resolvedSearchInputFocusRequester,
                        clearFocusRequester = resolvedSearchClearFocusRequester,
                        focusOrder = topBarFocusOrder,
                        onMoveDown = { onMoveDown?.invoke() ?: false }
                    )
                } else {
                    Text(text = title)
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    navigationIcon(resolvedNavigationFocusRequester)
                } else {
                    IconButton(
                        onClick = if (isSearchActive) onSearchClose else onBackClick,
                        modifier = navigationModifier.dpadFocusOutline(resolvedNavigationFocusRequester, 20.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.acc_back)
                        )
                    }
                }
            },
            actions = {
                actionItems.forEachIndexed { index, action ->
                    val focusRequester = actionFocusRequesters[index]
                    IconButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier
                            .dpadFocusOutline(focusRequester, 20.dp)
                            .dpadTopBarFocusNavigation(
                                focusRequester,
                                topBarFocusOrder,
                                onMoveDown = { onMoveDown?.invoke() ?: false }
                            )
                    ) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String?,
    focusRequester: FocusRequester,
    clearFocusRequester: FocusRequester,
    focusOrder: List<FocusRequester>,
    onMoveDown: () -> Boolean
) {
    val tvFieldState = if (isTelevisionDevice()) rememberTvTextFieldState(focusRequester) else null
    fun moveFocus(direction: DpadHorizontalDirection): Boolean =
        (adjacentDpadFocusTarget(focusRequester, focusOrder, direction) ?: focusRequester).requestFocus()
    LaunchedEffect(tvFieldState, focusRequester) {
        if (tvFieldState == null) requestFocusWhenReady(focusRequester) else tvFieldState.beginEditing()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .tvAwareTextFieldFocus(
                    state = tvFieldState,
                    enabled = true,
                    navigation = TvTextFieldNavigation(
                        focusRequester = focusRequester,
                        onMoveUp = { true },
                        onMoveDown = onMoveDown,
                        onMovePrevious = { moveFocus(DpadHorizontalDirection.Previous) },
                        onMoveNext = { moveFocus(DpadHorizontalDirection.Next) }
                    ),
                    onActivate = { tvFieldState?.beginEditing() }
                )
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                readOnly = tvFieldState != null && !tvFieldState.isEditing,
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                placeholder = { if (placeholder != null) Text(placeholder, style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)) },
                interactionSource = tvFieldState?.interactionSource,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.secondary,
                    selectionColors = TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.secondary,
                        backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                    )
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(tvFieldState?.let { Modifier.tvTextFieldEditorFocus(it) }
                        ?: Modifier.focusRequester(focusRequester))
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange(""); focusRequester.requestFocus() },
                modifier = Modifier
                    .dpadFocusOutline(clearFocusRequester, 20.dp)
                    .dpadTopBarFocusNavigation(clearFocusRequester, focusOrder, onMoveDown)
            ) {
                Icon(
                    painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                    stringResource(R.string.logcat_clear)
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    appName: String,
    packageName: String,
    icon: Any?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusOutline(focusRequester)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val model = remember(icon, packageName) {
            if (icon != null) {
                icon
            } else {
                val data = "appicon:$packageName"
                ImageRequest.Builder(context)
                    .data(data)
                    .fetcherFactory(AppIconFetcher.Factory(context))
                    .build()
            }
        }

        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
fun ItemDivider() {
    AppDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val color = if (LocalDarkTheme.current) dividerColorDark else dividerColorLight
    HorizontalDivider(modifier = modifier.fillMaxWidth(), thickness = 1.dp, color = color)
}

@Composable
fun NavigationBarsSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}

@Composable
fun NavigationBarsBottomPadding(): PaddingValues {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = bottom)
}

@Composable
fun VersionInfoBlock(
    versionText: String,
    appIdText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = versionText, style = MaterialTheme.typography.bodySmall)
        if (appIdText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = appIdText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun reorderableElevation(isDragging: Boolean) = animateDpAsState(
    targetValue = if (isDragging) 4.dp else 0.dp,
    label = "ReorderableElevation"
)

@Composable
fun ReorderableCollectionItemScope.reorderableDragHandle(): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    return Modifier.longPressDraggableHandle(
        onDragStarted = {
            // Platform haptics honor the user's touch-feedback setting.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    )
}

@Composable
fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(with(scope) { reorderableDragHandle() }),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(with(scope) { reorderableDragHandle() }),
        shadowElevation = elevation
    ) {
        content()
    }
}
