package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ConstructionAccent,
    secondary = AccentTeal,
    tertiary = ConstructionSafetyYellow,
    background = DarkBg,
    surface = DarkCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = ConstructionTextDark,
    onSurface = ConstructionTextDark,
    error = AbsentRed
)

private val LightColorScheme = lightColorScheme(
    primary = ConstructionBlue,
    secondary = ConstructionAccent,
    tertiary = ConstructionSafetyYellow,
    background = LightBg,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = ConstructionBlue,
    onSurface = ConstructionBlue,
    error = AbsentRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
