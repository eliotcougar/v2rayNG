package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NetworkMonitorTest {
    @Test
    fun callbacksAfterUnregisterCannotChangeTheUnderlay() = Fixture().use { f ->
        val network = mock<Network>()
        f.callback.onAvailable(network)
        f.monitor.unregister()
        f.callback.onAvailable(mock())
        f.callback.onCapabilitiesChanged(network, mock())
        f.callback.onLost(network)
        assertEquals(1, f.events.size)
        assertEquals(1, f.underlays.size)
        assertSame(network, f.underlays.single()!!.single())
    }

    @Test
    fun previousNetworkCallbacksCannotOverwriteTheNewUnderlay() = Fixture().use { f ->
        val previous = mock<Network>()
        val current = mock<Network>()
        val capabilities = mock<NetworkCapabilities>()
        f.callback.onAvailable(previous)
        f.callback.onAvailable(current)
        f.callback.onCapabilitiesChanged(current, capabilities)
        f.callback.onCapabilitiesChanged(previous, mock())
        f.callback.onLost(previous)
        assertTrue(f.handover.await(5, TimeUnit.SECONDS))
        assertSame(current, f.underlays.last()!!.single())
        assertSame(current, f.events.last().network)
        assertSame(capabilities, f.events.last().capabilities)
    }

    @Test
    fun losingTheCurrentNetworkCancelsItsPendingHandover() = Fixture().use { f ->
        f.callback.onAvailable(mock())
        val current = mock<Network>()
        f.callback.onAvailable(current)
        f.callback.onLost(current)
        assertNull(f.underlays.last())
        assertFalse(f.handover.await(1500, TimeUnit.MILLISECONDS))
        assertEquals(1, f.events.size)
    }

    @Test
    fun failedRegistrationCannotDeliverCallbacks() = Fixture(rejectRequest = true).use { f ->
        f.callback.onAvailable(mock())
        f.monitor.unregister()
        assertTrue(f.events.isEmpty())
        assertTrue(f.underlays.isEmpty())
        verify(f.connectivity, never()).unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
    }

    private class Fixture(rejectRequest: Boolean = false) : AutoCloseable {
        val connectivity = mock<ConnectivityManager>()
        val events = CopyOnWriteArrayList<NetworkMonitor.NetworkEvent>()
        val underlays = CopyOnWriteArrayList<List<Network>?>()
        val handover = CountDownLatch(1)
        private val log = mockStatic(Log::class.java)
        private val request = mock<NetworkRequest>()
        private val builders = mockConstruction(NetworkRequest.Builder::class.java) { builder, _ ->
            whenever(builder.addCapability(any())).thenReturn(builder)
            whenever(builder.build()).thenReturn(request)
        }
        val monitor = NetworkMonitor(
            connectivity = connectivity,
            includeLocationInfo = false,
            onUnderlyingNetworksChanged = { underlays.add(it?.toList()) },
            onNetworkEvent = {
                events.add(it)
                if (it.isHandover) handover.countDown()
            },
        )
        val callback: ConnectivityManager.NetworkCallback

        init {
            if (rejectRequest) {
                doThrow(SecurityException("denied")).whenever(connectivity)
                    .requestNetwork(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>())
            }
            monitor.register()
            val callbacks = argumentCaptor<ConnectivityManager.NetworkCallback>()
            verify(connectivity).requestNetwork(any<NetworkRequest>(), callbacks.capture())
            callback = callbacks.firstValue
        }

        override fun close() {
            monitor.unregister()
            builders.close()
            log.close()
        }
    }
}
