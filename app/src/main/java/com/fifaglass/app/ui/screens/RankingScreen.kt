package com.fifaglass.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.Team
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
        Spacer(Modifier.height(14.dp))

        // 男足/女足切换
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
                        .then(if (selected) Modifier.glass(16.dp) else Modifier)
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

        // 搜索
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

        // 洲筛选
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CONFEDS) { c ->
                val selected = confed == c
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (selected) Modifier.glass(14.dp) else Modifier)
                        .background(
                            if (selected) Color.Transparent else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable { confed = c }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        c,
                        color = if (selected) GlassColors.accentMint else GlassColors.textSecondary,
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
                            PodiumCard(teams.take(3), onTeamClick)
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
private fun ListHeader(title: String) {
    Text(
        title,
        color = GlassColors.accentGold,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Top 3 领奖台 */
@Composable
private fun PodiumCard(top3: List<Team>, onTeamClick: (Team) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
        Text(
            "本期三甲",
            color = GlassColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumItem(top3[1], "🥈", GlassColors.textSecondary, 0.9f, onTeamClick)
            PodiumItem(top3[0], "🥇", GlassColors.accentGold, 1.15f, onTeamClick)
            PodiumItem(top3[2], "🥉", Color(0xFFE8A87C), 0.82f, onTeamClick)
        }
    }
}

@Composable
private fun PodiumItem(
    team: Team,
    medal: String,
    accent: Color,
    scale: Float,
    onTeamClick: (Team) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTeamClick(team) }
            .padding(6.dp)
    ) {
        Text(medal, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(4.dp))
        AsyncImage(
            model = team.flagUrl,
            contentDescription = team.name,
            modifier = Modifier.size((44 * scale).dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
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
            fontSize = (11 * scale).sp
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
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        corner = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${team.rank}",
                color = when (team.rank) {
                    1 -> GlassColors.accentGold
                    in 2..3 -> GlassColors.accentMint
                    else -> GlassColors.textPrimary
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
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
                val changeText = when {
                    team.rankChange > 0 -> "▲ ${team.rankChange}"
                    team.rankChange < 0 -> "▼ ${-team.rankChange}"
                    else -> "—"
                }
                Text(
                    changeText,
                    color = when {
                        team.rankChange > 0 -> GlassColors.up
                        team.rankChange < 0 -> GlassColors.down
                        else -> GlassColors.textSecondary
                    },
                    fontSize = 12.sp
                )
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

/** 骨架屏加载态 */
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
