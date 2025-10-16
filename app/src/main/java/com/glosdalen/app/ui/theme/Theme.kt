package com.glosdalen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

// Color scheme based on seed color 0x1e7e75 (teal/green)
private val LightColorScheme = lightColorScheme(
    // Buttons, Toggles
    primary = Color(0xFF156c72),
    onPrimary = Color(0xFFFFFFFF),
    // Translation result
    primaryContainer = Color(0xFFf1cf8b),
    onPrimaryContainer = Color(0xFF002019),
    // not used.
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    // Container in card. e.g. Info block in settings
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    // not used?
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    // not used.
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF6F7977),
    // General app background
    background = Color(0xFFFAFDFB),
    onBackground = Color(0xFF191C1B),
    // Navigation bar at top
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF191C1B),
    // Card background
    surfaceVariant = Color(0xFFf3f3f3),
    onSurfaceVariant = Color(0xFF404944),
    // not used.
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    // not used.
    inversePrimary = Color(0xFF84D3C9),
    // not used.
    surfaceTint = Color(0xFF1e7e75),
    outlineVariant = Color(0xFFBFC9C5),
    scrim = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF84D3C9),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF8B6F2D),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFB0C9E8),
    onTertiary = Color(0xFF1B3447),
    tertiaryContainer = Color(0xFF314A60),
    onTertiaryContainer = Color(0xFFCCE5FF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF899390),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF328f97),
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF2E3130),
    inversePrimary = Color(0xFF1e7e75),
    surfaceTint = Color(0xFF84D3C9),
    outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000)
)

@Composable
fun GlosdalenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
