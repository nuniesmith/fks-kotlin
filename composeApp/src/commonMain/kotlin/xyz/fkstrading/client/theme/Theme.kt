package xyz.fkstrading.client.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * FKS Trading Terminal Color Scheme
 * Optimized for long trading sessions with reduced eye strain
 */
private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF00E676), // Bright Green (Bull)
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF005227),
        onPrimaryContainer = Color(0xFF7EFF9F),
        secondary = Color(0xFFFF5252), // Bright Red (Bear)
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF8B0000),
        onSecondaryContainer = Color(0xFFFFB4AB),
        tertiary = Color(0xFF82B1FF), // Blue (Neutral)
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF003F7F),
        onTertiaryContainer = Color(0xFFB8D7FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0D1117), // GitHub Dark BG
        onBackground = Color(0xFFE6E6E6),
        surface = Color(0xFF161B22), // Card/Surface
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = Color(0xFF21262D),
        onSurfaceVariant = Color(0xFFC9D1D9),
        outline = Color(0xFF30363D), // Border
        outlineVariant = Color(0xFF1F2428),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF00C853),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F5BB),
        onPrimaryContainer = Color(0xFF002106),
        secondary = Color(0xFFD32F2F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDAD6),
        onSecondaryContainer = Color(0xFF410002),
        tertiary = Color(0xFF1976D2),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD8E4FF),
        onTertiaryContainer = Color(0xFF001A41),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1C1E),
    )

@Composable
fun FksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FksTypography,
        content = content,
    )
}

// Extension colors for trading UI
object TradingColors {
    val bullGreen = Color(0xFF00E676)
    val bearRed = Color(0xFFFF5252)
    val neutralBlue = Color(0xFF82B1FF)
    val warningYellow = Color(0xFFFFD600)
    val chartGrid = Color(0xFF30363D)
    val chartText = Color(0xFF8B949E)
}
