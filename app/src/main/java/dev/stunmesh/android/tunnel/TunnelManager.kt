package dev.stunmesh.android.tunnel

import android.content.Context
import android.content.Intent
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

    /** Swap for the AAR-backed implementation when stunmesh-go ships `mobile/`. */
    val backend: StunmeshBackend = StubBackend()

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
}
