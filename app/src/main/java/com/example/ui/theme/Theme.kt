package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val KineticColorScheme = darkColorScheme(
    primary = KineticAccentGreen,
    secondary = KineticAccentBlue,
    tertiary = KineticAccentGreenGlow,
    background = KineticBackground,
    surface = KineticGlassBase,
    onBackground = KineticTextPrimary,
    onSurface = KineticTextPrimary,
    surfaceVariant = KineticGlassMd,
    onSurfaceVariant = KineticTextSecondary,
    outline = KineticBorderMedium
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark space theme
    dynamicColor: Boolean = false, // Stand by custom Kinetic colors
    content: @Composable () -> Unit,
) {
    // We enforce the Kinetic design system tokens consistently
    val colorScheme = KineticColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
