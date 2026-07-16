package com.termfast.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// === SECTION 1 END ===

// Modern color palette — indigo primary with teal accent
// Designed to look great on both Android and iOS

private val Indigo80 = Color(0xFFB4C5FF)
private val IndigoGrey80 = Color(0xFFC3C8E0)
private val Teal80 = Color(0xFF7DD3C0)
private val Error80 = Color(0xFFFFB4AB)
private val SurfaceDark = Color(0xFF131318)
private val SurfaceDarkVariant = Color(0xFF1D1D24)
private val SurfaceDarkContainer = Color(0xFF252530)

private val Indigo40 = Color(0xFF4858C0)
private val IndigoGrey40 = Color(0xFF5A5D72)
private val Teal40 = Color(0xFF00897B)
private val Error40 = Color(0xFFBA1A1A)
private val SurfaceLight = Color(0xFFFAF9FF)
private val SurfaceLightVariant = Color(0xFFE3E1EC)
private val SurfaceLightContainer = Color(0xFFF0EEF8)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF1A2A6B),
    primaryContainer = Color(0xFF334083),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Teal80,
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFF9CF0E0),
    tertiary = IndigoGrey80,
    onTertiary = Color(0xFF2D3043),
    tertiaryContainer = Color(0xFF43465F),
    onTertiaryContainer = Color(0xFFDFE0F4),
    error = Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = SurfaceDark,
    onBackground = Color(0xFFE4E1E9),
    surface = SurfaceDark,
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = Color(0xFFC3C8E0),
    surfaceContainer = SurfaceDarkContainer,
    surfaceContainerHigh = Color(0xFF2D2D38),
    surfaceContainerHighest = Color(0xFF383843),
    outline = Color(0xFF8B8FA3),
    outlineVariant = Color(0xFF44464F),
)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Teal40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CF0E0),
    onSecondaryContainer = Color(0xFF001F1C),
    tertiary = IndigoGrey40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE0F4),
    onTertiaryContainer = Color(0xFF16172B),
    error = Error40,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SurfaceLight,
    onBackground = Color(0xFF1A1B22),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = SurfaceLightVariant,
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainer = SurfaceLightContainer,
    surfaceContainerHigh = Color(0xFFE7E5EF),
    surfaceContainerHighest = Color(0xFFDCDAE4),
    outline = Color(0xFF757785),
    outlineVariant = Color(0xFFC3C8E0),
)

// === SECTION 2 END ===

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun TermFastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}