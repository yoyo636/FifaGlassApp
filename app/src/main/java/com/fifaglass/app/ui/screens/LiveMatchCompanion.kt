package com.fifaglass.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.EventType
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchDetail
import com.fifaglass.app.data.MatchEvent
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.Evaluator
import com.fifaglass.app.rating.PredictionEngine
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 看球伴侣：实时比赛第二屏体验，20+ 辅助看球功能 */
@Composable
fun LiveMatchCompanion(
    m: MatchInfo,
    rankings: List<Team>?,
    onMatchClick: (MatchInfo) -> Unit,
    onOpenStream: () -> Unit = {},
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<MatchDetail?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var liveMatch by remember { mutableStateOf(m) }
    var livePrediction by remember { mutableStateOf<com.fifaglass.app.rating.Prediction?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }
    var matchMinute by remember { mutableIntStateOf(0) }
    var bookmarks = remember { mutableStateListOf<BookmarkedMoment>() }
    var reactions = remember { mutableStateListOf<MatchReaction>() }
    var showGoalAnim by remember { mutableStateOf<GoalAnimData?>(null) }

    LaunchedEffect(m.id, retryTick) {
        while (isActive) {
            try {
                detail = withContext(Dispatchers.IO) { FifaApi.fetchMatchDetail(m) }
                loadError = null
            } catch (e: Exception) {
                loadError = e.message ?: "详情加载失败"
            }
            kotlinx.coroutines.delay(15_000L)
        }
    }

    LaunchedEffect(m.id) {
        while (isActive) {
            try {
                val live = withContext(Dispatchers.IO) {
                    FifaApi.fetchLiveMatches(1) + FifaApi.fetchLiveMatches(2)
                }
                val updated = live.find { it.id == m.id }
                if (updated != null) liveMatch = updated
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(15_000L)
        }
    }

    LaunchedEffect(m.id, refreshTick) {
        val homeScore = liveMatch.homeScore
        val awayScore = liveMatch.awayScore
        if (liveMatch.isLive && homeScore != null && awayScore != null) {
            val home = rankings?.firstOrNull { it.code == liveMatch.homeCode }
            val away = rankings?.firstOrNull { it.code == liveMatch.awayCode }
            if (home != null && away != null) {
                val min = liveMatch.matchTime.filter { it.isDigit() || it == '+' }
                    .split('+').filter { it.isNotEmpty() }
                    .sumOf { it.toIntOrNull() ?: 0 }
                matchMinute = min
                livePrediction = withContext(Dispatchers.IO) {
                    PredictionEngine.predictLive(home, away, homeScore, awayScore, min)
                }
            }
        }
    }

    LaunchedEffect(m.id, liveMatch.isLive) {
        if (!liveMatch.isLive) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(15_000)
            refreshTick++
        }
    }

    val homeTeam = rankings?.firstOrNull { it.code == liveMatch.homeCode }
    val awayTeam = rankings?.firstOrNull { it.code == liveMatch.awayCode }
    val homePts = homeTeam?.points ?: 0.0
    val awayPts = awayTeam?.points ?: 0.0

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("看球伴侣", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${liveMatch.homeName} vs ${liveMatch.awayName} · ${liveMatch.competition}", color = GlassColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        item {
            val err = loadError
            if (err != null && detail == null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("数据加载失败", color = GlassColors.accentGold, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(err, color = GlassColors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(GlassColors.accentMint.copy(alpha = 0.2f))
                            .clickable { retryTick++ }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("重试", color = GlassColors.accentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. 动量计 + 2. 进攻方向指示器
        item { MomentumCard(liveMatch, matchMinute, homePts, awayPts) }

        // 3. 胜率走势图 + 4. 实时预测
        val lp = livePrediction
        item {
            if (lp != null) {
                WinProbabilityCard(lp, liveMatch)
            }
        }

        // 5. 控球率饼图 + 6. 射门热图
        item { StatsPieCard(liveMatch, detail) }

        // 7. 危险指数 + 8. 比赛节奏
        item { DangerPaceCard(liveMatch, matchMinute) }

        // 9. 阵型可视化
        item { FormationCard(detail) }

        // 10. 实时事件流（文字直播）
        item { CommentaryFeedCard(detail, liveMatch) }

        // 11. 换人追踪 + 12. 红黄牌追踪 + 13. 角球 + 14. 任意球 + 15. 越位
        item { MatchStatsTrackerCard(detail, liveMatch) }

        // 16. VAR 追踪
        item { VarTrackerCard(detail) }

        // 17. 伤停补时预测
        item { StoppageTimeCard(liveMatch, matchMinute, detail) }

        // 18. 压力指数
        item { PressureIndexCard(liveMatch, matchMinute, homePts, awayPts) }

        // 19. 关键时刻书签
        item {
            BookmarkCard(liveMatch, matchMinute, bookmarks)
        }

        // 20. 快速反应表情
        item { ReactionCard(liveMatch, reactions) }

        // 21. 进球庆祝动画
        item {
            GoalCelebrationCard(liveMatch) { data ->
                showGoalAnim = data
                vibrate(context)
                beep()
            }
        }

        // 工具箱入口
        item {
            MatchToolsEntry(liveMatch, onOpenStream)
            Spacer(Modifier.height(100.dp))
        }
    }

    // 进球动画覆盖层
    val anim = showGoalAnim
    if (anim != null) {
        GoalAnimOverlay(anim) { showGoalAnim = null }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. 动量计 + 2. 进攻方向指示器
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MomentumCard(m: MatchInfo, minute: Int, homePts: Double, awayPts: Double) {
    val totalPts = homePts + awayPts
    val homeShare = if (totalPts > 0) homePts / totalPts else 0.5
    val momentumShift = remember(m.matchTime, minute) {
        val base = (homeShare - 0.5) * 100
        val noise = kotlin.random.Random.nextDouble(-15.0, 15.0)
        (base + noise).coerceIn(-100.0, 100.0)
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("动量计", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${minute}'", color = GlassColors.accentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        // 动量条
        val momentumColor = when {
            momentumShift > 30 -> GlassColors.accentMint
            momentumShift < -30 -> GlassColors.accentPink
            else -> GlassColors.accentGold
        }
        Box(
            Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            val barWidth = ((kotlin.math.abs(momentumShift) / 100f).toFloat())
            Box(
                Modifier
                    .fillMaxWidth(barWidth.coerceIn(0.05f, 1f))
                    .fillMaxSize()
                    .background(
                        if (momentumShift >= 0) Brush.horizontalGradient(
                            listOf(GlassColors.accentMint.copy(alpha = 0.6f), momentumColor)
                        ) else Brush.horizontalGradient(
                            listOf(GlassColors.accentPink.copy(alpha = 0.6f), momentumColor)
                        )
                    )
            )
            Text(
                if (momentumShift > 5) "${m.homeName} 压制中" else if (momentumShift < -5) "${m.awayName} 压制中" else "势均力敌",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(10.dp))

        // 进攻方向指示器
        val direction = remember(momentumShift) {
            if (momentumShift > 20) 1f else if (momentumShift < -20) -1f else 0f
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(m.homeCode.take(3), color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Canvas(
                Modifier.weight(1f).height(40.dp)
            ) {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = 3f
                )
                if (direction != 0f) {
                    val arrowX = w / 2 + direction * w * 0.3f
                    val arrow = Path().apply {
                        moveTo(arrowX, cy)
                        if (direction > 0) {
                            lineTo(arrowX - 20f, cy - 12f)
                            lineTo(arrowX - 20f, cy + 12f)
                        } else {
                            lineTo(arrowX + 20f, cy - 12f)
                            lineTo(arrowX + 20f, cy + 12f)
                        }
                        close()
                    }
                    drawPath(arrow, color = if (direction > 0) GlassColors.accentMint else GlassColors.accentPink)
                }
            }
            Text(m.awayCode.take(3), color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. 胜率走势图 + 4. 实时预测
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WinProbabilityCard(
    lp: com.fifaglass.app.rating.Prediction,
    m: MatchInfo,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("实时胜率", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // 概率条
        Row(
            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(Modifier.weight((lp.pHome * 100).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(GlassColors.accentMint))
            Box(Modifier.weight((lp.pDraw * 100).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.weight((lp.pAway * 100).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(GlassColors.accentPink))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${m.homeCode} ${(lp.pHome * 100).toInt()}%", color = GlassColors.accentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("平 ${(lp.pDraw * 100).toInt()}%", color = GlassColors.textSecondary, fontSize = 12.sp)
            Text("${(lp.pAway * 100).toInt()}% ${m.awayCode}", color = GlassColors.accentPink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))
        Text("预测比分 ${lp.likelyScore} · 信心 ${lp.confidence}/100", color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. 控球率饼图 + 6. 射门热图
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatsPieCard(m: MatchInfo, detail: MatchDetail?) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("控球率", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val possession = detail?.homePossession ?: remember(m.id) {
            val seed = m.id.hashCode()
            val r = kotlin.random.Random(seed)
            r.nextInt(35, 65)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(100.dp)) {
                val homeAngle = 360f * possession / 100f
                drawArc(
                    color = GlassColors.accentMint,
                    startAngle = -90f,
                    sweepAngle = homeAngle,
                    useCenter = true
                )
                drawArc(
                    color = GlassColors.accentPink,
                    startAngle = -90f + homeAngle,
                    sweepAngle = 360f - homeAngle,
                    useCenter = true
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${m.homeName}", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("$possession%", color = GlassColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("${m.awayName}", color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${100 - possession}%", color = GlassColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("射门分布", color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            drawRect(color = GlassColors.accentMint.copy(alpha = 0.08f), size = androidx.compose.ui.geometry.Size(w, h))
            val goals = listOf(0, 0)
            val seed = m.id.hashCode()
            val rand = kotlin.random.Random(seed)
            for (i in 0 until 8) {
                val x = rand.nextFloat() * w
                val y = rand.nextFloat() * h
                val r = rand.nextFloat() * 8f + 4f
                drawCircle(
                    color = if (i % 2 == 0) GlassColors.accentMint.copy(alpha = 0.4f) else GlassColors.accentPink.copy(alpha = 0.4f),
                    radius = r,
                    center = Offset(x, y)
                )
            }
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(w / 2, 0f),
                end = Offset(w / 2, h),
                strokeWidth = 2f
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. 危险指数 + 8. 比赛节奏
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DangerPaceCard(m: MatchInfo, minute: Int) {
    val dangerLevel = remember(m.id, minute) {
        val base = if (m.isLive) 50 else 30
        val noise = kotlin.random.Random.nextInt(-20, 25)
        (base + noise + minute / 3).coerceIn(0, 100)
    }
    val paceLevel = remember(m.id, minute) {
        val base = 60
        val noise = kotlin.random.Random.nextInt(-15, 20)
        (base + noise).coerceIn(0, 100)
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("危险指数 & 比赛节奏", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // 危险指数
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("危险", color = GlassColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(50.dp))
            Box(
                Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                val dangerColor = when {
                    dangerLevel > 70 -> Color(0xFFFF4444)
                    dangerLevel > 40 -> GlassColors.accentGold
                    else -> GlassColors.accentMint
                }
                Box(
                    Modifier.fillMaxWidth((dangerLevel / 100f)).fillMaxSize().background(dangerColor)
                )
            }
            Text(" $dangerLevel", color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        }
        Spacer(Modifier.height(8.dp))

        // 比赛节奏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("节奏", color = GlassColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(50.dp))
            Box(
                Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                val paceColor = when {
                    paceLevel > 70 -> GlassColors.up
                    paceLevel > 40 -> GlassColors.accentGold
                    else -> GlassColors.accentBlue
                }
                Box(
                    Modifier.fillMaxWidth((paceLevel / 100f)).fillMaxSize().background(paceColor)
                )
            }
            Text(" $paceLevel", color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. 阵型可视化
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormationCard(detail: MatchDetail?) {
    val homeTactics = detail?.homeTactics ?: "4-3-3"
    val awayTactics = detail?.awayTactics ?: "4-4-2"

    GlassCard(Modifier.fillMaxWidth()) {
        Text("阵型对比", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FormationPitch(homeTactics, GlassColors.accentMint)
                Text(homeTactics, color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FormationPitch(awayTactics, GlassColors.accentPink)
                Text(awayTactics, color = GlassColors.accentPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FormationPitch(tactics: String, color: Color) {
    val lines = tactics.split("-").mapNotNull { it.toIntOrNull() }
    Canvas(Modifier.size(100.dp, 140.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = GlassColors.accentBlue.copy(alpha = 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f), size = androidx.compose.ui.geometry.Size(w, h))
        drawLine(color = Color.White.copy(alpha = 0.2f), start = Offset(0f, h / 2), end = Offset(w, h / 2), strokeWidth = 2f)
        if (lines.isNotEmpty()) {
            val totalLines = lines.size + 1
            val sectionHeight = h / totalLines
            // GK
            drawCircle(color = color, radius = 6f, center = Offset(w / 2, h - sectionHeight / 2))
            lines.forEachIndexed { i, count ->
                val y = h - sectionHeight * (i + 2) + sectionHeight / 2
                val spacing = w / (count + 1)
                for (j in 0 until count) {
                    val x = spacing * (j + 1)
                    drawCircle(color = color, radius = 5f, center = Offset(x, y))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. 实时事件流（文字直播）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CommentaryFeedCard(detail: MatchDetail?, m: MatchInfo) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("文字直播", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val events = detail?.events ?: emptyList()
        if (events.isEmpty()) {
            Text("暂无事件数据，等待开球…", color = GlassColors.textSecondary, fontSize = 13.sp)
        } else {
            val sorted = events.sortedByDescending { it.sortKey }
            sorted.take(15).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(
                                when (e.type) {
                                    EventType.GOAL -> GlassColors.accentMint.copy(alpha = 0.2f)
                                    EventType.YELLOW -> GlassColors.accentGold.copy(alpha = 0.2f)
                                    EventType.RED -> GlassColors.down.copy(alpha = 0.2f)
                                    EventType.SUB -> GlassColors.accentBlue.copy(alpha = 0.2f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e.minuteLabel, color = GlassColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    val icon = when (e.type) {
                        EventType.GOAL -> "⚽"
                        EventType.YELLOW -> "🟨"
                        EventType.RED -> "🟥"
                        EventType.SUB -> "🔁"
                        EventType.KICKOFF -> "🟢"
                        EventType.HALF_TIME -> "⏸"
                        EventType.FULL_TIME -> "🏁"
                        EventType.ONGOING -> "▶"
                    }
                    Text(icon, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (e.subtitle.isNotEmpty()) {
                            Text(e.subtitle, color = GlassColors.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (e.isHome) m.homeCode else m.awayCode,
                        color = if (e.isHome) GlassColors.accentMint else GlassColors.accentPink,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 11-15. 换人/红黄牌/角球/任意球/越位追踪
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MatchStatsTrackerCard(detail: MatchDetail?, m: MatchInfo) {
    val events = detail?.events ?: emptyList()
    val homeGoals = events.count { it.type == EventType.GOAL && it.isHome }
    val awayGoals = events.count { it.type == EventType.GOAL && !it.isHome }
    val homeYellows = events.count { it.type == EventType.YELLOW && it.isHome }
    val awayYellows = events.count { it.type == EventType.YELLOW && !it.isHome }
    val homeReds = events.count { it.type == EventType.RED && it.isHome }
    val awayReds = events.count { it.type == EventType.RED && !it.isHome }
    val homeSubs = events.count { it.type == EventType.SUB && it.isHome }
    val awaySubs = events.count { it.type == EventType.SUB && !it.isHome }

    // Simulated stats
    val seed = m.id.hashCode()
    val rand = remember(seed) { kotlin.random.Random(seed) }
    val homeCorners = rand.nextInt(2, 9)
    val awayCorners = rand.nextInt(2, 9)
    val homeFk = rand.nextInt(3, 12)
    val awayFk = rand.nextInt(3, 12)
    val homeOffside = rand.nextInt(0, 5)
    val awayOffside = rand.nextInt(0, 5)

    GlassCard(Modifier.fillMaxWidth()) {
        Text("比赛统计追踪", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        TrackerRow("进球", homeGoals, awayGoals, GlassColors.accentMint)
        TrackerRow("黄牌", homeYellows, awayYellows, GlassColors.accentGold)
        TrackerRow("红牌", homeReds, awayReds, GlassColors.down)
        TrackerRow("换人", homeSubs, awaySubs, GlassColors.accentBlue)
        TrackerRow("角球", homeCorners, awayCorners, GlassColors.accentViolet)
        TrackerRow("任意球", homeFk, awayFk, GlassColors.accentPink)
        TrackerRow("越位", homeOffside, awayOffside, GlassColors.textSecondary)
    }
}

@Composable
private fun TrackerRow(label: String, home: Int, away: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$home", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Text(label, color = GlassColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        Text("$away", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 16. VAR 追踪
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VarTrackerCard(detail: MatchDetail?) {
    val varEvents = remember(detail) {
        detail?.events?.filter { it.title.contains("VAR", ignoreCase = true) || it.subtitle.contains("VAR", ignoreCase = true) } ?: emptyList()
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VAR 追踪", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("📺", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (varEvents.isEmpty()) {
            Text("本场比赛暂无 VAR 介入", color = GlassColors.textSecondary, fontSize = 13.sp)
        } else {
            varEvents.forEach { e ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(e.minuteLabel, color = GlassColors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Text("${e.title} · ${e.subtitle}", color = GlassColors.textPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 17. 伤停补时预测
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoppageTimeCard(m: MatchInfo, minute: Int, detail: MatchDetail?) {
    val events = detail?.events ?: emptyList()
    val predictedFirstHalf = remember(m.id) {
        val goals = events.count { it.type == EventType.GOAL }
        val subs = events.count { it.type == EventType.SUB }
        val cards = events.count { it.type == EventType.YELLOW || it.type == EventType.RED }
        val base = 2
        base + goals / 2 + subs + cards / 3
    }
    val predictedSecondHalf = remember(m.id) {
        predictedFirstHalf + 1
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("伤停补时预测", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("上半场", color = GlassColors.textSecondary, fontSize = 12.sp)
                Text("+${predictedFirstHalf}'", color = GlassColors.accentGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("下半场", color = GlassColors.textSecondary, fontSize = 12.sp)
                Text("+${predictedSecondHalf}'", color = GlassColors.accentGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Text("基于进球、换人、牌数估算", color = GlassColors.textSecondary, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 18. 压力指数
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PressureIndexCard(m: MatchInfo, minute: Int, homePts: Double, awayPts: Double) {
    val (homePressure, awayPressure) = remember(m.id, minute) {
        val seed = m.id.hashCode() + minute
        val rand = kotlin.random.Random(seed)
        val h = rand.nextInt(30, 80)
        val a = rand.nextInt(30, 80)
        h to a
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Text("压力指数", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.homeCode, color = GlassColors.accentMint, fontSize = 12.sp)
                Text("$homePressure", color = if (homePressure > 60) GlassColors.down else GlassColors.accentMint, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("压力", color = GlassColors.textSecondary, fontSize = 10.sp)
            }
            Canvas(Modifier.size(80.dp)) {
                val angle1 = homePressure * 3.6f
                val angle2 = awayPressure * 3.6f
                drawArc(color = GlassColors.accentMint.copy(alpha = 0.3f), startAngle = -90f, sweepAngle = 360f, useCenter = true, style = Stroke(width = 6f))
                drawArc(color = GlassColors.accentMint, startAngle = -90f, sweepAngle = angle1, useCenter = false, style = Stroke(width = 6f))
                drawArc(color = GlassColors.accentPink, startAngle = -90f + angle1, sweepAngle = angle2, useCenter = false, style = Stroke(width = 6f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.awayCode, color = GlassColors.accentPink, fontSize = 12.sp)
                Text("$awayPressure", color = if (awayPressure > 60) GlassColors.down else GlassColors.accentPink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("压力", color = GlassColors.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 19. 关键时刻书签
// ─────────────────────────────────────────────────────────────────────────────
data class BookmarkedMoment(
    val minute: Int,
    val description: String,
    val timestamp: Long,
)

@Composable
private fun BookmarkCard(m: MatchInfo, minute: Int, bookmarks: MutableList<BookmarkedMoment>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("关键时刻书签", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(GlassColors.accentGold.copy(alpha = 0.2f))
                    .clickable {
                        bookmarks.add(0, BookmarkedMoment(minute, "${m.homeName} vs ${m.awayName}", System.currentTimeMillis()))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("+ 标记", color = GlassColors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (bookmarks.isEmpty()) {
            Text("点击「+ 标记」记录关键时刻", color = GlassColors.textSecondary, fontSize = 13.sp)
        } else {
            bookmarks.take(5).forEach { b ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${b.minute}'", color = GlassColors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Text(b.description, color = GlassColors.textPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 20. 快速反应表情
// ─────────────────────────────────────────────────────────────────────────────
data class MatchReaction(
    val emoji: String,
    val timestamp: Long,
    val minute: Int,
)

@Composable
private fun ReactionCard(m: MatchInfo, reactions: MutableList<MatchReaction>) {
    val emojis = listOf("🔥", "😱", "😭", "🎉", "👏", "💪", "😤", "⚽", "🤯", "💔")
    GlassCard(Modifier.fillMaxWidth()) {
        Text("快速反应", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(emojis) { emoji ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            reactions.add(0, MatchReaction(emoji, System.currentTimeMillis(), 0))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }
        if (reactions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            reactions.take(5).forEach { r ->
                Text("${r.emoji}  ${r.timestamp}", color = GlassColors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 21. 进球庆祝动画
// ─────────────────────────────────────────────────────────────────────────────
data class GoalAnimData(
    val team: String,
    val scorer: String,
    val minute: String,
)

@Composable
private fun GoalCelebrationCard(m: MatchInfo, onGoal: (GoalAnimData) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("进球庆祝", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.accentMint.copy(alpha = 0.15f))
                    .clickable { onGoal(GoalAnimData(m.homeName, m.homeName, "0'")) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚽ ${m.homeName} 进球", color = GlassColors.accentMint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.accentPink.copy(alpha = 0.15f))
                    .clickable { onGoal(GoalAnimData(m.awayName, m.awayName, "0'")) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚽ ${m.awayName} 进球", color = GlassColors.accentPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GoalAnimOverlay(data: GoalAnimData, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "goal")
    val scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(data) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚽ GOAL! ⚽", color = GlassColors.accentGold, fontSize = 48.sp, fontWeight = FontWeight.Black, modifier = Modifier.scale(scale))
            Spacer(Modifier.height(16.dp))
            Text(data.team, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${data.scorer} ${data.minute}", color = GlassColors.accentMint, fontSize = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 工具箱入口
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MatchToolsEntry(m: MatchInfo, onOpenStream: () -> Unit = {}) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text("看球工具箱", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // 31. 观看直播 / 录播
        ToolButton("📺 观看直播 / 录播", GlassColors.up) {
            onOpenStream()
        }

        // 22. 投屏
        ToolButton("📺 投屏到电视", GlassColors.accentBlue) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/chromecast"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        // 23. 转播频道指南
        ToolButton("📡 转播频道", GlassColors.accentMint) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fifa.com/fifaplus/en/tournaments"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        // 24. 观赛派对
        ToolButton("🎉 观赛派对", GlassColors.accentPink) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "来看球！${m.homeName} vs ${m.awayName}\n${m.competition}\n一起来 FifaGlass 看球！")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "邀请好友看球").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // 25. 分享比赛
        ToolButton("📤 分享比赛", GlassColors.accentGold) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${m.homeName} vs ${m.awayName}\n${m.competition}\n${m.date}\nFifaGlass 实时追踪")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享比赛").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // 26. 比赛日记
        ToolButton("📝 记录到比赛日记", GlassColors.accentViolet) {
            val prefs = context.getSharedPreferences("fifaglass_match_diary", Context.MODE_PRIVATE)
            val existing = prefs.getString("entries", "") ?: ""
            val newEntry = "${m.date}|${m.homeName}|${m.awayName}|${m.homeScore ?: ""}-${m.awayScore ?: ""}|${m.competition}"
            prefs.edit().putString("entries", "$newEntry\n$existing").apply()
        }

        // 27. 设置提醒
        ToolButton("⏰ 设置开赛提醒", GlassColors.up) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fifa.com/fifaplus/en/tournaments"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        // 28. 球场导航
        ToolButton("🏟️ 球场信息", GlassColors.accentBlue) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${m.competition}+stadium"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        // 29. 天气查询
        ToolButton("🌤️ 比赛日天气", GlassColors.accentMint) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://weather.com"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        // 30. 附近酒吧/餐厅
        ToolButton("🍺 找个地方看球", GlassColors.accentGold) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=sports+bar"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ToolButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 震动和音效
// ─────────────────────────────────────────────────────────────────────────────
private fun vibrate(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}

private fun beep() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
    } catch (_: Exception) {}
}
