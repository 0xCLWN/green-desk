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
	var activeSrv io.Closer
	sysProxyOn := false

	var (
		btnEnable   *tview.Button
		btnPort     *tview.Button
		btnSysProxy *tview.Button
	)

	setStatus := func(online bool) {
		if online {
			label := "[green]● Online[-]"
			if name := d.ParseName(d.activeKey()); name != "" {
				label = "[green]● Online — " + tview.Escape(name) + "[-]"
			}
			status.SetText(label)
			btnEnable.SetLabel("Disable    ")
		} else {
			status.SetText("[red]● Stopped[-]")
			btnEnable.SetLabel("Enable     ")
		}
	}

	stopSrv := func() {
		if activeSrv != nil {
			activeSrv.Close()
			activeSrv = nil
		}
		if sysProxyOn && btnSysProxy != nil {
			DisableSystemProxy()
			sysProxyOn = false
			btnSysProxy.SetLabel("Sys Proxy: Off")
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
			app.SetFocus(btnEnable)
			if val != "" {
				onOK(val)
			}
		}
		cancel := func() {
			pages.RemovePage("modal")
			app.SetFocus(btnEnable)
		}

		form := tview.NewForm().
			AddFormItem(field).
			AddButton("OK", confirm).
			AddButton("Cancel", cancel)
		form.SetBorder(true).
			SetTitle(" "+title+" ").
			SetTitleColor(tcell.ColorYellow)

		field.SetDoneFunc(func(key tcell.Key) {
			switch key {
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
				AddItem(tview.NewBox(), 0, 1, false), 70, 1, true).
			AddItem(tview.NewBox(), 0, 1, false)

		pages.AddPage("modal", overlay, true, true)
		app.SetFocus(field)
	}

	// ── buttons ──────────────────────────────────────────────────
	btnEnable = tview.NewButton("Enable     ").SetSelectedFunc(func() {
		if activeSrv != nil {
			stopSrv()
		} else {
			startSrv()
		}
	})

	btnKey := tview.NewButton("Change Key").SetSelectedFunc(func() {
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
		})
	})

	btnPort = tview.NewButton(fmt.Sprintf("SOCKS5: %d", *d.Port)).SetSelectedFunc(func() {
		showInput("SOCKS5 port  (HTTP proxy = port+1)", strconv.Itoa(*d.Port), func(val string) {
			port, err := strconv.Atoi(val)
			if err != nil || port < 1 || port > 65535 {
				fmt.Fprintln(logView, "[red]Invalid port — enter 1–65535[-]")
				return
			}
			*d.Port = port
			btnPort.SetLabel(fmt.Sprintf("SOCKS5: %d", port))
			if activeSrv != nil {
				stopSrv()
				startSrv()
			}
		})
	})

	buttons := []*tview.Button{btnEnable, btnKey, btnPort}

	if SystemProxyAvailable() {
		btnSysProxy = tview.NewButton("Sys Proxy: Off").SetSelectedFunc(func() {
			if sysProxyOn {
				DisableSystemProxy()
				sysProxyOn = false
				btnSysProxy.SetLabel("Sys Proxy: Off")
			} else {
				if activeSrv == nil {
					fmt.Fprintln(logView, "[yellow]Start the proxy first[-]")
					return
				}
				if err := EnableSystemProxy(*d.Port); err != nil {
					fmt.Fprintf(logView, "[red]System proxy failed: %v[-]\n", err)
					return
				}
				sysProxyOn = true
				btnSysProxy.SetLabel("Sys Proxy: On ")
			}
			app.Draw()
		})
		buttons = append(buttons, btnSysProxy)
	}

	// ── button bar ───────────────────────────────────────────────
	bar := tview.NewFlex()
	bar.AddItem(tview.NewBox(), 1, 0, false)
	for i, btn := range buttons {
		if i > 0 {
			bar.AddItem(tview.NewBox(), 2, 0, false)
		}
		bar.AddItem(btn, 0, 1, i == 0)
	}
	bar.AddItem(tview.NewBox(), 1, 0, false)

	root := tview.NewFlex().SetDirection(tview.FlexRow).
		AddItem(status, 1, 0, false).
		AddItem(logView, 0, 1, false).
		AddItem(bar, 1, 0, true)

	pages.AddPage("main", root, true, true)

	// ── key bindings ─────────────────────────────────────────────
	focusIdx := 0
	app.SetInputCapture(func(event *tcell.EventKey) *tcell.EventKey {
		if pages.HasPage("modal") {
			return event
		}
		switch event.Key() {
		case tcell.KeyTab:
			focusIdx = (focusIdx + 1) % len(buttons)
			app.SetFocus(buttons[focusIdx])
			return nil
		case tcell.KeyBacktab:
			focusIdx = (focusIdx - 1 + len(buttons)) % len(buttons)
			app.SetFocus(buttons[focusIdx])
			return nil
		case tcell.KeyUp, tcell.KeyDown:
			app.SetFocus(logView)
			return event
		case tcell.KeyCtrlC:
			DisableSystemProxy()
			if activeSrv != nil {
				activeSrv.Close()
			}
			app.Stop()
			return nil
		}
		return event
	})

	// SIGTERM (macOS/Linux shutdown, systemd stop) — best-effort cleanup.
	// SIGKILL and Windows shutdown can't be caught; startup cleanup handles those.
	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
		<-sig
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
				if btnSysProxy != nil {
					btnSysProxy.SetLabel("Sys Proxy: On ")
				}
			}
		},
	)

	if err := app.SetRoot(pages, true).Run(); err != nil {
		panic(err)
	}
}
