package main

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
	"strings"

	xnet "github.com/0x1488/xray-core/common/net"
	"github.com/0x1488/xray-core/infra/conf"
)

var defaultPort string

var defaultPortInt = func() int {
	if result, err := strconv.Atoi(defaultPort); err == nil {
		return result
	}
	return 10808
}()

func Parse(deeplink string) (*conf.Config, error) {
	if !strings.HasPrefix(deeplink, "vless://") {
		return nil, fmt.Errorf("url must start with vless://")
	}

	raw := deeplink[len("vless://"):]
	if idx := strings.LastIndex(raw, "#"); idx != -1 {
		raw = raw[:idx]
	}

	u, err := url.Parse("vless://" + raw)
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}

	userID := u.User.Username()
	if userID == "" {
		return nil, fmt.Errorf("missing uuid")
	}

	host := u.Hostname()
	portStr := u.Port()
	if host == "" || portStr == "" {
		return nil, fmt.Errorf("missing address or port")
	}

	port, err := strconv.Atoi(portStr)
	if err != nil {
		return nil, fmt.Errorf("invalid port: %w", err)
	}

	params := u.Query()
	get := func(key, fallback string) string {
		if v := params.Get(key); v != "" {
			return v
		}
		return fallback
	}

	stream, err := buildConfStream(params)
	if err != nil {
		return nil, err
	}

	vlessSettings := conf.VLessOutboundConfig{
		Address:    address(host),
		Port:       uint16(port),
		Id:         userID,
		Encryption: get("encryption", "none"),
		Flow:       params.Get("flow"),
	}
	vlessRaw, err := json.Marshal(vlessSettings)
	if err != nil {
		return nil, fmt.Errorf("marshal vless settings: %w", err)
	}
	vlessMsg := json.RawMessage(vlessRaw)

	socksSettings := conf.SocksServerConfig{UDP: true}
	socksRaw, err := json.Marshal(socksSettings)
	if err != nil {
		return nil, fmt.Errorf("marshal socks settings: %w", err)
	}
	socksMsg := json.RawMessage(socksRaw)

	destOverride := conf.StringList{"http", "tls"}

	return &conf.Config{
		LogConfig:    &conf.LogConfig{LogLevel: "warning"},
		DNSConfig:    buildDNSConfig(),
		RouterConfig: buildRouterConfig(),
		InboundConfigs: []conf.InboundDetourConfig{
			{
				Protocol: "socks",
				PortList: portList(defaultPortInt),
				ListenOn: address("127.0.0.1"),
				Settings: &socksMsg,
				SniffingConfig: &conf.SniffingConfig{
					Enabled:      true,
					DestOverride: destOverride,
				},
			},
			{
				Protocol: "http",
				PortList: portList(defaultPortInt + 1),
				ListenOn: address("127.0.0.1"),
			},
		},
		OutboundConfigs: []conf.OutboundDetourConfig{
			{
				Tag:           "proxy",
				Protocol:      "vless",
				Settings:      &vlessMsg,
				StreamSetting: stream,
			},
			{Tag: "direct", Protocol: "freedom"},
			{Tag: "block", Protocol: "blackhole"},
		},
	}, nil
}

func buildConfStream(params url.Values) (*conf.StreamConfig, error) {
	get := func(key, fallback string) string {
		if v := params.Get(key); v != "" {
			return v
		}
		return fallback
	}

	network := conf.TransportProtocol(get("type", "tcp"))
	security := get("security", "none")

	s := &conf.StreamConfig{
		Network:  &network,
		Security: security,
	}

	switch security {
	case "tls":
		tls := &conf.TLSConfig{
			ServerName:    params.Get("sni"),
			Fingerprint:   params.Get("fp"),
			AllowInsecure: params.Get("allowInsecure") == "1" || params.Get("allowInsecure") == "true",
		}
		if v := params.Get("alpn"); v != "" {
			sl := conf.StringList(strings.Split(v, ","))
			tls.ALPN = &sl
		}
		s.TLSSettings = tls

	case "reality":
		s.REALITYSettings = &conf.REALITYConfig{
			ServerName:  params.Get("sni"),
			Fingerprint: params.Get("fp"),
			PublicKey:   params.Get("pbk"),
			ShortId:     params.Get("sid"),
			SpiderX:     params.Get("spx"),
		}
	}

	switch string(network) {
	case "tcp":
		if get("headerType", "none") == "http" {
			var headers map[string]*conf.StringList
			if host := params.Get("host"); host != "" {
				sl := conf.StringList{host}
				headers = map[string]*conf.StringList{"Host": &sl}
			}
			type httpHeader struct {
				Type    string                    `json:"type"`
				Request conf.AuthenticatorRequest `json:"request"`
			}
			headerJSON, err := json.Marshal(httpHeader{
				Type: "http",
				Request: conf.AuthenticatorRequest{
					Path:    conf.StringList{get("path", "/")},
					Headers: headers,
				},
			})
			if err != nil {
				return nil, err
			}
			s.TCPSettings = &conf.TCPConfig{HeaderConfig: json.RawMessage(headerJSON)}
		}

	case "ws", "websocket":
		ws := &conf.WebSocketConfig{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			ws.Host = host
		}
		s.WSSettings = ws

	case "grpc":
		grpc := &conf.GRPCConfig{ServiceName: params.Get("serviceName")}
		if params.Get("mode") == "multi" {
			grpc.MultiMode = true
		}
		s.GRPCSettings = grpc

	case "kcp", "mkcp":
		s.KCPSettings = &conf.KCPConfig{}

	case "httpupgrade":
		hu := &conf.HttpUpgradeConfig{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			hu.Host = host
		}
		s.HTTPUPGRADESettings = hu

	case "splithttp", "xhttp":
		sh := &conf.SplitHTTPConfig{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			sh.Host = host
		}
		s.SplitHTTPSettings = sh
	}

	return s, nil
}

func buildDNSConfig() *conf.DNSConfig {
	return &conf.DNSConfig{
		Servers: []*conf.NameServerConfig{
			// remote DNS goes through the proxy — no ISP snooping
			{Address: address("8.8.8.8"), Tag: "proxy"},
			// system DNS as last-resort fallback (e.g. when proxy is unreachable)
			{Address: address("localhost")},
		},
	}
}

func buildRouterConfig() *conf.RouterConfig {
	// route all private / loopback / link-local ranges directly so LAN and
	// localhost traffic never touches the proxy
	type fieldRule struct {
		Type        string   `json:"type"`
		OutboundTag string   `json:"outboundTag"`
		IP          []string `json:"ip"`
	}
	rule, _ := json.Marshal(fieldRule{
		Type:        "field",
		OutboundTag: "direct",
		IP: []string{
			"10.0.0.0/8",
			"172.16.0.0/12",
			"192.168.0.0/16",
			"100.64.0.0/10", // carrier-grade NAT (RFC 6598)
			"127.0.0.0/8",
			"169.254.0.0/16", // link-local
			"::1/128",
			"fc00::/7",
			"fe80::/10",
		},
	})

	// IPIfNonMatch: if a domain rule doesn't match, resolve the domain and
	// re-check against IP rules — ensures private-IP domains also go direct
	ds := "IPIfNonMatch"
	return &conf.RouterConfig{
		DomainStrategy: &ds,
		RuleList:       []json.RawMessage{rule},
	}
}

func portList(port int) *conf.PortList {
	p := uint32(port)
	return &conf.PortList{Range: []conf.PortRange{{From: p, To: p}}}
}

func address(s string) *conf.Address {
	return &conf.Address{Address: xnet.ParseAddress(s)}
}
