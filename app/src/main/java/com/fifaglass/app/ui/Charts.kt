package com.fifaglass.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.rating.EvalDim
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 六维能力雷达图；dimsB 非空时叠加第二组数据（球队对比） */
@Composable
fun RadarChart(
    dims: List<EvalDim>,
    modifier: Modifier = Modifier,
    dimsB: List<EvalDim>? = null,
) {
    if (dims.isEmpty()) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f * 0.60f
        val n = dims.size

        fun point(i: Int, radius: Float): Offset {
            val a = Math.toRadians((-90f + i * 360f / n).toDouble())
            return Offset(cx + (radius * cos(a)).toFloat(), cy + (radius * sin(a)).toFloat())
        }

        Canvas(Modifier.fillMaxSize()) {
            for (ring in 1..4) {
                val p = Path()
                for (i in 0..n) {
                    val pt = point(i % n, r * ring / 4f)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                drawPath(p, Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.dp.toPx()))
            }
            for (i in 0 until n) {
                val pt = point(i, r)
                drawLine(
                    Color.White.copy(alpha = 0.12f), Offset(cx, cy), pt,
                    strokeWidth = 1.dp.toPx()
                )
            }

            fun drawSeries(series: List<EvalDim>, fill: Brush, stroke: Color) {
                val data = Path()
                series.forEachIndexed { i, d ->
                    val pt = point(i, r * (d.value.coerceIn(0f, 100f) / 100f))
                    if (i == 0) data.moveTo(pt.x, pt.y) else data.lineTo(pt.x, pt.y)
                }
                data.close()
                drawPath(data, fill)
                drawPath(data, stroke, style = Stroke(width = 2.dp.toPx()))
                series.forEachIndexed { i, d ->
                    val pt = point(i, r * (d.value.coerceIn(0f, 100f) / 100f))
                    drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pt)
                }
            }

            drawSeries(
                dims,
                Brush.radialGradient(
                    listOf(
                        GlassColors.accentBlue.copy(alpha = 0.55f),
                        GlassColors.accentMint.copy(alpha = 0.22f)
                    ),
                    center = Offset(cx, cy),
                    radius = r
                ),
                GlassColors.accentBlue
            )
            if (dimsB != null && dimsB.size == n) {
                drawSeries(
                    dimsB,
                    Brush.radialGradient(
                        listOf(
                            GlassColors.accentPink.copy(alpha = 0.45f),
                            GlassColors.accentGold.copy(alpha = 0.18f)
                        ),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    GlassColors.accentPink
                )
            }
        }

        val labelR = r + with(density) { 20.dp.toPx() }
        val halfLabel = with(density) { 42.dp.toPx() }
        val halfLabelH = with(density) { 9.dp.toPx() }
        dims.forEachIndexed { i, d ->
            val a = Math.toRadians((-90f + i * 360f / n).toDouble())
            val x = cx + (labelR * cos(a)).toFloat()
            val y = cy + (labelR * sin(a)).toFloat()
            Box(
                Modifier
                    .offset { IntOffset((x - halfLabel).roundToInt(), (y - halfLabelH).roundToInt()) }
                    .width(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    d.label,
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/** 横向渐变条形行：排名 + 名称 + 条形 + 数值 */
@Composable
fun HBarRow(
    label: String,
    value: Double,
    maxValue: Double,
    valueText: String,
    barBrush: Brush,
    modifier: Modifier = Modifier,
    rank: Int? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (rank != null) {
            Text(
                "$rank",
                color = GlassColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(26.dp)
            )
        }
        Text(
            label,
            color = GlassColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((value / maxValue).toFloat().coerceIn(0.015f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(barBrush)
            )
        }
        Text(
            valueText,
            color = GlassColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(58.dp)
        )
    }
}

val blueMintBrush: Brush
    get() = Brush.horizontalGradient(listOf(GlassColors.accentBlue, GlassColors.accentMint))

val pinkGoldBrush: Brush
    get() = Brush.horizontalGradient(listOf(GlassColors.accentPink, GlassColors.accentGold))

val violetBlueBrush: Brush
    get() = Brush.horizontalGradient(listOf(GlassColors.accentViolet, GlassColors.accentBlue))
