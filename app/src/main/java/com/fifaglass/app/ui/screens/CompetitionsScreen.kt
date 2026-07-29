package com.fifaglass.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Competition
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 赛事中心：全部赛事列表，点击查看近三周比赛 */
@Composable
fun CompetitionsScreen(
    onCompetitionClick: (Competition) -> Unit,
) {
    var competitions by remember { mutableStateOf<List<Competition>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            competitions = withContext(Dispatchers.IO) { FifaApi.fetchCompetitions() }
        } catch (e: Exception) {
            error = e.message ?: "网络请求失败"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "赛事中心",
            color = GlassColors.textPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "全球联赛与杯赛 · 点击查看近期赛程",
            color = GlassColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("搜索赛事（如 World Cup / Premier League）", color = GlassColors.textSecondary) },
            singleLine = true,
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

        val err = error
        when {
            err != null -> ErrorBox(err)
            competitions == null -> LoadingBox()
            else -> {
                val filtered = competitions!!.filter {
                    query.isBlank() || it.name.contains(query, true)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { c ->
                        GlassCard(
                            Modifier.fillMaxWidth().clickable { onCompetitionClick(c) },
                            corner = 18.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.name,
                                        color = GlassColors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (c.region.isNotEmpty()) {
                                        Text(
                                            c.region,
                                            color = GlassColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Text("›", color = GlassColors.textSecondary, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个赛事的近期比赛列表 */
@Composable
fun CompetitionMatchesScreen(
    competition: Competition,
    onMatchClick: (MatchInfo) -> Unit,
) {
    var matches by remember { mutableStateOf<List<MatchInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competition.id) {
        try {
            matches = withContext(Dispatchers.IO) {
                FifaApi.fetchCompetitionMatches(competition.id)
            }
        } catch (e: Exception) {
            error = e.message ?: "网络请求失败"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            competition.name,
            color = GlassColors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "近三周赛程赛果 · 点击比赛看详情",
            color = GlassColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            matches == null -> LoadingBox()
            matches!!.isEmpty() -> GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "该赛事近三周没有比赛",
                    color = GlassColors.textSecondary,
                    fontSize = 14.sp
                )
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(matches!!, key = { it.id }) { m ->
                    GlassCard(
                        Modifier.fillMaxWidth().clickable { onMatchClick(m) },
                        corner = 18.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (m.date.length >= 10) m.date.substring(5, 10) else "",
                                color = GlassColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(44.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${m.homeName} vs ${m.awayName}",
                                    color = GlassColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    m.competition,
                                    color = GlassColors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (m.homeScore != null && m.awayScore != null) {
                                Text(
                                    "${m.homeScore}:${m.awayScore}",
                                    color = GlassColors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text("未赛", color = GlassColors.accentGold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
