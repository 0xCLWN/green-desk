package conf_test

import (
	"testing"

	. "github.com/0x1488/xray-core/infra/conf"
	"github.com/0x1488/xray-core/proxy/http"
)

func TestHTTPServerConfig(t *testing.T) {
	creator := func() Buildable {
		return new(HTTPServerConfig)
	}

	runMultiTestCase(t, []TestCase{
		{
			Input: `{
				"accounts": [
					{
						"user": "my-username",
						"pass": "my-password"
					}
				],
				"allowTransparent": true,
				"userLevel": 1
			}`,
			Parser: loadJSON(creator),
			Output: &http.ServerConfig{
				Accounts: map[string]string{
					"my-username": "my-password",
				},
				AllowTransparent: true,
				UserLevel:        1,
			},
		},
	})
}
