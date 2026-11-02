package com.v2ray.ang.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Wake work lives for one core service session, independently of connection delay tests. */
internal class XhttpWakeRefresh(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private var scope: CoroutineScope? = null
    private var onWake: (() -> Unit)? = null

    @Synchronized
    fun start(onWake: () -> Unit) {
        stop()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        this.onWake = onWake
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        onWake = null
    }

    @Synchronized
    fun onDeviceIdleModeChanged(isDeviceIdleMode: Boolean) {
        if (isDeviceIdleMode) return
        val onWake = onWake ?: return
        scope?.launch {
            ensureActive()
            onWake()
        }
    }
}
