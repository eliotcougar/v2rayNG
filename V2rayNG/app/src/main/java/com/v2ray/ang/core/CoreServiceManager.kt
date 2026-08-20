package com.v2ray.ang.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.IDialerService
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.ConfigResult
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BrowserDialerMode
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.service.NetworkIdentityResolver
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.Volatile
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object CoreServiceManager {

    private const val POLICY_ROUTE_POLL_INTERVAL_MS = 500L
    private const val ACTIVE_OUTBOUND_POLL_INTERVAL_MS = 1000L

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private val mSystemEventReceive = SystemEventHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null

    @Volatile private var runningProfileGuid = ""
    private val networkResetMutex = Mutex()
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var policyRoutePollJob: Job? = null
    private var activeOutboundPollJob: Job? = null
    private val networkTransitions = AtomicInteger(0)
    private val coreRecoveryEnabled = AtomicBoolean(false)
    private val primaryPolicyBalancerAvailable = AtomicBoolean(false)
    private val activeOutboundUpdatesEnabled = AtomicBoolean(false)

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Sequentially closes and recreates the only Xray instance in this process
     * after Android changes the selected underlay. The owning service and any
     * VPN interface remain active, and unchanged upstream process-global state
     * never overlaps two live instances.
     */
    private fun resetCoreNetworkState(
        service: Service,
        previousNetworkKey: String?,
        newNetworkKey: String,
        newNetworkHandle: Long,
    ) {
        networkTransitions.incrementAndGet()
        PolicyRouteCache.setCurrentNetwork(newNetworkKey, newNetworkHandle)
        if (!coreController.isRunning) {
            networkTransitions.decrementAndGet()
            return
        }

        val profileGuid = runningProfileGuid
        val transitionSnapshot = PolicyRouteCache.snapshot()
        coreScope.launch {
            networkResetMutex.withLock {
                try {
                    val currentTransition = PolicyRouteCache.snapshot()
                    if (!coreRecoveryEnabled.get() ||
                        runningProfileGuid != profileGuid ||
                        currentTransition.generation != transitionSnapshot.generation ||
                        currentTransition.networkHandle != transitionSnapshot.networkHandle ||
                        !coreController.isRunning
                    ) {
                        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Superseded network recovery skipped")
                        return@withLock
                    }
                    val currentHasPrimaryBalancer = primaryPolicyBalancerAvailable.get()
                    if (currentHasPrimaryBalancer) {
                        PolicyRouteCache.remember(
                            previousNetworkKey,
                            profileGuid,
                            currentPrimaryBalancerTarget(),
                            transitionSnapshot.generation,
                        )
                    }
                    val refreshedConfig = buildRefreshedCoreConfig(service, profileGuid)
                    val latestTransition = PolicyRouteCache.snapshot()
                    if (!coreRecoveryEnabled.get() ||
                        runningProfileGuid != profileGuid ||
                        latestTransition.generation != transitionSnapshot.generation ||
                        latestTransition.networkHandle != transitionSnapshot.networkHandle ||
                        !coreController.isRunning
                    ) {
                        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Superseded network recovery skipped")
                        return@withLock
                    }
                    val nextHasPrimaryBalancer = refreshedConfig?.hasPrimaryBalancer
                        ?: currentHasPrimaryBalancer
                    val warmTarget = if (nextHasPrimaryBalancer) {
                        PolicyRouteCache.lookup(latestTransition.networkKey, profileGuid).orEmpty()
                    } else {
                        ""
                    }
                    when {
                        refreshedConfig != null && nextHasPrimaryBalancer -> {
                            coreController.resetNetworkStateWithConfigAndWarmRoute(
                                refreshedConfig.content,
                                AppConfig.TAG_BALANCER,
                                warmTarget,
                            )
                        }

                        refreshedConfig != null -> {
                            coreController.resetNetworkStateWithConfig(refreshedConfig.content)
                        }

                        nextHasPrimaryBalancer -> {
                            coreController.resetNetworkStateWithWarmRoute(AppConfig.TAG_BALANCER, warmTarget)
                        }

                        else -> coreController.resetNetworkState()
                    }
                    primaryPolicyBalancerAvailable.set(nextHasPrimaryBalancer)
                    reconcilePolicyRouteTracking()
                    serviceControl?.get()?.let {
                        emitActiveOutbound(it, if (nextHasPrimaryBalancer) warmTarget else "")
                    }
                    LogUtil.i(
                        AppConfig.TAG,
                        "StartCore-Manager: Core network state reset" +
                            if (warmTarget.isNotEmpty()) " with cached policy route" else "",
                    )
                } catch (e: Exception) {
                    if (coreController.isRunning) {
                        LogUtil.e(
                            AppConfig.TAG,
                            "StartCore-Manager: Core network reset failed; continuing with the running core",
                            e,
                        )
                    } else {
                        coreRecoveryEnabled.set(false)
                        primaryPolicyBalancerAvailable.set(false)
                        stopPolicyRoutePolling()
                        stopActiveOutboundPolling()
                        LogUtil.e(
                            AppConfig.TAG,
                            "StartCore-Manager: Core network reset and recovery failed; keeping traffic fail-closed",
                            e,
                        )
                        getService()?.let { service ->
                            val message = service.getString(R.string.notification_core_recovery_failed)
                            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
                            NotificationManager.showCoreFailure(message)
                        }
                    }
                } finally {
                    if (networkTransitions.decrementAndGet() == 0 && isPolicyRouteTrackingActive()) {
                        refreshFreshPolicyRoute()
                    }
                }
            }
        }
    }

    private fun buildRefreshedCoreConfig(service: Service, profileGuid: String): ConfigResult? {
        return try {
            val result = CoreConfigManager.getV2rayConfig(service, profileGuid)
            if (result.status && result.content.isNotBlank()) {
                result
            } else {
                LogUtil.w(
                    AppConfig.TAG,
                    "StartCore-Manager: Keeping the running configuration because refresh failed: " +
                        result.errorMessage.ifBlank { "generated configuration is empty" },
                )
                null
            }
        } catch (e: Exception) {
            LogUtil.w(
                AppConfig.TAG,
                "StartCore-Manager: Keeping the running configuration because refresh failed",
                e,
            )
            null
        }
    }

    private fun updateCoreNetworkIdentity(newNetworkKey: String, newNetworkHandle: Long) {
        val previous = PolicyRouteCache.snapshot()
        if (previous.networkKey == newNetworkKey && previous.networkHandle == newNetworkHandle) return
        PolicyRouteCache.setCurrentNetwork(newNetworkKey, newNetworkHandle)
        if (coreController.isRunning && isPolicyRouteTrackingActive()) {
            coreScope.launch { refreshFreshPolicyRoute() }
        }
    }

    private fun isPolicyRouteTrackingActive() =
        coreRecoveryEnabled.get() && primaryPolicyBalancerAvailable.get()

    private fun currentPrimaryBalancerTarget(): String {
        if (!isPolicyRouteTrackingActive()) return ""
        return try {
            coreController.getBalancerPrincipleTarget(AppConfig.TAG_BALANCER)
        } catch (e: Exception) {
            LogUtil.d(AppConfig.TAG, "Primary policy route unavailable: ${e.message}")
            ""
        }
    }

    private fun refreshFreshPolicyRoute() {
        if (!isPolicyRouteTrackingActive()) return
        val cacheSnapshot = PolicyRouteCache.snapshot()
        rememberFreshPolicyRoute(currentPrimaryBalancerTarget(), cacheSnapshot)
    }

    private fun startPolicyRoutePolling() {
        policyRoutePollJob?.cancel()
        if (!isPolicyRouteTrackingActive()) {
            policyRoutePollJob = null
            return
        }
        policyRoutePollJob = coreScope.launch {
            var lastTarget = ""
            while (isPolicyRouteTrackingActive()) {
                if (coreController.isRunning && networkTransitions.get() == 0) {
                    val target = currentPrimaryBalancerTarget()
                    if (target.isNotBlank() && target != lastTarget) {
                        if (rememberFreshPolicyRoute(target)) {
                            lastTarget = target
                        }
                    }
                }
                delay(POLICY_ROUTE_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolicyRoutePolling() {
        policyRoutePollJob?.cancel()
        policyRoutePollJob = null
    }

    private fun rememberFreshPolicyRoute(
        target: String?,
        cacheSnapshot: PolicyRouteCache.Snapshot = PolicyRouteCache.snapshot(),
    ): Boolean {
        if (!isPolicyRouteTrackingActive() || networkTransitions.get() != 0 || target.isNullOrBlank()) return false
        val profileGuid = runningProfileGuid
        if (PolicyRouteCache.rememberCurrent(cacheSnapshot, profileGuid, target)) {
            serviceControl?.get()?.let { emitActiveOutbound(it, target) }
            LogUtil.i(AppConfig.TAG, "Policy route cache accepted fresh observatory target")
            return true
        }
        return false
    }

    private fun reconcilePolicyRouteTracking() {
        if (isPolicyRouteTrackingActive()) {
            startPolicyRoutePolling()
            if (activeOutboundUpdatesEnabled.get()) {
                startActiveOutboundPolling()
            }
        } else {
            stopPolicyRoutePolling()
            stopActiveOutboundPolling()
        }
    }

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    fun setActiveOutboundUpdatesEnabled(enabled: Boolean) {
        activeOutboundUpdatesEnabled.set(enabled)
        if (enabled && isPolicyRouteTrackingActive()) {
            startActiveOutboundPolling()
        } else {
            stopActiveOutboundPolling()
        }
    }

    private fun currentActiveOutbound() = currentPrimaryBalancerTarget()

    private fun emitActiveOutbound(serviceControl: ServiceControl, target: String) {
        MessageHelper.sendMsg2UI(
            serviceControl.getService(),
            AppConfig.MSG_ACTIVE_OUTBOUND_CHANGED,
            target,
        )
    }

    private fun emitCurrentActiveOutbound(serviceControl: ServiceControl) {
        emitActiveOutbound(serviceControl, currentActiveOutbound())
    }

    private fun startActiveOutboundPolling() {
        if (!coreController.isRunning || !isPolicyRouteTrackingActive() ||
            activeOutboundPollJob?.isActive == true
        ) return

        activeOutboundPollJob?.cancel()
        activeOutboundPollJob = coreScope.launch {
            var lastTarget: String? = null
            while (coreController.isRunning && activeOutboundUpdatesEnabled.get() &&
                isPolicyRouteTrackingActive()
            ) {
                val target = currentActiveOutbound()
                if (target != lastTarget) {
                    serviceControl?.get()?.let { emitActiveOutbound(it, target) }
                    lastTarget = target
                }
                delay(ACTIVE_OUTBOUND_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopActiveOutboundPolling() {
        activeOutboundPollJob?.cancel()
        activeOutboundPollJob = null
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        // Commands and probe results originate only from another process of
        // this application. Keeping this receiver non-exported prevents other
        // apps from forging control or policy-route cache messages.
        ContextCompat.registerReceiver(
            service,
            mMsgReceive,
            IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val systemFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(service, mSystemEventReceive, systemFilter, Utils.receiverFlags())

        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }
        runningProfileGuid = guid
        primaryPolicyBalancerAvailable.set(result.hasPrimaryBalancer)
        coreRecoveryEnabled.set(true)
        try {
            coreController.startLoop(result.content, tunFd)
        } catch (e: Exception) {
            coreRecoveryEnabled.set(false)
            primaryPolicyBalancerAvailable.set(false)
            runningProfileGuid = ""
            throw e
        }

        if (!isRunning()) {
            coreRecoveryEnabled.set(false)
            primaryPolicyBalancerAvailable.set(false)
            runningProfileGuid = ""
            error("Core failed to start")
        }

        if (isPolicyRouteTrackingActive()) {
            coreScope.launch { refreshFreshPolicyRoute() }
        }
        reconcilePolicyRouteTracking()
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        stopActiveOutboundPolling()
        coreRecoveryEnabled.set(false)
        primaryPolicyBalancerAvailable.set(false)
        stopPolicyRoutePolling()
        PolicyRouteCache.clear()
        runningProfileGuid = ""
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }
        try {
            service.unregisterReceiver(mSystemEventReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister system receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
    private fun startNetworkMonitor(service: Service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            includeLocationInfo = NetworkIdentityResolver.canReadWifiIdentity(service),
            onUnderlyingNetworksChanged = { networks -> serviceControl?.get()?.setUnderlyingNetworks(networks) },
            onNetworkEvent = { event -> handleNetworkEvent(service, event) },
        ).also { it.register() }
    }

    private fun handleNetworkEvent(service: Service, event: NetworkMonitor.NetworkEvent) {
        val networkHandle = event.network.networkHandle
        val resolvedKey = event.capabilities?.let { capabilities ->
            runCatching { NetworkIdentityResolver.resolve(service, capabilities) }
                .onFailure { error ->
                    LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to resolve underlay identity", error)
                }
                .getOrNull()
        }

        if (!event.isHandover) {
            if (resolvedKey != null) {
                updateCoreNetworkIdentity(resolvedKey, networkHandle)
            }
            return
        }

        val previousNetworkKey = PolicyRouteCache.snapshot().networkKey
        // Capabilities normally arrive before the one-second debounce expires. A handle-scoped
        // fallback still guarantees recovery without accidentally borrowing another network's
        // cached route if Android withholds them.
        val newNetworkKey = resolvedKey ?: "network:$networkHandle"
        LogUtil.i(
            AppConfig.TAG,
            "NetworkMonitor: Recovering core on ${newNetworkKey.substringBefore(':')} handover",
        )
        resetCoreNetworkState(service, previousNetworkKey, newNetworkKey, networkHandle)
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay() {
        if (!isRunning()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":").orEmpty()
            }
            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":").orEmpty()
                }
            }

            val result = ConnectionTestResult(
                delayMillis = time,
                errorMessage = errorStr,
            )
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, result)

            // Only fetch IP info if the delay test was successful
            if (time >= 0) {
                SpeedtestManager.getRemoteIPInfo()?.let { ip ->
                    MessageHelper.sendMsg2UI(
                        service,
                        AppConfig.MSG_MEASURE_DELAY_RESULT,
                        result.copy(
                            country = ip.country,
                            ipAddress = ip.ipAddress,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback startup")
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback shutdown")
            return 0
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback onEmitStatus $s")
            return 0
        }

        override fun onBalancerTargetChanged(balancerTag: String?, target: String?): Long {
            if (balancerTag == AppConfig.TAG_BALANCER) {
                return if (rememberFreshPolicyRoute(target)) 0 else 1
            }
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * App-private receiver for control messages sent to the service.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages from another app process.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (isRunning()) {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                        emitCurrentActiveOutbound(serviceControl)
                    } else {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    // The UI and daemon run in separate processes, so acknowledge the active
                    // daemon before stopping it instead of relying on possibly stale UI state.
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            serviceControl.stopService()
                            delay(500L)
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }

            }

        }
    }

    private class SystemEventHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification()
                }
            }
        }
    }
}
