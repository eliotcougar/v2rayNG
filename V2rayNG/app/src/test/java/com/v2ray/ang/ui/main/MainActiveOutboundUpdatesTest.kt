package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainActiveOutboundUpdatesTest {
    @Test
    fun visibilityRequestsDaemonPollingOnlyForAdaptivePolicyGroups() {
        val fixture = Fixture()
        for (type in listOf("", "1", "2", "3")) {
            whenever(fixture.source.decodeServerConfig("selected"))
                .thenReturn(ProfileItem(configType = EConfigType.POLICYGROUP, policyGroupType = type))
            clearInvocations(fixture.source)
            fixture.viewModel.onAction(MainAction.MainUiVisibilityChanged(true))
            verify(fixture.source).sendMsg2Service(AppConfig.MSG_SET_ACTIVE_OUTBOUND_UPDATES, if (type in listOf("", "1")) "1" else "0")
        }
        whenever(fixture.source.decodeServerConfig("selected")).thenReturn(null)
        clearInvocations(fixture.source)
        fixture.viewModel.onAction(MainAction.MainUiVisibilityChanged(true))
        verify(fixture.source).sendMsg2Service(AppConfig.MSG_SET_ACTIVE_OUTBOUND_UPDATES, "0")
    }

    @Test
    fun daemonRestartResubscribesAndBackgroundingDisablesUpdates() {
        val fixture = Fixture()
        whenever(fixture.source.decodeServerConfig("selected"))
            .thenReturn(ProfileItem(configType = EConfigType.POLICYGROUP, policyGroupType = ""))
        fixture.viewModel.onAction(MainAction.MainUiVisibilityChanged(true))
        clearInvocations(fixture.source)

        fixture.events.tryEmit(MainServiceEvent.StateRunning)
        verify(fixture.source).sendMsg2Service(AppConfig.MSG_SET_ACTIVE_OUTBOUND_UPDATES, "1")
        fixture.viewModel.onAction(MainAction.MainUiVisibilityChanged(false))
        verify(fixture.source).sendMsg2Service(AppConfig.MSG_SET_ACTIVE_OUTBOUND_UPDATES, "0")
    }

    private class Fixture {
        val source = mock<MainDataSource>()
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 1)
        val viewModel: MainViewModel

        init {
            whenever(source.mainServiceEvent).thenReturn(events)
            whenever(source.getSelectedSubscriptionId()).thenReturn("")
            whenever(source.getSelectServer()).thenReturn("selected")
            viewModel = MainViewModel(mock<Application>(), source, Dispatchers.Unconfined)
            runBlocking { viewModel.setupGroupTab().join() }
        }
    }
}
