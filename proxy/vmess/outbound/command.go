package outbound

import (
	"github.com/0x1488/xray-core/common/net"
	"github.com/0x1488/xray-core/common/protocol"
)

// As a stub command consumer.
func (h *Handler) handleCommand(dest net.Destination, cmd protocol.ResponseCommand) {
	switch cmd.(type) {
	default:
	}
}
