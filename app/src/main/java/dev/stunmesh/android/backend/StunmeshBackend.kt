package dev.stunmesh.android.backend

/**
 * Boundary to the Go core (stunmesh-go `mobile/` package, delivered as an AAR
 * built with gomobile). This interface mirrors the planned gomobile API
 * surface: config crosses as JSON strings, file descriptors as Int, and the
 * app supplies callbacks through [TunProvider], [SocketProtector] and
 * [EventListener].
 *
 * Until the AAR exists the app runs against [StubBackend].
 */
interface StunmeshBackend {
    /**
     * Start the node with the given config (JSON, see `config.TunnelConfig`).
     * Blocks until the data plane is up or throws on failure.
     */
    fun start(
        configJson: String,
        tunProvider: TunProvider,
        socketProtector: SocketProtector,
        eventListener: EventListener,
    )

    /** Stop the node and release the tun fd. Idempotent. */
    fun stop()

    /**
     * Hand a fresh tun fd to the running node after Android replaced the
     * network. The WG device must not restart.
     */
    fun renewTun(fd: Int)

    /** True while the node is running. */
    val isRunning: Boolean
}

/** Supplies the detached tun fd from `VpnService.Builder.establish()`. */
fun interface TunProvider {
    /** Returns a detached fd owned by the callee, or -1 on failure. */
    fun openTun(mtu: Int): Int
}

/**
 * Calls `VpnService.protect(fd)` on outer UDP sockets so they bypass the
 * tunnel and no routing loop forms.
 */
fun interface SocketProtector {
    /** Returns true when the socket was protected. */
    fun protect(fd: Int): Boolean
}

/** Receives status, log and error events from the Go core. */
interface EventListener {
    fun onStateChanged(state: BackendState)

    /** One log line from the core. [level] is one of "debug", "info", "warn", "error". */
    fun onLog(level: String, message: String)

    /** A STUNMESH event, e.g. endpoint discovered or peer endpoint updated. */
    fun onEvent(event: BackendEvent)
}

enum class BackendState {
    DOWN,
    STARTING,
    UP,
    STOPPING,
}

/**
 * Structured event from the core. [kind] examples: "endpoint_discovered",
 * "peer_endpoint_updated", "publish_ok", "handshake". [detail] is free text.
 */
data class BackendEvent(
    val kind: String,
    val peerPublicKey: String?,
    val detail: String,
)
