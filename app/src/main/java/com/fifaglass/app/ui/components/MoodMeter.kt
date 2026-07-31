package com.fifaglass.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.glowBorder

@Composable
fun MoodMeter(
    modifier: Modifier = Modifier,
    homeMomentum: Float,
    awayMomentum: Float,
    matchMinute: Int,
    intensity: Float
) {
    val theme = LocalGlassTheme.current
    val homePct = (homeMomentum.coerceIn(0f, 1f) * 100).toInt()
    val awayPct = (awayMomentum.coerceIn(0f, 1f) * 100).toInt()

    val moodLabel = when {
        intensity < 0.2f -> "平静"
        intensity < 0.4f -> "紧张"
        intensity < 0.6f -> "激烈"
        intensity < 0.8f -> "白热化"
        else -> "疯狂！"
    }
    val moodColor = when {
        intensity < 0.2f -> GlassColors.textSecondary
        intensity < 0.4f -> GlassColors.accentGold
        intensity < 0.6f -> GlassColors.accentMint
        intensity < 0.8f -> GlassColors.accentPink
        else -> GlassColors.accentPink
    }

    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val flow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isHot = intensity > 0.6f
    val cardModifier = if (isHot) {
        modifier.glowBorder(glow = GlassColors.accentPink, intensity = pulseAlpha)
    } else {
        modifier
    }

    GlassCard(modifier = cardModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "比赛情绪",
                    color = GlassColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${matchMinute}'",
                    color = GlassColors.accentGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                val w = size.width
                val h = size.height
                val half = w / 2f
                val homeW = half * homeMomentum.coerceIn(0f, 1f)
                val awayW = half * awayMomentum.coerceIn(0f, 1f)
                val corner = CornerRadius(14.dp.toPx(), 14.dp.toPx())

                drawRoundRect(
                    color = GlassColors.textSecondary.copy(alpha = 0.12f),
                    cornerRadius = corner
                )
                if (homeW > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                GlassColors.accentMint.copy(alpha = 0.55f),
                                GlassColors.accentMint
                            ),
                            startX = half - homeW,
                            endX = half
                        ),
                        topLeft = Offset(half - homeW, 0f),
                        size = Size(homeW, h)
                    )
                }
                if (awayW > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                GlassColors.accentPink,
                                GlassColors.accentPink.copy(alpha = 0.55f)
                            ),
                            startX = half,
                            endX = half + awayW
                        ),
                        topLeft = Offset(half, 0f),
                        size = Size(awayW, h)
                    )
                }
                val shimmerW = 48.dp.toPx()
                val shimmerX = flow * (w + shimmerW) - shimmerW
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        startX = shimmerX,
                        endX = shimmerX + shimmerW
                    ),
                    topLeft = Offset(shimmerX, 0f),
                    size = Size(shimmerW, h)
                )
                drawLine(
                    color = Color.White,
                    start = Offset(half, 0f),
                    end = Offset(half, h),
                    strokeWidth = 2f
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "主队 $homePct%",
                    color = GlassColors.accentMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "客队 $awayPct%",
                    color = GlassColors.accentPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(moodColor.copy(alpha = pulseAlpha))
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = moodLabel,
                    color = moodColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
