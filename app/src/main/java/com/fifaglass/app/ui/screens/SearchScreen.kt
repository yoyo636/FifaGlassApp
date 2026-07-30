package com.fifaglass.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.Competition
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    teams: List<Team>?,
    onTeamClick: (Team) -> Unit,
    onMatchClick: (MatchInfo) -> Unit,
    onCompetitionClick: (Competition) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var liveMatches by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var competitions by remember { mutableStateOf<List<Competition>?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val l = withContext(Dispatchers.IO) { FifaApi.fetchLiveMatches(1) }
            val r = withContext(Dispatchers.IO) { FifaApi.fetchRecentMatches(1) }
            val c = withContext(Dispatchers.IO) { FifaApi.fetchCompetitions() }
            liveMatches = (l + r).distinctBy { it.id }
            competitions = c
        } catch (_: Exception) {}
        loaded = true
    }

    val q = query.trim().lowercase()
    val filteredTeams = if (q.isNotEmpty()) teams?.filter {
        it.name.lowercase().contains(q) || it.code.lowercase().contains(q) || it.confederation.lowercase().contains(q)
    } ?: emptyList() else emptyList()
    val filteredMatches = if (q.isNotEmpty()) liveMatches?.filter {
        it.homeName.lowercase().contains(q) || it.awayName.lowercase().contains(q) || it.competition.lowercase().contains(q)
    } ?: emptyList() else emptyList()
    val filteredComps = if (q.isNotEmpty()) competitions?.filter {
        it.name.contains(q, true) || it.region.contains(q, true)
    } ?: emptyList() else emptyList()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("搜索", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("球队 · 比赛 · 赛事", color = GlassColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("输入关键词搜索...", color = GlassColors.textSecondary) },
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
        Spacer(Modifier.height(12.dp))

        if (q.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("输入关键词开始搜索", color = GlassColors.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("支持球队名称、国家代码、赛事名称、比赛对阵", color = GlassColors.textSecondary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredTeams.isNotEmpty()) {
                    item {
                        Text("球队 (${filteredTeams.size})", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    items(filteredTeams.take(10), key = { it.code }) { team ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onTeamClick(team) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = team.flagUrl,
                                contentDescription = team.name,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(team.name, color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("#${team.rank} · ${team.confederation}", color = GlassColors.textSecondary, fontSize = 11.sp)
                            }
                            Text("${"%.0f".format(team.points)} pts", color = GlassColors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (filteredMatches.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("比赛 (${filteredMatches.size})", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    items(filteredMatches.take(10), key = { it.id }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onMatchClick(m) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.homeName} vs ${m.awayName}", color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.competition, color = GlassColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (m.homeScore != null && m.awayScore != null) {
                                Text("${m.homeScore}:${m.awayScore}", color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("未赛", color = GlassColors.accentGold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (filteredComps.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("赛事 (${filteredComps.size})", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    items(filteredComps.take(10), key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onCompetitionClick(c) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, color = GlassColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.region.isNotEmpty()) Text(c.region, color = GlassColors.textSecondary, fontSize = 11.sp)
                            }
                            Text("›", color = GlassColors.textSecondary, fontSize = 18.sp)
                        }
                    }
                }

                if (filteredTeams.isEmpty() && filteredMatches.isEmpty() && filteredComps.isEmpty() && loaded) {
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("未找到 \"$query\" 的结果", color = GlassColors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }

                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}
