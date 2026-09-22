package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.ConnectionTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class MainConnectionStatusTest {
    private val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 16)
    private val source = mock(MainDataSource::class.java)
    private lateinit var viewModel: MainViewModel
    private lateinit var requestId: String

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        `when`(source.mainServiceEvent).thenReturn(events)
        `when`(source.getSelectedSubscriptionId()).thenReturn("")
        `when`(source.getSubscriptions()).thenReturn(emptyList())
        doAnswer { requestId = it.getArgument(0); null }.`when`(source).testCurrentServerRealPing(anyString())
        viewModel = MainViewModel(mock(Application::class.java), source)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun testResultsDoNotReplaceConnectionStateOrDisappearOnRunningRefresh() {
        assertEquals(MainStatus.Disconnected, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.testStatus)
        events.tryEmit(MainServiceEvent.StateRunning)
        for (result in listOf(ConnectionTestResult(42), ConnectionTestResult(-1, "unreachable"))) {
            viewModel.testCurrentServerRealPing()
            assertTrue(viewModel.uiState.value.isTesting)
            assertEquals(MainStatus.Testing, viewModel.uiState.value.testStatus)
            assertEquals(MainStatus.Connected, viewModel.uiState.value.status)
            events.tryEmit(MainServiceEvent.MeasureDelayResult(result, requestId))
            assertFalse(viewModel.uiState.value.isTesting)
            assertEquals(MainStatus.ConnectionTest(result), viewModel.uiState.value.testStatus)
            events.tryEmit(MainServiceEvent.StateRunning)
            assertEquals(MainStatus.ConnectionTest(result), viewModel.uiState.value.testStatus)
            assertEquals(MainStatus.Connected, viewModel.uiState.value.status)
        }
    }

    @Test
    fun staleReplyCannotFinishNewTestAndCancellationKeepsConnectionState() {
        events.tryEmit(MainServiceEvent.StateRunning)
        viewModel.testCurrentServerRealPing()
        val oldRequestId = requestId
        viewModel.testCurrentServerRealPing()
        events.tryEmit(MainServiceEvent.MeasureDelayResult(ConnectionTestResult(42), oldRequestId))
        assertTrue(viewModel.uiState.value.isTesting)
        assertEquals(MainStatus.Testing, viewModel.uiState.value.testStatus)
        events.tryEmit(MainServiceEvent.MeasureDelayCancelled(requestId))
        assertFalse(viewModel.uiState.value.isTesting)
        assertNull(viewModel.uiState.value.testStatus)
        assertEquals(MainStatus.Connected, viewModel.uiState.value.status)
    }

    @Test
    fun stopInvalidatesCurrentTestAndRejectsItsLateReply() {
        events.tryEmit(MainServiceEvent.StateRunning)
        viewModel.testCurrentServerRealPing()
        events.tryEmit(MainServiceEvent.StateNotRunning)
        events.tryEmit(MainServiceEvent.MeasureDelayResult(ConnectionTestResult(42), requestId))
        assertFalse(viewModel.uiState.value.isTesting)
        assertNull(viewModel.uiState.value.testStatus)
        assertEquals(MainStatus.Disconnected, viewModel.uiState.value.status)
    }

    @Test
    fun emptyBulkTestLeavesConnectionStateUntouched() {
        events.tryEmit(MainServiceEvent.StateRunning)
        viewModel.testAllRealPing()
        assertFalse(viewModel.uiState.value.isTesting)
        assertNull(viewModel.uiState.value.testStatus)
        assertEquals(MainStatus.Connected, viewModel.uiState.value.status)
    }
}
