package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MusicVerseDarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = Color.White,
    primaryContainer = DeepPurple,
    onPrimaryContainer = SoftLavender,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = Color(0xFF0E3A47),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianElevated,
    outline = GlassStroke,
    outlineVariant = Color(0x22FFFFFF),
    error = ErrorRose,
    onError = ObsidianVoid,
)

val MusicVerseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MusicVerseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MusicVerseDarkColorScheme,
        typography = MusicVerseTypography,
        shapes = MusicVerseShapes,
        content = content
    )
}
