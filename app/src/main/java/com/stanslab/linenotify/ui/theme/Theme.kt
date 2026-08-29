package com.stanslab.linenotify.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreen40,
    onPrimary = Color(0xFF003918),
    secondary = GreenGrey80,
    onSecondary = Color(0xFF1A1C1E),
    primaryContainer = DarkGreen,
    onPrimaryContainer = DarkGreen40,
    background = DarkBackground,
    onBackground = Color(0xFFE4E6EC),
    surface = DarkSurface,
    onSurface = Color(0xFFE4E6EC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B6C3),
    outline = DarkOutline,
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFF5C6C6),
)

private val LightColorScheme = lightColorScheme(
    primary = ActionGreen,
    onPrimary = Color.White,
    secondary = GreenGrey40,
    onSecondary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = DarkGreen,
    background = LightBackground,
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = LightMuted,
    onSurfaceVariant = Color(0xFF5F6675),
    outline = LightOutline,
    errorContainer = Color(0xFFFFF5F5),
    onErrorContainer = Color(0xFF9F1239),
)

@Composable
fun LineNotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
