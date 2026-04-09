package com.vkturn.proxy.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusRed,
    errorContainer = StatusRed.copy(alpha = 0.2f),
    onErrorContainer = StatusRed,
    surfaceVariant = DarkSurface.copy(alpha = 0.8f),
    onSurfaceVariant = TextSecondary
)

@Composable
fun VkTurnProxyTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    
    // В Android 15+ статус-бар и навигация настраиваются автоматически через enableEdgeToEdge() в MainActivity.
    // Здесь мы просто оставляем MaterialTheme для стилизации компонентов.
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
