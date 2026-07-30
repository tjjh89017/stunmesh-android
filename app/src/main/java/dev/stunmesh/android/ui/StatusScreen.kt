package dev.stunmesh.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.TunnelYaml
import dev.stunmesh.android.tunnel.TunnelManager

@Composable
fun StatusScreen(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by TunnelManager.state.collectAsState()
    val logLines by TunnelManager.logLines.collectAsState()
    val activeName by TunnelManager.activeTunnelName.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(buildLogExport(context).encodeToByteArray())
            } ?: error("could not open $uri for writing")
        }.onSuccess {
            TunnelManager.appendLog("[info] exported log")
        }.onFailure {
            TunnelManager.appendLog("[error] log export failed: ${it.message}")
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Log", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { exportLauncher.launch("stunmesh-log.txt") }) {
                Text("Export")
            }
        }
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

/**
 * The exported report: the running tunnel's config as YAML with secrets
 * redacted (falling back to the stored active tunnel when nothing runs),
 * followed by the rolling log.
 */
private fun buildLogExport(context: android.content.Context): String {
    val store = ConfigRepository(context).load()
    val tunnel = store.tunnels.firstOrNull { it.id == TunnelManager.activeTunnelId.value }
        ?: store.active
    return buildString {
        appendLine("=== stunmesh-android log export ===")
        if (tunnel != null) {
            appendLine()
            appendLine("--- tunnel config (secrets redacted) ---")
            append(TunnelYaml.encode(tunnel.redactSecrets()))
        }
        appendLine()
        appendLine("--- log ---")
        TunnelManager.logLines.value.forEach { appendLine(it) }
    }
}
