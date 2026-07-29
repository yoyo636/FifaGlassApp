package com.fifaglass.app.rating

import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team

/**
 * 兼容封装层：保留原有 API，内部全部委托给 [PredictionSystem] 万行预测系统。
 * 新代码请直接使用 PredictionSystem。
 */

/** 球队近期状态画像（由近 10 场比赛加权得出） */
data class TeamProfile(
    val idTeam: String,
    val played: Int,
    val formPoints: Double,
    val gf: Double,
    val ga: Double,
    val formText: String,
    val matches: List<MatchInfo>,
)

/** 历史交战记录 */
data class H2HRecordLegacy(
    val played: Int,
    val winsA: Int,
    val draws: Int,
    val winsB: Int,
)

/** 预测输出（轻量版，供旧 UI 使用） */
data class Prediction(
    val pHome: Double,
    val pDraw: Double,
    val pAway: Double,
    val likelyScore: String,
    val confidence: Int,
    val xgHome: Double,
    val xgAway: Double,
    val factors: List<String>,
)

object PredictionEngine {

    /** 拉取球队状态画像（委托给 PredictionSystem 的画像构建器） */
    fun profile(team: Team): TeamProfile {
        val p = ProfileBuilder.buildProfile(team)
        val matches = runCatching {
            com.fifaglass.app.data.FifaApi.fetchTeamMatches(team.idTeam)
                .filter { it.isFinished && it.homeScore != null && it.awayScore != null }
                .take(10)
        }.getOrDefault(emptyList())
        return TeamProfile(
            idTeam = team.idTeam,
            played = p.recentForm.matchesPlayed,
            formPoints = p.recentForm.pointsPerGame,
            gf = p.recentForm.goalsForPerGame,
            ga = p.recentForm.goalsAgainstPerGame,
            formText = p.recentForm.formText
                .replace("W", "胜").replace("D", "平").replace("L", "负"),
            matches = matches,
        )
    }

    /** 赛前预测（轻量输出） */
    fun predictPreMatch(
        home: Team, away: Team, trueHome: Boolean,
    ): Triple<Prediction, TeamProfile, TeamProfile> {
        val full = PredictionSystem.predictPreMatch(home, away, trueHome)
        return Triple(full.toLight(), profile(home), profile(away))
    }

    /** 完整赛前预测（新 API，推荐使用） */
    fun predictPreMatchFull(home: Team, away: Team, trueHome: Boolean): PredictionOutput =
        PredictionSystem.predictPreMatch(home, away, trueHome)

    /** 实时滚动预测 */
    fun predictLive(
        home: Team, away: Team,
        scoreH: Int, scoreA: Int, minute: Int,
    ): Prediction = PredictionSystem.predictLive(home, away, scoreH, scoreA, minute).toLight()

    /** 实时完整预测（新 API） */
    fun predictLiveFull(
        home: Team, away: Team,
        scoreH: Int, scoreA: Int, minute: Int,
    ): PredictionOutput = PredictionSystem.predictLive(home, away, scoreH, scoreA, minute)

    private fun PredictionOutput.toLight() = Prediction(
        pHome = pHome, pDraw = pDraw, pAway = pAway,
        likelyScore = likelyScore, confidence = confidence,
        xgHome = xgHome, xgAway = xgAway, factors = factors,
    )
}
