package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AccessibilityLiveRegionText
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.isTelevisionDevice

@Composable
fun MainBottomBar(
    displayText: String,
    accessibilityText: String,
    testAnnouncements: Flow<MainTestAnnouncement>,
    formatTestAnnouncement: (MainTestAnnouncement) -> String,
    testDisplayText: String,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onAction: (MainAction) -> Unit
) {
    var testAnnouncement by remember { mutableStateOf<MainTestAnnouncement?>(null) }

    LaunchedEffect(testAnnouncements) {
        testAnnouncements.collect { testAnnouncement = it }
    }

    val checkConnectionLabel = stringResource(R.string.connection_test_pending)
    val connectionActionModifier = if (isRunning) {
        Modifier.clickable(
            onClickLabel = checkConnectionLabel,
            onClick = { onAction(MainAction.TestCurrentServer) },
        )
    } else {
        Modifier
    }

    val isTelevision = isTelevisionDevice()
    if (isTelevision) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        .dpadFocusOutline()
                        .semantics(mergeDescendants = true) { contentDescription = accessibilityText }
                        .then(connectionActionModifier),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayText,
                        modifier = Modifier.semantics { hideFromAccessibility() },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (testDisplayText.isNotBlank()) {
                        Text(
                            text = testDisplayText,
                            modifier = Modifier.semantics { hideFromAccessibility() },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        AssertiveTestLiveRegion(testAnnouncement?.id, testAnnouncement?.let(formatTestAnnouncement).orEmpty())
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityText
                }
                .then(connectionActionModifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).semantics { hideFromAccessibility() }) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (testDisplayText.isNotBlank()) {
                        Text(
                            text = testDisplayText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        AssertiveTestLiveRegion(
            eventId = testAnnouncement?.id,
            text = testAnnouncement?.let(formatTestAnnouncement).orEmpty(),
        )
        FloatingActionButton(
            onClick = { onAction(MainAction.ToggleService) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding()
                .dpadFocusOutline(cornerRadius = 16.dp),
            containerColor = if (isRunning) colorFabActive
            else if (isDarkTheme) colorFabInactiveDark
            else colorFabInactiveLight
        ) {
            Icon(
                painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                else painterResource(R.drawable.ic_play_24dp),
                contentDescription = if (isRunning) {
                    stringResource(R.string.action_stop_service)
                } else {
                    stringResource(R.string.tasker_start_service)
                },
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AssertiveTestLiveRegion(
    eventId: Long?,
    text: String,
) {
    var liveRegionEventId by remember { mutableStateOf<Long?>(null) }
    var liveRegionText by remember { mutableStateOf("") }

    LaunchedEffect(eventId, text) {
        if (eventId == null || text.isBlank()) return@LaunchedEffect
        liveRegionEventId = eventId
        liveRegionText = text
        delay(TestLiveRegionLifetimeMs)
        if (liveRegionEventId == eventId) liveRegionText = ""
    }

    AccessibilityLiveRegionText(
        eventId = liveRegionEventId,
        text = liveRegionText,
        mode = LiveRegionMode.Assertive,
    )
}

private const val TestLiveRegionLifetimeMs = 1000L
