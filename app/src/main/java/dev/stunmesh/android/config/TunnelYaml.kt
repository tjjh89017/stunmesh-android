package dev.stunmesh.android.config

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * YAML form of a tunnel, for backing a config up, editing it on a desktop, or
 * moving it between devices.
 *
 * The document carries a `schema` field (absent means 1) and splits into two
 * top-level sections joined per peer by `public_key`:
 *
 *  - `wireguard`: the plain WireGuard device — wg-quick vocabulary, nothing
 *    STUNMESH-specific. Strip the rest of the file and this section still
 *    describes a working WG config.
 *  - `stunmesh`: the overlay, mirroring the desktop stunmesh-go config.yaml
 *    vocabulary (`plugins` entries with type/name, `stun.addresses`, peer
 *    `plugin`/`protocol`).
 *
 * An exported file contains the interface private key and any plugin API
 * token in plain text: treat it as a secret.
 */
object TunnelYaml {

    const val SCHEMA = 1

    fun encode(tunnel: TunnelConfig): String {
        val root = linkedMapOf<String, Any?>(
            "schema" to SCHEMA,
            "name" to tunnel.name,
            "wireguard" to linkedMapOf(
                "private_key" to tunnel.iface.privateKey,
                "addresses" to tunnel.iface.addresses,
                "dns_servers" to tunnel.iface.dnsServers,
                "listen_port" to tunnel.iface.listenPort,
                "mtu" to tunnel.iface.mtu,
                "peers" to tunnel.peers.map { peer ->
                    linkedMapOf(
                        "public_key" to peer.publicKey,
                        "preshared_key" to peer.presharedKey,
                        "allowed_ips" to peer.allowedIps,
                        "endpoint" to peer.endpoint,
                        "persistent_keepalive" to peer.persistentKeepalive,
                    )
                },
            ),
            "stunmesh" to linkedMapOf(
                "protocol" to tunnel.iface.protocol,
                "stun" to linkedMapOf("addresses" to tunnel.stunServers),
                "plugins" to tunnel.plugins.map { plugin ->
                    linkedMapOf(
                        "instance" to plugin.instance,
                        "type" to plugin.type,
                        "name" to plugin.name,
                        "config" to LinkedHashMap(plugin.config),
                    )
                },
                "refresh_interval_seconds" to tunnel.refreshIntervalSeconds,
                "log" to linkedMapOf("level" to tunnel.logLevel),
                "peers" to tunnel.peers.map { peer ->
                    linkedMapOf(
                        "public_key" to peer.publicKey,
                        "name" to peer.name,
                        "description" to peer.description,
                        "plugin" to peer.plugin,
                        "protocol" to peer.protocol,
                    )
                },
            ),
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

        return when (val version = root.int("schema", 1)) {
            1 -> decodeV1(root)
            else -> throw IllegalArgumentException(
                "config schema $version is newer than this app understands (up to $SCHEMA)"
            )
        }
    }

    private fun decodeV1(root: Map<*, *>): TunnelConfig {
        val wg = root.map("wireguard")
        val privateKey = wg.string("private_key")
        require(privateKey.isNotEmpty()) { "wireguard.private_key is required" }
        val sm = root.map("stunmesh")

        // The stunmesh section is an overlay: entries attach to a WireGuard
        // peer by public key, entries pointing at no WG peer are dropped.
        val overlays = sm.mapList("peers").associateBy { it.string("public_key") }

        return TunnelConfig(
            name = root.string("name").ifEmpty { "imported" },
            iface = InterfaceConfig(
                privateKey = privateKey,
                addresses = wg.stringList("addresses"),
                dnsServers = wg.stringList("dns_servers"),
                listenPort = wg.int("listen_port", 0),
                mtu = wg.int("mtu", 1420),
                protocol = sm.string("protocol").ifEmpty { "ipv4" },
            ),
            peers = wg.mapList("peers").map { peer ->
                val publicKey = peer.string("public_key")
                val overlay = overlays[publicKey] ?: emptyMap<Any, Any>()
                PeerConfig(
                    name = overlay.string("name"),
                    description = overlay.string("description"),
                    publicKey = publicKey,
                    presharedKey = peer.string("preshared_key"),
                    allowedIps = peer.stringList("allowed_ips"),
                    endpoint = peer.string("endpoint"),
                    plugin = overlay.string("plugin"),
                    protocol = overlay.string("protocol").ifEmpty { "ipv4" },
                    persistentKeepalive = peer.int("persistent_keepalive", 25),
                )
            },
            plugins = sm.mapList("plugins").map { decodePlugin(it) },
            stunServers = sm.map("stun").stringList("addresses"),
            refreshIntervalSeconds = sm.int("refresh_interval_seconds", 600),
            logLevel = sm.map("log").string("level").ifEmpty { "info" },
        )
    }

    private fun decodePlugin(plugin: Map<*, *>): PluginDefinition = PluginDefinition(
        instance = plugin.string("instance").ifEmpty { "cloudflare_builtin" },
        type = plugin.string("type").ifEmpty { "builtin" },
        name = plugin.string("name").ifEmpty { "cloudflare" },
        config = plugin.map("config")
            .entries
            .associate { (k, v) ->
                k.toString() to when (v) {
                    is List<*> -> v.map { scalar(it) }
                    else -> scalar(v)
                }
            },
    )

    private fun yaml(): Yaml {
        // Block style, but without pretty flow: it spreads an empty list over
        // two lines ("[\n]") instead of writing "[]".
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
        }
        return Yaml(options)
    }
}

internal fun Map<*, *>.map(key: String): Map<*, *> = this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

internal fun Map<*, *>.mapList(key: String): List<Map<*, *>> =
    (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

internal fun Map<*, *>.string(key: String): String = scalar(this[key])

internal fun Map<*, *>.stringList(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().map { scalar(it) }.filter { it.isNotEmpty() }

internal fun Map<*, *>.int(key: String, fallback: Int): Int = when (val v = this[key]) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull() ?: fallback
    else -> fallback
}

/** YAML scalars arrive as Int/Boolean/String; config fields are all text. */
internal fun scalar(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    else -> value.toString()
}
