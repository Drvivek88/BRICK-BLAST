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

private val ArcadeColorScheme = darkColorScheme(
  primary = NeonGreen,
  onPrimary = Color(0xFF052E16),
  secondary = NeonCyan,
  onSecondary = Color(0xFF082F49),
  tertiary = NeonYellow,
  background = ArcadeBg,
  surface = ArcadeSurface,
  onBackground = Color(0xFFE7ECFF),
  onSurface = Color(0xFFE7ECFF),
  surfaceVariant = ArcadeCard,
  onSurfaceVariant = TextMuted
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = ArcadeColorScheme, typography = Typography, content = content)
}
