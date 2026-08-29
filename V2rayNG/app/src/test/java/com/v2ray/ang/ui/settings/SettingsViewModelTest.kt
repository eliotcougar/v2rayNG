package com.v2ray.ang.ui.settings

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startupSettingsLoadAndPersistInActionOrder() = runTest(dispatcher) {
        val store = FakeStartupSettingsStore(autoConnect = true, startOnBoot = false)
        val viewModel = SettingsViewModel.createForTest(mock<Application>(), store, dispatcher)
        assertEquals(StartupSettingsState(true, false), viewModel.startupSettings.value)

        viewModel.setAutoConnectOnAppStart(false)
        viewModel.setStartOnBoot(true)
        assertEquals(StartupSettingsState(false, true), viewModel.startupSettings.value)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("auto:false", "boot:true"), store.writes)
    }

    private class FakeStartupSettingsStore(
        private val autoConnect: Boolean,
        private val startOnBoot: Boolean
    ) : StartupSettingsStore {
        val writes = mutableListOf<String>()
        override fun autoConnectOnAppStart(): Boolean = autoConnect
        override fun startOnBoot(): Boolean = startOnBoot
        override fun setAutoConnectOnAppStart(enabled: Boolean) { writes += "auto:$enabled" }
        override fun setStartOnBoot(enabled: Boolean) { writes += "boot:$enabled" }
    }
}
