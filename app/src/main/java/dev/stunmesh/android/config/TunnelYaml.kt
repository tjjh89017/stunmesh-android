package dev.stunmesh.android.config

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * YAML form of a tunnel, for backing a config up, editing it on a desktop, or
 * moving it between devices. Keys follow the stunmesh-go config vocabulary
 * (`plugins` entries with type/name, `stun.addresses`, peer `protocol`), with
 * the WireGuard interface fields Android needs since the app owns the device
 * rather than attaching to an existing one.
 *
 * An exported file contains the interface private key and any plugin API
 * token in plain text: treat it as a secret.
 */
object TunnelYaml {

    fun encode(tunnel: TunnelConfig): String {
        val root = linkedMapOf<String, Any?>(
            "name" to tunnel.name,
            "interface" to linkedMapOf(
                "private_key" to tunnel.iface.privateKey,
                "addresses" to tunnel.iface.addresses,
                "dns_servers" to tunnel.iface.dnsServers,
                "listen_port" to tunnel.iface.listenPort,
                "mtu" to tunnel.iface.mtu,
                "protocol" to tunnel.iface.protocol,
            ),
            "peers" to tunnel.peers.map { peer ->
                linkedMapOf(
                    "name" to peer.name,
                    "description" to peer.description,
                    "public_key" to peer.publicKey,
                    "preshared_key" to peer.presharedKey,
                    "allowed_ips" to peer.allowedIps,
                    "endpoint" to peer.endpoint,
                    "plugin" to peer.plugin,
                    "protocol" to peer.protocol,
                    "persistent_keepalive" to peer.persistentKeepalive,
                )
            },
            "plugins" to tunnel.plugins.map { plugin ->
                linkedMapOf(
                    "instance" to plugin.instance,
                    "type" to plugin.type,
                    "name" to plugin.name,
                    "config" to LinkedHashMap(plugin.config),
                )
            },
            "stun" to linkedMapOf("addresses" to tunnel.stunServers),
            "refresh_interval_seconds" to tunnel.refreshIntervalSeconds,
            "log" to linkedMapOf("level" to tunnel.logLevel),
        )
        return yaml().dump(root)
    }

    /**
     * Parses [text]. The tunnel gets a fresh id, so importing never
     * overwrites an existing tunnel; the caller decides how to merge.
     * Throws [IllegalArgumentException] when the document is not a tunnel.
     */
    fun decode(text: String): TunnelConfig {
        val root = runCatching { yaml().load<Any?>(text) }
            .getOrElse { throw IllegalArgumentException("not valid YAML: ${it.message}") }
        require(root is Map<*, *>) { "expected a YAML mapping at the top level" }

        val iface = root.map("interface")
        val privateKey = iface.string("private_key")
        require(privateKey.isNotEmpty()) { "interface.private_key is required" }

        return TunnelConfig(
            name = root.string("name").ifEmpty { "imported" },
            iface = InterfaceConfig(
                privateKey = privateKey,
                addresses = iface.stringList("addresses"),
                dnsServers = iface.stringList("dns_servers"),
                listenPort = iface.int("listen_port", 0),
                mtu = iface.int("mtu", 1420),
                protocol = iface.string("protocol").ifEmpty { "ipv4" },
            ),
            peers = root.mapList("peers").map { peer ->
                PeerConfig(
                    name = peer.string("name"),
                    description = peer.string("description"),
                    publicKey = peer.string("public_key"),
                    presharedKey = peer.string("preshared_key"),
                    allowedIps = peer.stringList("allowed_ips"),
                    endpoint = peer.string("endpoint"),
                    plugin = peer.string("plugin"),
                    protocol = peer.string("protocol").ifEmpty { "ipv4" },
                    persistentKeepalive = peer.int("persistent_keepalive", 25),
                )
            },
            plugins = root.mapList("plugins").map { plugin ->
                PluginDefinition(
                    instance = plugin.string("instance").ifEmpty { "cloudflare_builtin" },
                    type = plugin.string("type").ifEmpty { "builtin" },
                    name = plugin.string("name").ifEmpty { "cloudflare" },
                    config = plugin.map("config")
                        .entries
                        .associate { (k, v) -> k.toString() to scalar(v) },
                )
            },
            stunServers = root.map("stun").stringList("addresses"),
            refreshIntervalSeconds = root.int("refresh_interval_seconds", 600),
            logLevel = root.map("log").string("level").ifEmpty { "info" },
        )
    }

    private fun yaml(): Yaml {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        return Yaml(options)
    }
}

private fun Map<*, *>.map(key: String): Map<*, *> = this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

private fun Map<*, *>.mapList(key: String): List<Map<*, *>> =
    (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

private fun Map<*, *>.string(key: String): String = scalar(this[key])

private fun Map<*, *>.stringList(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().map { scalar(it) }.filter { it.isNotEmpty() }

private fun Map<*, *>.int(key: String, fallback: Int): Int = when (val v = this[key]) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull() ?: fallback
    else -> fallback
}

/** YAML scalars arrive as Int/Boolean/String; config fields are all text. */
private fun scalar(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    else -> value.toString()
}
