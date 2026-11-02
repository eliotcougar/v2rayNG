package com.v2ray.ang.ui.compose

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Process ownership lets unread messages follow navigation instead of dying with an activity. */
@MainThread
class AppSnackbarManager(
    private val scope: CoroutineScope,
    private val notifyBackground: (UserMessage) -> Unit,
    private val clearBackground: () -> Unit,
    private val closeLabel: () -> String,
    elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    val hostState = SnackbarHostState()
    internal val liveRegionMessages = LiveRegionMessageState(elapsedRealtime)
    private var liveRegionExpiry: Job? = null
    private var publishedLiveRegionId: Long? = null
    private val hosts = mutableListOf<String>()
    private val _activeHost = MutableStateFlow<String?>(null)
    val activeHost = _activeHost.asStateFlow()
    private val pending = linkedMapOf<Job, UserMessage>()
    private var presentation: Pair<String, SnackbarData>? = null
    var isForeground = false
        private set

    internal fun register(host: String) {
        hosts.add(host)
        activateHost(host)
    }

    internal fun unregister(host: String) {
        hosts.remove(host)
        activateHost(hosts.lastOrNull())
    }

    private fun activateHost(host: String?) {
        presentation?.let { (previousHost, snackbar) ->
            // Do not replay already-shown transient feedback on a different screen.
            // Unseen queued messages and errors requiring dismissal still follow navigation.
            if (host != null && previousHost != host && snackbar.visuals.duration != SnackbarDuration.Indefinite) {
                snackbar.dismiss()
                presentation = null
            }
        }
        _activeHost.value = host
    }

    internal fun onPresented(host: String, snackbar: SnackbarData) {
        if (_activeHost.value == host && hostState.currentSnackbarData === snackbar) {
            presentation = host to snackbar
        }
    }

    internal fun setForeground(foreground: Boolean) {
        isForeground = foreground
        if (!foreground) {
            liveRegionExpiry?.cancel()
            publishedLiveRegionId = null
            liveRegionMessages.clear()
            // Cancel the Material queue before posting: nothing may expire in a hidden host.
            pending.toList().forEach { (job, message) ->
                job.cancel()
                notifyBackground(message)
            }
        }
    }

    internal fun onLiveRegionPublished(id: Long) {
        if (liveRegionMessages.current?.id != id || publishedLiveRegionId == id) return
        publishedLiveRegionId = id
        liveRegionExpiry = scope.launch {
            delay(LiveRegionMessageLifetimeMs)
            liveRegionMessages.advance(id)
        }
    }

    fun show(message: UserMessage) {
        if (message.text.isBlank()) return
        if (!isForeground) {
            notifyBackground(message)
            return
        }
        liveRegionMessages.offer(message)
        // Material still owns serialization, dismissal and the accessibility-adjusted timeout.
        // Track its jobs only so unread messages can be handed to background notifications.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            hostState.showSnackbar(
                AppSnackbarVisuals(message, if (message.requiresDismissal) closeLabel() else null),
            )
        }
        pending[job] = message
        job.invokeOnCompletion { pending.remove(job) }
        job.start()
        clearBackground()
    }

    companion object {
        fun create(context: Context): AppSnackbarManager {
            val appContext = context.applicationContext
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val manager = AppSnackbarManager(
                scope = ProcessLifecycleOwner.get().lifecycleScope,
                notifyBackground = { NotificationHelper.notifyTransientMessage(appContext, it) },
                clearBackground = { NotificationHelper.cancelTransientMessage(appContext) },
                closeLabel = { AppLocaleManager.localizedContext(appContext).getString(R.string.action_close) },
            )
            // ProcessLifecycleOwner suppresses stop/start churn during activity replacement.
            // The observer and receiver have the same lifetime as this process-owned manager.
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = manager.setForeground(true)
                override fun onStop(owner: LifecycleOwner) = manager.setForeground(false)
            })
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!isOrderedBroadcast || resultCode == Activity.RESULT_OK) return
                    if (!manager.isForeground) return
                    val what = intent.getIntExtra("key", 0)
                    val content = intent.getStringExtra("content")
                    val message = MessageHelper.serviceMessage(context, what, content) ?: return
                    manager.show(message)
                    // The process now owns delivery even if the current screen closes.
                    resultCode = Activity.RESULT_OK
                }
            }
            ContextCompat.registerReceiver(
                appContext, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return manager
        }
    }
}

internal data class AppSnackbarVisuals(
    val feedback: UserMessage,
    override val actionLabel: String?,
) : SnackbarVisuals {
    override val message = feedback.text
    override val duration = when {
        feedback.requiresDismissal -> SnackbarDuration.Indefinite
        feedback.long -> SnackbarDuration.Long
        else -> SnackbarDuration.Short
    }
    override val withDismissAction = false
}

@Composable
fun AppSnackbarHost() {
    val manager = (LocalContext.current.applicationContext as AngApplication).snackbarManager
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // A recreated screen is the same destination, not navigation to a new message host.
    val host = rememberSaveable { UUID.randomUUID().toString() }
    LaunchedEffect(manager, lifecycleOwner, host) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            manager.register(host)
            try {
                awaitCancellation()
            } finally {
                manager.unregister(host)
            }
        }
    }
    val activeHost by manager.activeHost.collectAsStateWithLifecycle()
    if (activeHost != host) return
    val liveRegionMessage = manager.liveRegionMessages.current
    AccessibilityLiveRegionText(
        eventId = liveRegionMessage?.id,
        text = liveRegionMessage?.text.orEmpty(),
        mode = if (liveRegionMessage?.mode == AccessibilityLiveRegionMode.ASSERTIVE) {
            LiveRegionMode.Assertive
        } else LiveRegionMode.Polite,
        onPublished = manager::onLiveRegionPublished,
    )
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            // Keep upstream's offset above the service controls, but leave room above the IME.
            .padding(bottom = if (imeVisible) 8.dp else 100.dp),
    ) {
        val maxTextHeight = maxHeight / 2
        val current = manager.hostState.currentSnackbarData ?: return@BoxWithConstraints
        val feedback = (current.visuals as AppSnackbarVisuals).feedback
        val background = when (feedback.type) {
            UserMessage.Type.NORMAL -> if (LocalDarkTheme.current) toastNormalBgDark else toastNormalBgLight
            UserMessage.Type.SUCCESS -> toastSuccessBg
            UserMessage.Type.ERROR -> toastErrorBg
            UserMessage.Type.INFO -> toastInfoBg
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = maxWidth * 0.75f),
            shape = RoundedCornerShape(24.dp),
            color = background,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Material's animation wrapper adds its own live region and pane title. Clear that
                // entire subtree; keep the real Close button outside it. Long errors remain readable
                // as one text node, without a second live region or a focus request.
                SnackbarHost(
                    hostState = manager.hostState,
                    modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {
                        if (feedback.requiresDismissal) text = AnnotatedString(feedback.text)
                    },
                ) { data ->
                    SideEffect {
                        // A save-and-finish can still compose one last frame; leave its feedback unread.
                        if (activity?.isFinishing != true) manager.onPresented(host, data)
                    }
                    Text(
                        text = data.visuals.message,
                        color = toastTextColor,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(max = maxTextHeight)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                current.visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = { current.performAction() },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = toastTextColor),
                    ) { Text(label) }
                }
            }
        }
    }
}

internal data class LiveRegionMessage(
    val id: Long,
    val text: String,
    val mode: AccessibilityLiveRegionMode,
)

internal class LiveRegionMessageState(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    var current by mutableStateOf<LiveRegionMessage?>(null)
        private set

    private val pending = ArrayDeque<LiveRegionMessage>()
    private var nextId = 0L
    private var lastMessage: String? = null
    private var lastMode: AccessibilityLiveRegionMode? = null
    private var lastUpdateAt = 0L

    fun offer(event: UserMessage) {
        val mode = event.liveRegionMode
        val text = event.accessibilityText ?: event.text
        if (text.isBlank()) return
        val now = elapsedRealtime()
        if (text == lastMessage && mode == lastMode && now - lastUpdateAt < DuplicateLiveRegionMessageWindowMs) return

        lastMessage = text
        lastMode = mode
        lastUpdateAt = now
        val message = LiveRegionMessage(++nextId, text, mode)
        if (current == null) current = message else pending.addLast(message)
    }

    // Mode controls urgency, not publication order; every event keeps its own semantics lifetime.
    fun advance(id: Long) {
        if (current?.id != id) return
        current = pending.removeFirstOrNull()
    }

    fun clear() {
        current = null
        pending.clear()
        lastMessage = null
        lastMode = null
        lastUpdateAt = 0L
    }
}

internal const val DuplicateLiveRegionMessageWindowMs = 1000L
private const val LiveRegionMessageLifetimeMs = 1000L
