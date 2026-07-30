# stunmesh-android

Android client for [STUNMESH](https://github.com/tjjh89017/stunmesh-go) —
peer-to-peer WireGuard connections through Full-Cone NAT using STUN discovery
and encrypted peer-endpoint exchange, with no root required.

> **Status: early development.** The Go core (embedded wireguard-go with a
> custom STUN-demuxing `conn.Bind`, built with gomobile from stunmesh-go's
> `mobile/` package) is bundled when `app/libs/stunmesh.aar` is present —
> download it from the stunmesh-go Mobile workflow artifacts. Without the
> AAR the app builds against a stub data plane that moves no packets.
> On-device validation is still in progress.

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

## Installing

Grab `stunmesh-android-<tag>.apk` from the
[latest release](https://github.com/tjjh89017/stunmesh-android/releases) and open
it on the device; sideloading needs "install unknown apps" allowed for whatever
opened it (browser or file manager). The APK is universal — one file covers
arm64-v8a, armeabi-v7a, x86 and x86_64.

Releases are signed with the project's release key, so an installed copy upgrades
in place. A debug APK from CI is signed with a different key and cannot upgrade a
release install (or vice versa) — uninstall first to switch.

## Building

Open in Android Studio, or from the command line:

```
./gradlew assembleDebug
```

A release build is only installable if it is signed, which needs a keystore
supplied through the environment (or `-P` properties: `keystoreFile`,
`keystorePassword`, `keyAlias`, `keyPassword`):

```
KEYSTORE_FILE=/path/to/release.jks KEYSTORE_PASSWORD=... \
KEY_ALIAS=... KEY_PASSWORD=... ./gradlew assembleRelease
```

Without them the build still succeeds but emits
`stunmesh-android-release-unsigned.apk`, which no device will install. Publishing
is handled by the `Release` workflow: push a `v*` tag and it builds, signs and
uploads the APK to a GitHub release (a tag like `v0.1.0-rc1` publishes as a
prerelease). It needs these repository secrets:

| Secret | What it is |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` of the keystore |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | key password |

Generate the key once and keep it (and its passwords) backed up — losing it means
no future build can upgrade an existing install:

```
keytool -genkeypair -keystore release.jks -storetype PKCS12 \
  -alias stunmesh -keyalg RSA -keysize 4096 -validity 10000
```

## License

The app code in this repository is licensed under the
[Apache License 2.0](LICENSE).

The distributed APK will additionally contain the STUNMESH Go core from
[stunmesh-go](https://github.com/tjjh89017/stunmesh-go), which carries its own
license; see that repository for details.

WireGuard is a registered trademark of Jason A. Donenfeld.
