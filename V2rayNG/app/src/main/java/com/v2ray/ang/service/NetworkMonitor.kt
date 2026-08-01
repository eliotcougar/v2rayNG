package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.launch

/**
 * Watches the network that carries the tunnel and reports topology changes.
 *
 * Cellular -> Wi-Fi is a make-before-break handover: the new network is announced while the old one
 * is still connected, so the socket to the server is never reset and the core keeps using a dead
 * connection. Deciding that a handover happened is what this class is for, acting on it is not.
 *
 * Only used from Android P and above, see CoreServiceManager.startNetworkMonitor().
 * [onNetworkEvent] receives the initial/current network immediately, while real handovers are
 * delivered on a background thread after the debounce window and may block.
 */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val includeLocationInfo: Boolean,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onNetworkEvent: (NetworkEvent) -> Unit,
) {
    data class NetworkEvent(
        val network: Network,
        val capabilities: NetworkCapabilities?,
        val isHandover: Boolean,
    )

    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    private var upstream: Network? = null
    private var handoverJob: Job? = null
    @Volatile
    private var currentCapabilities: NetworkCapabilities? = null
    private var registered = false

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private val request by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val callback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && includeLocationInfo) {
            createLocationAwareCallback()
        } else {
            createNetworkCallback()
        }
    }

    /**
     * Starts watching. Safe to call more than once, only the first call registers.
     */
    fun register() {
        if (registered) return
        try {
            connectivity.requestNetwork(request, callback)
            registered = true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to request network", e)
        }
    }

    /**
     * Stops watching and drops the tracked state. Safe to call more than once.
     */
    fun unregister() {
        handoverJob?.cancel()
        handoverJob = null
        currentCapabilities = null
        upstream = null
        if (!registered) return
        registered = false
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to unregister callback", e)
        }
    }

    private fun createNetworkCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = handleAvailable(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            handleCapabilitiesChanged(network, capabilities)

        override fun onLost(network: Network) = handleLost(network)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createLocationAwareCallback() = object : ConnectivityManager.NetworkCallback(
        ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
    ) {
        override fun onAvailable(network: Network) = handleAvailable(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            handleCapabilitiesChanged(network, capabilities)

        override fun onLost(network: Network) = handleLost(network)
    }

    private fun handleAvailable(network: Network) {
        val previous = upstream
        upstream = network
        val capabilities = runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull()
        currentCapabilities = capabilities
        onUnderlyingNetworksChanged(arrayOf(network))

        if (previous != null && previous != network) {
            scheduleHandover(network)
        } else if (handoverJob?.isActive != true) {
            notifyNetworkEvent(network, capabilities, isHandover = false)
        }
    }

    private fun handleCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        currentCapabilities = capabilities
        onUnderlyingNetworksChanged(arrayOf(network))
        // Keep the previous identity until the pending handover has cached its working route.
        if (handoverJob?.isActive != true) {
            notifyNetworkEvent(network, capabilities, isHandover = false)
        }
    }

    private fun handleLost(@Suppress("UNUSED_PARAMETER") network: Network) {
        currentCapabilities = null
        onUnderlyingNetworksChanged(null)
    }

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream is now $network")
        handoverJob?.cancel()
        handoverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(HANDOVER_DEBOUNCE_MS)
                notifyNetworkEvent(network, currentCapabilities, isHandover = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to handle upstream change", e)
            }
        }
    }

    private fun notifyNetworkEvent(
        network: Network,
        capabilities: NetworkCapabilities?,
        isHandover: Boolean,
    ) {
        onNetworkEvent(
            NetworkEvent(
                network = network,
                capabilities = capabilities,
                isHandover = isHandover,
            )
        )
    }
}
