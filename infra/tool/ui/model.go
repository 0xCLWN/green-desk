package ui

import (
	"fmt"
	"os/exec"
	"os/user"
	"strconv"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/bubbles/textinput"
	"github.com/charmbracelet/bubbles/viewport"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
	"clwn/infra-tool/config"
	"clwn/infra-tool/crypto"
	"clwn/infra-tool/deploy"
	"clwn/infra-tool/scan"
)

type screenMode int

const (
	modeMenu screenMode = iota
	modeAddNode
	modeEditNode
	modeAddClient
	modeAddPath
	modeDeploy
	modeDeployRun
	modeScanner
	modeCredentials
	modeNodes
	modeKeys
)

const maxHops = 10

// — message types for async operations —
type deployEventMsg struct{ ev deploy.Event }
type deployDoneMsg struct{ err error }
type scanResultMsg struct{ r *scan.Result }
type scanProgressMsg struct{}
type scanDoneMsg struct{}
type keyCopiedMsg struct{}

// model is the root bubbletea model. Pointer receiver is used throughout so that
// huh form value bindings (via *string pointers into model fields) remain stable
// across Update calls.
type model struct {
	app    *App
	mode   screenMode
	width  int
	height int

	// scrollable session history shown above the active area
	history []string
	vp      viewport.Model
	vpReady bool

	// menu state
	menuCursor int

	// nodes list state
	nodesCursor  int
	editNodeName string // name of node being edited

	// keys view state
	keysCursor  int
	keysCopied  bool // flash "copied!" for one render cycle
	keysVP      viewport.Model
	keysVPReady bool

	// active huh form (nil when not in a form mode)
	form *huh.Form

	// node form bound vars
	nodeHostName    string
	nodeIP          string
	nodeUser        string
	nodeSSHKey      string
	nodePanelPort   string
	nodeInboundPort string
	nodeTarget      string
	nodeServerNames string
	nodeFingerprint string

	// client form bound var
	clientName string

	// path form bound vars (fixed-size array so pointers are stable)
	pathName     string
	pathHopVals  [maxHops]string
	pathHopCount int

	// deploy
	deployTarget  string
	deployAction  string
	deployRunning bool
	deployEventCh <-chan deploy.Event
	deployErrCh   <-chan error

	// scanner
	scanCIDR    textinput.Model
	scanWorkers textinput.Model
	scanFocus   int // 0=CIDR 1=workers 2=table
	scanTable   table.Model
	scanRunning bool
	scanTotal   int
	scanChecked int
	scanSpinner spinner.Model
	scanCh      <-chan *scan.Result
	scanProgCh  <-chan struct{}
	scanStopCh  chan struct{}
}

func newModel(app *App) *model {
	cidr := textinput.New()
	cidr.Placeholder = "1.2.3.0/24"
	cidr.CharLimit = 20
	cidr.Prompt = "CIDR › "
	cidr.Focus()

	workers := textinput.New()
	workers.Placeholder = "100"
	workers.CharLimit = 5
	workers.Prompt = "Workers › "

	cols := []table.Column{
		{Title: "IP", Width: 16},
		{Title: "Domain", Width: 30},
		{Title: "Issuer", Width: 22},
		{Title: "TLS", Width: 6},
		{Title: "Cert age", Width: 10},
	}
	tbl := table.New(
		table.WithColumns(cols),
		table.WithFocused(false),
		table.WithHeight(10),
	)
	ts := table.DefaultStyles()
	ts.Header = lipgloss.NewStyle().Foreground(clrAmber).Bold(true)
	ts.Cell = lipgloss.NewStyle().Foreground(clrWhite)
	ts.Selected = lipgloss.NewStyle().Foreground(clrViolet).Bold(true)
	tbl.SetStyles(ts)

	sp := spinner.New()
	sp.Spinner = spinner.Dot
	sp.Style = lipgloss.NewStyle().Foreground(clrAmber)

	defaultUser := "root"
	if u, err := user.Current(); err == nil && u.Username != "" {
		defaultUser = u.Username
	}

	return &model{
		app:             app,
		mode:            modeMenu,
		nodeUser:        defaultUser,
		nodeInboundPort: "443",
		nodeFingerprint: "firefox",
		deployTarget:    "all",
		deployAction:    "full",
		scanCIDR:        cidr,
		scanWorkers:     workers,
		scanTable:       tbl,
		scanSpinner:     sp,
	}
}

// ─── tea.Model interface ───────────────────────────────────────────────────

func (m *model) Init() tea.Cmd {
	return textinput.Blink
}

func (m *model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {

	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		if !m.vpReady {
			m.vp = viewport.New(m.width, m.vpHeight())
			m.vpReady = true
		} else {
			m.vp.Width = m.width
			m.vp.Height = m.vpHeight()
		}
		atBottom := m.vp.AtBottom()
		m.vp.SetContent(strings.Join(m.history, "\n"))
		if atBottom {
			m.vp.GotoBottom()
		}
		if m.form != nil {
			m.form.WithWidth(m.formWidth()).WithHeight(m.formHeight())
		}
		m.scanTable.SetWidth(m.width - 2)
		m.scanTable.SetHeight(m.scanTableHeight())
		keysH := m.keysVPHeight()
		if !m.keysVPReady {
			m.keysVP = viewport.New(m.width, keysH)
			m.keysVPReady = true
		} else {
			m.keysVP.Width = m.width
			m.keysVP.Height = keysH
		}
		if m.mode == modeKeys {
			m.keysVP.SetContent(m.buildKeysContent())
		}
		return m, nil

	case deployEventMsg:
		m.history = append(m.history, formatDeployEvent(msg.ev))
		m.updateVP()
		return m, m.awaitDeployEvent()

	case deployDoneMsg:
		m.deployRunning = false
		if msg.err != nil {
			m.logf(sErr, "✗ deploy failed: %v", msg.err)
		} else {
			m.logf(sOK, "✓ deploy completed")
		}
		m.mode = modeMenu
		return m, nil

	case scanResultMsg:
		rows := m.scanTable.Rows()
		r := msg.r
		rows = append(rows, table.Row{r.IP, r.Domain, r.Issuer, r.TLSVer, r.CertAge})
		m.scanTable.SetRows(rows)
		return m, m.awaitScanResult()

	case scanProgressMsg:
		m.scanChecked++
		return m, m.awaitScanProgress()

	case scanDoneMsg:
		m.scanRunning = false
		return m, nil

	case keyCopiedMsg:
		m.keysCopied = false
		m.keysVP.SetContent(m.buildKeysContent())
		return m, nil

	case spinner.TickMsg:
		if m.scanRunning {
			var cmd tea.Cmd
			m.scanSpinner, cmd = m.scanSpinner.Update(msg)
			return m, cmd
		}
		return m, nil

	case tea.KeyMsg:
		if msg.String() == "ctrl+c" {
			return m, tea.Quit
		}
		switch m.mode {
		case modeMenu:
			return m, m.updateMenu(msg)
		case modeScanner:
			return m.updateScanner(msg)
		case modeCredentials:
			if msg.Type == tea.KeyEsc || msg.String() == "q" {
				m.mode = modeMenu
			}
			return m, nil
		case modeNodes:
			return m, m.updateNodes(msg)
		case modeKeys:
			return m, m.updateKeys(msg)
		case modeDeployRun:
			if msg.Type == tea.KeyEsc {
				m.mode = modeMenu
				return m, nil
			}
			var cmd tea.Cmd
			m.vp, cmd = m.vp.Update(msg)
			return m, cmd
		}
	}

	// Form handling (modeAddNode / modeEditNode / modeAddClient / modeAddPath / modeDeploy)
	if m.form != nil {
		if keyMsg, ok := msg.(tea.KeyMsg); ok {
			if keyMsg.Type == tea.KeyEsc {
				if m.mode == modeEditNode {
					m.mode = modeNodes
				} else {
					m.mode = modeMenu
				}
				m.form = nil
				return m, nil
			}
			if m.mode == modeAddPath {
				switch keyMsg.String() {
				case "ctrl+n":
					if m.pathHopCount < maxHops {
						m.pathHopCount++
						m.form = m.buildPathForm()
						return m, m.form.Init()
					}
					return m, nil
				case "ctrl+d":
					if m.pathHopCount > 1 {
						m.pathHopCount--
						m.form = m.buildPathForm()
						return m, m.form.Init()
					}
					return m, nil
				}
			}
		}

		fm, cmd := m.form.Update(msg)
		if f, ok := fm.(*huh.Form); ok {
			m.form = f
		}
		switch m.form.State {
		case huh.StateCompleted:
			return m, m.handleFormComplete()
		case huh.StateAborted:
			m.mode = modeMenu
			m.form = nil
		}
		return m, cmd
	}

	// Viewport scroll in menu mode
	if m.vpReady && m.mode == modeMenu {
		var cmd tea.Cmd
		m.vp, cmd = m.vp.Update(msg)
		return m, cmd
	}

	return m, nil
}

func (m *model) View() string {
	if m.width == 0 || !m.vpReady {
		return "Loading…"
	}

	status := m.statusBar()
	help := m.helpBar()

	switch m.mode {
	// Full-screen form modes: status + form filling all available space + help
	case modeAddNode, modeEditNode, modeAddClient, modeAddPath, modeDeploy:
		formContent := ""
		if m.form != nil {
			formContent = m.form.View()
		}
		return strings.Join([]string{status, formContent, help}, "\n")

	case modeDeployRun:
		return strings.Join([]string{
			status,
			m.vp.View(),
			sDim.Render(strings.Repeat("─", m.width)),
			"\n  " + sBold.Render("⚡ Deploying…") + "\n  " + sDim.Render("output above · esc to return"),
			help,
		}, "\n")

	case modeScanner:
		return strings.Join([]string{status, m.scanView(), help}, "\n")

	case modeCredentials:
		return strings.Join([]string{status, m.credsView(), help}, "\n")

	case modeNodes:
		return strings.Join([]string{status, m.nodesView(), help}, "\n")

	case modeKeys:
		return strings.Join([]string{status, m.keysView(), help}, "\n")

	default: // modeMenu: history viewport + menu below
		menuH := len(menuItems) + 4
		vpH := m.height - 1 - 1 - menuH - 1 // status + divider + menu + help
		if vpH < 0 {
			vpH = 0
		}
		m.vp.Height = vpH
		parts := []string{status}
		if vpH > 0 {
			parts = append(parts, m.vp.View())
			parts = append(parts, sDim.Render(strings.Repeat("─", m.width)))
		}
		parts = append(parts, m.menuView(), help)
		return strings.Join(parts, "\n")
	}
}

// ─── layout helpers ────────────────────────────────────────────────────────

// vpHeight is used for the deploy run streaming view.
func (m *model) vpHeight() int {
	if m.height < 6 {
		return 1
	}
	return m.height - 6 // status + divider + 3-line status + help
}

func (m *model) formWidth() int {
	w := m.width - 4
	if w > 80 {
		w = 80
	}
	if w < 40 {
		w = 40
	}
	return w
}

// formHeight is the full usable area for a form: terminal minus status and help bars.
func (m *model) formHeight() int {
	h := m.height - 2
	if h < 4 {
		h = 4
	}
	return h
}

func (m *model) scanTableHeight() int {
	h := m.height - 4
	if h < 3 {
		h = 3
	}
	return h
}

// ─── status + help bars ────────────────────────────────────────────────────

func (m *model) statusBar() string {
	n := len(m.app.hosts.All.Hosts)
	c := len(m.app.vars.Clients)
	p := len(m.app.vars.Paths)

	left := lipgloss.NewStyle().Background(clrDark).Foreground(clrViolet).Bold(true).Padding(0, 1).Render("xnet-infra")

	rightParts := fmt.Sprintf("%d node(s) · %d client(s) · %d path(s)", n, c, p)
	if px := deploy.ActiveProxy(); px != "" {
		rightParts += "  proxy: " + px
	}
	right := lipgloss.NewStyle().Background(clrDark).Foreground(clrGray).Padding(0, 1).Render(rightParts)

	gap := m.width - lipgloss.Width(left) - lipgloss.Width(right)
	if gap < 0 {
		gap = 0
	}
	fill := lipgloss.NewStyle().Background(clrDark).Render(strings.Repeat(" ", gap))
	return left + fill + right
}

func (m *model) helpBar() string {
	var hints []string
	switch m.mode {
	case modeMenu:
		hints = []string{"↑↓ navigate", "j/k scroll log", "letter or enter select", "q quit"}
	case modeAddNode, modeEditNode, modeAddClient, modeDeploy:
		hints = []string{"↑↓ / enter navigate", "esc cancel"}
	case modeAddPath:
		hints = []string{"↑↓ / enter navigate", "ctrl+n add hop", "ctrl+d remove hop", "esc cancel"}
	case modeDeployRun:
		hints = []string{"j/k scroll", "esc return to menu"}
	case modeScanner:
		hints = []string{"tab cycle focus", "r run / stop", "enter use row", "esc back"}
	case modeCredentials:
		hints = []string{"esc / q back"}
	case modeNodes:
		hints = []string{"↑↓ / j/k navigate", "e edit", "o open browser", "c copy creds", "d delete", "esc back"}
	case modeKeys:
		hints = []string{"↑↓ navigate", "enter / c copy to clipboard", "esc / q back"}
	}
	return sDim.Render("  " + strings.Join(hints, "  ·  "))
}

// ─── active area ───────────────────────────────────────────────────────────

var menuItems = []struct{ key, label string }{
	{"n", "Add Node"},
	{"l", "List / delete nodes"},
	{"c", "Add Client"},
	{"p", "Add Path"},
	{"s", "Scan for REALITY targets"},
	{"d", "Deploy"},
	{"y", "VLESS keys"},
	{"r", "Show Credentials"},
}

func (m *model) menuView() string {
	var sb strings.Builder
	sb.WriteString("\n")
	for i, item := range menuItems {
		cursor := "  "
		label := sNorm.Render(item.label)
		if i == m.menuCursor {
			cursor = "▸ "
			label = sMenuSel.Render(item.label)
		}
		sb.WriteString(fmt.Sprintf("  %s%s %s\n", cursor, sMenuKey.Render("["+item.key+"]"), label))
	}
	return sb.String()
}

// ─── menu update ───────────────────────────────────────────────────────────

func (m *model) updateMenu(key tea.KeyMsg) tea.Cmd {
	n := len(menuItems)
	switch key.String() {
	case "q":
		return tea.Quit
	case "up":
		m.menuCursor = (m.menuCursor - 1 + n) % n
	case "down":
		m.menuCursor = (m.menuCursor + 1) % n
	case "j":
		m.vp.LineDown(1)
	case "k":
		m.vp.LineUp(1)
	case "n":
		return m.enterMode(modeAddNode)
	case "l":
		return m.enterMode(modeNodes)
	case "c":
		return m.enterMode(modeAddClient)
	case "p":
		return m.enterMode(modeAddPath)
	case "s":
		return m.enterMode(modeScanner)
	case "d":
		return m.enterMode(modeDeploy)
	case "y":
		return m.enterMode(modeKeys)
	case "r":
		return m.enterMode(modeCredentials)
	case "enter", " ":
		modes := []screenMode{modeAddNode, modeNodes, modeAddClient, modeAddPath, modeScanner, modeDeploy, modeKeys, modeCredentials}

		if m.menuCursor < len(modes) {
			return m.enterMode(modes[m.menuCursor])
		}
	}
	return nil
}

// ─── mode transitions ─────────────────────────────────────────────────────

func (m *model) enterMode(next screenMode) tea.Cmd {
	m.mode = next
	switch next {
	case modeAddNode:
		m.nodeHostName, m.nodeIP = "", ""
		if m.nodeUser == "" {
			m.nodeUser = "root"
		}
		m.nodeSSHKey = ""
		m.nodePanelPort = strconv.Itoa(crypto.GenPort())
		m.nodeInboundPort = "443"
		m.nodeTarget, m.nodeServerNames, m.nodeFingerprint = "", "", "firefox"
		m.form = m.buildNodeForm()
		return m.form.Init()

	case modeEditNode:
		// fields already populated by updateNodes before calling enterMode
		m.form = m.buildEditNodeForm()
		return m.form.Init()

	case modeAddClient:
		m.clientName = ""
		m.form = m.buildClientForm()
		return m.form.Init()

	case modeAddPath:
		if len(m.app.hosts.HostNames()) == 0 {
			m.logf(sErr, "✗ Add at least one node first")
			m.mode = modeMenu
			return nil
		}
		m.pathName = ""
		m.pathHopCount = 1
		m.pathHopVals = [maxHops]string{}
		if names := m.app.hosts.HostNames(); len(names) > 0 {
			m.pathHopVals[0] = names[0]
		}
		m.form = m.buildPathForm()
		return m.form.Init()

	case modeDeploy:
		m.deployTarget, m.deployAction = "all", "full"
		m.form = m.buildDeployForm()
		return m.form.Init()

	case modeScanner:
		m.scanTable.SetRows(nil)
		m.scanCIDR.Focus()
		m.scanWorkers.Blur()
		m.scanTable.Blur()
		m.scanFocus = 0

	case modeCredentials:
		// no setup needed

	case modeKeys:
		if m.keysVPReady {
			m.keysVP.SetContent(m.buildKeysContent())
			m.keysVP.GotoTop()
		}
	}
	return nil
}

// ─── form builders ─────────────────────────────────────────────────────────

// arrowKeyMap returns a huh keymap where ↑↓ navigate between Input fields
// in addition to the default enter/tab bindings.
func arrowKeyMap() *huh.KeyMap {
	km := huh.NewDefaultKeyMap()
	km.Input.Next = key.NewBinding(key.WithKeys("enter", "tab", "down"), key.WithHelp("↓/enter", "next"))
	km.Input.Prev = key.NewBinding(key.WithKeys("shift+tab", "up"), key.WithHelp("↑", "prev"))
	return km
}

func (m *model) buildNodeForm() *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("Host name").Placeholder("node-tokyo").Value(&m.nodeHostName),
			huh.NewInput().Title("IP address").Placeholder("1.2.3.4").Value(&m.nodeIP),
			huh.NewInput().Title("SSH user").Value(&m.nodeUser),
			huh.NewInput().Title("SSH key path (optional)").Placeholder("~/.ssh/id_ed25519").Value(&m.nodeSSHKey),
			huh.NewInput().Title("Panel port").Value(&m.nodePanelPort),
			huh.NewInput().Title("Inbound port").Value(&m.nodeInboundPort),
			huh.NewInput().Title("Fingerprint").Value(&m.nodeFingerprint),
			huh.NewInput().Title("Reality target").Placeholder("example.com:443").Value(&m.nodeTarget),
			huh.NewInput().Title("Server names (comma-sep)").Placeholder("example.com").Value(&m.nodeServerNames),
		).Title("Add node"),
	).WithWidth(m.formWidth()).WithHeight(m.formHeight()).WithShowHelp(false).WithKeyMap(arrowKeyMap())
}

func (m *model) buildEditNodeForm() *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("Host name").Value(&m.nodeHostName).
				Description("(read-only — rename not supported)"),
			huh.NewInput().Title("IP address").Value(&m.nodeIP),
			huh.NewInput().Title("SSH user").Value(&m.nodeUser),
			huh.NewInput().Title("SSH key path (optional)").Placeholder("~/.ssh/id_ed25519").Value(&m.nodeSSHKey),
			huh.NewInput().Title("Panel port").Value(&m.nodePanelPort),
			huh.NewInput().Title("Inbound port").Value(&m.nodeInboundPort),
			huh.NewInput().Title("Fingerprint").Value(&m.nodeFingerprint),
			huh.NewInput().Title("Reality target").Placeholder("example.com:443").Value(&m.nodeTarget),
			huh.NewInput().Title("Server names (comma-sep)").Value(&m.nodeServerNames),
		).Title("Edit node  "+m.editNodeName),
	).WithWidth(m.formWidth()).WithHeight(m.formHeight()).WithShowHelp(false).WithKeyMap(arrowKeyMap())
}

func (m *model) submitEditNode() tea.Cmd {
	name := m.editNodeName
	existing, ok := m.app.hosts.All.Hosts[name]
	if !ok {
		m.logf(sErr, "✗ Node %q not found", name)
		m.mode = modeNodes
		return nil
	}
	panelPort, _ := strconv.Atoi(m.nodePanelPort)
	inboundPort, _ := strconv.Atoi(m.nodeInboundPort)
	existing.AnsibleHost = strings.TrimSpace(m.nodeIP)
	existing.AnsibleUser = strings.TrimSpace(m.nodeUser)
	existing.SSHKeyPath = strings.TrimSpace(m.nodeSSHKey)
	existing.PanelPort = panelPort
	existing.Inbound.Port = inboundPort
	existing.Inbound.RealityTarget = strings.TrimSpace(m.nodeTarget)
	existing.Inbound.ServerNames = splitTrim(m.nodeServerNames)
	existing.Inbound.Fingerprint = strings.TrimSpace(m.nodeFingerprint)
	m.app.hosts.All.Hosts[name] = existing
	if err := m.app.saveAll(); err != nil {
		m.logf(sErr, "✗ Save failed: %v", err)
	} else {
		m.logf(sOK, "» Updated node %s", name)
	}
	m.mode = modeNodes
	return nil
}

func (m *model) buildClientForm() *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Title("Client name").Placeholder("alice").Value(&m.clientName),
		).Title("Add client"),
	).WithWidth(m.formWidth()).WithHeight(m.formHeight()).WithShowHelp(false).WithKeyMap(arrowKeyMap())
}

func (m *model) buildPathForm() *huh.Form {
	names := m.app.hosts.HostNames()
	opts := make([]huh.Option[string], len(names))
	for i, n := range names {
		opts[i] = huh.NewOption(n, n)
	}

	fields := []huh.Field{
		huh.NewInput().Title("Path name").Placeholder("relay-a-b").Value(&m.pathName),
	}
	for i := 0; i < m.pathHopCount; i++ {
		idx := i
		o := make([]huh.Option[string], len(opts))
		copy(o, opts)
		fields = append(fields, huh.NewSelect[string]().
			Title(fmt.Sprintf("Hop %d", idx+1)).
			Options(o...).
			Value(&m.pathHopVals[idx]))
	}

	return huh.NewForm(
		huh.NewGroup(fields...).
			Title("Add path").
			Description("ctrl+n add hop · ctrl+d remove last hop"),
	).WithWidth(m.formWidth()).WithHeight(m.formHeight()).WithShowHelp(false).WithKeyMap(arrowKeyMap())
}

func (m *model) buildDeployForm() *huh.Form {
	targetOpts := []huh.Option[string]{huh.NewOption("All nodes", "all")}
	for _, name := range m.app.hosts.HostNames() {
		targetOpts = append(targetOpts, huh.NewOption(name, name))
	}
	actionOpts := []huh.Option[string]{
		huh.NewOption("Full (bootstrap → sync → keys)", "full"),
		huh.NewOption("Bootstrap only", "bootstrap"),
		huh.NewOption("Sync only", "sync"),
		huh.NewOption("Write keys", "keys"),
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewSelect[string]().Title("Target").Options(targetOpts...).Value(&m.deployTarget),
			huh.NewSelect[string]().Title("Action").Options(actionOpts...).Value(&m.deployAction),
		).Title("Deploy"),
	).WithWidth(m.formWidth()).WithHeight(m.formHeight()).WithShowHelp(false).WithKeyMap(arrowKeyMap())
}

// ─── form submission ────────────────────────────────────────────────────────

func (m *model) handleFormComplete() tea.Cmd {
	m.form = nil
	switch m.mode {
	case modeAddNode:
		return m.submitNode()
	case modeEditNode:
		return m.submitEditNode()
	case modeAddClient:
		return m.submitClient()
	case modeAddPath:
		return m.submitPath()
	case modeDeploy:
		return m.startDeploy()
	}
	m.mode = modeMenu
	return nil
}

func (m *model) submitNode() tea.Cmd {
	hostName := strings.TrimSpace(m.nodeHostName)
	ip := strings.TrimSpace(m.nodeIP)
	if hostName == "" || ip == "" {
		m.logf(sErr, "✗ Host name and IP are required")
		m.mode = modeMenu
		return nil
	}
	if _, exists := m.app.hosts.All.Hosts[hostName]; exists {
		m.logf(sErr, "✗ Host %q already exists", hostName)
		m.mode = modeMenu
		return nil
	}

	panelPort, _ := strconv.Atoi(m.nodePanelPort)
	inboundPort, _ := strconv.Atoi(m.nodeInboundPort)
	serverNames := splitTrim(m.nodeServerNames)

	priv, pub, err := crypto.GenX25519()
	if err != nil {
		m.logf(sErr, "✗ Keygen failed: %v", err)
		m.mode = modeMenu
		return nil
	}
	shortID := crypto.GenShortID()

	m.app.hosts.All.Hosts[hostName] = config.Node{
		AnsibleHost: ip,
		AnsibleUser: m.nodeUser,
		SSHKeyPath:  strings.TrimSpace(m.nodeSSHKey),
		PanelPort:   panelPort,
		Inbound: config.Inbound{
			Port:          inboundPort,
			Tag:           "vless-in-" + hostName,
			RealityTarget: m.nodeTarget,
			ServerNames:   serverNames,
			Fingerprint:   m.nodeFingerprint,
		},
	}
	basePath := "/" + crypto.GenShortID() + "/"
	m.app.vault.Nodes[hostName] = config.VaultNode{
		AdminPassword: crypto.GenPassword(24),
		APIToken:      crypto.GenUUID(),
		WebBasePath:   basePath,
		PrivateKey:    priv,
		PublicKey:     pub,
		ShortIDs:      []string{shortID},
	}

	// Auto-create relay UUIDs for any existing path legs involving this new host
	for _, path := range m.app.vars.Paths {
		for i := 0; i < len(path.Hops)-1; i++ {
			up, down := path.Hops[i], path.Hops[i+1]
			legKey := up + "_to_" + down
			if (up == hostName || down == hostName) && m.app.vault.RelayClients[legKey].UUID == "" {
				m.app.vault.RelayClients[legKey] = config.VaultRelay{UUID: crypto.GenUUID()}
			}
		}
	}

	if err := m.app.saveAll(); err != nil {
		m.logf(sErr, "✗ Save failed: %v", err)
		m.mode = modeMenu
		return nil
	}

	m.logf(sBold, "» Added node %s", hostName)
	m.logf(sNorm, "  IP: %s  panel: %d  inbound: %d", ip, panelPort, inboundPort)
	m.logf(sDim, "  Panel path: %s", basePath)
	m.logf(sDim, "  Public key: %s", pub)
	m.logf(sDim, "  Short ID:   %s", shortID)
	m.mode = modeMenu
	return nil
}

func (m *model) submitClient() tea.Cmd {
	name := strings.TrimSpace(m.clientName)
	if name == "" {
		m.logf(sErr, "✗ Client name is required")
		m.mode = modeMenu
		return nil
	}
	if _, exists := m.app.vault.Clients[name]; exists {
		m.logf(sErr, "✗ Client %q already exists", name)
		m.mode = modeMenu
		return nil
	}

	uuid := crypto.GenUUID()
	subID := crypto.GenUUID()
	m.app.vars.Clients = append(m.app.vars.Clients, config.Client{Name: name})
	m.app.vault.Clients[name] = config.VaultClient{UUID: uuid, SubID: subID}

	if err := m.app.saveAll(); err != nil {
		m.logf(sErr, "✗ Save failed: %v", err)
		m.mode = modeMenu
		return nil
	}

	m.logf(sBold, "» Added client %s", name)
	m.logf(sDim, "  UUID:   %s", uuid)
	m.logf(sDim, "  Sub ID: %s", subID)
	m.mode = modeMenu
	return nil
}

func (m *model) submitPath() tea.Cmd {
	name := strings.TrimSpace(m.pathName)
	if name == "" {
		m.logf(sErr, "✗ Path name is required")
		m.mode = modeMenu
		return nil
	}

	hops := make([]string, m.pathHopCount)
	for i := range hops {
		hops[i] = m.pathHopVals[i]
	}

	var newLegs []string
	for i := 0; i < len(hops)-1; i++ {
		up, down := hops[i], hops[i+1]
		legKey := up + "_to_" + down
		if m.app.vault.RelayClients[legKey].UUID == "" {
			m.app.vault.RelayClients[legKey] = config.VaultRelay{UUID: crypto.GenUUID()}
			newLegs = append(newLegs, legKey)
		}
	}
	m.app.vars.Paths = append(m.app.vars.Paths, config.Path{Name: name, Hops: hops})

	if err := m.app.saveAll(); err != nil {
		m.logf(sErr, "✗ Save failed: %v", err)
		m.mode = modeMenu
		return nil
	}

	m.logf(sBold, "» Added path %s", name)
	m.logf(sNorm, "  Hops: %s", strings.Join(hops, " → "))
	for _, leg := range newLegs {
		m.logf(sDim, "  New relay UUID: %s", leg)
	}
	m.mode = modeMenu
	return nil
}

// ─── deploy ─────────────────────────────────────────────────────────────────

func (m *model) startDeploy() tea.Cmd {
	if len(m.app.hosts.All.Hosts) == 0 {
		m.logf(sErr, "✗ No nodes configured")
		m.mode = modeMenu
		return nil
	}

	action := deploy.Action(m.deployAction)
	m.logf(sBold, "» deploy  target=%s  action=%s", m.deployTarget, action)

	eventCh, errCh := deploy.Run(
		m.app.hosts, m.app.vars, m.app.vault,
		m.app.infraDir, m.deployTarget, action,
	)

	m.deployEventCh = eventCh
	m.deployErrCh = errCh
	m.deployRunning = true
	m.mode = modeDeployRun
	return m.awaitDeployEvent()
}

func (m *model) awaitDeployEvent() tea.Cmd {
	eventCh := m.deployEventCh
	errCh := m.deployErrCh
	return func() tea.Msg {
		ev, ok := <-eventCh
		if !ok {
			return deployDoneMsg{err: <-errCh}
		}
		return deployEventMsg{ev: ev}
	}
}

// ─── scanner ────────────────────────────────────────────────────────────────

func (m *model) updateScanner(key tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch key.Type {
	case tea.KeyEsc:
		if m.scanRunning {
			select {
			case m.scanStopCh <- struct{}{}:
			default:
			}
		}
		m.mode = modeMenu
		return m, nil

	case tea.KeyTab:
		switch m.scanFocus {
		case 0:
			m.scanCIDR.Blur()
			m.scanWorkers.Focus()
			m.scanFocus = 1
		case 1:
			m.scanWorkers.Blur()
			m.scanTable.Focus()
			m.scanFocus = 2
		default:
			m.scanTable.Blur()
			m.scanCIDR.Focus()
			m.scanFocus = 0
		}
		return m, nil

	case tea.KeyEnter:
		if m.scanFocus == 2 {
			row := m.scanTable.SelectedRow()
			if len(row) >= 2 {
				domain := row[1]
				if domain == "" {
					domain = row[0]
				}
				m.logf(sOK, "✓ Scanner: %s  (use as Reality target: %s:443)", domain, domain)
				m.mode = modeMenu
				return m, nil
			}
		}
	}

	switch key.String() {
	case "r":
		if m.scanRunning {
			select {
			case m.scanStopCh <- struct{}{}:
			default:
			}
			return m, nil
		}
		return m.startScan()
	case "q":
		if !m.scanRunning {
			m.mode = modeMenu
			return m, nil
		}
	}

	var cmd tea.Cmd
	switch m.scanFocus {
	case 0:
		m.scanCIDR, cmd = m.scanCIDR.Update(key)
	case 1:
		m.scanWorkers, cmd = m.scanWorkers.Update(key)
	case 2:
		m.scanTable, cmd = m.scanTable.Update(key)
	}
	return m, cmd
}

func (m *model) startScan() (tea.Model, tea.Cmd) {
	cidr := m.scanCIDR.Value()
	if cidr == "" {
		cidr = "0.0.0.0/24"
	}
	workers, _ := strconv.Atoi(m.scanWorkers.Value())
	if workers <= 0 {
		workers = 100
	}
	m.scanStopCh = make(chan struct{}, 1)
	total, resultCh, progCh := scan.ScanCIDR(cidr, 443, workers, 5*time.Second)
	m.scanCh = resultCh
	m.scanProgCh = progCh
	m.scanTotal = total
	m.scanChecked = 0
	m.scanRunning = true
	m.scanTable.SetRows(nil)
	return m, tea.Batch(m.awaitScanResult(), m.awaitScanProgress(), m.scanSpinner.Tick)
}

func (m *model) awaitScanResult() tea.Cmd {
	ch := m.scanCh
	stopCh := m.scanStopCh
	return func() tea.Msg {
		select {
		case r, ok := <-ch:
			if !ok {
				return scanDoneMsg{}
			}
			return scanResultMsg{r: r}
		case <-stopCh:
			return scanDoneMsg{}
		}
	}
}

func (m *model) awaitScanProgress() tea.Cmd {
	ch := m.scanProgCh
	return func() tea.Msg {
		if _, ok := <-ch; ok {
			return scanProgressMsg{}
		}
		return nil
	}
}

func (m *model) scanView() string {
	var statusTxt string
	if m.scanRunning {
		statusTxt = m.scanSpinner.View() + sWarn.Render(fmt.Sprintf(" %d/%d  ·  %d found  (r to stop)", m.scanChecked, m.scanTotal, len(m.scanTable.Rows())))
	} else if len(m.scanTable.Rows()) > 0 {
		statusTxt = sOK.Render(fmt.Sprintf("%d feasible hosts", len(m.scanTable.Rows())))
	} else {
		statusTxt = sDim.Render("press r to scan")
	}

	controls := "  " + m.scanCIDR.View() + "   " + m.scanWorkers.View() + "   " + statusTxt
	divider := sDim.Render(strings.Repeat("─", m.width))
	return strings.Join([]string{controls, divider, m.scanTable.View()}, "\n")
}

// ─── credentials view ───────────────────────────────────────────────────────

func (m *model) credsView() string {
	maxH := m.height - 2
	var lines []string
	lines = append(lines, "")
	lines = append(lines, "  "+sBold.Render("Admin username: ")+sNorm.Render(m.app.vault.AdminUsername))
	lines = append(lines, "")
	for _, name := range m.app.hosts.HostNames() {
		node, ok := m.app.vault.Nodes[name]
		if !ok {
			continue
		}
		lines = append(lines, "  "+sBold.Render("─── "+name+" ───"))
		lines = append(lines, "  "+sDim.Render("Password:  ")+sNorm.Render(node.AdminPassword))
		lines = append(lines, "  "+sDim.Render("API token: ")+sNorm.Render(node.APIToken))
		lines = append(lines, "")
	}
	if len(lines) > maxH {
		lines = lines[:maxH]
	}
	return strings.Join(lines, "\n")
}

// ─── keys view ───────────────────────────────────────────────────────────────

type vlessEntry struct {
	label string // "client @ path"
	uri   string
}

func (m *model) buildVLESSEntries() []vlessEntry {
	var out []vlessEntry
	for _, client := range m.app.vars.Clients {
		vc, ok := m.app.vault.Clients[client.Name]
		if !ok {
			continue
		}
		for _, path := range m.app.vars.Paths {
			uri := deploy.BuildVLESSURI(client.Name, vc.UUID, path, m.app.hosts, m.app.vault)
			if uri == "" {
				continue
			}
			out = append(out, vlessEntry{
				label: client.Name + " @ " + path.Name,
				uri:   uri,
			})
		}
	}
	return out
}

func (m *model) updateKeys(msg tea.KeyMsg) tea.Cmd {
	entries := m.buildVLESSEntries()
	n := len(entries)
	switch msg.String() {
	case "esc", "q":
		m.mode = modeMenu
		return nil
	case "up", "k":
		if m.keysCursor > 0 {
			m.keysCursor--
			m.keysVP.SetContent(m.buildKeysContent())
		}
	case "down", "j":
		if m.keysCursor < n-1 {
			m.keysCursor++
			m.keysVP.SetContent(m.buildKeysContent())
		}
	case "enter", "c":
		if n == 0 {
			break
		}
		uri := entries[m.keysCursor].uri
		if err := copyToClipboard(uri); err == nil {
			m.keysCopied = true
			m.keysVP.SetContent(m.buildKeysContent())
			return func() tea.Msg { return keyCopiedMsg{} }
		}
	default:
		// pass scroll events (pgup/pgdn/etc.) to the viewport
		var cmd tea.Cmd
		m.keysVP, cmd = m.keysVP.Update(msg)
		return cmd
	}
	return nil
}

func (m *model) keysVPHeight() int {
	return m.height - 2 // status bar + help bar
}

func (m *model) buildKeysContent() string {
	entries := m.buildVLESSEntries()
	if len(entries) == 0 {
		return "\n  " + sDim.Render("No clients or paths configured.")
	}

	colW := m.width - 6
	if colW < 20 {
		colW = 20
	}
	var sb strings.Builder
	sb.WriteString("\n")
	for i, e := range entries {
		selected := i == m.keysCursor
		cursor := "  "
		labelStyle := sDim
		if selected {
			cursor = "▸ "
			labelStyle = sMenuSel
		}
		sb.WriteString(cursor + labelStyle.Render(e.label))
		if selected && m.keysCopied {
			sb.WriteString("  " + sOK.Render("✓ copied!"))
		}
		sb.WriteString("\n")

		uri := e.uri
		indent := "    "
		for len(uri) > 0 {
			chunk := uri
			if len(chunk) > colW {
				chunk = uri[:colW]
			}
			uri = uri[len(chunk):]
			if selected {
				sb.WriteString(indent + sWarn.Render(chunk) + "\n")
			} else {
				sb.WriteString(indent + sDim.Render(chunk) + "\n")
			}
		}
		sb.WriteString("\n")
	}
	return sb.String()
}

func (m *model) keysView() string {
	if !m.keysVPReady {
		return ""
	}
	return m.keysVP.View()
}

func copyToClipboard(s string) error {
	for _, args := range [][]string{
		{"pbcopy"},
		{"xclip", "-selection", "clipboard"},
		{"xsel", "--clipboard", "--input"},
	} {
		cmd := exec.Command(args[0], args[1:]...)
		cmd.Stdin = strings.NewReader(s)
		if err := cmd.Run(); err == nil {
			return nil
		}
	}
	return fmt.Errorf("no clipboard command available")
}

// ─── nodes list ──────────────────────────────────────────────────────────────

func (m *model) updateNodes(msg tea.KeyMsg) tea.Cmd {
	names := m.app.hosts.HostNames()
	n := len(names)
	switch msg.String() {
	case "esc", "q":
		m.mode = modeMenu
	case "up", "k":
		if m.nodesCursor > 0 {
			m.nodesCursor--
		}
	case "down", "j":
		if m.nodesCursor < n-1 {
			m.nodesCursor++
		}
	case "e", "enter":
		if n == 0 {
			break
		}
		name := names[m.nodesCursor]
		node := m.app.hosts.All.Hosts[name]
		m.editNodeName = name
		m.nodeHostName = name
		m.nodeIP = node.AnsibleHost
		m.nodeUser = node.AnsibleUser
		m.nodeSSHKey = node.SSHKeyPath
		m.nodePanelPort = strconv.Itoa(node.PanelPort)
		m.nodeInboundPort = strconv.Itoa(node.Inbound.Port)
		m.nodeTarget = node.Inbound.RealityTarget
		m.nodeServerNames = strings.Join(node.Inbound.ServerNames, ", ")
		m.nodeFingerprint = node.Inbound.Fingerprint
		return m.enterMode(modeEditNode)
	case "o":
		if n == 0 {
			break
		}
		name := names[m.nodesCursor]
		node := m.app.hosts.All.Hosts[name]
		vn := m.app.vault.Nodes[name]
		url := fmt.Sprintf("https://%s:%d%s", node.AnsibleHost, node.PanelPort, vn.WebBasePath)
		exec.Command("open", url).Start()
	case "c":
		if n == 0 {
			break
		}
		name := names[m.nodesCursor]
		node := m.app.hosts.All.Hosts[name]
		vn := m.app.vault.Nodes[name]
		url := fmt.Sprintf("https://%s:%d%s", node.AnsibleHost, node.PanelPort, vn.WebBasePath)
		copyToClipboard(fmt.Sprintf("%s\nuser: %s\npass: %s", url, m.app.vault.AdminUsername, vn.AdminPassword))
	case "d":
		if n == 0 {
			break
		}
		name := names[m.nodesCursor]
		delete(m.app.hosts.All.Hosts, name)
		delete(m.app.vault.Nodes, name)
		if m.nodesCursor >= len(m.app.hosts.HostNames()) {
			m.nodesCursor = max(0, len(m.app.hosts.HostNames())-1)
		}
		if err := m.app.saveAll(); err != nil {
			m.logf(sErr, "✗ Save failed: %v", err)
		} else {
			m.logf(sWarn, "» Deleted node %s", name)
		}
	}
	return nil
}

func (m *model) nodesView() string {
	names := m.app.hosts.HostNames()
	var sb strings.Builder
	sb.WriteString("\n")
	if len(names) == 0 {
		sb.WriteString("  " + sDim.Render("No nodes configured.") + "\n")
		return sb.String()
	}
	for i, name := range names {
		node := m.app.hosts.All.Hosts[name]
		selected := i == m.nodesCursor
		cursor := "  "
		nameStyle := sNorm
		if selected {
			cursor = "▸ "
			nameStyle = sMenuSel
		}
		sb.WriteString(fmt.Sprintf("  %s%s  %s  panel %d  inbound %d\n",
			cursor,
			nameStyle.Render(name),
			sDim.Render(node.AnsibleHost),
			node.PanelPort,
			node.Inbound.Port,
		))
		if selected {
			vn := m.app.vault.Nodes[name]
			url := fmt.Sprintf("https://%s:%d%s", node.AnsibleHost, node.PanelPort, vn.WebBasePath)
			sb.WriteString(fmt.Sprintf("    %s  %s  %s\n",
				sWarn.Render(url),
				sDim.Render("user: ")+sNorm.Render(m.app.vault.AdminUsername),
				sDim.Render("pass: ")+sNorm.Render(vn.AdminPassword),
			))
		}
	}
	return sb.String()
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

// ─── misc helpers ────────────────────────────────────────────────────────────

func (m *model) logf(style lipgloss.Style, format string, args ...interface{}) {
	ts := sDim.Render(time.Now().Format("15:04:05"))
	m.history = append(m.history, ts+" "+style.Render(fmt.Sprintf(format, args...)))
	m.updateVP()
}

func (m *model) updateVP() {
	if !m.vpReady {
		return
	}
	atBottom := m.vp.AtBottom()
	m.vp.SetContent(strings.Join(m.history, "\n"))
	if atBottom {
		m.vp.GotoBottom()
	}
}

func formatDeployEvent(ev deploy.Event) string {
	ts := sDim.Render(time.Now().Format("15:04:05"))
	node := ""
	if ev.Node != "" {
		node = sDim.Render("["+ev.Node+"] ")
	}
	var style lipgloss.Style
	switch ev.Kind {
	case "ok":
		style = sOK
	case "warn":
		style = sWarn
	case "err":
		style = sErr
	default:
		style = sNorm
	}
	return ts + " " + node + style.Render(ev.Text)
}

func splitTrim(s string) []string {
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if t := strings.TrimSpace(p); t != "" {
			out = append(out, t)
		}
	}
	return out
}
