package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ReorderCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoutingSettingsViewModel(application: Application) : BaseViewModel(application) {
    private val mutationMutex = Mutex()
    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = _rulesetsFlow.value

    suspend fun reload() = mutationMutex.withLock {
        val loaded = withContext(Dispatchers.IO) {
            MmkvManager.decodeRoutingRulesetsForEditing().orEmpty()
        }
        _rulesetsFlow.value = loaded.toList()
    }

    suspend fun update(ruleId: String, item: RulesetItem): Boolean = mutationMutex.withLock {
        val rules = getAll()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return@withLock false
        val updated = item.copy(id = ruleId)
        val saved = withContext(Dispatchers.IO) { SettingsManager.saveRoutingRulesetById(ruleId, updated) }
        if (saved) _rulesetsFlow.value = rules.toMutableList().also { it[index] = updated }
        saved
    }

    suspend fun remove(ruleId: String) = mutationMutex.withLock {
        withContext(Dispatchers.IO) { MmkvManager.removeRoutingRuleset(ruleId) }
        // An already removed target must disappear locally too, without deleting a different row.
        _rulesetsFlow.value = getAll().filterNot { it.id == ruleId }
    }

    suspend fun move(ruleId: String, targetId: String): Boolean = mutationMutex.withLock {
        val rules = getAll()
        persistMove(rules.indexOfFirst { it.id == ruleId }, rules.indexOfFirst { it.id == targetId })
    }

    internal suspend fun move(ruleId: String, command: ReorderCommand): Boolean = mutationMutex.withLock {
        val rules = getAll()
        val from = rules.indexOfFirst { it.id == ruleId }
        val to = command.targetIndex(from, rules.size) ?: return@withLock false
        persistMove(from, to)
    }

    private suspend fun persistMove(from: Int, to: Int): Boolean {
        val reordered = getAll().toMutableList()
        if (!reordered.moveItem(from, to)) return false
        withContext(Dispatchers.IO) {
            check(MmkvManager.encodeRoutingRulesets(reordered)) { "Failed to persist routing rule order" }
        }
        _rulesetsFlow.value = reordered
        return true
    }
}
