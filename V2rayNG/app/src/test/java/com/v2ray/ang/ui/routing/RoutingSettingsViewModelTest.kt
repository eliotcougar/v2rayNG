package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class RoutingSettingsViewModelTest {

    @Test
    fun updateAndRemoveResolveCurrentPositionByRuleId() {
        val store = FakeRoutingRulesDataSource(rule("a"), rule("b"))
        val viewModel = RoutingSettingsViewModel.createForTest(mock<Application>(), store)
        viewModel.reload()
        viewModel.move(0, 1)

        viewModel.update("a", rule("a", enabled = false))
        viewModel.remove("b")

        assertEquals(listOf("a"), viewModel.rulesetsFlow.value.map { it.id })
        assertFalse(viewModel.rulesetsFlow.value.single().enabled)
        assertEquals(listOf("a"), store.items.map { it.id })
    }

    @Test
    fun missingRuleIdDoesNotMutateOrPersistAnotherRule() {
        val store = FakeRoutingRulesDataSource(rule("a"))
        val viewModel = RoutingSettingsViewModel.createForTest(mock<Application>(), store)
        viewModel.reload()

        viewModel.update("missing", rule("missing", enabled = false))
        viewModel.remove("missing")

        assertEquals(listOf("a"), viewModel.rulesetsFlow.value.map { it.id })
        assertTrue(store.items.single().enabled)
    }

    @Test
    fun reloadMigratesMissingIdsInOneListWrite() {
        val store = FakeRoutingRulesDataSource(rule(""))
        val viewModel = RoutingSettingsViewModel.createForTest(mock<Application>(), store) { "generated" }

        viewModel.reload()

        assertEquals("generated", viewModel.rulesetsFlow.value.single().id)
        assertEquals(1, store.saveAllCount)
    }

    private fun rule(id: String, enabled: Boolean = true) = RulesetItem(id = id, remarks = id, enabled = enabled)

    private class FakeRoutingRulesDataSource(vararg initial: RulesetItem) : RoutingRulesDataSource {
        var items = initial.map { it.copy() }.toMutableList()
        var saveAllCount = 0

        override fun load(): MutableList<RulesetItem> = items.map { it.copy() }.toMutableList()
        override fun save(ruleId: String, item: RulesetItem): Boolean {
            val index = items.indexOfFirst { it.id == ruleId }
            if (index < 0) return false
            items[index] = item.copy()
            return true
        }
        override fun remove(ruleId: String): Boolean = items.removeAll { it.id == ruleId }
        override fun saveAll(items: List<RulesetItem>) {
            this.items = items.map { it.copy() }.toMutableList()
            saveAllCount++
        }
    }
}
