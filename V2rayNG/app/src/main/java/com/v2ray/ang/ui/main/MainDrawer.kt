package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState as TvDrawerState
import androidx.tv.material3.DrawerValue as TvDrawerValue
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.ListItemScale as TvListItemScale
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.ModalNavigationDrawer as TvModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.lightColorScheme as tvColorScheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.dpadLogicalHorizontalNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.requestFocusWhenReady
import com.v2ray.ang.ui.compose.verticalScrollbar

private val V2rayNgFontFamily = FontFamily(Font(R.font.montserrat_thin))
private val TvDrawerCollapsedWidth = 72.dp
private val TvDrawerExpandedWidth = 272.dp
private const val TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS = 100

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

@Composable
internal fun AppBrandTitle(
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
        style = style.copy(
            fontFamily = V2rayNgFontFamily,
            fontWeight = FontWeight.Thin
        ),
        textAlign = textAlign
    )
}

private val primaryDrawerItems = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings
)

private val secondaryDrawerItems = listOf(
    MainDestination.Promotion,
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About
)

private val drawerItems = primaryDrawerItems + secondaryDrawerItems

@Composable
fun MainDrawerContent(
    drawerState: DrawerState,
    onClose: () -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val drawerScrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val isDarkTheme = LocalDarkTheme.current
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        colorFilter = if (isDarkTheme) {
                            ColorFilter.tint(Color.White, BlendMode.SrcIn)
                        } else {
                            null
                        }
                    )
                    AppBrandTitle(style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                }
            }

            drawerItems.forEachIndexed { index, item ->
                if (index == primaryDrawerItems.size) {
                    AppDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                DrawerMenuItem(
                    icon = painterResource(item.iconRes),
                    label = stringResource(item.labelRes),
                    onClick = {
                        onClose()
                        onNavigate(item)
                    }
                )
            }
        }
    }
}

@Composable
fun TvMainNavigationDrawer(
    drawerState: TvDrawerState,
    focusGeneration: Int,
    onClose: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    content: @Composable () -> Unit
) {
    val mobileColors = MaterialTheme.colorScheme
    val tvColors = remember(mobileColors) { mobileColors.toTvColorScheme() }
    TvMaterialTheme(colorScheme = tvColors) {
        TvModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerValue ->
                TvMainDrawerContent(drawerValue, focusGeneration, onClose, onNavigate)
            },
            content = {
                Box(modifier = Modifier.fillMaxSize().padding(start = TvDrawerCollapsedWidth)) { content() }
            }
        )
    }
}

@Composable
private fun NavigationDrawerScope.TvMainDrawerContent(
    drawerValue: TvDrawerValue,
    focusGeneration: Int,
    onClose: () -> Unit,
    onNavigate: (MainDestination) -> Unit
) {
    val focusRequesters = remember { List(drawerItems.size) { FocusRequester() } }
    val drawerScrollState = rememberScrollState()
    var focusedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(drawerValue, focusGeneration) {
        if (drawerValue == TvDrawerValue.Open) {
            requestFocusWhenReady(focusRequesters[focusedIndex], focusRequesters.first())
        }
    }

    val drawerWidth by animateDpAsState(
        targetValue = if (drawerValue == TvDrawerValue.Open) TvDrawerExpandedWidth else TvDrawerCollapsedWidth,
        animationSpec = tween(TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS),
        label = "TV drawer width"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (drawerValue == TvDrawerValue.Open) 1f else 0f,
        animationSpec = tween(TV_DRAWER_WIDTH_ANIMATION_DURATION_MILLIS),
        label = "TV drawer labels"
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(TvDrawerExpandedWidth)
            .drawWithContent {
                val visibleWidth = drawerWidth.toPx()
                val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - visibleWidth
                val right = if (layoutDirection == LayoutDirection.Ltr) visibleWidth else size.width
                // Reveal the fixed drawer from its start edge so its icons never move with the animation.
                clipRect(left = left, right = right) { this@drawWithContent.drawContent() }
            }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            if (drawerValue == TvDrawerValue.Open) {
                Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        val isDarkTheme = LocalDarkTheme.current
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            colorFilter = if (isDarkTheme) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null
                        )
                        AppBrandTitle(
                            style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.width(64.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            drawerItems.forEachIndexed { index, item ->
                if (index == primaryDrawerItems.size) {
                    AppDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
                }
                TvListItem(
                    selected = false,
                    onClick = { onNavigate(item) },
                    headlineContent = {
                        TvText(stringResource(item.labelRes), modifier = Modifier.alpha(labelAlpha), maxLines = 1)
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = if (drawerValue == TvDrawerValue.Open && focusedIndex == index) {
                                MaterialTheme.colorScheme.inverseOnSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    scale = TvListItemScale.None,
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .onFocusChanged { if (it.isFocused) focusedIndex = index }
                        .dpadLogicalHorizontalNavigation(onMovePrevious = {}, onMoveNext = onClose)
                        .dpadVerticalFocusNavigation(
                            onMoveUp = {
                                focusRequesters[(index - 1).coerceAtLeast(0)].requestFocus()
                                true
                            },
                            onMoveDown = {
                                focusRequesters[(index + 1).coerceAtMost(focusRequesters.lastIndex)].requestFocus()
                                true
                            }
                        )
                )
            }
        }
    }
}

private fun ColorScheme.toTvColorScheme() = tvColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = surfaceTint,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    border = outline,
    borderVariant = outlineVariant,
    scrim = scrim
)

@Composable
private fun DrawerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
