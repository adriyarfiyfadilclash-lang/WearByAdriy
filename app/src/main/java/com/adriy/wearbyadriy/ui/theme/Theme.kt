package com.adriy.wearbyadriy.ui.theme

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
import com.adriy.wearbyadriy.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1D1A16),
    onPrimary = Color(0xFFFBF8F2),
    primaryContainer = Color(0xFFEDE4D3),
    onPrimaryContainer = Color(0xFF1D1A16),
    secondary = Color(0xFF8A5A3B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E3D6),
    onSecondaryContainer = Color(0xFF4A2E1C),
    tertiary = Color(0xFF5E6B4E),
    onTertiary = Color.White,
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF1D1A16),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF1D1A16),
    surfaceVariant = Color(0xFFF3EDE2),
    onSurfaceVariant = Color(0xFF5F574C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFFF4EFE6),
    surfaceContainerHigh = Color(0xFFEFE8DC),
    surfaceContainerHighest = Color(0xFFE9E1D3),
    outline = Color(0xFFB9AE9C),
    outlineVariant = Color(0xFFE4DCCD),
    error = Color(0xFFA8362A),
    onError = Color.White,
    errorContainer = Color(0xFFF8DDD7),
    onErrorContainer = Color(0xFF5A140C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF1EADF),
    onPrimary = Color(0xFF1D1A16),
    primaryContainer = Color(0xFF3A342C),
    onPrimaryContainer = Color(0xFFF1EADF),
    secondary = Color(0xFFD9A983),
    onSecondary = Color(0xFF3B2414),
    secondaryContainer = Color(0xFF4E3524),
    onSecondaryContainer = Color(0xFFF6DFCC),
    tertiary = Color(0xFFB8C6A3),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF2A2620),
    onSurfaceVariant = Color(0xFFC9BFB0),
    surfaceContainerLowest = Color(0xFF100E0B),
    surfaceContainerLow = Color(0xFF1C1915),
    surfaceContainer = Color(0xFF211E19),
    surfaceContainerHigh = Color(0xFF2B2721),
    surfaceContainerHighest = Color(0xFF36312A),
    outline = Color(0xFF7D7366),
    outlineVariant = Color(0xFF3D372F),
    error = Color(0xFFF0A597),
    onError = Color(0xFF5A140C),
    errorContainer = Color(0xFF7A2418),
    onErrorContainer = Color(0xFFF8DDD7),
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        headlineLarge = t.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 23.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 21.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WearTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
