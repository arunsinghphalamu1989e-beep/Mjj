package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = SpaceBlack,
    primaryContainer = ElectricBlue,
    onPrimaryContainer = Color.White,
    secondary = BrightAzure,
    onSecondary = SpaceBlack,
    secondaryContainer = SpaceCard,
    onSecondaryContainer = SoftCyan,
    tertiary = PlasmaPurple,
    onTertiary = Color.White,
    background = SpaceDark,
    onBackground = TextPrimary,
    surface = SpaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SpaceCardBorder,
    onSurfaceVariant = TextSecondary,
    outline = NeonCyan.copy(alpha = 0.3f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature sci-fi dark neon theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
