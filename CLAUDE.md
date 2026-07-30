# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android client for [STUNMESH](https://github.com/tjjh89017/stunmesh-go) — peer-to-peer WireGuard
connections through Full-Cone NAT using STUN discovery and encrypted peer-endpoint exchange, no root
required. Status: early development, on-device validation still in progress.

The data plane is an embedded [wireguard-go](https://git.zx2c4.com/wireguard-go/) device inside a Go
core built with `gomobile bind` from stunmesh-go's `mobile/` package, delivered as a prebuilt AAR.
STUNMESH owns the outer UDP socket inside a custom `conn.Bind`: STUN discovery/hole-punching share the
socket with WireGuard traffic, demuxed on receive. Peer endpoints are exchanged through storage plugins
(v1: built-in Cloudflare DNS) and applied at runtime over WireGuard's UAPI — the tunnel never restarts
for an endpoint change. On a network change, the app hands the running core a fresh tun fd instead of
restarting the WG device.

Limitations: Android allows only one active VPN app at a time; only built-in endpoint-exchange plugins
are supported (`exec`/`shell` plugins need external processes and stay desktop-only); Doze can delay
keepalives, consider exempting the app from battery optimization.

## Commands

```
./gradlew assembleDebug              # build debug APK -> app/build/outputs/apk/debug/stunmesh-android-debug.apk
./gradlew testDebugUnitTest          # run JVM unit tests (what CI runs)
./gradlew testDebugUnitTest --tests "dev.stunmesh.android.config.TunnelYamlTest"   # single test class
./gradlew testDebugUnitTest --tests "dev.stunmesh.android.config.TunnelYamlTest.someTestMethod"  # single test method
```

`androidTest` currently contains only the unused Espresso template; there is no real instrumented
coverage to run.

### Version resolution

`versionName`/`versionCode` default to `git describe --tags --always --dirty` / `git rev-list --count
HEAD`, overridable via `-PversionName`/`VERSION_NAME` and `-PversionCode`/`VERSION_CODE`. Reading git
needs full history — a shallow checkout silently produces `dev`/`1` unless overridden. CI derives these
from workflow context instead (tag ref → `VERSION_NAME=<tag>`, else `0.0.0-<sha7>`; `VERSION_CODE=$run_number`,
monotonic like versionCode must be) so it can stay shallow.

### Release signing and publishing

`release` gets a signing config only when all four of `KEYSTORE_FILE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are present (env, or the matching
`-PkeystoreFile`-style properties); otherwise `assembleRelease` still succeeds but emits
`stunmesh-android-release-unsigned.apk`, which no device installs. v3 signing is on so the
key can carry a lineage later; AGP then drops the redundant v2 block, which minSdk 28
doesn't need. `.github/workflows/release.yml` runs on `v*` tags: decodes the keystore from
`RELEASE_KEYSTORE_BASE64` into `$RUNNER_TEMP`, builds, and `gh release create`s the APK as
`stunmesh-android-<tag>.apk` (prerelease when the tag has a `-suffix`). Unlike CI, a
release build resolves the pinned Go core AAR, so the published APK carries a real data
plane.

### Go core AAR

Set `stunmeshCoreVersion` in `gradle.properties` to a stunmesh-go release tag to pull
`dev.stunmesh:stunmesh-android:<tag>@aar` from a custom Ivy repo pointed at that repo's GitHub release
assets (declared in `settings.gradle.kts`). Debug builds instead prefer any `.aar` dropped in `app/libs/`
(for iterating against an unreleased core), falling back to the pinned version; release builds always
use the pinned version only — a shipped build must be reproducible from sources alone. With neither
present, the app builds against the stub backend at runtime (not a build failure). CI never sets
`stunmeshCoreVersion`, so CI always builds/tests against the stub.

## Architecture

```
app/src/main/java/dev/stunmesh/android/
  MainActivity.kt          entry point: Compose host, bottom-nav Scaffold (Status/Tunnels/About),
                            VpnService.prepare() consent flow, YAML export/import via SAF pickers
  backend/
    StunmeshBackend.kt      interface = the gomobile boundary contract (start/stop/renewTun,
                            TunProvider, SocketProtector, EventListener, BackendState, BackendEvent);
                            config crosses as JSON, fds as Int
    StubBackend.kt          always-compiled placeholder: exercises the full flow, emits fake
                            state/events, moves no packets
  config/
    ConfigRepository.kt     encrypts the whole TunnelStore as one AES-256-GCM blob keyed by an
                            Android-Keystore key, written atomically (.tmp + rename) to tunnel_config.bin
    TunnelStore.kt          all tunnels + activeId
    TunnelConfig.kt         TunnelConfig/InterfaceConfig/PeerConfig/PluginDefinition + JSON
                            (de)serialization; field names mirror stunmesh-go's internal/config and
                            pluginapi.PluginDefinition since this JSON also crosses into the Go core
    TunnelYaml.kt           separate human/desktop-facing YAML schema (schema=1, wireguard + stunmesh
                            sections joined by public_key) for backup/export
    WgQuickConf.kt          wg-quick .conf import (sniffed via looksLikeConf, not exception fallthrough)
  service/
    StunmeshVpnService.kt   android.net.VpnService subclass; single-thread executor serializes
                            up/down/renew; builds the tun device from TunnelConfig; registers a
                            ConnectivityManager.NetworkCallback to call backend.renewTun(fd) on
                            default-network change instead of restarting the WG device; onRevoke()
                            handles another VPN app taking over
  tunnel/
    TunnelManager.kt        process-wide singleton (object) holding the loaded backend, StateFlow<BackendState>,
                            rolling log (max 200 lines), active tunnel id/name; start()/stop() send
                            Intents (ACTION_UP/ACTION_DOWN) to the service rather than calling the
                            backend directly
  ui/                       Compose screens: StatusScreen, TunnelListScreen, TunnelEditorScreen,
                            AboutScreen, theme/

app/src/gobackend/kotlin/dev/stunmesh/android/backend/GoBackend.kt
                            wraps mobile.Mobile/mobile.Node from stunmesh-go's gomobile package; only
                            added to the debug/release Kotlin source sets when the Go core AAR is
                            resolvable (see build.gradle.kts sourceSets block)

app/src/debug/java/dev/stunmesh/android/debug/ConfigImportReceiver.kt
                            debug-only BroadcastReceiver for adb-injecting a TunnelConfig JSON (base64)
```

**Backend loading**: `TunnelManager.loadBackend()` picks `GoBackend` via `Class.forName(...)`
reflection, so the `main` source set never has a compile-time dependency on the optional class.
`ClassNotFoundException` is the expected no-AAR path; any other throwable means the Go core is present
but broken, and is logged as an error while still falling back to `StubBackend` rather than crashing.

**Manifest**: only `INTERNET` and `ACCESS_NETWORK_STATE` permissions. `StunmeshVpnService` is exported
(required so the system can bind it for always-on VPN) and gated by `android.permission.BIND_VPN_SERVICE`
(only the system holds it), with the required `android.net.VpnService` intent-filter.

## Dev environment

No Android Studio here — headless Debian. `local.properties` (`sdk.dir=...`, gitignored) points Gradle
at the Android SDK; JDK 21 and cmdline-tools/platform-tools/build-tools;36.0.0/platforms;android-36 are
installed under `~/android-sdk`. For on-device testing, either a real device over `adb` or a local AVD
(x86_64, KVM-accelerated, run with `emulator -avd <name> -no-window -no-audio`) both work; `adb install`
+ `adb shell monkey -p dev.stunmesh.android -c android.intent.category.LAUNCHER 1` launches the app
without a GUI.
