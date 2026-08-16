package all

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"

	xnet "github.com/0xCLWN/xray-core/common/net"
	"github.com/0xCLWN/xray-core/extra"
	"github.com/0xCLWN/xray-core/infra/conf"
	"github.com/0xCLWN/xray-core/main/commands/base"
)

var cmdKey2Json = &base.Command{
	UsageLine: "{{.Exec}} key2json",
	Short:     "Convert a vless:// URI to an xray JSON config",
}

var (
	k2jSocksPort = cmdKey2Json.Flag.Int("socks-port", 10808, "SOCKS5 listen port")
	k2jHttpPort  = cmdKey2Json.Flag.Int("http-port", 10809, "HTTP proxy listen port")
	k2jApiPort   = cmdKey2Json.Flag.Int("api-port", 0, "gRPC API port (0 = disabled)")
)

func init() {
	cmdKey2Json.Run = execKey2Json
}

func execKey2Json(cmd *base.Command, args []string) {
	// Accept URI either as a positional arg or via stdin (preferred — keeps URI off the process list).
	var uri string
	if len(args) >= 1 {
		uri = args[0]
	} else {
		data, err := io.ReadAll(os.Stdin)
		if err != nil {
			base.Fatalf("reading stdin: %v", err)
		}
		uri = strings.TrimSpace(string(data))
	}
	if uri == "" {
		base.Fatalf("usage: key2json [--socks-port N] [--http-port N] [--api-port N] <vless://...>")
	}

	cfg, err := extra.Parse(uri)
	if err != nil {
		base.Fatalf("%v", err)
	}

	for i := range cfg.InboundConfigs {
		switch cfg.InboundConfigs[i].Protocol {
		case "socks":
			cfg.InboundConfigs[i].PortList = k2jPortList(*k2jSocksPort)
		case "http":
			cfg.InboundConfigs[i].PortList = k2jPortList(*k2jHttpPort)
		}
	}

	if *k2jApiPort > 0 {
		apiSettings := json.RawMessage(`{"address":"127.0.0.1"}`)
		cfg.InboundConfigs = append(cfg.InboundConfigs, conf.InboundDetourConfig{
			Tag:      "api",
			Protocol: "dokodemo-door",
			PortList: k2jPortList(*k2jApiPort),
			ListenOn: &conf.Address{Address: xnet.ParseAddress("127.0.0.1")},
			Settings: &apiSettings,
		})
		cfg.API = &conf.APIConfig{
			Tag:      "api",
			Services: []string{"HandlerService", "StatsService", "LoggerService"},
		}
		cfg.Stats = &conf.StatsConfig{}

		apiRule, _ := json.Marshal(map[string]any{
			"type":        "field",
			"inboundTag":  []string{"api"},
			"outboundTag": "api",
		})
		cfg.RouterConfig.RuleList = append([]json.RawMessage{apiRule}, cfg.RouterConfig.RuleList...)
	}

	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		base.Fatalf("marshal: %v", err)
	}
	fmt.Println(string(out))
}

func k2jPortList(port int) *conf.PortList {
	p := uint32(port)
	return &conf.PortList{Range: []conf.PortRange{{From: p, To: p}}}
}
