//go:build linux

package ui

import (
	"fmt"
	"os"
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
	if !NetAdminAvailable() {
		return fmt.Errorf(
			"system proxy requires root or CAP_NET_ADMIN\n\n" +
				"Run with sudo, or grant the capability once:\n" +
				"  sudo setcap cap_net_admin+ep ./xray-tray",
		)
	}
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

func NetAdminAvailable() bool {
	if os.Getuid() == 0 {
		return true
	}
	data, err := os.ReadFile("/proc/self/status")
	if err != nil {
		return false
	}
	for _, line := range strings.Split(string(data), "\n") {
		if !strings.HasPrefix(line, "CapEff:") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			return false
		}
		var caps uint64
		fmt.Sscanf(fields[1], "%x", &caps)
		return caps&(1<<12) != 0 // CAP_NET_ADMIN = 12
	}
	return false
}

// CleanupStaleProxy removes any TUN interface and routing rules left over from
// a previous run that crashed or was killed.  The TUN vanishes on reboot anyway,
// but within the same session a crash leaves it behind and breaks networking.
func CleanupStaleProxy(_ int) {
	if exec.Command("ip", "link", "show", tunDev).Run() == nil {
		DisableSystemProxy()
	}
}
