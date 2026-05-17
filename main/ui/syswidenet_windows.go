//go:build windows

package ui

import (
	"fmt"
	"strings"
	"syscall"

	"golang.org/x/sys/windows/registry"
)

const (
	internetOptionSettingsChanged = 39
	internetOptionRefresh         = 37
)

var (
	wininet            = syscall.NewLazyDLL("wininet.dll")
	internetSetOptionW = wininet.NewProc("InternetSetOptionW")
)

func notifyWininet() {
	internetSetOptionW.Call(0, internetOptionSettingsChanged, 0, 0)
	internetSetOptionW.Call(0, internetOptionRefresh, 0, 0)
}

func openInternetSettings(access uint32) (registry.Key, error) {
	return registry.OpenKey(registry.CURRENT_USER, `Software\Microsoft\Windows\CurrentVersion\Internet Settings`, access)
}

func SystemProxyAvailable() bool { return true }

func EnableSystemProxy(socksPort int) error {
	k, err := openInternetSettings(registry.SET_VALUE)
	if err != nil {
		return fmt.Errorf("open registry: %w", err)
	}
	defer k.Close()

	httpPort := socksPort + 1
	proxyServer := fmt.Sprintf("http=127.0.0.1:%d;https=127.0.0.1:%d;socks=127.0.0.1:%d", httpPort, httpPort, socksPort)

	if err := k.SetDWordValue("ProxyEnable", 1); err != nil {
		return fmt.Errorf("set ProxyEnable: %w", err)
	}
	if err := k.SetStringValue("ProxyServer", proxyServer); err != nil {
		return fmt.Errorf("set ProxyServer: %w", err)
	}
	if err := k.SetStringValue("ProxyOverride", "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>"); err != nil {
		return fmt.Errorf("set ProxyOverride: %w", err)
	}
	notifyWininet()
	return nil
}

func DisableSystemProxy() {
	k, err := openInternetSettings(registry.SET_VALUE)
	if err != nil {
		return
	}
	defer k.Close()
	_ = k.SetDWordValue("ProxyEnable", 0)
	notifyWininet()
}

func CleanupStaleProxy(port int) {
	k, err := openInternetSettings(registry.QUERY_VALUE)
	if err != nil {
		return
	}
	defer k.Close()

	enabled, _, err := k.GetIntegerValue("ProxyEnable")
	if err != nil || enabled == 0 {
		return
	}

	server, _, err := k.GetStringValue("ProxyServer")
	if err != nil {
		return
	}

	// Check if the proxy server string contains our SOCKS or HTTP port.
	if strings.Contains(server, fmt.Sprintf(":%d", port)) ||
		strings.Contains(server, fmt.Sprintf(":%d", port+1)) {
		DisableSystemProxy()
	}
}
