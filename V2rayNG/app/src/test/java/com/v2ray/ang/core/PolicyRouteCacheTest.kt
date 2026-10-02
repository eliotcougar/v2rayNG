package com.v2ray.ang.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyRouteCacheTest {
    @After
    fun clear() = PolicyRouteCache.clear()

    @Test
    fun delayedIdentityCannotUpdateAnotherNetworkOrNewServiceSession() {
        PolicyRouteCache.setCurrentNetwork("wifi:A", 1L)
        val departing = PolicyRouteCache.snapshot()
        PolicyRouteCache.setCurrentNetwork("wifi:B", 2L)
        assertFalse(PolicyRouteCache.isCurrent(departing))
        PolicyRouteCache.setCurrentNetwork("wifi:A", 3L)
        assertFalse(PolicyRouteCache.isCurrent(departing))
        val returning = PolicyRouteCache.snapshot()
        assertTrue(PolicyRouteCache.isCurrent(returning))
        PolicyRouteCache.clear()
        PolicyRouteCache.setCurrentNetwork("wifi:A", 3L)
        assertFalse(PolicyRouteCache.isCurrent(returning))
    }

    @Test
    fun refinedIdentitySupersedesAnEarlierEventOnTheSameHandle() {
        PolicyRouteCache.setCurrentNetwork("network:1", 1L)
        val unresolved = PolicyRouteCache.snapshot()
        PolicyRouteCache.setCurrentNetwork("wifi:A", 1L)
        assertFalse(PolicyRouteCache.isCurrent(unresolved))
        assertTrue(PolicyRouteCache.isCurrent(PolicyRouteCache.snapshot()))
    }
}
