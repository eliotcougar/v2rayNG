package com.v2ray.ang.core

import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

class XhttpWakeRefreshTest {
    @Test
    fun idleEntryDoesNotRefreshAndIdleExitRunsOffTheReceiver() {
        val dispatcher = QueuedDispatcher()
        val refresh = XhttpWakeRefresh(dispatcher)
        var calls = 0
        refresh.start { calls++ }

        refresh.onDeviceIdleModeChanged(true)
        dispatcher.runPending()
        assertEquals(0, calls)

        refresh.onDeviceIdleModeChanged(false)
        assertEquals(0, calls)
        dispatcher.runPending()
        assertEquals(1, calls)
        refresh.stop()
    }

    @Test
    fun stopCancelsPendingWakeAndRejectsLaterEvents() {
        val dispatcher = QueuedDispatcher()
        val refresh = XhttpWakeRefresh(dispatcher)
        var calls = 0
        refresh.start { calls++ }
        refresh.onDeviceIdleModeChanged(false)

        refresh.stop()
        refresh.stop()
        refresh.onDeviceIdleModeChanged(false)
        dispatcher.runPending()
        assertEquals(0, calls)
    }

    @Test
    fun newServiceSessionCannotRunThePreviousOwnersWake() {
        val dispatcher = QueuedDispatcher()
        val refresh = XhttpWakeRefresh(dispatcher)
        var oldCalls = 0
        var newCalls = 0
        refresh.start { oldCalls++ }
        refresh.onDeviceIdleModeChanged(false)

        refresh.start { newCalls++ }
        refresh.onDeviceIdleModeChanged(false)
        dispatcher.runPending()
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        refresh.stop()
    }

    @Test
    fun stoppedServiceNeverStartsNativeWork() {
        val dispatcher = QueuedDispatcher()
        val refresh = XhttpWakeRefresh(dispatcher)
        refresh.onDeviceIdleModeChanged(false)
        dispatcher.runPending()
        assertEquals(0, dispatcher.dispatched)
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        var dispatched = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched++
            pending.addLast(block)
        }

        fun runPending() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }
}
