package com.fifaglass.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.LocalGlassTheme

data class WinProbabilityPoint(
    val minute: Int,
    val pHome: Float,
    val pDraw: Float,
    val pAway: Float
)

private fun generateSimulatedData(): List<WinProbabilityPoint> {
    val result = mutableListOf<WinProbabilityPoint>()
    var home = 0.45f
    var draw = 0.30f
    val rng = java.util.Random(20260731L)
    for (m in 0..90 step 3) {
        val dHome = (rng.nextFloat() - 0.5f) * 0.08f
        val dDraw = (rng.nextFloat() - 0.5f) * 0.06f
        home = (home + dHome).coerceIn(0.1f, 0.8f)
        draw = (draw + dDraw).coerceIn(0.05f, 0.5f)
        val away = (1f - home - draw).coerceIn(0.05f, 0.8f)
        result.add(WinProbabilityPoint(m, home, draw, away))
    }
    return result
}

private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i < points.size - 2) points[i + 2] else p2
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

@Composable
fun WinProbabilityChart(
    modifier: Modifier = Modifier,
    homeName: String,
    awayName: String,
    dataPoints: List<WinProbabilityPoint>,
    currentMinute: Int
) {
    val theme = LocalGlassTheme.current
    var trigger by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { trigger = 1f }
    val progress by animateFloatAsState(
        targetValue = trigger,
        animationSpec = tween(durationMillis = 1200),
        label = "drawProgress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
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
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val points = remember(dataPoints) {
        if (dataPoints.size >= 2) dataPoints else generateSimulatedData()
    }
    val textMeasurer = rememberTextMeasurer()

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "胜率走势",
                color = GlassColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendDot(GlassColors.accentMint, homeName)
                LegendDot(GlassColors.textSecondary, "平局")
                LegendDot(GlassColors.accentPink, awayName)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftPad = 38.dp.toPx()
                    val rightPad = 10.dp.toPx()
                    val topPad = 8.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val chartW = size.width - leftPad - rightPad
                    val chartH = size.height - topPad - bottomPad

                    for (i in 0..4) {
                        val y = topPad + chartH * (i / 4f)
                        drawLine(
                            color = GlassColors.textSecondary.copy(alpha = 0.15f),
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + chartW, y),
                            strokeWidth = 1f
                        )
                        val label = "${100 - i * 25}%"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(0f, y - 6.dp.toPx()),
                            style = TextStyle(
                                color = GlassColors.textSecondary,
                                fontSize = 9.sp
                            )
                        )
                    }
                    for (m in 0..90 step 15) {
                        val x = leftPad + chartW * (m / 90f)
                        drawLine(
                            color = GlassColors.textSecondary.copy(alpha = 0.10f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + chartH),
                            strokeWidth = 1f
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${m}'",
                            topLeft = Offset(x - 7.dp.toPx(), topPad + chartH + 4.dp.toPx()),
                            style = TextStyle(
                                color = GlassColors.textSecondary,
                                fontSize = 9.sp
                            )
                        )
                    }

                    val xOf = { minute: Int ->
                        leftPad + chartW * (minute.coerceIn(0, 90) / 90f)
                    }
                    val yOf = { p: Float ->
                        topPad + chartH * (1f - p.coerceIn(0f, 1f))
                    }

                    val visibleCount = (points.size * progress).toInt().coerceIn(1, points.size)
                    val visible = points.subList(0, visibleCount)

                    if (visible.isNotEmpty()) {
                        val homePts = visible.map { Offset(xOf(it.minute), yOf(it.pHome)) }
                        val drawPts = visible.map { Offset(xOf(it.minute), yOf(it.pDraw)) }
                        val awayPts = visible.map { Offset(xOf(it.minute), yOf(it.pAway)) }

                        val homePath = buildSmoothPath(homePts)
                        val fillPath = Path().apply {
                            addPath(homePath)
                            lineTo(xOf(visible.last().minute), topPad + chartH)
                            lineTo(xOf(visible.first().minute), topPad + chartH)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Aurora.success()
                        )
                        drawPath(
                            path = homePath,
                            color = GlassColors.accentMint,
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = buildSmoothPath(drawPts),
                            color = GlassColors.textSecondary.copy(alpha = 0.8f),
                            style = Stroke(width = 2f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = buildSmoothPath(awayPts),
                            color = GlassColors.accentPink,
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )

                        drawCircle(GlassColors.accentMint, 4.dp.toPx(), homePts.last())
                        drawCircle(GlassColors.textSecondary, 3.dp.toPx(), drawPts.last())
                        drawCircle(GlassColors.accentPink, 4.dp.toPx(), awayPts.last())
                    }

                    val cx = xOf(currentMinute)
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(cx, topPad),
                        end = Offset(cx, topPad + chartH),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = pulseAlpha),
                        radius = 5.dp.toPx() * pulseScale,
                        center = Offset(cx, topPad)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(cx, topPad)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            color = GlassColors.textSecondary,
            fontSize = 11.sp
        )
    }
}
