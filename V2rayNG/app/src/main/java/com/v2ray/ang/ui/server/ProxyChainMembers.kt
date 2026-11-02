package com.v2ray.ang.ui.server

import com.v2ray.ang.extension.moveItem

internal data class ProxyChainMember(val id: Long, val remark: String) : java.io.Serializable

/** Resolve the stable row ID at confirmation so reordering and duplicate names cannot change the target. */
internal fun withoutProxyChainMember(members: List<ProxyChainMember>, memberId: Long): List<ProxyChainMember> {
    if (members.none { it.id == memberId }) return members
    return members.filterNot { it.id == memberId }
}

/** Drag and accessibility moves resolve the current positions from stable draft IDs. */
internal fun moveProxyChainMember(
    members: List<ProxyChainMember>,
    fromId: Long,
    toId: Long,
): List<ProxyChainMember>? {
    val fromIndex = members.indexOfFirst { it.id == fromId }
    val toIndex = members.indexOfFirst { it.id == toId }
    return members.toMutableList().takeIf { it.moveItem(fromIndex, toIndex) }
}
