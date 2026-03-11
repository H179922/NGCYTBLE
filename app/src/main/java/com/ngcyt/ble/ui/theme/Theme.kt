package com.ngcyt.ble.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// -- Palette --

// Threat / danger accents
private val Red400 = Color(0xFFEF5350)
private val Red700 = Color(0xFFD32F2F)
private val RedDark = Color(0xFF8E1B1B)

// Safe / clear accents
private val Green400 = Color(0xFF66BB6A)
private val Green700 = Color(0xFF388E3C)

// Surveillance-grade neutrals
private val Gray950 = Color(0xFF0D0D0D)
private val Gray900 = Color(0xFF1A1A1A)
private val Gray850 = Color(0xFF212121)
private val Gray800 = Color(0xFF2C2C2C)
private val Gray700 = Color(0xFF424242)
private val Gray400 = Color(0xFF9E9E9E)
private val Gray200 = Color(0xFFEEEEEE)
private val Gray100 = Color(0xFFF5F5F5)
private val White = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = Green400,
    onPrimary = Gray950,
    primaryContainer = Green700,
    onPrimaryContainer = Gray200,
    secondary = Gray700,
    onSecondary = Gray200,
    secondaryContainer = Gray800,
    onSecondaryContainer = Gray200,
    tertiary = Red400,
    onTertiary = White,
    tertiaryContainer = RedDark,
    onTertiaryContainer = Gray200,
    background = Gray950,
    onBackground = Gray200,
    surface = Gray900,
    onSurface = Gray200,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray400,
    error = Red400,
    onError = Gray950,
    errorContainer = RedDark,
    onErrorContainer = Gray200,
    outline = Gray700,
)

private val LightColorScheme = lightColorScheme(
    primary = Green700,
    onPrimary = White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Gray700,
    onSecondary = White,
    secondaryContainer = Gray200,
    onSecondaryContainer = Gray800,
    tertiary = Red700,
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFCDD2),
    onTertiaryContainer = RedDark,
    background = Gray100,
    onBackground = Gray850,
    surface = White,
    onSurface = Gray850,
    surfaceVariant = Gray200,
    onSurfaceVariant = Gray700,
    error = Red700,
    onError = White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = RedDark,
    outline = Gray400,
)

@Composable
fun NgcytBleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
