package dev.stunmesh.android.backend

import android.os.ParcelFileDescriptor

/**
 * Placeholder for the real Go core. It exercises the full app flow — takes
 * the tun fd, reports state transitions and emits fake events — but moves no
 * packets. Replace with the AAR-backed implementation once stunmesh-go
 * publishes the `mobile/` package.
 */
class StubBackend : StunmeshBackend {

    @Volatile
    private var tunFd: Int = -1

    @Volatile
    override var isRunning: Boolean = false
        private set

    private var listener: EventListener? = null

    override fun start(
        configJson: String,
        tunProvider: TunProvider,
        socketProtector: SocketProtector,
        eventListener: EventListener,
    ) {
        check(!isRunning) { "backend already running" }
        listener = eventListener
        eventListener.onStateChanged(BackendState.STARTING)
        eventListener.onLog("info", "stub backend starting; config ${configJson.length} bytes")

        val fd = tunProvider.openTun(DEFAULT_MTU)
        if (fd < 0) {
            eventListener.onStateChanged(BackendState.DOWN)
            error("tun fd not available")
        }
        tunFd = fd
        eventListener.onLog("info", "tun fd $fd received (stub: no packets will move)")

        isRunning = true
        eventListener.onStateChanged(BackendState.UP)
        eventListener.onEvent(
            BackendEvent("stub", null, "data plane is a stub; waiting for the real AAR")
        )
    }

    override fun stop() {
        if (!isRunning) return
        listener?.onStateChanged(BackendState.STOPPING)
        closeTun()
        isRunning = false
        listener?.onStateChanged(BackendState.DOWN)
        listener = null
    }

    override fun renewTun(fd: Int) {
        listener?.onLog("info", "renewTun($fd)")
        closeTun()
        tunFd = fd
    }

    private fun closeTun() {
        val fd = tunFd
        tunFd = -1
        if (fd >= 0) {
            runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
        }
    }

    companion object {
        const val DEFAULT_MTU = 1420
    }
}
