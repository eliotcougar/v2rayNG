package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MainSubscriptionRefreshTest {

    @Test
    fun populateSubscriptionGroupData_refreshesChangedGroupsAndAggregateState() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()

            assertEquals(2, fixture.viewModel.serverGroupState(ALL).value.servers.size)
            assertEquals(1, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
            assertEquals(1, fixture.viewModel.serverGroupState(GROUP_B).value.servers.size)

            fixture.addProfile(GROUP_A, "a-2")
            fixture.selectedGuid = "a-2"
            fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

            assertEquals(3, fixture.viewModel.serverGroupState(ALL).value.servers.size)
            assertEquals(2, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
            assertEquals(2, fixture.viewModel.serverGroupState(GROUP_A).value.rows.size)
            assertEquals(1, fixture.viewModel.serverGroupState(GROUP_B).value.servers.size)
            assertEquals(GROUP_A, fixture.viewModel.uiState.value.selectedGroupId)
            assertEquals("a-2", fixture.viewModel.uiState.value.selectedGuid)
        }
    }

    @Test
    fun subscriptionCompletionEvent_refreshesCountsAndHeaderMetadata() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()
            fixture.addProfile(GROUP_A, "a-2")
            fixture.setUsageInfo(GROUP_A, SubscriptionUsageInfo(download = 42, total = 100))

            fixture.events.emit(MainServiceEvent.SubscriptionDataChanged(listOf(GROUP_A)))

            withTimeout(5000) {
                fixture.viewModel.serverGroupState(ALL).first { it.servers.size == 3 }
                fixture.viewModel.uiState.first { state ->
                    state.groups.first { it.id == GROUP_A }.usageInfo?.download == 42L
                }
            }
            val group = fixture.viewModel.uiState.value.groups.first { it.id == GROUP_A }
            assertEquals(2, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
            assertEquals("https://example.com/group-a", group.usageInfo?.webPageUrl)
            assertEquals(100L, group.usageInfo?.total)
            assertEquals("Group A", group.remarks)
        }
    }

    @Test
    fun populateSubscriptionGroupData_clearsWithdrawnHeadersAndRetainsOtherGroups() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()
            fixture.setUsageInfo(GROUP_A, SubscriptionUsageInfo(download = 10))
            fixture.setUsageInfo(GROUP_B, SubscriptionUsageInfo(download = 20))
            fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A, GROUP_B))
            assertEquals(10L, fixture.groupUsageInfo(GROUP_A)?.download)

            fixture.setUsageInfo(GROUP_A, null)
            fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

            assertNull(fixture.groupUsageInfo(GROUP_A))
            assertEquals(20L, fixture.groupUsageInfo(GROUP_B)?.download)
        }
    }

    @Test
    fun populateSubscriptionGroupData_refreshesAnEmptySubscriptionAfterPruning() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()
            fixture.removeProfiles(GROUP_A)

            fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

            assertEquals(0, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
            assertEquals(0, fixture.viewModel.serverGroupState(GROUP_A).value.rows.size)
            assertEquals(1, fixture.viewModel.serverGroupState(ALL).value.servers.size)
        }
    }

    @Test
    fun populateSubscriptionGroupData_continuesWithOtherGroupsAfterLoadFailure() = runBlocking {
        // Avoid Android logging and MMKV initialization in this JVM failure-path test.
        val logPriority = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
        val previousPriority = logPriority.getInt(null)
        logPriority.setInt(null, Int.MAX_VALUE)
        try {
            Fixture().use { fixture ->
                fixture.loadInitialState()
                fixture.addProfile(GROUP_A, "a-2")
                fixture.loadFailures[GROUP_A] = IllegalStateException("Unable to read group")

                fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

                assertEquals(1, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
                assertEquals(3, fixture.viewModel.serverGroupState(ALL).value.servers.size)
            }
        } finally {
            logPriority.setInt(null, previousPriority)
        }
    }

    @Test
    fun populateSubscriptionGroupData_toleratesADeletedSubscription() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()
            fixture.removeProfiles(GROUP_A)
            fixture.removeSubscription(GROUP_A)

            fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A))

            assertEquals(0, fixture.viewModel.serverGroupState(GROUP_A).value.servers.size)
            assertEquals(1, fixture.viewModel.serverGroupState(ALL).value.servers.size)
            assertNull(fixture.groupUsageInfo(GROUP_A))
        }
    }

    @Test
    fun populateSubscriptionGroupData_propagatesCancellation() = runBlocking {
        Fixture().use { fixture ->
            fixture.loadInitialState()
            fixture.addProfile(GROUP_A, "a-2")
            fixture.loadFailures[GROUP_A] = CancellationException("Refresh cancelled")

            assertThrows(CancellationException::class.java) {
                runBlocking { fixture.viewModel.populateSubscriptionGroupData(listOf(GROUP_A)) }
            }

            assertEquals(2, fixture.viewModel.serverGroupState(ALL).value.servers.size)
        }
    }

    @Test
    fun subscriptionGroupRefreshOrder_prioritizesSelectedGroupAndDeduplicatesIds() {
        assertEquals(
            emptyList<String>(),
            subscriptionGroupRefreshOrder(listOf(ALL, GROUP_A), GROUP_A, emptyList())
        )
        assertEquals(
            listOf(GROUP_B, GROUP_A, ALL),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_B,
                changedSubscriptionIds = listOf(GROUP_A, GROUP_B, GROUP_A),
            )
        )
        assertEquals(
            listOf(ALL),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_A,
                changedSubscriptionIds = listOf("not-visible"),
            )
        )
        assertEquals(
            emptyList<String>(),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(ALL, GROUP_A, GROUP_B),
                selectedGroupId = GROUP_A,
                changedSubscriptionIds = listOf("", " "),
            )
        )
        assertEquals(
            emptyList<String>(),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = listOf(GROUP_A, GROUP_B),
                selectedGroupId = GROUP_A,
                changedSubscriptionIds = listOf("not-visible"),
            )
        )
        assertEquals(
            emptyList<String>(),
            subscriptionGroupRefreshOrder(
                visibleGroupIds = emptyList(),
                selectedGroupId = ALL,
                changedSubscriptionIds = listOf(GROUP_A),
            )
        )
    }

    private class Fixture : AutoCloseable {
        private val groupGuids = linkedMapOf(
            GROUP_A to mutableListOf("a-1"),
            GROUP_B to mutableListOf("b-1"),
        )
        private val profiles = linkedMapOf(
            "a-1" to profile(GROUP_A, "a-1"),
            "b-1" to profile(GROUP_B, "b-1"),
        )
        private val dataSource = mock<MainDataSource>()
        private val subscriptions = linkedMapOf(
            ALL to SubscriptionItem(remarks = "All"),
            GROUP_A to SubscriptionItem(remarks = "Group A", url = "https://example.com/group-a"),
            GROUP_B to SubscriptionItem(remarks = "Group B", url = "https://example.com/group-b"),
        )
        val events = MutableSharedFlow<MainServiceEvent>()
        val loadFailures = mutableMapOf<String, Exception>()
        var selectedGuid: String? = null

        val viewModel: MainViewModel

        init {
            whenever(dataSource.mainServiceEvent).thenReturn(events)
            whenever(dataSource.getSelectedSubscriptionId()).thenReturn(GROUP_A)
            whenever(dataSource.getSelectServer()).thenAnswer { selectedGuid }
            whenever(dataSource.getConfirmRemove()).thenReturn(false)
            whenever(dataSource.getDoubleColumnDisplay()).thenReturn(false)
            whenever(dataSource.getSubscriptions()).thenAnswer {
                subscriptions.map { (id, item) -> SubscriptionCache(id, item) }
            }
            whenever(dataSource.getServerGuidList(any())).thenAnswer { invocation ->
                val groupId = invocation.getArgument<String>(0)
                loadFailures[groupId]?.let { throw it }
                if (groupId == ALL) profiles.keys.toList()
                else groupGuids[groupId]?.toList().orEmpty()
            }
            whenever(dataSource.decodeServerConfig(any())).thenAnswer { invocation ->
                profiles[invocation.getArgument<String>(0)]
            }
            whenever(dataSource.getSubscriptionItem(any())).thenAnswer { invocation ->
                subscriptions[invocation.getArgument<String>(0)]
            }

            viewModel = MainViewModel(
                application = mock<Application>(),
                dataSource = dataSource,
                serviceEventDispatcher = Dispatchers.Unconfined,
            )
        }

        suspend fun loadInitialState() {
            withTimeout(5000) {
                viewModel.serverGroupState(ALL).first { it.servers.size == 2 }
                viewModel.serverGroupState(GROUP_A).first { it.servers.size == 1 }
                viewModel.serverGroupState(GROUP_B).first { it.servers.size == 1 }
            }
        }

        fun addProfile(groupId: String, guid: String) {
            groupGuids.getValue(groupId) += guid
            profiles[guid] = profile(groupId, guid)
        }

        fun removeProfiles(groupId: String) {
            groupGuids.getValue(groupId).forEach(profiles::remove)
            groupGuids.getValue(groupId).clear()
        }

        fun setUsageInfo(groupId: String, info: SubscriptionUsageInfo?) {
            subscriptions.getValue(groupId).usageInfo = info
        }

        fun removeSubscription(groupId: String) {
            subscriptions.remove(groupId)
        }

        fun groupUsageInfo(groupId: String): SubscriptionUsageInfo? =
            viewModel.uiState.value.groups.first { it.id == groupId }.usageInfo

        override fun close() {
            viewModel.viewModelScope.cancel()
        }
    }

    companion object {
        private const val ALL = ""
        private const val GROUP_A = "group-a"
        private const val GROUP_B = "group-b"

        private fun profile(groupId: String, remarks: String) = ProfileItem(
            configType = EConfigType.VMESS,
            subscriptionId = groupId,
            remarks = remarks,
            description = "description",
        )
    }
}
