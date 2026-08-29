package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.dto.entities.SubscriptionCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class MainAutoConnectTest {
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
    fun unknownDaemonStateRequestsOneStartAfterTimeout() = runTest(dispatcher) {
        val viewModel = MainViewModel(mock<Application>(), dataSource(autoConnect = true, selectedGuid = "server"))
        val effects = collectEffects(viewModel)

        viewModel.onAction(MainAction.AppResumed)
        advanceTimeBy(499)
        assertTrue(effects.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        viewModel.onAction(MainAction.AppResumed)
        runCurrent()

        assertEquals(listOf(MainActivityEffect.RequestAutoConnect), effects)
    }

    @Test
    fun knownRunningDaemonDoesNotRequestStart() = runTest(dispatcher) {
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 1)
        val viewModel = MainViewModel(mock<Application>(), dataSource(true, "server", events))
        val effects = collectEffects(viewModel)
        runCurrent()
        events.emit(MainServiceEvent.StateRunning)
        runCurrent()

        viewModel.onAction(MainAction.AppResumed)
        runCurrent()

        assertTrue(effects.isEmpty())
    }

    @Test
    fun disabledSettingOrMissingServerDoesNotRequestStart() = runTest(dispatcher) {
        val disabled = MainViewModel(mock<Application>(), dataSource(false, "server"))
        val missingServer = MainViewModel(mock<Application>(), dataSource(true, null))
        val disabledEffects = collectEffects(disabled)
        val missingServerEffects = collectEffects(missingServer)

        disabled.onAction(MainAction.AppResumed)
        missingServer.onAction(MainAction.AppResumed)
        advanceTimeBy(501)
        runCurrent()

        assertTrue(disabledEffects.isEmpty())
        assertTrue(missingServerEffects.isEmpty())
    }

    @Test
    fun profileReloadDoesNotCancelPendingDaemonStateCheck() = runTest(dispatcher) {
        val viewModel = MainViewModel(mock<Application>(), dataSource(true, "server"))
        val effects = collectEffects(viewModel)

        viewModel.onAction(MainAction.AppResumed)
        viewModel.reloadAllGroups(emptyList())
        advanceTimeBy(501)
        runCurrent()

        assertEquals(listOf(MainActivityEffect.RequestAutoConnect), effects)
    }

    @Test
    fun launcherIntentAllowsAnotherAttemptOnlyWhileStopped() = runTest(dispatcher) {
        val viewModel = MainViewModel(mock<Application>(), dataSource(true, "server"))
        val effects = collectEffects(viewModel)

        viewModel.onAction(MainAction.AppResumed)
        advanceTimeBy(501)
        runCurrent()
        viewModel.onAction(MainAction.ResetAutoConnectAttempt)
        viewModel.onAction(MainAction.AppResumed)
        advanceTimeBy(501)
        runCurrent()

        assertEquals(2, effects.size)
    }

    private fun kotlinx.coroutines.test.TestScope.collectEffects(viewModel: MainViewModel): MutableList<MainActivityEffect> {
        val effects = mutableListOf<MainActivityEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.activityEffects.collect { effects += it }
        }
        return effects
    }

    private fun dataSource(
        autoConnect: Boolean,
        selectedGuid: String?,
        events: MutableSharedFlow<MainServiceEvent> = MutableSharedFlow(extraBufferCapacity = 1)
    ): MainDataSource = mock {
        on { mainServiceEvent } doReturn events
        on { getSelectedSubscriptionId() } doReturn ""
        on { getSelectServer() } doReturn selectedGuid
        on { getConfirmRemove() } doReturn false
        on { getDoubleColumnDisplay() } doReturn false
        on { isGroupAllDisplayEnabled() } doReturn false
        on { isAutoConnectOnAppStartEnabled() } doReturn autoConnect
        on { getSubscriptions() } doReturn emptyList<SubscriptionCache>()
        on { getServerGuidList("") } doReturn emptyList()
        on { getSubsList() } doReturn emptyList()
    }
}
