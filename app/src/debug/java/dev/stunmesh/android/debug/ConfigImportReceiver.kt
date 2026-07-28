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
            ConfigRepository(context).save(TunnelConfig.fromJson(json))
        }.onSuccess {
            Log.i(TAG, "config imported")
        }.onFailure {
            Log.e(TAG, "config import failed", it)
        }
    }

    private companion object {
        const val TAG = "StunmeshDebug"
    }
}
