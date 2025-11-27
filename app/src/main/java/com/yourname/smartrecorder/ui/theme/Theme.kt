package com.yourname.smartrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    background = Background,
    onBackground = OnBackground,
    error = Error,
    onError = OnError
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A65), // Lighter orange for dark mode
    onPrimary = Color(0xFF5C1F00),
    primaryContainer = Color(0xFFBF360C), // Darker orange container
    onPrimaryContainer = Color(0xFFFFE5DE),
    surface = Color(0xFF1C1C1C), // Dark background
    onSurface = Color(0xFFF5F5F5), // Bright text for contrast
    surfaceVariant = Color(0xFF2E2E2E), // Lighter variant for dark mode
    onSurfaceVariant = Color(0xFFE0E0E0),
    background = Color(0xFF1C1C1C), // Consistent with surface
    onBackground = Color(0xFFF5F5F5), // Bright text
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

