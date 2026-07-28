package dev.stunmesh.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.tunnel.TunnelManager

@Composable
fun StatusScreen(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by TunnelManager.state.collectAsState()
    val logLines by TunnelManager.logLines.collectAsState()
    val activeName by TunnelManager.activeTunnelName.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (state) {
                        BackendState.DOWN -> "Disconnected"
                        BackendState.STARTING -> "Connecting…"
                        BackendState.UP -> "Connected"
                        BackendState.STOPPING -> "Disconnecting…"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (state != BackendState.DOWN && activeName.isNotEmpty()) {
                    Text(
                        text = activeName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = state == BackendState.UP || state == BackendState.STARTING,
                ) {
                    Text("Disconnect")
                }
            }
        }

        Text("Log", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            reverseLayout = true,
        ) {
            items(logLines.asReversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
