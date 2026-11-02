package com.v2ray.ang.ui.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProxyChainMembersTest {
    @Test
    fun movingResolvesCurrentKeysAndKeepsDuplicateNamesDistinct() {
        val members = listOf(ProxyChainMember(1, "Same"), ProxyChainMember(2, "Other"), ProxyChainMember(3, "Same"))
        val moved = moveProxyChainMember(members, 3, 1)!!
        assertEquals(listOf("Same", "Same", "Other"), moved.map { it.remark })
        assertEquals(listOf(3L, 1L, 2L), moved.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), members.map { it.id })

        assertEquals(members, moveProxyChainMember(moved, 3, 2))
    }

    @Test
    fun missingKeysEmptyChainsAndUnchangedPositionsRejectMoves() {
        val members = listOf(ProxyChainMember(1, "One"), ProxyChainMember(2, "Two"))
        assertNull(moveProxyChainMember(members, 99, 2))
        assertNull(moveProxyChainMember(members, 1, 99))
        assertNull(moveProxyChainMember(members, 1, 1))
        assertNull(moveProxyChainMember(emptyList(), 1, 2))
    }

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
