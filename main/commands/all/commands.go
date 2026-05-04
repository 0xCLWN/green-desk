package all

import (
	"github.com/0x1488/xray-core/main/commands/all/api"
	"github.com/0x1488/xray-core/main/commands/all/convert"
	"github.com/0x1488/xray-core/main/commands/all/tls"
	"github.com/0x1488/xray-core/main/commands/base"
)

func init() {
	base.RootCommand.Commands = append(
		base.RootCommand.Commands,
		api.CmdAPI,
		convert.CmdConvert,
		tls.CmdTLS,
		cmdUUID,
		cmdX25519,
		cmdWG,
		cmdMLDSA65,
		cmdMLKEM768,
		cmdVLESSEnc,
	)
}
