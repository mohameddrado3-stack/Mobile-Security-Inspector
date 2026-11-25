package com.msi.inspector.design.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object MsiColors {
    val Background = Color(0xFF080D18)
    val Surface = Color(0xFF10243A)
    val SurfaceElevated = Color(0xFF162D45)
    val Accent = Color(0xFF70E7D1)
    val Calm = Color(0xFF70E7D1)
    val Review = Color(0xFFFFC857)
    val Attention = Color(0xFFFF7A6B)
    val TextPrimary = Color(0xFFF2F6FC)
    val TextSecondary = Color(0xFFAAB9CA)
}

private val msiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

enum class MsiThemeMode {
    DARK, LIGHT, AMOLED
}

private val msiDarkScheme = darkColorScheme(
    primary = MsiColors.Accent,
    onPrimary = Color(0xFF07131A),
    secondary = Color(0xFF9CC8FF),
    onSecondary = Color(0xFF071426),
    tertiary = MsiColors.Review,
    background = MsiColors.Background,
    onBackground = MsiColors.TextPrimary,
    surface = MsiColors.Surface,
    onSurface = MsiColors.TextPrimary,
    surfaceVariant = Color(0xFF1B3149),
    onSurfaceVariant = MsiColors.TextSecondary,
    error = MsiColors.Attention
)

private val msiAmoledScheme = msiDarkScheme.copy(
    background = Color.Black,
    surface = Color(0xFF05080D),
    surfaceVariant = Color(0xFF101820)
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
    MaterialTheme(colorScheme = colors, shapes = msiShapes, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
