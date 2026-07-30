package dev.stunmesh.android.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import dev.stunmesh.android.backend.SocketProtector
import dev.stunmesh.android.backend.TunProvider
import dev.stunmesh.android.config.ConfigRepository
import dev.stunmesh.android.config.TunnelConfig
import dev.stunmesh.android.tunnel.TunnelManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the tun device. The backend pulls the fd through [TunProvider]
 * (`VpnService.Builder.establish()` + `detachFd()`) and protects its outer
 * UDP sockets through [SocketProtector] so no routing loop forms. On a
 * default-network change a fresh tun fd goes to the backend via `renewTun` —
 * the WG device must survive without a restart.
 */
class StunmeshVpnService : VpnService() {

    // Serializes up/down/renew; backend.start blocks and must stay off the
    // main thread.
    private lateinit var executor: ExecutorService
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private var dnsCallback: ConnectivityManager.NetworkCallback? = null
    private val dnsServersByNetwork = mutableMapOf<Network, List<String>>()

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // stopSelf(startId) rather than stopSelf(): a start that arrives
            // while this is queued must not be cancelled by it.
            ACTION_DOWN -> executor.execute {
                down()
                stopSelf(startId)
            }
            // ACTION_UP from the UI; null on service restart; SERVICE_INTERFACE
            // when the system starts us for always-on VPN.
            else -> executor.execute { up() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // Another VPN app took over (Android permits one active VPN).
        executor.execute {
            down()
            stopSelf()
        }
    }

    override fun onDestroy() {
        executor.execute { down() }
        executor.shutdown()
        super.onDestroy()
    }

    private fun up() {
        // Android permits one active VPN, so bringing up a tunnel while
        // another runs replaces it.
        if (TunnelManager.backend.isRunning) {
            down()
        }
        val config = ConfigRepository(this).activeTunnel()
        if (config == null) {
            TunnelManager.appendLog("[error] no tunnel selected")
            stopSelf()
            return
        }
        TunnelManager.setActiveTunnel(config.id, config.name)
        try {
            TunnelManager.backend.start(
                configJson = config.toJson(),
                tunProvider = TunProvider { mtu -> establishTun(config, mtu) },
                socketProtector = SocketProtector { fd -> protect(fd) },
                eventListener = TunnelManager.eventListener,
            )
            registerNetworkCallback()
            registerDnsCallback()
        } catch (t: Throwable) {
            TunnelManager.appendLog("[error] tunnel up failed: ${t.message}")
            stopSelf()
        }
    }

    private fun down() {
        unregisterNetworkCallback()
        unregisterDnsCallback()
        TunnelManager.backend.stop()
    }

    /**
     * Builds and establishes the tun device, returning a detached fd the Go
     * side owns from here on. Returns -1 when establish fails (e.g. VPN
     * consent was revoked).
     */
    private fun establishTun(config: TunnelConfig, fallbackMtu: Int): Int {
        val builder = Builder().setSession(SESSION_NAME)
        builder.setMtu(if (config.iface.mtu > 0) config.iface.mtu else fallbackMtu)
        config.iface.addresses.forEach { cidr ->
            parseCidr(cidr)?.let { (addr, prefix) -> builder.addAddress(addr, prefix) }
        }
        config.iface.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
        }
        // Allowed IPs double as the routes captured into the tunnel.
        config.peers.flatMap { it.allowedIps }.forEach { cidr ->
            parseCidr(cidr)?.let { (addr, prefix) ->
                runCatching { builder.addRoute(addr, prefix) }
            }
        }
        val pfd = builder.establish() ?: return -1
        return pfd.detachFd()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Callbacks can still be in flight briefly after
                // unregisterNetworkCallback(); the executor may already be shut down.
                runCatching { executor.execute { onDefaultNetworkChanged(network) } }
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            }
        }
        networkCallback = null
        lastNetwork = null
    }

    /**
     * Tracks the underlay networks' DNS servers (not the tunnel's — plugin
     * sockets are protected out of the tunnel, so a tunnel-internal resolver
     * would be unreachable from them). NOT_VPN excludes the VPN itself, which
     * `registerDefaultNetworkCallback` above would otherwise see as default
     * once up, feeding the core its own unreachable DNS.
     *
     * More than one underlay can be up at once (handover windows, "mobile
     * data always active", dual-SIM), and this request matches all of them —
     * there is no cheap way to single out the one protected sockets actually
     * route over. So every change pushes the union of all tracked networks'
     * servers rather than guessing a "current" one; the core already retries
     * the next server in the list on a dial failure, so unreachable entries
     * from a non-default network are harmless.
     */
    private fun registerDnsCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        lateinit var callback: ConnectivityManager.NetworkCallback
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                // Same in-flight-after-unregister race as onAvailable above.
                runCatching {
                    executor.execute {
                        // Tasks queued before down()'s unregister() land after its
                        // clear(); a stale callback must not repopulate the map.
                        if (dnsCallback !== callback) return@execute
                        // Link-local resolvers (e.g. RA RDNSS fe80::1) carry a %zone
                        // in hostAddress that the core can't dial on API 30+.
                        dnsServersByNetwork[network] = linkProperties.dnsServers
                            .filterNot { it.isLinkLocalAddress }
                            .mapNotNull { it.hostAddress }
                        pushDnsServers()
                    }
                }
            }

            override fun onLost(network: Network) {
                runCatching {
                    executor.execute {
                        if (dnsCallback !== callback) return@execute
                        dnsServersByNetwork.remove(network)
                        pushDnsServers()
                    }
                }
            }
        }
        cm.registerNetworkCallback(request, callback)
        dnsCallback = callback
    }

    private fun pushDnsServers() {
        val servers = dnsServersByNetwork.values.flatten().distinct().joinToString(",")
        TunnelManager.backend.setDnsServers(servers)
    }

    private fun unregisterDnsCallback() {
        dnsCallback?.let {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            }
        }
        dnsCallback = null
        dnsServersByNetwork.clear()
    }

    private fun onDefaultNetworkChanged(network: Network) {
        val previous = lastNetwork
        lastNetwork = network
        if (previous == null || previous == network || !TunnelManager.backend.isRunning) return
        TunnelManager.appendLog("[info] default network changed, renewing tun fd")
        val config = ConfigRepository(this).activeTunnel() ?: return
        val fd = establishTun(config, config.iface.mtu)
        if (fd >= 0) {
            TunnelManager.backend.renewTun(fd)
        } else {
            TunnelManager.appendLog("[error] tun renewal failed")
        }
    }

    /** "10.0.0.2/32" → address + prefix length; null when malformed. */
    private fun parseCidr(cidr: String): Pair<String, Int>? {
        val trimmed = cidr.trim()
        if (trimmed.isEmpty()) return null
        val slash = trimmed.lastIndexOf('/')
        if (slash <= 0) return null
        val prefix = trimmed.substring(slash + 1).toIntOrNull() ?: return null
        return trimmed.substring(0, slash) to prefix
    }

    companion object {
        const val ACTION_UP = "dev.stunmesh.android.action.UP"
        const val ACTION_DOWN = "dev.stunmesh.android.action.DOWN"
        private const val SESSION_NAME = "STUNMESH"
    }
}
