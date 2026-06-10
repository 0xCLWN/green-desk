# PoC Plan

## Goal

Single-screen Android app that connects to an xray proxy server using a config bundled in assets.
No config UI, no server management. Just: open app → tap connect → traffic is proxied.

## Constraints

- arm64-v8a only
- xray config is hardcoded in `assets/config.json`
- Prebuilt native libraries pulled from v2rayNG (no local Go/NDK build)

## Steps

### 1. Get prebuilt libraries

Two native libraries needed:

| Library           | What it is                      | Where to get                                  |
|-------------------|---------------------------------|-----------------------------------------------|
| `libxray.aar`     | xray-core compiled via gomobile | XTLS/libxray releases or v2rayNG APK          |
| `libtun2socks.so` | tun2socks for arm64             | Extracted from v2rayNG APK (`lib/arm64-v8a/`) |

Action: download latest v2rayNG APK, unzip it, pull both files out.

### 2. Wire libraries into the project

```
app/
  libs/
    libxray.aar
  src/main/
    jniLibs/
      arm64-v8a/
        libtun2socks.so
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```

### 3. AndroidManifest.xml

Permissions and service declaration needed:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<service
    android:name=".GreenVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

### 4. Bundle xray config

Copy existing xray client config to `assets/config.json`.

The config must have a SOCKS5 inbound so tun2socks has somewhere to connect:

```json
{
  "inbounds": [
    {
      "tag": "socks-in",
      "protocol": "socks",
      "listen": "127.0.0.1",
      "port": 10808,
      "settings": { "udp": true }
    }
  ],
  "outbounds": [
    // ... your existing outbound (VLESS/VMess/etc.)
  ]
}
```

### 5. Implement GreenVpnService

`VpnService` subclass — the core of the app.

Responsibilities:

- Read config from assets, start xray via libxray API
- Build the TUN interface via `VpnService.Builder`
- Start tun2socks, hand it the TUN file descriptor
- Post a foreground notification (Android requires this)
- On stop: tear everything down in reverse order

```kotlin
class GreenVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. start xray
        // 2. establish TUN via Builder
        // 3. start tun2socks with fd
        // 4. post foreground notification
    }

    override fun onDestroy() {
        // stop tun2socks, stop xray, close fd
    }
}
```

### 6. Implement VpnViewModel

Owns connect/disconnect logic and exposes UI state.

```kotlin
sealed class VpnState { object Disconnected, Connecting, Connected }

class VpnViewModel : ViewModel() {
    val state: StateFlow<VpnState>
    fun connect(context: Context)
    fun disconnect(context: Context)
}
```

`connect()` must first call `VpnService.prepare()` — this may show a system dialog asking
the user to grant VPN permission. Handle the `ActivityResult`.

### 7. UI

Single screen. Compose.

```
[ status indicator ]   "Connected" / "Disconnected"
[   connect button ]   toggles on tap
```

No navigation, no settings screen.

## Open questions (resolve during implementation)

- Exact JNI/gomobile API surface of `libxray.aar` and `libtun2socks.so` from v2rayNG
  → read their source / inspect the .aar before writing service code
- tun2socks invocation: does it run as a thread, a coroutine, or does it block?
- Notification channel ID and foreground service type declaration (Android 14+ strict)

## What this PoC deliberately omits

- Config UI (import, QR scan)
- Multiple server profiles
- Per-app split tunneling UI
- Kill switch / always-on VPN
- Error recovery / reconnect logic
- Stats (bytes transferred, latency)
