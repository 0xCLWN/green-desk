package com.green.android.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.ui.components.PushHeader
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary
import com.green.android.ui.theme.Warn

private data class DisguiseOption(
    val key: String,
    val label: String,
    val bgColor: Color,
    @DrawableRes val fgRes: Int,
    val fullIcon: Boolean = false,
)

@Composable
fun DisguiseSettingsScreen(
    disguise: String,
    onDisguise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val options = listOf(
        DisguiseOption("default",   stringResource(R.string.disguise_option_default), Color(0xFF1F2B25), R.drawable.ic_launcher_foreground),
        DisguiseOption("alfa_bank", stringResource(R.string.disguise_option_alfa),    Color.Transparent, R.mipmap.ic_launcher_alfa, fullIcon = true),
        DisguiseOption("weather",   stringResource(R.string.disguise_option_weather), Color.Transparent, R.drawable.ic_launcher_weather_fg, fullIcon = true),
    )
    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_disguise), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22E8A24A))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    tint = Warn,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp),
                )
                Text(
                    stringResource(R.string.disguise_restart_warning),
                    fontSize = 13.sp,
                    color = Warn,
                    lineHeight = 18.sp,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
            ) {
                options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = Border)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .clickable { if (disguise != option.key) onDisguise(option.key) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(option.bgColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(option.fgRes),
                                contentDescription = null,
                                modifier = if (option.fullIcon) Modifier.size(36.dp) else Modifier.size(28.dp),
                            )
                        }
                        Text(
                            option.label,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                        )
                        if (disguise == option.key) {
                            Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
