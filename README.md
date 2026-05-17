# xray

Minimal VLESS proxy client. Paste a `vless://` key, toggle on/off, optionally route all traffic through it.

Built on [XTLS/Xray-core](https://github.com/XTLS/Xray-core).

## Ports

| Port                 | Protocol   |
|----------------------|------------|
| 10808 (configurable) | SOCKS5     |
| 10809 (port + 1)     | HTTP proxy |

## Quick start

Requires [Go 1.26+](https://go.dev/doc/install).

```sh
go run -tags systray github.com/0x1488/xray-core/main@latest -c 'vless://YOUR_KEY'
```

## Build

Replace `vless://YOUR_KEY` with your key.

### macOS

```sh
go build -tags systray \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

### Linux — systray

Install GTK deps first:

```sh
sudo apt install libayatana-appindicator3-dev   # Debian/Ubuntu
sudo dnf install libayatana-appindicator-gtk3-devel  # Fedora
```

```sh
CGO_ENABLED=1 go build -tags systray \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

For system-wide proxy (TUN mode): `go install github.com/xjasonlyu/tun2socks/v2@latest`

### Linux — TUI (static, no dependencies)

```sh
CGO_ENABLED=0 go build \
  -ldflags '-s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray ./main
```

### Windows

Run natively on a Windows machine with Go installed:

```sh
go build -tags systray \
  -ldflags '-H windowsgui -s -w -X main.defaultConfigFiles=vless://YOUR_KEY' \
  -o xray-tray.exe ./main
```

## Compile-time flags

| Flag                      | Default | Description                                               |
|---------------------------|---------|-----------------------------------------------------------|
| `main.defaultConfigFiles` | —       | `vless://` key baked into the binary                      |
| `main.defaultPort`        | `10808` | SOCKS5 listen port                                        |
| `main.defaultEnabled`     | —       | `true` = start proxy on launch                            |
| `main.defaultSysProxy`    | —       | `true` = start proxy + enable system-wide proxy on launch |

## Runtime

Key and port can be changed at runtime via the UI. To pass a key without baking it in:

```sh
./xray-tray -c 'vless://YOUR_KEY'
```
