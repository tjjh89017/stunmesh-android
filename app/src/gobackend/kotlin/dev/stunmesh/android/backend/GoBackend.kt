package dev.stunmesh.android.backend

import mobile.Mobile
import mobile.Node

/**
 * Backend backed by the stunmesh-go `mobile/` package (stunmesh.aar,
 * `libgojni.so`). This source directory is only compiled when
 * `app/libs/stunmesh.aar` exists; without it the app falls back to
 * [StubBackend]. [dev.stunmesh.android.tunnel.TunnelManager] picks this class
 * up via reflection.
 */
class GoBackend : StunmeshBackend {

    @Volatile
    private var node: Node? = null

    override val isRunning: Boolean
        get() = node?.isRunning ?: false

    override val coreVersion: String
        get() = Mobile.version()

    override fun start(
        configJson: String,
        tunProvider: TunProvider,
        socketProtector: SocketProtector,
        eventListener: EventListener,
    ) {
        check(node == null) { "backend already running" }
        val goNode = Mobile.newNode(
            configJson,
            { mtu -> tunProvider.openTun(mtu) },
            { fd -> socketProtector.protect(fd) },
            object : mobile.EventListener {
                override fun onStateChanged(state: String) {
                    eventListener.onStateChanged(state.toBackendState())
                }

                override fun onLog(level: String, message: String) {
                    eventListener.onLog(level, message)
                }

                override fun onEvent(kind: String, peerPublicKey: String, detail: String) {
                    eventListener.onEvent(
                        BackendEvent(kind, peerPublicKey.ifEmpty { null }, detail)
                    )
                }
            },
        )
        node = goNode
        try {
            goNode.start()
        } catch (t: Throwable) {
            node = null
            throw t
        }
    }

    override fun stop() {
        node?.stop()
        node = null
    }

    override fun renewTun(fd: Int) {
        node?.renewTun(fd)
    }
}

private fun String.toBackendState(): BackendState = when (this) {
    "starting" -> BackendState.STARTING
    "up" -> BackendState.UP
    "stopping" -> BackendState.STOPPING
    else -> BackendState.DOWN
}
