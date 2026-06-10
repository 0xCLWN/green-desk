# Green VPN — Architecture

## Packet flow (Chrome → internet)

```
Chrome (com.android.chrome)
         ↓
   Android kernel
   VpnService routes traffic by UID:
     - allowed apps  → tun0
     - bypassed apps → direct to internet
         ↓
       tun0
   (raw IP packets via ParcelFileDescriptor)
         ↓
     tun2socks
   Userspace TCP/IP stack.
   Reads raw packets off the tun0 fd,
   reconstructs TCP/UDP connections,
   forwards them as SOCKS5 requests.
         ↓
   127.0.0.1:10808
   (xray-core SOCKS5 inbound)
         ↓
      xray-core
   Applies routing rules and outbound protocol
   (VLESS / VMess / Trojan / etc.)
         ↓
   remote proxy server
         ↓
      internet
```

## Per-app routing (split tunneling)

Android's `VpnService.Builder` supports UID-based routing at the kernel level.

```kotlin
// whitelist — only these apps go through VPN, everything else is direct
builder.addAllowedApplication("com.android.chrome")

// blacklist — these apps bypass VPN, everything else goes through
builder.addDisallowedApplication("com.spotify.music")
```

- Cannot mix both modes in the same builder — pick one.
- Whitelist is the recommended UX: nothing is proxied by default, user opts apps in.
- Package names are resolved to Linux UIDs at build time. Routing is enforced by the kernel per-UID,
  not per-package-name string.
- A malicious app cannot spoof an installed app's package name — Android enforces uniqueness by
  signing key.
- If an app is not installed, its package name has no UID and routing rules for it are inert. Build
  the list dynamically from `PackageManager`, never hardcode.

## Components

| Component    | Role                                         | Source                                   |
|--------------|----------------------------------------------|------------------------------------------|
| `VpnService` | Creates tun0, sets routing rules per app UID | Android SDK                              |
| tun2socks    | Translates raw IP packets → SOCKS5           | Prebuilt `.so` from v2rayNG or own build |
| xray-core    | Proxy core, handles outbound protocol        | `libxray.aar` via gomobile               |

## Key files (planned)

| File                                | Purpose                                     |
|-------------------------------------|---------------------------------------------|
| `VpnService` subclass               | Owns the tun0 fd, starts tun2socks and xray |
| `VpnViewModel`                      | UI state, connect/disconnect commands       |
| `assets/config.json`                | xray config (bundled for PoC)               |
| `jniLibs/arm64-v8a/libtun2socks.so` | Prebuilt tun2socks native library           |
