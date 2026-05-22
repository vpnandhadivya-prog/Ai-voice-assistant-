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

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = ElectricViolet,
    tertiary = HotPink,
    background = DeepObsidian,
    surface = HologramSurface,
    onPrimary = Color(0xFF010203),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = OnBackgroundIce,
    onSurface = Color.White,
    outline = GlassBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F), // Ice Teal Chrome
    secondary = Color(0xFF6A1B9A), // Cosmic Purple
    tertiary = Color(0xFFAD1457), // Neon Rose
    background = Color(0xFFEDF3F7), // Warm Ice Blue
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF131B22),
    onSurface = Color(0xFF131B22),
    outline = Color(0xFFCFD8DC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic color to enforce our premium customized look by default!
    content: @Composable () -> Unit,
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
        typography = Typography,
        content = content
    )
}
