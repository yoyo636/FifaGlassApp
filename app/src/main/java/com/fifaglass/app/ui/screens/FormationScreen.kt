package com.fifaglass.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.GlassTheme
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.glowBorder

@Composable
fun FormationScreen(
    home: Team,
    away: Team,
    homeTactics: String?,
    awayTactics: String?,
    onBack: () -> Unit,
) {
    val theme = LocalGlassTheme.current
    var homeFormation by remember {
        mutableStateOf(normalizeFormation(homeTactics))
    }
    var awayFormation by remember {
        mutableStateOf(normalizeFormation(awayTactics))
    }

    val formationOptions = listOf("4-3-3", "4-4-2", "3-5-2", "4-2-3-1", "5-3-2", "3-4-3", "4-5-1")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aurora.cool())
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FormationHeader(onBack = onBack, theme = theme)
            }

            item {
                FootballField(
                    homeFormation = homeFormation,
                    awayFormation = awayFormation,
                    home = home,
                    away = away,
                    theme = theme,
                )
            }

            item {
                Text("阵型切换", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("点击选择不同阵型查看球员站位", color = theme.textSecondary, fontSize = 12.sp)
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(formationOptions) { formation ->
                        val isSelected = homeFormation == formation && awayFormation == formation
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Aurora.primary(isDark = false)
                                    else SolidColor(theme.surfaceVariant.copy(alpha = 0.6f))
                                )
                                .clickable {
                                    homeFormation = formation
                                    awayFormation = formation
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formation,
                                color = if (isSelected) Color.White else theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            item {
                TacticalAnalysisCard(home = home, away = away, homeFormation = homeFormation, awayFormation = awayFormation, theme = theme)
            }
        }
    }
}

@Composable
private fun FormationHeader(onBack: () -> Unit, theme: GlassTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Aurora.cool())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "战术阵型",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "交互式球场阵型可视化",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🗺️", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun FootballField(
    homeFormation: String,
    awayFormation: String,
    home: Team,
    away: Team,
    theme: GlassTheme,
) {
    val homePositions = remember(homeFormation) {
        getFormationPositions(homeFormation, isHome = true)
    }
    val awayPositions = remember(awayFormation) {
        getFormationPositions(awayFormation, isHome = false)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .glowBorder(glow = GlassColors.accentMint, intensity = 0.25f),
        corner = 16.dp,
    ) {
        Spacer(Modifier.height(4.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            val fieldW = maxWidth
            val fieldH = maxHeight
            val dotSize = 34.dp

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFieldBackground()
                drawFieldLines()
                drawConnectionLines(homePositions)
                drawConnectionLines(awayPositions)
            }

            homePositions.forEach { pos ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (pos.x * fieldW.value).dp - dotSize / 2,
                            y = (pos.y * fieldH.value).dp - dotSize / 2,
                        )
                        .size(dotSize)
                        .shadow(4.dp, CircleShape, ambientColor = GlassColors.accentBlue.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, GlassColors.accentBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        pos.number.toString(),
                        color = GlassColors.accentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            awayPositions.forEach { pos ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (pos.x * fieldW.value).dp - dotSize / 2,
                            y = (pos.y * fieldH.value).dp - dotSize / 2,
                        )
                        .size(dotSize)
                        .shadow(4.dp, CircleShape, ambientColor = GlassColors.accentPink.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, GlassColors.accentPink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        pos.number.toString(),
                        color = GlassColors.accentPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    "${home.name} $homeFormation",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.accentPink.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    "${away.name} $awayFormation",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFieldBackground() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A5632),
                Color(0xFF2D7A47),
                Color(0xFF1A5632),
            )
        )
    )
    val stripeCount = 10
    val stripeHeight = size.height / stripeCount
    for (i in 0 until stripeCount step 2) {
        drawRect(
            color = Color(0xFF246B3C).copy(alpha = 0.5f),
            topLeft = Offset(0f, i * stripeHeight),
            size = Size(size.width, stripeHeight),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFieldLines() {
    val lineColor = Color.White.copy(alpha = 0.65f)
    val lineWidth = 2f
    val margin = 8f

    drawRect(
        color = lineColor,
        topLeft = Offset(margin, margin),
        size = Size(size.width - margin * 2, size.height - margin * 2),
        style = Stroke(width = lineWidth),
    )

    drawLine(
        color = lineColor,
        start = Offset(margin, size.height / 2),
        end = Offset(size.width - margin, size.height / 2),
        strokeWidth = lineWidth,
    )

    val centerRadius = size.width * 0.1f
    drawCircle(
        color = lineColor,
        radius = centerRadius,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = lineWidth),
    )
    drawCircle(
        color = lineColor,
        radius = 3f,
        center = Offset(size.width / 2, size.height / 2),
    )

    val paWidth = size.width * 0.6f
    val paHeight = size.height * 0.14f
    val paLeft = (size.width - paWidth) / 2

    drawRect(
        color = lineColor,
        topLeft = Offset(paLeft, margin),
        size = Size(paWidth, paHeight),
        style = Stroke(width = lineWidth),
    )
    drawRect(
        color = lineColor,
        topLeft = Offset(paLeft, size.height - paHeight - margin),
        size = Size(paWidth, paHeight),
        style = Stroke(width = lineWidth),
    )

    val gaWidth = size.width * 0.3f
    val gaHeight = size.height * 0.05f
    val gaLeft = (size.width - gaWidth) / 2

    drawRect(
        color = lineColor,
        topLeft = Offset(gaLeft, margin),
        size = Size(gaWidth, gaHeight),
        style = Stroke(width = lineWidth),
    )
    drawRect(
        color = lineColor,
        topLeft = Offset(gaLeft, size.height - gaHeight - margin),
        size = Size(gaWidth, gaHeight),
        style = Stroke(width = lineWidth),
    )

    val goalWidth = size.width * 0.16f
    val goalHeight = 6f
    val goalLeft = (size.width - goalWidth) / 2

    drawRect(
        color = lineColor,
        topLeft = Offset(goalLeft, margin - goalHeight / 2),
        size = Size(goalWidth, goalHeight),
        style = Stroke(width = lineWidth),
    )
    drawRect(
        color = lineColor,
        topLeft = Offset(goalLeft, size.height - goalHeight / 2 - margin),
        size = Size(goalWidth, goalHeight),
        style = Stroke(width = lineWidth),
    )

    drawCircle(
        color = lineColor,
        radius = 3f,
        center = Offset(size.width / 2, margin + paHeight * 0.75f),
    )
    drawCircle(
        color = lineColor,
        radius = 3f,
        center = Offset(size.width / 2, size.height - margin - paHeight * 0.75f),
    )

    val arcWidth = size.width * 0.18f
    drawArc(
        color = lineColor,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width / 2 - arcWidth, size.height / 2 - arcWidth),
        size = Size(arcWidth * 2, arcWidth * 2),
        style = Stroke(width = lineWidth),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnectionLines(
    positions: List<PlayerPos>,
) {
    val lineColor = Color.White.copy(alpha = 0.25f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

    val grouped = positions.groupBy { it.y }
    val sortedYs = grouped.keys.sorted()

    for ((y, players) in grouped) {
        val sorted = players.sortedBy { it.x }
        for (i in 0 until sorted.size - 1) {
            val p1 = sorted[i]
            val p2 = sorted[i + 1]
            drawLine(
                color = lineColor,
                start = Offset(p1.x * size.width, p1.y * size.height),
                end = Offset(p2.x * size.width, p2.y * size.height),
                strokeWidth = 1.5f,
                pathEffect = dashEffect,
            )
        }
    }

    for (i in 0 until sortedYs.size - 1) {
        val currentLine = grouped[sortedYs[i]]!!.sortedBy { it.x }
        val nextLine = grouped[sortedYs[i + 1]]!!.sortedBy { it.x }
        for (p1 in currentLine) {
            val nearest = nextLine.minByOrNull { p2 ->
                kotlin.math.abs(p1.x - p2.x)
            } ?: continue
            drawLine(
                color = lineColor,
                start = Offset(p1.x * size.width, p1.y * size.height),
                end = Offset(nearest.x * size.width, nearest.y * size.height),
                strokeWidth = 1.5f,
                pathEffect = dashEffect,
            )
        }
    }
}

private data class PlayerPos(val x: Float, val y: Float, val number: Int)

private fun getFormationPositions(formation: String, isHome: Boolean): List<PlayerPos> {
    val base = when (formation) {
        "4-3-3" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.2f, 0.15f, 2), Triple(0.4f, 0.15f, 3), Triple(0.6f, 0.15f, 4), Triple(0.8f, 0.15f, 5),
            Triple(0.3f, 0.28f, 6), Triple(0.5f, 0.28f, 7), Triple(0.7f, 0.28f, 8),
            Triple(0.25f, 0.4f, 9), Triple(0.5f, 0.4f, 10), Triple(0.75f, 0.4f, 11),
        )
        "4-4-2" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.2f, 0.15f, 2), Triple(0.4f, 0.15f, 3), Triple(0.6f, 0.15f, 4), Triple(0.8f, 0.15f, 5),
            Triple(0.15f, 0.28f, 6), Triple(0.38f, 0.28f, 7), Triple(0.62f, 0.28f, 8), Triple(0.85f, 0.28f, 11),
            Triple(0.38f, 0.4f, 9), Triple(0.62f, 0.4f, 10),
        )
        "3-5-2" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.25f, 0.15f, 2), Triple(0.5f, 0.15f, 3), Triple(0.75f, 0.15f, 4),
            Triple(0.1f, 0.28f, 5), Triple(0.3f, 0.28f, 6), Triple(0.5f, 0.28f, 7), Triple(0.7f, 0.28f, 8), Triple(0.9f, 0.28f, 11),
            Triple(0.38f, 0.4f, 9), Triple(0.62f, 0.4f, 10),
        )
        "4-2-3-1" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.2f, 0.15f, 2), Triple(0.4f, 0.15f, 3), Triple(0.6f, 0.15f, 4), Triple(0.8f, 0.15f, 5),
            Triple(0.35f, 0.22f, 6), Triple(0.65f, 0.22f, 7),
            Triple(0.25f, 0.35f, 8), Triple(0.5f, 0.35f, 9), Triple(0.75f, 0.35f, 10),
            Triple(0.5f, 0.42f, 11),
        )
        "5-3-2" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.15f, 0.15f, 2), Triple(0.32f, 0.15f, 3), Triple(0.5f, 0.15f, 4), Triple(0.68f, 0.15f, 5), Triple(0.85f, 0.15f, 6),
            Triple(0.3f, 0.28f, 7), Triple(0.5f, 0.28f, 8), Triple(0.7f, 0.28f, 9),
            Triple(0.38f, 0.4f, 10), Triple(0.62f, 0.4f, 11),
        )
        "3-4-3" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.25f, 0.15f, 2), Triple(0.5f, 0.15f, 3), Triple(0.75f, 0.15f, 4),
            Triple(0.15f, 0.28f, 5), Triple(0.38f, 0.28f, 6), Triple(0.62f, 0.28f, 7), Triple(0.85f, 0.28f, 8),
            Triple(0.25f, 0.4f, 9), Triple(0.5f, 0.4f, 10), Triple(0.75f, 0.4f, 11),
        )
        "4-5-1" -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.2f, 0.15f, 2), Triple(0.4f, 0.15f, 3), Triple(0.6f, 0.15f, 4), Triple(0.8f, 0.15f, 5),
            Triple(0.1f, 0.28f, 6), Triple(0.3f, 0.28f, 7), Triple(0.5f, 0.28f, 8), Triple(0.7f, 0.28f, 9), Triple(0.9f, 0.28f, 10),
            Triple(0.5f, 0.42f, 11),
        )
        else -> listOf(
            Triple(0.5f, 0.05f, 1),
            Triple(0.2f, 0.15f, 2), Triple(0.4f, 0.15f, 3), Triple(0.6f, 0.15f, 4), Triple(0.8f, 0.15f, 5),
            Triple(0.3f, 0.28f, 6), Triple(0.5f, 0.28f, 7), Triple(0.7f, 0.28f, 8),
            Triple(0.25f, 0.4f, 9), Triple(0.5f, 0.4f, 10), Triple(0.75f, 0.4f, 11),
        )
    }
    return base.map { (x, y, num) ->
        val actualY = if (isHome) y else 1f - y
        PlayerPos(x, actualY, num)
    }
}

private fun normalizeFormation(tactics: String?): String {
    if (tactics.isNullOrBlank()) return "4-3-3"
    val cleaned = tactics.trim().replace(" ", "")
    val known = setOf("4-3-3", "4-4-2", "3-5-2", "4-2-3-1", "5-3-2", "3-4-3", "4-5-1")
    return if (cleaned in known) cleaned else "4-3-3"
}

@Composable
private fun TacticalAnalysisCard(
    home: Team,
    away: Team,
    homeFormation: String,
    awayFormation: String,
    theme: GlassTheme,
) {
    val homeStyle = getTeamStyle(home.rank, homeFormation)
    val awayStyle = getTeamStyle(away.rank, awayFormation)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .glowBorder(glow = GlassColors.accentViolet, intensity = 0.25f)
    ) {
        Text("战术分析", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("主队风格", color = theme.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(home.name, color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(homeStyle.first, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(homeStyle.second, color = theme.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .height(80.dp)
                    .background(theme.stroke)
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("客队风格", color = theme.textSecondary, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(away.name, color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Text(awayStyle.first, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(awayStyle.second, color = theme.textSecondary, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassColors.accentGold.copy(alpha = 0.08f))
                .padding(14.dp)
        ) {
            Column {
                Text("⚡ 关键对决", color = GlassColors.accentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "中场控制权争夺将是本场比赛的核心。${home.name}采用 ${homeFormation} 阵型，${away.name}以 ${awayFormation} 应对。" +
                        "双方中场的人数配置和跑动覆盖将直接决定比赛走势。",
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassColors.accentBlue.copy(alpha = 0.08f))
                .padding(14.dp)
        ) {
            Column {
                Text("📈 预测", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val homePossession = predictPossession(home.rank, away.rank, homeFormation, awayFormation)
                Text(
                    "预计${home.name}控球率约 ${homePossession.first}%-${homePossession.second}%，" +
                        "${away.name}控球率约 ${100 - homePossession.second}%-${100 - homePossession.first}%。" +
                        generatePredictionText(home, away, homeFormation, awayFormation),
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

private fun getTeamStyle(rank: Int, formation: String): Pair<String, String> {
    val styleName = when {
        rank <= 10 -> "进攻型 · 控球为主"
        rank <= 25 -> "攻守平衡 · 灵活多变"
        rank <= 50 -> "稳守反击 · 快速转换"
        else -> "防守型 · 密集防守"
    }
    val desc = when {
        rank <= 10 -> "世界顶级强队，善于控球压迫，通过高位逼抢和快速传递主导比赛节奏。$formation 阵型有利于发挥进攻优势。"
        rank <= 25 -> "实力不俗的球队，攻守两端均有亮点，能根据对手灵活调整战术。$formation 阵型体现了攻守平衡的思路。"
        rank <= 50 -> "中游球队，主打防守反击，通过稳固防线和快速前场转换寻找得分机会。$formation 阵型有助于加强中场拦截。"
        else -> "实力相对较弱，以密集防守为核心，利用定位球和反击创造威胁。$formation 阵型强调防守稳定性。"
    }
    return styleName to desc
}

private fun predictPossession(
    homeRank: Int,
    awayRank: Int,
    homeFormation: String,
    awayFormation: String,
): Pair<Int, Int> {
    var base = 55
    if (homeRank < awayRank) base += 5
    if (homeRank > awayRank) base -= 5
    if (homeFormation.startsWith("4-3-3") || homeFormation.startsWith("4-2-3-1")) base += 2
    if (awayFormation.startsWith("5-3-2") || awayFormation.startsWith("4-5-1")) base += 3
    base = base.coerceIn(40, 65)
    return base to (base + 5).coerceAtMost(70)
}

private fun generatePredictionText(
    home: Team,
    away: Team,
    homeFormation: String,
    awayFormation: String,
): String {
    return when {
        home.rank < away.rank - 10 -> "${home.name}排名优势明显，有望掌控比赛主动权，通过中场组织创造更多得分机会。"
        away.rank < home.rank - 10 -> "${away.name}虽客场作战但实力更强，预计能通过控球和压迫限制主队发挥。"
        kotlin.math.abs(home.rank - away.rank) <= 5 -> "双方实力接近，控球率可能交替领先，比赛走势取决于临场发挥和关键球员表现。"
        else -> "比赛节奏预计较为胶着，双方都会尝试控制中场，谁能在攻防转换中更高效谁就能占据上风。"
    }
}
