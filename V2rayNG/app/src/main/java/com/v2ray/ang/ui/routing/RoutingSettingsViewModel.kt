package com.v2ray.ang.ui.routing

import android.app.Application
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal interface RoutingRulesDataSource {
    fun load(): MutableList<RulesetItem>
    fun save(ruleId: String, item: RulesetItem): Boolean
    fun remove(ruleId: String): Boolean
    fun saveAll(items: List<RulesetItem>)
}

private object MmkvRoutingRulesDataSource : RoutingRulesDataSource {
    override fun load(): MutableList<RulesetItem> = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
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

    fun reload() {
        val loaded = dataSource.load()
        var needsSave = false
        loaded.forEach { item ->
            if (item.id.isEmpty()) {
                item.id = newRuleId()
                needsSave = true
            }
        }
        if (needsSave) dataSource.saveAll(loaded)
        rulesets.clear()
        rulesets.addAll(loaded)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun update(ruleId: String, item: RulesetItem) {
        val index = rulesets.indexOfFirst { it.id == ruleId }
        if (index < 0 || !dataSource.save(ruleId, item)) return
        rulesets[index] = item
        _rulesetsFlow.value = rulesets.toList()
    }

    fun remove(ruleId: String) {
        val index = rulesets.indexOfFirst { it.id == ruleId }
        if (index < 0 || !dataSource.remove(ruleId)) return
        rulesets.removeAt(index)
        _rulesetsFlow.value = rulesets.toList()
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (rulesets.moveItem(fromPosition, toPosition)) {
            dataSource.saveAll(rulesets)
            _rulesetsFlow.value = rulesets.toList()
        }
    }
}
