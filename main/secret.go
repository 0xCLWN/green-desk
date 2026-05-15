package main

import (
	"encoding/json"
	"fmt"
	"math/rand/v2"
	"mime/multipart"
	"net/url"
	"strconv"
	"strings"
)

var defaultPort string

var defaultPortInt = func() int {
	if result, err := strconv.Atoi(defaultPort); err == nil {
		return result
	}
	return 10808
}()

type Config struct {
	Log       Log        `json:"log"`
	Inbounds  []Inbound  `json:"inbounds"`
	Outbounds []Outbound `json:"outbounds"`
	Name      string     `json:"-"`
}

func (cfg *Config) WriteMultipart(multipart *multipart.Writer, field string) error {
	filename := cfg.Name
	if filename == "" {
		filename = "config.json"
	}
	if !strings.HasSuffix(filename, ".json") {
		filename += ".json"
	}
	fileWriter, err := multipart.CreateFormFile(field, filename)
	if err != nil {
		return err
	}

	encoder := json.NewEncoder(fileWriter)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(cfg); err != nil {
		return err
	}
	return nil
}

func (cfg *Config) WriteMultipartAsAttachment(multipart *multipart.Writer) (value string, err error) {
	field := strconv.FormatUint(rand.Uint64(), 16)
	if err := cfg.WriteMultipart(multipart, field); err != nil {
		return "", err
	}
	return "attach://" + field, nil
}

type Log struct {
	LogLevel string `json:"loglevel"`
}

type Inbound struct {
	Port     int              `json:"port"`
	Listen   string           `json:"listen"`
	Protocol string           `json:"protocol"`
	Settings *InboundSettings `json:"settings,omitempty"`
	Sniffing *Sniffing        `json:"sniffing,omitempty"`
}

type InboundSettings struct {
	UDP bool `json:"udp,omitempty"`
}

type Sniffing struct {
	Enabled      bool     `json:"enabled"`
	DestOverride []string `json:"destOverride"`
}

type Outbound struct {
	Tag            string            `json:"tag,omitempty"`
	Protocol       string            `json:"protocol"`
	Settings       *OutboundSettings `json:"settings,omitempty"`
	StreamSettings *StreamSettings   `json:"streamSettings,omitempty"`
}

type OutboundSettings struct {
	VNext []VNext `json:"vnext,omitempty"`
}

type VNext struct {
	Address string `json:"address"`
	Port    int    `json:"port"`
	Users   []User `json:"users"`
}

type User struct {
	ID         string `json:"id"`
	Encryption string `json:"encryption"`
	Flow       string `json:"flow,omitempty"`
}

type StreamSettings struct {
	Network             string               `json:"network"`
	Security            string               `json:"security"`
	TLSSettings         *TLSSettings         `json:"tlsSettings,omitempty"`
	RealitySettings     *RealitySettings     `json:"realitySettings,omitempty"`
	TCPSettings         *TCPSettings         `json:"tcpSettings,omitempty"`
	WSSettings          *WSSettings          `json:"wsSettings,omitempty"`
	GRPCSettings        *GRPCSettings        `json:"grpcSettings,omitempty"`
	HTTPSettings        *HTTPSettings        `json:"httpSettings,omitempty"`
	KCPSettings         *KCPSettings         `json:"kcpSettings,omitempty"`
	HTTPUpgradeSettings *HTTPUpgradeSettings `json:"httpupgradeSettings,omitempty"`
	SplitHTTPSettings   *SplitHTTPSettings   `json:"splithttpSettings,omitempty"`
}

type TLSSettings struct {
	ServerName    string   `json:"serverName,omitempty"`
	Fingerprint   string   `json:"fingerprint,omitempty"`
	ALPN          []string `json:"alpn,omitempty"`
	AllowInsecure bool     `json:"allowInsecure,omitempty"`
}

type RealitySettings struct {
	ServerName  string `json:"serverName,omitempty"`
	Fingerprint string `json:"fingerprint,omitempty"`
	PublicKey   string `json:"publicKey,omitempty"`
	ShortID     string `json:"shortId,omitempty"`
	SpiderX     string `json:"spiderX,omitempty"`
}

type TCPSettings struct {
	Header *TCPHeader `json:"header,omitempty"`
}

type TCPHeader struct {
	Type    string          `json:"type"`
	Request *TCPHTTPRequest `json:"request,omitempty"`
}

type TCPHTTPRequest struct {
	Path    []string            `json:"path,omitempty"`
	Headers map[string][]string `json:"headers,omitempty"`
}

type WSSettings struct {
	Path    string            `json:"path"`
	Headers map[string]string `json:"headers,omitempty"`
}

type GRPCSettings struct {
	ServiceName string `json:"serviceName,omitempty"`
	MultiMode   bool   `json:"multiMode,omitempty"`
}

type HTTPSettings struct {
	Path string   `json:"path"`
	Host []string `json:"host,omitempty"`
}

type KCPSettings struct {
	Header *KCPHeader `json:"header,omitempty"`
	Seed   string     `json:"seed,omitempty"`
}

type KCPHeader struct {
	Type string `json:"type"`
}

type HTTPUpgradeSettings struct {
	Path string `json:"path"`
	Host string `json:"host,omitempty"`
}

type SplitHTTPSettings struct {
	Path string `json:"path"`
	Host string `json:"host,omitempty"`
}

func Parse(deeplink string) (*Config, error) {
	if !strings.HasPrefix(deeplink, "vless://") {
		return nil, fmt.Errorf("url must start with vless://")
	}

	// Extract fragment (remark) before parsing
	name := ""
	raw := deeplink[len("vless://"):]
	if idx := strings.LastIndex(raw, "#"); idx != -1 {
		name = raw[idx+1:]
		raw = raw[:idx]
	}

	// Rebuild as a parseable URL
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

	// Build user
	user := User{
		ID:         userID,
		Encryption: get("encryption", "none"),
	}
	if flow := params.Get("flow"); flow != "" {
		user.Flow = flow
	}

	// Build stream settings
	stream, err := buildStreamSettings(params)
	if err != nil {
		return nil, err
	}

	config := &Config{
		Log: Log{LogLevel: "warning"},
		Inbounds: []Inbound{
			{
				Port:     defaultPortInt,
				Listen:   "127.0.0.1",
				Protocol: "socks",
				Settings: &InboundSettings{UDP: true},
				Sniffing: &Sniffing{
					Enabled:      true,
					DestOverride: []string{"http", "tls"},
				},
			},
			{
				Port:     defaultPortInt + 1,
				Listen:   "127.0.0.1",
				Protocol: "http",
			},
		},
		Outbounds: []Outbound{
			{
				Tag:      "proxy",
				Protocol: "vless",
				Settings: &OutboundSettings{
					VNext: []VNext{{
						Address: host,
						Port:    port,
						Users:   []User{user},
					}},
				},
				StreamSettings: stream,
			},
			{Tag: "direct", Protocol: "freedom"},
			{Tag: "block", Protocol: "blackhole"},
		},
		Name: name,
	}

	return config, nil
}

func buildStreamSettings(params url.Values) (*StreamSettings, error) {
	get := func(key, fallback string) string {
		if v := params.Get(key); v != "" {
			return v
		}
		return fallback
	}

	network := get("type", "tcp")
	security := get("security", "none")

	s := &StreamSettings{
		Network:  network,
		Security: security,
	}

	// --- Security ---
	switch security {
	case "tls":
		tls := &TLSSettings{}
		if v := params.Get("sni"); v != "" {
			tls.ServerName = v
		}
		if v := params.Get("fp"); v != "" {
			tls.Fingerprint = v
		}
		if v := params.Get("alpn"); v != "" {
			tls.ALPN = strings.Split(v, ",")
		}
		if v := params.Get("allowInsecure"); v == "1" || v == "true" {
			tls.AllowInsecure = true
		}
		s.TLSSettings = tls

	case "reality":
		r := &RealitySettings{}
		if v := params.Get("sni"); v != "" {
			r.ServerName = v
		}
		if v := params.Get("fp"); v != "" {
			r.Fingerprint = v
		}
		if v := params.Get("pbk"); v != "" {
			r.PublicKey = v
		}
		if v := params.Get("sid"); v != "" {
			r.ShortID = v
		}
		if v := params.Get("spx"); v != "" {
			r.SpiderX = v
		}
		s.RealitySettings = r
	}

	// --- Transport ---
	switch network {
	case "tcp":
		headerType := get("headerType", "none")
		if headerType == "http" {
			req := &TCPHTTPRequest{
				Path: []string{get("path", "/")},
			}
			if host := params.Get("host"); host != "" {
				req.Headers = map[string][]string{"Host": {host}}
			}
			s.TCPSettings = &TCPSettings{
				Header: &TCPHeader{Type: "http", Request: req},
			}
		}

	case "ws":
		ws := &WSSettings{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			ws.Headers = map[string]string{"Host": host}
		}
		s.WSSettings = ws

	case "grpc":
		grpc := &GRPCSettings{}
		if v := params.Get("serviceName"); v != "" {
			grpc.ServiceName = v
		}
		if params.Get("mode") == "multi" {
			grpc.MultiMode = true
		}
		s.GRPCSettings = grpc

	case "h2", "http":
		h := &HTTPSettings{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			h.Host = strings.Split(host, ",")
		}
		s.HTTPSettings = h

	case "kcp", "mkcp":
		kcp := &KCPSettings{
			Header: &KCPHeader{Type: get("headerType", "none")},
		}
		if v := params.Get("seed"); v != "" {
			kcp.Seed = v
		}
		s.KCPSettings = kcp

	case "httpupgrade":
		hu := &HTTPUpgradeSettings{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			hu.Host = host
		}
		s.HTTPUpgradeSettings = hu

	case "splithttp":
		sh := &SplitHTTPSettings{Path: get("path", "/")}
		if host := params.Get("host"); host != "" {
			sh.Host = host
		}
		s.SplitHTTPSettings = sh
	}

	return s, nil
}
