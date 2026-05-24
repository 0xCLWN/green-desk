# xray

Minimal VLESS proxy client built on [XTLS/Xray-core](https://github.com/XTLS/Xray-core).

Paste a `vless://` key and toggle the proxy on/off from a systray icon (macOS, Windows, Linux) or a terminal UI (Linux). Optionally route all system traffic through it.

---

## Features

- **Systray** (macOS, Windows, Linux+GTK) or **terminal UI** (Linux, no dependencies)
- Toggle proxy on/off with one click / keypress
- Change key and port at runtime — no restart needed
- System-wide proxy routing
  - macOS — `networksetup` (SOCKS5 + HTTP)
  - Linux — TUN interface + policy routing via `tun2socks`
  - Windows — registry (`Internet Settings`)
- Upstream health monitoring — turns icon red and auto-disables system proxy when server is unreachable, restores when it recovers
- Launch at login (macOS)
- Single-instance lock — second launch shows an error instead of corrupting state
- Stale proxy cleanup on startup — safe after crashes and reboots

---

## Ports

| Port                 | Protocol   |
|----------------------|------------|
| 10808 (configurable) | SOCKS5     |
| 10809 (port + 1)     | HTTP proxy |

---

## Quick start

Requires [Go 1.22+](https://go.dev/doc/install).

```sh
go run -tags systray github.com/0x1488/xray-core/main@latest -c 'vless://YOUR_KEY'
```

---

## Building

### macOS — systray

```sh
go build -tags systray \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

### Linux — systray

Install GTK deps first:

```sh
sudo apt install libayatana-appindicator3-dev        # Debian / Ubuntu
sudo dnf install libayatana-appindicator-gtk3-devel  # Fedora
```

```sh
CGO_ENABLED=1 go build -tags systray \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

The binary needs `CAP_NET_ADMIN` to create a TUN interface for system-wide proxy. Either run with `sudo`, or grant it once:

```sh
sudo setcap cap_net_admin+ep ./xray-tray
```

### Linux — terminal UI (static, no system dependencies)

```sh
CGO_ENABLED=0 go build \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

Fully static binary, runs on any x86-64 Linux without GTK or any other library.

### Windows

Run natively on a Windows machine with Go installed:

```sh
go build -tags systray \
  -ldflags '-H windowsgui -s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray.exe ./main
```

`-H windowsgui` suppresses the console window.

---

## Compile-time flags

Bake defaults into the binary with `-ldflags '-X flag=value'`.

| Flag                      | Default  | Description                                          |
|---------------------------|----------|------------------------------------------------------|
| `main.defaultConfigFiles` | —        | `vless://` key baked into the binary                 |
| `main.defaultPort`        | `10808`  | SOCKS5 listen port                                   |
| `main.defaultEnabled`     | —        | `true` — start proxy automatically on launch         |
| `main.defaultSysProxy`    | —        | `true` — also enable system-wide proxy on launch     |
| `main.defaultStartup`     | —        | `true` — register as a login item on launch (macOS)  |

---

## Recipes

### Just a proxy, no UI fluff

Pass the key at runtime and skip baking anything in:

```sh
./xray-tray -c 'vless://YOUR_KEY'
```

### Binary that starts and connects automatically

```sh
go build -tags systray \
  -ldflags '-s -w
    -X main.defaultConfigFiles=vless://YOUR_KEY
    -X main.defaultEnabled=true' \
  -o xray-tray ./main
```

### Full auto — connect + system proxy on launch

```sh
go build -tags systray \
  -ldflags '-s -w
    -X main.defaultConfigFiles=vless://YOUR_KEY
    -X main.defaultEnabled=true
    -X main.defaultSysProxy=true' \
  -o xray-tray ./main
```

### Auto-start on login (macOS)

Add `defaultStartup=true` to any of the above. On first launch the binary registers itself as a login item (visible under System Settings → General → Login Items → Allow in Background). Toggle it from the systray menu anytime.

```sh
go build -tags systray \
  -ldflags '-s -w
    -X main.defaultConfigFiles=vless://YOUR_KEY
    -X main.defaultEnabled=true
    -X main.defaultSysProxy=true
    -X main.defaultStartup=true' \
  -o xray-tray ./main
```

### Custom SOCKS5 port

```sh
go build -tags systray \
  -ldflags '-s -w
    -X main.defaultConfigFiles=vless://YOUR_KEY
    -X main.defaultPort=1080' \
  -o xray-tray ./main
```

### Linux system-wide proxy without sudo every time

```sh
CGO_ENABLED=0 go build \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main

sudo setcap cap_net_admin+ep ./xray-tray
./xray-tray
```

---

## Troubleshooting

### Linux — no internet after a crash

If the process is killed hard (SIGKILL, power loss) while system proxy is active, the TUN interface disappears automatically but the policy routing rules may linger and black-hole traffic. One command fixes it:

```sh
sudo ip rule del priority 1000; sudo ip rule del priority 100; true
```

If `tun0` somehow stuck around too:

```sh
sudo ip link del tun0
```

---

## Terminal UI keys

| Key | Action |
|-----|--------|
| `E` | Enable / disable proxy |
| `K` | Change VLESS key |
| `P` | Change SOCKS5 port |
| `S` | Toggle system-wide proxy |
| `Q` / `Ctrl+C` | Quit (cleans up system proxy) |
| `↑` `↓` | Scroll log |
