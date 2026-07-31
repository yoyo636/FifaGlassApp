package com.fifaglass.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.LocalGlassTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class PlayerStats(
    val passing: Float,
    val shooting: Float,
    val dribbling: Float,
    val defending: Float,
    val physical: Float,
    val pace: Float
)

@Composable
fun PlayerRadarChart(
    modifier: Modifier = Modifier,
    playerName: String,
    playerNumber: Int,
    stats: PlayerStats
) {
    val theme = LocalGlassTheme.current
    val animPassing by animateFloatAsState(
        targetValue = stats.passing, animationSpec = tween(700), label = "passing"
    )
    val animShooting by animateFloatAsState(
        targetValue = stats.shooting, animationSpec = tween(700), label = "shooting"
    )
    val animDribbling by animateFloatAsState(
        targetValue = stats.dribbling, animationSpec = tween(700), label = "dribbling"
    )
    val animDefending by animateFloatAsState(
        targetValue = stats.defending, animationSpec = tween(700), label = "defending"
    )
    val animPhysical by animateFloatAsState(
        targetValue = stats.physical, animationSpec = tween(700), label = "physical"
    )
    val animPace by animateFloatAsState(
        targetValue = stats.pace, animationSpec = tween(700), label = "pace"
    )
    val animatedValues = listOf(
        animPassing, animShooting, animDribbling, animDefending, animPhysical, animPace
    )
    val labels = listOf("传球", "射门", "盘带", "防守", "身体", "速度")

    val textMeasurer = rememberTextMeasurer()

    GlassCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = (min(size.width, size.height) / 2f) * 0.7f
                val angles = (0..5).map { -Math.PI / 2.0 + it * Math.PI / 3.0 }

                for (layer in 1..4) {
                    val r = maxR * (layer / 4f)
                    val gridPath = Path()
                    angles.forEachIndexed { i, a ->
                        val x = cx + r * cos(a).toFloat()
                        val y = cy + r * sin(a).toFloat()
                        if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                    }
                    gridPath.close()
                    drawPath(
                        path = gridPath,
                        color = GlassColors.textSecondary.copy(alpha = 0.15f),
                        style = Stroke(width = 1f)
                    )
                }

                angles.forEach { a ->
                    drawLine(
                        color = GlassColors.textSecondary.copy(alpha = 0.2f),
                        start = Offset(cx, cy),
                        end = Offset(
                            cx + maxR * cos(a).toFloat(),
                            cy + maxR * sin(a).toFloat()
                        ),
                        strokeWidth = 1f
                    )
                }

                val dataPts = angles.mapIndexed { i, a ->
                    val v = (animatedValues[i] / 100f).coerceIn(0f, 1f)
                    Offset(
                        cx + maxR * v * cos(a).toFloat(),
                        cy + maxR * v * sin(a).toFloat()
                    )
                }
                val dataPath = Path()
                dataPts.forEachIndexed { i, p ->
                    if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
                }
                dataPath.close()
                drawPath(
                    path = dataPath,
                    brush = Aurora.primary(isDark = false)
                )
                drawPath(
                    path = dataPath,
                    color = GlassColors.accentBlue,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
                dataPts.forEach { p ->
                    drawCircle(GlassColors.accentBlue, 4.dp.toPx(), p)
                    drawCircle(GlassColors.textPrimary, 1.6.dp.toPx(), p)
                }

                labels.forEachIndexed { i, label ->
                    val a = angles[i]
                    val labelR = maxR * 1.18f
                    val lx = cx + labelR * cos(a).toFloat()
                    val ly = cy + labelR * sin(a).toFloat()
                    val txt = "$label ${animatedValues[i].toInt()}"
                    val measured = textMeasurer.measure(
                        text = txt,
                        style = TextStyle(
                            color = GlassColors.textSecondary,
                            fontSize = 10.sp
                        )
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            lx - measured.size.width / 2f,
                            ly - measured.size.height / 2f
                        )
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#$playerNumber",
                    color = GlassColors.accentGold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = playerName,
                    color = GlassColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
