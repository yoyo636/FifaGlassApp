package com.fifaglass.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 液态玻璃（Liquid Glass）视觉体系：深色底 + 彩色光斑 + 半透明高光玻璃卡片 */
object GlassColors {
    val bgTop = Color(0xFF0A0F1E)
    val bgBottom = Color(0xFF141C33)
    val cardHi = Color.White.copy(alpha = 0.14f)
    val cardLo = Color.White.copy(alpha = 0.05f)
    val strokeHi = Color.White.copy(alpha = 0.45f)
    val strokeLo = Color.White.copy(alpha = 0.08f)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.62f)
    val accentBlue = Color(0xFF7C9EFF)
    val accentMint = Color(0xFF4AE3C7)
    val accentPink = Color(0xFFFF8FB2)
    val accentGold = Color(0xFFFFD57C)
    val accentViolet = Color(0xFFB49CFF)
    val up = Color(0xFF5BE49B)
    val down = Color(0xFFFF7A7A)
}

fun Modifier.glass(corner: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(GlassColors.cardHi, GlassColors.cardLo)))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(GlassColors.strokeHi, GlassColors.strokeLo)),
            shape
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.glass(corner).padding(16.dp), content = content)
}

/** 全局背景：深色渐变 + 三团彩色径向光斑，透过玻璃卡片产生"液态"感 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(GlassColors.bgTop, GlassColors.bgBottom))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3B5BDB).copy(alpha = 0.50f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.16f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.12f, size.height * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0CA678).copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.42f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.95f, size.height * 0.42f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD6336C).copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.35f, size.height * 0.95f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.35f, size.height * 0.95f),
            )
        }
        content()
    }
}
