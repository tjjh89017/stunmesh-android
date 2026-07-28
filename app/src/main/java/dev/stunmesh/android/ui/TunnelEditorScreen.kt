package dev.stunmesh.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.config.InterfaceConfig
import dev.stunmesh.android.config.PeerConfig
import dev.stunmesh.android.config.PluginDefinition
import dev.stunmesh.android.config.TunnelConfig

/**
 * Tunnel editor: an Interface section, one card per peer with the full WG
 * peer fields, and a STUNMESH section for what a plain WG config does not
 * have (endpoint-exchange plugin, STUN servers, protocols).
 */
@Composable
fun TunnelEditorScreen(
    initial: TunnelConfig,
    onSave: (TunnelConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the tunnel id so switching tunnels resets the form instead of
    // keeping the previously edited tunnel's values.
    val key = initial.id
    var tunnelName by remember(key) { mutableStateOf(initial.name) }
    var privateKey by remember(key) { mutableStateOf(initial.iface.privateKey) }
    var addresses by remember(key) { mutableStateOf(initial.iface.addresses.joinToString(", ")) }
    var listenPort by remember(key) { mutableStateOf(portText(initial.iface.listenPort)) }
    var dnsServers by remember(key) { mutableStateOf(initial.iface.dnsServers.joinToString(", ")) }
    var mtu by remember(key) { mutableStateOf(initial.iface.mtu.toString()) }

    var ifaceProtocol by remember(key) { mutableStateOf(initial.iface.protocol) }
    val plugin = initial.plugins.firstOrNull() ?: PluginDefinition()
    var pluginName by remember(key) { mutableStateOf(plugin.name) }
    var cfZone by remember(key) { mutableStateOf(plugin.config["zone"].orEmpty()) }
    var cfToken by remember(key) { mutableStateOf(plugin.config["token"].orEmpty()) }
    var cfSubdomain by remember(key) { mutableStateOf(plugin.config["subdomain"].orEmpty()) }
    var dhtEndpoint by remember(key) { mutableStateOf(plugin.config["endpoint"].orEmpty()) }
    var stunServers by remember(key) { mutableStateOf(initial.stunServers.joinToString(", ")) }
    var refreshInterval by remember(key) {
        mutableStateOf(initial.refreshIntervalSeconds.toString())
    }

    val peers = remember(key) { initial.peers.map { PeerForm(it) }.toMutableStateList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Interface") {
            FormField(tunnelName, { tunnelName = it }, "Name")
            FormField(privateKey, { privateKey = it }, "Private key", secret = true)
            FormField(addresses, { addresses = it }, "Addresses")
            FormField(listenPort, { listenPort = it }, "Listen port (empty = automatic)")
            FormField(dnsServers, { dnsServers = it }, "DNS servers")
            FormField(mtu, { mtu = it }, "MTU")
        }

        peers.forEachIndexed { index, peer ->
            SectionCard(title = "Peer") {
                FormField(peer.name, { peer.name = it }, "Name")
                FormField(peer.publicKey, { peer.publicKey = it }, "Public key")
                FormField(peer.presharedKey, { peer.presharedKey = it }, "Pre-shared key (optional)", secret = true)
                FormField(peer.endpoint, { peer.endpoint = it }, "Endpoint (optional, STUNMESH overrides)")
                FormField(peer.allowedIps, { peer.allowedIps = it }, "Allowed IPs")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = peer.keepalive,
                        onValueChange = { peer.keepalive = it },
                        label = { Text("Persistent keepalive") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = peer.protocol,
                        onValueChange = { peer.protocol = it },
                        label = { Text("Protocol") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { peers.removeAt(index) }) {
                    Text("Delete peer")
                }
            }
        }
        OutlinedButton(
            onClick = { peers.add(PeerForm(PeerConfig())) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add peer")
        }

        SectionCard(title = "STUNMESH") {
            FormField(ifaceProtocol, { ifaceProtocol = it }, "Discovery protocol (ipv4 / ipv6 / dualstack)")
            FormField(stunServers, { stunServers = it }, "STUN servers (empty = default)")
            FormField(refreshInterval, { refreshInterval = it }, "Refresh interval (seconds)")
        }

        SectionCard(title = "Endpoint exchange plugin") {
            FormField(pluginName, { pluginName = it }, "Plugin (cloudflare / opendht)")
            if (pluginName.trim() == "opendht") {
                FormField(dhtEndpoint, { dhtEndpoint = it }, "DHT proxy endpoint (URL)")
            } else {
                FormField(cfZone, { cfZone = it }, "Zone")
                FormField(cfToken, { cfToken = it }, "API token", secret = true)
                FormField(cfSubdomain, { cfSubdomain = it }, "Subdomain (optional)")
            }
        }

        Button(
            onClick = {
                val chosenPlugin = pluginName.trim().ifEmpty { "cloudflare" }
                val pluginConfig = if (chosenPlugin == "opendht") {
                    mapOf("endpoint" to dhtEndpoint.trim())
                } else {
                    buildMap {
                        put("zone", cfZone.trim())
                        put("token", cfToken.trim())
                        if (cfSubdomain.isNotBlank()) put("subdomain", cfSubdomain.trim())
                    }
                }
                val instance = "${chosenPlugin}_builtin"
                onSave(
                    TunnelConfig(
                        id = initial.id,
                        name = tunnelName.trim().ifEmpty { "stunmesh" },
                        iface = InterfaceConfig(
                            privateKey = privateKey.trim(),
                            addresses = addresses.splitList(),
                            dnsServers = dnsServers.splitList(),
                            listenPort = listenPort.trim().toIntOrNull() ?: 0,
                            mtu = mtu.trim().toIntOrNull() ?: 1420,
                            protocol = ifaceProtocol.trim().ifEmpty { "ipv4" },
                        ),
                        peers = peers.map { it.toPeer(instance) },
                        plugins = listOf(
                            PluginDefinition(
                                instance = instance,
                                type = "builtin",
                                name = chosenPlugin,
                                config = pluginConfig,
                            )
                        ),
                        stunServers = stunServers.splitList(),
                        refreshIntervalSeconds = refreshInterval.trim().toIntOrNull() ?: 600,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

/**
 * Form state for one peer. List-valued config fields are edited as
 * comma-separated text and split on save.
 */
private class PeerForm(peer: PeerConfig) {
    var name by mutableStateOf(peer.name)
    var publicKey by mutableStateOf(peer.publicKey)
    var presharedKey by mutableStateOf(peer.presharedKey)
    var endpoint by mutableStateOf(peer.endpoint)
    var allowedIps by mutableStateOf(peer.allowedIps.joinToString(", "))
    var protocol by mutableStateOf(peer.protocol)
    var keepalive by mutableStateOf(peer.persistentKeepalive.toString())

    fun toPeer(pluginInstance: String): PeerConfig = PeerConfig(
        name = name.trim(),
        publicKey = publicKey.trim(),
        presharedKey = presharedKey.trim(),
        allowedIps = allowedIps.splitList(),
        endpoint = endpoint.trim(),
        plugin = pluginInstance,
        protocol = protocol.trim().ifEmpty { "ipv4" },
        persistentKeepalive = keepalive.trim().toIntOrNull() ?: 25,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun portText(port: Int): String = if (port == 0) "" else port.toString()

private fun String.splitList(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }
