package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainBatchedTestResultsTest {
    private val profile = ProfileItem(configType = EConfigType.VMESS)
    private val state = ServerGroupUiState(
        servers = listOf(ServersCache("a", profile), ServersCache("b", profile)),
        rows = listOf("a", "b").map { ServerRowUiModel(it, profile, it, "", "VMESS", 0L, "") }
    )

    @Test
    fun batchUpdatesSourceAndRowsByGuidWithoutChangingOrderOrMetadata() {
        val updated = state.withTestResults(mapOf("b" to -1L, "a" to 32L, "removed" to 10L))

        assertEquals(listOf("a", "b"), updated.servers.map { it.guid })
        assertEquals(listOf(32L, -1L), updated.servers.map { it.testDelayMillis })
        assertEquals(listOf(32L, -1L), updated.rows.map { it.testDelayMillis })
        assertEquals(state.rows[0].copy(testDelayMillis = 32L), updated.rows[0])
        assertSame(profile, updated.servers[0].profile)
        assertEquals(0L, state.servers[0].testDelayMillis)
    }

    @Test
    fun emptyUnknownAndUnchangedResultsPreserveExistingItems() {
        assertSame(state, state.withTestResults(emptyMap()))
        for (updates in listOf(mapOf("removed" to 10L), mapOf("a" to 0L))) {
            val updated = state.withTestResults(updates)
            assertEquals(state, updated)
            assertSame(state.servers[0], updated.servers[0])
            assertSame(state.rows[0], updated.rows[0])
        }
        assertEquals(ServerGroupUiState(), ServerGroupUiState().withTestResults(mapOf("a" to 32L)))
    }
}
