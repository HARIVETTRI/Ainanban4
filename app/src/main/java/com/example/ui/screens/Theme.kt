package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimarySleek,
    secondary = SecondarySleek,
    tertiary = Pink80,
    background = BackgroundSleek,
    surface = SurfaceSleek,
    onPrimary = Color.White,
    onSecondary = OnSecondarySleek,
    onBackground = OnBackgroundSleek,
    onSurface = OnSurfaceSleek
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimarySleek,
    secondary = SecondarySleek,
    tertiary = Pink40,
    background = BackgroundSleek,
    surface = SurfaceSleek,
    onPrimary = Color.White,
    onSecondary = OnSecondarySleek,
    onBackground = OnBackgroundSleek,
    onSurface = OnSurfaceSleek,
    surfaceVariant = SurfaceSleek,
    onSurfaceVariant = OnSurfaceVariantSleek,
    outline = BorderSleek
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Set to false to default to our gorgeous light Sleek Theme
  dynamicColor: Boolean = false, // Disable dynamic colors to strictly enforce the Sleek Interface colors
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
