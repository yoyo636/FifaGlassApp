package com.fifaglass.app.rating

import com.fifaglass.app.data.Team
import kotlin.math.abs
import kotlin.math.pow

data class EvalDim(val label: String, val value: Float) // 0..100

data class EvalResult(
    val overall: Float,   // 0..100
    val grade: String,    // S/A/B/C/D
    val dims: List<EvalDim>,
)

/**
 * 内部评测体系：完全基于 FIFA 官方排名数据推导，算法透明。
 * 六维能力 + 综合评分 + Elo 胜率 + 实时表现分。
 */
object Evaluator {

    fun evaluate(team: Team, all: List<Team>): EvalResult {
        if (all.isEmpty()) return EvalResult(0f, "-", emptyList())
        val n = all.size
        val maxPts = all.maxOf { it.points }.coerceAtLeast(1.0)

        // 积分实力：相对第一名的积分比
        val strength = team.points / maxPts * 100.0
        // 排名地位：在全部球队中的分位
        val rankScore = (n - team.rank + 1).toDouble() / n * 100.0
        // 近期走势：名次升降折算（中性 55）
        val momentum = (55.0 + team.rankChange * 1.5).coerceIn(5.0, 100.0)
        // 稳定性：名次波动越小越高
        val stability = (100 - abs(team.rankChange) * 6).coerceIn(10, 100).toDouble()
        // 顶尖程度：距离榜首的绝对位置
        val elite = (100.0 - (team.rank - 1) * 0.6).coerceIn(0.0, 100.0)
        // 洲内竞争：积分相对本洲头名的比例
        val confMax = all.filter { it.confederation == team.confederation }
            .maxOfOrNull { it.points }?.coerceAtLeast(1.0) ?: maxPts
        val confScore = team.points / confMax * 100.0

        val dims = listOf(
            EvalDim("积分实力", strength.toFloat()),
            EvalDim("排名地位", rankScore.toFloat()),
            EvalDim("近期走势", momentum.toFloat()),
            EvalDim("稳定性", stability.toFloat()),
            EvalDim("顶尖程度", elite.toFloat()),
            EvalDim("洲内竞争", confScore.toFloat()),
        )

        val overall = (strength * 0.40 + rankScore * 0.25 + momentum * 0.15 +
                stability * 0.10 + elite * 0.10).toFloat()
        val grade = when {
            overall >= 88 -> "S"
            overall >= 75 -> "A"
            overall >= 60 -> "B"
            overall >= 45 -> "C"
            else -> "D"
        }
        return EvalResult(overall, grade, dims)
    }

    /** Elo 期望胜率（不含平局），基于双方 FIFA 积分差 */
    fun winExpectancy(pointsA: Double, pointsB: Double): Double =
        1.0 / (1.0 + 10.0.pow(-(pointsA - pointsB) / 400.0))

    /** 含平局的三结果概率，返回 Triple(主胜, 平, 客胜) */
    fun matchProbabilities(homePts: Double, awayPts: Double): Triple<Double, Double, Double> {
        val pHomeRaw = winExpectancy(homePts, awayPts)
        val draw = (0.28 - abs(pHomeRaw - 0.5) * 0.24).coerceIn(0.12, 0.28)
        val pHome = pHomeRaw * (1 - draw)
        val pAway = (1 - pHomeRaw) * (1 - draw)
        return Triple(pHome, draw, pAway)
    }

    /** 实时表现分 0..100：比分状态为主，对手强度修正 */
    fun livePerformance(selfPts: Double, oppPts: Double, goalsFor: Int, goalsAgainst: Int): Float {
        val diff = goalsFor - goalsAgainst
        var score = 50.0 + diff * 12.0
        val strengthAdj = (oppPts - selfPts) / 80.0
        score += if (diff >= 0) strengthAdj * 2.0 else strengthAdj
        return score.coerceIn(2.0, 98.0).toFloat()
    }

    fun performanceLabel(score: Float): String = when {
        score >= 80 -> "统治级"
        score >= 65 -> "出色"
        score >= 50 -> "合格"
        score >= 35 -> "低迷"
        else -> "崩盘"
    }

    fun strengthLabel(p: Double): String = when {
        p >= 0.70 -> "明显占优"
        p >= 0.55 -> "略占上风"
        p > 0.45 -> "势均力敌"
        p > 0.30 -> "处于下风"
        else -> "实力悬殊"
    }
}
