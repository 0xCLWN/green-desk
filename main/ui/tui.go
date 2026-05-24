//go:build !systray

package ui

import (
	"bufio"
	"fmt"
	"io"
	"log"
	"os"
	"os/signal"
	"runtime"
	"runtime/debug"
	"strconv"
	"strings"
	"syscall"

	"github.com/gdamore/tcell/v2"
	"github.com/rivo/tview"

	"github.com/0x1488/xray-core/common/cmdarg"
)

func Start(d *Deps) {
	if !acquireSingleInstance(*d.Port) {
		fmt.Fprintln(os.Stderr, "xray-tray: another instance is already running (port", *d.Port, "in use)")
		os.Exit(1)
	}
	defer releaseInstanceLock()

	if d.AutoStartup {
		_ = EnableStartup()
	}
	CleanupStaleProxy(*d.Port)

	app := tview.NewApplication()
	pages := tview.NewPages()

	// ── status bar ───────────────────────────────────────────────
	status := tview.NewTextView().SetDynamicColors(true)
	status.SetText("[red]● Stopped[-]")

	// ── log view ─────────────────────────────────────────────────
	logView := tview.NewTextView().
		SetDynamicColors(true).
		SetScrollable(true).
		SetMaxLines(2000)
	logView.SetBorder(true).
		SetTitle("  Logs (↑↓ scroll)  ").
		SetTitleColor(tcell.ColorGray)
	logView.SetChangedFunc(func() {
		app.QueueUpdateDraw(func() { logView.ScrollToEnd() })
	})

	r, w, _ := os.Pipe()
	os.Stdout = w
	os.Stderr = w
	log.SetOutput(w)
	go func() {
		sc := bufio.NewScanner(r)
		for sc.Scan() {
			fmt.Fprintln(logView, sc.Text())
		}
	}()

	// ── state ────────────────────────────────────────────────────
	var (
		activeSrv       io.Closer
		stopHealth      func()
		sysProxyAutoOff bool
	)
	sysProxyOn := false

	// ── hints bar ────────────────────────────────────────────────
	hintsView := tview.NewTextView().SetDynamicColors(true)

	key := func(k, label string) string {
		return "[black:yellow] " + k + " [-:-] " + label
	}

	updateHints := func() {
		parts := []string{key("E", "enable")}
		if activeSrv != nil {
			parts[0] = key("E", "disable")
		}
		parts = append(parts, key("K", "key"), key("P", fmt.Sprintf("port %d", *d.Port)))
		if SystemProxyAvailable() {
			if !NetAdminAvailable() {
				parts = append(parts, "[gray]S  sys-proxy (need sudo)[-]")
			} else if sysProxyOn {
				parts = append(parts, key("S", "sys-proxy: on "))
			} else {
				parts = append(parts, key("S", "sys-proxy: off"))
			}
		}
		parts = append(parts, key("Q", "quit"))
		hintsView.SetText("  " + strings.Join(parts, "  "))
	}

	// ── status helpers ───────────────────────────────────────────
	setStatus := func(online bool) {
		if online {
			label := "[green]● Online[-]"
			if name := d.ParseName(d.activeKey()); name != "" {
				label = "[green]● Online — " + tview.Escape(name) + "[-]"
			}
			status.SetText(label)
		} else {
			status.SetText("[red]● Stopped[-]")
		}
	}

	// ── stop / start ─────────────────────────────────────────────
	stopSrv := func() {
		if stopHealth != nil {
			stopHealth()
			stopHealth = nil
		}
		sysProxyAutoOff = false
		if activeSrv != nil {
			activeSrv.Close()
			activeSrv = nil
		}
		if sysProxyOn {
			DisableSystemProxy()
			sysProxyOn = false
		}
		setStatus(false)
	}

	startSrv := func() {
		srv, err := d.StartXray()
		if err != nil {
			fmt.Fprintf(logView, "[red]Failed to start: %v[-]\n", err)
			return
		}
		activeSrv = srv
		runtime.GC()
		debug.FreeOSMemory()
		setStatus(true)
		stopHealth = startHealthWatch(*d.Port,
			func() {
				app.QueueUpdateDraw(func() {
					status.SetText("[red]● Upstream down[-]")
					if sysProxyOn {
						DisableSystemProxy()
						sysProxyOn = false
						sysProxyAutoOff = true
					}
					updateHints()
				})
			},
			func() {
				app.QueueUpdateDraw(func() {
					setStatus(true)
					if sysProxyAutoOff {
						if err := EnableSystemProxy(*d.Port); err == nil {
							sysProxyOn = true
							sysProxyAutoOff = false
						}
					}
					updateHints()
				})
			},
		)
	}

	// ── input modal ──────────────────────────────────────────────
	showInput := func(title, initial string, onOK func(string)) {
		field := tview.NewInputField().
			SetText(initial).
			SetFieldWidth(0).
			SetFieldBackgroundColor(tcell.ColorDarkSlateGray)

		confirm := func() {
			val := strings.TrimSpace(field.GetText())
			pages.RemovePage("modal")
			if val != "" {
				onOK(val)
			}
		}
		cancel := func() { pages.RemovePage("modal") }

		form := tview.NewForm().
			AddFormItem(field).
			AddButton("OK", confirm).
			AddButton("Cancel", cancel)
		form.SetBorder(true).
			SetTitle(" " + title + " ").
			SetTitleColor(tcell.ColorYellow)

		field.SetDoneFunc(func(k tcell.Key) {
			switch k {
			case tcell.KeyEnter:
				confirm()
			case tcell.KeyEscape:
				cancel()
			}
		})

		overlay := tview.NewFlex().
			AddItem(tview.NewBox(), 0, 1, false).
			AddItem(tview.NewFlex().SetDirection(tview.FlexRow).
				AddItem(tview.NewBox(), 0, 1, false).
				AddItem(form, 7, 1, true).
				AddItem(tview.NewBox(), 0, 1, false), 60, 1, true).
			AddItem(tview.NewBox(), 0, 1, false)

		pages.AddPage("modal", overlay, true, true)
		app.SetFocus(field)
	}

	// ── layout ───────────────────────────────────────────────────
	root := tview.NewFlex().SetDirection(tview.FlexRow).
		AddItem(status, 1, 0, false).
		AddItem(logView, 0, 1, false).
		AddItem(hintsView, 1, 0, false)

	pages.AddPage("main", root, true, true)

	// ── global key handler ───────────────────────────────────────
	app.SetInputCapture(func(event *tcell.EventKey) *tcell.EventKey {
		if pages.HasPage("modal") {
			return event
		}
		switch event.Key() {
		case tcell.KeyUp, tcell.KeyDown:
			app.SetFocus(logView)
			return event
		case tcell.KeyCtrlC:
			stopSrv()
			app.Stop()
			return nil
		}
		switch event.Rune() {
		case 'e', 'E':
			if activeSrv != nil {
				stopSrv()
			} else {
				startSrv()
			}
			updateHints()
			return nil
		case 'k', 'K':
			showInput("Paste a vless:// key", d.activeKey(), func(val string) {
				if err := d.ValidateKey(val); err != nil {
					fmt.Fprintf(logView, "[red]Invalid key: %v[-]\n", err)
					return
				}
				*d.ConfigFiles = cmdarg.Arg{val}
				if activeSrv != nil {
					stopSrv()
					startSrv()
				}
				updateHints()
			})
			return nil
		case 'p', 'P':
			showInput("SOCKS5 port  (HTTP proxy = port+1)", strconv.Itoa(*d.Port), func(val string) {
				port, err := strconv.Atoi(val)
				if err != nil || port < 1 || port > 65535 {
					fmt.Fprintln(logView, "[red]Invalid port — enter 1–65535[-]")
					return
				}
				*d.Port = port
				if activeSrv != nil {
					stopSrv()
					startSrv()
				}
				updateHints()
			})
			return nil
		case 's', 'S':
			if !SystemProxyAvailable() {
				return event
			}
			if !NetAdminAvailable() {
				fmt.Fprintln(logView, "[red]System proxy requires root or CAP_NET_ADMIN.\nRun with sudo, or: sudo setcap cap_net_admin+ep ./xray-tray[-]")
				return nil
			}
			if sysProxyOn {
				DisableSystemProxy()
				sysProxyOn = false
				sysProxyAutoOff = false
			} else {
				if activeSrv == nil {
					fmt.Fprintln(logView, "[yellow]Start the proxy first[-]")
					return nil
				}
				if err := EnableSystemProxy(*d.Port); err != nil {
					fmt.Fprintf(logView, "[red]System proxy failed: %v[-]\n", err)
					return nil
				}
				sysProxyOn = true
			}
			updateHints()
			app.Draw()
			return nil
		case 'q', 'Q':
			stopSrv()
			app.Stop()
			return nil
		}
		return event
	})

	// SIGTERM (systemd stop, macOS shutdown) — best-effort cleanup.
	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
		<-sig
		if stopHealth != nil {
			stopHealth()
		}
		DisableSystemProxy()
		if activeSrv != nil {
			activeSrv.Close()
		}
		app.Stop()
	}()

	d.PrintVersion()

	d.applyDefaults(
		func() bool { startSrv(); return activeSrv != nil },
		func() {
			if err := EnableSystemProxy(*d.Port); err == nil {
				sysProxyOn = true
			}
		},
	)
	updateHints()

	if err := app.SetRoot(pages, true).Run(); err != nil {
		panic(err)
	}
}
