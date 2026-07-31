package com.fifaglass.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.PredictionEngine
import com.fifaglass.app.rating.PredictionOutput
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.GlassTheme
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.glowBorder

@Composable
fun AIAnalystScreen(
    home: Team,
    away: Team,
    match: MatchInfo?,
    onBack: () -> Unit,
) {
    val theme = LocalGlassTheme.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("赛前分析", "实时洞察", "赛后报告")

    val prediction = remember(home.idTeam, away.idTeam) {
        try {
            PredictionEngine.predictPreMatchFull(home, away, true)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aurora.primary(isDark = false))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AIAnalystHeader(onBack = onBack, theme = theme)
            }
            item {
                AIAnalystTabBar(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    theme = theme,
                )
            }
            when (selectedTab) {
                0 -> preMatchAnalysis(prediction, home, away, theme)
                1 -> liveInsights(match, home, away, theme)
                2 -> postMatchReport(match, home, away, theme)
            }
        }
    }
}

@Composable
private fun AIAnalystHeader(onBack: () -> Unit, theme: GlassTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Aurora.warm())
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
                    "AI 比赛分析师",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "智能预测 · 深度解析 · 实时洞察",
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
                Text("🤖", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun AIAnalystTabBar(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    theme: GlassTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedTab
            val animatedWeight by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 1f,
                animationSpec = tween(300),
                label = "tab_weight_$index",
            )
            Box(
                modifier = Modifier
                    .weight(animatedWeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Aurora.primary(isDark = false)
                        else SolidColor(Color.Transparent)
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else theme.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

private fun LazyListScope.preMatchAnalysis(
    prediction: PredictionOutput?,
    home: Team,
    away: Team,
    theme: GlassTheme,
) {
    if (prediction == null) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "AI 预测引擎暂时不可用，请稍后再试",
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        return
    }

    item {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = GlassColors.accentBlue, intensity = 0.3f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("比赛预测摘要", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("AI 预测", color = GlassColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(home.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(home.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(20.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Aurora.primary(isDark = false))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        prediction.likelyScore,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(away.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(away.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "最可能比分 · 概率 ${(prediction.likelyScoreProbability * 100).toInt()}%",
                color = theme.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            Text("胜率预测", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            WinProbabilityBars(prediction, home, away)

            Spacer(Modifier.height(16.dp))
            Text("信心指数", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ConfidenceBar(prediction.confidence, theme)
        }
    }

    item {
        Text("战术分析", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("基于 xG 模型的深度战术解读", color = theme.textSecondary, fontSize = 12.sp)
    }

    val tacticalItems = generateTacticalAnalysis(prediction, home, away)
    items(tacticalItems.size) { index ->
        TacticalAnalysisItem(tacticalItems[index], theme)
    }

    item {
        Spacer(Modifier.height(4.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("关键因素", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            prediction.factors.forEachIndexed { idx, factor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Aurora.primary(isDark = false)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(factor, color = theme.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }

    item {
        val upsetPct = (prediction.upsetProbability * 100).toInt()
        val riskColor = when {
            upsetPct >= 40 -> GlassColors.down
            upsetPct >= 20 -> GlassColors.accentGold
            else -> GlassColors.up
        }
        val riskText = when {
            upsetPct >= 40 -> "高风险比赛"
            upsetPct >= 20 -> "中等风险"
            else -> "低风险比赛"
        }
        val riskDesc = when {
            upsetPct >= 40 -> "弱队有较大可能爆冷，比赛结果难以预测。建议谨慎参考预测结果。"
            upsetPct >= 20 -> "存在一定冷门可能性，排名较低的一方仍有翻盘机会。"
            else -> "比赛结果较可预测，强队大概率正常发挥。"
        }
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = riskColor, intensity = 0.35f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ 风险评估", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(riskColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(riskText, color = riskColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("冷门概率", color = theme.textSecondary, fontSize = 13.sp)
                Text("$upsetPct%", color = riskColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(riskColor.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(prediction.upsetProbability.toFloat())
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(riskColor)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(riskDesc, color = theme.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }

    item {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = GlassColors.accentViolet, intensity = 0.3f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 AI 推荐", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.accentViolet.copy(alpha = 0.1f))
                    .padding(14.dp)
            ) {
                Text(
                    prediction.recommendation,
                    color = theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun WinProbabilityBars(prediction: PredictionOutput, home: Team, away: Team) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        ProbabilityColumn(home.code, prediction.pHome, GlassColors.accentBlue)
        ProbabilityColumn("平", prediction.pDraw, GlassColors.accentGold)
        ProbabilityColumn(away.code, prediction.pAway, GlassColors.accentPink)
    }
}

@Composable
private fun ProbabilityColumn(label: String, value: Double, color: Color) {
    val animatedHeight by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(800),
        label = "prob_$label",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.height(110.dp),
    ) {
        Text(
            "${(value * 100).toInt()}%",
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height((80 * animatedHeight).coerceAtLeast(2f).dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.5f))
                    )
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = GlassColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConfidenceBar(confidence: Int, theme: GlassTheme) {
    val animatedValue by animateFloatAsState(
        targetValue = confidence / 100f,
        animationSpec = tween(800),
        label = "confidence",
    )
    val confColor = when {
        confidence >= 75 -> GlassColors.up
        confidence >= 50 -> GlassColors.accentGold
        else -> GlassColors.down
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("模型信心", color = theme.textSecondary, fontSize = 12.sp)
            Text("$confidence%", color = confColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(confColor.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedValue)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(confColor)
            )
        }
    }
}

private fun generateTacticalAnalysis(prediction: PredictionOutput, home: Team, away: Team): List<TacticalItem> {
    val items = mutableListOf<TacticalItem>()
    val xgH = prediction.xgHome
    val xgA = prediction.xgAway

    items.add(
        TacticalItem(
            icon = "⚔️",
            title = "主场进攻效率",
            content = when {
                xgH >= 1.5 -> "${home.name}主场进攻火力强劲，预期进球 $xgH 远超平均水平，大概率能制造多次得分机会。"
                xgH >= 1.0 -> "${home.name}主场进攻效率 $xgH，具备一定威胁，有望攻破对方防线。"
                else -> "${home.name}主场进攻偏弱，预期进球仅 $xgH，需要依靠定位球或反击寻找机会。"
            },
            color = GlassColors.accentBlue,
        )
    )

    items.add(
        TacticalItem(
            icon = "🛡️",
            title = "客队反击能力",
            content = when {
                xgA >= 1.5 -> "${away.name}客场反击能力出色，预期进球 $xgA，即使客场作战也极具威胁。"
                xgA >= 1.0 -> "${away.name}客场进攻效率 $xgA，有一定得分能力，不能掉以轻心。"
                else -> "${away.name}客场进攻乏力，预期进球仅 $xgA，大概率以防守反击为主。"
            },
            color = GlassColors.accentPink,
        )
    )

    items.add(
        TacticalItem(
            icon = "📊",
            title = "攻防对比",
            content = when {
                xgH - xgA > 0.8 -> "${home.name}在预期进球方面占据明显优势（差值 ${(xgH - xgA).let { "%.2f".format(it) }}），有望掌控比赛节奏。"
                xgA - xgH > 0.8 -> "${away.name}在预期进球方面反客为主（差值 ${(xgA - xgH).let { "%.2f".format(it) }}），主场方面需要警惕。"
                else -> "双方预期进球接近（${"%.2f".format(xgH)} vs ${"%.2f".format(xgA)}），比赛可能势均力敌。"
            },
            color = GlassColors.accentGold,
        )
    )

    items.add(
        TacticalItem(
            icon = "🏟️",
            title = "主场优势",
            content = if (home.rank < away.rank) {
                "${home.name}（世界排名第${home.rank}）排名高于${away.name}（第${away.rank}），加上主场之利，胜算较大。"
            } else {
                "${away.name}（世界排名第${away.rank}）排名高于${home.name}（第${home.rank}），但主场优势可能缩小差距。"
            },
            color = GlassColors.accentMint,
        )
    )

    items.add(
        TacticalItem(
            icon = "📈",
            title = "比分预测",
            content = "最可能比分为 ${prediction.likelyScore}，概率 ${(prediction.likelyScoreProbability * 100).toInt()}%。" +
                if (prediction.pHome > prediction.pAway) {
                    "${home.name}胜率更高，达 ${(prediction.pHome * 100).toInt()}%。"
                } else if (prediction.pAway > prediction.pHome) {
                    "${away.name}胜率更高，达 ${(prediction.pAway * 100).toInt()}%。"
                } else {
                    "双方胜率接近。"
                },
            color = GlassColors.accentViolet,
        )
    )

    return items
}

private data class TacticalItem(
    val icon: String,
    val title: String,
    val content: String,
    val color: Color,
)

@Composable
private fun TacticalAnalysisItem(item: TacticalItem, theme: GlassTheme) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(item.content, color = theme.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

private fun LazyListScope.liveInsights(
    match: MatchInfo?,
    home: Team,
    away: Team,
    theme: GlassTheme,
) {
    if (match == null || !match.isLive) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏳", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (match?.isScheduled == true) "比赛尚未开始" else "暂无实时比赛数据",
                        color = theme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (match?.isScheduled == true) "请查看赛前分析获取预测信息" else "比赛开始后将显示实时洞察",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        return
    }

    val homeScore = match.homeScore ?: 0
    val awayScore = match.awayScore ?: 0
    val minute = parseMinute(match.matchTime)

    item {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = GlassColors.up, intensity = 0.3f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("实时比分", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "pulse_alpha",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GlassColors.up.copy(alpha = pulseAlpha))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = GlassColors.up, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(home.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(home.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(24.dp))
                Row {
                    Text(homeScore.toString(), color = theme.textPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(" : ", color = theme.textTertiary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(awayScore.toString(), color = theme.textPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(away.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(away.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                match.matchTime,
                color = GlassColors.accentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }

    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("动量分析", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val homeMomentum = calculateMomentum(homeScore, awayScore, minute, home.rank, away.rank)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(home.name, color = GlassColors.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(away.name, color = GlassColors.accentPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GlassColors.accentPink.copy(alpha = 0.3f))
            ) {
                val animatedMomentum by animateFloatAsState(
                    targetValue = homeMomentum,
                    animationSpec = tween(600),
                    label = "momentum",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedMomentum)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(GlassColors.accentBlue, GlassColors.accentTeal)
                            )
                        )
                )
            }
            Spacer(Modifier.height(6.dp))
            val homePct = (homeMomentum * 100).toInt()
            Text(
                "主队动量 $homePct% · 客队动量 ${100 - homePct}%",
                color = theme.textSecondary,
                fontSize = 11.sp,
            )
        }
    }

    item {
        Text("实时洞察", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("AI 实时分析比赛走势", color = theme.textSecondary, fontSize = 12.sp)
    }

    val insights = generateLiveInsights(homeScore, awayScore, minute, home, away)
    items(insights.size) { index ->
        LiveInsightItem(insights[index], theme)
    }
}

private data class LiveInsight(
    val icon: String,
    val title: String,
    val content: String,
    val color: Color,
)

@Composable
private fun LiveInsightItem(insight: LiveInsight, theme: GlassTheme) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(insight.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(insight.icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(insight.title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(insight.content, color = theme.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

private fun generateLiveInsights(
    homeScore: Int,
    awayScore: Int,
    minute: Int,
    home: Team,
    away: Team,
): List<LiveInsight> {
    val insights = mutableListOf<LiveInsight>()
    val scoreDiff = homeScore - awayScore

    insights.add(
        LiveInsight(
            icon = "🔥",
            title = "比赛态势",
            content = when {
                minute < 15 -> "比赛刚刚开始，双方还在互相试探，节奏较为谨慎。"
                minute < 45 -> "比赛进入中段，双方逐渐加快节奏，攻防转换频繁。"
                minute in 45..60 -> "下半场刚开始，双方可能做出战术调整和换人。"
                minute in 60..75 -> "比赛进入关键阶段，体能下降明显，失误可能增多。"
                minute > 75 -> "比赛进入白热化阶段，双方都在寻找制胜球，每一分钟都至关重要。"
                else -> "比赛正在进行中，双方激烈对抗。"
            },
            color = GlassColors.accentGold,
        )
    )

    insights.add(
        LiveInsight(
            icon = "⚽",
            title = "比分分析",
            content = when {
                scoreDiff >= 2 -> "${home.name}领先${scoreDiff}球，优势明显。${away.name}需要大幅调整战术才有翻盘希望。"
                scoreDiff == 1 -> "${home.name}一球领先，优势不大。${away.name}仍有充足时间扳平比分。"
                scoreDiff == 0 -> "双方战平，比赛悬念十足。任何一方进球都可能改变全局。"
                scoreDiff == -1 -> "${away.name}一球领先，${home.name}主场落后面临压力，需要加强进攻。"
                else -> "${away.name}领先${-scoreDiff}球，${home.name}主场陷入困境，急需调整。"
            },
            color = if (scoreDiff >= 0) GlassColors.accentBlue else GlassColors.accentPink,
        )
    )

    if (minute > 70 && kotlin.math.abs(scoreDiff) <= 1) {
        insights.add(
            LiveInsight(
                icon = "⚡",
                title = "关键时刻",
                content = "比赛进入最后阶段且比分接近，双方都在寻找制胜球。这个阶段进球概率最高，建议密切关注。",
                color = GlassColors.accentViolet,
            )
        )
    }

    if (homeScore + awayScore >= 3) {
        insights.add(
            LiveInsight(
                icon = "🎯",
                title = "进球趋势",
                content = "本场比赛已产生${homeScore + awayScore}粒进球，进攻效率很高。双方防线都出现了问题，后续可能还有进球。",
                color = GlassColors.accentMint,
            )
        )
    }

    if (homeScore + awayScore == 0 && minute > 30) {
        insights.add(
            LiveInsight(
                icon = "🛡️",
                title = "防守对决",
                content = "比赛进行到第${minute}分钟仍未进球，双方防守严密。可能需要定位球或个人能力打破僵局。",
                color = GlassColors.accentTeal,
            )
        )
    }

    insights.add(
        LiveInsight(
            icon = "📊",
            title = "AI 预测更新",
            content = when {
                scoreDiff >= 2 && minute > 60 -> "${home.name}胜率大幅提升至85%以上，比赛基本失去悬念。"
                scoreDiff == 1 && minute > 75 -> "${home.name}有望保住胜果，胜率约70%。但最后阶段仍有变数。"
                scoreDiff == 0 && minute > 75 -> "平局概率最高，约45%。任何一方进球都可能直接带走比赛。"
                scoreDiff <= -2 && minute > 60 -> "${away.name}胜率大幅提升，${home.name}翻盘希望渺茫。"
                else -> "比赛走势仍在预期范围内，胜负悬念保留。"
            },
            color = GlassColors.accentIndigo,
        )
    )

    return insights
}

private fun LazyListScope.postMatchReport(
    match: MatchInfo?,
    home: Team,
    away: Team,
    theme: GlassTheme,
) {
    if (match == null || !match.isFinished) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "比赛尚未结束",
                        color = theme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "比赛结束后将生成完整赛后报告",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        return
    }

    val homeScore = match.homeScore ?: 0
    val awayScore = match.awayScore ?: 0
    val totalGoals = homeScore + awayScore
    val winner = when {
        homeScore > awayScore -> home
        awayScore > homeScore -> away
        else -> null
    }

    item {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = GlassColors.accentGold, intensity = 0.3f)
        ) {
            Text("最终比分", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(home.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(home.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (winner == home) {
                        Spacer(Modifier.height(4.dp))
                        Text("🏆 胜", color = GlassColors.accentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Row {
                    Text(homeScore.toString(), color = theme.textPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text(" : ", color = theme.textTertiary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(awayScore.toString(), color = theme.textPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(away.code, color = theme.textSecondary, fontSize = 12.sp)
                    Text(away.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (winner == away) {
                        Spacer(Modifier.height(4.dp))
                        Text("🏆 胜", color = GlassColors.accentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (winner != null) "${winner.name} 获胜" else "双方战平",
                color = GlassColors.accentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }

    item {
        val bestPlayerTeam = winner ?: if (home.rank <= away.rank) home else away
        val bestPlayerName = generateBestPlayerName(bestPlayerTeam)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐ 最佳球员", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(GlassColors.accentGold, GlassColors.accentGold.copy(alpha = 0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆", fontSize = 28.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(bestPlayerName, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${bestPlayerTeam.name} · ${bestPlayerTeam.code}", color = theme.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("全场最佳 · 关键贡献", color = GlassColors.accentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("🔄 比赛转折点", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val turningPoint = generateTurningPoint(homeScore, awayScore, home, away)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.08f))
                    .padding(14.dp)
            ) {
                Text(turningPoint, color = theme.textPrimary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }

    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("📊 统计亮点", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val stats = generateStatsHighlights(homeScore, awayScore, totalGoals, home, away)
            stats.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stat.first, color = theme.textSecondary, fontSize = 13.sp)
                    Text(stat.second, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    item {
        val rating = calculateMatchRating(totalGoals, kotlin.math.abs(homeScore - awayScore))
        val ratingColor = when (rating.first()) {
            'A' -> GlassColors.up
            'B' -> GlassColors.accentMint
            'C' -> GlassColors.accentGold
            'D' -> GlassColors.accentCoral
            else -> GlassColors.down
        }
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(glow = ratingColor, intensity = 0.35f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎬 比赛评分", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ratingColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(rating, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                generateRatingComment(rating, totalGoals, homeScore, awayScore, home, away),
                color = theme.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

private fun generateBestPlayerName(team: Team): String {
    val names = mapOf(
        "BRA" to "维尼修斯", "ARG" to "梅西", "FRA" to "姆巴佩", "ESP" to "亚马尔",
        "ENG" to "凯恩", "GER" to "穆西亚拉", "POR" to "C罗", "NED" to "德佩",
        "BEL" to "德布劳内", "ITA" to "巴雷拉", "CRO" to "莫德里奇",
    )
    return names[team.code] ?: "${team.name}核心球员"
}

private fun generateTurningPoint(homeScore: Int, awayScore: Int, home: Team, away: Team): String {
    val diff = homeScore - awayScore
    return when {
        kotlin.math.abs(diff) >= 3 -> "比分差距悬殊，${if (diff > 0) home.name else away.name}从开场就占据绝对优势，对手全程被动。比赛的转折点在于开场阶段的早期进球，彻底打乱了落后方的战术部署。"
        kotlin.math.abs(diff) == 2 -> "${if (diff > 0) home.name else away.name}凭借两球优势获胜。第二粒进球是关键转折点，扩大了领先优势并打击了对手的士气。"
        kotlin.math.abs(diff) == 1 -> "比赛十分胶着，唯一进球成为决定性时刻。${if (diff > 0) home.name else away.name}抓住有限的机会完成致命一击，防守端也经受住了考验。"
        else -> "双方势均力敌，最终战平。比赛中双方都有制胜机会但未能把握，防守端的表现成为平局的关键因素。"
    }
}

private fun generateStatsHighlights(
    homeScore: Int,
    awayScore: Int,
    totalGoals: Int,
    home: Team,
    away: Team,
): List<Pair<String, String>> {
    return listOf(
        "总进球数" to "$totalGoals 球",
        "主队进球" to "$homeScore",
        "客队进球" to "$awayScore",
        "比分差距" to "${kotlin.math.abs(homeScore - awayScore)} 球",
        "比赛性质" to if (totalGoals >= 4) "高进球比赛" else if (totalGoals <= 1) "低进球比赛" else "常规比赛",
        "精彩程度" to if (totalGoals >= 3) "精彩" else if (totalGoals >= 2) "不错" else "一般",
    )
}

private fun calculateMatchRating(totalGoals: Int, scoreDiff: Int): String {
    return when {
        totalGoals >= 4 -> "A"
        totalGoals >= 3 || (totalGoals >= 2 && scoreDiff <= 1) -> "B"
        totalGoals >= 2 -> "C"
        totalGoals >= 1 -> "D"
        else -> "F"
    }
}

private fun generateRatingComment(
    rating: String,
    totalGoals: Int,
    homeScore: Int,
    awayScore: Int,
    home: Team,
    away: Team,
): String {
    return when (rating) {
        "A" -> "精彩的比赛！全场贡献了$totalGoals 粒进球，进攻端表现亮眼，观众大饱眼福。"
        "B" -> "不错的比赛，共产生$totalGoals 粒进球，双方展现了较好的竞技水平。"
        "C" -> "中规中矩的比赛，$totalGoals 粒进球，有一定的看点但整体节奏偏慢。"
        "D" -> "比赛较为沉闷，仅产生$totalGoals 粒进球，进攻端缺乏亮点。"
        else -> "沉闷的比赛，双方0-0互交白卷，防守严密但缺乏进攻火花。"
    }
}

private fun parseMinute(matchTime: String): Int {
    return matchTime.filter { it.isDigit() }.toIntOrNull() ?: 0
}

private fun calculateMomentum(
    homeScore: Int,
    awayScore: Int,
    minute: Int,
    homeRank: Int,
    awayRank: Int,
): Float {
    var momentum = 0.5f
    momentum += (homeScore - awayScore) * 0.08f
    if (homeRank < awayRank) momentum += 0.05f
    if (minute in 45..75) momentum += 0.03f
    return momentum.coerceIn(0.15f, 0.85f)
}
