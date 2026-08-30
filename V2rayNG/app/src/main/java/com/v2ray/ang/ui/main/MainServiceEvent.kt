package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.RealPingResult

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    // Snapshot supplied by the daemon; current selection may already refer to another server.
    data class StateStartSuccess(val serverName: String) : MainServiceEvent()
    data object StateStartFailure : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelayResult(val result: ConnectionTestResult) : MainServiceEvent()
    data class ActiveOutboundChanged(val target: String) : MainServiceEvent()
    data class MeasureConfigSuccess(val result: RealPingResult) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String) : MainServiceEvent()
    data class MeasureConfigFinish(val finishedCount: String?) : MainServiceEvent()
    data class SubscriptionDataChanged(val subscriptionIds: List<String>) : MainServiceEvent()
}

internal fun MainServiceEvent.StateStartSuccess.accessibilityMessage(
    formatConnectedTo: (String) -> String,
): String? = serverName.trim().takeIf(String::isNotEmpty)?.let(formatConnectedTo)
