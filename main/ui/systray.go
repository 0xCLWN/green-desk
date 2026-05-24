//go:build systray

package ui

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"io"
	"math"
	"os"
	"os/signal"
	"runtime"
	"runtime/debug"
	"strconv"
	"strings"
	"syscall"

	"github.com/getlantern/systray"
	"github.com/ncruces/zenity"

	"github.com/0x1488/xray-core/common/cmdarg"
)

func circleIcon(r, g, b uint8) []byte {
	const size = 64
	img := image.NewRGBA(image.Rect(0, 0, size, size))
	cx, cy := float64(size-1)/2, float64(size-1)/2
	outer := float64(size)/2 - 2.0
	borderW := 3.5

	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			dx := float64(x) - cx
			dy := float64(y) - cy
			d := math.Sqrt(dx*dx + dy*dy)
			if d >= outer {
				continue
			}
			a := 1.0
			if d > outer-1 {
				a = outer - d
			}
			alpha := uint8(255 * a)
			if d >= outer-borderW {
				img.Set(x, y, color.RGBA{20, 20, 20, alpha})
			} else {
				img.Set(x, y, color.RGBA{r, g, b, alpha})
			}
		}
	}

	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	pngData := buf.Bytes()

	// Windows systray requires ICO format — wrap the PNG in a minimal ICO container.
	// Since Vista, ICO files may contain PNG-compressed images directly.
	if runtime.GOOS == "windows" {
		return pngToICO(pngData, 64)
	}
	return pngData
}

// pngToICO wraps raw PNG bytes in a single-image ICO container.
// The ICO header is 6 bytes (ICONDIR) + 16 bytes (ICONDIRENTRY) = 22 bytes.
func pngToICO(pngData []byte, size int) []byte {
	var buf bytes.Buffer
	// ICONDIR
	binary.Write(&buf, binary.LittleEndian, uint16(0)) // reserved
	binary.Write(&buf, binary.LittleEndian, uint16(1)) // type: icon
	binary.Write(&buf, binary.LittleEndian, uint16(1)) // image count
	// ICONDIRENTRY
	w := byte(size)
	if size >= 256 {
		w = 0 // 0 means 256 in ICO spec
	}
	buf.WriteByte(w)                                                    // width
	buf.WriteByte(w)                                                    // height
	buf.WriteByte(0)                                                    // palette size (0 = no palette)
	buf.WriteByte(0)                                                    // reserved
	binary.Write(&buf, binary.LittleEndian, uint16(1))                 // color planes
	binary.Write(&buf, binary.LittleEndian, uint16(32))                // bits per pixel
	binary.Write(&buf, binary.LittleEndian, uint32(len(pngData)))      // image data size
	binary.Write(&buf, binary.LittleEndian, uint32(6+16))              // image data offset
	buf.Write(pngData)
	return buf.Bytes()
}

var (
	iconOn   = circleIcon(34, 197, 94)   // green-500
	iconDown = circleIcon(239, 68, 68)   // red-500
	iconOff  = circleIcon(156, 163, 175) // gray-400
)

// mSysProxyItem is non-nil when SystemProxyAvailable() returns true.
var mSysProxyItem *systray.MenuItem

func Start(d *Deps) {
	if !acquireSingleInstance(*d.Port) {
		_ = zenity.Error(
			fmt.Sprintf("Xray is already running on port %d.\nOnly one instance can run at a time.", *d.Port),
			zenity.Title("Xray — already running"),
		)
		return
	}
	defer releaseInstanceLock()

	if d.AutoStartup {
		_ = EnableStartup()
	}
	CleanupStaleProxy(*d.Port)
	systray.Run(func() { onReady(d) }, nil)
}

func setEnabled(d *Deps, mStatus, mEnable *systray.MenuItem) {
	label := "● Online"
	if name := d.ParseName(d.activeKey()); name != "" {
		label = "● Online — " + name
	}
	mEnable.Check()
	mStatus.SetTitle(label)
	systray.SetIcon(iconOn)
	systray.SetTooltip("Xray — Online")
}

func setDisabled(mStatus, mEnable *systray.MenuItem) {
	mEnable.Uncheck()
	mStatus.SetTitle("● Stopped")
	systray.SetIcon(iconOff)
	systray.SetTooltip("Xray — Stopped")
}

func stopSrv(srv *io.Closer, d *Deps, mStatus, mEnable *systray.MenuItem) {
	if *srv != nil {
		(*srv).Close()
		*srv = nil
	}
	if mSysProxyItem != nil && mSysProxyItem.Checked() {
		DisableSystemProxy()
		mSysProxyItem.Uncheck()
	}
	setDisabled(mStatus, mEnable)
}

func launchSrv(srv *io.Closer, d *Deps, mStatus, mEnable *systray.MenuItem) bool {
	s, err := d.StartXray()
	if err != nil {
		_ = zenity.Error(err.Error(), zenity.Title("Xray — failed to start"))
		setDisabled(mStatus, mEnable)
		return false
	}
	*srv = s
	setEnabled(d, mStatus, mEnable)
	runtime.GC()
	debug.FreeOSMemory()
	return true
}

func onReady(d *Deps) {
	systray.SetIcon(iconOff)
	systray.SetTooltip("Xray — Stopped")

	mStatus := systray.AddMenuItem("● Stopped", "")
	mStatus.Disable()
	systray.AddSeparator()

	mEnable := systray.AddMenuItemCheckbox("Enable", "Start or stop the proxy", false)
	mChangeKey := systray.AddMenuItem("Change Key…", "Switch to a different VLESS key")
	systray.AddSeparator()

	mSocks := systray.AddMenuItem(fmt.Sprintf("SOCKS5 Port: %d", *d.Port), "Click to change")

	var sysProxyCh <-chan struct{}
	if SystemProxyAvailable() {
		tooltip := "Route all traffic through Xray via TUN"
		if !NetAdminAvailable() {
			tooltip = "Requires root or CAP_NET_ADMIN — run with sudo or: sudo setcap cap_net_admin+ep ./xray-tray"
		}
		mSysProxyItem = systray.AddMenuItemCheckbox("System Proxy", tooltip, false)
		if !NetAdminAvailable() {
			mSysProxyItem.Disable()
		} else {
			sysProxyCh = mSysProxyItem.ClickedCh
		}
	}

	mStartup := systray.AddMenuItemCheckbox("Launch at Login", "Start Xray automatically at login", StartupEnabled())
	systray.AddSeparator()

	mQuit := systray.AddMenuItem("Quit", "Quit Xray")

	var (
		activeSrv       io.Closer
		stopHealth      func()
		sysProxyAutoOff bool // sys proxy was auto-disabled due to upstream being down
	)

	doStop := func() {
		if stopHealth != nil {
			stopHealth()
			stopHealth = nil
		}
		sysProxyAutoOff = false
		stopSrv(&activeSrv, d, mStatus, mEnable)
	}

	doLaunch := func() bool {
		if !launchSrv(&activeSrv, d, mStatus, mEnable) {
			return false
		}
		stopHealth = startHealthWatch(*d.Port,
			func() { // upstream went down
				systray.SetIcon(iconDown)
				mStatus.SetTitle("● Upstream down")
				systray.SetTooltip("Xray — Upstream down")
				if mSysProxyItem != nil && mSysProxyItem.Checked() {
					DisableSystemProxy()
					mSysProxyItem.Uncheck()
					sysProxyAutoOff = true
				}
			},
			func() { // upstream recovered
				setEnabled(d, mStatus, mEnable)
				if sysProxyAutoOff {
					if EnableSystemProxy(*d.Port) == nil && mSysProxyItem != nil {
						mSysProxyItem.Check()
					}
					sysProxyAutoOff = false
				}
			},
		)
		return true
	}

	d.applyDefaults(
		doLaunch,
		func() {
			if err := EnableSystemProxy(*d.Port); err == nil && mSysProxyItem != nil {
				mSysProxyItem.Check()
			}
		},
	)

	// SIGTERM (macOS/Linux shutdown) — best-effort cleanup.
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
		systray.Quit()
	}()

	for {
		select {
		case <-mEnable.ClickedCh:
			if activeSrv != nil {
				doStop()
			} else {
				doLaunch()
			}

		case <-mChangeKey.ClickedCh:
			result, err := zenity.Entry(
				"Paste a vless:// key:",
				zenity.EntryText(d.activeKey()),
				zenity.Title("Xray — Change Key"),
			)
			if err != nil || result == "" {
				continue
			}
			if !strings.HasPrefix(result, "vless://") {
				_ = zenity.Error("Key must start with vless://", zenity.Title("Xray"))
				continue
			}
			if err := d.ValidateKey(result); err != nil {
				_ = zenity.Error(err.Error(), zenity.Title("Xray — invalid key"))
				continue
			}
			*d.ConfigFiles = cmdarg.Arg{result}
			if activeSrv != nil {
				doStop()
				doLaunch()
			}

		case <-mSocks.ClickedCh:
			result, err := zenity.Entry(
				"SOCKS5 port (HTTP proxy will use port+1):",
				zenity.EntryText(strconv.Itoa(*d.Port)),
				zenity.Title("Xray — SOCKS5 Port"),
			)
			if err != nil || result == "" {
				continue
			}
			port, err := strconv.Atoi(result)
			if err != nil || port < 1 || port > 65535 {
				_ = zenity.Error("Enter a number between 1 and 65535.", zenity.Title("Xray"))
				continue
			}
			*d.Port = port
			mSocks.SetTitle(fmt.Sprintf("SOCKS5 Port: %d", port))
			if activeSrv != nil {
				doStop()
				doLaunch()
			}

		case <-sysProxyCh: // nil when SystemProxyAvailable() is false — never fires
			if mSysProxyItem.Checked() {
				mSysProxyItem.Uncheck()
				DisableSystemProxy()
				sysProxyAutoOff = false
			} else {
				if activeSrv == nil {
					_ = zenity.Error("Start the proxy first, then enable System Proxy.", zenity.Title("Xray"))
					continue
				}
				if err := EnableSystemProxy(*d.Port); err != nil {
					_ = zenity.Error(err.Error(), zenity.Title("Xray — System Proxy failed"))
					continue
				}
				mSysProxyItem.Check()
			}

		case <-mStartup.ClickedCh:
			if mStartup.Checked() {
				if err := DisableStartup(); err != nil {
					_ = zenity.Error(err.Error(), zenity.Title("Xray — Launch at Login"))
				} else {
					mStartup.Uncheck()
				}
			} else {
				if err := EnableStartup(); err != nil {
					_ = zenity.Error(err.Error(), zenity.Title("Xray — Launch at Login"))
				} else {
					mStartup.Check()
				}
			}

		case <-mQuit.ClickedCh:
			if stopHealth != nil {
				stopHealth()
			}
			DisableSystemProxy()
			if activeSrv != nil {
				activeSrv.Close()
			}
			systray.Quit()
			return
		}
	}
}
