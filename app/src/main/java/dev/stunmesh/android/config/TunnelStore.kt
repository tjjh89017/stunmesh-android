package dev.stunmesh.android.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * All stored tunnels plus which one the VPN service brings up. Android
 * permits one active VPN, so only [activeId] runs at a time; turning on
 * another tunnel replaces it.
 */
data class TunnelStore(
    val tunnels: List<TunnelConfig> = emptyList(),
    val activeId: String = "",
) {
    val active: TunnelConfig?
        get() = tunnels.firstOrNull { it.id == activeId }

    fun upsert(tunnel: TunnelConfig): TunnelStore {
        val index = tunnels.indexOfFirst { it.id == tunnel.id }
        val updated = if (index >= 0) {
            tunnels.toMutableList().apply { set(index, tunnel) }
        } else {
            tunnels + tunnel
        }
        return copy(tunnels = updated)
    }

    fun remove(id: String): TunnelStore = copy(
        tunnels = tunnels.filterNot { it.id == id },
        activeId = if (activeId == id) "" else activeId,
    )

    fun toJson(): String {
        val o = JSONObject()
        o.put("tunnels", JSONArray().apply { tunnels.forEach { put(it.toJsonObject()) } })
        o.put("active_id", activeId)
        return o.toString(2)
    }

    companion object {
        fun fromJson(json: String): TunnelStore {
            val o = JSONObject(json)
            // A store written before multi-tunnel support holds a single
            // tunnel at the top level; adopt it as the only entry.
            val array = o.optJSONArray("tunnels")
                ?: return TunnelConfig.fromJsonObject(o).let {
                    TunnelStore(listOf(it), it.id)
                }
            val tunnels = (0 until array.length()).map {
                TunnelConfig.fromJsonObject(array.getJSONObject(it))
            }
            val activeId = o.optString("active_id")
            return TunnelStore(
                tunnels = tunnels,
                activeId = if (tunnels.any { it.id == activeId }) activeId else "",
            )
        }
    }
}
