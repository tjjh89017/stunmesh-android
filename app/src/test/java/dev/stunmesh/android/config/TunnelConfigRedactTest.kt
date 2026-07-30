package dev.stunmesh.android.config

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelConfigRedactTest {

    private val tunnel = TunnelConfig(
        name = "home",
        iface = InterfaceConfig(privateKey = "PRIVKEY", addresses = listOf("10.0.0.2/32")),
        peers = listOf(
            PeerConfig(publicKey = "PUBKEY1", presharedKey = "PSK1"),
            PeerConfig(publicKey = "PUBKEY2", presharedKey = ""),
        ),
        plugins = listOf(
            PluginDefinition(
                config = mapOf(
                    "zone" to "example.com",
                    "token" to "cf-api-token",
                    "subdomain" to "wg",
                ),
            ),
        ),
    )

    @Test
    fun `replaces private key, preshared keys and plugin tokens`() {
        val redacted = tunnel.redactSecrets()

        assertEquals(TunnelConfig.REDACTED, redacted.iface.privateKey)
        assertEquals(TunnelConfig.REDACTED, redacted.peers[0].presharedKey)
        assertEquals(TunnelConfig.REDACTED, redacted.plugins[0].config["token"])
    }

    @Test
    fun `keeps non-secret fields and empty secrets`() {
        val redacted = tunnel.redactSecrets()

        // An empty preshared key stays empty: the reader should still see
        // that no PSK was configured, rather than a redacted-looking one.
        assertEquals("", redacted.peers[1].presharedKey)
        assertEquals("PUBKEY1", redacted.peers[0].publicKey)
        assertEquals("example.com", redacted.plugins[0].config["zone"])
        assertEquals("wg", redacted.plugins[0].config["subdomain"])
        assertEquals(listOf("10.0.0.2/32"), redacted.iface.addresses)
    }

    @Test
    fun `redacted config still encodes to yaml without secrets`() {
        val yaml = TunnelYaml.encode(tunnel.redactSecrets())

        assertEquals(false, yaml.contains("PRIVKEY"))
        assertEquals(false, yaml.contains("PSK1"))
        assertEquals(false, yaml.contains("cf-api-token"))
    }
}
