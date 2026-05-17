//go:build darwin

package ui

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"
)

// macServices returns all enabled network services (skips lines prefixed with "*").
func macServices() ([]string, error) {
	out, err := exec.Command("networksetup", "-listallnetworkservices").Output()
	if err != nil {
		return nil, err
	}
	var svcs []string
	sc := bufio.NewScanner(strings.NewReader(string(out)))
	sc.Scan() // skip header ("An asterisk (*) denotes that a network service is disabled.")
	for sc.Scan() {
		if line := sc.Text(); line != "" && !strings.HasPrefix(line, "*") {
			svcs = append(svcs, line)
		}
	}
	return svcs, nil
}

// runNS runs networksetup with the given args.  If the command fails (usually
// a permissions error), it retries via osascript which shows the macOS
// administrator password dialog.
func runNS(args ...string) error {
	if exec.Command("networksetup", args...).Run() == nil {
		return nil
	}
	// Shell-quote each argument with single quotes (POSIX-safe).
	parts := make([]string, len(args)+1)
	parts[0] = "networksetup"
	for i, a := range args {
		parts[i+1] = "'" + strings.ReplaceAll(a, "'", `'\''`) + "'"
	}
	shellCmd := strings.Join(parts, " ")
	// Embed in AppleScript do shell script, escaping inner double-quotes.
	script := `do shell script "` + strings.ReplaceAll(shellCmd, `"`, `\"`) + `" with administrator privileges`
	return exec.Command("osascript", "-e", script).Run()
}

// EnableSystemProxy configures all active network services to route traffic
// through Xray's SOCKS5 proxy (socksPort) and HTTP proxy (socksPort+1).
func EnableSystemProxy(socksPort int) error {
	svcs, err := macServices()
	if err != nil {
		return fmt.Errorf("list network services: %w", err)
	}
	if len(svcs) == 0 {
		return fmt.Errorf("no active network services found")
	}

	host := "127.0.0.1"
	sp := fmt.Sprint(socksPort)
	hp := fmt.Sprint(socksPort + 1)

	for _, svc := range svcs {
		if err := runNS("-setsocksfirewallproxy", svc, host, sp); err != nil {
			return fmt.Errorf("SOCKS proxy on %q: %w", svc, err)
		}
		runNS("-setsocksfirewallproxystate", svc, "on")
		// HTTP proxy handles HTTPS via CONNECT tunneling — no Secure Web Proxy needed.
		// Setting -setsecurewebproxy causes macOS to export https://host:port to clients,
		// which makes curl/brew try to SSL-handshake with the proxy itself and fail.
		runNS("-setwebproxy", svc, host, hp)
		runNS("-setwebproxystate", svc, "on")
	}
	return nil
}

// DisableSystemProxy clears the proxy settings on all active network services.
func DisableSystemProxy() {
	svcs, _ := macServices()
	for _, svc := range svcs {
		runNS("-setsocksfirewallproxystate", svc, "off")
		runNS("-setwebproxystate", svc, "off")
	}
}

func SystemProxyAvailable() bool { return true }

// CleanupStaleProxy runs at startup and removes any proxy settings that were
// left behind by a previous run that crashed, was force-killed, or lost power.
// macOS persists networksetup settings across reboots, so without this the user
// would have no internet after an ungraceful exit.
func CleanupStaleProxy(port int) {
	svcs, err := macServices()
	if err != nil {
		return
	}
	for _, svc := range svcs {
		if socksProxyIsOurs(svc, port) {
			DisableSystemProxy()
			return
		}
	}
}

// socksProxyIsOurs returns true when the SOCKS5 proxy on svc is enabled and
// pointing at 127.0.0.1:<port> — i.e. the settings we wrote.
func socksProxyIsOurs(svc string, port int) bool {
	out, err := exec.Command("networksetup", "-getsocksfirewallproxy", svc).Output()
	if err != nil {
		return false
	}
	// Output looks like:
	//   Enabled: Yes
	//   Server: 127.0.0.1
	//   Port: 10808
	//   Authenticated Proxy Enabled: 0
	enabled, serverOK, portOK := false, false, false
	sc := bufio.NewScanner(strings.NewReader(string(out)))
	for sc.Scan() {
		line := sc.Text()
		switch {
		case line == "Enabled: Yes":
			enabled = true
		case line == "Server: 127.0.0.1":
			serverOK = true
		case line == "Port: "+fmt.Sprint(port):
			portOK = true
		}
	}
	return enabled && serverOK && portOK
}
