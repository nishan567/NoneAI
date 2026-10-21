package com.nonea.ai.ui

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

/** Extra design tokens (gradients, neon accents) that don't fit Material's ColorScheme. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Brush,
    val glass: Color,        // translucent panel fill
    val glassStroke: Color,  // hairline border for panels
    val cyan: Color,
    val violet: Color,
    val pink: Color,
    val userBubble: Brush,
    val glow: Float          // multiplier for orb glow (softer on light backgrounds)
)

private val DarkPalette = Palette(
    dark = true,
    bg = Brush.verticalGradient(listOf(Color(0xFF070B1A), Color(0xFF0C1230), Color(0xFF150F2E))),
    glass = Color(0xFF1A2347).copy(alpha = 0.72f), glassStroke = Color(0xFF3A4A8A).copy(alpha = 0.6f),
    cyan = Color(0xFF4DD8F0), violet = Color(0xFFA78BFA), pink = Color(0xFFFF7EC8),
    userBubble = Brush.linearGradient(listOf(Color(0xFF5B3FD0), Color(0xFF0B7390))), glow = 1f
)

private val LightPalette = Palette(
    dark = false,
    bg = Brush.verticalGradient(listOf(Color(0xFFF7F8FF), Color(0xFFEEF1FF), Color(0xFFF3EDFF))),
    glass = Color.White.copy(alpha = 0.78f), glassStroke = Color(0xFFC3CBEA),
    cyan = Color(0xFF12B5D8), violet = Color(0xFF7C5CF0), pink = Color(0xFFE5489E),
    userBubble = Brush.linearGradient(listOf(Color(0xFF5B3FD0), Color(0xFF0B7390))), glow = 0.7f
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4DD8F0), onPrimary = Color(0xFF021824),
    secondary = Color(0xFFA78BFA), tertiary = Color(0xFFFF7EC8),
    background = Color(0xFF070B1A), onBackground = Color(0xFFEAF0FF),
    surface = Color(0xFF111833), onSurface = Color(0xFFEAF0FF),
    surfaceVariant = Color(0xFF1A2347), onSurfaceVariant = Color(0xFFA9B4D6),
    outline = Color(0xFF3A4A8A), error = Color(0xFFFF8A80)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0A7EA4), onPrimary = Color.White,
    secondary = Color(0xFF6D4AE0), tertiary = Color(0xFFC2278A),
    background = Color(0xFFF4F6FF), onBackground = Color(0xFF0E1330),
    surface = Color.White, onSurface = Color(0xFF0E1330),
    surfaceVariant = Color(0xFFE6EAFB), onSurfaceVariant = Color(0xFF4A557E),
    outline = Color(0xFFC3CBEA), error = Color(0xFFB3261E)
)

private val NoneaTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.6.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

val LocalPalette = staticCompositionLocalOf<Palette> { error("Palette not provided") }
/** Resources for the chosen interface language (so the in-app language switch needs no activity restart). */
val LocalRes = staticCompositionLocalOf<Resources> { error("Resources not provided") }
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun str(@StringRes id: Int, vararg args: Any): String = LocalRes.current.getString(id, *args)

@Composable
fun NoneaTheme(theme: String, uiLang: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val res = remember(uiLang, ctx) {
        if (uiLang == "en" || uiLang == "bn") {
            val cfg = Configuration(ctx.resources.configuration)
            cfg.setLocale(Locale(uiLang))
            ctx.createConfigurationContext(cfg).resources
        } else ctx.resources
    }
    // Honour the system "remove animations" accessibility setting.
    val reduce = remember(ctx) {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as? Activity)?.window
            if (w != null) {
                val c = WindowCompat.getInsetsController(w, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalPalette provides (if (dark) DarkPalette else LightPalette),
        LocalRes provides res,
        LocalReduceMotion provides reduce,
        LocalContentColor provides scheme.onBackground
    ) {
        MaterialTheme(colorScheme = scheme, typography = NoneaTypography, content = content)
    }
}
