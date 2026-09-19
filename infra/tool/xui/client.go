package xui

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	base     string
	basePath string // e.g. "/a3f1b2c4" — prepended to every request path
	hc       *http.Client
	bearer   string
}

type apiResp[T any] struct {
	Success bool   `json:"success"`
	Msg     string `json:"msg"`
	Obj     T      `json:"obj"`
}

// InboundObj is the 3x-ui inbound structure.
// Settings, StreamSettings, and Sniffing are serialised JSON strings,
// as the 3x-ui API requires them that way.
type InboundObj struct {
	ID             int    `json:"id,omitempty"`
	Remark         string `json:"remark"`
	Enable         bool   `json:"enable"`
	Protocol       string `json:"protocol"`
	Port           int    `json:"port"`
	Listen         string `json:"listen"`
	Tag            string `json:"tag"`
	Settings       string `json:"settings"`
	StreamSettings string `json:"streamSettings"`
	Sniffing       string `json:"sniffing"`
}

func New(host string, port int) *Client {
	return NewWithBasePath(host, port, "")
}

// NewWithBasePath creates a client with a custom panel base path (e.g. "/a3f1b2c4/").
func NewWithBasePath(host string, port int, basePath string) *Client {
	jar, _ := cookiejar.New(nil)
	// Normalise: store without trailing slash so we can always do basePath+"/endpoint".
	bp := strings.TrimRight(basePath, "/")
	return &Client{
		base:     fmt.Sprintf("https://%s:%d", host, port),
		basePath: bp,
		hc: &http.Client{
			Jar:     jar,
			Timeout: 30 * time.Second,
			Transport: &http.Transport{
				// 3x-ui uses a self-signed IP cert; this is an internal admin tool.
				TLSClientConfig: &tls.Config{InsecureSkipVerify: true}, //nolint:gosec
				// Respects ALL_PROXY, HTTPS_PROXY, HTTP_PROXY env vars automatically.
				Proxy: http.ProxyFromEnvironment,
			},
		},
	}
}

func (c *Client) SetToken(token string) { c.bearer = token }

// WaitReady polls the login page until the panel responds, up to the given timeout.
func (c *Client) WaitReady(timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	loginURL := c.base + c.basePath + "/login"
	for time.Now().Before(deadline) {
		resp, err := c.hc.Get(loginURL)
		if err == nil {
			resp.Body.Close()
			if resp.StatusCode == 200 || resp.StatusCode == 405 {
				return nil
			}
		}
		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("panel at %s did not become ready within %s", c.base+c.basePath, timeout)
}

// Login authenticates and stores the session cookie for subsequent calls.
func (c *Client) Login(username, password string) error {
	resp, err := c.hc.PostForm(c.base+c.basePath+"/login", url.Values{
		"username": {username},
		"password": {password},
	})
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	var r apiResp[any]
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return err
	}
	if !r.Success {
		return fmt.Errorf("login: %s", r.Msg)
	}
	return nil
}

// GetAPITokens returns the list of API tokens. Requires an active session cookie.
func (c *Client) GetAPITokens() ([]map[string]any, error) {
	return apiGet[[]map[string]any](c, "/panel/api/setting/getApiTokens", false)
}

// AddAPIToken creates a named API token. Requires an active session cookie.
func (c *Client) AddAPIToken(name, token, scope string) error {
	_, err := apiPost[any](c, "/panel/api/setting/addApiToken", map[string]any{
		"name": name, "token": token, "scope": scope,
	}, false)
	return err
}

// ListInbounds returns all inbounds on this node.
func (c *Client) ListInbounds() ([]InboundObj, error) {
	return apiGet[[]InboundObj](c, "/panel/api/inbounds/list", true)
}

// AddInbound creates a new inbound and returns its ID.
func (c *Client) AddInbound(ib InboundObj) (int, error) {
	obj, err := apiPost[InboundObj](c, "/panel/api/inbounds/add", ib, true)
	if err != nil {
		return 0, err
	}
	return obj.ID, nil
}

// ClientExists reports whether a client with the given email is registered on any inbound.
func (c *Client) ClientExists(email string) (bool, error) {
	resp, err := c.do("GET", "/panel/api/inbounds/getClientTraffics/"+email, nil, true)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == 404 {
		return false, nil
	}
	var r apiResp[any]
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return false, nil
	}
	return r.Success, nil
}

// AddClient registers a VLESS client on the given inbound.
// 3x-ui requires the client list to be a JSON-encoded string inside the outer body.
func (c *Client) AddClient(email, uuid, subID, flow string, enable bool, inboundID int) error {
	settings, err := json.Marshal(map[string]any{
		"clients": []any{map[string]any{
			"id": uuid, "email": email, "enable": enable,
			"subId": subID, "flow": flow,
			"tgId": "", "limitIp": 0, "totalGB": 0, "expiryTime": 0, "reset": 0,
		}},
	})
	if err != nil {
		return err
	}
	_, err = apiPost[any](c, "/panel/api/inbounds/addClient", map[string]any{
		"id":       inboundID,
		"settings": string(settings),
	}, true)
	return err
}

// UpdateClientEnable sets the enable flag for a client by UUID.
func (c *Client) UpdateClientEnable(email, uuid string, enable bool, inboundID int) error {
	settings, err := json.Marshal(map[string]any{
		"clients": []any{map[string]any{
			"id": uuid, "email": email, "enable": enable,
		}},
	})
	if err != nil {
		return err
	}
	_, err = apiPost[any](c, "/panel/api/inbounds/updateClient/"+uuid, map[string]any{
		"id":       inboundID,
		"settings": string(settings),
	}, true)
	return err
}

// GetSettings returns the panel settings object.
func (c *Client) GetSettings() (map[string]any, error) {
	return apiGet[map[string]any](c, "/panel/api/setting/all", true)
}

// UpdateSettings posts updated panel settings.
func (c *Client) UpdateSettings(settings map[string]any) error {
	_, err := apiPost[any](c, "/panel/api/setting/update", settings, true)
	return err
}

// GetXrayConfig returns the current xray configuration object.
func (c *Client) GetXrayConfig() (map[string]any, error) {
	return apiGet[map[string]any](c, "/panel/api/xray/config", true)
}

// SetXrayConfig posts an updated xray configuration.
func (c *Client) SetXrayConfig(cfg map[string]any) error {
	_, err := apiPost[any](c, "/panel/api/xray/config", cfg, true)
	return err
}

// ─── internals ────────────────────────────────────────────────────────────────

func (c *Client) do(method, path string, body any, bearer bool) (*http.Response, error) {
	var r io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		r = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, c.base+c.basePath+path, r)
	if err != nil {
		return nil, err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if bearer && c.bearer != "" {
		req.Header.Set("Authorization", "Bearer "+c.bearer)
	}
	return c.hc.Do(req)
}

func apiGet[T any](c *Client, path string, bearer bool) (T, error) {
	var zero T
	resp, err := c.do("GET", path, nil, bearer)
	if err != nil {
		return zero, err
	}
	defer resp.Body.Close()
	var r apiResp[T]
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return zero, err
	}
	if !r.Success {
		return zero, fmt.Errorf("%s", r.Msg)
	}
	return r.Obj, nil
}

func apiPost[T any](c *Client, path string, body any, bearer bool) (T, error) {
	var zero T
	resp, err := c.do("POST", path, body, bearer)
	if err != nil {
		return zero, err
	}
	defer resp.Body.Close()
	var r apiResp[T]
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return zero, err
	}
	if !r.Success {
		return zero, fmt.Errorf("%s", r.Msg)
	}
	return r.Obj, nil
}
