package com.green.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.VpnState
import com.green.android.VpnStatus
import com.green.android.data.Config
import com.green.android.nameWithFlag
import com.green.android.ui.components.SplitTunnelLine
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.Glow
import com.green.android.ui.theme.GradA
import com.green.android.ui.theme.GradB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class ProxyConnectException : java.io.IOException()
private class ServerConnectException : java.io.IOException()

private data class DiagResult(
    val serverHost: String,
    val serverPort: Int,
    val serverIp: String?,
    val sni: String,
    val sniIp: String?,
    val sameSubnet: Boolean?,
    val directMs: Long?,
)

@Composable
fun ConnectedLayer(
    visible: Boolean,
    config: Config?,
    allowedApps: Set<String>,
    status: VpnStatus,
    shake: Boolean,
    onDisconnect: () -> Unit,
    onLocked: () -> Unit,
    onSplitTap: () -> Unit,
    onShakeDone: () -> Unit,
) {
    val slideY by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium),
        label = "layer",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )

    // Bloom flash — bright glow pulse when the layer first appears
    var bloomTarget by remember { mutableFloatStateOf(0f) }
    val bloom by animateFloatAsState(bloomTarget, tween(700, easing = FastOutSlowInEasing), label = "bloom")
    LaunchedEffect(visible) {
        if (visible) { bloomTarget = 1f; delay(60); bloomTarget = 0f }
    }

    var shakeX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shake) {
        if (!shake) return@LaunchedEffect
        for (offset in listOf(-6f, 6f, -4f, 4f, -2f, 2f, 0f)) {
            shakeX = offset
            delay(55)
        }
        onShakeDone()
    }

    var testState by remember { mutableStateOf<String?>(null) }
    var diagState by remember { mutableStateOf<DiagResult?>(null) }
    val testOk = testState?.startsWith("ok:") == true
    val testFailed = testState?.startsWith("failed") == true
    val testFailedProxy = testState == "failed:proxy"
    val testFailedServer = testState == "failed:server"
    val testMs = testState?.removePrefix("ok:")?.toLongOrNull()
    val testMsLabel = if (testMs == 0L) "<1" else testMs?.toString()
    LaunchedEffect(visible) { if (!visible) { testState = null; diagState = null } }
    LaunchedEffect(testState) {
        if (testState != "testing") return@LaunchedEffect
        diagState = null
        val port = VpnState.socksPort.value
        val user = VpnState.socksUser.value
        val pass = VpnState.socksPass.value
        if (port == 0) { testState = "failed:proxy"; return@LaunchedEffect }
        val vlessLink = config?.vlessLink
        coroutineScope {
            val mainD = async(Dispatchers.IO) {
                val t0 = System.nanoTime()
                try {
                    testViaSocks5(port, user, pass)
                    "ok:${(System.nanoTime() - t0) / 1_000_000L}"
                } catch (_: ProxyConnectException) { "failed:proxy" }
                  catch (_: ServerConnectException) { "failed:server" }
                  catch (_: Exception) { "failed" }
            }
            val diagD = if (vlessLink != null) async(Dispatchers.IO) { runDiagnostics(vlessLink) } else null
            val result = mainD.await()
            testState = result
            if (result.startsWith("failed")) diagState = diagD?.await() else diagD?.cancel()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = size.height * slideY
                translationX = shakeX.dp.toPx()
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        // Green gradient background
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(GradA, GradB)))
        )
        // Top radial glow (static base + animated bloom on entry)
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Accent.copy(alpha = 0.22f + bloom * 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 22.dp),
        ) {
            // Grab handle
            Box(
                Modifier
                    .size(width = 42.dp, height = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(14.dp))

            // Status row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PulsingDot()
                    Text(
                        if (status == VpnStatus.CONNECTING) stringResource(R.string.status_connecting) else stringResource(R.string.status_connected),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(0.22f))
                        .clickable(onClick = onLocked)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFFCDEED8), modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.settings_locked), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCDEED8))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(config?.name?.let { nameWithFlag(it) } ?: "—", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = config?.vlessLink?.substringAfter("@")?.substringBefore("?") ?: ""
            Text(if (meta.isNotEmpty()) "$meta · vless" else "vless", fontSize = 13.sp, color = Color(0xFFAEE6C2), fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))

            SplitTunnelLine(allowedApps = allowedApps, readOnly = true, onClick = onSplitTap)
            Spacer(Modifier.height(18.dp))

            Spacer(Modifier.weight(1f))

            // Test connection
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp, when {
                            testOk -> Glow
                            testFailed -> Danger.copy(alpha = 0.6f)
                            else -> Color.White.copy(0.2f)
                        }, RoundedCornerShape(16.dp)
                    )
                    .background(Color.Black.copy(0.18f))
                    .clickable { if (testState != "testing") testState = "testing" }
                    .padding(13.dp),
                contentAlignment = Alignment.Center,
            ) {
                val testColor = when { testOk -> Glow; testFailed -> Danger; else -> Color.White }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    when {
                        testState == "testing" -> CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        testFailed -> Icon(Icons.Default.Close, null, tint = Danger, modifier = Modifier.size(15.dp))
                        else -> Icon(Icons.Default.Bolt, null, tint = testColor, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        when {
                            testState == "testing" -> stringResource(R.string.test_testing)
                            testOk -> stringResource(R.string.test_ok, testMsLabel ?: "0")
                            testFailedProxy -> stringResource(R.string.test_failed_proxy)
                            testFailedServer -> stringResource(R.string.test_failed_server)
                            testFailed -> stringResource(R.string.test_failed)
                            else -> stringResource(R.string.test_idle)
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = testColor,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))

            // Diagnostic panel
            AnimatedVisibility(visible = testFailed && diagState != null) {
                val diag = diagState ?: return@AnimatedVisibility
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 11.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(0.15f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DiagRow("server", "${diag.serverIp ?: "unresolved"} · ${diag.serverPort}")
                    if (diag.sni.isNotEmpty()) {
                        DiagRow("sni", diag.sni + (diag.sniIp?.let { " → $it" } ?: " · unresolved"))
                    }
                    if (diag.sameSubnet != null) {
                        DiagRow("subnet", if (diag.sameSubnet) "same /24 ✓" else "different /24")
                    }
                    DiagRow("direct", diag.directMs?.let { "${it}ms" } ?: "unreachable")
                }
            }

            // Disconnect
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(vertical = 17.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text(stringResource(R.string.btn_disconnect), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.sp)
            }
        }
    }
}

private fun testViaSocks5(port: Int, user: String, pass: String) {
    val s = try {
        java.net.Socket().also { it.soTimeout = 5_000; it.connect(java.net.InetSocketAddress("127.0.0.1", port), 5_000) }
    } catch (_: Exception) { throw ProxyConnectException() }
    s.use {
        val out = s.getOutputStream(); val inp = s.getInputStream()
        out.write(byteArrayOf(5, 1, 2)); out.flush()
        inp.read(); inp.read()
        val ub = user.toByteArray(); val pb = pass.toByteArray()
        out.write(byteArrayOf(1, ub.size.toByte()) + ub + byteArrayOf(pb.size.toByte()) + pb); out.flush()
        inp.read(); if (inp.read() != 0) throw ServerConnectException()
        val host = "1.1.1.1".toByteArray()
        out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(0, 80)); out.flush()
        inp.read(); if (inp.read() != 0) throw ServerConnectException(); repeat(8) { inp.read() }
        out.write("GET / HTTP/1.0\r\nHost: 1.1.1.1\r\n\r\n".toByteArray()); out.flush()
        if (inp.read() == -1) throw ServerConnectException()
    }
}

private suspend fun runDiagnostics(vlessLink: String): DiagResult = coroutineScope {
    val uri = java.net.URI(vlessLink)
    val serverHost = uri.host ?: ""
    val serverPort = uri.port
    val sni = uri.rawQuery?.split("&")
        ?.firstOrNull { it.startsWith("sni=") }
        ?.removePrefix("sni=")
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""

    val serverIpD = async(Dispatchers.IO) {
        runCatching { java.net.InetAddress.getByName(serverHost).hostAddress }.getOrNull()
    }
    val sniIpD = if (sni.isNotEmpty()) async(Dispatchers.IO) {
        runCatching { java.net.InetAddress.getByName(sni).hostAddress }.getOrNull()
    } else null
    val directMsD = async(Dispatchers.IO) {
        runCatching {
            val t0 = System.nanoTime()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(serverHost, serverPort), 3_000) }
            (System.nanoTime() - t0) / 1_000_000L
        }.getOrNull()
    }

    val serverIp = serverIpD.await()
    val sniIp = sniIpD?.await()
    val directMs = directMsD.await()
    val sameSubnet = if (serverIp != null && sniIp != null)
        serverIp.substringBeforeLast(".") == sniIp.substringBeforeLast(".") else null

    DiagResult(serverHost, serverPort, serverIp, sni, sniIp, sameSubnet, directMs)
}

@Composable
fun PulsingDot() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val scale by inf.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "scale")
    val alpha by inf.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "alpha")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(11.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .clip(CircleShape)
                .background(Glow))
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(Glow)
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(0.5f), fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(52.dp))
        Text(value, fontSize = 11.sp, color = Color.White.copy(0.85f), fontFamily = FontFamily.Monospace)
    }
}
