package dev.stunmesh.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stunmesh.android.BuildConfig
import dev.stunmesh.android.tunnel.TunnelManager

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("STUNMESH", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Peer-to-peer WireGuard connections through Full-Cone NAT, " +
                "using STUN discovery and encrypted peer-endpoint exchange.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Versions", style = MaterialTheme.typography.titleMedium)
                InfoRow("App", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Build", BuildConfig.BUILD_TYPE)
                InfoRow("STUNMESH core", TunnelManager.backend.coreVersion)
                InfoRow("Data plane", TunnelManager.backend::class.java.simpleName)
            }
        }

        Text(
            "The app is licensed under the Apache License 2.0. The bundled " +
                "STUNMESH core carries its own license; see the stunmesh-go " +
                "repository.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "WireGuard is a registered trademark of Jason A. Donenfeld.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
