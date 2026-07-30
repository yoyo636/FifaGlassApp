package com.fifaglass.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassColors {
    val bgBase = Color(0xFFFAFAFC)
    val bgBaseDark = Color(0xFF1A1A1F)
    val cardSurface = Color.White
    val cardSurfaceDark = Color(0xFF2A2A30)
    val glassTint = Color(0xFFF5F5F7)
    val glassTintDark = Color(0xFF2C2C32)
    val strokeLight = Color(0x14000000)
    val strokeDark = Color(0x22FFFFFF)
    val textPrimary = Color(0xFF1D1D1F)
    val textPrimaryDark = Color(0xFFF5F5F7)
    val textSecondary = Color(0xFF6E6E73)
    val textSecondaryDark = Color(0xFF98989D)

    val accentBlue = Color(0xFF007AFF)
    val accentMint = Color(0xFF34C759)
    val accentPink = Color(0xFFFF2D55)
    val accentGold = Color(0xFFFF9500)
    val accentViolet = Color(0xFFAF52DE)
    val accentTeal = Color(0xFF5AC8FA)
    val up = Color(0xFF34C759)
    val down = Color(0xFFFF3B30)
}

data class GlassTheme(
    val isDark: Boolean = false,
)

val LocalGlassTheme = staticCompositionLocalOf { GlassTheme() }

fun Modifier.liquidGlass(corner: Dp = 20.dp, theme: GlassTheme = GlassTheme()): Modifier {
    val shape = RoundedCornerShape(corner)
    val tint = if (theme.isDark) GlassColors.glassTintDark else GlassColors.glassTint
    val stroke = if (theme.isDark) GlassColors.strokeDark else GlassColors.strokeLight
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.72f))
        .border(0.5.dp, stroke, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalGlassTheme.current
    Column(modifier = modifier.liquidGlass(corner, theme).padding(16.dp), content = content)
}

@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalGlassTheme.current
    val baseColor = if (theme.isDark) GlassColors.bgBaseDark else GlassColors.bgBase
    val accent1 = if (theme.isDark) Color(0xFF0A84FF).copy(alpha = 0.18f) else Color(0xFF007AFF).copy(alpha = 0.06f)
    val accent2 = if (theme.isDark) Color(0xFF30D158).copy(alpha = 0.12f) else Color(0xFF34C759).copy(alpha = 0.05f)
    val accent3 = if (theme.isDark) Color(0xFFFF453A).copy(alpha = 0.10f) else Color(0xFFFF2D55).copy(alpha = 0.04f)
    Box(
        modifier.background(baseColor)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent1, Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.15f, size.height * 0.1f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent2, Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.35f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.9f, size.height * 0.35f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent3, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.85f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.85f),
            )
        }
        content()
    }
}

@Composable
fun ProvideGlassTheme(isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassTheme provides GlassTheme(isDark)) {
        content()
    }
}

@Composable
fun Modifier.glass(corner: Dp = 20.dp): Modifier = this.liquidGlass(corner, LocalGlassTheme.current)
