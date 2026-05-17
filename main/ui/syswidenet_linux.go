//go:build linux

package ui

import (
	"fmt"
	"os/exec"
	"strings"
)

const (
	tunDev     = "tun0"
	tunCIDR    = "198.18.0.1/15" // RFC 5737 reserved — won't clash with real LANs
	rtTable    = "100"
	rtPrioTUN  = "1000"
	rtPrioMark = "100"
)

var sysTun2socksCmd *exec.Cmd

// EnableSystemProxy creates a TUN interface and configures policy routing so
// all traffic flows through it, then forwards packets to Xray's SOCKS5 via
// tun2socks.  Xray's own sockets carry fwmark=XrayFWMark and are routed via
// the main table (real gateway), breaking the forwarding loop.
func EnableSystemProxy(socksPort int) error {
	if _, err := exec.LookPath("tun2socks"); err != nil {
		return fmt.Errorf(
			"tun2socks not found in PATH\n\n" +
				"Install: go install github.com/xjasonlyu/tun2socks/v2@latest\n" +
				"Then make sure ~/go/bin is in your PATH.",
		)
	}

	run := func(args ...string) error {
		out, err := exec.Command(args[0], args[1:]...).CombinedOutput()
		if err != nil {
			return fmt.Errorf("%s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
		}
		return nil
	}

	steps := []func() error{
		func() error { return run("ip", "tuntap", "add", "mode", "tun", "dev", tunDev) },
		func() error { return run("ip", "addr", "add", tunCIDR, "dev", tunDev) },
		func() error { return run("ip", "link", "set", tunDev, "up") },
		func() error { return run("ip", "route", "add", "default", "dev", tunDev, "table", rtTable) },
		func() error {
			return run("ip", "rule", "add",
				"fwmark", fmt.Sprint(XrayFWMark),
				"table", "main",
				"priority", rtPrioMark)
		},
		func() error { return run("ip", "rule", "add", "table", rtTable, "priority", rtPrioTUN) },
	}

	for _, step := range steps {
		if err := step(); err != nil {
			DisableSystemProxy()
			return err
		}
	}

	sysTun2socksCmd = exec.Command("tun2socks",
		"-device", tunDev,
		"-proxy", fmt.Sprintf("socks5://127.0.0.1:%d", socksPort),
		"-loglevel", "warning",
	)
	if err := sysTun2socksCmd.Start(); err != nil {
		DisableSystemProxy()
		return fmt.Errorf("tun2socks: %w", err)
	}
	return nil
}

func DisableSystemProxy() {
	if sysTun2socksCmd != nil && sysTun2socksCmd.Process != nil {
		_ = sysTun2socksCmd.Process.Kill()
		_ = sysTun2socksCmd.Wait()
		sysTun2socksCmd = nil
	}
	exec.Command("ip", "rule", "del", "table", rtTable, "priority", rtPrioTUN).Run()
	exec.Command("ip", "rule", "del",
		"fwmark", fmt.Sprint(XrayFWMark),
		"table", "main",
		"priority", rtPrioMark).Run()
	exec.Command("ip", "link", "del", tunDev).Run()
}

func SystemProxyAvailable() bool { return true }

// CleanupStaleProxy removes any TUN interface and routing rules left over from
// a previous run that crashed or was killed.  The TUN vanishes on reboot anyway,
// but within the same session a crash leaves it behind and breaks networking.
func CleanupStaleProxy(_ int) {
	if exec.Command("ip", "link", "show", tunDev).Run() == nil {
		DisableSystemProxy()
	}
}
