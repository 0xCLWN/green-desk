package libgreen

// Blank imports register xray-core's protocol and transport handlers via init().
// Without these, the JSON config loader won't know about the protocols.
import (
	// Core services
	_ "github.com/0xCLWN/xray-core/app/dispatcher"
	_ "github.com/0xCLWN/xray-core/app/proxyman/inbound"
	_ "github.com/0xCLWN/xray-core/app/proxyman/outbound"

	// Inbound protocols
	_ "github.com/0xCLWN/xray-core/proxy/socks"

	// Outbound protocols
	_ "github.com/0xCLWN/xray-core/proxy/freedom"
	_ "github.com/0xCLWN/xray-core/proxy/shadowsocks"
	_ "github.com/0xCLWN/xray-core/proxy/trojan"
	_ "github.com/0xCLWN/xray-core/proxy/vless/outbound"
	_ "github.com/0xCLWN/xray-core/proxy/vmess/outbound"

	// Transports
	_ "github.com/0xCLWN/xray-core/transport/internet/grpc"
	_ "github.com/0xCLWN/xray-core/transport/internet/reality"
	_ "github.com/0xCLWN/xray-core/transport/internet/tcp"
	_ "github.com/0xCLWN/xray-core/transport/internet/tls"
	_ "github.com/0xCLWN/xray-core/transport/internet/websocket"
)
