// Package deploy orchestrates 3x-ui node provisioning directly via the REST
// API, replacing Ansible for the common workflow.
package deploy

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/agent"
	"golang.org/x/net/proxy"
	"clwn/infra-tool/config"
	"clwn/infra-tool/xui"
)

// Event carries a log line back to the TUI.
type Event struct {
	Node string
	Kind string // "info" | "ok" | "warn" | "err"
	Text string
}

// Action controls which deploy steps run.
type Action string

const (
	ActionFull      Action = "full"      // bootstrap → sync → keys
	ActionBootstrap Action = "bootstrap" // SSH setup only
	ActionSync      Action = "sync"      // API sync only
	ActionKeys      Action = "keys"      // write local key files only
)

// Run starts the deploy in a background goroutine and returns an event channel
// and an error channel. The event channel is closed when the operation ends;
// the error channel then contains the final error (nil on success).
func Run(hosts *config.Hosts, vars *config.Vars, vault *config.Vault,
	infraDir, target string, action Action) (<-chan Event, <-chan error) {

	eventCh := make(chan Event, 64)
	errCh := make(chan error, 1)

	go func() {
		defer close(eventCh)
		errCh <- runAll(hosts, vars, vault, infraDir, target, action, eventCh)
	}()

	return eventCh, errCh
}

func emit(ch chan<- Event, node, kind, text string) {
	ch <- Event{Node: node, Kind: kind, Text: text}
}

func emitf(ch chan<- Event, node, kind, format string, args ...any) {
	ch <- Event{Node: node, Kind: kind, Text: fmt.Sprintf(format, args...)}
}

func runAll(hosts *config.Hosts, vars *config.Vars, vault *config.Vault,
	infraDir, target string, action Action, events chan<- Event) error {

	var nodeNames []string
	if target == "all" {
		nodeNames = hosts.HostNames()
	} else {
		if _, ok := hosts.All.Hosts[target]; !ok {
			return fmt.Errorf("node %q not found in inventory", target)
		}
		nodeNames = []string{target}
	}

	for _, hostname := range nodeNames {
		switch action {
		case ActionFull:
			if err := bootstrap(hostname, hosts, vars, vault, infraDir, events); err != nil {
				return fmt.Errorf("%s: bootstrap: %w", hostname, err)
			}
			if err := syncNode(hostname, hosts, vars, vault, events); err != nil {
				return fmt.Errorf("%s: sync: %w", hostname, err)
			}
		case ActionBootstrap:
			if err := bootstrap(hostname, hosts, vars, vault, infraDir, events); err != nil {
				return fmt.Errorf("%s: bootstrap: %w", hostname, err)
			}
		case ActionSync:
			if err := syncNode(hostname, hosts, vars, vault, events); err != nil {
				return fmt.Errorf("%s: sync: %w", hostname, err)
			}
		}
	}

	if action == ActionFull || action == ActionKeys {
		if err := writeKeys(hosts, vars, vault, infraDir, events); err != nil {
			return fmt.Errorf("write keys: %w", err)
		}
	}

	return nil
}

// ─── bootstrap ────────────────────────────────────────────────────────────────

// bootstrap installs 3x-ui bare-metal via install.sh (non-interactive), issues
// a Let's Encrypt IP certificate, and registers the API token. Safe to call on
// already-bootstrapped nodes: the sentinel /etc/xui-bootstrapped skips re-runs.
func bootstrap(hostname string, hosts *config.Hosts, vars *config.Vars, vault *config.Vault, infraDir string, events chan<- Event) error {
	node := hosts.All.Hosts[hostname]
	vaultNode := vault.Nodes[hostname]

	emitf(events, hostname, "info", "connecting via SSH as %s@%s", node.AnsibleUser, node.AnsibleHost)
	sc, err := dialSSH(node.AnsibleHost, node.AnsibleUser, node.SSHKeyPath)
	if err != nil {
		return fmt.Errorf("SSH dial: %w", err)
	}
	defer sc.Close()

	out, err := runSSH(sc, "[ -f /etc/xui-bootstrapped ] && echo yes || echo no")
	if err != nil {
		return fmt.Errorf("check sentinel: %w", err)
	}
	if strings.TrimSpace(out) == "yes" {
		emitf(events, hostname, "ok", "already bootstrapped, skipping")
		return nil
	}

	// install.sh lives at <repo>/server/install.sh; infraDir is <repo>/infra/
	installScriptPath := filepath.Join(filepath.Dir(infraDir), "server", "install.sh")
	scriptBytes, err := os.ReadFile(installScriptPath)
	if err != nil {
		return fmt.Errorf("read install.sh at %s: %w", installScriptPath, err)
	}

	basePath := vaultNode.WebBasePath
	if basePath == "" {
		basePath = "/"
	}
	env := map[string]string{
		"XUI_NONINTERACTIVE": "1",
		"XUI_USERNAME":       vault.AdminUsername,
		"XUI_PASSWORD":       vaultNode.AdminPassword,
		"XUI_PANEL_PORT":     strconv.Itoa(node.PanelPort),
		"XUI_SSL_MODE":       "ip",
		"XUI_ACME_HTTP_PORT": "80",
		"XUI_WEB_BASE_PATH":  basePath,
		"XUI_SERVER_IP":      node.AnsibleHost,
	}
	emitf(events, hostname, "info", "running install.sh (bare metal, SSL mode: ip)")
	if err := runSSHStreaming(sc, buildEnvPrefix(env)+"sudo -E bash -s", bytes.NewReader(scriptBytes), hostname, events); err != nil {
		return fmt.Errorf("install.sh failed: %w", err)
	}

	emitf(events, hostname, "info", "waiting for HTTPS panel (up to 120 s)")
	xc := xui.NewWithBasePath(node.AnsibleHost, node.PanelPort, vaultNode.WebBasePath)
	if err := xc.WaitReady(120 * time.Second); err != nil {
		return err
	}

	emitf(events, hostname, "info", "logging in")
	if err := xc.Login(vault.AdminUsername, vaultNode.AdminPassword); err != nil {
		return fmt.Errorf("login: %w", err)
	}

	tokens, err := xc.GetAPITokens()
	if err != nil {
		return fmt.Errorf("get API tokens: %w", err)
	}
	hasToken := false
	for _, t := range tokens {
		if t["name"] == "xnet-tool" {
			hasToken = true
			break
		}
	}
	if !hasToken {
		emitf(events, hostname, "info", "registering API token")
		if err := xc.AddAPIToken("xnet-tool", vaultNode.APIToken, "admin"); err != nil {
			return fmt.Errorf("add API token: %w", err)
		}
	}

	if out, err := runSSH(sc, "touch /etc/xui-bootstrapped && chmod 600 /etc/xui-bootstrapped"); err != nil {
		return fmt.Errorf("mark bootstrapped: %w\n%s", err, out)
	}

	emitf(events, hostname, "ok", "bootstrap complete")
	return nil
}

// ─── sync ─────────────────────────────────────────────────────────────────────

func syncNode(hostname string, hosts *config.Hosts, vars *config.Vars, vault *config.Vault, events chan<- Event) error {
	node := hosts.All.Hosts[hostname]
	vaultNode := vault.Nodes[hostname]

	xc := xui.NewWithBasePath(node.AnsibleHost, node.PanelPort, vaultNode.WebBasePath)

	// Quick reachability probe — if the login page doesn't respond, the panel
	// isn't up yet or the base path is wrong.
	emitf(events, hostname, "info", "checking panel reachability")
	if err := xc.WaitReady(8 * time.Second); err != nil {
		return fmt.Errorf("panel not reachable (run bootstrap first, or check panel port/base path): %w", err)
	}

	// Prefer bearer token auth; fall back to cookie login if token is absent or rejected.
	authed := false
	if vaultNode.APIToken != "" {
		xc.SetToken(vaultNode.APIToken)
		inbounds, err := xc.ListInbounds()
		if err == nil {
			inboundID, err := ensureInboundFromList(hostname, xc, node, vaultNode, inbounds, events)
			if err != nil {
				return fmt.Errorf("inbound: %w", err)
			}
			return finishSync(hostname, xc, node, vaultNode, hosts, vars, vault, inboundID, events)
		}
		emitf(events, hostname, "warn", "API token rejected (%v), retrying with password login", err)
		authed = false
	}
	if !authed {
		emitf(events, hostname, "info", "logging in with username/password")
		if err := xc.Login(vault.AdminUsername, vaultNode.AdminPassword); err != nil {
			return fmt.Errorf("login: %w", err)
		}
	}

	inbounds, err := xc.ListInbounds()
	if err != nil {
		return fmt.Errorf("inbound: %w", err)
	}
	inboundID, err := ensureInboundFromList(hostname, xc, node, vaultNode, inbounds, events)
	if err != nil {
		return fmt.Errorf("inbound: %w", err)
	}
	return finishSync(hostname, xc, node, vaultNode, hosts, vars, vault, inboundID, events)
}

func finishSync(hostname string, xc *xui.Client, node config.Node, vaultNode config.VaultNode,
	hosts *config.Hosts, vars *config.Vars, vault *config.Vault, inboundID int, events chan<- Event) error {
	if err := syncClients(hostname, xc, vars, vault, inboundID, events); err != nil {
		return fmt.Errorf("clients: %w", err)
	}
	if err := syncRelayClients(hostname, xc, vault, inboundID, events); err != nil {
		return fmt.Errorf("relay clients: %w", err)
	}
	if err := syncSub(hostname, xc, vars, events); err != nil {
		return fmt.Errorf("sub settings: %w", err)
	}
	if err := syncTopology(hostname, xc, hosts, vars, vault, node, events); err != nil {
		return fmt.Errorf("topology: %w", err)
	}
	emitf(events, hostname, "ok", "sync complete")
	return nil
}

func ensureInboundFromList(hostname string, xc *xui.Client, node config.Node, vn config.VaultNode, inbounds []xui.InboundObj, events chan<- Event) (int, error) {
	for _, ib := range inbounds {
		if ib.Tag == node.Inbound.Tag {
			emitf(events, hostname, "info", "inbound %s exists (id=%d)", node.Inbound.Tag, ib.ID)
			return ib.ID, nil
		}
	}

	settings, _ := json.Marshal(map[string]any{
		"clients": []any{}, "decryption": "none",
	})
	streamSettings, _ := json.Marshal(map[string]any{
		"network":  "tcp",
		"security": "reality",
		"realitySettings": map[string]any{
			"show": false, "xver": 0,
			"target":      node.Inbound.RealityTarget,
			"serverNames": node.Inbound.ServerNames,
			"privateKey":  vn.PrivateKey,
			"shortIds":    vn.ShortIDs,
			"settings": map[string]any{
				"publicKey":   vn.PublicKey,
				"fingerprint": node.Inbound.Fingerprint,
				"spiderX":     "/",
			},
		},
	})
	sniffing, _ := json.Marshal(map[string]any{
		"enabled":      true,
		"destOverride": []string{"http", "tls", "quic", "fakedns"},
		"metadataOnly": false,
	})

	emitf(events, hostname, "info", "creating inbound %s on port %d", node.Inbound.Tag, node.Inbound.Port)
	id, err := xc.AddInbound(xui.InboundObj{
		Remark:         hostname + "-vless-reality",
		Enable:         true,
		Protocol:       "vless",
		Port:           node.Inbound.Port,
		Listen:         "0.0.0.0",
		Tag:            node.Inbound.Tag,
		Settings:       string(settings),
		StreamSettings: string(streamSettings),
		Sniffing:       string(sniffing),
	})
	if err != nil {
		return 0, err
	}
	emitf(events, hostname, "ok", "created inbound %s (id=%d)", node.Inbound.Tag, id)
	return id, nil
}

func syncClients(hostname string, xc *xui.Client, vars *config.Vars, vault *config.Vault, inboundID int, events chan<- Event) error {
	for _, client := range vars.Clients {
		vc, ok := vault.Clients[client.Name]
		if !ok {
			emitf(events, hostname, "warn", "client %s has no vault entry, skipping", client.Name)
			continue
		}
		exists, err := xc.ClientExists(client.Name)
		if err != nil {
			return fmt.Errorf("check %s: %w", client.Name, err)
		}
		if !exists {
			emitf(events, hostname, "info", "adding client %s", client.Name)
			if err := xc.AddClient(client.Name, vc.UUID, vc.SubID, "xtls-rprx-vision", true, inboundID); err != nil {
				return fmt.Errorf("add %s: %w", client.Name, err)
			}
		}
		if client.Enable != nil {
			if err := xc.UpdateClientEnable(client.Name, vc.UUID, *client.Enable, inboundID); err != nil {
				emitf(events, hostname, "warn", "update enable %s: %v", client.Name, err)
			}
		}
	}
	return nil
}

func syncRelayClients(hostname string, xc *xui.Client, vault *config.Vault, inboundID int, events chan<- Event) error {
	suffix := "_to_" + hostname
	for legKey, relay := range vault.RelayClients {
		if !strings.HasSuffix(legKey, suffix) {
			continue
		}
		email := "relay-" + strings.ToLower(strings.ReplaceAll(legKey, "_", "-"))
		exists, err := xc.ClientExists(email)
		if err != nil {
			return fmt.Errorf("check relay %s: %w", email, err)
		}
		if !exists {
			emitf(events, hostname, "info", "adding relay client %s", email)
			if err := xc.AddClient(email, relay.UUID, "", "xtls-rprx-vision", true, inboundID); err != nil {
				return fmt.Errorf("add relay %s: %w", email, err)
			}
		}
	}
	return nil
}

func syncSub(hostname string, xc *xui.Client, vars *config.Vars, events chan<- Event) error {
	settings, err := xc.GetSettings()
	if err != nil {
		return err
	}
	wantPort := fmt.Sprintf("%d", vars.SubPort)
	if settings["subEnable"] == true &&
		fmt.Sprintf("%v", settings["subPort"]) == wantPort &&
		settings["subPath"] == vars.SubPath {
		emitf(events, hostname, "info", "sub settings already correct")
		return nil
	}
	emitf(events, hostname, "info", "updating sub settings (port=%d path=%s)", vars.SubPort, vars.SubPath)
	return xc.UpdateSettings(map[string]any{
		"subEnable": true,
		"subPort":   vars.SubPort,
		"subPath":   vars.SubPath,
		"subDomain": "",
	})
}

func syncTopology(hostname string, xc *xui.Client, hosts *config.Hosts, vars *config.Vars, vault *config.Vault, node config.Node, events chan<- Event) error {
	newOutbounds, newRules := buildRelayLegs(hostname, hosts, vars, vault, node)

	cfg, err := xc.GetXrayConfig()
	if err != nil {
		return fmt.Errorf("get xray config: %w", err)
	}

	// Replace all relay-out-* outbounds with the freshly computed set.
	existing := asSlice(cfg["outbounds"])
	filtered := make([]any, 0, len(existing))
	for _, ob := range existing {
		m, ok := ob.(map[string]any)
		if ok && strings.HasPrefix(fmt.Sprintf("%v", m["tag"]), "relay-out-") {
			continue
		}
		filtered = append(filtered, ob)
	}
	cfg["outbounds"] = append(filtered, newOutbounds...)

	routing := asMap(cfg["routing"])
	existingRules := asSlice(routing["rules"])
	filteredRules := make([]any, 0, len(existingRules))
	for _, rule := range existingRules {
		m, ok := rule.(map[string]any)
		if ok && strings.HasPrefix(fmt.Sprintf("%v", m["outboundTag"]), "relay-out-") {
			continue
		}
		filteredRules = append(filteredRules, rule)
	}
	routing["rules"] = append(filteredRules, newRules...)
	cfg["routing"] = routing

	// Skip write if nothing changed.
	if len(newOutbounds) == 0 && len(existing) == len(filtered) && len(existingRules) == len(filteredRules) {
		emitf(events, hostname, "info", "topology unchanged")
		return nil
	}

	emitf(events, hostname, "info", "updating xray config (%d relay outbounds)", len(newOutbounds))
	return xc.SetXrayConfig(cfg)
}

// buildRelayLegs returns relay outbounds and routing rules for paths where
// hostname is the upstream node.
func buildRelayLegs(hostname string, hosts *config.Hosts, vars *config.Vars, vault *config.Vault, node config.Node) (outbounds []any, rules []any) {
	seen := map[string]bool{}
	for _, path := range vars.Paths {
		for i := 0; i < len(path.Hops)-1; i++ {
			up, down := path.Hops[i], path.Hops[i+1]
			if up != hostname {
				continue
			}
			tag := "relay-out-" + down
			if seen[tag] {
				continue
			}
			seen[tag] = true

			downNode, ok1 := hosts.All.Hosts[down]
			downVault, ok2 := vault.Nodes[down]
			relay, ok3 := vault.RelayClients[up+"_to_"+down]
			if !ok1 || !ok2 || !ok3 || len(downVault.ShortIDs) == 0 || len(downNode.Inbound.ServerNames) == 0 {
				continue
			}

			outbounds = append(outbounds, map[string]any{
				"tag":      tag,
				"protocol": "vless",
				"settings": map[string]any{
					"vnext": []any{map[string]any{
						"address": downNode.AnsibleHost,
						"port":    downNode.Inbound.Port,
						"users": []any{map[string]any{
							"id": relay.UUID, "flow": "xtls-rprx-vision", "encryption": "none",
						}},
					}},
				},
				"streamSettings": map[string]any{
					"network":  "tcp",
					"security": "reality",
					"realitySettings": map[string]any{
						"publicKey":   downVault.PublicKey,
						"shortId":     downVault.ShortIDs[0],
						"serverName":  downNode.Inbound.ServerNames[0],
						"fingerprint": "firefox",
						"spiderX":     "/",
					},
				},
			})
			rules = append(rules, map[string]any{
				"type":        "field",
				"inboundTag":  []string{node.Inbound.Tag},
				"outboundTag": tag,
			})
		}
	}
	return
}

// ─── write keys ───────────────────────────────────────────────────────────────

func writeKeys(hosts *config.Hosts, vars *config.Vars, vault *config.Vault, infraDir string, events chan<- Event) error {
	keysDir := filepath.Join(infraDir, "keys")
	if err := os.MkdirAll(keysDir, 0700); err != nil {
		return err
	}

	for _, client := range vars.Clients {
		vc, ok := vault.Clients[client.Name]
		if !ok {
			continue
		}
		var sb strings.Builder
		fmt.Fprintf(&sb, "# VLESS links for %s\n\n", client.Name)
		for _, path := range vars.Paths {
			if len(path.Hops) == 0 {
				continue
			}
			link := BuildVLESSURI(client.Name, vc.UUID, path, hosts, vault)
			if link == "" {
				continue
			}
			fmt.Fprintf(&sb, "# %s\n%s\n\n", path.Name, link)
		}
		dest := filepath.Join(keysDir, client.Name+".txt")
		if err := os.WriteFile(dest, []byte(sb.String()), 0600); err != nil {
			return err
		}
		emitf(events, "", "ok", "wrote %s", dest)
	}
	return nil
}

// BuildVLESSURI builds a vless:// URI for a client connecting via a given path.
// The entry point is path.Hops[0]; that node's Reality settings are used.
func BuildVLESSURI(clientName, uuid string, path config.Path, hosts *config.Hosts, vault *config.Vault) string {
	if len(path.Hops) == 0 {
		return ""
	}
	entryName := path.Hops[0]
	node, ok := hosts.All.Hosts[entryName]
	if !ok {
		return ""
	}
	vn, ok := vault.Nodes[entryName]
	if !ok {
		return ""
	}
	sni := ""
	if len(node.Inbound.ServerNames) > 0 {
		sni = node.Inbound.ServerNames[0]
	}
	sid := ""
	if len(vn.ShortIDs) > 0 {
		sid = vn.ShortIDs[0]
	}
	remark := clientName + "@" + path.Name
	return fmt.Sprintf(
		"vless://%s@%s:%d?type=tcp&security=reality&pbk=%s&fp=%s&sni=%s&sid=%s&flow=xtls-rprx-vision#%s",
		uuid, node.AnsibleHost, node.Inbound.Port,
		vn.PublicKey, node.Inbound.Fingerprint, sni, sid, remark,
	)
}

// ─── SSH helpers ───────────────────────────────────────────────────────────────

func dialSSH(host, user, keyPath string) (*ssh.Client, error) {
	auths := sshAuthMethods(keyPath)
	if len(auths) == 0 {
		return nil, fmt.Errorf("no SSH auth methods available (start ssh-agent, add a key to ~/.ssh/, or set SSH key path on the node)")
	}
	cfg := &ssh.ClientConfig{
		User:            user,
		Auth:            auths,
		HostKeyCallback: ssh.InsecureIgnoreHostKey(), //nolint:gosec // internal admin tool
		Timeout:         15 * time.Second,
	}
	addr := net.JoinHostPort(host, "22")
	if d := sshProxyDialer(); d != nil {
		conn, err := d.Dial("tcp", addr)
		if err != nil {
			return nil, err
		}
		c, chans, reqs, err := ssh.NewClientConn(conn, addr, cfg)
		if err != nil {
			conn.Close()
			return nil, err
		}
		return ssh.NewClient(c, chans, reqs), nil
	}
	return ssh.Dial("tcp", addr, cfg)
}

// proxyEnvVars is checked in order; first non-empty value wins.
// Matches the precedence curl and most desktop tools follow.
var proxyEnvVars = []string{
	"ALL_PROXY", "all_proxy",
	"SOCKS5_PROXY", "socks5_proxy",
	"HTTPS_PROXY", "https_proxy",
	"HTTP_PROXY", "http_proxy",
}

// ActiveProxy returns the first proxy URL found in standard env vars, or "".
func ActiveProxy() string {
	for _, key := range proxyEnvVars {
		if val := os.Getenv(key); val != "" {
			return val
		}
	}
	return ""
}

// sshProxyDialer returns a Dialer that routes TCP through the configured proxy.
// Supports socks5:// (via x/net/proxy) and http:// (via HTTP CONNECT).
func sshProxyDialer() proxy.Dialer {
	raw := ActiveProxy()
	if raw == "" {
		return nil
	}
	u, err := url.Parse(raw)
	if err != nil {
		return nil
	}
	switch u.Scheme {
	case "socks5", "socks5h":
		d, err := proxy.FromURL(u, proxy.Direct)
		if err != nil {
			return nil
		}
		return d
	case "http", "https":
		return &httpConnectDialer{proxyAddr: u.Host}
	}
	return nil
}

// httpConnectDialer tunnels TCP connections through an HTTP CONNECT proxy.
type httpConnectDialer struct{ proxyAddr string }

func (d *httpConnectDialer) Dial(network, addr string) (net.Conn, error) {
	conn, err := net.DialTimeout("tcp", d.proxyAddr, 15*time.Second)
	if err != nil {
		return nil, fmt.Errorf("http proxy dial: %w", err)
	}
	fmt.Fprintf(conn, "CONNECT %s HTTP/1.1\r\nHost: %s\r\nProxy-Connection: keep-alive\r\n\r\n", addr, addr)
	br := bufio.NewReader(conn)
	resp, err := http.ReadResponse(br, &http.Request{Method: "CONNECT"})
	if err != nil {
		conn.Close()
		return nil, fmt.Errorf("http proxy CONNECT: %w", err)
	}
	resp.Body.Close()
	if resp.StatusCode != 200 {
		conn.Close()
		return nil, fmt.Errorf("http proxy CONNECT: status %d", resp.StatusCode)
	}
	return conn, nil
}

func sshAuthMethods(keyPath string) []ssh.AuthMethod {
	// Collect all signers upfront into a single PublicKeys method.
	// Using PublicKeysCallback with an empty agent causes auth to fail on
	// modern OpenSSH servers even when a valid key follows in the method list.
	var signers []ssh.Signer

	// Agent — resolve synchronously so we only add keys that exist.
	if sock := os.Getenv("SSH_AUTH_SOCK"); sock != "" {
		if conn, err := net.Dial("unix", sock); err == nil {
			if ss, err := agent.NewClient(conn).Signers(); err == nil {
				signers = append(signers, ss...)
			}
		}
	}

	home, _ := os.UserHomeDir()

	if keyPath != "" {
		if strings.HasPrefix(keyPath, "~/") {
			keyPath = filepath.Join(home, keyPath[2:])
		}
		if pem, err := os.ReadFile(keyPath); err == nil {
			if s, err := ssh.ParsePrivateKey(pem); err == nil {
				signers = append(signers, s)
			}
			// passphrase-protected: agent above handles it if user ran ssh-add
		}
	} else {
		for _, name := range []string{"id_ed25519", "id_rsa", "id_ecdsa"} {
			pem, err := os.ReadFile(filepath.Join(home, ".ssh", name))
			if err != nil {
				continue
			}
			if s, err := ssh.ParsePrivateKey(pem); err == nil {
				signers = append(signers, s)
			}
		}
	}

	if len(signers) == 0 {
		return nil
	}
	return []ssh.AuthMethod{ssh.PublicKeys(signers...)}
}

func runSSH(client *ssh.Client, cmd string) (string, error) {
	sess, err := client.NewSession()
	if err != nil {
		return "", err
	}
	defer sess.Close()
	out, err := sess.CombinedOutput(cmd)
	return string(out), err
}

var ansiRE = regexp.MustCompile(`\x1b\[[0-9;]*[a-zA-Z]`)

func stripANSI(s string) string { return ansiRE.ReplaceAllString(s, "") }

// runSSHStreaming runs cmd on the remote host, streaming each output line as a
// deploy event so the user can see progress in real time.
func runSSHStreaming(client *ssh.Client, cmd string, stdin io.Reader, node string, events chan<- Event) error {
	sess, err := client.NewSession()
	if err != nil {
		return err
	}
	defer sess.Close()

	pr, pw := io.Pipe()
	sess.Stdout = pw
	sess.Stderr = pw
	sess.Stdin = stdin

	if err := sess.Start(cmd); err != nil {
		pw.Close()
		return err
	}

	// Read lines and emit them while the command runs.
	done := make(chan error, 1)
	go func() {
		done <- sess.Wait()
		pw.Close()
	}()

	scanner := bufio.NewScanner(pr)
	for scanner.Scan() {
		line := scanner.Text()
		// Strip ANSI colour codes — the script uses colour but the TUI has its own styling.
		line = stripANSI(line)
		if line != "" {
			emitf(events, node, "info", "%s", line)
		}
	}

	return <-done
}

func runSSHWithStdin(client *ssh.Client, cmd string, stdin io.Reader) (string, error) {
	sess, err := client.NewSession()
	if err != nil {
		return "", err
	}
	defer sess.Close()
	var buf bytes.Buffer
	sess.Stdout = &buf
	sess.Stderr = &buf
	sess.Stdin = stdin
	err = sess.Run(cmd)
	return buf.String(), err
}

// buildEnvPrefix returns a shell-safe "KEY='val' KEY2='val2' " prefix string.
func buildEnvPrefix(env map[string]string) string {
	var sb strings.Builder
	for k, v := range env {
		quoted := "'" + strings.ReplaceAll(v, "'", `'\''`) + "'"
		fmt.Fprintf(&sb, "%s=%s ", k, quoted)
	}
	return sb.String()
}

// ─── type-assertion helpers ───────────────────────────────────────────────────

func asSlice(v any) []any {
	if s, ok := v.([]any); ok {
		return s
	}
	return nil
}

func asMap(v any) map[string]any {
	if m, ok := v.(map[string]any); ok {
		return m
	}
	return map[string]any{}
}
