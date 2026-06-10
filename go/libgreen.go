// Package libgreen wraps xray-core for Android via gomobile.
// Exported names become the Kotlin/Java API: Libgreen.start(), Libgreen.stop(), etc.
package libgreen

import (
	"encoding/json"
	stdnet "net"
	"os"
	"strings"
	"syscall"

	xnet "github.com/0xCLWN/xray-core/common/net"
	"github.com/0xCLWN/xray-core/core"
	"github.com/0xCLWN/xray-core/extra"
	"github.com/0xCLWN/xray-core/infra/conf"
	"github.com/0xCLWN/xray-core/infra/conf/serial"
	"github.com/0xCLWN/xray-core/transport/internet"
)

// Protector is implemented by the Android VpnService to protect outbound sockets
// from being routed back into the VPN tunnel (which would cause a loop).
type Protector interface {
	Protect(fd int32) bool
}

var instance *core.Instance

// SetAssetPath tells xray where to find geoip.dat and geosite.dat.
// Call before Start whenever geo routing rules are present in the config.
func SetAssetPath(path string) {
	os.Setenv("XRAY_LOCATION_ASSET", path)
}

// SetProtector registers the Android socket protector. Call before Start.
func SetProtector(p Protector) error {
	return internet.RegisterDialerController(
		func(network, address string, conn syscall.RawConn) error {
			return conn.Control(func(fd uintptr) {
				p.Protect(int32(fd))
			})
		},
	)
}

// Start starts xray-core with the given JSON configuration string.
func Start(configJSON string) error {
	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return err
	}
	inst, err := core.New(config)
	if err != nil {
		return err
	}
	if err := inst.Start(); err != nil {
		return err
	}
	instance = inst
	return nil
}

// Stop stops xray-core and releases resources.
func Stop() error {
	if instance == nil {
		return nil
	}
	err := instance.Close()
	instance = nil
	return err
}

// Version returns the xray-core version string.
func Version() string {
	return core.Version()
}

// ValidateVlessKey parses key and returns an error if it is not a valid vless:// link.
// No network I/O is performed, so this is safe to call at any time.
func ValidateVlessKey(key string) error {
	_, err := extra.Parse(key)
	return err
}

// VlessKeyDnsServer returns the primary DNS server address for the given vless key.
// Pass this to Android VpnService.Builder.addDnsServer() so the Android OS sends
// DNS through the tunnel rather than to the network's own resolver.
func VlessKeyDnsServer(key string) (string, error) {
	cfg, err := extra.Parse(key)
	if err != nil {
		return "", err
	}
	if cfg.DNSConfig != nil {
		for _, s := range cfg.DNSConfig.Servers {
			if s.Address == nil {
				continue
			}
			addr := s.Address.String()
			// skip localhost / loopback — not usable as Android DNS
			if addr == "localhost" || addr == "127.0.0.1" || addr == "::1" {
				continue
			}
			return addr, nil
		}
	}
	return "1.1.1.1", nil
}

// VlessKeyToXrayJson converts a vless:// link into an xray JSON config string.
// It resolves the server hostname to an IP address so that xray never needs to
// perform a DNS lookup while the VPN tunnel is active (which would deadlock).
// Call this before establishing the TUN interface.
func VlessKeyToXrayJson(key string) (string, error) {
	cfg, err := extra.Parse(key)
	if err != nil {
		return "", err
	}

	// On Android, hev-socks5-tunnel owns all DNS at the packet level.
	// Letting xray intercept DNS too creates a bootstrap deadlock when the
	// VLESS server is identified by domain name.
	cfg.DNSConfig = nil

	// Pre-resolve the VLESS server hostname to an IP while normal DNS is
	// available (must be called before the VPN TUN is established).
	resolveVlessAddresses(cfg)

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func resolveVlessAddresses(cfg *conf.Config) {
	for i, ob := range cfg.OutboundConfigs {
		if ob.Protocol != "vless" || ob.Settings == nil {
			continue
		}
		var settings conf.VLessOutboundConfig
		if err := json.Unmarshal(*ob.Settings, &settings); err != nil {
			continue
		}
		if settings.Address == nil {
			continue
		}
		host := settings.Address.String()
		if stdnet.ParseIP(host) != nil {
			continue // already an IP
		}
		ips, err := stdnet.LookupHost(host)
		if err != nil || len(ips) == 0 {
			continue
		}
		settings.Address = &conf.Address{Address: xnet.ParseAddress(ips[0])}
		raw, err := json.Marshal(settings)
		if err != nil {
			continue
		}
		msg := json.RawMessage(raw)
		cfg.OutboundConfigs[i].Settings = &msg
	}
}
