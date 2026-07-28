package dev.stunmesh.android

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.TunnelConfig
import dev.stunmesh.android.tunnel.TunnelManager
import dev.stunmesh.android.ui.StatusScreen
import dev.stunmesh.android.ui.TunnelEditorScreen
import dev.stunmesh.android.ui.TunnelListScreen
import dev.stunmesh.android.ui.theme.StunmeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StunmeshTheme {
                MainScreen()
            }
        }
    }
}

private enum class Tab { Status, Tunnels }

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }

    var tab by remember { mutableStateOf(Tab.Tunnels) }
    var store by remember { mutableStateOf(repository.load()) }
    var editing by remember { mutableStateOf<TunnelConfig?>(null) }
    // The tunnel whose switch was flipped on, held until VPN consent returns.
    var pendingStartId by remember { mutableStateOf("") }

    val state by TunnelManager.state.collectAsState()
    val runningId by TunnelManager.activeTunnelId.collectAsState()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingStartId
        pendingStartId = ""
        if (result.resultCode == Activity.RESULT_OK && id.isNotEmpty()) {
            TunnelManager.start(context, id)
            tab = Tab.Status
        } else {
            TunnelManager.appendLog("[warn] VPN consent denied")
        }
    }

    // prepare() returns an intent the first time (or after another VPN app
    // took consent); null means consent is already granted.
    fun startTunnel(id: String) {
        val consent = VpnService.prepare(context)
        if (consent == null) {
            TunnelManager.start(context, id)
            tab = Tab.Status
        } else {
            pendingStartId = id
            consentLauncher.launch(consent)
        }
    }

    val editingTunnel = editing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (editingTunnel == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Status,
                        onClick = { tab = Tab.Status },
                        icon = {},
                        label = { Text("Status") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Tunnels,
                        onClick = { tab = Tab.Tunnels },
                        icon = {},
                        label = { Text("Tunnels") },
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            editingTunnel != null -> TunnelEditorScreen(
                initial = editingTunnel,
                onSave = { config ->
                    store = store.upsert(config)
                    repository.save(store)
                    editing = null
                    TunnelManager.appendLog("[info] saved tunnel ${config.name}")
                },
                onCancel = { editing = null },
                modifier = Modifier.padding(innerPadding),
            )

            tab == Tab.Status -> StatusScreen(
                onDisconnect = { TunnelManager.stop(context) },
                modifier = Modifier.padding(innerPadding),
            )

            else -> TunnelListScreen(
                tunnels = store.tunnels,
                runningTunnelId = runningId,
                state = state,
                onToggle = { tunnel, on ->
                    if (on) startTunnel(tunnel.id) else TunnelManager.stop(context)
                },
                onEdit = { editing = it },
                onDelete = { tunnel ->
                    store = store.remove(tunnel.id)
                    repository.save(store)
                },
                onAdd = { editing = TunnelConfig() },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
