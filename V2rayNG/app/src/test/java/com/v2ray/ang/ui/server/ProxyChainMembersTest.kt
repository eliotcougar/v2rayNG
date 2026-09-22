package com.v2ray.ang.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyChainMembersTest {
    @Test
    fun removalFollowsThePendingKeyAfterReordering() {
        val members = listOf(ProxyChainMember(2, "Two"), ProxyChainMember(1, "One"), ProxyChainMember(3, "Three"))
        val remaining = withoutProxyChainMember(members, 2)

        assertEquals(listOf(ProxyChainMember(1, "One"), ProxyChainMember(3, "Three")), remaining)
        assertEquals(listOf(2L, 1L, 3L), members.map { it.id })
    }

    @Test
    fun duplicateNamesDoNotChangeWhichMemberIsRemoved() {
        val members = listOf(ProxyChainMember(1, "Same"), ProxyChainMember(2, "Same"), ProxyChainMember(3, "Other"))
        assertEquals(listOf(members[0], members[2]), withoutProxyChainMember(members, 2))
    }

    @Test
    fun missingMemberLeavesTheListUnchanged() {
        val members = listOf(ProxyChainMember(1, "One"), ProxyChainMember(3, "Three"))
        assertSame(members, withoutProxyChainMember(members, 2))
    }

    @Test
    fun emptyChainRemainsEmpty() {
        assertEquals(emptyList<ProxyChainMember>(), withoutProxyChainMember(emptyList(), 1))
    }

    @Test
    fun blankMemberIsRemovedWithItsOwnKey() {
        val members = listOf(ProxyChainMember(1, "One"), ProxyChainMember(2, ""))
        assertEquals(listOf(members[0]), withoutProxyChainMember(members, 2))
    }
}
