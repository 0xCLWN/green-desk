package green

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import green.ui.MainWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Window as AwtWindow

fun main() {
    System.setProperty("apple.awt.application.name", "Green")
    System.setProperty("apple.awt.UIElement", "true")

    val appDir = appDataDir()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vm = AppViewModel(
        keyStore = KeyStore(appDir),
        settingsStore = SettingsStore(appDir),
        xray = XrayProcess(appDir),
        scope = scope,
    )

    application(exitProcessOnExit = true) {
        val state by vm.state.collectAsState()
        var windowVisible by remember { mutableStateOf(true) }
        var focusTrigger by remember { mutableStateOf(0) }

        fun openWindow() { windowVisible = true; focusTrigger++ }

        Tray(
            icon = circlePainter(
                color = when {
                    state.error != null -> Color(0xFFEF4444)
                    state.running -> Color(0xFF22C55E)
                    else -> Color(0xFF6B7280)
                }
            ),
            tooltip = if (state.running) "Green — Connected" else "Green — Disconnected",
            onAction = { openWindow() },
            menu = {
                Item("Open", onClick = { openWindow() })
                Separator()
                Item(
                    text = if (state.running) "Disconnect" else "Connect",
                    enabled = state.activeKeyId != null,
                    onClick = vm::toggleProxy,
                )
                CheckboxItem(
                    text = "System Proxy",
                    checked = state.sysProxyEnabled,
                    onCheckedChange = { vm.toggleSysProxy() },
                )
                Separator()
                Item("Quit", onClick = {
                    vm.onExit()
                    exitApplication()
                })
            },
        )

        Window(
            onCloseRequest = { windowVisible = false },
            visible = windowVisible,
            title = "Green",
            state = rememberWindowState(size = DpSize(420.dp, 540.dp)),
            resizable = false,
        ) {
            val awtWindow = window
            LaunchedEffect(focusTrigger) {
                if (windowVisible) bringToFront(awtWindow)
            }
            MainWindow(
                state = state,
                onToggleProxy = vm::toggleProxy,
                onToggleSysProxy = vm::toggleSysProxy,
                onAddKey = vm::addKey,
                onRemoveKey = vm::removeKey,
                onActivateKey = vm::activateKey,
                onRenameKey = vm::renameKey,
                onEditKey = vm::editKey,
                onUpdatePorts = vm::updatePorts,
                onInstallUpdate = { state.availableUpdate?.let(vm::installUpdate) },
                onDismissUpdate = vm::dismissUpdate,
                onCheckUpdate = vm::checkUpdate,
            )
        }
    }
}

private fun bringToFront(window: AwtWindow) {
    if (isMac) {
        // activateIgnoringOtherApps — without this UIElement apps can't steal focus
        runCatching {
            val cls = Class.forName("com.apple.eawt.Application")
            val app = cls.getMethod("getApplication").invoke(null)
            cls.getMethod("requestForeground", Boolean::class.javaPrimitiveType).invoke(app, true)
        }
    }
    window.isAlwaysOnTop = true
    window.toFront()
    window.requestFocus()
    // release after macOS has processed the window ordering
    javax.swing.Timer(150) { window.isAlwaysOnTop = false }.apply { isRepeats = false; start() }
}

private fun circlePainter(color: Color): Painter = object : Painter() {
    override val intrinsicSize = Size(22f, 22f)
    override fun DrawScope.onDraw() {
        drawCircle(color, radius = size.minDimension / 2f - 3f)
    }
}
