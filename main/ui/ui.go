package ui

import (
	"io"
	"strings"

	"github.com/0x1488/xray-core/common/cmdarg"
)

// XrayFWMark is set on all outbound sockets so policy routing can exempt
// Xray's own traffic from the TUN interface when system proxy is active.
const XrayFWMark int32 = 255

// Deps carries everything the UI needs from the main package.
type Deps struct {
	ConfigFiles *cmdarg.Arg
	DefaultKey  string // baked-in default (from -X main.defaultConfigFiles=…)
	Port        *int

	// AutoEnable starts the proxy immediately on launch (set via -X main.defaultEnabled=true).
	AutoEnable bool
	// AutoSysProxy also enables system-wide proxy on launch; implies AutoEnable.
	AutoSysProxy bool
	// AutoStartup registers the binary as a login item on launch (macOS only).
	AutoStartup bool

	// StartXray creates and starts the proxy server; returns a handle to close it.
	StartXray    func() (io.Closer, error)
	ValidateKey  func(string) error
	ParseName    func(string) string
	PrintVersion func()
}

// applyDefaults auto-starts and/or enables system proxy based on baked-in defaults.
// startSrv must start the proxy and return true on success.
// setSysProxy enables system proxy and updates any UI state; only called when SystemProxyAvailable().
func (d *Deps) applyDefaults(startSrv func() bool, setSysProxy func()) {
	if !d.AutoEnable && !d.AutoSysProxy {
		return
	}
	if !startSrv() {
		return
	}
	if d.AutoSysProxy && SystemProxyAvailable() {
		setSysProxy()
	}
}

func (d *Deps) activeKey() string {
	if len(*d.ConfigFiles) > 0 && strings.HasPrefix(string((*d.ConfigFiles)[0]), "vless://") {
		return string((*d.ConfigFiles)[0])
	}
	return d.DefaultKey
}
