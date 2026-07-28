package dev.stunmesh.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.TunnelConfig

/**
 * Debug-only adb hook to inject a tunnel config without typing it into the
 * UI. The extra "config" carries the TunnelConfig JSON, base64-encoded so it
 * survives shell quoting.
 */
class ConfigImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val encoded = intent.getStringExtra("config") ?: return
        runCatching {
            val json = Base64.decode(encoded, Base64.DEFAULT).decodeToString()
            val tunnel = TunnelConfig.fromJson(json)
            val repository = ConfigRepository(context)
            // Replace by name so repeated imports of the same test config
            // update it instead of piling up duplicates.
            val store = repository.load()
            val existing = store.tunnels.firstOrNull { it.name == tunnel.name }
            val imported = if (existing != null) tunnel.copy(id = existing.id) else tunnel
            repository.save(store.upsert(imported).copy(activeId = imported.id))
            imported.name
        }.onSuccess {
            Log.i(TAG, "config imported: $it")
        }.onFailure {
            Log.e(TAG, "config import failed", it)
        }
    }

    private companion object {
        const val TAG = "StunmeshDebug"
    }
}
