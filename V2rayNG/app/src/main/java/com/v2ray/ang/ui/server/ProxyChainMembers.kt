package com.v2ray.ang.ui.server

internal data class ProxyChainMember(val id: Long, val remark: String) : java.io.Serializable

/** Resolve the stable row ID at confirmation so reordering and duplicate names cannot change the target. */
internal fun withoutProxyChainMember(members: List<ProxyChainMember>, memberId: Long): List<ProxyChainMember> {
    if (members.none { it.id == memberId }) return members
    return members.filterNot { it.id == memberId }
}
