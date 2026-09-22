package com.v2ray.ang.ui.settings

import com.v2ray.ang.util.LogUtil
import android.util.Log
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var androidLog: MockedStatic<Log>
    private val logLevel = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
    private var previousLogLevel = 0
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<SettingsViewModel>()

    @Before
    fun setUp() {
        androidLog = mockStatic(Log::class.java)
        // Keep platform logging from initializing native MMKV in a preference-store unit test.
        previousLogLevel = logLevel.getInt(null)
        logLevel.setInt(null, Log.WARN)
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        androidLog.close()
        logLevel.setInt(null, previousLogLevel)
    }

    @Test
    fun startupSettingsLoadAndPersistInActionOrder() = runTest(dispatcher) {
        val store = FakeStartupSettingsStore(startOnBoot = true)
        val viewModel = createViewModel(store)
        assertEquals(null, viewModel.startupSettings.value)
        assertEquals(0, store.reads)

        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, viewModel.startupSettings.value)
        assertEquals(emptyList<Boolean>(), store.writes)

        viewModel.setStartOnBoot(false)
        viewModel.setStartOnBoot(true)
        assertEquals(emptyList<Boolean>(), store.writes)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(false, true), store.writes)
        assertEquals(true, viewModel.startupSettings.value)

        val reopened = createViewModel(store)
        dispatcher.scheduler.runCurrent()
        assertEquals(viewModel.startupSettings.value, reopened.startupSettings.value)
    }

    @Test
    fun failedWritePreservesSavedValueAndAllowsRetry() = runTest(dispatcher) {
        val store = FakeStartupSettingsStore(startOnBoot = true)
        val viewModel = createViewModel(store)
        dispatcher.scheduler.runCurrent()

        store.canWrite = false
        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, viewModel.startupSettings.value)
        assertEquals(true, store.startOnBoot())

        store.canWrite = true
        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(false, viewModel.startupSettings.value)
        assertEquals(false, store.startOnBoot())
    }

    @Test
    fun thrownWriteDoesNotBlockTheNextChoice() = runTest(dispatcher) {
        val store = FakeStartupSettingsStore(true)
        val viewModel = createViewModel(store)
        dispatcher.scheduler.runCurrent()
        store.failure = IllegalStateException("storage unavailable")
        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, viewModel.startupSettings.value)

        store.failure = null
        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(false, viewModel.startupSettings.value)
    }

    @Test
    fun failedInitialReadKeepsTheControlDisabled() = runTest(dispatcher) {
        var writes = 0
        val viewModel = SettingsViewModel(mock<Application>(), { error("storage unavailable") }, { writes++; true }, dispatcher)
            .also(viewModels::add)
        dispatcher.scheduler.runCurrent()
        viewModel.setStartOnBoot(true)
        dispatcher.scheduler.runCurrent()
        assertEquals(null, viewModel.startupSettings.value)
        assertEquals(0, writes)
    }

    @Test
    fun vpnSettingsVisibilityFollowsActivityAvailability() = runBlocking(Dispatchers.IO) {
        val packageManager = mock<PackageManager>()
        val application = mock<Application>()
        whenever(application.packageManager).thenReturn(packageManager)
        var activity: ComponentName? = null

        mockConstruction(Intent::class.java) { intent, context ->
            assertEquals(listOf(Settings.ACTION_VPN_SETTINGS), context.arguments())
            whenever(intent.resolveActivity(packageManager)).thenAnswer { activity }
        }.use {
            val viewModel = SettingsViewModel(application, { false }, { true }, dispatcher)
                .also(viewModels::add)
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)

            activity = mock<ComponentName>()
            viewModel.refreshSystemVpnSettingsAvailability()
            assertTrue(viewModel.systemVpnSettingsAvailable.value)

            activity = null
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
        }
    }

    private fun createViewModel(store: FakeStartupSettingsStore) =
        SettingsViewModel(mock<Application>(), store::startOnBoot, store::setStartOnBoot, dispatcher).also(viewModels::add)

    private class FakeStartupSettingsStore(private var startOnBoot: Boolean) {
        var reads = 0
        var canWrite = true
        var failure: RuntimeException? = null
        val writes = mutableListOf<Boolean>()
        fun startOnBoot(): Boolean {
            reads++
            return startOnBoot
        }
        fun setStartOnBoot(enabled: Boolean): Boolean {
            writes += enabled
            failure?.let { throw it }
            if (canWrite) startOnBoot = enabled
            return canWrite
        }
    }
}
