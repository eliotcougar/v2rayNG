package com.v2ray.ang.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
    private val viewModels = mutableListOf<SettingsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun startupSettingsLoadAndPersistInActionOrder() = runTest(dispatcher) {
        val store = FakeStartupSettingsStore(startOnBoot = true)
        val viewModel = createViewModel(store)
        assertEquals(StartupSettingsState(), viewModel.startupSettings.value)
        assertEquals(0, store.reads)

        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(StartupSettingsState(true, isReady = true), viewModel.startupSettings.value)
        assertEquals(emptyList<Boolean>(), store.writes)

        viewModel.setStartOnBoot(false)
        viewModel.setStartOnBoot(true)
        assertEquals(emptyList<Boolean>(), store.writes)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(false, true), store.writes)
        assertEquals(StartupSettingsState(true, isReady = true), viewModel.startupSettings.value)

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
        assertEquals(StartupSettingsState(true, isReady = true), viewModel.startupSettings.value)
        assertEquals(true, store.startOnBoot())

        store.canWrite = true
        viewModel.setStartOnBoot(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(StartupSettingsState(false, isReady = true), viewModel.startupSettings.value)
        assertEquals(false, store.startOnBoot())
    }

    private fun createViewModel(store: StartupSettingsStore) =
        SettingsViewModel.createForTest(mock<Application>(), store, dispatcher).also(viewModels::add)

    private class FakeStartupSettingsStore(private var startOnBoot: Boolean) : StartupSettingsStore {
        var reads = 0
        var canWrite = true
        val writes = mutableListOf<Boolean>()
        override fun startOnBoot(): Boolean {
            reads++
            return startOnBoot
        }
        override fun setStartOnBoot(enabled: Boolean): Boolean {
            writes += enabled
            if (canWrite) startOnBoot = enabled
            return canWrite
        }
    }
}
