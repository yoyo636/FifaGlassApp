package com.fifaglass.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.glowBorder
import androidx.compose.ui.graphics.lerp

data class TimelineEvent(
    val minute: Int,
    val type: String,
    val team: String,
    val title: String,
    val description: String,
    val importance: Float
)

private fun iconForType(type: String): String = when (type) {
    "进球" -> "\u26BD"
    "黄牌" -> "\uD83D\uDFE8"
    "红牌" -> "\uD83D\uDFE5"
    "换人" -> "\uD83D\uDD01"
    "开球" -> "\uD83D\uDFE2"
    "半场" -> "\u23F8"
    "终场" -> "\uD83C\uDFC1"
    else -> "\u2022"
}

private fun importanceColor(importance: Float): Color {
    val f = importance.coerceIn(0f, 1f)
    return when {
        f < 0.5f -> lerp(GlassColors.accentGold, GlassColors.accentMint, f / 0.5f)
        else -> lerp(GlassColors.accentMint, GlassColors.accentPink, (f - 0.5f) / 0.5f)
    }
}

@Composable
fun TimelineTheater(
    modifier: Modifier = Modifier,
    events: List<TimelineEvent>,
    homeTeam: String,
    awayTeam: String,
    currentMinute: Int
) {
    val theme = LocalGlassTheme.current
    val infiniteTransition = rememberInfiniteTransition(label = "timeline")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "比赛时间轴",
                color = GlassColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                itemsIndexed(events) { index, event ->
                    val isHome = event.team == homeTeam
                    val nextMinute = events.getOrNull(index + 1)?.minute ?: 90
                    val isCurrent = when {
                        index == 0 && currentMinute <= event.minute -> true
                        currentMinute in event.minute until nextMinute -> true
                        else -> false
                    }
                    val currentFrac = if (isCurrent && nextMinute > event.minute) {
                        ((currentMinute - event.minute).coerceAtLeast(0).toFloat() /
                                (nextMinute - event.minute).toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    TimelineEventItem(
                        event = event,
                        isHome = isHome,
                        isFirst = index == 0,
                        isLast = index == events.lastIndex,
                        showCurrent = isCurrent,
                        currentFrac = currentFrac,
                        pulseAlpha = pulseAlpha,
                        pulseScale = pulseScale
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventItem(
    event: TimelineEvent,
    isHome: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    showCurrent: Boolean,
    currentFrac: Float,
    pulseAlpha: Float,
    pulseScale: Float
) {
    val textMeasurer = rememberTextMeasurer()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "scale"
    )

    val eventColor = importanceColor(event.importance)
    val highImportance = event.importance > 0.7f

    Box(
        modifier = Modifier
            .width(128.dp)
            .height(280.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val centerX = w / 2f

                drawRect(
                    brush = Aurora.primary(isDark = false),
                    topLeft = Offset(0f, cy - 1f),
                    size = Size(w, 2f)
                )

                if (isFirst) {
                    drawCircle(
                        color = GlassColors.textSecondary.copy(alpha = 0.8f),
                        radius = 5.dp.toPx(),
                        center = Offset(0f, cy)
                    )
                    val m0 = textMeasurer.measure(
                        text = "0'",
                        style = TextStyle(color = GlassColors.textSecondary, fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = m0,
                        topLeft = Offset(0f, cy + 6.dp.toPx())
                    )
                }
                if (isLast) {
                    drawCircle(
                        color = GlassColors.textSecondary.copy(alpha = 0.8f),
                        radius = 5.dp.toPx(),
                        center = Offset(w, cy)
                    )
                    val m90 = textMeasurer.measure(
                        text = "90'",
                        style = TextStyle(color = GlassColors.textSecondary, fontSize = 9.sp)
                    )
                    drawText(
                        textLayoutResult = m90,
                        topLeft = Offset(w - m90.size.width, cy + 6.dp.toPx())
                    )
                }

                drawCircle(
                    color = GlassColors.textPrimary,
                    radius = 3.dp.toPx(),
                    center = Offset(centerX, cy)
                )
                drawCircle(
                    color = eventColor,
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )

                val connectorEnd = if (isHome) cy - 40.dp.toPx() else cy + 40.dp.toPx()
                drawLine(
                    color = eventColor.copy(alpha = 0.6f),
                    start = Offset(centerX, cy),
                    end = Offset(centerX, connectorEnd),
                    strokeWidth = 1.5f
                )

                if (showCurrent) {
                    val markerX = centerX + currentFrac * w
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(markerX, 0f),
                        end = Offset(markerX, h),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = pulseAlpha),
                        radius = 6.dp.toPx() * pulseScale,
                        center = Offset(markerX, cy)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(markerX, cy)
                    )
                }
            }
    ) {
        if (isHome) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                EventCard(
                    event = event,
                    color = eventColor,
                    highImportance = highImportance,
                    scale = scale
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                EventCard(
                    event = event,
                    color = eventColor,
                    highImportance = highImportance,
                    scale = scale
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    event: TimelineEvent,
    color: Color,
    highImportance: Boolean,
    scale: Float
) {
    val cardModifier = Modifier
        .width(120.dp)
        .scale(scale, scale)
        .alpha(scale)
        .then(
            if (highImportance) {
                Modifier.glowBorder(glow = color, intensity = 0.8f)
            } else {
                Modifier
            }
        )
    GlassCard(modifier = cardModifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${event.minute}'",
                    color = GlassColors.accentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = iconForType(event.type),
                    fontSize = 18.sp
                )
            }
            Text(
                text = event.title,
                color = GlassColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.description,
                color = GlassColors.textSecondary,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(event.importance.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(color, color.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
