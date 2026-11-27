package de.vocabscan.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF414FBD), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8FF), onPrimaryContainer = Color(0xFF232C72),
    secondary = Color(0xFF466B60), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EEE7), onSecondaryContainer = Color(0xFF24483C),
    tertiary = Color(0xFFA74F38), tertiaryContainer = Color(0xFFFFE4D8), onTertiaryContainer = Color(0xFF6A2D1D),
    background = Color(0xFFF7F6F2), onBackground = Color(0xFF202335),
    surface = Color(0xFFFFFEFB), onSurface = Color(0xFF202335),
    surfaceVariant = Color(0xFFEFEEE9), onSurfaceVariant = Color(0xFF656777),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCFBF7),
    surfaceContainer = Color(0xFFF1F0EB), surfaceContainerHigh = Color(0xFFEAE9E4),
    outline = Color(0xFF858694), outlineVariant = Color(0xFFDDDED9),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC2FF), onPrimary = Color(0xFF202B78),
    primaryContainer = Color(0xFF303A79), onPrimaryContainer = Color(0xFFE2E5FF),
    secondary = Color(0xFFACD1BD), secondaryContainer = Color(0xFF293F36), onSecondaryContainer = Color(0xFFCCEBD9),
    tertiary = Color(0xFFFFB89D), tertiaryContainer = Color(0xFF56372F), onTertiaryContainer = Color(0xFFFFDCCF),
    background = Color(0xFF141620), onBackground = Color(0xFFE8E8EF),
    surface = Color(0xFF1C1E2A), onSurface = Color(0xFFE8E8EF),
    surfaceVariant = Color(0xFF2B2D3A), onSurfaceVariant = Color(0xFFB8B9C7),
    surfaceContainerLowest = Color(0xFF11131B), surfaceContainerLow = Color(0xFF1A1C28),
    surfaceContainer = Color(0xFF222430), surfaceContainerHigh = Color(0xFF2B2D3A),
    outline = Color(0xFF8B8D9C), outlineVariant = Color(0xFF3B3D4B),
)

val Ink = Color(0xFF252E63)
val InkAccent = Color(0xFFDBE2A5)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 56.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
)

enum class ThemeMode(val title: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

object ThemePreference {
    private const val FILE = "appearance"
    private const val KEY = "theme_mode"

    fun read(context: Context): ThemeMode {
        val value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.SYSTEM
    }

    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

@Composable
fun VocabTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)), content = content)
}
