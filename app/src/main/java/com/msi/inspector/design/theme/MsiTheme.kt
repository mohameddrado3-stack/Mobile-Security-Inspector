package com.msi.inspector.design.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.msi.inspector.R

object MsiColors {
    val Background = Color(0xFF080D18)
    val Surface = Color(0xFF10243A)
    val SurfaceElevated = Color(0xFF18283B)
    val Accent = Color(0xFF70E7D1)
    val Calm = Accent
    val Review = Color(0xFFFF9B45)
    val Attention = Color(0xFFFF604D)
    val TextPrimary = Color(0xFFF1F5FA)
    val TextSecondary = Color(0xFF9AA9BA)
    val Outline = Color(0xFF243750)
    val Info = Color(0xFF85B8FF)
}
@Immutable
data class MsiSemanticColors(
    val calm: Color,
    val review: Color,
    val attention: Color,
    val info: Color
)

private val msiDarkSemantic = MsiSemanticColors(
    calm = MsiColors.Accent,
    review = MsiColors.Review,
    attention = MsiColors.Attention,
    info = MsiColors.Info
)

private val msiLightSemantic = MsiSemanticColors(
    calm = Color(0xFF006B61),
    review = Color(0xFF9A5B00),
    attention = Color(0xFFC12B20),
    info = Color(0xFF2B5C93)
)

val LocalMsiSemanticColors = staticCompositionLocalOf { msiDarkSemantic }

@Composable
fun msiSemanticColors(): MsiSemanticColors = LocalMsiSemanticColors.current

val MsiFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold)
)

private fun msiStyle(
    size: Int,
    weight: FontWeight,
    lineHeightEm: Float
) = TextStyle(
    fontFamily = MsiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeightEm.em
)

private val msiTypography = Typography(
    displayLarge = msiStyle(48, FontWeight.SemiBold, 1.2f),
    displayMedium = msiStyle(40, FontWeight.SemiBold, 1.2f),
    displaySmall = msiStyle(32, FontWeight.SemiBold, 1.25f),
    headlineLarge = msiStyle(30, FontWeight.SemiBold, 1.3f),
    headlineMedium = msiStyle(26, FontWeight.SemiBold, 1.3f),
    headlineSmall = msiStyle(22, FontWeight.SemiBold, 1.35f),
    titleLarge = msiStyle(20, FontWeight.SemiBold, 1.4f),
    titleMedium = msiStyle(16, FontWeight.SemiBold, 1.45f),
    titleSmall = msiStyle(14, FontWeight.Medium, 1.45f),
    bodyLarge = msiStyle(16, FontWeight.Normal, 1.55f),
    bodyMedium = msiStyle(14, FontWeight.Normal, 1.55f),
    bodySmall = msiStyle(12, FontWeight.Normal, 1.5f),
    labelLarge = msiStyle(14, FontWeight.Medium, 1.4f),
    labelMedium = msiStyle(12, FontWeight.Medium, 1.4f),
    labelSmall = msiStyle(12, FontWeight.Medium, 1.4f)
)

private val msiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class MsiThemeMode {
    DARK, LIGHT, AMOLED
}

private val msiDarkScheme = darkColorScheme(
    primary = MsiColors.Accent,
    onPrimary = Color(0xFF080D18),
    secondary = Color(0xFF9CC8FF),
    onSecondary = Color(0xFF071426),
    tertiary = MsiColors.Review,
    background = MsiColors.Background,
    onBackground = MsiColors.TextPrimary,
    surface = MsiColors.Surface,
    onSurface = MsiColors.TextPrimary,
    surfaceVariant = Color(0xFF1B3149),
    onSurfaceVariant = MsiColors.TextSecondary,
    outline = Color(0xFF5E7186),
    outlineVariant = MsiColors.Outline,
    surfaceContainerLowest = MsiColors.Background,
    surfaceContainerLow = MsiColors.Surface,
    surfaceContainer = MsiColors.Surface,
    surfaceContainerHigh = MsiColors.SurfaceElevated,
    surfaceContainerHighest = Color(0xFF1F3249),
    error = MsiColors.Attention
)

private val msiAmoledScheme = msiDarkScheme.copy(
    background = Color.Black,
    surface = Color(0xFF05080D),
    surfaceVariant = Color(0xFF101820),
    outlineVariant = Color(0xFF1B2A3C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF05080D),
    surfaceContainer = Color(0xFF05080D),
    surfaceContainerHigh = Color(0xFF0C131B),
    surfaceContainerHighest = Color(0xFF101820)
)

private val msiLightScheme = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color.White,
    secondary = Color(0xFF345D83),
    onSecondary = Color.White,
    tertiary = Color(0xFF855900),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF15212C),
    surface = Color.White,
    onSurface = Color(0xFF15212C),
    surfaceVariant = Color(0xFFE7EEF3),
    onSurfaceVariant = Color(0xFF526575),
    outline = Color(0xFF7A8C9B),
    outlineVariant = Color(0xFFD5DEE6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF2F6),
    surfaceContainerHighest = Color(0xFFE7EEF3),
    error = Color(0xFFB7473E)
)

@Composable
fun MsiTheme(
    mode: MsiThemeMode = MsiThemeMode.DARK,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicAllowed = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        mode == MsiThemeMode.AMOLED -> msiAmoledScheme
        dynamicAllowed && mode == MsiThemeMode.LIGHT -> dynamicLightColorScheme(context)
        dynamicAllowed -> dynamicDarkColorScheme(context)
        mode == MsiThemeMode.LIGHT -> msiLightScheme
        else -> msiDarkScheme
    }
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                val useDarkSystemIcons = mode == MsiThemeMode.LIGHT
                controller.isAppearanceLightStatusBars = useDarkSystemIcons
                controller.isAppearanceLightNavigationBars = useDarkSystemIcons
            }
        }
    }
    val semantic = if (mode == MsiThemeMode.LIGHT) {
        msiLightSemantic.copy(calm = colors.primary)
    } else {
        msiDarkSemantic.copy(calm = colors.primary)
    }
    CompositionLocalProvider(LocalMsiSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colors,
            shapes = msiShapes,
            typography = msiTypography,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
