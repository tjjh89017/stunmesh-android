package dev.stunmesh.android.config

/**
 * Parses a standard wg-quick `.conf` (the `[Interface]`/`[Peer]` INI format
 * every WireGuard tool exports) into a [TunnelConfig], so a user coming from
 * an existing WireGuard setup only has to add the STUNMESH overlay — pick a
 * plugin, name the peers — instead of retyping the whole device.
 *
 * Keys wg-quick owns but the app has no use for (`Table`, `PostUp`,
 * `SaveConfig`, ...) are skipped; keys nothing recognizes are an error, since
 * a typo like `PublicKay` silently dropped would produce a peer that never
 * handshakes.
 */
object WgQuickConf {

    /** A `.conf` is recognized by its `[Interface]` section header. */
    fun looksLikeConf(text: String): Boolean =
        text.lineSequence().any { it.trim().equals("[Interface]", ignoreCase = true) }

    fun decode(text: String, name: String): TunnelConfig {
        var iface = InterfaceConfig()
        var sawInterface = false
        val peers = mutableListOf<PeerConfig>()
        // Which config object the key/value lines currently apply to; null
        // until the first section header.
        var inPeer = false

        for ((index, rawLine) in text.lineSequence().withIndex()) {
            // wg-quick strips comments anywhere in the line, not only at the start.
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue

            when {
                line.equals("[Interface]", ignoreCase = true) -> {
                    require(!sawInterface) { "line ${index + 1}: duplicate [Interface] section" }
                    sawInterface = true
                    inPeer = false
                }
                line.equals("[Peer]", ignoreCase = true) -> {
                    peers.add(PeerConfig(name = "peer${peers.size + 1}"))
                    inPeer = true
                }
                else -> {
                    val key = line.substringBefore('=', "").trim()
                    val value = line.substringAfter('=', "").trim()
                    require(key.isNotEmpty() && '=' in line) {
                        "line ${index + 1}: expected key = value, got \"$line\""
                    }
                    if (inPeer) {
                        peers[peers.lastIndex] = peerKey(peers.last(), key, value, index + 1)
                    } else {
                        require(sawInterface) {
                            "line ${index + 1}: \"$key\" before any [Interface] section"
                        }
                        iface = interfaceKey(iface, key, value, index + 1)
                    }
                }
            }
        }

        require(sawInterface) { "no [Interface] section — not a wg-quick config" }
        require(iface.privateKey.isNotEmpty()) { "[Interface] has no PrivateKey" }
        return TunnelConfig(name = name, iface = iface, peers = peers)
    }

    private fun interfaceKey(
        iface: InterfaceConfig,
        key: String,
        value: String,
        line: Int,
    ): InterfaceConfig = when (key.lowercase()) {
        "privatekey" -> iface.copy(privateKey = value)
        "address" -> iface.copy(addresses = iface.addresses + splitList(value))
        "dns" -> iface.copy(dnsServers = iface.dnsServers + splitList(value))
        "listenport" -> iface.copy(listenPort = intValue(key, value, line))
        "mtu" -> iface.copy(mtu = intValue(key, value, line))
        // wg-quick's own scripting/routing machinery; the app does that itself.
        "table", "saveconfig", "fwmark",
        "preup", "postup", "predown", "postdown" -> iface
        else -> throw IllegalArgumentException("line $line: unknown [Interface] key \"$key\"")
    }

    private fun peerKey(
        peer: PeerConfig,
        key: String,
        value: String,
        line: Int,
    ): PeerConfig = when (key.lowercase()) {
        "publickey" -> peer.copy(publicKey = value)
        "presharedkey" -> peer.copy(presharedKey = value)
        "allowedips" -> peer.copy(allowedIps = peer.allowedIps + splitList(value))
        "endpoint" -> peer.copy(endpoint = value)
        "persistentkeepalive" -> peer.copy(persistentKeepalive = intValue(key, value, line))
        else -> throw IllegalArgumentException("line $line: unknown [Peer] key \"$key\"")
    }

    private fun splitList(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun intValue(key: String, value: String, line: Int): Int =
        value.toIntOrNull()
            ?: throw IllegalArgumentException("line $line: $key wants a number, got \"$value\"")
}
