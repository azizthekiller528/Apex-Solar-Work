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
    surfaceContainerHigh = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF4C1123),
    onErrorContainer = Color(0xFFFFE4E6)
)

val DocFolioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
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

enum class AppThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "Auto (Phone)"),
    LIGHT("light", "Day Mode"),
    DARK("dark", "Night Mode");

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM

        fun nextManualToggle(currentlyDark: Boolean): AppThemeMode =
            if (currentlyDark) LIGHT else DARK

        fun nextCycle(current: AppThemeMode): AppThemeMode = when (current) {
            SYSTEM -> DARK
            DARK -> LIGHT
            LIGHT -> SYSTEM
        }
    }
}

object ThemePreferenceStore {
    private const val PREFS_NAME = "docfolio_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun loadThemeMode(context: android.content.Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return AppThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null))
    }

    fun saveThemeMode(context: android.content.Context, mode: AppThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
    }
}
