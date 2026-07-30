package com.fifaglass.app.ui
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember

object GlassColors {
    val bgBase = Color(0xFFF6F7FB)
    val bgBaseDark = Color(0xFF0B0B14)

    val surface = Color(0xFFFFFFFF)
    val surfaceDark = Color(0xFF181822)

    val surfaceVariant = Color(0xFFF2F3F8)
    val surfaceVariantDark = Color(0xFF22222E)

    val glassTint = Color(0xFFEFEFF5)
    val glassTintDark = Color(0xFF1E1E2A)

    val glassHighlight = Color(0xFFFFFFFF)
    val glassHighlightDark = Color(0xFF2A2A38)

    val strokeLight = Color(0x1A000000)
    val strokeDark = Color(0x28FFFFFF)

    val textPrimary = Color(0xFF111118)
    val textPrimaryDark = Color(0xFFF2F2F7)
    val textSecondary = Color(0xFF5A5A66)
    val textSecondaryDark = Color(0xFF9595A2)
    val textTertiary = Color(0xFF8A8A95)
    val textTertiaryDark = Color(0xFF6E6E7A)

    val accentBlue = Color(0xFF0A84FF)
    val accentIndigo = Color(0xFF5E5CE6)
    val accentMint = Color(0xFF00C896)
    val accentPink = Color(0xFFFF375F)
    val accentGold = Color(0xFFFF9F0A)
    val accentViolet = Color(0xFFBF5AF2)
    val accentTeal = Color(0xFF64D2FF)
    val accentCoral = Color(0xFFFF6482)
    val accentLime = Color(0xFF32D74B)

    val up = Color(0xFF00C896)
    val down = Color(0xFFFF453A)
    val warning = Color(0xFFFF9F0A)

    val aurora1 = Color(0xFF6E5CE6)
    val aurora2 = Color(0xFF00C896)
    val aurora3 = Color(0xFFFF375F)
    val aurora4 = Color(0xFF00B4FF)
    val aurora5 = Color(0xFFBF5AF2)

    val aurora1Dark = Color(0xFF4A3FB8)
    val aurora2Dark = Color(0xFF008866)
    val aurora3Dark = Color(0xFFB8254A)
    val aurora4Dark = Color(0xFF0080BF)
    val aurora5Dark = Color(0xFF8E40B8)
}

data class GlassTheme(
    val isDark: Boolean = false,
) {
    val bg: Color get() = if (isDark) GlassColors.bgBaseDark else GlassColors.bgBase
    val surface: Color get() = if (isDark) GlassColors.surfaceDark else GlassColors.surface
    val surfaceVariant: Color get() = if (isDark) GlassColors.surfaceVariantDark else GlassColors.surfaceVariant
    val glassTint: Color get() = if (isDark) GlassColors.glassTintDark else GlassColors.glassTint
    val glassHighlight: Color get() = if (isDark) GlassColors.glassHighlightDark else GlassColors.glassHighlight
    val stroke: Color get() = if (isDark) GlassColors.strokeDark else GlassColors.strokeLight
    val textPrimary: Color get() = if (isDark) GlassColors.textPrimaryDark else GlassColors.textPrimary
    val textSecondary: Color get() = if (isDark) GlassColors.textSecondaryDark else GlassColors.textSecondary
    val textTertiary: Color get() = if (isDark) GlassColors.textTertiaryDark else GlassColors.textTertiary
}

val LocalGlassTheme = staticCompositionLocalOf { GlassTheme() }

fun Modifier.liquidGlass(corner: Dp = 22.dp, theme: GlassTheme = GlassTheme()): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(theme.glassTint.copy(alpha = if (theme.isDark) 0.65f else 0.78f))
        .border(0.5.dp, theme.stroke, shape)
}

fun Modifier.glowBorder(corner: Dp = 22.dp, glow: Color = GlassColors.accentBlue, intensity: Float = 0.4f): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .border(1.5.dp, glow.copy(alpha = intensity), shape)
}

@Composable
fun Modifier.glass(corner: Dp = 22.dp): Modifier = this.liquidGlass(corner, LocalGlassTheme.current)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalGlassTheme.current
    Column(modifier = modifier.liquidGlass(corner, theme).padding(18.dp), content = content)
}

@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = LocalGlassTheme.current
    Box(modifier.background(theme.bg)) {
        AuroraCanvas(isDark = theme.isDark, modifier = Modifier.fillMaxSize())
        content()
    }
}

@Composable
fun AuroraCanvas(isDark: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "phase2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(34000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "phase3"
    )

    val a1 = if (isDark) GlassColors.aurora1Dark else GlassColors.aurora1
    val a2 = if (isDark) GlassColors.aurora2Dark else GlassColors.aurora2
    val a3 = if (isDark) GlassColors.aurora3Dark else GlassColors.aurora3
    val a4 = if (isDark) GlassColors.aurora4Dark else GlassColors.aurora4
    val a5 = if (isDark) GlassColors.aurora5Dark else GlassColors.aurora5
    val alphaMul = if (isDark) 0.30f else 0.16f

    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a1.copy(alpha = alphaMul * (0.85f + 0.3f * phase1)), Color.Transparent),
                center = Offset(size.width * (0.15f + 0.10f * phase2), size.height * (0.10f + 0.05f * phase3)),
                radius = size.width * 0.95f,
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * (0.15f + 0.10f * phase2), size.height * (0.10f + 0.05f * phase3)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a2.copy(alpha = alphaMul * (0.9f + 0.2f * phase2)), Color.Transparent),
                center = Offset(size.width * (0.85f - 0.08f * phase1), size.height * (0.28f + 0.06f * phase3)),
                radius = size.width * 0.85f,
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * (0.85f - 0.08f * phase1), size.height * (0.28f + 0.06f * phase3)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a3.copy(alpha = alphaMul * (0.8f + 0.3f * phase3)), Color.Transparent),
                center = Offset(size.width * (0.50f + 0.15f * phase1), size.height * (0.55f + 0.05f * phase2)),
                radius = size.width * 0.95f,
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * (0.50f + 0.15f * phase1), size.height * (0.55f + 0.05f * phase2)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a4.copy(alpha = alphaMul * 0.6f * (0.7f + 0.3f * phase2)), Color.Transparent),
                center = Offset(size.width * (0.20f - 0.05f * phase3), size.height * (0.78f + 0.04f * phase1)),
                radius = size.width * 0.7f,
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * (0.20f - 0.05f * phase3), size.height * (0.78f + 0.04f * phase1)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a5.copy(alpha = alphaMul * 0.7f * (0.85f + 0.2f * phase3)), Color.Transparent),
                center = Offset(size.width * (0.92f - 0.05f * phase1), size.height * (0.88f - 0.04f * phase2)),
                radius = size.width * 0.7f,
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * (0.92f - 0.05f * phase1), size.height * (0.88f - 0.04f * phase2)),
        )
    }
}

@Composable
fun ProvideGlassTheme(isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassTheme provides GlassTheme(isDark)) {
        content()
    }
}
