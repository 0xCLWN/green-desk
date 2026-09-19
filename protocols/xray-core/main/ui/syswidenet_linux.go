//go:build linux

package ui

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

const (
	tunDev     = "tun0"
	tunCIDR    = "198.18.0.1/15"
	rtTable    = "100"
	rtPrioTUN  = "1000"
	rtPrioMark = "100"
)

var tunRunning bool

func EnableSystemProxy(socksPort int) error {
	if !NetAdminAvailable() {
		return fmt.Errorf(
			"system proxy requires root or CAP_NET_ADMIN\n\n" +
				"Run with sudo, or grant the capability once:\n" +
				"  sudo setcap cap_net_admin+ep ./xray-tray",
		)
	}

	run := func(args ...string) error {
		out, err := exec.Command(args[0], args[1:]...).CombinedOutput()
		if err != nil {
			return fmt.Errorf("%s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
		}
		return nil
	}

	engine.Insert(&engine.Key{
		Device:   "tun://" + tunDev,
		Proxy:    fmt.Sprintf("socks5://127.0.0.1:%d", socksPort),
		LogLevel: "silent",
		Mark:     int(XrayFWMark),
	})
	engine.Start()

	// Start() creates the TUN synchronously; give the kernel a moment to surface it.
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if exec.Command("ip", "link", "show", tunDev).Run() == nil {
			break
		}
		time.Sleep(50 * time.Millisecond)
	}
	if exec.Command("ip", "link", "show", tunDev).Run() != nil {
		engine.Stop()
		return fmt.Errorf("tun2socks: TUN interface %s did not appear", tunDev)
	}

	steps := []func() error{
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

	tunRunning = true
	return nil
}

func DisableSystemProxy() {
	exec.Command("ip", "rule", "del", "table", rtTable, "priority", rtPrioTUN).Run()
	exec.Command("ip", "rule", "del",
		"fwmark", fmt.Sprint(XrayFWMark),
		"table", "main",
		"priority", rtPrioMark).Run()
	if tunRunning {
		engine.Stop()
		tunRunning = false
	}
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
		return caps&(1<<12) != 0
	}
	return false
}

// CleanupStaleProxy removes routing rules left over from a previous crash.
// The TUN interface itself vanishes automatically when the process that owned
// the fd dies, so only the ip rules need cleaning up.
func CleanupStaleProxy(_ int) {
	exec.Command("ip", "rule", "del", "table", rtTable, "priority", rtPrioTUN).Run()
	exec.Command("ip", "rule", "del",
		"fwmark", fmt.Sprint(XrayFWMark),
		"table", "main",
		"priority", rtPrioMark).Run()
}
