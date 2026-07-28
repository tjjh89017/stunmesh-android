package dev.stunmesh.android.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.config.TunnelConfig

/**
 * The tunnel list. Each row toggles its tunnel: Android permits one active
 * VPN, so turning one on turns off whichever was running. Swiping a row left
 * reveals its edit/export/delete actions.
 */
@Composable
fun TunnelListScreen(
    tunnels: List<TunnelConfig>,
    runningTunnelId: String,
    state: BackendState,
    onToggle: (TunnelConfig, Boolean) -> Unit,
    onEdit: (TunnelConfig) -> Unit,
    onDelete: (TunnelConfig) -> Unit,
    onExport: (TunnelConfig) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deleting is irreversible (the private key goes with the tunnel), so the
    // delete action only arms this and the dialog does the actual delete.
    var confirmingDelete by remember { mutableStateOf<TunnelConfig?>(null) }

    confirmingDelete?.let { tunnel ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete ${tunnel.name}?") },
            text = {
                Text(
                    "This permanently removes the tunnel and its keys. " +
                        "Export it first if you may want it back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = null
                    onDelete(tunnel)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") }
            },
        )
    }

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
                    onDelete = { confirmingDelete = tunnel },
                    onExport = { onExport(tunnel) },
                )
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add tunnel")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import from YAML")
        }
    }
}

/** How far a row slides open, sized to the three action buttons behind it. */
private val ACTIONS_WIDTH = 168.dp

@Composable
private fun TunnelRow(
    tunnel: TunnelConfig,
    running: Boolean,
    state: BackendState,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    val cardOffset by animateDpAsState(
        targetValue = if (revealed) -ACTIONS_WIDTH else 0.dp,
        label = "reveal",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { revealed = false; onEdit() }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { revealed = false; onExport() }) {
                Icon(Icons.Default.Share, contentDescription = "Export")
            }
            // A running tunnel cannot be deleted; stop it first.
            IconButton(onClick = { revealed = false; onDelete() }, enabled = !running) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (running) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = cardOffset)
                .pointerInput(Unit) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -40f) revealed = true
                            if (drag > 40f) revealed = false
                        },
                    ) { _, amount -> drag += amount }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { if (revealed) revealed = false else onEdit() })
                    .padding(12.dp),
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
        }
    }
}
