package com.fifaglass.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

private val CONFEDS = listOf("全部", "UEFA", "CONMEBOL", "CONCACAF", "CAF", "AFC", "OFC")

@Composable
fun RankingScreen(
    teams: List<Team>?,
    gender: Int,
    onGenderChange: (Int) -> Unit,
    error: String?,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onTeamClick: (Team) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var confed by remember { mutableStateOf("全部") }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "世界排名",
                    color = GlassColors.textPrimary,
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "FIFA/Coca-Cola 官方数据 · 内部接口直取",
                    color = GlassColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            PulsingDot()
            Spacer(Modifier.width(8.dp))
            Text("实时", color = GlassColors.up, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .glass(20.dp)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(1 to "男足", 2 to "女足").forEach { (g, label) ->
                val selected = gender == g
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (selected) Modifier.background(Aurora.primary(isDark = false), alpha = 0.22f)
                            else Modifier
                        )
                        .clickable { onGenderChange(g) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) GlassColors.textPrimary else GlassColors.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("搜索球队 / 国家代码", color = GlassColors.textSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = GlassColors.textPrimary,
                unfocusedTextColor = GlassColors.textPrimary,
                cursorColor = GlassColors.accentMint,
            )
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CONFEDS) { c ->
                val selected = confed == c
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (selected) Modifier.background(Aurora.cool(), alpha = 0.30f)
                            else Modifier.background(Color.White.copy(alpha = 0.06f))
                        )
                        .clickable { confed = c }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        c,
                        color = if (selected) GlassColors.accentBlue else GlassColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            teams == null -> LoadingBox()
            else -> {
                val filtered = teams.filter {
                    (query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.code.contains(query, ignoreCase = true)) &&
                        (confed == "全部" || it.confederation == confed)
                }
                val favTeams = filtered.filter { it.code in favorites }
                val showPodium = query.isBlank() && confed == "全部"

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (showPodium && teams.size >= 3) {
                        item(key = "podium") {
                            AuroraPodiumCard(teams.take(3), onTeamClick)
                        }
                    }
                    if (favTeams.isNotEmpty()) {
                        item(key = "fav_header") { ListHeader("★ 我的收藏") }
                        items(favTeams, key = { "fav_" + it.code }) { team ->
                            TeamRow(team, true, onToggleFavorite) { onTeamClick(team) }
                        }
                        item(key = "all_header") { ListHeader("全部球队") }
                    }
                    items(filtered, key = { it.code + "_" + it.rank }) { team ->
                        TeamRow(team, team.code in favorites, onToggleFavorite) {
                            onTeamClick(team)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "pulse-dot")
    val scale by transition.animateFloat(
        initialValue = 0.7f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse-dot-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse-dot-alpha"
    )
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(10.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(GlassColors.up.copy(alpha = alpha * 0.4f))
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(GlassColors.up)
        )
    }
}

@Composable
private fun ListHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(GlassColors.accentGold)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = GlassColors.accentGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AuroraPodiumCard(top3: List<Team>, onTeamClick: (Team) -> Unit) {
    val transition = rememberInfiniteTransition(label = "podium")
    val glow1 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "podium-glow1"
    )
    val glow2 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "podium-glow2"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = GlassColors.accentGold.copy(alpha = glow1 * 0.45f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFFF8E7),
                        Color(0xFFFFEBC7),
                        Color(0xFFFFD9B3),
                    )
                )
            )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "本期三甲",
                    color = GlassColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "TOP 3",
                    color = GlassColors.accentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                AuroraPodiumItem(top3[1], "🥈", GlassColors.textSecondary, 0.9f, glow2 * 0.35f, onTeamClick)
                AuroraPodiumItem(top3[0], "🥇", GlassColors.accentGold, 1.15f, glow1 * 0.6f, onTeamClick)
                AuroraPodiumItem(top3[2], "🥉", Color(0xFFE8A87C), 0.82f, glow2 * 0.30f, onTeamClick)
            }
        }
    }
}

@Composable
private fun AuroraPodiumItem(
    team: Team,
    medal: String,
    accent: Color,
    scale: Float,
    glow: Float,
    onTeamClick: (Team) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTeamClick(team) }
            .padding(6.dp)
    ) {
        Text(medal, fontSize = (24 * scale).sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size((48 * scale).dp)
                .shadow(
                    if (glow > 0f) 14.dp else 4.dp,
                    CircleShape,
                    ambientColor = accent.copy(alpha = glow)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White, accent.copy(alpha = 0.3f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = team.flagUrl,
                contentDescription = team.name,
                modifier = Modifier.size((44 * scale).dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            team.name,
            color = GlassColors.textPrimary,
            fontSize = (13 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            "%.0f 分".format(team.points),
            color = accent,
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TeamRow(
    team: Team,
    isFavorite: Boolean,
    onToggleFavorite: (String) -> Unit,
    onClick: () -> Unit,
) {
    val isTop3 = team.rank in 1..3
    val glowColor = when {
        isTop3 -> GlassColors.accentGold
        team.rankChange > 0 -> GlassColors.up
        team.rankChange < 0 -> GlassColors.down
        else -> null
    }

    val baseMod = Modifier
        .fillMaxWidth()
        .clickable { onClick() }

    val cardMod = if (glowColor != null) {
        baseMod.shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = glowColor.copy(alpha = 0.30f))
    } else {
        baseMod.shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.06f))
    }

    Box(
        cardMod
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isTop3) Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF7E0).copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.92f),
                    )
                )
                else Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.82f),
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (team.rank) {
                            1 -> Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
                            2 -> Brush.linearGradient(listOf(Color(0xFFC0C0C0), Color(0xFF888888)))
                            3 -> Brush.linearGradient(listOf(Color(0xFFCD7F32), Color(0xFF8B4513)))
                            else -> Brush.linearGradient(listOf(Color(0xFFEFEFF5), Color(0xFFF6F6FA)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${team.rank}",
                    color = if (team.rank in 1..3) Color.White else GlassColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = team.flagUrl,
                contentDescription = team.name,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    team.name,
                    color = GlassColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${team.code} · ${team.confederation}",
                    color = GlassColors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.2f".format(team.points),
                    color = GlassColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                ChangeTag(team.rankChange)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (isFavorite) "★" else "☆",
                color = if (isFavorite) GlassColors.accentGold else GlassColors.textSecondary,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onToggleFavorite(team.code) }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun ChangeTag(rankChange: Int) {
    when {
        rankChange > 0 -> Text(
            "▲ $rankChange",
            color = GlassColors.up,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        rankChange < 0 -> Text(
            "▼ ${-rankChange}",
            color = GlassColors.down,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        else -> Text("—", color = GlassColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun LoadingBox() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Column(
        Modifier.fillMaxSize().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(7) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (it == 0) 90.dp else 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = alpha * 0.35f))
            )
        }
    }
}

@Composable
fun ErrorBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Text("加载失败", color = GlassColors.down, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = GlassColors.textSecondary, fontSize = 13.sp)
        }
    }
}
