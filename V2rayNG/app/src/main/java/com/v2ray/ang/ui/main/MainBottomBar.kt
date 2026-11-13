package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AccessibilityLiveRegionText
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.accessibilityClickLabel
import kotlinx.coroutines.launch

private val KNOB_SIZE = 56.dp
private val BAR_MAX_WIDTH = 480.dp
private const val TEXT_MAX_SP = 14f
private const val TEXT_LINE_HEIGHT_FACTOR = 1.3f

/**
 * Connection status bar: a rounded track with the status message and the round connect button.
 *
 * The button always sits on the physical right edge, in left-to-right and right-to-left languages
 * alike, and never moves, so the bar can be used with one hand. The message is start-aligned (left
 * in LTR, right in RTL, next to the button). Tapping the track tests the current server. On wide
 * screens the bar is capped at [BAR_MAX_WIDTH] and centered.
 */
@Composable
fun MainBottomBar(
    displayText: String,
    accessibilityText: String,
    testStatus: MainStatus?,
    testDisplayText: String,
    testAnnouncements: Flow<MainTestAnnouncement?>,
    formatTestAnnouncement: (MainStatus) -> String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val announcements = remember { MainTestAnnouncements() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var publishedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(testAnnouncements, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                testAnnouncements.collect(announcements::offer)
            } finally {
                announcements.clear()
            }
        }
    }

    val testAnnouncement = announcements.current
    LaunchedEffect(testAnnouncement?.id, publishedId) {
        val id = testAnnouncement?.id ?: return@LaunchedEffect
        if (publishedId != id) return@LaunchedEffect
        // Preserve publication order, including fast Testing -> result updates. This is a
        // semantics lifetime, not a timer claiming to know when TalkBack finishes speaking.
        delay(TestLiveRegionLifetimeMs)
        announcements.advance(id)
    }
    val completedResult = (testStatus as? MainStatus.ConnectionTest)
        ?.takeIf { isRunning && it.result.isFinal && testAnnouncement == null }
    val statusText = localizedTestText(
        completedResult?.let { "$accessibilityText. ${formatTestAnnouncement(it)}" } ?: accessibilityText
    )

    val checkConnectionLabel = stringResource(R.string.connection_test_pending)
    val connectionActionModifier = if (isRunning) {
        Modifier.clickable(
            onClickLabel = checkConnectionLabel,
            onClick = { onAction(MainAction.TestCurrentServer) },
        )
    } else {
        Modifier
    }

    AccessibilityLiveRegionText(
        eventId = testAnnouncement?.id,
        text = testAnnouncement?.status?.let(formatTestAnnouncement).orEmpty(),
        mode = LiveRegionMode.Assertive,
        onPublished = { publishedId = it },
    )

    if (isTelevisionDevice()) {
        Column(modifier = modifier.fillMaxWidth()) {
            AppDivider()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(64.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .semantics(mergeDescendants = true) { text = statusText },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayText,
                        modifier = Modifier.clearAndSetSemantics { hideFromAccessibility() },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (testDisplayText.isNotBlank()) {
                        Text(
                            text = testDisplayText,
                            modifier = Modifier.clearAndSetSemantics { hideFromAccessibility() },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }
    val serviceActionLabel = stringResource(if (isRunning) R.string.acc_disconnect else R.string.acc_connect)
    val textLayoutDirection = LocalLayoutDirection.current

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    // The track is tall enough for two lines of text at the largest size (at the current font scale), so
    // its height is the same whether the message takes one line or two, in every language.
    val twoLinesHeight = with(LocalDensity.current) {
        (TEXT_MAX_SP * TEXT_LINE_HEIGHT_FACTOR * 2).sp.toDp()
    }
    val trackHeight = maxOf(KNOB_SIZE + 4.dp, twoLinesHeight + 20.dp)
    val knobInset = (trackHeight - KNOB_SIZE) / 2

    Box(
        modifier = modifier
            // On wide screens (tablets, landscape) the bar keeps a phone-like width and stays centered
            // instead of stretching across the whole screen.
            .widthIn(max = BAR_MAX_WIDTH)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        // Forced left-to-right so that the button stays on the physical right edge in every language;
        // the message below gets the real layout direction back.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                // The clipped layer is separate from the button so its shadow is never cut off.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics { text = statusText }
                        .then(connectionActionModifier)
                )
                CompositionLocalProvider(LocalLayoutDirection provides textLayoutDirection) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            // Room for the button on the right; absolute so it does not flip with the language.
                            .absolutePadding(left = 20.dp, right = KNOB_SIZE + knobInset + 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val statusStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = TEXT_MAX_SP.sp,
                            lineHeight = (TEXT_MAX_SP * TEXT_LINE_HEIGHT_FACTOR).sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = statusStyle,
                            maxLines = if (testDisplayText.isBlank()) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics { hideFromAccessibility() }
                        )
                        if (testDisplayText.isNotBlank()) {
                            Text(
                                text = testDisplayText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = statusStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clearAndSetSemantics { hideFromAccessibility() }
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (!isRunning) {
                            scope.launch {
                                rotationAnim.animateTo(
                                    targetValue = 720f,
                                    animationSpec = tween(durationMillis = 3000)
                                )
                            }
                        }
                        onAction(MainAction.ToggleService)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = knobInset)
                        .size(KNOB_SIZE)
                        .accessibilityClickLabel(serviceActionLabel),
                    shape = CircleShape,
                    containerColor = if (isRunning) colorFabActive
                    else if (isDarkTheme) colorFabInactiveDark
                    else colorFabInactiveLight
                ) {
                    Icon(
                        painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                        else painterResource(R.drawable.ic_play_24dp),
                        contentDescription = stringResource(
                            if (isRunning) R.string.acc_stop else R.string.acc_start
                        ),
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = rotationAnim.value }
                    )
                }
            }
        }
    }
}

@Composable
private fun localizedTestText(text: String): AnnotatedString {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    return remember(text, languageTag) {
        buildAnnotatedString {
            withStyle(SpanStyle(localeList = LocaleList(Locale(languageTag)))) {
                append(text)
            }
        }
    }
}

private const val TestLiveRegionLifetimeMs = 1000L
