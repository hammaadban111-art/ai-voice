package com.hammaad.voiceappv4.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ColorSchemeWarningContainer = Color(0xFFFFE0B2)
val ColorSchemeWarning = Color(0xFF8A4B00)

private val Ink = Color(0xFF0F1D26)
private val NightInk = Color(0xFF0B161D)
private val Canvas = Color(0xFFF5F8F6)
private val SignalAmber = Color(0xFFF2B544)
private val SignalAmberSoft = Color(0xFFFFE6AC)
private val SignalTeal = Color(0xFF197D72)
private val SignalTealSoft = Color(0xFFBCEFE6)
private val Mist = Color(0xFFE3ECE9)
private val InkMuted = Color(0xFF52646B)
private val Critical = Color(0xFFB54A4A)

private val Light = lightColorScheme(
    primary = SignalAmber,
    onPrimary = Ink,
    primaryContainer = SignalAmberSoft,
    onPrimaryContainer = Color(0xFF3D2B00),
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = SignalTeal,
    onTertiary = Color.White,
    tertiaryContainer = SignalTealSoft,
    onTertiaryContainer = Color(0xFF003A34),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAF1EF),
    onSurfaceVariant = InkMuted,
    outline = Color(0xFF7E9090),
    outlineVariant = Color(0xFFC9D7D3),
    error = Critical,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFD174),
    onPrimary = Color(0xFF3B2A00),
    primaryContainer = Color(0xFF705000),
    onPrimaryContainer = Color(0xFFFFE6AC),
    secondary = Color(0xFFB6C6C9),
    onSecondary = Color(0xFF1D2B30),
    secondaryContainer = Color(0xFF34464C),
    onSecondaryContainer = Color(0xFFD5E4E5),
    tertiary = Color(0xFF8FE0D2),
    onTertiary = Color(0xFF003A34),
    tertiaryContainer = Color(0xFF07564E),
    onTertiaryContainer = Color(0xFFBCEFE6),
    background = NightInk,
    onBackground = Color(0xFFE0EDEF),
    surface = Color(0xFF11232C),
    onSurface = Color(0xFFE0EDEF),
    surfaceVariant = Color(0xFF293B43),
    onSurfaceVariant = Color(0xFFC3D1D3),
    outline = Color(0xFF8A9B9E),
    outlineVariant = Color(0xFF40535A),
    error = Color(0xFFFFB4B2),
    onError = Color(0xFF68000B),
    errorContainer = Color(0xFF930014),
    onErrorContainer = Color(0xFFFFDAD8),
)

private val VoiceTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.1).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.7).sp,
    ),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.2.sp),
)

private val VoiceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun VoiceAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = VoiceTypography,
        shapes = VoiceShapes,
        content = content,
    )
}

/** Compatibility alias for any core-side preview that already references VoiceTheme. */
@Composable
fun VoiceTheme(content: @Composable () -> Unit) = VoiceAppTheme(content = content)
