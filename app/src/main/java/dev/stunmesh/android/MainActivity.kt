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
import dev.stunmesh.android.config.TunnelYaml
import dev.stunmesh.android.config.WgQuickConf
import dev.stunmesh.android.tunnel.TunnelManager
import dev.stunmesh.android.ui.AboutScreen
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

private enum class Tab { Status, Tunnels, About }

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

    // The tunnel whose YAML the create-document picker will receive.
    var exporting by remember { mutableStateOf<TunnelConfig?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/yaml")
    ) { uri ->
        val tunnel = exporting
        exporting = null
        if (uri == null || tunnel == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(TunnelYaml.encode(tunnel).encodeToByteArray())
            } ?: error("could not open $uri for writing")
        }.onSuccess {
            TunnelManager.appendLog("[info] exported ${tunnel.name}")
        }.onFailure {
            TunnelManager.appendLog("[error] export failed: ${it.message}")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("could not open $uri for reading")
            // Both the app's own YAML and plain wg-quick .conf files import;
            // a .conf has no YAML mapping shape, so sniff for its section
            // header rather than trying decoders in sequence.
            val tunnel = if (WgQuickConf.looksLikeConf(text)) {
                WgQuickConf.decode(text, displayName(context, uri))
            } else {
                TunnelYaml.decode(text)
            }
            store = store.upsert(tunnel)
            repository.save(store)
            tunnel.name
        }.onSuccess {
            TunnelManager.appendLog("[info] imported $it")
        }.onFailure {
            TunnelManager.appendLog("[error] import failed: ${it.message}")
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
                    NavigationBarItem(
                        selected = tab == Tab.About,
                        onClick = { tab = Tab.About },
                        icon = {},
                        label = { Text("About") },
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

            tab == Tab.About -> AboutScreen(modifier = Modifier.padding(innerPadding))

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
                onExport = { tunnel ->
                    exporting = tunnel
                    exportLauncher.launch("${tunnel.name}.yaml")
                },
                onAdd = { editing = TunnelConfig() },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Tunnel name for an imported .conf: the picked file's name, extension off. */
private fun displayName(context: android.content.Context, uri: android.net.Uri): String {
    val name = context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
    return name?.substringBeforeLast('.')?.ifEmpty { null } ?: "imported"
}
