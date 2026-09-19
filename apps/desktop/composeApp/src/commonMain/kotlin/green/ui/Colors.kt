package green.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val BgPage        = Color(0xFF111111)  // outermost dark
internal val BgCard        = Color(0xFF212121)  // card/row surface
internal val BgCardSelected= Color(0xFF192518)  // card selected — subtle green tint
internal val BgInput       = Color(0xFF181818)  // input background
internal val BorderCard    = Color(0xFF2D2D2D)  // card borders, dividers
internal val BorderSelected= Color(0xFF4ADE80)  // selected card border (accent green)
internal val RadioBorderUnselected = Color(0xFF555555)
internal val BorderInput   = Color(0xFF353535)  // input border
internal val AccentGreen   = Color(0xFF4ADE80)
internal val StopBorderGreen = Color(0xFF2A5C38) // dark green Stop button border
internal val OnAccent      = Color(0xFF0F2118)
internal val TextPrimary   = Color(0xFFF0F0F0)
internal val TextSecondary = Color(0xFF909090)  // subtitle / URL text
internal val TextMid       = Color(0xFFC0C0C0)  // System Proxy label
internal val TextMuted     = Color(0xFF686868)  // KEYS eyebrow
internal val DotDisconnected = Color(0xFF6B6B6B)
internal val ToggleTrackOff  = Color(0xFF383838)
internal val ToggleThumb     = Color(0xFFF5F5F5)
internal val DestructiveRed  = Color(0xFFE05454)

internal val GreenThemeColors = darkColorScheme(
    background    = BgPage,
    surface       = Color(0xFF1A1A1A),
    surfaceVariant= BgCard,
    onBackground  = TextPrimary,
    onSurface     = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline       = BorderCard,
    outlineVariant= BorderCard,
    primary       = AccentGreen,
    onPrimary     = OnAccent,
    secondary     = AccentGreen,
    onSecondary   = OnAccent,
    error         = DestructiveRed,
    onError       = TextPrimary,
    errorContainer    = Color(0xFF3C1A1A),
    onErrorContainer  = DestructiveRed,
    primaryContainer  = BgCardSelected,
    onPrimaryContainer= AccentGreen,
    surfaceTint   = Color.Transparent,
    scrim         = Color(0x8C000000),
)
