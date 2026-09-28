package com.example.mediqr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RedOnDark,
    onPrimary = Color(0xFF690000),
    primaryContainer = RedDarkContainer,
    onPrimaryContainer = MedicalRedContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = RedOnDark,
    onError = Color(0xFF690000),
)

private val LightColorScheme = lightColorScheme(
    primary = MedicalRed,
    onPrimary = Color.White,
    primaryContainer = MedicalRedContainer,
    onPrimaryContainer = OnMedicalRedContainer,
    secondary = AppOnSurfaceVariant,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = AppOnBackground,
    surface = AppBackground,
    onSurface = AppOnBackground,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppOnSurfaceVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = MedicalRedLight,
    onErrorContainer = MedicalRedDark,
)

/**
 * One corner-radius scale for the whole app so every surface feels like it
 * belongs to the same product:
 *  - medium: buttons, text fields, banners (12.dp)
 *  - large:  content cards (16.dp)
 */
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun MediQRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep the medical red brand color instead of wallpaper-based dynamic color.
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
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
