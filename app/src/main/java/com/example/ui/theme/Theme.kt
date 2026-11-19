package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BlueprintNavy,
    onPrimary = Color.White,
    primaryContainer = BlueprintContainer,
    onPrimaryContainer = OnBlueprintContainer,
    secondary = SafetyAmber,
    onSecondary = Color.White,
    secondaryContainer = SafetyAmberContainer,
    onSecondaryContainer = OnSafetyAmberContainer,
    tertiary = VerifiedTeal,
    onTertiary = Color.White,
    tertiaryContainer = VerifiedTealContainer,
    onTertiaryContainer = OnVerifiedTealContainer,
    background = ArchivalIvory,
    onBackground = InkPrimary,
    surface = DraftingPaper,
    onSurface = InkPrimary,
    surfaceVariant = LedgerSurfaceLow,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = DraftingPaper,
    surfaceContainerLow = ArchivalIvory,
    surfaceContainer = LedgerSurfaceLow,
    surfaceContainerHigh = LedgerSurfaceHigh,
    outline = HairlineOutline,
    outlineVariant = HairlineOutlineVariant,
    error = SlipRose,
    onError = Color.White,
    errorContainer = SlipRoseContainer,
    onErrorContainer = Color(0xFF470C1D)
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceHigh,
    surfaceContainerHigh = Color(0xFF24374A),
    outline = DarkOutline,
    outlineVariant = Color(0xFF223344),
    error = Color(0xFFFF8BA4),
    onError = Color(0xFF5A0B20),
    errorContainer = Color(0xFF7B1B36),
    onErrorContainer = Color(0xFFFFD9E1)
)

val DocFolioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun DocFolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DocFolioShapes,
        content = content
    )
}
