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
import kotlinx.coroutines.withContext
import java.util.UUID
import com.v2ray.ang.handler.withUniqueRoutingRuleIds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RoutingRulesDataSource {
    fun load(): MutableList<RulesetItem>
    fun save(ruleId: String, item: RulesetItem): Boolean
    fun remove(ruleId: String): Boolean
    fun saveAll(items: List<RulesetItem>): Boolean
}

private object MmkvRoutingRulesDataSource : RoutingRulesDataSource {
    override fun load(): MutableList<RulesetItem> = MmkvManager.decodeRoutingRulesetsForEditing() ?: mutableListOf()
    override fun save(ruleId: String, item: RulesetItem): Boolean = SettingsManager.saveRoutingRulesetById(ruleId, item)
    override fun remove(ruleId: String): Boolean = SettingsManager.removeRoutingRulesetById(ruleId)
    override fun saveAll(items: List<RulesetItem>) = MmkvManager.encodeRoutingRulesets(items.toMutableList())
}

class RoutingSettingsViewModel private constructor(
    application: Application,
    private val dataSource: RoutingRulesDataSource,
    private val newRuleId: () -> String
) : BaseViewModel(application) {
    constructor(application: Application) : this(application, MmkvRoutingRulesDataSource, { UUID.randomUUID().toString() })

    internal companion object {
        fun createForTest(
            application: Application,
            dataSource: RoutingRulesDataSource,
            newRuleId: () -> String = { UUID.randomUUID().toString() }
        ) = RoutingSettingsViewModel(application, dataSource, newRuleId)
    }
    private val rulesets: MutableList<RulesetItem> = mutableListOf()

    private val _rulesetsFlow = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesetsFlow: StateFlow<List<RulesetItem>> = _rulesetsFlow.asStateFlow()

    fun getAll(): List<RulesetItem> = rulesets.toList()

    private var revision = 0L
    private val mutations = Mutex()

    suspend fun reload() {
        val requestedRevision = ++revision
        val loaded = withContext(Dispatchers.IO) {
            val raw = dataSource.load()
            val normalized = withUniqueRoutingRuleIds(raw, newRuleId)
            if (normalized != raw) check(dataSource.saveAll(normalized)) { "Failed to persist routing rule IDs" }
            normalized
        }
        if (revision != requestedRevision) return
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    suspend fun update(ruleId: String, item: RulesetItem): Boolean = mutations.withLock {
        revision++
        val saved = withContext(Dispatchers.IO) { dataSource.save(ruleId, item.copy(id = ruleId)) }
        if (!saved) return@withLock false
        val index = rulesets.indexOfFirst { it.id == ruleId }
        if (index >= 0) rulesets[index] = item.copy(id = ruleId)
        _rulesetsFlow.value = rulesets.toList()
        true
    }

    suspend fun remove(ruleId: String): Boolean = mutations.withLock {
        revision++
        if (!withContext(Dispatchers.IO) { dataSource.remove(ruleId) }) return@withLock false
        rulesets.removeAll { it.id == ruleId }
        _rulesetsFlow.value = rulesets.toList()
        true
    }

    suspend fun move(fromPosition: Int, toPosition: Int): Boolean {
        val ruleId = rulesets.getOrNull(fromPosition)?.id ?: return false
        val targetId = rulesets.getOrNull(toPosition)?.id ?: return false
        return mutations.withLock {
            val reordered = rulesets.toMutableList()
            if (!reordered.moveItem(reordered.indexOfFirst { it.id == ruleId }, reordered.indexOfFirst { it.id == targetId })) {
                return@withLock false
            }
            revision++
            if (!withContext(Dispatchers.IO) { dataSource.saveAll(reordered) }) return@withLock false
            rulesets.clear()
            rulesets.addAll(reordered)
            _rulesetsFlow.value = rulesets.toList()
            true
        }
    }

    internal suspend fun move(ruleId: String, command: ReorderCommand): Boolean {
        val fromPosition = rulesets.indexOfFirst { it.id == ruleId }
        val toPosition = command.targetIndex(fromPosition, rulesets.size) ?: return false
        return move(fromPosition, toPosition)
    }
}
