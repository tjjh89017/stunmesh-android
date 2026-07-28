package dev.stunmesh.android.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tunnel configuration. Serialized to JSON with [toJson] — the same string
 * that crosses the gomobile boundary into the Go core (gomobile cannot pass
 * maps or arbitrary slices, so complex data travels as JSON).
 *
 * Field names follow the stunmesh-go YAML config (`internal/config`): peers
 * carry `public_key`/`plugin`/`protocol`, plugin definitions carry `type`
 * plus plugin-specific keys (cloudflare: `zone`, `token`, `subdomain`).
 * Interface-level WG fields (private key, addresses, DNS, MTU, allowed IPs,
 * keepalive) are Android additions: the desktop daemon attaches to an
 * existing WG interface, but on Android the app owns the whole device.
 */
data class TunnelConfig(
    /**
     * Stable identity, kept across renames so the active-tunnel pointer and
     * any references survive editing. Generated when the tunnel is created.
     */
    val id: String = java.util.UUID.randomUUID().toString(),
    /** Tunnel display name. */
    val name: String = "stunmesh",
    val iface: InterfaceConfig = InterfaceConfig(),
    val peers: List<PeerConfig> = emptyList(),
    val plugins: List<PluginDefinition> = emptyList(),
    val stunServers: List<String> = emptyList(),
    val refreshIntervalSeconds: Int = 600,
    val logLevel: String = "info",
) {
    fun toJson(): String = toJsonObject().toString(2)

    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("interface", iface.toJson())
        o.put("peers", JSONArray().apply { peers.forEach { put(it.toJson()) } })
        o.put("plugins", JSONArray().apply { plugins.forEach { put(it.toJson()) } })
        o.put("stun", JSONObject().put("addresses", JSONArray(stunServers)))
        o.put("refresh_interval_seconds", refreshIntervalSeconds)
        o.put("log", JSONObject().put("level", logLevel))
        return o
    }

    companion object {
        fun fromJson(json: String): TunnelConfig = fromJsonObject(JSONObject(json))

        fun fromJsonObject(o: JSONObject): TunnelConfig {
            val peersArray = o.optJSONArray("peers") ?: JSONArray()
            val pluginsArray = o.optJSONArray("plugins") ?: JSONArray()
            return TunnelConfig(
                // A config written before tunnels had ids gets one now.
                id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                name = o.optString("name", "stunmesh"),
                iface = InterfaceConfig.fromJson(o.optJSONObject("interface") ?: JSONObject()),
                peers = (0 until peersArray.length()).map {
                    PeerConfig.fromJson(peersArray.getJSONObject(it))
                },
                plugins = (0 until pluginsArray.length()).map {
                    PluginDefinition.fromJson(pluginsArray.getJSONObject(it))
                },
                stunServers = (o.optJSONObject("stun")?.optJSONArray("addresses")).toStringList(),
                refreshIntervalSeconds = o.optInt("refresh_interval_seconds", 600),
                logLevel = o.optJSONObject("log")?.optString("level", "info") ?: "info",
            )
        }
    }
}

data class InterfaceConfig(
    /** Base64 WireGuard private key. */
    val privateKey: String = "",
    /** Tunnel addresses in CIDR form, e.g. "10.0.0.2/32". */
    val addresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    /** 0 lets the core pick an ephemeral port. */
    val listenPort: Int = 0,
    val mtu: Int = 1420,
    /** STUN discovery protocol: "ipv4", "ipv6" or "dualstack". */
    val protocol: String = "ipv4",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("private_key", privateKey)
        put("addresses", JSONArray(addresses))
        put("dns_servers", JSONArray(dnsServers))
        put("listen_port", listenPort)
        put("mtu", mtu)
        put("protocol", protocol)
    }

    companion object {
        fun fromJson(o: JSONObject): InterfaceConfig = InterfaceConfig(
            privateKey = o.optString("private_key"),
            addresses = o.optJSONArray("addresses").toStringList(),
            dnsServers = o.optJSONArray("dns_servers").toStringList(),
            listenPort = o.optInt("listen_port", 0),
            mtu = o.optInt("mtu", 1420),
            protocol = o.optString("protocol", "ipv4"),
        )
    }
}

data class PeerConfig(
    /** Peer name — the map key in the desktop YAML. */
    val name: String = "",
    val description: String = "",
    /** Base64 WireGuard public key. */
    val publicKey: String = "",
    /** Base64 WireGuard pre-shared key; empty means none. */
    val presharedKey: String = "",
    /** Allowed IPs in CIDR form; also installed as tunnel routes. */
    val allowedIps: List<String> = emptyList(),
    /**
     * Optional static endpoint "host:port", as in a plain WG config. Usually
     * empty — STUNMESH discovers and sets endpoints at run time; a static
     * value only serves as the initial endpoint before discovery.
     */
    val endpoint: String = "",
    /** Name of the plugin instance used for endpoint exchange. */
    val plugin: String = "",
    /** Endpoint selection: "ipv4", "ipv6", "prefer_ipv4" or "prefer_ipv6". */
    val protocol: String = "ipv4",
    val persistentKeepalive: Int = 25,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("public_key", publicKey)
        put("preshared_key", presharedKey)
        put("allowed_ips", JSONArray(allowedIps))
        put("endpoint", endpoint)
        put("plugin", plugin)
        put("protocol", protocol)
        put("persistent_keepalive", persistentKeepalive)
    }

    companion object {
        fun fromJson(o: JSONObject): PeerConfig = PeerConfig(
            name = o.optString("name"),
            description = o.optString("description"),
            publicKey = o.optString("public_key"),
            presharedKey = o.optString("preshared_key"),
            allowedIps = o.optJSONArray("allowed_ips").toStringList(),
            endpoint = o.optString("endpoint"),
            plugin = o.optString("plugin"),
            protocol = o.optString("protocol", "ipv4"),
            persistentKeepalive = o.optInt("persistent_keepalive", 25),
        )
    }
}

/**
 * A named endpoint-exchange plugin instance. v1 supports built-in plugins
 * only (`exec`/`shell` need external processes and stay desktop-only).
 * Mirrors `pluginapi.PluginDefinition`: `type` plus free-form config keys.
 */
data class PluginDefinition(
    /** Instance name peers reference, e.g. "cloudflare_builtin". */
    val instance: String = "cloudflare_builtin",
    /** Plugin type; only "builtin" is valid on Android. */
    val type: String = "builtin",
    /** Built-in plugin name, e.g. "cloudflare". */
    val name: String = "cloudflare",
    /** Plugin-specific keys. Cloudflare: `zone`, `token`, optional `subdomain`. */
    val config: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("instance", instance)
        put("type", type)
        put("name", name)
        put("config", JSONObject(config as Map<*, *>))
    }

    companion object {
        fun fromJson(o: JSONObject): PluginDefinition {
            val configObj = o.optJSONObject("config") ?: JSONObject()
            return PluginDefinition(
                instance = o.optString("instance", "cloudflare_builtin"),
                type = o.optString("type", "builtin"),
                name = o.optString("name", "cloudflare"),
                config = configObj.keys().asSequence().associateWith { configObj.getString(it) },
            )
        }
    }
}

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }
