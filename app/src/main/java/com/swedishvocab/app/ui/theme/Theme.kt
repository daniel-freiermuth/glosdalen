package com.swedishvocab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

// Color scheme based on seed color 0x1e7e75 (teal/green)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1e7e75),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA0F0E5),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF6F7977),
    background = Color(0xFFFAFDFB),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E1),
    onSurfaceVariant = Color(0xFF404944),
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    inversePrimary = Color(0xFF84D3C9),
    surfaceTint = Color(0xFF1e7e75),
    outlineVariant = Color(0xFFBFC9C5),
    scrim = Color(0xFF000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF84D3C9),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005B51),
    onPrimaryContainer = Color(0xFFA0F0E5),
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
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C5),
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF2E3130),
    inversePrimary = Color(0xFF1e7e75),
    surfaceTint = Color(0xFF84D3C9),
    outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000)
)

@Composable
fun SwedishVocabAppTheme(
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
