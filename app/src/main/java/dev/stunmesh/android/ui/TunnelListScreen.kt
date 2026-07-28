package dev.stunmesh.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.config.TunnelConfig

/**
 * The tunnel list. Each row toggles its tunnel: Android permits one active
 * VPN, so turning one on turns off whichever was running.
 */
@Composable
fun TunnelListScreen(
    tunnels: List<TunnelConfig>,
    runningTunnelId: String,
    state: BackendState,
    onToggle: (TunnelConfig, Boolean) -> Unit,
    onEdit: (TunnelConfig) -> Unit,
    onDelete: (TunnelConfig) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tunnels.isEmpty()) {
            Text(
                "No tunnels yet.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tunnels, key = { it.id }) { tunnel ->
                TunnelRow(
                    tunnel = tunnel,
                    running = tunnel.id == runningTunnelId && state != BackendState.DOWN,
                    state = state,
                    onToggle = { on -> onToggle(tunnel, on) },
                    onEdit = { onEdit(tunnel) },
                    onDelete = { onDelete(tunnel) },
                )
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add tunnel")
        }
    }
}

@Composable
private fun TunnelRow(
    tunnel: TunnelConfig,
    running: Boolean,
    state: BackendState,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tunnel.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when {
                            running && state == BackendState.UP -> "Connected"
                            running -> state.name.lowercase().replaceFirstChar { it.uppercase() }
                            else -> "${tunnel.peers.size} peer(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = onToggle,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete, enabled = !running) { Text("Delete") }
            }
        }
    }
}
