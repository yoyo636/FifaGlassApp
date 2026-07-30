package com.fifaglass.app.ui.screens
import androidx.compose.ui.draw.shadow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.ui.Favorites
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.GlassTheme
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.PrimaryButton
import com.fifaglass.app.ui.SecondaryButton
import com.fifaglass.app.ui.glass
import com.fifaglass.app.ui.glowBorder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    teams: List<Team>?,
    gender: Int,
    onGenderChange: (Int) -> Unit,
    error: String?,
    favorites: Set<String>,
    onTeamClick: (Team) -> Unit,
    onMatchClick: (MatchInfo) -> Unit,
    onOpenCompetitions: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val theme = LocalGlassTheme.current
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero Header with brand mark + greeting
        HomeHero(gender = gender, onGenderChange = onGenderChange)

        // Live Pulse ticker
        Spacer(Modifier.height(8.dp))
        LivePulseBar()

        // Quick actions grid (4 cards)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            QuickActionsGrid(
                onOpenMatches = onOpenMatches,
                onOpenRanking = onOpenRanking,
                onOpenStats = onOpenStats,
                onOpenCompetitions = onOpenCompetitions,
            )
            Spacer(Modifier.height(20.dp))
        }

        // Spotlight section
        Text(
            "焦点赛事",
            color = theme.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "AI 智能推荐 · 实时分析",
            color = theme.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(14.dp))
        SpotlightMatchesStrip(teams = teams, onMatchClick = onMatchClick)

        // Top 3 podium
        Spacer(Modifier.height(28.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            TopThreePodium(teams = teams, favorites = favorites, onTeamClick = onTeamClick)
        }

        // Competitions row
        Spacer(Modifier.height(28.dp))
        Text(
            "热门赛事",
            color = theme.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(14.dp))
        CompetitionsQuickRow(onOpenCompetitions = onOpenCompetitions)

        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun HomeHero(gender: Int, onGenderChange: (Int) -> Unit) {
    val theme = LocalGlassTheme.current
    val now = remember { System.currentTimeMillis() }
    val today = remember(now) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("MM月dd日 EEEE", Locale.CHINA))
    }
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "夜深了"
            hour < 11 -> "早安"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height * 0.5f
            // Multi-layer aurora behind hero
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassColors.aurora1.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(centerX * 0.5f, centerY * 0.6f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(centerX * 0.5f, centerY * 0.6f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassColors.aurora2.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(centerX * 1.5f, centerY * 0.4f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(centerX * 1.5f, centerY * 0.4f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassColors.aurora4.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(centerX, centerY * 1.4f),
                    radius = size.width * 0.6f,
                ),
                radius = size.width * 0.6f,
                center = Offset(centerX, centerY * 1.4f),
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Aurora.primary(isDark = false))
                        .shadow(20.dp, CircleShape, ambientColor = GlassColors.accentBlue.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u26bd", fontSize = 24.sp, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$greeting · FifaGlass",
                        color = theme.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        today,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
                GenderTogglePill(gender, onGenderChange)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v3.0",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Aurora.primary(isDark = false))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Beta · Liquid Glass UI",
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun GenderTogglePill(gender: Int, onGenderChange: (Int) -> Unit) {
    val theme = LocalGlassTheme.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(theme.glassTint.copy(alpha = 0.6f))
            .padding(3.dp)
    ) {
        Row {
            listOf(1 to "\u2642", 2 to "\u2640").forEach { (g, sym) ->
                val selected = gender == g
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Aurora.primary(isDark = false)
                            else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onGenderChange(g) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(sym, color = if (selected) Color.White else theme.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LivePulseBar() {
    val theme = LocalGlassTheme.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse-anim"
    )
    val scroll by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse-scroll"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.surfaceVariant.copy(alpha = 0.85f))
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(GlassColors.up)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "LIVE",
                color = GlassColors.up,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val headlines = listOf(
                    "巴西 vs 阿根廷 正在进行 · 第67分钟 2-1",
                    "CCTV-5 正在直播 西甲第15轮 皇马 vs 巴萨",
                    "🔥 世界杯预选赛 中国 vs 韩国 1-0 进球!",
                    "⚽ 欧冠1/8决赛 拜仁 3-2 曼城 姆巴佩帽子戏法",
                    "🎯 英超焦点战 利物浦 1-1 曼联 萨拉赫破门",
                )
                val h = headlines[((scroll * headlines.size).toInt()) % headlines.size]
                Text(h, color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onOpenMatches: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCompetitions: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionCard(
            icon = "\u26bd",
            title = "比赛",
            subtitle = "今日赛程",
            onClick = onOpenMatches,
            gradient = Aurora.primary(isDark = false),
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            icon = "\u2605",
            title = "排名",
            subtitle = "FIFA 官方",
            onClick = onOpenRanking,
            gradient = Aurora.success(),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionCard(
            icon = "\u2605",
            title = "排名",
            subtitle = "FIFA 官方",
            onClick = onOpenRanking,
            gradient = Aurora.success(),
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            icon = "\u2728",
            title = "数据",
            subtitle = "深度分析",
            onClick = onOpenStats,
            gradient = Aurora.warm(),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionCard(
            icon = "\u26f3",
            title = "赛事",
            subtitle = "全球联赛",
            onClick = onOpenCompetitions,
            gradient = Aurora.cool(),
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            icon = "📊",
            title = "对比",
            subtitle = "球队 PK",
            onClick = onOpenMatches,
            gradient = Aurora.danger(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    gradient: Brush,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "qa-scale"
    )
    val theme = LocalGlassTheme.current

    Box(
        modifier = modifier
            .height(96.dp)
            .scale(scale)
            .shadow(12.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = Color(0x20000000))
            .clip(RoundedCornerShape(20.dp))
            .background(theme.surface.copy(alpha = 0.92f))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        // Decorative corner gradient
        Box(
            Modifier
                .size(70.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(70f, 0f)
                    )
                )
        )

        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Color.White, fontSize = 20.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = theme.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpotlightMatchesStrip(teams: List<Team>?, onMatchClick: (MatchInfo) -> Unit) {
    val demoMatches = listOf(
        DemoMatch("巴西", "BRA", "阿根廷", "ARG", "2 - 1", "67'", true),
        DemoMatch("法国", "FRA", "西班牙", "ESP", "0 - 0", "32'", true),
        DemoMatch("英格兰", "ENG", "德国", "GER", "vs", "今晚 03:00", false),
        DemoMatch("葡萄牙", "POR", "荷兰", "NED", "vs", "明天 01:00", false),
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(demoMatches) { m ->
            SpotlightCard(m) { /* navigate */ }
        }
    }
}

private data class DemoMatch(
    val homeName: String, val homeCode: String,
    val awayName: String, val awayCode: String,
    val score: String, val time: String,
    val isLive: Boolean,
)

@Composable
private fun SpotlightCard(m: DemoMatch, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "spot-scale"
    )
    val theme = LocalGlassTheme.current

    Box(
        Modifier
            .width(220.dp)
            .height(140.dp)
            .scale(scale)
            .shadow(if (m.isLive) 24.dp else 12.dp, RoundedCornerShape(24.dp), ambientColor = if (m.isLive) GlassColors.accentPink.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(24.dp))
            .background(theme.surface)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        // Decorative corner
        Box(
            Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (m.isLive) GlassColors.accentPink.copy(alpha = 0.18f)
                            else GlassColors.accentBlue.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(140f, 0f),
                        radius = 180f
                    )
                )
        )

        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (m.isLive) GlassColors.up else GlassColors.textTertiary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (m.isLive) "LIVE" else "即将开赛",
                    color = if (m.isLive) GlassColors.up else theme.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    m.time,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.homeName, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(m.homeCode, color = theme.textTertiary, fontSize = 10.sp)
                }
                Text(
                    m.score,
                    color = if (m.isLive) theme.textPrimary else theme.textTertiary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(m.awayName, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(m.awayCode, color = theme.textTertiary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (m.isLive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBar(0.65f, GlassColors.up)
                    Spacer(Modifier.width(8.dp))
                    Text("主队控球 65%", color = theme.textSecondary, fontSize = 10.sp)
                }
            } else {
                Text(
                    "${m.homeCode} vs ${m.awayCode}",
                    color = theme.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LiveBar(value: Float, color: Color) {
    Box(
        Modifier
            .width(80.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.2f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
                .height(4.dp)
                .background(color)
        )
    }
}

@Composable
private fun TopThreePodium(teams: List<Team>?, favorites: Set<String>, onTeamClick: (Team) -> Unit) {
    val theme = LocalGlassTheme.current
    if (teams == null || teams.size < 3) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .glass(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("加载中...", color = theme.textSecondary, fontSize = 13.sp)
        }
        return
    }
    val top3 = teams.take(3)
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("世界前三", color = theme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("实时", color = GlassColors.up, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(GlassColors.up)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PodiumItem(top3[1], 2, 0.75f, GlassColors.accentTeal, theme, onTeamClick)
            PodiumItem(top3[0], 1, 1.0f, GlassColors.accentGold, theme, onTeamClick)
            PodiumItem(top3[2], 3, 0.62f, GlassColors.accentCoral, theme, onTeamClick)
        }
    }
}

@Composable
private fun PodiumItem(team: Team, rank: Int, heightFactor: Float, accent: Color, theme: GlassTheme, onClick: (Team) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pod-scale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null) { onClick(team) }
    ) {
        Box(
            Modifier
                .size((40 * heightFactor).dp)
                .shadow(if (rank == 1) 20.dp else 10.dp, CircleShape, ambientColor = accent.copy(alpha = 0.6f))
                .clip(CircleShape)
                .background(Aurora.card(theme.isDark))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                team.code,
                color = theme.textPrimary,
                fontSize = (12 * heightFactor).sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(team.name, color = theme.textPrimary, fontSize = (12 * heightFactor).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("%.0f".format(team.points), color = accent, fontSize = (10 * heightFactor).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(54.dp)
                .height((60 * heightFactor).dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.3f))
                    )
                )
        )
    }
}

@Composable
private fun CompetitionsQuickRow(onOpenCompetitions: () -> Unit) {
    val theme = LocalGlassTheme.current
    val comps = listOf(
        Triple("🏆", "世界杯", "WORLD CUP") to Aurora.warm(),
        Triple("👑", "欧洲杯", "EURO") to Aurora.primary(isDark = false),
        Triple("💰", "欧冠", "UCL") to Aurora.cool(),
        Triple("🌐", "英超", "EPL") to Aurora.danger(),
        Triple("🇨🇦", "西甲", "LA LIGA") to Aurora.success(),
        Triple("🔥", "意甲", "SERIE A") to Aurora.warm(),
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(comps) { (info, gradient) ->
            Box(
                Modifier
                    .width(96.dp)
                    .height(96.dp)
                    .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x20000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .clickable { onOpenCompetitions() }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent)
                            )
                        )
                )
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(info.first, fontSize = 24.sp)
                    Column {
                        Text(info.second, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(info.third, color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
