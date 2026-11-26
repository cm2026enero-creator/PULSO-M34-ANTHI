package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PulsoDarkColorScheme = darkColorScheme(
    primary = TacticalAmber,
    onPrimary = ObsidianBg,
    primaryContainer = TacticalAmberContainer,
    onPrimaryContainer = TacticalAmberBright,
    secondary = ResponderEmerald,
    onSecondary = ObsidianBg,
    secondaryContainer = ResponderEmeraldContainer,
    onSecondaryContainer = ResponderEmeraldBright,
    tertiary = TelemetryCyan,
    onTertiary = ObsidianBg,
    tertiaryContainer = TelemetryCyanContainer,
    onTertiaryContainer = TelemetryCyan,
    error = SosCrimson,
    onError = TextPrimary,
    errorContainer = SosCrimsonContainer,
    onErrorContainer = SosCrimsonBright,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = TacticalDeck,
    onSurface = TextPrimary,
    surfaceVariant = TacticalSurface,
    onSurfaceVariant = TextSecondary,
    outline = TacticalBorder,
    outlineVariant = TacticalSurfaceAlt
)

@Composable
fun PulsoM34Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PulsoDarkColorScheme,
        typography = PulsoTypography,
        content = content
    )
}
