package dev.stunmesh.android.tunnel

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.stunmesh.android.backend.BackendEvent
import dev.stunmesh.android.backend.BackendState
import dev.stunmesh.android.backend.EventListener
import dev.stunmesh.android.backend.StubBackend
import dev.stunmesh.android.backend.StunmeshBackend
import dev.stunmesh.android.service.StunmeshVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide tunnel state shared between the UI and [StunmeshVpnService].
 * The service drives the backend; the UI observes [state] and [logLines] and
 * asks for transitions with [start]/[stop].
 */
object TunnelManager {

    /**
     * The Go-core backend when the AAR is bundled (GoBackend only exists in
     * builds that include app/libs/stunmesh.aar), otherwise the stub. A
     * ClassNotFoundException is the expected stub-build path; anything else
     * means the Go core is present but unusable, which must be visible
     * rather than silently degrade to a data plane that moves no packets.
     */
    val backend: StunmeshBackend = loadBackend()

    private fun loadBackend(): StunmeshBackend = try {
        Class.forName("dev.stunmesh.android.backend.GoBackend")
            .getDeclaredConstructor()
            .newInstance() as StunmeshBackend
    } catch (e: ClassNotFoundException) {
        Log.i(TAG, "Go core not bundled, using stub backend")
        StubBackend()
    } catch (t: Throwable) {
        Log.e(TAG, "Go core present but failed to load, using stub backend", t)
        StubBackend()
    }

    private val _state = MutableStateFlow(BackendState.DOWN)
    val state: StateFlow<BackendState> = _state.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    val eventListener: EventListener = object : EventListener {
        override fun onStateChanged(state: BackendState) {
            _state.value = state
        }

        override fun onLog(level: String, message: String) {
            appendLog("[$level] $message")
        }

        override fun onEvent(event: BackendEvent) {
            val peer = event.peerPublicKey?.let { " peer=${it.take(8)}…" } ?: ""
            appendLog("event ${event.kind}$peer: ${event.detail}")
        }
    }

    /** Caller must have completed the `VpnService.prepare()` consent flow. */
    fun start(context: Context) {
        context.startService(serviceIntent(context, StunmeshVpnService.ACTION_UP))
    }

    fun stop(context: Context) {
        context.startService(serviceIntent(context, StunmeshVpnService.ACTION_DOWN))
    }

    fun appendLog(line: String) {
        _logLines.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    private fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, StunmeshVpnService::class.java).setAction(action)

    private const val MAX_LOG_LINES = 200
    private const val TAG = "Stunmesh"
}
