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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.tunnel.TunnelManager
import dev.stunmesh.android.ui.SettingsScreen
import dev.stunmesh.android.ui.StatusScreen
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

private enum class Screen { Status, Settings }

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Status) }
    val configRepository = remember { ConfigRepository(context) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            TunnelManager.start(context)
        } else {
            TunnelManager.appendLog("[warn] VPN consent denied")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.Status,
                    onClick = { screen = Screen.Status },
                    icon = {},
                    label = { Text("Status") },
                )
                NavigationBarItem(
                    selected = screen == Screen.Settings,
                    onClick = { screen = Screen.Settings },
                    icon = {},
                    label = { Text("Settings") },
                )
            }
        },
    ) { innerPadding ->
        when (screen) {
            Screen.Status -> StatusScreen(
                onConnect = {
                    // prepare() returns an intent on first use (or after
                    // another VPN app took consent); null means already granted.
                    val consent = VpnService.prepare(context)
                    if (consent != null) {
                        consentLauncher.launch(consent)
                    } else {
                        TunnelManager.start(context)
                    }
                },
                onDisconnect = { TunnelManager.stop(context) },
                modifier = Modifier.padding(innerPadding),
            )
            Screen.Settings -> SettingsScreen(
                initial = configRepository.load(),
                onSave = { config ->
                    configRepository.save(config)
                    TunnelManager.appendLog("[info] config saved")
                    screen = Screen.Status
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
