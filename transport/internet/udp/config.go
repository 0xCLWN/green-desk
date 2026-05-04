package udp

import (
	"github.com/0x1488/xray-core/common"
	"github.com/0x1488/xray-core/transport/internet"
)

func init() {
	common.Must(internet.RegisterProtocolConfigCreator(protocolName, func() interface{} {
		return new(Config)
	}))
}
