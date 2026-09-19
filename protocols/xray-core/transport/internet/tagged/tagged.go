package tagged

import (
	"context"

	"github.com/0xCLWN/xray-core/common/net"
	"github.com/0xCLWN/xray-core/features/routing"
)

type DialFunc func(ctx context.Context, dispatcher routing.Dispatcher, dest net.Destination, tag string) (net.Conn, error)

var Dialer DialFunc
