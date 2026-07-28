# stunmesh-android

Android client for [STUNMESH](https://github.com/tjjh89017/stunmesh-go) —
peer-to-peer WireGuard connections through Full-Cone NAT using STUN discovery
and encrypted peer-endpoint exchange, with no root required.

> **Status: early development.** The app currently runs against a stub data
> plane. The real Go core (embedded wireguard-go with a custom STUN-demuxing
> `conn.Bind`, built with gomobile from stunmesh-go's `mobile/` package) is
> under development and not wired in yet. Nothing moves packets today.

## How it works

- The app is a full VPN app built on Android's `VpnService` API (no root, no
  raw sockets).
- The data plane is an embedded [wireguard-go](https://git.zx2c4.com/wireguard-go/)
  device inside the Go core, delivered as an AAR (`libgojni.so`) built with
  `gomobile bind` from the stunmesh-go repository.
- STUNMESH owns the outer UDP socket inside a custom `conn.Bind`: STUN
  discovery and hole punching share the socket with WireGuard traffic, and a
  demux on the receive path separates STUN responses from WG packets.
- Peer endpoints are exchanged through storage plugins (v1: built-in
  Cloudflare DNS) and applied at run time over WireGuard's UAPI — the tunnel
  never restarts for an endpoint change.
- On a network change the app hands a fresh tun fd to the running core; the
  WG device survives without a restart.

## Limitations

- Android allows one active VPN app at a time; STUNMESH cannot run alongside
  another VPN app.
- Only built-in endpoint-exchange plugins are supported (`exec`/`shell`
  plugins need external processes and stay desktop-only).
- Battery optimization (Doze) can delay keepalives in deep sleep; consider
  exempting the app from battery optimization for reliable long-lived
  tunnels.

## Building

Open in Android Studio, or from the command line:

```
./gradlew assembleDebug
```

## License

The app code in this repository is licensed under the
[Apache License 2.0](LICENSE).

The distributed APK will additionally contain the STUNMESH Go core from
[stunmesh-go](https://github.com/tjjh89017/stunmesh-go), which carries its own
license; see that repository for details.

WireGuard is a registered trademark of Jason A. Donenfeld.
