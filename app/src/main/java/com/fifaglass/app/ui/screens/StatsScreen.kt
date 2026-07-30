package com.fifaglass.app.ui.screens

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Team
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.HBarRow
import com.fifaglass.app.ui.blueMintBrush
import com.fifaglass.app.ui.pinkGoldBrush
import com.fifaglass.app.ui.violetBlueBrush

/** 数据可视化：Top20 积分、洲分布、升幅榜 */
@Composable
fun StatsScreen(teams: List<Team>?, error: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "数据可视化",
            color = GlassColors.textPrimary,
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            "积分榜 · 洲分布 · 走势榜",
            color = GlassColors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        val err = error
        when {
            err != null -> ErrorBox(err)
            teams == null -> LoadingBox()
            else -> {
                val top20 = teams.take(20)
                val maxPts = top20.maxOfOrNull { it.points } ?: 0.0

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Top 20 积分")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        top20.forEach { t ->
                            HBarRow(
                                label = t.name,
                                value = t.points,
                                maxValue = maxPts,
                                valueText = "%.0f".format(t.points),
                                barBrush = blueMintBrush,
                                rank = t.rank
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val top50 = teams.take(50)
                val confGroups = top50.groupBy { it.confederation }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedByDescending { it.second }
                val maxConf = confGroups.maxOfOrNull { it.second } ?: 1

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Top 50 各足联席位")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        confGroups.forEach { (conf, count) ->
                            HBarRow(
                                label = conf,
                                value = count.toDouble(),
                                maxValue = maxConf.toDouble(),
                                valueText = "$count 队",
                                barBrush = pinkGoldBrush
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val risers = teams.filter { it.rankChange > 0 }
                    .sortedByDescending { it.rankChange }
                    .take(10)
                val maxRise = risers.maxOfOrNull { it.rankChange } ?: 1

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("本期升幅榜 Top 10")
                    Spacer(Modifier.height(10.dp))
                    if (risers.isEmpty()) {
                        Text("本期无上升球队", color = GlassColors.textSecondary, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            risers.forEach { t ->
                                HBarRow(
                                    label = t.name,
                                    value = t.rankChange.toDouble(),
                                    maxValue = maxRise.toDouble(),
                                    valueText = "+${t.rankChange}",
                                    barBrush = violetBlueBrush,
                                    rank = t.rank
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}
