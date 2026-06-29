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

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantBluePrimary,
    onPrimary = ElegantBlueDarkText,
    secondary = ElegantBlueActive,
    onSecondary = ElegantBlueDarkText,
    tertiary = ElegantAccentPink,
    background = ElegantDarkBg,
    onBackground = ElegantDarkTextPrimary,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkTextPrimary,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantDarkTextSecondary,
    outline = ElegantBorderSilver
  )

private val LightColorScheme = DarkColorScheme // Elegant Dark is applied across all settings to match intent

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme for the "Elegant Dark" aesthetic
  dynamicColor: Boolean = false, // Keep elegant dark colors prioritized instead of default system engine tints
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
