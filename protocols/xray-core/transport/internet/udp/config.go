package udp

import (
	"github.com/0xCLWN/xray-core/common"
	"github.com/0xCLWN/xray-core/transport/internet"
)

func init() {
	common.Must(internet.RegisterProtocolConfigCreator(protocolName, func() interface{} {
		return new(Config)
	}))
}
