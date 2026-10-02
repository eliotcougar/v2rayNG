package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpeedtestRoutingTest {
    private fun config(primary: Boolean, burst: Boolean = false): V2rayConfig {
        val selectors = listOf("tested-", "unrelated-")
        return V2rayConfig(
            log = V2rayConfig.LogBean(), inbounds = arrayListOf(), outbounds = arrayListOf(),
            routing = V2rayConfig.RoutingBean("AsIs", rules = arrayListOf(
                V2rayConfig.RoutingBean.RulesBean(outboundTag = AppConfig.TAG_BLOCKED),
                V2rayConfig.RoutingBean.RulesBean(balancerTag = "unrelated"),
            ), balancers = listOfNotNull(
                V2rayConfig.RoutingBean.BalancerBean("unrelated", listOf("unrelated-")),
                if (primary) V2rayConfig.RoutingBean.BalancerBean(AppConfig.TAG_BALANCER, listOf("tested-")) else null,
            )),
            observatory = V2rayConfig.ObservatoryObject(if (burst) listOf("unrelated-") else selectors, "http://probe", "1m"),
            burstObservatory = V2rayConfig.BurstObservatoryObject(if (burst) selectors else listOf("unrelated-"),
                V2rayConfig.BurstObservatoryObject.PingConfigObject("http://probe", interval = "1m", sampling = 2)),
        )
    }

    @Test fun policyGroupUsesItsOwnBalancerAndRegularObserver() {
        val config = config(primary = true)
        CoreConfigManager.prepareSpeedtestRouting(config)
        assertEquals(listOf(AppConfig.TAG_BALANCER), config.routing.balancers!!.map { it.tag })
        assertEquals(listOf(V2rayConfig.RoutingBean.RulesBean(network = "tcp,udp", balancerTag = AppConfig.TAG_BALANCER)), config.routing.rules)
        assertEquals(listOf("tested-"), (config.observatory as V2rayConfig.ObservatoryObject).subjectSelector)
        assertNull(config.burstObservatory)
    }

    @Test fun leastLoadKeepsOnlyItsBurstObserver() {
        val config = config(primary = true, burst = true)
        CoreConfigManager.prepareSpeedtestRouting(config)
        assertNull(config.observatory)
        assertEquals(listOf("tested-"), (config.burstObservatory as V2rayConfig.BurstObservatoryObject).subjectSelector)
    }

    @Test fun standaloneAndChainProfilesKeepDefaultOutboundWithoutRoutingGroups() {
        val config = config(primary = false)
        CoreConfigManager.prepareSpeedtestRouting(config)
        assertEquals(emptyList<V2rayConfig.RoutingBean.RulesBean>(), config.routing.rules)
        assertNull(config.routing.balancers)
        assertNull(config.observatory)
        assertNull(config.burstObservatory)
    }
}
