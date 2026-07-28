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
 * Form state for one peer. List-valued config fields are edited as
 * comma-separated text and split on save.
 */
private class PeerForm(peer: PeerConfig) {
    var name by mutableStateOf(peer.name)
    var publicKey by mutableStateOf(peer.publicKey)
    var allowedIps by mutableStateOf(peer.allowedIps.joinToString(", "))
    var protocol by mutableStateOf(peer.protocol)
    var keepalive by mutableStateOf(peer.persistentKeepalive.toString())

    fun toPeer(pluginInstance: String): PeerConfig = PeerConfig(
        name = name.trim(),
        publicKey = publicKey.trim(),
        allowedIps = allowedIps.splitList(),
        plugin = pluginInstance,
        protocol = protocol.trim().ifEmpty { "ipv4" },
        persistentKeepalive = keepalive.trim().toIntOrNull() ?: 25,
    )
}

@Composable
fun SettingsScreen(
    initial: TunnelConfig,
    onSave: (TunnelConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var privateKey by remember { mutableStateOf(initial.iface.privateKey) }
    var addresses by remember { mutableStateOf(initial.iface.addresses.joinToString(", ")) }
    var dnsServers by remember { mutableStateOf(initial.iface.dnsServers.joinToString(", ")) }
    var listenPort by remember { mutableStateOf(initial.iface.listenPort.toString()) }
    var mtu by remember { mutableStateOf(initial.iface.mtu.toString()) }
    var ifaceProtocol by remember { mutableStateOf(initial.iface.protocol) }

    val cloudflare = initial.plugins.firstOrNull() ?: PluginDefinition()
    var cfZone by remember { mutableStateOf(cloudflare.config["zone"].orEmpty()) }
    var cfToken by remember { mutableStateOf(cloudflare.config["token"].orEmpty()) }
    var cfSubdomain by remember { mutableStateOf(cloudflare.config["subdomain"].orEmpty()) }

    var stunServers by remember { mutableStateOf(initial.stunServers.joinToString(", ")) }
    var refreshInterval by remember {
        mutableStateOf(initial.refreshIntervalSeconds.toString())
    }

    val peers = remember { initial.peers.map { PeerForm(it) }.toMutableStateList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Interface", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = privateKey,
            onValueChange = { privateKey = it },
            label = { Text("Private key (base64)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = addresses,
            onValueChange = { addresses = it },
            label = { Text("Addresses (CIDR, comma-separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dnsServers,
            onValueChange = { dnsServers = it },
            label = { Text("DNS servers (comma-separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = listenPort,
                onValueChange = { listenPort = it },
                label = { Text("Listen port") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = mtu,
                onValueChange = { mtu = it },
                label = { Text("MTU") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = ifaceProtocol,
            onValueChange = { ifaceProtocol = it },
            label = { Text("Protocol (ipv4 / ipv6 / dualstack)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Peers", style = MaterialTheme.typography.titleMedium)
        peers.forEachIndexed { index, peer ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = peer.name,
                        onValueChange = { peer.name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = peer.publicKey,
                        onValueChange = { peer.publicKey = it },
                        label = { Text("Public key (base64)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = peer.allowedIps,
                        onValueChange = { peer.allowedIps = it },
                        label = { Text("Allowed IPs (CIDR, comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = peer.protocol,
                            onValueChange = { peer.protocol = it },
                            label = { Text("Protocol") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = peer.keepalive,
                            onValueChange = { peer.keepalive = it },
                            label = { Text("Keepalive (s)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(onClick = { peers.removeAt(index) }) {
                        Text("Remove peer")
                    }
                }
            }
        }
        OutlinedButton(onClick = { peers.add(PeerForm(PeerConfig())) }) {
            Text("Add peer")
        }

        Text("Cloudflare plugin", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = cfZone,
            onValueChange = { cfZone = it },
            label = { Text("Zone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cfToken,
            onValueChange = { cfToken = it },
            label = { Text("API token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cfSubdomain,
            onValueChange = { cfSubdomain = it },
            label = { Text("Subdomain (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("STUNMESH", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = stunServers,
            onValueChange = { stunServers = it },
            label = { Text("STUN servers (comma-separated, empty = default)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = refreshInterval,
            onValueChange = { refreshInterval = it },
            label = { Text("Refresh interval (seconds)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val pluginInstance = "cloudflare_builtin"
                val cfConfig = buildMap {
                    put("zone", cfZone.trim())
                    put("token", cfToken.trim())
                    if (cfSubdomain.isNotBlank()) put("subdomain", cfSubdomain.trim())
                }
                onSave(
                    TunnelConfig(
                        iface = InterfaceConfig(
                            privateKey = privateKey.trim(),
                            addresses = addresses.splitList(),
                            dnsServers = dnsServers.splitList(),
                            listenPort = listenPort.trim().toIntOrNull() ?: 0,
                            mtu = mtu.trim().toIntOrNull() ?: 1420,
                            protocol = ifaceProtocol.trim().ifEmpty { "ipv4" },
                        ),
                        peers = peers.map { it.toPeer(pluginInstance) },
                        plugins = listOf(
                            PluginDefinition(
                                instance = pluginInstance,
                                type = "builtin",
                                name = "cloudflare",
                                config = cfConfig,
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
    }
}

private fun String.splitList(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }
