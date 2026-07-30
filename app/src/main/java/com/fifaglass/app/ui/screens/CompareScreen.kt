package com.fifaglass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.rating.PredictionEngine
import com.fifaglass.app.rating.PredictionOutput
import com.fifaglass.app.rating.RiskLevel
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.RadarChart
import com.fifaglass.app.ui.blueMintBrush
import com.fifaglass.app.ui.pinkGoldBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 对比页：选两支球队 → 六维雷达对比 + 数据对比 +
 * 由独立预测系统（PredictionSystem）输出的完整比赛预测。
 */
@Composable
fun CompareScreen(teams: List<Team>?, error: String?) {
    var teamA by remember { mutableStateOf<Team?>(null) }
    var teamB by remember { mutableStateOf<Team?>(null) }
    var picking by remember { mutableStateOf(0) }
    var trueHome by remember { mutableStateOf(true) }

    var prediction by remember { mutableStateOf<PredictionOutput?>(null) }
    var predicting by remember { mutableStateOf(false) }
    var predictError by remember { mutableStateOf<String?>(null) }

    // 两队选定后自动运行完整预测系统（网络拉取近期比赛 → 后台线程）
    LaunchedEffect(teamA, teamB, trueHome) {
        val a = teamA; val b = teamB
        prediction = null
        predictError = null
        if (a == null || b == null) return@LaunchedEffect
        predicting = true
        try {
            prediction = withContext(Dispatchers.IO) {
                PredictionEngine.predictPreMatchFull(a, b, trueHome)
            }
        } catch (e: Exception) {
            predictError = e.message ?: "预测失败"
        } finally {
            predicting = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "球队对比 · 预测",
            color = GlassColors.textPrimary,
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            "选两支球队：能力对比 + 万行预测系统完整输出",
            color = GlassColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            teams == null -> LoadingBox()
            else -> {
                Row(Modifier.fillMaxWidth()) {
                    TeamSlot(
                        team = teamA,
                        hint = "球队 A（默认主场）",
                        accent = GlassColors.accentBlue,
                        modifier = Modifier.weight(1f)
                    ) { picking = 1 }
                    Spacer(Modifier.width(10.dp))
                    TeamSlot(
                        team = teamB,
                        hint = "球队 B",
                        accent = GlassColors.accentPink,
                        modifier = Modifier.weight(1f)
                    ) { picking = 2 }
                }
                Spacer(Modifier.height(10.dp))

                // 场地切换
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(4.dp)
                ) {
                    listOf(true to "真实主场", false to "中立场").forEach { (v, label) ->
                        val selected = trueHome == v
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable { trueHome = v }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) GlassColors.accentMint
                                else GlassColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (picking != 0) {
                    TeamPicker(
                        teams = teams,
                        exclude = if (picking == 1) teamB?.code else teamA?.code
                    ) { chosen ->
                        if (picking == 1) teamA = chosen else teamB = chosen
                        picking = 0
                    }
                } else if (teamA != null && teamB != null) {
                    // 六维能力对比
                    CompareRadarSection(teamA!!, teamB!!)
                    Spacer(Modifier.height(12.dp))

                    // 数据对比
                    CompareDataSection(teamA!!, teamB!!)
                    Spacer(Modifier.height(12.dp))

                    // 完整预测系统输出
                    when {
                        predicting -> GlassCard(Modifier.fillMaxWidth()) {
                            Text(
                                "预测系统运行中：拉取近期比赛 · 构建画像 · 泊松矩阵 · 蒙特卡洛…",
                                color = GlassColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        predictError != null -> GlassCard(Modifier.fillMaxWidth()) {
                            Text("预测失败", color = GlassColors.down, fontWeight = FontWeight.Bold)
                            Text(predictError!!, color = GlassColors.textSecondary, fontSize = 12.sp)
                        }
                        prediction != null -> FullPredictionSection(teamA!!, teamB!!, prediction!!)
                    }
                } else {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "点击上方槽位，选择两支球队。\n将展示六维能力对比，并由独立预测系统输出胜率、比分、大小球、BTTS、角球、半全场、蒙特卡洛模拟等完整预测。",
                            color = GlassColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 六维雷达对比
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompareRadarSection(a: Team, b: Team) {
    val dimsA = remember(a, b) { Evaluator.evaluate(a, FullPool.teams ?: listOf(a, b)).dims }
    val dimsB = remember(a, b) { Evaluator.evaluate(b, FullPool.teams ?: listOf(a, b)).dims }

    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "六维能力对比",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Row {
            LegendDot(GlassColors.accentBlue, a.code)
            Spacer(Modifier.width(12.dp))
            LegendDot(GlassColors.accentPink, b.code)
        }
        Spacer(Modifier.height(8.dp))
        RadarChart(
            dims = dimsA,
            dimsB = dimsB,
            modifier = Modifier.fillMaxWidth().height(280.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 数据对比
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompareDataSection(a: Team, b: Team) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "数据对比",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        CompareRow("世界排名", "#${a.rank}", "#${b.rank}", a.rank <= b.rank)
        CompareRow(
            "FIFA 积分",
            "%.1f".format(a.points),
            "%.1f".format(b.points),
            a.points >= b.points
        )
        CompareRow(
            "名次变化",
            if (a.rankChange >= 0) "+${a.rankChange}" else "${a.rankChange}",
            if (b.rankChange >= 0) "+${b.rankChange}" else "${b.rankChange}",
            a.rankChange >= b.rankChange
        )
        CompareRow("所属足联", a.confederation, b.confederation, null)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 完整预测系统输出
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullPredictionSection(a: Team, b: Team, p: PredictionOutput) {
    // 胜负预测
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "胜负预测（万行预测系统）",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        ProbabilityBarTriple(p.pHome, p.pDraw, p.pAway)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${a.code} ${(p.pHome * 100).toInt()}%",
                color = GlassColors.accentMint,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "平 ${(p.pDraw * 100).toInt()}%",
                color = GlassColors.textSecondary,
                fontSize = 13.sp
            )
            Text(
                "${(p.pAway * 100).toInt()}% ${b.code}",
                color = GlassColors.accentPink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("最可能比分", color = GlassColors.textSecondary, fontSize = 12.sp)
                Text(
                    p.likelyScore,
                    color = GlassColors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "概率 ${(p.likelyScoreProbability * 100).toInt()}%",
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("信心指数", color = GlassColors.textSecondary, fontSize = 12.sp)
                Text(
                    "${p.confidence}",
                    color = when {
                        p.confidence >= 70 -> GlassColors.up
                        p.confidence >= 45 -> GlassColors.accentGold
                        else -> GlassColors.textSecondary
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "风险：" + when (p.riskLevel) {
                        RiskLevel.LOW -> "低"
                        RiskLevel.MEDIUM -> "中"
                        RiskLevel.HIGH -> "高"
                        RiskLevel.EXTREME -> "极高"
                    },
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "期望进球（xG） ${"%.2f".format(p.xgHome)} : ${"%.2f".format(p.xgAway)} · 冷门概率 ${(p.upsetProbability * 100).toInt()}%",
            color = GlassColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(12.dp))

    // Top 5 比分
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "最可能比分 Top 5",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        p.topScores.forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.score,
                    color = GlassColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(s.probability.toFloat().coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(blueMintBrush)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(s.probability * 100).toInt()}%",
                    color = GlassColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    // 衍生市场
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "衍生市场",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        MarketRow("大 2.5 球", "${(p.overProbabilities.over25 * 100).toInt()}%")
        MarketRow("小 2.5 球", "${(p.overProbabilities.under25 * 100).toInt()}%")
        MarketRow("双方都进球", "${(p.bttsProbability * 100).toInt()}%")
        MarketRow("预期总角球", "%.1f".format(p.cornerPrediction.expectedTotal))
        MarketRow("角球大 9.5", "${(p.cornerPrediction.over95 * 100).toInt()}%")
        MarketRow("预期黄牌总数", "%.1f".format(p.cardPrediction.expectedTotalYellows))
        MarketRow("出现红牌概率", "${(p.cardPrediction.anyRedCard * 100).toInt()}%")
        val htft = p.halfTimeFullTime.firstOrNull()
        if (htft != null) {
            MarketRow("最可能半全场", "${htft.htft}（${(htft.probability * 100).toInt()}%）")
        }
        MarketRow("首球期望时间", "第 ${p.firstGoalTimePrediction.expectedMinute.toInt()} 分钟")
        MarketRow("0:0 闷平概率", "${(p.firstGoalTimePrediction.noGoalProbability * 100).toInt()}%")
    }
    Spacer(Modifier.height(12.dp))

    // 蒙特卡洛
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "蒙特卡洛模拟（${p.monteCarlo.simulations} 次）",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        MarketRow("主胜", "${p.monteCarlo.homeWins} 次（${p.monteCarlo.homeWins * 100 / p.monteCarlo.simulations}%）")
        MarketRow("平局", "${p.monteCarlo.draws} 次（${p.monteCarlo.draws * 100 / p.monteCarlo.simulations}%）")
        MarketRow("客胜", "${p.monteCarlo.awayWins} 次（${p.monteCarlo.awayWins * 100 / p.monteCarlo.simulations}%）")
        MarketRow("场均总进球", "%.2f".format(p.monteCarlo.avgTotalGoals))
        MarketRow("最大主胜", p.monteCarlo.biggestHomeWin)
        MarketRow("最大客胜", p.monteCarlo.biggestAwayWin)
    }
    Spacer(Modifier.height(12.dp))

    // 情景分析
    if (p.scenarios.isNotEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "情景分析",
                color = GlassColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            p.scenarios.take(4).forEach { s ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.name,
                            color = GlassColors.accentGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(s.probability * 100).toInt()}%",
                            color = GlassColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        s.description,
                        color = GlassColors.textPrimary,
                        fontSize = 12.sp
                    )
                    Text(
                        s.impact,
                        color = GlassColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // 决策依据
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "决策依据",
            color = GlassColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        p.factors.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", color = GlassColors.accentMint, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    f,
                    color = GlassColors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            p.recommendation,
            color = GlassColors.accentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MarketRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = GlassColors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = GlassColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 公共组件
// ─────────────────────────────────────────────────────────────────────────────

/** 三结果概率条（对比页 / 比赛详情页共用） */
@Composable
fun ProbabilityBarTriple(pHome: Double, pDraw: Double, pAway: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (pHome > 0) {
            Box(
                Modifier
                    .weight(pHome.toFloat().coerceAtLeast(0.01f))
                    .fillMaxSize()
                    .background(blueMintBrush)
            )
        }
        if (pDraw > 0) {
            Box(
                Modifier
                    .weight(pDraw.toFloat().coerceAtLeast(0.01f))
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
        if (pAway > 0) {
            Box(
                Modifier
                    .weight(pAway.toFloat().coerceAtLeast(0.01f))
                    .fillMaxSize()
                    .background(pinkGoldBrush)
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = GlassColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun CompareRow(label: String, va: String, vb: String, aBetter: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            va,
            color = if (aBetter == true) GlassColors.accentMint else GlassColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (aBetter == true) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
        Text(
            label,
            color = GlassColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(90.dp),
            textAlign = TextAlign.Center
        )
        Text(
            vb,
            color = if (aBetter == false) GlassColors.accentPink else GlassColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (aBetter == false) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 由 AppRoot 注入的完整排名池，供对比评测使用 */
object FullPool {
    var teams: List<Team>? = null
}
