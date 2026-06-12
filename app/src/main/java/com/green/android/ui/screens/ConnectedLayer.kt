package com.green.android.ui.screens

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
import com.green.android.VpnStatus
import com.green.android.data.Config
import com.green.android.nameWithFlag
import com.green.android.ui.components.SplitTunnelLine
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.Glow
import com.green.android.ui.theme.GradA
import com.green.android.ui.theme.GradB
import com.green.android.ui.theme.Accent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val testOk = testState?.startsWith("ok:") == true
    val testFailed = testState == "failed"
    val testMs = testState?.removePrefix("ok:")?.toLongOrNull()
    val testMsLabel = if (testMs == 0L) "<1" else testMs?.toString()
    LaunchedEffect(visible) { if (!visible) testState = null }
    LaunchedEffect(testState) {
        if (testState != "testing") return@LaunchedEffect
        testState = withContext(Dispatchers.IO) {
            runCatching {
                val t0 = System.nanoTime()
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("1.1.1.1", 443), 5_000) }
                "ok:${(System.nanoTime() - t0) / 1_000_000L}"
            }.getOrElse { "failed" }
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
                            testFailed -> stringResource(R.string.test_failed)
                            else -> stringResource(R.string.test_idle)
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = testColor,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))

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
