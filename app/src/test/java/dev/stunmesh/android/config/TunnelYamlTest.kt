package dev.stunmesh.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelYamlTest {

    private val tunnel = TunnelConfig(
        name = "home",
        iface = InterfaceConfig(
            privateKey = "PRIV",
            addresses = listOf("10.0.0.2/32"),
            dnsServers = listOf("10.0.0.1"),
            listenPort = 51820,
            mtu = 1380,
            protocol = "dualstack",
        ),
        peers = listOf(
            PeerConfig(
                name = "gateway",
                description = "home router",
                publicKey = "PUB1",
                presharedKey = "PSK",
                allowedIps = listOf("10.0.0.0/24"),
                endpoint = "203.0.113.7:51820",
                plugin = "cf",
                protocol = "prefer_ipv6",
                persistentKeepalive = 20,
            ),
        ),
        plugins = listOf(
            PluginDefinition(
                instance = "cf",
                type = "builtin",
                name = "cloudflare",
                config = mapOf("zone" to "example.com", "token" to "T"),
            ),
        ),
        stunServers = listOf("stun.l.google.com:19302"),
        refreshIntervalSeconds = 300,
        logLevel = "debug",
    )

    @Test
    fun roundTripKeepsEveryField() {
        val decoded = TunnelYaml.decode(TunnelYaml.encode(tunnel))
        // The id is deliberately regenerated on import; everything else survives.
        assertEquals(tunnel.copy(id = decoded.id), decoded)
    }

    @Test
    fun encodeWritesVersionedSplitDocument() {
        val text = TunnelYaml.encode(tunnel)
        assertTrue(text, text.contains("schema: 1"))
        assertTrue(text, text.contains("wireguard:"))
        assertTrue(text, text.contains("stunmesh:"))
        // The WireGuard section must stay free of overlay vocabulary.
        val wgSection = text.substringAfter("wireguard:").substringBefore("stunmesh:")
        assertTrue(wgSection, "plugin" !in wgSection)
        assertTrue(wgSection, "stun" !in wgSection)
    }

    @Test
    fun stunmeshOverlayJoinsByPublicKey() {
        val decoded = TunnelYaml.decode(
            """
            schema: 1
            name: joined
            wireguard:
              private_key: PRIV
              peers:
                - public_key: PUB1
                  allowed_ips: [10.0.0.0/24]
                - public_key: PUB2
            stunmesh:
              peers:
                - public_key: PUB2
                  name: second
                  plugin: cf
                - public_key: GHOST
                  name: ignored
            """.trimIndent()
        )
        assertEquals(2, decoded.peers.size)
        // PUB1 has no overlay entry: WG fields survive, overlay fields default.
        assertEquals("", decoded.peers[0].name)
        assertEquals(listOf("10.0.0.0/24"), decoded.peers[0].allowedIps)
        // PUB2's overlay attaches; the entry for a nonexistent peer is dropped.
        assertEquals("second", decoded.peers[1].name)
        assertEquals("cf", decoded.peers[1].plugin)
    }

    @Test
    fun missingSchemaMeansCurrent() {
        val decoded = TunnelYaml.decode("wireguard:\n  private_key: PRIV\n")
        assertEquals("PRIV", decoded.iface.privateKey)
    }

    @Test
    fun rejectsSchemasFromTheFuture() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            TunnelYaml.decode("schema: 2\nwireguard:\n  private_key: PRIV\n")
        }
        assertTrue(e.message!!, "schema 2" in e.message!!)
    }

    @Test
    fun rejectsDocumentWithoutPrivateKey() {
        assertThrows(IllegalArgumentException::class.java) {
            TunnelYaml.decode("schema: 1\nname: nokey\n")
        }
    }
}
