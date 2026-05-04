//go:build wasm || openbsd
// +build wasm openbsd

package buf

import (
	"io"
	"syscall"

	"github.com/0x1488/xray-core/features/stats"
)

const useReadv = false

func NewReadVReader(reader io.Reader, rawConn syscall.RawConn, counter stats.Counter) Reader {
	panic("not implemented")
}
