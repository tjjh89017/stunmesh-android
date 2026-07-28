package dev.stunmesh.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WgQuickConfTest {

    @Test
    fun parsesTypicalExportedConf() {
        val tunnel = WgQuickConf.decode(
            """
            [Interface]
            PrivateKey = PRIV
            Address = 10.0.0.2/32, fd00::2/128
            DNS = 10.0.0.1
            ListenPort = 51820
            MTU = 1380
            # wg-quick machinery the app replaces:
            Table = off
            PostUp = /etc/wireguard/up.sh

            [Peer]
            PublicKey = PUB1
            PresharedKey = PSK
            AllowedIPs = 10.0.0.0/24
            AllowedIPs = fd00::/64
            Endpoint = 203.0.113.7:51820  # trailing comment
            PersistentKeepalive = 25

            [Peer]
            PublicKey = PUB2
            AllowedIPs = 10.0.1.0/24
            """.trimIndent(),
            name = "office",
        )

        assertEquals("office", tunnel.name)
        assertEquals("PRIV", tunnel.iface.privateKey)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), tunnel.iface.addresses)
        assertEquals(listOf("10.0.0.1"), tunnel.iface.dnsServers)
        assertEquals(51820, tunnel.iface.listenPort)
        assertEquals(1380, tunnel.iface.mtu)

        assertEquals(2, tunnel.peers.size)
        val first = tunnel.peers[0]
        assertEquals("PUB1", first.publicKey)
        assertEquals("PSK", first.presharedKey)
        // Repeated keys accumulate, as wg-quick treats them.
        assertEquals(listOf("10.0.0.0/24", "fd00::/64"), first.allowedIps)
        assertEquals("203.0.113.7:51820", first.endpoint)
        assertEquals(25, first.persistentKeepalive)
        // Imported peers get placeholder names so the list is navigable
        // before the user assigns real ones.
        assertEquals("peer1", first.name)
        assertEquals("peer2", tunnel.peers[1].name)
    }

    @Test
    fun rejectsUnknownKeysInsteadOfDroppingThem() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            WgQuickConf.decode(
                "[Interface]\nPrivateKey = PRIV\n\n[Peer]\nPublicKay = OOPS\n",
                name = "typo",
            )
        }
        assertTrue(e.message!!, "PublicKay" in e.message!!)
    }

    @Test
    fun rejectsConfWithoutPrivateKey() {
        assertThrows(IllegalArgumentException::class.java) {
            WgQuickConf.decode("[Interface]\nAddress = 10.0.0.2/32\n", name = "x")
        }
    }

    @Test
    fun sniffsConfVersusYaml() {
        assertTrue(WgQuickConf.looksLikeConf("  [interface]\nPrivateKey = X"))
        assertFalse(WgQuickConf.looksLikeConf("version: 2\nwireguard:\n  private_key: X"))
    }
}
