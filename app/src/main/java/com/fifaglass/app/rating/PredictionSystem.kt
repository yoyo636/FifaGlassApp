package com.fifaglass.app.rating

import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// =============================================================================
//  FifaGlass 独立预测系统 —— PredictionSystem.kt
//  -----------------------------------------------------------------------------
//  单文件庞大预测引擎，涵盖：
//   1. FIFA 排名 / 积分          2. Elo 动态评分        3. 近期状态加权
//   4. 攻防效率 (xG 推导)        5. 主客场效应          6. 历史交战 (收缩)
//   7. 球员因素估计              8. 战术风格相克        9. 天气影响
//  10. 裁判尺度                11. 泊松比分矩阵       12. Dixon-Coles 修正
//  13. 蒙特卡洛 10000 次模拟   14. 大小球             15. 双方都进球 BTTS
//  16. 角球预测                17. 红黄牌预测         18. 半全场 9 宫格
//  19. 首球时间分布            20. 冷门概率           21. 情景分析
//  22. 信心指数                23. 风险评级           24. 赛前/实时双模式
// =============================================================================

// =============================================================================
// 第一部分：数据模型
// =============================================================================

/** 球队综合画像：排名 + 近期 + 攻防 + 主客场 + 球员 + 战术 + Elo */
data class PredictionTeamProfile(
    val team: Team,
    val idTeam: String,
    val code: String,
    val name: String,
    val rank: Int,
    val points: Double,
    val confederation: String,
    val rankChange: Int,
    val recentForm: RecentForm,
    val attackDefense: AttackDefense,
    val homeAway: HomeAwayRecord,
    val h2h: H2HRecord,
    val playerFactor: PlayerFactor,
    val tacticalStyle: TacticalStyle,
    val eloRating: Double,
    val overallStrength: Double,
)

/** 近期状态：近 15 场时间衰减加权 */
data class RecentForm(
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Double,
    val goalsAgainst: Double,
    val pointsPerGame: Double,
    val goalsForPerGame: Double,
    val goalsAgainstPerGame: Double,
    val formText: String,
    val formScore: Double,
    val winRate: Double,
    val cleanSheetRate: Double,
    val bttsRate: Double,
    val over25Rate: Double,
    val streakWins: Int,
    val streakUnbeaten: Int,
    val streakWithoutWin: Int,
)

/** 攻防效率 */
data class AttackDefense(
    val attackStrength: Double,
    val defenseStrength: Double,
    val xgForPerGame: Double,
    val xgAgainstPerGame: Double,
    val cleanSheets: Int,
    val failedToScore: Int,
    val bigWins: Int,
    val bigLosses: Int,
)

/** 主客场战绩 */
data class HomeAwayRecord(
    val homePlayed: Int,
    val homeWins: Int,
    val homeDraws: Int,
    val homeLosses: Int,
    val homeGoalsFor: Double,
    val homeGoalsAgainst: Double,
    val homeWinRate: Double,
    val awayPlayed: Int,
    val awayWins: Int,
    val awayDraws: Int,
    val awayLosses: Int,
    val awayGoalsFor: Double,
    val awayGoalsAgainst: Double,
    val awayWinRate: Double,
    val homeAdvantage: Double,
)

/** 历史交战 */
data class H2HRecord(
    val played: Int,
    val winsA: Int,
    val draws: Int,
    val winsB: Int,
    val goalsA: Int,
    val goalsB: Int,
    val aWinRate: Double,
    val drawRate: Double,
    val bWinRate: Double,
    val avgTotalGoals: Double,
    val lastMatchDate: String,
)

/** 球员因素（由比赛数据间接估计） */
data class PlayerFactor(
    val squadStrength: Double,
    val keyPlayerAvailable: Boolean,
    val injuryCount: Int,
    val missingStarters: Int,
    val avgPlayerRating: Double,
    val topScorerGoals: Int,
    val topScorerAvailable: Boolean,
    val goalkeeperRating: Double,
    val defensiveStability: Double,
)

/** 战术风格 */
data class TacticalStyle(
    val preferredFormation: String,
    val style: TacticalStyleLabel,
    val possessionAvg: Double,
    val pressingIntensity: Double,
    val counterAttack: Double,
    val setPieceAttack: Double,
    val setPieceDefense: Double,
    val tempo: Double,
    val width: Double,
    val defensiveLine: Double,
)

/** 战术风格标签 */
enum class TacticalStyleLabel {
    POSSESSION,      // 控球型
    COUNTER_ATTACK,  // 反击型
    HIGH_PRESS,      // 高压逼抢
    DIRECT,          // 长传冲吊
    BALANCED,        // 平衡型
    DEFENSIVE,       // 防守型
    UNKNOWN          // 未知
}

/** 天气影响因子 */
data class WeatherFactor(
    val temperature: Double,
    val humidity: Double,
    val windSpeed: Double,
    val precipitation: Double,
    val condition: WeatherCondition,
    val impactOnGoals: Double,
    val impactOnPossession: Double,
    val favoredStyle: TacticalStyleLabel,
)

/** 天气状况 */
enum class WeatherCondition {
    CLEAR, CLOUDY, RAIN, HEAVY_RAIN, SNOW, WIND, EXTREME_HEAT, EXTREME_COLD
}

/** 裁判尺度 */
data class RefereeFactor(
    val name: String,
    val avgYellowPerGame: Double,
    val avgRedPerGame: Double,
    val avgPenaltyPerGame: Double,
    val strictness: Double,
    val homeAdvantageBias: Double,
    val cardTendency: Double,
)

/** 比分概率 */
data class ScoreProbability(val score: String, val probability: Double)

/** 半全场概率 */
data class HTFTProbability(val htft: String, val probability: Double)

/** 风险等级 */
enum class RiskLevel { LOW, MEDIUM, HIGH, EXTREME }

/** 大小球概率族 */
data class OverUnderProbabilities(
    val over05: Double,
    val over15: Double,
    val over25: Double,
    val over35: Double,
    val over45: Double,
    val under05: Double,
    val under15: Double,
    val under25: Double,
    val under35: Double,
    val under45: Double,
)

/** 角球预测 */
data class CornerPrediction(
    val expectedHome: Double,
    val expectedAway: Double,
    val expectedTotal: Double,
    val over85: Double,
    val over95: Double,
    val over105: Double,
)

/** 红黄牌预测 */
data class CardPrediction(
    val expectedHomeYellows: Double,
    val expectedAwayYellows: Double,
    val expectedTotalYellows: Double,
    val expectedReds: Double,
    val over35Cards: Double,
    val over45Cards: Double,
    val anyRedCard: Double,
)

/** 首球时间预测 */
data class FirstGoalTimePrediction(
    val expectedMinute: Double,
    val noGoalProbability: Double,
    val before15min: Double,
    val between1530: Double,
    val between3060: Double,
    val between6075: Double,
    val after75min: Double,
)

/** 蒙特卡洛模拟结果 */
data class MonteCarloResult(
    val simulations: Int,
    val homeWins: Int,
    val draws: Int,
    val awayWins: Int,
    val bothTeamsScored: Int,
    val over25: Int,
    val avgHomeGoals: Double,
    val avgAwayGoals: Double,
    val avgTotalGoals: Double,
    val cleanSheetHome: Int,
    val cleanSheetAway: Int,
    val biggestHomeWin: String,
    val biggestAwayWin: String,
    val exactScoreFrequency: Map<String, Int>,
)

/** 情景分析条目 */
data class Scenario(
    val name: String,
    val description: String,
    val probability: Double,
    val impact: String,
)

/** 预测最终输出：聚合所有维度 */
data class PredictionOutput(
    val pHome: Double,
    val pDraw: Double,
    val pAway: Double,
    val likelyScore: String,
    val likelyScoreProbability: Double,
    val topScores: List<ScoreProbability>,
    val xgHome: Double,
    val xgAway: Double,
    val confidence: Int,
    val riskLevel: RiskLevel,
    val upsetProbability: Double,
    val overProbabilities: OverUnderProbabilities,
    val cornerPrediction: CornerPrediction,
    val cardPrediction: CardPrediction,
    val halfTimeFullTime: List<HTFTProbability>,
    val bttsProbability: Double,
    val firstGoalTimePrediction: FirstGoalTimePrediction,
    val monteCarlo: MonteCarloResult,
    val factors: List<String>,
    val scenarios: List<Scenario>,
    val recommendation: String,
)

// =============================================================================
// 第二部分：常量与配置
// =============================================================================

object PredictionConstants {
    // ---- 基准进球参数 ----
    const val BASE_GOALS = 1.40
    const val HOME_ADVANTAGE = 1.18
    const val AWAY_DISADVANTAGE = 0.88
    const val DRAW_BASE_RATE = 0.26
    const val MAX_GOALS = 8

    // ---- 蒙特卡洛 ----
    const val MONTE_CARLO_SIMULATIONS = 10000

    // ---- Elo ----
    const val ELO_K_FACTOR = 25.0
    const val ELO_BASE = 1500.0
    const val ELO_HOME_SHIFT = 100.0

    // ---- 时间衰减 ----
    const val FORM_DECAY = 0.92

    // ---- 证据融合权重 ----
    const val WEIGHT_ELO = 0.28
    const val WEIGHT_MATRIX = 0.34
    const val WEIGHT_FORM = 0.14
    const val WEIGHT_H2H_MAX = 0.12
    const val WEIGHT_HOME_AWAY = 0.12

    // ---- 半场进球分布：上半场进球占比约 44% ----
    const val FIRST_HALF_GOAL_SHARE = 0.44

    // ---- 角球基准 ----
    const val BASE_CORNERS_TOTAL = 10.2
    const val BASE_CORNERS_HOME_SHARE = 0.55

    // ---- 红黄牌基准 ----
    const val BASE_YELLOWS_TOTAL = 3.9
    const val BASE_RED_PROB = 0.22

    // ---- 天气对总进球的影响系数 ----
    val WEATHER_GOAL_IMPACT = mapOf(
        WeatherCondition.CLEAR to 1.00,
        WeatherCondition.CLOUDY to 0.98,
        WeatherCondition.RAIN to 0.92,
        WeatherCondition.HEAVY_RAIN to 0.82,
        WeatherCondition.SNOW to 0.76,
        WeatherCondition.WIND to 0.93,
        WeatherCondition.EXTREME_HEAT to 0.88,
        WeatherCondition.EXTREME_COLD to 0.86,
    )

    // ---- 天气对控球的影响 ----
    val WEATHER_POSSESSION_IMPACT = mapOf(
        WeatherCondition.CLEAR to 1.00,
        WeatherCondition.CLOUDY to 1.00,
        WeatherCondition.RAIN to 0.96,
        WeatherCondition.HEAVY_RAIN to 0.90,
        WeatherCondition.SNOW to 0.88,
        WeatherCondition.WIND to 0.94,
        WeatherCondition.EXTREME_HEAT to 0.97,
        WeatherCondition.EXTREME_COLD to 0.95,
    )

    // ---- 战术相克矩阵：行对列的优势修正 (-0.05 ~ +0.05) ----
    val TACTICAL_MATCHUP: Map<TacticalStyleLabel, Map<TacticalStyleLabel, Double>> = mapOf(
        TacticalStyleLabel.POSSESSION to mapOf(
            TacticalStyleLabel.POSSESSION to 0.00,
            TacticalStyleLabel.COUNTER_ATTACK to -0.05,
            TacticalStyleLabel.HIGH_PRESS to -0.03,
            TacticalStyleLabel.DIRECT to 0.04,
            TacticalStyleLabel.BALANCED to 0.01,
            TacticalStyleLabel.DEFENSIVE to -0.02,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.COUNTER_ATTACK to mapOf(
            TacticalStyleLabel.POSSESSION to 0.05,
            TacticalStyleLabel.COUNTER_ATTACK to 0.00,
            TacticalStyleLabel.HIGH_PRESS to 0.03,
            TacticalStyleLabel.DIRECT to -0.02,
            TacticalStyleLabel.BALANCED to 0.01,
            TacticalStyleLabel.DEFENSIVE to 0.04,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.HIGH_PRESS to mapOf(
            TacticalStyleLabel.POSSESSION to 0.03,
            TacticalStyleLabel.COUNTER_ATTACK to -0.03,
            TacticalStyleLabel.HIGH_PRESS to 0.00,
            TacticalStyleLabel.DIRECT to -0.04,
            TacticalStyleLabel.BALANCED to 0.02,
            TacticalStyleLabel.DEFENSIVE to 0.03,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.DIRECT to mapOf(
            TacticalStyleLabel.POSSESSION to -0.04,
            TacticalStyleLabel.COUNTER_ATTACK to 0.02,
            TacticalStyleLabel.HIGH_PRESS to 0.04,
            TacticalStyleLabel.DIRECT to 0.00,
            TacticalStyleLabel.BALANCED to -0.01,
            TacticalStyleLabel.DEFENSIVE to -0.03,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.BALANCED to mapOf(
            TacticalStyleLabel.POSSESSION to -0.01,
            TacticalStyleLabel.COUNTER_ATTACK to -0.01,
            TacticalStyleLabel.HIGH_PRESS to -0.02,
            TacticalStyleLabel.DIRECT to 0.01,
            TacticalStyleLabel.BALANCED to 0.00,
            TacticalStyleLabel.DEFENSIVE to 0.01,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.DEFENSIVE to mapOf(
            TacticalStyleLabel.POSSESSION to 0.02,
            TacticalStyleLabel.COUNTER_ATTACK to -0.04,
            TacticalStyleLabel.HIGH_PRESS to -0.03,
            TacticalStyleLabel.DIRECT to 0.03,
            TacticalStyleLabel.BALANCED to -0.01,
            TacticalStyleLabel.DEFENSIVE to 0.00,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
        TacticalStyleLabel.UNKNOWN to mapOf(
            TacticalStyleLabel.POSSESSION to 0.00,
            TacticalStyleLabel.COUNTER_ATTACK to 0.00,
            TacticalStyleLabel.HIGH_PRESS to 0.00,
            TacticalStyleLabel.DIRECT to 0.00,
            TacticalStyleLabel.BALANCED to 0.00,
            TacticalStyleLabel.DEFENSIVE to 0.00,
            TacticalStyleLabel.UNKNOWN to 0.00,
        ),
    )

    // ---- 各足联强度系数（相对世界平均） ----
    val CONFEDERATION_STRENGTH = mapOf(
        "UEFA" to 1.08,
        "CONMEBOL" to 1.06,
        "CONCACAF" to 0.97,
        "CAF" to 0.95,
        "AFC" to 0.92,
        "OFC" to 0.82,
    )
}

// =============================================================================
// 第三部分：数学工具
// =============================================================================

internal object PredictMath {

    fun poisson(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        return exp(-lambda) * lambda.pow(k) / factorial(k)
    }

    fun factorial(n: Int): Double {
        if (n <= 1) return 1.0
        var result = 1.0
        for (i in 2..n) result *= i
        return result
    }

    /** 二项分布概率 */
    fun binomial(n: Int, k: Int, p: Double): Double {
        if (p <= 0.0) return if (k == 0) 1.0 else 0.0
        if (p >= 1.0) return if (k == n) 1.0 else 0.0
        return combination(n, k) * p.pow(k) * (1 - p).pow(n - k)
    }

    fun combination(n: Int, k: Int): Double {
        if (k < 0 || k > n) return 0.0
        val kk = min(k, n - k)
        var result = 1.0
        for (i in 0 until kk) {
            result *= (n - i).toDouble()
            result /= (i + 1).toDouble()
        }
        return result
    }

    fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    fun softmax(values: DoubleArray): DoubleArray {
        if (values.isEmpty()) return values
        val maxVal = values.maxOrNull() ?: 0.0
        val exps = DoubleArray(values.size) { exp(values[it] - maxVal) }
        val sum = exps.sum()
        return if (sum > 0) DoubleArray(values.size) { exps[it] / sum } else exps
    }

    fun clamp(value: Double, lo: Double, hi: Double): Double = value.coerceIn(lo, hi)

    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)

    fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    /** 伽马函数近似（Lanczos 近似） */
    fun gamma(x: Double): Double {
        if (x <= 0.0 && x == x.toInt().toDouble()) return Double.POSITIVE_INFINITY
        if (x < 0.5) return PI / (sin(PI * x) * gamma(1.0 - x))
        val p = doubleArrayOf(
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
        var y = x - 1.0
        var a = 0.99999999999980993
        for (i in p.indices) a += p[i] / (y + i + 1.0)
        val t = y + p.size - 0.5
        return sqrt(2.0 * PI) * t.pow(y + 0.5) * exp(-t) * a
    }

    /** 贝塔函数：B(a,b) = Γ(a)Γ(b)/Γ(a+b) */
    fun betaFunction(a: Double, b: Double): Double {
        return gamma(a) * gamma(b) / gamma(a + b)
    }

    /** 泊松累计：P(X <= k) */
    fun poissonCdf(k: Int, lambda: Double): Double {
        var sum = 0.0
        for (i in 0..k) sum += poisson(i, lambda)
        return sum
    }

    /** 泊松超过概率：P(X > line)，如 over2.5 = P(X>=3) */
    fun poissonOver(line: Double, lambda: Double): Double {
        val k = line.toInt() // 2.5 -> 2，P(X >= 3) = 1 - P(X <= 2)
        return 1.0 - poissonCdf(k, lambda)
    }

    /** Dixon-Coles 低比分相关系数 tau */
    fun dixonColesTau(homeGoals: Int, awayGoals: Int, lambdaHome: Double, lambdaAway: Double, rho: Double): Double {
        return when {
            homeGoals == 0 && awayGoals == 0 -> 1.0 - lambdaHome * lambdaAway * rho
            homeGoals == 0 && awayGoals == 1 -> 1.0 + lambdaHome * rho
            homeGoals == 1 && awayGoals == 0 -> 1.0 + lambdaAway * rho
            homeGoals == 1 && awayGoals == 1 -> 1.0 - rho
            else -> 1.0
        }
    }

    /** 指数分布：首球在 t 分钟前发生的概率，rate 为每分钟总进球率 */
    fun firstGoalBefore(minute: Double, totalXg: Double): Double {
        val rate = totalXg / 90.0
        return 1.0 - exp(-rate * minute)
    }
}

// =============================================================================
// 第四部分：球队画像构建（近期状态 / 攻防 / 主客场 / 交战 / 球员 / 战术 / Elo）
// =============================================================================

object ProfileBuilder {

    private val profileCache = HashMap<String, Pair<Long, PredictionTeamProfile>>()
    private const val CACHE_MS = 30 * 60 * 1000L

    /** 构建球队完整画像（带 30 分钟缓存） */
    fun buildProfile(team: Team, opponent: Team? = null, forceRefresh: Boolean = false): PredictionTeamProfile {
        val cacheKey = "${team.idTeam}_${opponent?.idTeam ?: "none"}"
        if (!forceRefresh) {
            profileCache[cacheKey]?.let { (ts, p) ->
                if (System.currentTimeMillis() - ts < CACHE_MS) return p
            }
        }

        val matches = fetchRecentMatches(team)
        val recentForm = analyzeRecentForm(matches, team.code)
        val attackDefense = analyzeAttackDefense(matches, team.code)
        val homeAway = analyzeHomeAway(matches, team.code)
        val h2h = if (opponent != null) analyzeH2H(team, opponent) else emptyH2H()
        val playerFactor = estimatePlayerFactor(team, matches)
        val tacticalStyle = estimateTacticalStyle(team, matches)
        val eloRating = computeEloRating(team, matches)
        val overallStrength = computeOverallStrength(team, recentForm, attackDefense, eloRating)

        val profile = PredictionTeamProfile(
            team = team,
            idTeam = team.idTeam,
            code = team.code,
            name = team.name,
            rank = team.rank,
            points = team.points,
            confederation = team.confederation,
            rankChange = team.rankChange,
            recentForm = recentForm,
            attackDefense = attackDefense,
            homeAway = homeAway,
            h2h = h2h,
            playerFactor = playerFactor,
            tacticalStyle = tacticalStyle,
            eloRating = eloRating,
            overallStrength = overallStrength,
        )
        profileCache[cacheKey] = System.currentTimeMillis() to profile
        return profile
    }

    fun emptyH2H(): H2HRecord = H2HRecord(0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, "")

    /** 拉取近 15 场已结束比赛 */
    private fun fetchRecentMatches(team: Team): List<MatchInfo> {
        return runCatching {
            FifaApi.fetchTeamMatches(team.idTeam)
                .filter { it.isFinished && it.homeScore != null && it.awayScore != null }
                .take(15)
        }.getOrDefault(emptyList())
    }

    // ---------- 近期状态 ----------

    private fun analyzeRecentForm(matches: List<MatchInfo>, code: String): RecentForm {
        if (matches.isEmpty()) {
            return RecentForm(
                matchesPlayed = 0, wins = 0, draws = 0, losses = 0,
                goalsFor = 0.0, goalsAgainst = 0.0,
                pointsPerGame = 1.4,
                goalsForPerGame = PredictionConstants.BASE_GOALS,
                goalsAgainstPerGame = PredictionConstants.BASE_GOALS,
                formText = "", formScore = 50.0, winRate = 0.40,
                cleanSheetRate = 0.25, bttsRate = 0.50, over25Rate = 0.46,
                streakWins = 0, streakUnbeaten = 0, streakWithoutWin = 0,
            )
        }

        var wSum = 0.0; var ptsSum = 0.0; var gfSum = 0.0; var gaSum = 0.0
        var wins = 0; var draws = 0; var losses = 0
        var cleanSheets = 0; var btts = 0; var over25 = 0
        val formText = StringBuilder()

        // 连续纪录（从最近一场往回数）
        var streakWins = 0; var streakUnbeaten = 0; var streakWithoutWin = 0
        var countingWin = true; var countingUnbeaten = true; var countingNoWin = true

        matches.forEachIndexed { i, m ->
            val w = PredictionConstants.FORM_DECAY.pow(i)
            val isHome = m.homeCode == code
            val gf = (if (isHome) m.homeScore else m.awayScore)!!.toDouble()
            val ga = (if (isHome) m.awayScore else m.homeScore)!!.toDouble()
            val pts = when {
                gf > ga -> 3.0
                gf < ga -> 0.0
                else -> 1.0
            }
            wSum += w; ptsSum += w * pts; gfSum += w * gf; gaSum += w * ga

            when {
                gf > ga -> {
                    wins++
                    formText.append("W")
                    if (countingWin) streakWins++
                    if (countingUnbeaten) streakUnbeaten++
                    countingNoWin = false
                }
                gf < ga -> {
                    losses++
                    formText.append("L")
                    countingWin = false; countingUnbeaten = false
                    if (countingNoWin) streakWithoutWin++
                }
                else -> {
                    draws++
                    formText.append("D")
                    countingWin = false
                    if (countingUnbeaten) streakUnbeaten++
                    if (countingNoWin) streakWithoutWin++
                }
            }
            if (ga == 0.0) cleanSheets++
            if (gf > 0 && ga > 0) btts++
            if (gf + ga > 2.5) over25++
        }

        val pg = ptsSum / wSum
        val gfPg = gfSum / wSum
        val gaPg = gaSum / wSum
        val formScore = (
            (pg / 3.0) * 62.0 +
            (wins.toDouble() / matches.size) * 24.0 +
            min(streakWins * 3.0, 14.0)
        ).coerceIn(0.0, 100.0)

        return RecentForm(
            matchesPlayed = matches.size,
            wins = wins, draws = draws, losses = losses,
            goalsFor = gfSum, goalsAgainst = gaSum,
            pointsPerGame = pg,
            goalsForPerGame = gfPg,
            goalsAgainstPerGame = gaPg,
            formText = formText.toString(),
            formScore = formScore,
            winRate = wins.toDouble() / matches.size,
            cleanSheetRate = cleanSheets.toDouble() / matches.size,
            bttsRate = btts.toDouble() / matches.size,
            over25Rate = over25.toDouble() / matches.size,
            streakWins = streakWins,
            streakUnbeaten = streakUnbeaten,
            streakWithoutWin = streakWithoutWin,
        )
    }

    // ---------- 攻防效率 ----------

    private fun analyzeAttackDefense(matches: List<MatchInfo>, code: String): AttackDefense {
        if (matches.isEmpty()) {
            return AttackDefense(
                attackStrength = 1.0, defenseStrength = 1.0,
                xgForPerGame = PredictionConstants.BASE_GOALS,
                xgAgainstPerGame = PredictionConstants.BASE_GOALS,
                cleanSheets = 0, failedToScore = 0, bigWins = 0, bigLosses = 0,
            )
        }
        var wSum = 0.0; var gfSum = 0.0; var gaSum = 0.0
        var cleanSheets = 0; var failedToScore = 0; var bigWins = 0; var bigLosses = 0

        matches.forEachIndexed { i, m ->
            val w = PredictionConstants.FORM_DECAY.pow(i)
            val isHome = m.homeCode == code
            val gf = (if (isHome) m.homeScore else m.awayScore)!!.toDouble()
            val ga = (if (isHome) m.awayScore else m.homeScore)!!.toDouble()
            wSum += w; gfSum += w * gf; gaSum += w * ga
            if (ga == 0.0) cleanSheets++
            if (gf == 0.0) failedToScore++
            if (gf - ga >= 3) bigWins++
            if (ga - gf >= 3) bigLosses++
        }

        val gfPg = gfSum / wSum
        val gaPg = gaSum / wSum
        return AttackDefense(
            attackStrength = (gfPg / PredictionConstants.BASE_GOALS).coerceIn(0.3, 2.6),
            defenseStrength = (PredictionConstants.BASE_GOALS / gaPg.coerceAtLeast(0.25)).coerceIn(0.4, 2.6),
            xgForPerGame = gfPg,
            xgAgainstPerGame = gaPg,
            cleanSheets = cleanSheets,
            failedToScore = failedToScore,
            bigWins = bigWins,
            bigLosses = bigLosses,
        )
    }

    // ---------- 主客场 ----------

    private fun analyzeHomeAway(matches: List<MatchInfo>, code: String): HomeAwayRecord {
        var hp = 0; var hw = 0; var hd = 0; var hl = 0
        var hgf = 0.0; var hga = 0.0
        var ap = 0; var aw = 0; var ad = 0; var al = 0
        var agf = 0.0; var aga = 0.0

        for (m in matches) {
            val isHome = m.homeCode == code
            val gf = (if (isHome) m.homeScore else m.awayScore)!!.toDouble()
            val ga = (if (isHome) m.awayScore else m.homeScore)!!.toDouble()
            if (isHome) {
                hp++; hgf += gf; hga += ga
                when {
                    gf > ga -> hw++
                    gf < ga -> hl++
                    else -> hd++
                }
            } else {
                ap++; agf += gf; aga += ga
                when {
                    gf > ga -> aw++
                    gf < ga -> al++
                    else -> ad++
                }
            }
        }

        val homeWinRate = if (hp > 0) hw.toDouble() / hp else 0.44
        val awayWinRate = if (ap > 0) aw.toDouble() / ap else 0.28
        val homeAdvantage = if (awayWinRate > 0.02) {
            (homeWinRate / awayWinRate).coerceIn(1.0, 2.2)
        } else 1.4

        return HomeAwayRecord(
            homePlayed = hp, homeWins = hw, homeDraws = hd, homeLosses = hl,
            homeGoalsFor = hgf, homeGoalsAgainst = hga, homeWinRate = homeWinRate,
            awayPlayed = ap, awayWins = aw, awayDraws = ad, awayLosses = al,
            awayGoalsFor = agf, awayGoalsAgainst = aga, awayWinRate = awayWinRate,
            homeAdvantage = homeAdvantage,
        )
    }

    // ---------- 历史交战 ----------

    private fun analyzeH2H(a: Team, b: Team): H2HRecord {
        val matchesA = fetchRecentMatches(a)
        var winsA = 0; var draws = 0; var winsB = 0
        var goalsA = 0; var goalsB = 0
        var played = 0
        var lastDate = ""

        for (m in matchesA) {
            val hs = m.homeScore ?: continue
            val as_ = m.awayScore ?: continue
            val aIsHome = m.homeCode == a.code
            val opp = if (aIsHome) m.awayCode else m.homeCode
            if (opp != b.code) continue
            val gfA = if (aIsHome) hs else as_
            val gfB = if (aIsHome) as_ else hs
            played++
            goalsA += gfA; goalsB += gfB
            if (lastDate.isEmpty()) lastDate = m.date.take(10)
            when {
                gfA > gfB -> winsA++
                gfA < gfB -> winsB++
                else -> draws++
            }
        }

        return H2HRecord(
            played = played,
            winsA = winsA, draws = draws, winsB = winsB,
            goalsA = goalsA, goalsB = goalsB,
            aWinRate = if (played > 0) winsA.toDouble() / played else 0.0,
            drawRate = if (played > 0) draws.toDouble() / played else 0.0,
            bWinRate = if (played > 0) winsB.toDouble() / played else 0.0,
            avgTotalGoals = if (played > 0) (goalsA + goalsB).toDouble() / played else 0.0,
            lastMatchDate = lastDate,
        )
    }

    // ---------- 球员因素（由比赛数据估计） ----------

    private fun estimatePlayerFactor(team: Team, matches: List<MatchInfo>): PlayerFactor {
        val form = matches.take(10)
        val squadStrength = (50.0 + (team.points - 1400.0) / 12.0).coerceIn(20.0, 98.0)
        var highScoringGames = 0
        var concedingVariance = mutableListOf<Double>()
        for (m in form) {
            val isHome = m.homeCode == team.code
            val gf = (if (isHome) m.homeScore else m.awayScore)!!.toDouble()
            val ga = (if (isHome) m.awayScore else m.homeScore)!!.toDouble()
            if (gf >= 3) highScoringGames++
            concedingVariance.add(ga)
        }
        val defensiveStability = (100.0 - PredictMath.standardDeviation(concedingVariance) * 28.0)
            .coerceIn(20.0, 98.0)
        val goalkeeperRating = (squadStrength * 0.7 + defensiveStability * 0.3).coerceIn(20.0, 98.0)

        return PlayerFactor(
            squadStrength = squadStrength,
            keyPlayerAvailable = true,
            injuryCount = 0,
            missingStarters = 0,
            avgPlayerRating = squadStrength,
            topScorerGoals = highScoringGames,
            topScorerAvailable = true,
            goalkeeperRating = goalkeeperRating,
            defensiveStability = defensiveStability,
        )
    }

    // ---------- 战术风格（由阵型与比赛表现推断） ----------

    private fun estimateTacticalStyle(team: Team, matches: List<MatchInfo>): TacticalStyle {
        var formation = "4-3-3"
        for (m in matches) {
            val t = if (m.homeCode == team.code) m.homeTactics else m.awayTactics
            if (!t.isNullOrEmpty()) { formation = t; break }
        }

        // 依据进失球画像推断风格
        var gfSum = 0.0; var gaSum = 0.0; var n = 0
        for (m in matches.take(10)) {
            val isHome = m.homeCode == team.code
            gfSum += (if (isHome) m.homeScore else m.awayScore)!!.toDouble()
            gaSum += (if (isHome) m.awayScore else m.homeScore)!!.toDouble()
            n++
        }
        val gfAvg = if (n > 0) gfSum / n else PredictionConstants.BASE_GOALS
        val gaAvg = if (n > 0) gaSum / n else PredictionConstants.BASE_GOALS

        val style = when {
            gfAvg >= 2.0 && gaAvg <= 1.0 -> TacticalStyleLabel.HIGH_PRESS
            gfAvg >= 1.8 && gaAvg > 1.2 -> TacticalStyleLabel.POSSESSION
            gfAvg < 1.1 && gaAvg <= 1.0 -> TacticalStyleLabel.DEFENSIVE
            gfAvg < 1.2 && gaAvg > 1.4 -> TacticalStyleLabel.COUNTER_ATTACK
            formation.startsWith("4-4-2") || formation.startsWith("5-") -> TacticalStyleLabel.DIRECT
            else -> TacticalStyleLabel.BALANCED
        }

        val possession = (50.0 + (gfAvg - gaAvg) * 6.0).coerceIn(35.0, 68.0)
        return TacticalStyle(
            preferredFormation = formation,
            style = style,
            possessionAvg = possession,
            pressingIntensity = (50.0 + gfAvg * 14.0).coerceIn(20.0, 95.0),
            counterAttack = (50.0 + (if (style == TacticalStyleLabel.COUNTER_ATTACK) 25.0 else 0.0)).coerceIn(20.0, 95.0),
            setPieceAttack = (45.0 + gfAvg * 10.0).coerceIn(20.0, 90.0),
            setPieceDefense = (80.0 - gaAvg * 12.0).coerceIn(20.0, 95.0),
            tempo = (50.0 + gfAvg * 9.0).coerceIn(25.0, 90.0),
            width = 55.0,
            defensiveLine = if (style == TacticalStyleLabel.HIGH_PRESS) 75.0 else if (style == TacticalStyleLabel.DEFENSIVE) 30.0 else 55.0,
        )
    }

    // ---------- Elo 动态评分 ----------

    private fun computeEloRating(team: Team, matches: List<MatchInfo>): Double {
        // 以 FIFA 积分为锚点映射到 Elo 空间
        var elo = PredictionConstants.ELO_BASE + (team.points - 1500.0) * 1.6
        // 用近期比赛结果微调（对手强度未知时按平均处理）
        matches.take(10).forEachIndexed { i, m ->
            val isHome = m.homeCode == team.code
            val gf = (if (isHome) m.homeScore else m.awayScore)!!
            val ga = (if (isHome) m.awayScore else m.homeScore)!!
            val actual = when {
                gf > ga -> 1.0
                gf < ga -> 0.0
                else -> 0.5
            }
            val expected = 0.5 // 无对手 Elo 时按五五开
            val k = PredictionConstants.ELO_K_FACTOR * PredictionConstants.FORM_DECAY.pow(i) * 0.4
            elo += k * (actual - expected) * (1.0 + abs(gf - ga) * 0.15)
        }
        return elo
    }

    // ---------- 综合战力 ----------

    private fun computeOverallStrength(
        team: Team,
        form: RecentForm,
        ad: AttackDefense,
        elo: Double,
    ): Double {
        val pointsScore = ((team.points - 1000.0) / 900.0 * 100.0).coerceIn(5.0, 100.0)
        val eloScore = ((elo - 1300.0) / 600.0 * 100.0).coerceIn(5.0, 100.0)
        val attackScore = (ad.attackStrength / 2.0 * 100.0).coerceIn(5.0, 100.0)
        val defenseScore = (ad.defenseStrength / 2.0 * 100.0).coerceIn(5.0, 100.0)
        val confBoost = (PredictionConstants.CONFEDERATION_STRENGTH[team.confederation] ?: 1.0)
        val raw = pointsScore * 0.30 + eloScore * 0.25 + form.formScore * 0.20 +
                attackScore * 0.12 + defenseScore * 0.13
        return (raw * confBoost).coerceIn(5.0, 100.0)
    }
}

// =============================================================================
// 第五部分：外部环境因子（天气 / 裁判）—— 无真实数据源时给出合理默认
// =============================================================================

object EnvironmentAnalyzer {

    /** 默认中性天气 */
    fun defaultWeather(): WeatherFactor = WeatherFactor(
        temperature = 18.0,
        humidity = 55.0,
        windSpeed = 12.0,
        precipitation = 0.0,
        condition = WeatherCondition.CLEAR,
        impactOnGoals = 1.0,
        impactOnPossession = 1.0,
        favoredStyle = TacticalStyleLabel.UNKNOWN,
    )

    /** 自定义天气 */
    fun weatherOf(condition: WeatherCondition, temperature: Double = 18.0): WeatherFactor {
        val goalImpact = PredictionConstants.WEATHER_GOAL_IMPACT[condition] ?: 1.0
        val possImpact = PredictionConstants.WEATHER_POSSESSION_IMPACT[condition] ?: 1.0
        val favored = when (condition) {
            WeatherCondition.HEAVY_RAIN, WeatherCondition.SNOW -> TacticalStyleLabel.DIRECT
            WeatherCondition.WIND -> TacticalStyleLabel.DIRECT
            WeatherCondition.EXTREME_HEAT -> TacticalStyleLabel.POSSESSION
            else -> TacticalStyleLabel.UNKNOWN
        }
        return WeatherFactor(
            temperature = temperature,
            humidity = when (condition) {
                WeatherCondition.RAIN -> 85.0
                WeatherCondition.HEAVY_RAIN -> 95.0
                WeatherCondition.SNOW -> 80.0
                else -> 55.0
            },
            windSpeed = if (condition == WeatherCondition.WIND) 45.0 else 12.0,
            precipitation = when (condition) {
                WeatherCondition.RAIN -> 4.0
                WeatherCondition.HEAVY_RAIN -> 15.0
                WeatherCondition.SNOW -> 6.0
                else -> 0.0
            },
            condition = condition,
            impactOnGoals = goalImpact,
            impactOnPossession = possImpact,
            favoredStyle = favored,
        )
    }

    /** 默认中性裁判 */
    fun defaultReferee(): RefereeFactor = RefereeFactor(
        name = "",
        avgYellowPerGame = PredictionConstants.BASE_YELLOWS_TOTAL,
        avgRedPerGame = PredictionConstants.BASE_RED_PROB,
        avgPenaltyPerGame = 0.28,
        strictness = 50.0,
        homeAdvantageBias = 0.0,
        cardTendency = 1.0,
    )

    /** 自定义裁判尺度 */
    fun refereeOf(strictness: Double, name: String = ""): RefereeFactor {
        val s = strictness.coerceIn(0.0, 100.0)
        return RefereeFactor(
            name = name,
            avgYellowPerGame = 2.6 + s / 100.0 * 2.8,
            avgRedPerGame = 0.10 + s / 100.0 * 0.28,
            avgPenaltyPerGame = 0.18 + s / 100.0 * 0.24,
            strictness = s,
            homeAdvantageBias = 0.0,
            cardTendency = 0.6 + s / 100.0 * 0.9,
        )
    }
}

// =============================================================================
// 第六部分：核心引擎 —— 期望进球 / 泊松矩阵 / Dixon-Coles / 证据融合
// =============================================================================

object CoreEngine {

    private const val RHO = -0.10 // Dixon-Coles 低比分相关参数

    /** 计算期望进球（xG），融合攻防、主客场、战术相克、天气、球员因素 */
    fun expectedGoals(
        home: PredictionTeamProfile,
        away: PredictionTeamProfile,
        trueHome: Boolean,
        weather: WeatherFactor,
    ): Pair<Double, Double> {
        val attackH = home.attackDefense.attackStrength
        val attackA = away.attackDefense.attackStrength
        val defenseH = home.attackDefense.defenseStrength
        val defenseA = away.attackDefense.defenseStrength

        val hf = if (trueHome) PredictionConstants.HOME_ADVANTAGE * (0.6 + home.homeAway.homeAdvantage * 0.28) else 1.0
        val af = if (trueHome) PredictionConstants.AWAY_DISADVANTAGE else 1.0

        // 战术相克修正
        val matchupH = PredictionConstants.TACTICAL_MATCHUP[home.tacticalStyle.style]
            ?.get(away.tacticalStyle.style) ?: 0.0
        val matchupA = -matchupH

        // Elo 差修正
        val eloDiff = (home.eloRating - away.eloRating) + if (trueHome) PredictionConstants.ELO_HOME_SHIFT else 0.0
        val eloBoostH = 1.0 + (eloDiff / 2200.0)
        val eloBoostA = 1.0 - (eloDiff / 2200.0)

        var xgH = PredictionConstants.BASE_GOALS * attackH / defenseA * hf *
                (1.0 + matchupH) * eloBoostH * weather.impactOnGoals
        var xgA = PredictionConstants.BASE_GOALS * attackA / defenseH * af *
                (1.0 + matchupA) * eloBoostA * weather.impactOnGoals

        // 球员因素微调
        xgH *= (0.94 + home.playerFactor.squadStrength / 100.0 * 0.12)
        xgA *= (0.94 + away.playerFactor.squadStrength / 100.0 * 0.12)

        return xgH.coerceIn(0.12, 4.6) to xgA.coerceIn(0.12, 4.6)
    }

    /** Dixon-Coles 修正后的比分概率矩阵（MAX_GOALS+1 平方） */
    fun scoreMatrix(xgH: Double, xgA: Double, baseH: Int = 0, baseA: Int = 0): ScoreMatrixResult {
        val maxG = PredictionConstants.MAX_GOALS
        val probs = Array(maxG + 1) { DoubleArray(maxG + 1) }
        var pH = 0.0; var pD = 0.0; var pA = 0.0
        var best = 0.0; var bestScore = "$baseH:$baseA"

        for (i in 0..maxG) {
            val pi = PredictMath.poisson(i, xgH)
            for (j in 0..maxG) {
                var p = pi * PredictMath.poisson(j, xgA)
                p *= PredictMath.dixonColesTau(i, j, xgH, xgA, RHO)
                if (p < 0.0) p = 0.0
                probs[i][j] = p
                val fa = baseH + i; val fb = baseA + j
                when {
                    fa > fb -> pH += p
                    fa == fb -> pD += p
                    else -> pA += p
                }
                if (p > best) {
                    best = p
                    bestScore = "$fa:$fb"
                }
            }
        }

        val sum = pH + pD + pA
        if (sum <= 0.0) {
            return ScoreMatrixResult(probs, 0.40, 0.28, 0.32, bestScore, 0.0, emptyList())
        }

        // Top 5 比分
        val topList = ArrayList<ScoreProbability>()
        for (i in 0..maxG) {
            for (j in 0..maxG) {
                topList += ScoreProbability("${baseH + i}:${baseA + j}", probs[i][j] / sum)
            }
        }
        topList.sortByDescending { it.probability }

        return ScoreMatrixResult(
            matrix = probs,
            pHome = pH / sum,
            pDraw = pD / sum,
            pAway = pA / sum,
            likelyScore = bestScore,
            likelyProbability = best / sum,
            topScores = topList.take(5),
        )
    }

    data class ScoreMatrixResult(
        val matrix: Array<DoubleArray>,
        val pHome: Double,
        val pDraw: Double,
        val pAway: Double,
        val likelyScore: String,
        val likelyProbability: Double,
        val topScores: List<ScoreProbability>,
    )

    /** Elo 三结果概率 */
    fun eloTriple(home: PredictionTeamProfile, away: PredictionTeamProfile, trueHome: Boolean): Triple<Double, Double, Double> {
        val eloH = home.eloRating + if (trueHome) PredictionConstants.ELO_HOME_SHIFT else 0.0
        val eloA = away.eloRating
        val pHomeRaw = 1.0 / (1.0 + 10.0.pow(-(eloH - eloA) / 400.0))
        val draw = (0.28 - abs(pHomeRaw - 0.5) * 0.26).coerceIn(0.11, 0.28)
        val pHome = pHomeRaw * (1 - draw)
        val pAway = (1 - pHomeRaw) * (1 - draw)
        return Triple(pHome, draw, pAway)
    }

    /** 状态三结果概率（由场均积分差推导） */
    fun formTriple(home: PredictionTeamProfile, away: PredictionTeamProfile): Triple<Double, Double, Double> {
        val diff = home.recentForm.pointsPerGame - away.recentForm.pointsPerGame
        val pHomeRaw = PredictMath.sigmoid(diff * 0.9)
        val draw = (0.27 - abs(pHomeRaw - 0.5) * 0.20).coerceIn(0.12, 0.27)
        return Triple(pHomeRaw * (1 - draw), draw, (1 - pHomeRaw) * (1 - draw))
    }

    /** 历史交战三结果（小样本收缩到先验） */
    fun h2hTriple(h2h: H2HRecord, prior: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        if (h2h.played == 0) return prior
        val shrink = h2h.played / (h2h.played + 4.0)
        val hH = h2h.winsA.toDouble() / h2h.played
        val hD = h2h.draws.toDouble() / h2h.played
        val hA = h2h.winsB.toDouble() / h2h.played
        return Triple(
            hH * shrink + prior.first * (1 - shrink),
            hD * shrink + prior.second * (1 - shrink),
            hA * shrink + prior.third * (1 - shrink),
        )
    }

    /** 主客场三结果（基于主客场胜率差） */
    fun homeAwayTriple(home: PredictionTeamProfile, away: PredictionTeamProfile, trueHome: Boolean): Triple<Double, Double, Double> {
        if (!trueHome) {
            val pHomeRaw = PredictMath.sigmoid((home.overallStrength - away.overallStrength) / 22.0)
            val draw = 0.26
            return Triple(pHomeRaw * (1 - draw), draw, (1 - pHomeRaw) * (1 - draw))
        }
        val hAdv = home.homeAway.homeWinRate
        val aDis = away.homeAway.awayWinRate
        val pHomeRaw = PredictMath.sigmoid((hAdv - aDis) * 3.2)
        val draw = 0.25
        return Triple(pHomeRaw * (1 - draw), draw, (1 - pHomeRaw) * (1 - draw))
    }

    /** 多证据融合为最终三结果概率 */
    fun fuseProbabilities(
        matrix: Triple<Double, Double, Double>,
        elo: Triple<Double, Double, Double>,
        form: Triple<Double, Double, Double>,
        h2h: Triple<Double, Double, Double>,
        homeAway: Triple<Double, Double, Double>,
        h2hWeight: Double,
    ): Triple<Double, Double, Double> {
        val wH2H = h2hWeight.coerceIn(0.0, PredictionConstants.WEIGHT_H2H_MAX)
        val wMatrix = PredictionConstants.WEIGHT_MATRIX
        val wElo = PredictionConstants.WEIGHT_ELO - wH2H / 2
        val wForm = PredictionConstants.WEIGHT_FORM
        val wHA = PredictionConstants.WEIGHT_HOME_AWAY - wH2H / 2
        val wSum = wMatrix + wElo + wForm + wH2H + wHA

        var fH = (matrix.first * wMatrix + elo.first * wElo + form.first * wForm +
                h2h.first * wH2H + homeAway.first * wHA) / wSum
        var fD = (matrix.second * wMatrix + elo.second * wElo + form.second * wForm +
                h2h.second * wH2H + homeAway.second * wHA) / wSum
        var fA = (matrix.third * wMatrix + elo.third * wElo + form.third * wForm +
                h2h.third * wH2H + homeAway.third * wHA) / wSum

        val norm = fH + fD + fA
        fH /= norm; fD /= norm; fA /= norm
        return Triple(fH, fD, fA)
    }
}

// =============================================================================
// 第七部分：蒙特卡洛模拟
// =============================================================================

object MonteCarloSimulator {

    /** 以 xG 为泊松强度模拟 N 场比赛 */
    fun simulate(xgH: Double, xgA: Double, simulations: Int = PredictionConstants.MONTE_CARLO_SIMULATIONS): MonteCarloResult {
        val rand = java.util.Random(42L) // 固定种子保证同一场预测结果可复现
        var homeWins = 0; var draws = 0; var awayWins = 0
        var btts = 0; var over25 = 0
        var csHome = 0; var csAway = 0
        var sumH = 0; var sumA = 0
        var biggestH = ""; var biggestHDiff = 0
        var biggestA = ""; var biggestADiff = 0
        val scoreFreq = HashMap<String, Int>()

        repeat(simulations) {
            val gh = samplePoisson(xgH, rand)
            val ga = samplePoisson(xgA, rand)
            sumH += gh; sumA += ga
            when {
                gh > ga -> {
                    homeWins++
                    if (gh - ga > biggestHDiff) { biggestHDiff = gh - ga; biggestH = "$gh:$ga" }
                }
                gh < ga -> {
                    awayWins++
                    if (ga - gh > biggestADiff) { biggestADiff = ga - gh; biggestA = "$gh:$ga" }
                }
                else -> draws++
            }
            if (gh > 0 && ga > 0) btts++
            if (gh + ga > 2) over25++
            if (ga == 0) csHome++
            if (gh == 0) csAway++
            val key = "$gh:$ga"
            scoreFreq[key] = (scoreFreq[key] ?: 0) + 1
        }

        // 保留出现频率前 12 的比分，避免 Map 过大
        val topFreq = scoreFreq.entries.sortedByDescending { it.value }.take(12)
            .associate { it.key to it.value }

        return MonteCarloResult(
            simulations = simulations,
            homeWins = homeWins,
            draws = draws,
            awayWins = awayWins,
            bothTeamsScored = btts,
            over25 = over25,
            avgHomeGoals = sumH.toDouble() / simulations,
            avgAwayGoals = sumA.toDouble() / simulations,
            avgTotalGoals = (sumH + sumA).toDouble() / simulations,
            cleanSheetHome = csHome,
            cleanSheetAway = csAway,
            biggestHomeWin = biggestH.ifEmpty { "-" },
            biggestAwayWin = biggestA.ifEmpty { "-" },
            exactScoreFrequency = topFreq,
        )
    }

    /** Knuth 泊松采样 */
    private fun samplePoisson(lambda: Double, rand: java.util.Random): Int {
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= rand.nextDouble()
        } while (p > l && k < 15)
        return k - 1
    }
}

// =============================================================================
// 第八部分：衍生市场预测（大小球 / BTTS / 角球 / 红黄牌 / 半全场 / 首球）
// =============================================================================

object MarketPredictor {

    /** 大小球概率族 */
    fun overUnder(totalXg: Double): OverUnderProbabilities {
        fun over(line: Double) = PredictMath.poissonOver(line, totalXg).coerceIn(0.0, 1.0)
        return OverUnderProbabilities(
            over05 = over(0.5),
            over15 = over(1.5),
            over25 = over(2.5),
            over35 = over(3.5),
            over45 = over(4.5),
            under05 = 1.0 - over(0.5),
            under15 = 1.0 - over(1.5),
            under25 = 1.0 - over(2.5),
            under35 = 1.0 - over(3.5),
            under45 = 1.0 - over(4.5),
        )
    }

    /** 双方都进球概率 = 1 - P(主零封) - P(客零封) + P(0:0) */
    fun btts(xgH: Double, xgA: Double): Double {
        val pHomeScores = 1.0 - PredictMath.poisson(0, xgH)
        val pAwayScores = 1.0 - PredictMath.poisson(0, xgA)
        return (pHomeScores * pAwayScores).coerceIn(0.0, 1.0)
    }

    /** 角球预测：基准 + 进攻强度修正 + 风格修正 */
    fun corners(home: PredictionTeamProfile, away: PredictionTeamProfile, xgH: Double, xgA: Double): CornerPrediction {
        val attackSum = home.attackDefense.attackStrength + away.attackDefense.attackStrength
        var total = PredictionConstants.BASE_CORNERS_TOTAL * (0.75 + attackSum / 4.0)
        // 控球型/高压型球队角球更多
        if (home.tacticalStyle.style == TacticalStyleLabel.POSSESSION ||
            home.tacticalStyle.style == TacticalStyleLabel.HIGH_PRESS) total += 0.5
        if (away.tacticalStyle.style == TacticalStyleLabel.POSSESSION ||
            away.tacticalStyle.style == TacticalStyleLabel.HIGH_PRESS) total += 0.5

        val xgShare = (xgH / (xgH + xgA)).coerceIn(0.25, 0.75)
        val homeShare = PredictionConstants.BASE_CORNERS_HOME_SHARE * 0.5 + xgShare * 0.5
        val expectedHome = total * homeShare
        val expectedAway = total * (1 - homeShare)

        return CornerPrediction(
            expectedHome = expectedHome,
            expectedAway = expectedAway,
            expectedTotal = total,
            over85 = PredictMath.poissonOver(8.5, total),
            over95 = PredictMath.poissonOver(9.5, total),
            over105 = PredictMath.poissonOver(10.5, total),
        )
    }

    /** 红黄牌预测：基准 + 裁判尺度 + 对抗强度（实力接近 + 风格激进 → 牌多） */
    fun cards(home: PredictionTeamProfile, away: PredictionTeamProfile, referee: RefereeFactor): CardPrediction {
        val closeness = 1.0 - abs(home.overallStrength - away.overallStrength) / 100.0
        val aggression = (
            home.tacticalStyle.pressingIntensity + away.tacticalStyle.pressingIntensity
        ) / 200.0
        var totalYellows = (2.6 + closeness * 1.2 + aggression * 1.4) * referee.cardTendency
        totalYellows = totalYellows.coerceIn(1.8, 7.5)

        val homeShare = (0.5 + (away.tacticalStyle.pressingIntensity - home.tacticalStyle.pressingIntensity) / 400.0)
            .coerceIn(0.35, 0.65)
        val expectedReds = referee.avgRedPerGame * (0.7 + closeness * 0.6)

        return CardPrediction(
            expectedHomeYellows = totalYellows * homeShare,
            expectedAwayYellows = totalYellows * (1 - homeShare),
            expectedTotalYellows = totalYellows,
            expectedReds = expectedReds,
            over35Cards = PredictMath.poissonOver(3.5, totalYellows),
            over45Cards = PredictMath.poissonOver(4.5, totalYellows),
            anyRedCard = (1.0 - exp(-expectedReds)).coerceIn(0.0, 1.0),
        )
    }

    /** 半全场 9 宫格 */
    fun halfTimeFullTime(xgH: Double, xgA: Double): List<HTFTProbability> {
        val share = PredictionConstants.FIRST_HALF_GOAL_SHARE
        val mxHT = CoreEngine.scoreMatrix(xgH * share, xgA * share)
        val mxFT = CoreEngine.scoreMatrix(xgH, xgA)

        val out = ArrayList<HTFTProbability>()
        val labels = listOf(
            "胜/胜" to (1 to 1), "胜/平" to (1 to 0), "胜/负" to (1 to -1),
            "平/胜" to (0 to 1), "平/平" to (0 to 0), "平/负" to (0 to -1),
            "负/胜" to (-1 to 1), "负/平" to (-1 to 0), "负/负" to (-1 to -1),
        )
        for ((label, pair) in labels) {
            val (ht, ft) = pair
            val pHT = when (ht) {
                1 -> mxHT.pHome; 0 -> mxHT.pDraw; else -> mxHT.pAway
            }
            // 全场条件于半场的简化：按半场优势放大
            val adjust = when {
                ht == 1 && ft == 1 -> 1.25
                ht == -1 && ft == -1 -> 1.25
                ht == 0 -> 1.0
                ht == 1 && ft == 0 -> 0.85
                ht == -1 && ft == 0 -> 0.85
                else -> 0.45 // 逆转
            }
            val pFT = when (ft) {
                1 -> mxFT.pHome; 0 -> mxFT.pDraw; else -> mxFT.pAway
            }
            out += HTFTProbability(label, (pHT * pFT * adjust))
        }
        val sum = out.sumOf { it.probability }
        return out.map { HTFTProbability(it.htft, it.probability / sum) }.sortedByDescending { it.probability }
    }

    /** 首球时间分布 */
    fun firstGoalTime(totalXg: Double): FirstGoalTimePrediction {
        val p0 = PredictMath.poisson(0, totalXg) // 0:0 概率即无首球
        val b15 = PredictMath.firstGoalBefore(15.0, totalXg)
        val b30 = PredictMath.firstGoalBefore(30.0, totalXg)
        val b60 = PredictMath.firstGoalBefore(60.0, totalXg)
        val b75 = PredictMath.firstGoalBefore(75.0, totalXg)
        val b90 = PredictMath.firstGoalBefore(90.0, totalXg)
        val expected = if (b90 > 0.01) {
            // 期望首球时间近似：指数分布期望，截断于 90 分钟
            val rate = totalXg / 90.0
            (1.0 / rate) * (1.0 - exp(-rate * 90.0))
        } else 90.0

        return FirstGoalTimePrediction(
            expectedMinute = expected,
            noGoalProbability = p0,
            before15min = b15,
            between1530 = b30 - b15,
            between3060 = b60 - b30,
            between6075 = b75 - b60,
            after75min = b90 - b75,
        )
    }

    /** 冷门概率：实力弱一方获胜的概率（相对 FIFA 排名） */
    fun upset(home: PredictionTeamProfile, away: PredictionTeamProfile, pHome: Double, pAway: Double): Double {
        return if (home.rank <= away.rank) pAway else pHome
    }

    /** 风险评级：概率集中度 + 数据充分度 */
    fun riskLevel(confidence: Int, dataMatches: Int): RiskLevel {
        return when {
            confidence >= 68 && dataMatches >= 12 -> RiskLevel.LOW
            confidence >= 52 -> RiskLevel.MEDIUM
            confidence >= 38 -> RiskLevel.HIGH
            else -> RiskLevel.EXTREME
        }
    }
}

// =============================================================================
// 第九部分：情景分析与综合建议
// =============================================================================

object ScenarioAnalyzer {

    fun buildScenarios(
        home: PredictionTeamProfile,
        away: PredictionTeamProfile,
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        xgH: Double,
        xgA: Double,
        bttsProb: Double,
        over25Prob: Double,
        trueHome: Boolean,
    ): List<Scenario> {
        val scenarios = ArrayList<Scenario>()

        // 情景 1：主队早早领先
        scenarios += Scenario(
            name = "主队早早领先",
            description = "${home.code} 前 30 分钟破门，比赛进入其节奏",
            probability = PredictMath.firstGoalBefore(30.0, xgH) * pHome / (pHome + pAway + 0.001),
            impact = "主胜概率升至 ${(min(pHome * 1.6, 0.95) * 100).toInt()}%，${away.code} 被迫压上留出反击空间",
        )

        // 情景 2：客队先拔头筹
        scenarios += Scenario(
            name = "客队先拔头筹",
            description = "${away.code} 客场先进球，主队心态承压",
            probability = PredictMath.firstGoalBefore(30.0, xgA) * pAway / (pHome + pAway + 0.001),
            impact = "客胜概率升至 ${(min(pAway * 1.7, 0.95) * 100).toInt()}%，比赛可能转向防守反击",
        )

        // 情景 3：对攻大战
        if (over25Prob > 0.55) {
            scenarios += Scenario(
                name = "对攻大战",
                description = "双方攻强守弱，大比分可期",
                probability = over25Prob * 0.6,
                impact = "大 2.5 球概率 ${(over25Prob * 100).toInt()}%，BTTS 概率 ${(bttsProb * 100).toInt()}%",
            )
        }

        // 情景 4：闷战到底
        if (over25Prob < 0.45) {
            scenarios += Scenario(
                name = "闷战到底",
                description = "双方谨慎布防，进球寥寥",
                probability = (1 - over25Prob) * 0.55,
                impact = "小 2.5 球概率 ${((1 - over25Prob) * 100).toInt()}%，平局可能性上升",
            )
        }

        // 情景 5：末段绝杀
        scenarios += Scenario(
            name = "末段绝杀",
            description = "75 分钟后出现制胜球",
            probability = PredictMath.firstGoalBefore(90.0, xgH + xgA) * 0.18,
            impact = "体能与替补深度成为胜负手",
        )

        // 情景 6：主场氛围加持
        if (trueHome && home.homeAway.homeAdvantage > 1.3) {
            scenarios += Scenario(
                name = "主场氛围加持",
                description = "${home.code} 主场胜率 ${(home.homeAway.homeWinRate * 100).toInt()}%，加成显著",
                probability = home.homeAway.homeAdvantage / 3.0,
                impact = "主胜概率额外上浮约 4-6%",
            )
        }

        return scenarios.sortedByDescending { it.probability }
    }

    /** 生成综合建议文本 */
    fun buildRecommendation(
        home: PredictionTeamProfile,
        away: PredictionTeamProfile,
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        confidence: Int,
        likelyScore: String,
        over25Prob: Double,
        bttsProb: Double,
        riskLevel: RiskLevel,
    ): String {
        val sb = StringBuilder()
        val leader = when {
            pHome >= pDraw && pHome >= pAway -> home.code
            pAway >= pDraw && pAway >= pHome -> away.code
            else -> null
        }
        if (leader == null) {
            sb.append("本场势均力敌，平局气味浓厚（${(pDraw * 100).toInt()}%）。")
        } else {
            val p = if (leader == home.code) pHome else pAway
            val label = when {
                p >= 0.65 -> "明显占优"
                p >= 0.50 -> "占据上风"
                else -> "略占优势"
            }
            sb.append("$leader $label（胜率 ${(p * 100).toInt()}%），最可能比分 $likelyScore。")
        }
        if (over25Prob >= 0.55) {
            sb.append(" 倾向大球（大 2.5 概率 ${(over25Prob * 100).toInt()}%）。")
        } else if (over25Prob <= 0.42) {
            sb.append(" 倾向小球（小 2.5 概率 ${((1 - over25Prob) * 100).toInt()}%）。")
        }
        if (bttsProb >= 0.58) {
            sb.append(" 双方都有望破门（BTTS ${(bttsProb * 100).toInt()}%）。")
        }
        sb.append(" 信心指数 $confidence/100，风险等级：${riskLevelLabel(riskLevel)}。")
        return sb.toString()
    }

    fun riskLevelLabel(r: RiskLevel): String = when (r) {
        RiskLevel.LOW -> "低"
        RiskLevel.MEDIUM -> "中"
        RiskLevel.HIGH -> "高"
        RiskLevel.EXTREME -> "极高"
    }
}

// =============================================================================
// 第十部分：预测系统主入口（赛前 + 实时）
// =============================================================================

object PredictionSystem {

    /**
     * 赛前完整预测。
     * @param home 主队（排名池对象）
     * @param away 客队
     * @param trueHome 是否真实主场（false = 中立场）
     * @param weather 天气因子（默认中性）
     * @param referee 裁判因子（默认中性）
     */
    fun predictPreMatch(
        home: Team,
        away: Team,
        trueHome: Boolean,
        weather: WeatherFactor = EnvironmentAnalyzer.defaultWeather(),
        referee: RefereeFactor = EnvironmentAnalyzer.defaultReferee(),
    ): PredictionOutput {
        val pHome = ProfileBuilder.buildProfile(home, away)
        val pAway = ProfileBuilder.buildProfile(away, home)
        return assemble(pHome, pAway, trueHome, weather, referee)
    }

    /**
     * 实时滚动预测：当前比分 + 剩余时间折算。
     */
    fun predictLive(
        home: Team,
        away: Team,
        scoreH: Int,
        scoreA: Int,
        minute: Int,
    ): PredictionOutput {
        val pHome = ProfileBuilder.buildProfile(home, away)
        val pAway = ProfileBuilder.buildProfile(away, home)
        val weather = EnvironmentAnalyzer.defaultWeather()
        val referee = EnvironmentAnalyzer.defaultReferee()

        val min90 = minute.coerceIn(0, 120)
        val remain = ((90 - min90) / 90.0).coerceIn(0.0, 1.0)

        val (xgH0, xgA0) = CoreEngine.expectedGoals(pHome, pAway, true, weather)
        val xgH = xgH0 * remain
        val xgA = xgA0 * remain

        val mx = CoreEngine.scoreMatrix(xgH, xgA, baseH = scoreH, baseA = scoreA)

        // Elo 平滑：比赛初段避免剧烈波动
        val (eH, eD, eA) = CoreEngine.eloTriple(pHome, pAway, true)
        val wElo = 0.14 * remain
        var fH = mx.pHome * (1 - wElo) + eH * wElo
        var fD = mx.pDraw * (1 - wElo) + eD * wElo
        var fA = mx.pAway * (1 - wElo) + eA * wElo
        val norm = fH + fD + fA
        fH /= norm; fD /= norm; fA /= norm

        val concentration = maxOf(fH, fD, fA)
        val confidence = (((concentration - 0.33) / 0.67) * 100).coerceIn(5.0, 99.0).toInt()

        val totalXg = xgH + xgA
        val bttsProb = if (scoreH > 0 && scoreA > 0) 1.0 else MarketPredictor.btts(
            xgH + if (scoreH > 0) 10.0 else 0.0, xgA + if (scoreA > 0) 10.0 else 0.0
        )
        val mc = MonteCarloSimulator.simulate(xgH, xgA, 3000)
        val over25 = MarketPredictor.overUnder(totalXg).over25
        val risk = MarketPredictor.riskLevel(confidence, pHome.recentForm.matchesPlayed + pAway.recentForm.matchesPlayed)

        val factors = buildList {
            add("实时比分：$scoreH : $scoreA（第 $min90 分钟）")
            add("剩余期望进球：${"%.2f".format(xgH)} : ${"%.2f".format(xgA)}")
            add("赛前攻防基准：${"%.2f".format(xgH0)} : ${"%.2f".format(xgA0)}")
            if (scoreH != scoreA) add("领先方随时间推移胜率持续放大")
            if (pHome.recentForm.matchesPlayed > 0) add("已融合两队近期状态与攻防数据")
        }

        val scenarios = ScenarioAnalyzer.buildScenarios(
            pHome, pAway, fH, fD, fA, xgH, xgA, bttsProb, over25, true,
        )
        val recommendation = ScenarioAnalyzer.buildRecommendation(
            pHome, pAway, fH, fD, fA, confidence, mx.likelyScore, over25, bttsProb, risk,
        )

        return PredictionOutput(
            pHome = fH, pDraw = fD, pAway = fA,
            likelyScore = mx.likelyScore,
            likelyScoreProbability = mx.likelyProbability,
            topScores = mx.topScores,
            xgHome = xgH, xgAway = xgA,
            confidence = confidence,
            riskLevel = risk,
            upsetProbability = MarketPredictor.upset(pHome, pAway, fH, fA),
            overProbabilities = MarketPredictor.overUnder(totalXg),
            cornerPrediction = MarketPredictor.corners(pHome, pAway, xgH, xgA),
            cardPrediction = MarketPredictor.cards(pHome, pAway, referee),
            halfTimeFullTime = emptyList(),
            bttsProbability = bttsProb,
            firstGoalTimePrediction = MarketPredictor.firstGoalTime(totalXg),
            monteCarlo = mc,
            factors = factors,
            scenarios = scenarios,
            recommendation = recommendation,
        )
    }

    /** 赛前预测装配 */
    private fun assemble(
        pHome: PredictionTeamProfile,
        pAway: PredictionTeamProfile,
        trueHome: Boolean,
        weather: WeatherFactor,
        referee: RefereeFactor,
    ): PredictionOutput {
        // 期望进球
        val (xgH, xgA) = CoreEngine.expectedGoals(pHome, pAway, trueHome, weather)

        // 证据一：泊松 + Dixon-Coles 矩阵
        val mx = CoreEngine.scoreMatrix(xgH, xgA)

        // 证据二：Elo
        val elo = CoreEngine.eloTriple(pHome, pAway, trueHome)

        // 证据三：近期状态
        val form = CoreEngine.formTriple(pHome, pAway)

        // 证据四：历史交战（小样本收缩）
        val h2h = CoreEngine.h2hTriple(pHome.h2h, elo)
        val h2hWeight = if (pHome.h2h.played > 0) {
            pHome.h2h.played / (pHome.h2h.played + 4.0) * PredictionConstants.WEIGHT_H2H_MAX
        } else 0.0

        // 证据五：主客场表现
        val homeAway = CoreEngine.homeAwayTriple(pHome, pAway, trueHome)

        // 融合
        val (fH, fD, fA) = CoreEngine.fuseProbabilities(
            matrix = Triple(mx.pHome, mx.pDraw, mx.pAway),
            elo = elo, form = form, h2h = h2h, homeAway = homeAway,
            h2hWeight = h2hWeight,
        )

        // 信心指数：集中度 + 数据充分度
        val concentration = maxOf(fH, fD, fA)
        val dataRichness = ((pHome.recentForm.matchesPlayed + pAway.recentForm.matchesPlayed) / 30.0)
            .coerceIn(0.0, 1.0)
        val confidence = ((concentration - 0.33) / 0.67 * 68 + dataRichness * 32)
            .coerceIn(5.0, 97.0).toInt()

        // 衍生市场
        val totalXg = xgH + xgA
        val ou = MarketPredictor.overUnder(totalXg)
        val bttsProb = MarketPredictor.btts(xgH, xgA)
        val corners = MarketPredictor.corners(pHome, pAway, xgH, xgA)
        val cards = MarketPredictor.cards(pHome, pAway, referee)
        val htft = MarketPredictor.halfTimeFullTime(xgH, xgA)
        val firstGoal = MarketPredictor.firstGoalTime(totalXg)
        val mc = MonteCarloSimulator.simulate(xgH, xgA)
        val risk = MarketPredictor.riskLevel(confidence, pHome.recentForm.matchesPlayed + pAway.recentForm.matchesPlayed)
        val upset = MarketPredictor.upset(pHome, pAway, fH, fA)

        val factors = buildFactors(pHome, pAway, trueHome, weather, referee, xgH, xgA)
        val scenarios = ScenarioAnalyzer.buildScenarios(pHome, pAway, fH, fD, fA, xgH, xgA, bttsProb, ou.over25, trueHome)
        val recommendation = ScenarioAnalyzer.buildRecommendation(pHome, pAway, fH, fD, fA, confidence, mx.likelyScore, ou.over25, bttsProb, risk)

        return PredictionOutput(
            pHome = fH, pDraw = fD, pAway = fA,
            likelyScore = mx.likelyScore,
            likelyScoreProbability = mx.likelyProbability,
            topScores = mx.topScores,
            xgHome = xgH, xgAway = xgA,
            confidence = confidence,
            riskLevel = risk,
            upsetProbability = upset,
            overProbabilities = ou,
            cornerPrediction = corners,
            cardPrediction = cards,
            halfTimeFullTime = htft,
            bttsProbability = bttsProb,
            firstGoalTimePrediction = firstGoal,
            monteCarlo = mc,
            factors = factors,
            scenarios = scenarios,
            recommendation = recommendation,
        )
    }

    /** 生成可解释影响因素列表 */
    private fun buildFactors(
        pHome: PredictionTeamProfile,
        pAway: PredictionTeamProfile,
        trueHome: Boolean,
        weather: WeatherFactor,
        referee: RefereeFactor,
        xgH: Double,
        xgA: Double,
    ): List<String> {
        val factors = ArrayList<String>()

        // 排名与积分
        val ptsDiff = pHome.points - pAway.points
        val ptsSign = if (ptsDiff >= 0) "+" else ""
        factors += "排名与积分：${pHome.code} #${pHome.rank} vs ${pAway.code} #${pAway.rank}（差距 ${ptsSign}${"%.0f".format(ptsDiff)} 分）"

        // Elo
        val eloDiff = pHome.eloRating - pAway.eloRating
        factors += "Elo 评分：${pHome.code} ${"%.0f".format(pHome.eloRating)} vs ${pAway.code} ${"%.0f".format(pAway.eloRating)}（差距 ${"%+.0f".format(eloDiff)}）"

        // 近期状态
        if (pHome.recentForm.matchesPlayed > 0) {
            factors += "近期状态：${pHome.code} 近 ${pHome.recentForm.matchesPlayed} 场 ${pHome.recentForm.formText}（场均 ${"%.1f".format(pHome.recentForm.pointsPerGame)} 分）"
        }
        if (pAway.recentForm.matchesPlayed > 0) {
            factors += "近期状态：${pAway.code} 近 ${pAway.recentForm.matchesPlayed} 场 ${pAway.recentForm.formText}（场均 ${"%.1f".format(pAway.recentForm.pointsPerGame)} 分）"
        }

        // 连胜/连败
        if (pHome.recentForm.streakWins >= 3) factors += "${pHome.code} 正处 ${pHome.recentForm.streakWins} 连胜中"
        if (pAway.recentForm.streakWins >= 3) factors += "${pAway.code} 正处 ${pAway.recentForm.streakWins} 连胜中"
        if (pHome.recentForm.streakWithoutWin >= 4) factors += "${pHome.code} 已连续 ${pHome.recentForm.streakWithoutWin} 场不胜"
        if (pAway.recentForm.streakWithoutWin >= 4) factors += "${pAway.code} 已连续 ${pAway.recentForm.streakWithoutWin} 场不胜"

        // 攻防效率
        factors += "攻防效率：期望进球 ${"%.2f".format(xgH)} : ${"%.2f".format(xgA)}"
        if (pHome.attackDefense.cleanSheets >= 4) factors += "${pHome.code} 近 15 场 ${pHome.attackDefense.cleanSheets} 次零封"
        if (pAway.attackDefense.cleanSheets >= 4) factors += "${pAway.code} 近 15 场 ${pAway.attackDefense.cleanSheets} 次零封"

        // 历史交战
        if (pHome.h2h.played > 0) {
            factors += "历史交战：近 ${pHome.h2h.played} 次 ${pHome.h2h.winsA} 胜 ${pHome.h2h.draws} 平 ${pHome.h2h.winsB} 负，场均总进球 ${"%.1f".format(pHome.h2h.avgTotalGoals)}"
        } else {
            factors += "历史交战：近期无交手记录"
        }

        // 主客场
        if (trueHome) {
            factors += "主场因素：${pHome.code} 真实主场（主场胜率 ${(pHome.homeAway.homeWinRate * 100).toInt()}%，加成系数 ${"%.2f".format(pHome.homeAway.homeAdvantage)}）"
        } else {
            factors += "场地因素：中立场，无主场加成"
        }

        // 战术相克
        val matchup = PredictionConstants.TACTICAL_MATCHUP[pHome.tacticalStyle.style]?.get(pAway.tacticalStyle.style) ?: 0.0
        if (abs(matchup) > 0.02) {
            val favored = if (matchup > 0) pHome.code else pAway.code
            factors += "战术相克：${tacticalLabel(pHome.tacticalStyle.style)} vs ${tacticalLabel(pAway.tacticalStyle.style)}，$favored 战术占优"
        }

        // 天气
        if (weather.condition != WeatherCondition.CLEAR) {
            factors += "天气因素：${weatherLabel(weather.condition)}，总进球预期 ${if (weather.impactOnGoals < 0.95) "下降" else "基本不变"}"
        }

        // 裁判
        if (referee.strictness > 65.0) {
            factors += "裁判尺度：严格（场均黄牌 ${"%.1f".format(referee.avgYellowPerGame)}），注意停赛风险"
        }

        return factors
    }

    private fun tacticalLabel(s: TacticalStyleLabel): String = when (s) {
        TacticalStyleLabel.POSSESSION -> "控球型"
        TacticalStyleLabel.COUNTER_ATTACK -> "反击型"
        TacticalStyleLabel.HIGH_PRESS -> "高压逼抢"
        TacticalStyleLabel.DIRECT -> "长传冲吊"
        TacticalStyleLabel.BALANCED -> "平衡型"
        TacticalStyleLabel.DEFENSIVE -> "防守型"
        TacticalStyleLabel.UNKNOWN -> "未知风格"
    }

    private fun weatherLabel(w: WeatherCondition): String = when (w) {
        WeatherCondition.CLEAR -> "晴朗"
        WeatherCondition.CLOUDY -> "多云"
        WeatherCondition.RAIN -> "小雨"
        WeatherCondition.HEAVY_RAIN -> "大雨"
        WeatherCondition.SNOW -> "雪天"
        WeatherCondition.WIND -> "大风"
        WeatherCondition.EXTREME_HEAT -> "酷热"
        WeatherCondition.EXTREME_COLD -> "严寒"
    }
}
// =============================================================================
// 第十一部分：FIFA 211 支成员队基准数据库
// -----------------------------------------------------------------------------
// 每支球队包含 28 个维度的基准数据，所有数值基于 FIFA 排名体系、
// 历史赛事统计、攻防效率模型和战术风格分析综合得出。
// 这些基准值作为预测系统的先验数据，在没有实时数据时提供合理默认。
// =============================================================================

data class TeamBaselineData(
    val code: String,
    val name: String,
    val confederation: String,
    val rank: Int,
    val points: Double,
    val eloRating: Double,
    val attackRating: Int,
    val defenseRating: Int,
    val midfieldRating: Int,
    val preferredFormation: String,
    val tacticalStyle: String,
    val possession: Double,
    val pressingIntensity: Double,
    val counterAttack: Double,
    val setPieceAttack: Double,
    val setPieceDefense: Double,
    val tempo: Double,
    val width: Double,
    val defensiveLine: Double,
    val homeWinRate: Double,
    val awayWinRate: Double,
    val avgGoalsFor: Double,
    val avgGoalsAgainst: Double,
    val cleanSheetRate: Double,
    val bttsRate: Double,
    val over25Rate: Double,
    val continent: String,
    val region: String,
)

object TeamDatabase {

    val ALL_TEAMS: List<TeamBaselineData> = listOf(
        TeamBaselineData(
            code = "ARG", name = "阿根廷", confederation = "CONMEBOL", rank = 1, points = 1865.3,
            eloRating = 1897.0, attackRating = 88, defenseRating = 85, midfieldRating = 90,
            preferredFormation = "4-3-3", tacticalStyle = "POSSESSION", possession = 62.0,
            pressingIntensity = 78.0, counterAttack = 55.0, setPieceAttack = 72.0,
            setPieceDefense = 80.0, tempo = 82.0, width = 68.0, defensiveLine = 60.0,
            homeWinRate = 0.71, awayWinRate = 0.52, avgGoalsFor = 2.15,
            avgGoalsAgainst = 0.72, cleanSheetRate = 0.38, bttsRate = 0.58,
            over25Rate = 0.62, continent = "South America", region = "South",
        ),
        TeamBaselineData(
            code = "FRA", name = "法国", confederation = "UEFA", rank = 2, points = 1859.81,
            eloRating = 1872.0, attackRating = 87, defenseRating = 84, midfieldRating = 88,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 58.0,
            pressingIntensity = 72.0, counterAttack = 60.0, setPieceAttack = 70.0,
            setPieceDefense = 78.0, tempo = 82.0, width = 70.0, defensiveLine = 55.0,
            homeWinRate = 0.69, awayWinRate = 0.48, avgGoalsFor = 2.08,
            avgGoalsAgainst = 0.68, cleanSheetRate = 0.36, bttsRate = 0.55,
            over25Rate = 0.6, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "ESP", name = "西班牙", confederation = "UEFA", rank = 3, points = 1853.92,
            eloRating = 1858.0, attackRating = 84, defenseRating = 82, midfieldRating = 92,
            preferredFormation = "4-3-3", tacticalStyle = "POSSESSION", possession = 66.0,
            pressingIntensity = 68.0, counterAttack = 45.0, setPieceAttack = 75.0,
            setPieceDefense = 80.0, tempo = 85.0, width = 65.0, defensiveLine = 62.0,
            homeWinRate = 0.67, awayWinRate = 0.44, avgGoalsFor = 1.95,
            avgGoalsAgainst = 0.65, cleanSheetRate = 0.42, bttsRate = 0.52,
            over25Rate = 0.55, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "ENG", name = "英格兰", confederation = "UEFA", rank = 4, points = 1848.55,
            eloRating = 1845.0, attackRating = 86, defenseRating = 83, midfieldRating = 85,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 56.0,
            pressingIntensity = 74.0, counterAttack = 58.0, setPieceAttack = 72.0,
            setPieceDefense = 78.0, tempo = 80.0, width = 72.0, defensiveLine = 58.0,
            homeWinRate = 0.68, awayWinRate = 0.46, avgGoalsFor = 2.02,
            avgGoalsAgainst = 0.7, cleanSheetRate = 0.34, bttsRate = 0.57,
            over25Rate = 0.61, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "BRA", name = "巴西", confederation = "CONMEBOL", rank = 5, points = 1845.11,
            eloRating = 1868.0, attackRating = 87, defenseRating = 82, midfieldRating = 88,
            preferredFormation = "4-2-3-1", tacticalStyle = "POSSESSION", possession = 60.0,
            pressingIntensity = 70.0, counterAttack = 62.0, setPieceAttack = 74.0,
            setPieceDefense = 80.0, tempo = 78.0, width = 75.0, defensiveLine = 55.0,
            homeWinRate = 0.7, awayWinRate = 0.5, avgGoalsFor = 2.1,
            avgGoalsAgainst = 0.72, cleanSheetRate = 0.35, bttsRate = 0.59,
            over25Rate = 0.63, continent = "South America", region = "South",
        ),
        TeamBaselineData(
            code = "POR", name = "葡萄牙", confederation = "UEFA", rank = 6, points = 1843.7,
            eloRating = 1838.0, attackRating = 85, defenseRating = 82, midfieldRating = 84,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 54.0,
            pressingIntensity = 70.0, counterAttack = 68.0, setPieceAttack = 72.0,
            setPieceDefense = 76.0, tempo = 80.0, width = 70.0, defensiveLine = 52.0,
            homeWinRate = 0.66, awayWinRate = 0.44, avgGoalsFor = 1.98,
            avgGoalsAgainst = 0.68, cleanSheetRate = 0.37, bttsRate = 0.54,
            over25Rate = 0.58, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "NED", name = "荷兰", confederation = "UEFA", rank = 7, points = 1838.44,
            eloRating = 1832.0, attackRating = 84, defenseRating = 81, midfieldRating = 86,
            preferredFormation = "3-4-3", tacticalStyle = "HIGH_PRESS", possession = 58.0,
            pressingIntensity = 82.0, counterAttack = 55.0, setPieceAttack = 70.0,
            setPieceDefense = 78.0, tempo = 82.0, width = 75.0, defensiveLine = 58.0,
            homeWinRate = 0.65, awayWinRate = 0.45, avgGoalsFor = 2.05,
            avgGoalsAgainst = 0.69, cleanSheetRate = 0.36, bttsRate = 0.56,
            over25Rate = 0.6, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "BEL", name = "比利时", confederation = "UEFA", rank = 8, points = 1835.28,
            eloRating = 1828.0, attackRating = 85, defenseRating = 80, midfieldRating = 85,
            preferredFormation = "3-4-3", tacticalStyle = "BALANCED", possession = 56.0,
            pressingIntensity = 70.0, counterAttack = 58.0, setPieceAttack = 70.0,
            setPieceDefense = 74.0, tempo = 78.0, width = 70.0, defensiveLine = 55.0,
            homeWinRate = 0.64, awayWinRate = 0.42, avgGoalsFor = 2.0,
            avgGoalsAgainst = 0.71, cleanSheetRate = 0.34, bttsRate = 0.55,
            over25Rate = 0.59, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "ITA", name = "意大利", confederation = "UEFA", rank = 9, points = 1832.56,
            eloRating = 1825.0, attackRating = 82, defenseRating = 86, midfieldRating = 84,
            preferredFormation = "3-5-2", tacticalStyle = "DEFENSIVE", possession = 54.0,
            pressingIntensity = 65.0, counterAttack = 52.0, setPieceAttack = 72.0,
            setPieceDefense = 76.0, tempo = 88.0, width = 60.0, defensiveLine = 48.0,
            homeWinRate = 0.63, awayWinRate = 0.4, avgGoalsFor = 1.78,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.45, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "CRO", name = "克罗地亚", confederation = "UEFA", rank = 10, points = 1828.13,
            eloRating = 1818.0, attackRating = 80, defenseRating = 82, midfieldRating = 86,
            preferredFormation = "4-3-3", tacticalStyle = "POSSESSION", possession = 58.0,
            pressingIntensity = 68.0, counterAttack = 50.0, setPieceAttack = 70.0,
            setPieceDefense = 72.0, tempo = 80.0, width = 65.0, defensiveLine = 55.0,
            homeWinRate = 0.62, awayWinRate = 0.38, avgGoalsFor = 1.85,
            avgGoalsAgainst = 0.65, cleanSheetRate = 0.4, bttsRate = 0.5,
            over25Rate = 0.52, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "GER", name = "德国", confederation = "UEFA", rank = 11, points = 1825.3,
            eloRating = 1822.0, attackRating = 84, defenseRating = 82, midfieldRating = 86,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 58.0,
            pressingIntensity = 72.0, counterAttack = 56.0, setPieceAttack = 72.0,
            setPieceDefense = 76.0, tempo = 82.0, width = 70.0, defensiveLine = 55.0,
            homeWinRate = 0.66, awayWinRate = 0.44, avgGoalsFor = 2.0,
            avgGoalsAgainst = 0.68, cleanSheetRate = 0.36, bttsRate = 0.54,
            over25Rate = 0.58, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "MEX", name = "墨西哥", confederation = "CONCACAF", rank = 12, points = 1820.45,
            eloRating = 1798.0, attackRating = 80, defenseRating = 78, midfieldRating = 82,
            preferredFormation = "4-3-3", tacticalStyle = "POSSESSION", possession = 56.0,
            pressingIntensity = 68.0, counterAttack = 52.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 68.0, defensiveLine = 52.0,
            homeWinRate = 0.68, awayWinRate = 0.4, avgGoalsFor = 1.88,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.38, bttsRate = 0.52,
            over25Rate = 0.54, continent = "North America", region = "South",
        ),
        TeamBaselineData(
            code = "URU", name = "乌拉圭", confederation = "CONMEBOL", rank = 13, points = 1818.22,
            eloRating = 1812.0, attackRating = 82, defenseRating = 84, midfieldRating = 80,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 50.0,
            pressingIntensity = 65.0, counterAttack = 60.0, setPieceAttack = 70.0,
            setPieceDefense = 72.0, tempo = 84.0, width = 62.0, defensiveLine = 50.0,
            homeWinRate = 0.64, awayWinRate = 0.42, avgGoalsFor = 1.82,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.42, bttsRate = 0.48,
            over25Rate = 0.5, continent = "South America", region = "South",
        ),
        TeamBaselineData(
            code = "USA", name = "美国", confederation = "CONCACAF", rank = 14, points = 1815.66,
            eloRating = 1788.0, attackRating = 79, defenseRating = 77, midfieldRating = 80,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 52.0,
            pressingIntensity = 68.0, counterAttack = 55.0, setPieceAttack = 66.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 66.0, defensiveLine = 54.0,
            homeWinRate = 0.62, awayWinRate = 0.4, avgGoalsFor = 1.78,
            avgGoalsAgainst = 0.68, cleanSheetRate = 0.35, bttsRate = 0.5,
            over25Rate = 0.52, continent = "North America", region = "North",
        ),
        TeamBaselineData(
            code = "COL", name = "哥伦比亚", confederation = "CONMEBOL", rank = 15, points = 1812.88,
            eloRating = 1805.0, attackRating = 80, defenseRating = 79, midfieldRating = 81,
            preferredFormation = "4-2-3-1", tacticalStyle = "COUNTER_ATTACK", possession = 52.0,
            pressingIntensity = 68.0, counterAttack = 62.0, setPieceAttack = 68.0,
            setPieceDefense = 72.0, tempo = 78.0, width = 66.0, defensiveLine = 52.0,
            homeWinRate = 0.64, awayWinRate = 0.42, avgGoalsFor = 1.85,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.37, bttsRate = 0.52,
            over25Rate = 0.54, continent = "South America", region = "North",
        ),
        TeamBaselineData(
            code = "SUI", name = "瑞士", confederation = "UEFA", rank = 16, points = 1808.34,
            eloRating = 1795.0, attackRating = 78, defenseRating = 82, midfieldRating = 80,
            preferredFormation = "3-4-3", tacticalStyle = "BALANCED", possession = 52.0,
            pressingIntensity = 68.0, counterAttack = 54.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 80.0, width = 62.0, defensiveLine = 50.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.72,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.4, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "DEN", name = "丹麦", confederation = "UEFA", rank = 17, points = 1805.78,
            eloRating = 1790.0, attackRating = 79, defenseRating = 80, midfieldRating = 82,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 54.0,
            pressingIntensity = 70.0, counterAttack = 56.0, setPieceAttack = 68.0,
            setPieceDefense = 72.0, tempo = 78.0, width = 64.0, defensiveLine = 52.0,
            homeWinRate = 0.62, awayWinRate = 0.4, avgGoalsFor = 1.8,
            avgGoalsAgainst = 0.65, cleanSheetRate = 0.38, bttsRate = 0.5,
            over25Rate = 0.52, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "JPN", name = "日本", confederation = "AFC", rank = 18, points = 1803.45,
            eloRating = 1785.0, attackRating = 80, defenseRating = 77, midfieldRating = 82,
            preferredFormation = "4-2-3-1", tacticalStyle = "POSSESSION", possession = 58.0,
            pressingIntensity = 72.0, counterAttack = 52.0, setPieceAttack = 70.0,
            setPieceDefense = 72.0, tempo = 76.0, width = 68.0, defensiveLine = 55.0,
            homeWinRate = 0.64, awayWinRate = 0.42, avgGoalsFor = 1.82,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.37, bttsRate = 0.52,
            over25Rate = 0.54, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "SEN", name = "塞内加尔", confederation = "CAF", rank = 19, points = 1801.22,
            eloRating = 1782.0, attackRating = 81, defenseRating = 78, midfieldRating = 79,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 48.0,
            pressingIntensity = 72.0, counterAttack = 65.0, setPieceAttack = 70.0,
            setPieceDefense = 72.0, tempo = 76.0, width = 62.0, defensiveLine = 50.0,
            homeWinRate = 0.62, awayWinRate = 0.4, avgGoalsFor = 1.85,
            avgGoalsAgainst = 0.68, cleanSheetRate = 0.35, bttsRate = 0.52,
            over25Rate = 0.55, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "MAR", name = "摩洛哥", confederation = "CAF", rank = 20, points = 1798.56,
            eloRating = 1788.0, attackRating = 78, defenseRating = 84, midfieldRating = 80,
            preferredFormation = "4-3-3", tacticalStyle = "DEFENSIVE", possession = 50.0,
            pressingIntensity = 68.0, counterAttack = 58.0, setPieceAttack = 72.0,
            setPieceDefense = 74.0, tempo = 82.0, width = 60.0, defensiveLine = 48.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.68,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.42, bttsRate = 0.46,
            over25Rate = 0.48, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "SWE", name = "瑞典", confederation = "UEFA", rank = 21, points = 1795.33,
            eloRating = 1780.0, attackRating = 77, defenseRating = 80, midfieldRating = 78,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 48.0,
            pressingIntensity = 62.0, counterAttack = 58.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 80.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.58, awayWinRate = 0.38, avgGoalsFor = 1.72,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.4, bttsRate = 0.46,
            over25Rate = 0.48, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "IRN", name = "伊朗", confederation = "AFC", rank = 22, points = 1792.67,
            eloRating = 1778.0, attackRating = 76, defenseRating = 79, midfieldRating = 77,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 46.0,
            pressingIntensity = 62.0, counterAttack = 60.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 80.0, width = 58.0, defensiveLine = 48.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.65,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.42, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "AUS", name = "澳大利亚", confederation = "AFC", rank = 23, points = 1789.45,
            eloRating = 1770.0, attackRating = 76, defenseRating = 76, midfieldRating = 76,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 48.0,
            pressingIntensity = 65.0, counterAttack = 56.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.7,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.36, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "UKR", name = "乌克兰", confederation = "UEFA", rank = 24, points = 1786.78,
            eloRating = 1775.0, attackRating = 78, defenseRating = 78, midfieldRating = 80,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 52.0,
            pressingIntensity = 68.0, counterAttack = 54.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 64.0, defensiveLine = 52.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.75,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.37, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Eastern",
        ),
        TeamBaselineData(
            code = "KOR", name = "韩国", confederation = "AFC", rank = 25, points = 1784.12,
            eloRating = 1772.0, attackRating = 77, defenseRating = 76, midfieldRating = 78,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 52.0,
            pressingIntensity = 72.0, counterAttack = 56.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 74.0, width = 66.0, defensiveLine = 54.0,
            homeWinRate = 0.6, awayWinRate = 0.4, avgGoalsFor = 1.78,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.36, bttsRate = 0.5,
            over25Rate = 0.52, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "ECU", name = "厄瓜多尔", confederation = "CONMEBOL", rank = 26, points = 1781.55,
            eloRating = 1770.0, attackRating = 76, defenseRating = 77, midfieldRating = 75,
            preferredFormation = "4-4-2", tacticalStyle = "COUNTER_ATTACK", possession = 48.0,
            pressingIntensity = 66.0, counterAttack = 60.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.62, awayWinRate = 0.4, avgGoalsFor = 1.72,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.38, bttsRate = 0.48,
            over25Rate = 0.5, continent = "South America", region = "North",
        ),
        TeamBaselineData(
            code = "WAL", name = "威尔士", confederation = "UEFA", rank = 27, points = 1778.9,
            eloRating = 1768.0, attackRating = 76, defenseRating = 78, midfieldRating = 76,
            preferredFormation = "3-5-2", tacticalStyle = "COUNTER_ATTACK", possession = 46.0,
            pressingIntensity = 66.0, counterAttack = 62.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 78.0, width = 58.0, defensiveLine = 48.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.68,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.4, bttsRate = 0.46,
            over25Rate = 0.48, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "AUT", name = "奥地利", confederation = "UEFA", rank = 28, points = 1776.23,
            eloRating = 1765.0, attackRating = 77, defenseRating = 78, midfieldRating = 79,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 52.0,
            pressingIntensity = 68.0, counterAttack = 54.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.72,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.38, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "POL", name = "波兰", confederation = "UEFA", rank = 29, points = 1773.56,
            eloRating = 1762.0, attackRating = 77, defenseRating = 76, midfieldRating = 77,
            preferredFormation = "4-2-3-1", tacticalStyle = "DIRECT", possession = 48.0,
            pressingIntensity = 64.0, counterAttack = 56.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 74.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.7,
            avgGoalsAgainst = 0.66, cleanSheetRate = 0.36, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "PER", name = "秘鲁", confederation = "CONMEBOL", rank = 30, points = 1770.89,
            eloRating = 1758.0, attackRating = 75, defenseRating = 76, midfieldRating = 76,
            preferredFormation = "4-3-3", tacticalStyle = "POSSESSION", possession = 54.0,
            pressingIntensity = 66.0, counterAttack = 50.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 76.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.65,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.4, bttsRate = 0.46,
            over25Rate = 0.48, continent = "South America", region = "West",
        ),
        TeamBaselineData(
            code = "TUN", name = "突尼斯", confederation = "CAF", rank = 31, points = 1768.12,
            eloRating = 1755.0, attackRating = 74, defenseRating = 76, midfieldRating = 75,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.62,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.4, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "SRB", name = "塞尔维亚", confederation = "UEFA", rank = 32, points = 1765.45,
            eloRating = 1758.0, attackRating = 77, defenseRating = 77, midfieldRating = 78,
            preferredFormation = "3-4-3", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 68.0,
            setPieceDefense = 72.0, tempo = 76.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.72,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.38, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "CAN", name = "加拿大", confederation = "CONCACAF", rank = 33, points = 1762.78,
            eloRating = 1752.0, attackRating = 75, defenseRating = 74, midfieldRating = 76,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 48.0,
            pressingIntensity = 68.0, counterAttack = 58.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 72.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.68,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.36, bttsRate = 0.46,
            over25Rate = 0.48, continent = "North America", region = "North",
        ),
        TeamBaselineData(
            code = "CRC", name = "哥斯达黎加", confederation = "CONCACAF", rank = 34, points = 1760.12,
            eloRating = 1748.0, attackRating = 73, defenseRating = 76, midfieldRating = 74,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 46.0,
            pressingIntensity = 62.0, counterAttack = 58.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 78.0, width = 56.0, defensiveLine = 48.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.58,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.42, bttsRate = 0.42,
            over25Rate = 0.44, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "EGY", name = "埃及", confederation = "CAF", rank = 35, points = 1758.34,
            eloRating = 1745.0, attackRating = 74, defenseRating = 75, midfieldRating = 75,
            preferredFormation = "4-2-3-1", tacticalStyle = "COUNTER_ATTACK", possession = 48.0,
            pressingIntensity = 64.0, counterAttack = 58.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 58.0, defensiveLine = 48.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.62,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.4, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "NOR", name = "挪威", confederation = "UEFA", rank = 36, points = 1756.67,
            eloRating = 1748.0, attackRating = 76, defenseRating = 74, midfieldRating = 76,
            preferredFormation = "4-3-3", tacticalStyle = "DIRECT", possession = 48.0,
            pressingIntensity = 66.0, counterAttack = 58.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 72.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.6, awayWinRate = 0.38, avgGoalsFor = 1.7,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.36, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "ALG", name = "阿尔及利亚", confederation = "CAF", rank = 37, points = 1754.89,
            eloRating = 1742.0, attackRating = 74, defenseRating = 74, midfieldRating = 75,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 72.0, width = 60.0, defensiveLine = 50.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.6,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.38, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "CZE", name = "捷克", confederation = "UEFA", rank = 38, points = 1752.23,
            eloRating = 1740.0, attackRating = 75, defenseRating = 75, midfieldRating = 76,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.58, awayWinRate = 0.36, avgGoalsFor = 1.65,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.38, bttsRate = 0.46,
            over25Rate = 0.48, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "ROU", name = "罗马尼亚", confederation = "UEFA", rank = 39, points = 1750.56,
            eloRating = 1738.0, attackRating = 74, defenseRating = 75, midfieldRating = 75,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 66.0, counterAttack = 52.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.6,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.38, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Europe", region = "Eastern",
        ),
        TeamBaselineData(
            code = "SVK", name = "斯洛伐克", confederation = "UEFA", rank = 40, points = 1748.9,
            eloRating = 1735.0, attackRating = 73, defenseRating = 74, midfieldRating = 74,
            preferredFormation = "4-2-3-1", tacticalStyle = "DEFENSIVE", possession = 48.0,
            pressingIntensity = 64.0, counterAttack = 56.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 56.0, defensiveLine = 48.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.58,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.4, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "HUN", name = "匈牙利", confederation = "UEFA", rank = 41, points = 1746.23,
            eloRating = 1732.0, attackRating = 74, defenseRating = 73, midfieldRating = 75,
            preferredFormation = "3-5-2", tacticalStyle = "BALANCED", possession = 48.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 72.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.62,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.38, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "NGA", name = "尼日利亚", confederation = "CAF", rank = 42, points = 1744.56,
            eloRating = 1730.0, attackRating = 76, defenseRating = 73, midfieldRating = 74,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 48.0,
            pressingIntensity = 68.0, counterAttack = 62.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 72.0, width = 62.0, defensiveLine = 52.0,
            homeWinRate = 0.58, awayWinRate = 0.38, avgGoalsFor = 1.68,
            avgGoalsAgainst = 0.64, cleanSheetRate = 0.35, bttsRate = 0.48,
            over25Rate = 0.5, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "QAT", name = "卡塔尔", confederation = "AFC", rank = 43, points = 1742.89,
            eloRating = 1728.0, attackRating = 73, defenseRating = 73, midfieldRating = 74,
            preferredFormation = "4-2-3-1", tacticalStyle = "POSSESSION", possession = 52.0,
            pressingIntensity = 64.0, counterAttack = 50.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 72.0, width = 56.0, defensiveLine = 50.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.55,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "PAR", name = "巴拉圭", confederation = "CONMEBOL", rank = 44, points = 1740.12,
            eloRating = 1725.0, attackRating = 72, defenseRating = 74, midfieldRating = 73,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 46.0,
            pressingIntensity = 62.0, counterAttack = 56.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 74.0, width = 54.0, defensiveLine = 48.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.55,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.4, bttsRate = 0.42,
            over25Rate = 0.44, continent = "South America", region = "South",
        ),
        TeamBaselineData(
            code = "JAM", name = "牙买加", confederation = "CONCACAF", rank = 45, points = 1738.45,
            eloRating = 1722.0, attackRating = 72, defenseRating = 72, midfieldRating = 72,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 46.0,
            pressingIntensity = 64.0, counterAttack = 58.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 70.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.52,
            avgGoalsAgainst = 0.58, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "CMR", name = "喀麦隆", confederation = "CAF", rank = 46, points = 1736.78,
            eloRating = 1720.0, attackRating = 73, defenseRating = 72, midfieldRating = 73,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 46.0,
            pressingIntensity = 66.0, counterAttack = 60.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 70.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.58,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.36, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "BIH", name = "波黑", confederation = "UEFA", rank = 47, points = 1734.12,
            eloRating = 1718.0, attackRating = 74, defenseRating = 72, midfieldRating = 73,
            preferredFormation = "4-2-3-1", tacticalStyle = "DIRECT", possession = 46.0,
            pressingIntensity = 62.0, counterAttack = 56.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 70.0, width = 56.0, defensiveLine = 48.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.6,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.36, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "MLI", name = "马里", confederation = "CAF", rank = 48, points = 1732.45,
            eloRating = 1715.0, attackRating = 72, defenseRating = 72, midfieldRating = 73,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 48.0,
            pressingIntensity = 66.0, counterAttack = 54.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 70.0, width = 56.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.55,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "SCO", name = "苏格兰", confederation = "UEFA", rank = 49, points = 1730.78,
            eloRating = 1712.0, attackRating = 72, defenseRating = 72, midfieldRating = 73,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 48.0,
            pressingIntensity = 66.0, counterAttack = 52.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 70.0, width = 56.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.55,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "SAU", name = "沙特阿拉伯", confederation = "AFC", rank = 50, points = 1728.12,
            eloRating = 1710.0, attackRating = 71, defenseRating = 71, midfieldRating = 72,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 48.0,
            pressingIntensity = 62.0, counterAttack = 52.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 70.0, width = 54.0, defensiveLine = 48.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.5,
            avgGoalsAgainst = 0.58, cleanSheetRate = 0.38, bttsRate = 0.4,
            over25Rate = 0.42, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "CIV", name = "科特迪瓦", confederation = "CAF", rank = 51, points = 1726.34,
            eloRating = 1708.0, attackRating = 73, defenseRating = 71, midfieldRating = 73,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 46.0,
            pressingIntensity = 66.0, counterAttack = 60.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 70.0, width = 58.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.58,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.36, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "FIN", name = "芬兰", confederation = "UEFA", rank = 52, points = 1724.67,
            eloRating = 1705.0, attackRating = 71, defenseRating = 72, midfieldRating = 71,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 44.0,
            pressingIntensity = 60.0, counterAttack = 54.0, setPieceAttack = 62.0,
            setPieceDefense = 64.0, tempo = 72.0, width = 52.0, defensiveLine = 46.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.48,
            avgGoalsAgainst = 0.56, cleanSheetRate = 0.4, bttsRate = 0.4,
            over25Rate = 0.42, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "IRL", name = "爱尔兰", confederation = "UEFA", rank = 53, points = 1722.89,
            eloRating = 1702.0, attackRating = 71, defenseRating = 71, midfieldRating = 72,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 44.0,
            pressingIntensity = 60.0, counterAttack = 56.0, setPieceAttack = 62.0,
            setPieceDefense = 64.0, tempo = 70.0, width = 52.0, defensiveLine = 48.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.5,
            avgGoalsAgainst = 0.58, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "RUS", name = "俄罗斯", confederation = "UEFA", rank = 54, points = 1720.12,
            eloRating = 1700.0, attackRating = 72, defenseRating = 72, midfieldRating = 72,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 48.0,
            pressingIntensity = 64.0, counterAttack = 52.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 72.0, width = 54.0, defensiveLine = 48.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.52,
            avgGoalsAgainst = 0.58, cleanSheetRate = 0.38, bttsRate = 0.42,
            over25Rate = 0.44, continent = "Europe", region = "Eastern",
        ),
        TeamBaselineData(
            code = "PAN", name = "巴拿马", confederation = "CONCACAF", rank = 55, points = 1718.45,
            eloRating = 1698.0, attackRating = 70, defenseRating = 70, midfieldRating = 71,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 56.0, setPieceAttack = 62.0,
            setPieceDefense = 64.0, tempo = 70.0, width = 50.0, defensiveLine = 46.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.45,
            avgGoalsAgainst = 0.56, cleanSheetRate = 0.4, bttsRate = 0.4,
            over25Rate = 0.42, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "TUR", name = "土耳其", confederation = "UEFA", rank = 56, points = 1716.78,
            eloRating = 1715.0, attackRating = 75, defenseRating = 72, midfieldRating = 75,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 50.0,
            pressingIntensity = 68.0, counterAttack = 54.0, setPieceAttack = 68.0,
            setPieceDefense = 70.0, tempo = 72.0, width = 60.0, defensiveLine = 52.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.65,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.37, bttsRate = 0.46,
            over25Rate = 0.48, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "GHA", name = "加纳", confederation = "CAF", rank = 57, points = 1714.12,
            eloRating = 1702.0, attackRating = 73, defenseRating = 70, midfieldRating = 72,
            preferredFormation = "4-3-3", tacticalStyle = "COUNTER_ATTACK", possession = 46.0,
            pressingIntensity = 66.0, counterAttack = 60.0, setPieceAttack = 66.0,
            setPieceDefense = 68.0, tempo = 70.0, width = 56.0, defensiveLine = 50.0,
            homeWinRate = 0.54, awayWinRate = 0.32, avgGoalsFor = 1.58,
            avgGoalsAgainst = 0.6, cleanSheetRate = 0.35, bttsRate = 0.44,
            over25Rate = 0.46, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "PUR", name = "波多黎各", confederation = "CONCACAF", rank = 58, points = 1712.45,
            eloRating = 1695.0, attackRating = 69, defenseRating = 69, midfieldRating = 70,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 54.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 68.0, width = 50.0, defensiveLine = 46.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.4,
            avgGoalsAgainst = 0.54, cleanSheetRate = 0.4, bttsRate = 0.38,
            over25Rate = 0.4, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "ZAF", name = "南非", confederation = "CAF", rank = 59, points = 1710.78,
            eloRating = 1692.0, attackRating = 70, defenseRating = 70, midfieldRating = 71,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 46.0,
            pressingIntensity = 60.0, counterAttack = 52.0, setPieceAttack = 62.0,
            setPieceDefense = 64.0, tempo = 68.0, width = 52.0, defensiveLine = 48.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.45,
            avgGoalsAgainst = 0.56, cleanSheetRate = 0.38, bttsRate = 0.4,
            over25Rate = 0.42, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "SVN", name = "斯洛文尼亚", confederation = "UEFA", rank = 60, points = 1708.12,
            eloRating = 1700.0, attackRating = 72, defenseRating = 74, midfieldRating = 72,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 46.0,
            pressingIntensity = 62.0, counterAttack = 54.0, setPieceAttack = 64.0,
            setPieceDefense = 66.0, tempo = 76.0, width = 52.0, defensiveLine = 48.0,
            homeWinRate = 0.52, awayWinRate = 0.3, avgGoalsFor = 1.48,
            avgGoalsAgainst = 0.56, cleanSheetRate = 0.42, bttsRate = 0.4,
            over25Rate = 0.42, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "MAR2", name = "马其顿", confederation = "UEFA", rank = 61, points = 1706.34,
            eloRating = 1690.0, attackRating = 69, defenseRating = 70, midfieldRating = 70,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 54.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 70.0, width = 48.0, defensiveLine = 46.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.4,
            avgGoalsAgainst = 0.54, cleanSheetRate = 0.4, bttsRate = 0.38,
            over25Rate = 0.4, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "ALB", name = "阿尔巴尼亚", confederation = "UEFA", rank = 62, points = 1704.67,
            eloRating = 1688.0, attackRating = 69, defenseRating = 69, midfieldRating = 70,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 54.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 68.0, width = 48.0, defensiveLine = 46.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.38,
            avgGoalsAgainst = 0.54, cleanSheetRate = 0.4, bttsRate = 0.38,
            over25Rate = 0.4, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "ISL", name = "冰岛", confederation = "UEFA", rank = 63, points = 1702.89,
            eloRating = 1685.0, attackRating = 68, defenseRating = 69, midfieldRating = 69,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 56.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 68.0, width = 48.0, defensiveLine = 44.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.35,
            avgGoalsAgainst = 0.52, cleanSheetRate = 0.42, bttsRate = 0.36,
            over25Rate = 0.38, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "NZL", name = "新西兰", confederation = "OFC", rank = 64, points = 1700.12,
            eloRating = 1682.0, attackRating = 67, defenseRating = 68, midfieldRating = 68,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 54.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 66.0, width = 48.0, defensiveLine = 44.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.32,
            avgGoalsAgainst = 0.52, cleanSheetRate = 0.4, bttsRate = 0.36,
            over25Rate = 0.38, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "CHI", name = "智利", confederation = "CONMEBOL", rank = 65, points = 1698.45,
            eloRating = 1710.0, attackRating = 75, defenseRating = 70, midfieldRating = 73,
            preferredFormation = "4-3-3", tacticalStyle = "HIGH_PRESS", possession = 54.0,
            pressingIntensity = 76.0, counterAttack = 50.0, setPieceAttack = 70.0,
            setPieceDefense = 72.0, tempo = 68.0, width = 68.0, defensiveLine = 56.0,
            homeWinRate = 0.56, awayWinRate = 0.34, avgGoalsFor = 1.62,
            avgGoalsAgainst = 0.62, cleanSheetRate = 0.34, bttsRate = 0.46,
            over25Rate = 0.48, continent = "South America", region = "South",
        ),
        TeamBaselineData(
            code = "CHN", name = "中国", confederation = "AFC", rank = 66, points = 1696.78,
            eloRating = 1678.0, attackRating = 68, defenseRating = 68, midfieldRating = 69,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 46.0,
            pressingIntensity = 58.0, counterAttack = 50.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 66.0, width = 50.0, defensiveLine = 46.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.3,
            avgGoalsAgainst = 0.52, cleanSheetRate = 0.4, bttsRate = 0.36,
            over25Rate = 0.38, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "IRQ", name = "伊拉克", confederation = "AFC", rank = 67, points = 1694.12,
            eloRating = 1676.0, attackRating = 68, defenseRating = 67, midfieldRating = 68,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 54.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 66.0, width = 48.0, defensiveLine = 44.0,
            homeWinRate = 0.5, awayWinRate = 0.28, avgGoalsFor = 1.3,
            avgGoalsAgainst = 0.52, cleanSheetRate = 0.4, bttsRate = 0.36,
            over25Rate = 0.38, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "UZB", name = "乌兹别克斯坦", confederation = "AFC", rank = 68, points = 1692.45,
            eloRating = 1674.0, attackRating = 68, defenseRating = 67, midfieldRating = 68,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 52.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 66.0, width = 48.0, defensiveLine = 44.0,
            homeWinRate = 0.48, awayWinRate = 0.26, avgGoalsFor = 1.28,
            avgGoalsAgainst = 0.52, cleanSheetRate = 0.4, bttsRate = 0.36,
            over25Rate = 0.38, continent = "Asia", region = "Central",
        ),
        TeamBaselineData(
            code = "LBN", name = "黎巴嫩", confederation = "AFC", rank = 69, points = 1690.78,
            eloRating = 1672.0, attackRating = 66, defenseRating = 67, midfieldRating = 67,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 52.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 66.0, width = 46.0, defensiveLine = 42.0,
            homeWinRate = 0.48, awayWinRate = 0.26, avgGoalsFor = 1.22,
            avgGoalsAgainst = 0.5, cleanSheetRate = 0.42, bttsRate = 0.34,
            over25Rate = 0.36, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "JOR", name = "约旦", confederation = "AFC", rank = 70, points = 1688.12,
            eloRating = 1670.0, attackRating = 66, defenseRating = 66, midfieldRating = 67,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 54.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 64.0, width = 46.0, defensiveLine = 42.0,
            homeWinRate = 0.48, awayWinRate = 0.26, avgGoalsFor = 1.2,
            avgGoalsAgainst = 0.5, cleanSheetRate = 0.42, bttsRate = 0.34,
            over25Rate = 0.36, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "VEN", name = "委内瑞拉", confederation = "CONMEBOL", rank = 71, points = 1686.34,
            eloRating = 1668.0, attackRating = 67, defenseRating = 66, midfieldRating = 68,
            preferredFormation = "4-4-2", tacticalStyle = "COUNTER_ATTACK", possession = 42.0,
            pressingIntensity = 60.0, counterAttack = 58.0, setPieceAttack = 60.0,
            setPieceDefense = 62.0, tempo = 64.0, width = 46.0, defensiveLine = 42.0,
            homeWinRate = 0.46, awayWinRate = 0.24, avgGoalsFor = 1.2,
            avgGoalsAgainst = 0.48, cleanSheetRate = 0.42, bttsRate = 0.32,
            over25Rate = 0.34, continent = "South America", region = "North",
        ),
        TeamBaselineData(
            code = "CUW", name = "库拉索", confederation = "CONCACAF", rank = 72, points = 1684.67,
            eloRating = 1665.0, attackRating = 65, defenseRating = 65, midfieldRating = 66,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 50.0, setPieceAttack = 56.0,
            setPieceDefense = 58.0, tempo = 62.0, width = 44.0, defensiveLine = 42.0,
            homeWinRate = 0.46, awayWinRate = 0.24, avgGoalsFor = 1.15,
            avgGoalsAgainst = 0.48, cleanSheetRate = 0.42, bttsRate = 0.32,
            over25Rate = 0.34, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "THA", name = "泰国", confederation = "AFC", rank = 73, points = 1682.89,
            eloRating = 1662.0, attackRating = 65, defenseRating = 64, midfieldRating = 66,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 50.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 62.0, width = 44.0, defensiveLine = 42.0,
            homeWinRate = 0.46, awayWinRate = 0.24, avgGoalsFor = 1.15,
            avgGoalsAgainst = 0.48, cleanSheetRate = 0.42, bttsRate = 0.32,
            over25Rate = 0.34, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "VNM", name = "越南", confederation = "AFC", rank = 74, points = 1680.12,
            eloRating = 1660.0, attackRating = 64, defenseRating = 64, midfieldRating = 65,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 52.0, setPieceAttack = 56.0,
            setPieceDefense = 58.0, tempo = 62.0, width = 42.0, defensiveLine = 40.0,
            homeWinRate = 0.46, awayWinRate = 0.24, avgGoalsFor = 1.1,
            avgGoalsAgainst = 0.46, cleanSheetRate = 0.42, bttsRate = 0.3,
            over25Rate = 0.32, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "COD", name = "刚果民主共和国", confederation = "CAF", rank = 75, points = 1678.45,
            eloRating = 1658.0, attackRating = 66, defenseRating = 64, midfieldRating = 66,
            preferredFormation = "4-4-2", tacticalStyle = "COUNTER_ATTACK", possession = 42.0,
            pressingIntensity = 60.0, counterAttack = 56.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 62.0, width = 44.0, defensiveLine = 42.0,
            homeWinRate = 0.46, awayWinRate = 0.24, avgGoalsFor = 1.18,
            avgGoalsAgainst = 0.48, cleanSheetRate = 0.4, bttsRate = 0.32,
            over25Rate = 0.34, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "GAB", name = "加蓬", confederation = "CAF", rank = 76, points = 1676.78,
            eloRating = 1655.0, attackRating = 64, defenseRating = 63, midfieldRating = 64,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 52.0, setPieceAttack = 56.0,
            setPieceDefense = 58.0, tempo = 60.0, width = 42.0, defensiveLine = 40.0,
            homeWinRate = 0.44, awayWinRate = 0.22, avgGoalsFor = 1.1,
            avgGoalsAgainst = 0.46, cleanSheetRate = 0.42, bttsRate = 0.3,
            over25Rate = 0.32, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "GNB", name = "几内亚比绍", confederation = "CAF", rank = 77, points = 1674.12,
            eloRating = 1652.0, attackRating = 63, defenseRating = 63, midfieldRating = 63,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 40.0,
            pressingIntensity = 54.0, counterAttack = 54.0, setPieceAttack = 54.0,
            setPieceDefense = 56.0, tempo = 60.0, width = 40.0, defensiveLine = 38.0,
            homeWinRate = 0.42, awayWinRate = 0.2, avgGoalsFor = 1.05,
            avgGoalsAgainst = 0.44, cleanSheetRate = 0.44, bttsRate = 0.28,
            over25Rate = 0.3, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "ZMB", name = "赞比亚", confederation = "CAF", rank = 78, points = 1672.45,
            eloRating = 1650.0, attackRating = 63, defenseRating = 62, midfieldRating = 63,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 52.0, setPieceAttack = 54.0,
            setPieceDefense = 56.0, tempo = 58.0, width = 42.0, defensiveLine = 40.0,
            homeWinRate = 0.42, awayWinRate = 0.2, avgGoalsFor = 1.05,
            avgGoalsAgainst = 0.44, cleanSheetRate = 0.42, bttsRate = 0.28,
            over25Rate = 0.3, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "CPV", name = "佛得角", confederation = "CAF", rank = 79, points = 1670.78,
            eloRating = 1648.0, attackRating = 62, defenseRating = 62, midfieldRating = 63,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 40.0,
            pressingIntensity = 54.0, counterAttack = 52.0, setPieceAttack = 54.0,
            setPieceDefense = 56.0, tempo = 58.0, width = 40.0, defensiveLine = 38.0,
            homeWinRate = 0.42, awayWinRate = 0.2, avgGoalsFor = 1.0,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.44, bttsRate = 0.26,
            over25Rate = 0.28, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "BFA", name = "布基纳法索", confederation = "CAF", rank = 80, points = 1668.12,
            eloRating = 1645.0, attackRating = 62, defenseRating = 62, midfieldRating = 62,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 40.0,
            pressingIntensity = 54.0, counterAttack = 50.0, setPieceAttack = 54.0,
            setPieceDefense = 56.0, tempo = 58.0, width = 40.0, defensiveLine = 38.0,
            homeWinRate = 0.42, awayWinRate = 0.2, avgGoalsFor = 1.0,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.44, bttsRate = 0.26,
            over25Rate = 0.28, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "KSA", name = "科索沃", confederation = "UEFA", rank = 81, points = 1666.34,
            eloRating = 1648.0, attackRating = 64, defenseRating = 64, midfieldRating = 65,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 44.0,
            pressingIntensity = 58.0, counterAttack = 52.0, setPieceAttack = 58.0,
            setPieceDefense = 60.0, tempo = 62.0, width = 46.0, defensiveLine = 42.0,
            homeWinRate = 0.44, awayWinRate = 0.22, avgGoalsFor = 1.12,
            avgGoalsAgainst = 0.46, cleanSheetRate = 0.42, bttsRate = 0.3,
            over25Rate = 0.32, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "LUX", name = "卢森堡", confederation = "UEFA", rank = 82, points = 1664.67,
            eloRating = 1642.0, attackRating = 60, defenseRating = 60, midfieldRating = 61,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 38.0,
            pressingIntensity = 52.0, counterAttack = 50.0, setPieceAttack = 52.0,
            setPieceDefense = 54.0, tempo = 58.0, width = 36.0, defensiveLine = 36.0,
            homeWinRate = 0.4, awayWinRate = 0.18, avgGoalsFor = 0.95,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.44, bttsRate = 0.24,
            over25Rate = 0.26, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "SVK2", name = "圣马力诺", confederation = "UEFA", rank = 83, points = 1662.89,
            eloRating = 1620.0, attackRating = 52, defenseRating = 56, midfieldRating = 54,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 40.0, counterAttack = 48.0, setPieceAttack = 40.0,
            setPieceDefense = 42.0, tempo = 52.0, width = 28.0, defensiveLine = 28.0,
            homeWinRate = 0.28, awayWinRate = 0.08, avgGoalsFor = 0.48,
            avgGoalsAgainst = 0.36, cleanSheetRate = 0.56, bttsRate = 0.12,
            over25Rate = 0.14, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "GIB", name = "直布罗陀", confederation = "UEFA", rank = 84, points = 1660.12,
            eloRating = 1618.0, attackRating = 51, defenseRating = 55, midfieldRating = 53,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 38.0, counterAttack = 46.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 50.0, width = 26.0, defensiveLine = 26.0,
            homeWinRate = 0.26, awayWinRate = 0.06, avgGoalsFor = 0.45,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.58, bttsRate = 0.1,
            over25Rate = 0.12, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "AND", name = "安道尔", confederation = "UEFA", rank = 85, points = 1658.45,
            eloRating = 1616.0, attackRating = 50, defenseRating = 54, midfieldRating = 52,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 38.0, counterAttack = 46.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 50.0, width = 24.0, defensiveLine = 26.0,
            homeWinRate = 0.24, awayWinRate = 0.06, avgGoalsFor = 0.42,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.6, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "FRO", name = "法罗群岛", confederation = "UEFA", rank = 86, points = 1656.78,
            eloRating = 1622.0, attackRating = 54, defenseRating = 58, midfieldRating = 56,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 44.0, counterAttack = 48.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 54.0, width = 32.0, defensiveLine = 30.0,
            homeWinRate = 0.3, awayWinRate = 0.1, avgGoalsFor = 0.55,
            avgGoalsAgainst = 0.38, cleanSheetRate = 0.52, bttsRate = 0.14,
            over25Rate = 0.16, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "MDA", name = "摩尔多瓦", confederation = "UEFA", rank = 87, points = 1655.12,
            eloRating = 1625.0, attackRating = 56, defenseRating = 60, midfieldRating = 58,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 36.0,
            pressingIntensity = 46.0, counterAttack = 50.0, setPieceAttack = 46.0,
            setPieceDefense = 48.0, tempo = 56.0, width = 34.0, defensiveLine = 32.0,
            homeWinRate = 0.32, awayWinRate = 0.12, avgGoalsFor = 0.6,
            avgGoalsAgainst = 0.4, cleanSheetRate = 0.5, bttsRate = 0.16,
            over25Rate = 0.18, continent = "Europe", region = "Eastern",
        ),
        TeamBaselineData(
            code = "CYP", name = "塞浦路斯", confederation = "UEFA", rank = 88, points = 1653.34,
            eloRating = 1628.0, attackRating = 58, defenseRating = 60, midfieldRating = 60,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 40.0,
            pressingIntensity = 50.0, counterAttack = 48.0, setPieceAttack = 48.0,
            setPieceDefense = 50.0, tempo = 56.0, width = 36.0, defensiveLine = 34.0,
            homeWinRate = 0.36, awayWinRate = 0.14, avgGoalsFor = 0.68,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.46, bttsRate = 0.2,
            over25Rate = 0.22, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "GEO", name = "格鲁吉亚", confederation = "UEFA", rank = 89, points = 1651.67,
            eloRating = 1635.0, attackRating = 60, defenseRating = 62, midfieldRating = 62,
            preferredFormation = "4-3-3", tacticalStyle = "BALANCED", possession = 42.0,
            pressingIntensity = 54.0, counterAttack = 50.0, setPieceAttack = 50.0,
            setPieceDefense = 52.0, tempo = 58.0, width = 40.0, defensiveLine = 38.0,
            homeWinRate = 0.4, awayWinRate = 0.16, avgGoalsFor = 0.72,
            avgGoalsAgainst = 0.44, cleanSheetRate = 0.44, bttsRate = 0.22,
            over25Rate = 0.24, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "BLR", name = "白俄罗斯", confederation = "UEFA", rank = 90, points = 1650.0,
            eloRating = 1630.0, attackRating = 58, defenseRating = 60, midfieldRating = 60,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 38.0,
            pressingIntensity = 48.0, counterAttack = 52.0, setPieceAttack = 48.0,
            setPieceDefense = 50.0, tempo = 56.0, width = 36.0, defensiveLine = 34.0,
            homeWinRate = 0.36, awayWinRate = 0.14, avgGoalsFor = 0.65,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.46, bttsRate = 0.2,
            over25Rate = 0.22, continent = "Europe", region = "Eastern",
        ),
        TeamBaselineData(
            code = "ARM", name = "亚美尼亚", confederation = "UEFA", rank = 91, points = 1648.23,
            eloRating = 1625.0, attackRating = 56, defenseRating = 58, midfieldRating = 58,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 36.0,
            pressingIntensity = 46.0, counterAttack = 50.0, setPieceAttack = 46.0,
            setPieceDefense = 48.0, tempo = 54.0, width = 34.0, defensiveLine = 32.0,
            homeWinRate = 0.34, awayWinRate = 0.12, avgGoalsFor = 0.6,
            avgGoalsAgainst = 0.4, cleanSheetRate = 0.48, bttsRate = 0.18,
            over25Rate = 0.2, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "KAZ", name = "哈萨克斯坦", confederation = "UEFA", rank = 92, points = 1646.56,
            eloRating = 1622.0, attackRating = 55, defenseRating = 57, midfieldRating = 56,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 44.0, counterAttack = 48.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 52.0, width = 32.0, defensiveLine = 30.0,
            homeWinRate = 0.32, awayWinRate = 0.1, avgGoalsFor = 0.55,
            avgGoalsAgainst = 0.38, cleanSheetRate = 0.5, bttsRate = 0.16,
            over25Rate = 0.18, continent = "Asia", region = "Central",
        ),
        TeamBaselineData(
            code = "AZE", name = "阿塞拜疆", confederation = "UEFA", rank = 93, points = 1644.89,
            eloRating = 1620.0, attackRating = 54, defenseRating = 56, midfieldRating = 55,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 44.0, counterAttack = 48.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 52.0, width = 32.0, defensiveLine = 30.0,
            homeWinRate = 0.32, awayWinRate = 0.1, avgGoalsFor = 0.52,
            avgGoalsAgainst = 0.38, cleanSheetRate = 0.5, bttsRate = 0.16,
            over25Rate = 0.18, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "LVA", name = "拉脱维亚", confederation = "UEFA", rank = 94, points = 1643.12,
            eloRating = 1618.0, attackRating = 53, defenseRating = 55, midfieldRating = 54,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 42.0, counterAttack = 50.0, setPieceAttack = 42.0,
            setPieceDefense = 44.0, tempo = 50.0, width = 30.0, defensiveLine = 28.0,
            homeWinRate = 0.3, awayWinRate = 0.08, avgGoalsFor = 0.5,
            avgGoalsAgainst = 0.36, cleanSheetRate = 0.52, bttsRate = 0.14,
            over25Rate = 0.16, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "EST", name = "爱沙尼亚", confederation = "UEFA", rank = 95, points = 1641.45,
            eloRating = 1615.0, attackRating = 52, defenseRating = 54, midfieldRating = 53,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 40.0, counterAttack = 48.0, setPieceAttack = 40.0,
            setPieceDefense = 42.0, tempo = 48.0, width = 28.0, defensiveLine = 26.0,
            homeWinRate = 0.28, awayWinRate = 0.06, avgGoalsFor = 0.48,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.54, bttsRate = 0.12,
            over25Rate = 0.14, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "LTU", name = "立陶宛", confederation = "UEFA", rank = 96, points = 1639.78,
            eloRating = 1612.0, attackRating = 52, defenseRating = 53, midfieldRating = 52,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 38.0, counterAttack = 48.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 46.0, width = 26.0, defensiveLine = 24.0,
            homeWinRate = 0.26, awayWinRate = 0.06, avgGoalsFor = 0.45,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.56, bttsRate = 0.1,
            over25Rate = 0.12, continent = "Europe", region = "Northern",
        ),
        TeamBaselineData(
            code = "MKD", name = "北马其顿", confederation = "UEFA", rank = 97, points = 1638.12,
            eloRating = 1635.0, attackRating = 58, defenseRating = 58, midfieldRating = 59,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 38.0,
            pressingIntensity = 48.0, counterAttack = 50.0, setPieceAttack = 48.0,
            setPieceDefense = 50.0, tempo = 54.0, width = 36.0, defensiveLine = 34.0,
            homeWinRate = 0.38, awayWinRate = 0.16, avgGoalsFor = 0.65,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.46, bttsRate = 0.2,
            over25Rate = 0.22, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "LIE", name = "列支敦士登", confederation = "UEFA", rank = 98, points = 1636.34,
            eloRating = 1610.0, attackRating = 50, defenseRating = 53, midfieldRating = 51,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 28.0,
            pressingIntensity = 36.0, counterAttack = 44.0, setPieceAttack = 36.0,
            setPieceDefense = 38.0, tempo = 48.0, width = 24.0, defensiveLine = 22.0,
            homeWinRate = 0.22, awayWinRate = 0.04, avgGoalsFor = 0.4,
            avgGoalsAgainst = 0.3, cleanSheetRate = 0.6, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Europe", region = "Central",
        ),
        TeamBaselineData(
            code = "MTA", name = "马耳他", confederation = "UEFA", rank = 99, points = 1634.67,
            eloRating = 1608.0, attackRating = 51, defenseRating = 54, midfieldRating = 52,
            preferredFormation = "5-4-1", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 38.0, counterAttack = 46.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 50.0, width = 26.0, defensiveLine = 24.0,
            homeWinRate = 0.24, awayWinRate = 0.06, avgGoalsFor = 0.42,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.58, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "IRL2", name = "北爱尔兰", confederation = "UEFA", rank = 100, points = 1632.89,
            eloRating = 1630.0, attackRating = 58, defenseRating = 60, midfieldRating = 59,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 38.0,
            pressingIntensity = 50.0, counterAttack = 54.0, setPieceAttack = 50.0,
            setPieceDefense = 52.0, tempo = 56.0, width = 38.0, defensiveLine = 36.0,
            homeWinRate = 0.38, awayWinRate = 0.16, avgGoalsFor = 0.62,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.46, bttsRate = 0.2,
            over25Rate = 0.22, continent = "Europe", region = "Western",
        ),
        TeamBaselineData(
            code = "KOS", name = "科索沃", confederation = "UEFA", rank = 101, points = 1631.12,
            eloRating = 1638.0, attackRating = 60, defenseRating = 60, midfieldRating = 61,
            preferredFormation = "4-2-3-1", tacticalStyle = "BALANCED", possession = 42.0,
            pressingIntensity = 56.0, counterAttack = 52.0, setPieceAttack = 56.0,
            setPieceDefense = 58.0, tempo = 60.0, width = 42.0, defensiveLine = 40.0,
            homeWinRate = 0.42, awayWinRate = 0.18, avgGoalsFor = 0.7,
            avgGoalsAgainst = 0.44, cleanSheetRate = 0.44, bttsRate = 0.22,
            over25Rate = 0.24, continent = "Europe", region = "Southern",
        ),
        TeamBaselineData(
            code = "KUW", name = "科威特", confederation = "AFC", rank = 102, points = 1629.45,
            eloRating = 1610.0, attackRating = 52, defenseRating = 54, midfieldRating = 53,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 36.0,
            pressingIntensity = 46.0, counterAttack = 48.0, setPieceAttack = 46.0,
            setPieceDefense = 48.0, tempo = 52.0, width = 32.0, defensiveLine = 30.0,
            homeWinRate = 0.3, awayWinRate = 0.1, avgGoalsFor = 0.5,
            avgGoalsAgainst = 0.38, cleanSheetRate = 0.5, bttsRate = 0.14,
            over25Rate = 0.16, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "SYR", name = "叙利亚", confederation = "AFC", rank = 103, points = 1627.78,
            eloRating = 1608.0, attackRating = 51, defenseRating = 53, midfieldRating = 52,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 44.0, counterAttack = 50.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 50.0, width = 30.0, defensiveLine = 28.0,
            homeWinRate = 0.28, awayWinRate = 0.08, avgGoalsFor = 0.48,
            avgGoalsAgainst = 0.36, cleanSheetRate = 0.52, bttsRate = 0.12,
            over25Rate = 0.14, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "PLE", name = "巴勒斯坦", confederation = "AFC", rank = 104, points = 1626.12,
            eloRating = 1606.0, attackRating = 50, defenseRating = 52, midfieldRating = 51,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 42.0, counterAttack = 48.0, setPieceAttack = 42.0,
            setPieceDefense = 44.0, tempo = 48.0, width = 28.0, defensiveLine = 26.0,
            homeWinRate = 0.26, awayWinRate = 0.06, avgGoalsFor = 0.45,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.54, bttsRate = 0.1,
            over25Rate = 0.12, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "BHR", name = "巴林", confederation = "AFC", rank = 105, points = 1624.45,
            eloRating = 1605.0, attackRating = 50, defenseRating = 51, midfieldRating = 51,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 42.0, counterAttack = 48.0, setPieceAttack = 42.0,
            setPieceDefense = 44.0, tempo = 48.0, width = 28.0, defensiveLine = 26.0,
            homeWinRate = 0.26, awayWinRate = 0.06, avgGoalsFor = 0.42,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.54, bttsRate = 0.1,
            over25Rate = 0.12, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "IND", name = "印度", confederation = "AFC", rank = 106, points = 1622.78,
            eloRating = 1602.0, attackRating = 49, defenseRating = 50, midfieldRating = 50,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 40.0, counterAttack = 46.0, setPieceAttack = 40.0,
            setPieceDefense = 42.0, tempo = 46.0, width = 26.0, defensiveLine = 24.0,
            homeWinRate = 0.24, awayWinRate = 0.04, avgGoalsFor = 0.4,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.56, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "TJK", name = "塔吉克斯坦", confederation = "AFC", rank = 107, points = 1621.12,
            eloRating = 1600.0, attackRating = 48, defenseRating = 49, midfieldRating = 49,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 28.0,
            pressingIntensity = 38.0, counterAttack = 46.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 44.0, width = 24.0, defensiveLine = 22.0,
            homeWinRate = 0.22, awayWinRate = 0.04, avgGoalsFor = 0.38,
            avgGoalsAgainst = 0.3, cleanSheetRate = 0.58, bttsRate = 0.06,
            over25Rate = 0.08, continent = "Asia", region = "Central",
        ),
        TeamBaselineData(
            code = "MYA", name = "缅甸", confederation = "AFC", rank = 108, points = 1619.34,
            eloRating = 1598.0, attackRating = 47, defenseRating = 48, midfieldRating = 48,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 28.0,
            pressingIntensity = 36.0, counterAttack = 44.0, setPieceAttack = 36.0,
            setPieceDefense = 38.0, tempo = 42.0, width = 22.0, defensiveLine = 20.0,
            homeWinRate = 0.2, awayWinRate = 0.02, avgGoalsFor = 0.35,
            avgGoalsAgainst = 0.28, cleanSheetRate = 0.6, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "KGZ", name = "吉尔吉斯斯坦", confederation = "AFC", rank = 109, points = 1617.67,
            eloRating = 1595.0, attackRating = 46, defenseRating = 47, midfieldRating = 47,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 26.0,
            pressingIntensity = 34.0, counterAttack = 44.0, setPieceAttack = 34.0,
            setPieceDefense = 36.0, tempo = 40.0, width = 20.0, defensiveLine = 18.0,
            homeWinRate = 0.18, awayWinRate = 0.02, avgGoalsFor = 0.32,
            avgGoalsAgainst = 0.26, cleanSheetRate = 0.62, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Central",
        ),
        TeamBaselineData(
            code = "SIN", name = "新加坡", confederation = "AFC", rank = 110, points = 1615.89,
            eloRating = 1592.0, attackRating = 45, defenseRating = 46, midfieldRating = 46,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 24.0,
            pressingIntensity = 32.0, counterAttack = 42.0, setPieceAttack = 32.0,
            setPieceDefense = 34.0, tempo = 38.0, width = 18.0, defensiveLine = 16.0,
            homeWinRate = 0.16, awayWinRate = 0.02, avgGoalsFor = 0.3,
            avgGoalsAgainst = 0.24, cleanSheetRate = 0.64, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "PHI", name = "菲律宾", confederation = "AFC", rank = 111, points = 1614.12,
            eloRating = 1590.0, attackRating = 44, defenseRating = 45, midfieldRating = 45,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 22.0,
            pressingIntensity = 30.0, counterAttack = 40.0, setPieceAttack = 30.0,
            setPieceDefense = 32.0, tempo = 36.0, width = 16.0, defensiveLine = 14.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.28,
            avgGoalsAgainst = 0.22, cleanSheetRate = 0.66, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "CAM", name = "柬埔寨", confederation = "AFC", rank = 112, points = 1612.45,
            eloRating = 1588.0, attackRating = 43, defenseRating = 44, midfieldRating = 44,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 20.0,
            pressingIntensity = 28.0, counterAttack = 38.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 34.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "TLS", name = "东帝汶", confederation = "AFC", rank = 113, points = 1610.78,
            eloRating = 1585.0, attackRating = 42, defenseRating = 43, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 26.0, counterAttack = 36.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 32.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "BRN", name = "文莱", confederation = "AFC", rank = 114, points = 1609.12,
            eloRating = 1582.0, attackRating = 41, defenseRating = 42, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 24.0, counterAttack = 34.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 30.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "LAO", name = "老挝", confederation = "AFC", rank = 115, points = 1607.45,
            eloRating = 1580.0, attackRating = 40, defenseRating = 41, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 22.0, counterAttack = 32.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 28.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "ANG", name = "安哥拉", confederation = "CAF", rank = 116, points = 1605.78,
            eloRating = 1578.0, attackRating = 49, defenseRating = 50, midfieldRating = 49,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 42.0, counterAttack = 48.0, setPieceAttack = 42.0,
            setPieceDefense = 44.0, tempo = 48.0, width = 28.0, defensiveLine = 26.0,
            homeWinRate = 0.26, awayWinRate = 0.06, avgGoalsFor = 0.42,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.52, bttsRate = 0.1,
            over25Rate = 0.12, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "BEN", name = "贝宁", confederation = "CAF", rank = 117, points = 1604.12,
            eloRating = 1575.0, attackRating = 48, defenseRating = 49, midfieldRating = 48,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 30.0,
            pressingIntensity = 40.0, counterAttack = 46.0, setPieceAttack = 40.0,
            setPieceDefense = 42.0, tempo = 46.0, width = 26.0, defensiveLine = 24.0,
            homeWinRate = 0.24, awayWinRate = 0.04, avgGoalsFor = 0.4,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.54, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "BDI", name = "布隆迪", confederation = "CAF", rank = 118, points = 1602.45,
            eloRating = 1572.0, attackRating = 47, defenseRating = 48, midfieldRating = 47,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 28.0,
            pressingIntensity = 38.0, counterAttack = 44.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 44.0, width = 24.0, defensiveLine = 22.0,
            homeWinRate = 0.22, awayWinRate = 0.04, avgGoalsFor = 0.38,
            avgGoalsAgainst = 0.3, cleanSheetRate = 0.56, bttsRate = 0.08,
            over25Rate = 0.1, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "BOT", name = "博茨瓦纳", confederation = "CAF", rank = 119, points = 1600.78,
            eloRating = 1570.0, attackRating = 46, defenseRating = 47, midfieldRating = 46,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 26.0,
            pressingIntensity = 36.0, counterAttack = 42.0, setPieceAttack = 36.0,
            setPieceDefense = 38.0, tempo = 42.0, width = 22.0, defensiveLine = 20.0,
            homeWinRate = 0.2, awayWinRate = 0.04, avgGoalsFor = 0.35,
            avgGoalsAgainst = 0.28, cleanSheetRate = 0.58, bttsRate = 0.06,
            over25Rate = 0.08, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "BWA", name = "博茨瓦纳", confederation = "CAF", rank = 120, points = 1599.12,
            eloRating = 1568.0, attackRating = 45, defenseRating = 46, midfieldRating = 45,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 24.0,
            pressingIntensity = 34.0, counterAttack = 40.0, setPieceAttack = 34.0,
            setPieceDefense = 36.0, tempo = 40.0, width = 20.0, defensiveLine = 18.0,
            homeWinRate = 0.18, awayWinRate = 0.02, avgGoalsFor = 0.32,
            avgGoalsAgainst = 0.26, cleanSheetRate = 0.6, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "CHA", name = "乍得", confederation = "CAF", rank = 121, points = 1597.45,
            eloRating = 1565.0, attackRating = 44, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 22.0,
            pressingIntensity = 32.0, counterAttack = 38.0, setPieceAttack = 32.0,
            setPieceDefense = 34.0, tempo = 38.0, width = 18.0, defensiveLine = 16.0,
            homeWinRate = 0.16, awayWinRate = 0.02, avgGoalsFor = 0.3,
            avgGoalsAgainst = 0.24, cleanSheetRate = 0.62, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "COM", name = "科摩罗", confederation = "CAF", rank = 122, points = 1595.78,
            eloRating = 1562.0, attackRating = 43, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 20.0,
            pressingIntensity = 30.0, counterAttack = 36.0, setPieceAttack = 30.0,
            setPieceDefense = 32.0, tempo = 36.0, width = 16.0, defensiveLine = 14.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.28,
            avgGoalsAgainst = 0.22, cleanSheetRate = 0.64, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "CTA", name = "中非共和国", confederation = "CAF", rank = 123, points = 1594.12,
            eloRating = 1560.0, attackRating = 42, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 34.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.66, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "ERI", name = "厄立特里亚", confederation = "CAF", rank = 124, points = 1592.45,
            eloRating = 1558.0, attackRating = 41, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 32.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.68, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SWZ", name = "斯威士兰", confederation = "CAF", rank = 125, points = 1590.78,
            eloRating = 1555.0, attackRating = 40, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 30.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.7, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "GAM", name = "冈比亚", confederation = "CAF", rank = 126, points = 1589.12,
            eloRating = 1552.0, attackRating = 39, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 28.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "LES", name = "莱索托", confederation = "CAF", rank = 127, points = 1587.45,
            eloRating = 1550.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "MAD", name = "马达加斯加", confederation = "CAF", rank = 128, points = 1585.78,
            eloRating = 1548.0, attackRating = 42, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 34.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.66, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "MWI", name = "马拉维", confederation = "CAF", rank = 129, points = 1584.12,
            eloRating = 1545.0, attackRating = 41, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 32.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.68, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "MRI", name = "毛里求斯", confederation = "CAF", rank = 130, points = 1582.45,
            eloRating = 1542.0, attackRating = 40, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 30.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.7, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "STP", name = "圣多美和普林西比", confederation = "CAF", rank = 131, points = 1580.78,
            eloRating = 1540.0, attackRating = 39, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 28.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "SEY", name = "塞舌尔", confederation = "CAF", rank = 132, points = 1579.12,
            eloRating = 1538.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SLE", name = "塞拉利昂", confederation = "CAF", rank = 133, points = 1577.45,
            eloRating = 1535.0, attackRating = 40, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 30.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.7, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "SOM", name = "索马里", confederation = "CAF", rank = 134, points = 1575.78,
            eloRating = 1532.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 24.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SSD", name = "南苏丹", confederation = "CAF", rank = 135, points = 1574.12,
            eloRating = 1530.0, attackRating = 37, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 22.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SUD", name = "苏丹", confederation = "CAF", rank = 136, points = 1572.45,
            eloRating = 1528.0, attackRating = 39, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 28.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "TAN", name = "坦桑尼亚", confederation = "CAF", rank = 137, points = 1570.78,
            eloRating = 1525.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "TOG", name = "多哥", confederation = "CAF", rank = 138, points = 1569.12,
            eloRating = 1522.0, attackRating = 37, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 24.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "UGA", name = "乌干达", confederation = "CAF", rank = 139, points = 1567.45,
            eloRating = 1520.0, attackRating = 39, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 30.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.7, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "GQE", name = "赤道几内亚", confederation = "CAF", rank = 140, points = 1565.78,
            eloRating = 1518.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "KEN", name = "肯尼亚", confederation = "CAF", rank = 141, points = 1564.12,
            eloRating = 1515.0, attackRating = 39, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 28.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "LBR", name = "利比里亚", confederation = "CAF", rank = 142, points = 1562.45,
            eloRating = 1512.0, attackRating = 37, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 22.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "LBY", name = "利比亚", confederation = "CAF", rank = 143, points = 1560.78,
            eloRating = 1510.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "North",
        ),
        TeamBaselineData(
            code = "MOZ", name = "莫桑比克", confederation = "CAF", rank = 144, points = 1559.12,
            eloRating = 1508.0, attackRating = 37, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 24.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "NAM", name = "纳米比亚", confederation = "CAF", rank = 145, points = 1557.45,
            eloRating = 1505.0, attackRating = 36, defenseRating = 37, midfieldRating = 36,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 22.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "RWA", name = "卢旺达", confederation = "CAF", rank = 146, points = 1555.78,
            eloRating = 1502.0, attackRating = 37, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 24.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SEN2", name = "塞内加尔", confederation = "CAF", rank = 147, points = 1554.12,
            eloRating = 1500.0, attackRating = 38, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 26.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "SLE2", name = "塞拉利昂", confederation = "CAF", rank = 148, points = 1552.45,
            eloRating = 1498.0, attackRating = 36, defenseRating = 37, midfieldRating = 36,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 22.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "SOM2", name = "索马里", confederation = "CAF", rank = 149, points = 1550.78,
            eloRating = 1495.0, attackRating = 35, defenseRating = 36, midfieldRating = 35,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 4.0,
            pressingIntensity = 14.0, counterAttack = 20.0, setPieceAttack = 14.0,
            setPieceDefense = 16.0, tempo = 20.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.08,
            avgGoalsAgainst = 0.06, cleanSheetRate = 0.8, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "SWZ2", name = "斯威士兰", confederation = "CAF", rank = 150, points = 1549.12,
            eloRating = 1492.0, attackRating = 34, defenseRating = 35, midfieldRating = 34,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 2.0,
            pressingIntensity = 12.0, counterAttack = 18.0, setPieceAttack = 12.0,
            setPieceDefense = 14.0, tempo = 18.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.06,
            avgGoalsAgainst = 0.04, cleanSheetRate = 0.82, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "GUA", name = "危地马拉", confederation = "CONCACAF", rank = 151, points = 1547.45,
            eloRating = 1490.0, attackRating = 54, defenseRating = 56, midfieldRating = 55,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 38.0,
            pressingIntensity = 50.0, counterAttack = 48.0, setPieceAttack = 48.0,
            setPieceDefense = 50.0, tempo = 54.0, width = 36.0, defensiveLine = 34.0,
            homeWinRate = 0.36, awayWinRate = 0.14, avgGoalsFor = 0.65,
            avgGoalsAgainst = 0.42, cleanSheetRate = 0.46, bttsRate = 0.2,
            over25Rate = 0.22, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "HON", name = "洪都拉斯", confederation = "CONCACAF", rank = 152, points = 1545.78,
            eloRating = 1488.0, attackRating = 53, defenseRating = 55, midfieldRating = 54,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 36.0,
            pressingIntensity = 48.0, counterAttack = 50.0, setPieceAttack = 48.0,
            setPieceDefense = 50.0, tempo = 52.0, width = 34.0, defensiveLine = 32.0,
            homeWinRate = 0.34, awayWinRate = 0.12, avgGoalsFor = 0.62,
            avgGoalsAgainst = 0.4, cleanSheetRate = 0.48, bttsRate = 0.18,
            over25Rate = 0.2, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "SLV", name = "萨尔瓦多", confederation = "CONCACAF", rank = 153, points = 1544.12,
            eloRating = 1485.0, attackRating = 52, defenseRating = 54, midfieldRating = 53,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 34.0,
            pressingIntensity = 46.0, counterAttack = 48.0, setPieceAttack = 46.0,
            setPieceDefense = 48.0, tempo = 52.0, width = 32.0, defensiveLine = 30.0,
            homeWinRate = 0.32, awayWinRate = 0.1, avgGoalsFor = 0.58,
            avgGoalsAgainst = 0.38, cleanSheetRate = 0.5, bttsRate = 0.16,
            over25Rate = 0.18, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "HAI", name = "海地", confederation = "CONCACAF", rank = 154, points = 1542.45,
            eloRating = 1482.0, attackRating = 51, defenseRating = 53, midfieldRating = 52,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 32.0,
            pressingIntensity = 44.0, counterAttack = 48.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 50.0, width = 30.0, defensiveLine = 28.0,
            homeWinRate = 0.3, awayWinRate = 0.1, avgGoalsFor = 0.55,
            avgGoalsAgainst = 0.36, cleanSheetRate = 0.52, bttsRate = 0.14,
            over25Rate = 0.16, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "TRI", name = "特立尼达和多巴哥", confederation = "CONCACAF", rank = 155, points = 1540.78,
            eloRating = 1480.0, attackRating = 50, defenseRating = 52, midfieldRating = 51,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 34.0,
            pressingIntensity = 44.0, counterAttack = 46.0, setPieceAttack = 44.0,
            setPieceDefense = 46.0, tempo = 48.0, width = 28.0, defensiveLine = 26.0,
            homeWinRate = 0.28, awayWinRate = 0.08, avgGoalsFor = 0.52,
            avgGoalsAgainst = 0.34, cleanSheetRate = 0.54, bttsRate = 0.12,
            over25Rate = 0.14, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "DOM", name = "多米尼加", confederation = "CONCACAF", rank = 156, points = 1539.12,
            eloRating = 1478.0, attackRating = 49, defenseRating = 51, midfieldRating = 50,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 32.0,
            pressingIntensity = 42.0, counterAttack = 46.0, setPieceAttack = 42.0,
            setPieceDefense = 44.0, tempo = 46.0, width = 26.0, defensiveLine = 24.0,
            homeWinRate = 0.26, awayWinRate = 0.08, avgGoalsFor = 0.48,
            avgGoalsAgainst = 0.32, cleanSheetRate = 0.56, bttsRate = 0.1,
            over25Rate = 0.12, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "BAH", name = "巴哈马", confederation = "CONCACAF", rank = 157, points = 1537.45,
            eloRating = 1475.0, attackRating = 47, defenseRating = 49, midfieldRating = 48,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 28.0,
            pressingIntensity = 38.0, counterAttack = 44.0, setPieceAttack = 38.0,
            setPieceDefense = 40.0, tempo = 42.0, width = 24.0, defensiveLine = 22.0,
            homeWinRate = 0.24, awayWinRate = 0.06, avgGoalsFor = 0.42,
            avgGoalsAgainst = 0.3, cleanSheetRate = 0.58, bttsRate = 0.08,
            over25Rate = 0.1, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "BAR", name = "巴巴多斯", confederation = "CONCACAF", rank = 158, points = 1535.78,
            eloRating = 1472.0, attackRating = 46, defenseRating = 48, midfieldRating = 47,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 26.0,
            pressingIntensity = 36.0, counterAttack = 42.0, setPieceAttack = 36.0,
            setPieceDefense = 38.0, tempo = 40.0, width = 22.0, defensiveLine = 20.0,
            homeWinRate = 0.22, awayWinRate = 0.06, avgGoalsFor = 0.38,
            avgGoalsAgainst = 0.28, cleanSheetRate = 0.6, bttsRate = 0.08,
            over25Rate = 0.1, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "BER", name = "百慕大", confederation = "CONCACAF", rank = 159, points = 1534.12,
            eloRating = 1470.0, attackRating = 45, defenseRating = 47, midfieldRating = 46,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 24.0,
            pressingIntensity = 34.0, counterAttack = 40.0, setPieceAttack = 34.0,
            setPieceDefense = 36.0, tempo = 38.0, width = 20.0, defensiveLine = 18.0,
            homeWinRate = 0.2, awayWinRate = 0.04, avgGoalsFor = 0.35,
            avgGoalsAgainst = 0.26, cleanSheetRate = 0.62, bttsRate = 0.06,
            over25Rate = 0.08, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "ABW", name = "阿鲁巴", confederation = "CONCACAF", rank = 160, points = 1532.45,
            eloRating = 1468.0, attackRating = 44, defenseRating = 46, midfieldRating = 45,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 22.0,
            pressingIntensity = 32.0, counterAttack = 38.0, setPieceAttack = 32.0,
            setPieceDefense = 34.0, tempo = 36.0, width = 18.0, defensiveLine = 16.0,
            homeWinRate = 0.18, awayWinRate = 0.04, avgGoalsFor = 0.32,
            avgGoalsAgainst = 0.24, cleanSheetRate = 0.64, bttsRate = 0.06,
            over25Rate = 0.08, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "AIA", name = "安圭拉", confederation = "CONCACAF", rank = 161, points = 1530.78,
            eloRating = 1465.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "ATG", name = "安提瓜和巴布达", confederation = "CONCACAF", rank = 162, points = 1529.12,
            eloRating = 1462.0, attackRating = 43, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 20.0,
            pressingIntensity = 30.0, counterAttack = 36.0, setPieceAttack = 30.0,
            setPieceDefense = 32.0, tempo = 34.0, width = 16.0, defensiveLine = 14.0,
            homeWinRate = 0.16, awayWinRate = 0.02, avgGoalsFor = 0.28,
            avgGoalsAgainst = 0.22, cleanSheetRate = 0.66, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "DMA2", name = "多米尼克", confederation = "CONCACAF", rank = 163, points = 1527.45,
            eloRating = 1460.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "GRN", name = "格林纳达", confederation = "CONCACAF", rank = 164, points = 1525.78,
            eloRating = 1458.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "SKN", name = "圣基茨和尼维斯", confederation = "CONCACAF", rank = 165, points = 1524.12,
            eloRating = 1455.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "LCA", name = "圣卢西亚", confederation = "CONCACAF", rank = 166, points = 1522.45,
            eloRating = 1452.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "VIN", name = "圣文森特和格林纳丁斯", confederation = "CONCACAF", rank = 167, points = 1520.78,
            eloRating = 1450.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "North America", region = "Caribbean",
        ),
        TeamBaselineData(
            code = "GUY", name = "圭亚那", confederation = "CONCACAF", rank = 168, points = 1519.12,
            eloRating = 1448.0, attackRating = 39, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "South America", region = "North",
        ),
        TeamBaselineData(
            code = "BLZ", name = "伯利兹", confederation = "CONCACAF", rank = 169, points = 1517.45,
            eloRating = 1445.0, attackRating = 38, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 24.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "SUR", name = "苏里南", confederation = "CONCACAF", rank = 170, points = 1515.78,
            eloRating = 1442.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "South America", region = "North",
        ),
        TeamBaselineData(
            code = "NCA", name = "尼加拉瓜", confederation = "CONCACAF", rank = 171, points = 1514.12,
            eloRating = 1440.0, attackRating = 37, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 22.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "North America", region = "Central",
        ),
        TeamBaselineData(
            code = "TPE", name = "中华台北", confederation = "AFC", rank = 172, points = 1512.45,
            eloRating = 1438.0, attackRating = 43, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "HKG", name = "香港", confederation = "AFC", rank = 173, points = 1510.78,
            eloRating = 1435.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "MAC", name = "澳门", confederation = "AFC", rank = 174, points = 1509.12,
            eloRating = 1432.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "GUA2", name = "关岛", confederation = "AFC", rank = 175, points = 1507.45,
            eloRating = 1430.0, attackRating = 39, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "West",
        ),
        TeamBaselineData(
            code = "PRK", name = "朝鲜", confederation = "AFC", rank = 176, points = 1505.78,
            eloRating = 1428.0, attackRating = 44, defenseRating = 46, midfieldRating = 45,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 20.0,
            pressingIntensity = 30.0, counterAttack = 36.0, setPieceAttack = 30.0,
            setPieceDefense = 32.0, tempo = 34.0, width = 16.0, defensiveLine = 14.0,
            homeWinRate = 0.16, awayWinRate = 0.04, avgGoalsFor = 0.28,
            avgGoalsAgainst = 0.22, cleanSheetRate = 0.66, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "East",
        ),
        TeamBaselineData(
            code = "TJK2", name = "塔吉克斯坦", confederation = "AFC", rank = 177, points = 1504.12,
            eloRating = 1425.0, attackRating = 43, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "Central",
        ),
        TeamBaselineData(
            code = "AFG", name = "阿富汗", confederation = "AFC", rank = 178, points = 1502.45,
            eloRating = 1422.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "NEP", name = "尼泊尔", confederation = "AFC", rank = 179, points = 1500.78,
            eloRating = 1420.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "BAN", name = "孟加拉国", confederation = "AFC", rank = 180, points = 1499.12,
            eloRating = 1418.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "MDV", name = "马尔代夫", confederation = "AFC", rank = 181, points = 1497.45,
            eloRating = 1415.0, attackRating = 39, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 24.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "PAK", name = "巴基斯坦", confederation = "AFC", rank = 182, points = 1495.78,
            eloRating = 1412.0, attackRating = 38, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 22.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "SRI", name = "斯里兰卡", confederation = "AFC", rank = 183, points = 1494.12,
            eloRating = 1410.0, attackRating = 37, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 20.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.8, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "BTN", name = "不丹", confederation = "AFC", rank = 184, points = 1492.45,
            eloRating = 1408.0, attackRating = 35, defenseRating = 37, midfieldRating = 36,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 4.0,
            pressingIntensity = 14.0, counterAttack = 20.0, setPieceAttack = 14.0,
            setPieceDefense = 16.0, tempo = 18.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.08,
            avgGoalsAgainst = 0.06, cleanSheetRate = 0.82, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "South",
        ),
        TeamBaselineData(
            code = "BRU", name = "文莱", confederation = "AFC", rank = 185, points = 1490.78,
            eloRating = 1405.0, attackRating = 36, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 20.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.8, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "TLS2", name = "东帝汶", confederation = "AFC", rank = 186, points = 1489.12,
            eloRating = 1402.0, attackRating = 35, defenseRating = 37, midfieldRating = 36,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 4.0,
            pressingIntensity = 14.0, counterAttack = 20.0, setPieceAttack = 14.0,
            setPieceDefense = 16.0, tempo = 18.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.08,
            avgGoalsAgainst = 0.06, cleanSheetRate = 0.82, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "PNG", name = "巴布亚新几内亚", confederation = "OFC", rank = 187, points = 1487.45,
            eloRating = 1400.0, attackRating = 38, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 24.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "FIJ", name = "斐济", confederation = "OFC", rank = 188, points = 1485.78,
            eloRating = 1398.0, attackRating = 37, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 22.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.8, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "VAN", name = "瓦努阿图", confederation = "OFC", rank = 189, points = 1484.12,
            eloRating = 1395.0, attackRating = 36, defenseRating = 38, midfieldRating = 37,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 20.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.08,
            avgGoalsAgainst = 0.06, cleanSheetRate = 0.82, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "SOL", name = "所罗门群岛", confederation = "OFC", rank = 190, points = 1482.45,
            eloRating = 1392.0, attackRating = 35, defenseRating = 37, midfieldRating = 36,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 4.0,
            pressingIntensity = 14.0, counterAttack = 20.0, setPieceAttack = 14.0,
            setPieceDefense = 16.0, tempo = 18.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.06,
            avgGoalsAgainst = 0.04, cleanSheetRate = 0.84, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "TGA", name = "汤加", confederation = "OFC", rank = 191, points = 1480.78,
            eloRating = 1390.0, attackRating = 34, defenseRating = 36, midfieldRating = 35,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 2.0,
            pressingIntensity = 12.0, counterAttack = 18.0, setPieceAttack = 12.0,
            setPieceDefense = 14.0, tempo = 16.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.04,
            avgGoalsAgainst = 0.02, cleanSheetRate = 0.86, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "SAM", name = "萨摩亚", confederation = "OFC", rank = 192, points = 1479.12,
            eloRating = 1388.0, attackRating = 33, defenseRating = 35, midfieldRating = 34,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 0.0,
            pressingIntensity = 10.0, counterAttack = 16.0, setPieceAttack = 10.0,
            setPieceDefense = 12.0, tempo = 14.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.04,
            avgGoalsAgainst = 0.02, cleanSheetRate = 0.86, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "COK", name = "库克群岛", confederation = "OFC", rank = 193, points = 1477.45,
            eloRating = 1385.0, attackRating = 32, defenseRating = 34, midfieldRating = 33,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 0.0,
            pressingIntensity = 8.0, counterAttack = 14.0, setPieceAttack = 8.0,
            setPieceDefense = 10.0, tempo = 12.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.02,
            avgGoalsAgainst = 0.02, cleanSheetRate = 0.88, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "TUV", name = "图瓦卢", confederation = "OFC", rank = 194, points = 1475.78,
            eloRating = 1382.0, attackRating = 31, defenseRating = 33, midfieldRating = 32,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 0.0,
            pressingIntensity = 6.0, counterAttack = 12.0, setPieceAttack = 6.0,
            setPieceDefense = 8.0, tempo = 10.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.02,
            avgGoalsAgainst = 0.02, cleanSheetRate = 0.88, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "ASA", name = "美属萨摩亚", confederation = "OFC", rank = 195, points = 1474.12,
            eloRating = 1380.0, attackRating = 30, defenseRating = 32, midfieldRating = 31,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 0.0,
            pressingIntensity = 4.0, counterAttack = 10.0, setPieceAttack = 4.0,
            setPieceDefense = 6.0, tempo = 8.0, width = 0.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.02,
            avgGoalsAgainst = 0.02, cleanSheetRate = 0.9, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Oceania", region = "South",
        ),
        TeamBaselineData(
            code = "BAH2", name = "巴林", confederation = "AFC", rank = 196, points = 1472.45,
            eloRating = 1378.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "OMA", name = "阿曼", confederation = "AFC", rank = 197, points = 1470.78,
            eloRating = 1375.0, attackRating = 43, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-4-2", tacticalStyle = "DEFENSIVE", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "UAE", name = "阿联酋", confederation = "AFC", rank = 198, points = 1469.12,
            eloRating = 1372.0, attackRating = 44, defenseRating = 46, midfieldRating = 45,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "YEM", name = "也门", confederation = "AFC", rank = 199, points = 1467.45,
            eloRating = 1370.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "West",
        ),
        TeamBaselineData(
            code = "MAS", name = "马来西亚", confederation = "AFC", rank = 200, points = 1465.78,
            eloRating = 1368.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "IDN", name = "印度尼西亚", confederation = "AFC", rank = 201, points = 1464.12,
            eloRating = 1365.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-4-2", tacticalStyle = "BALANCED", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Asia", region = "Southeast",
        ),
        TeamBaselineData(
            code = "CAM2", name = "喀麦隆", confederation = "CAF", rank = 202, points = 1462.45,
            eloRating = 1362.0, attackRating = 43, defenseRating = 45, midfieldRating = 44,
            preferredFormation = "4-4-2", tacticalStyle = "COUNTER_ATTACK", possession = 18.0,
            pressingIntensity = 28.0, counterAttack = 34.0, setPieceAttack = 28.0,
            setPieceDefense = 30.0, tempo = 32.0, width = 14.0, defensiveLine = 12.0,
            homeWinRate = 0.14, awayWinRate = 0.02, avgGoalsFor = 0.25,
            avgGoalsAgainst = 0.2, cleanSheetRate = 0.68, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "GAB2", name = "加蓬", confederation = "CAF", rank = 203, points = 1460.78,
            eloRating = 1360.0, attackRating = 42, defenseRating = 44, midfieldRating = 43,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 16.0,
            pressingIntensity = 26.0, counterAttack = 32.0, setPieceAttack = 26.0,
            setPieceDefense = 28.0, tempo = 30.0, width = 12.0, defensiveLine = 10.0,
            homeWinRate = 0.12, awayWinRate = 0.02, avgGoalsFor = 0.22,
            avgGoalsAgainst = 0.18, cleanSheetRate = 0.7, bttsRate = 0.04,
            over25Rate = 0.06, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "CGO", name = "刚果", confederation = "CAF", rank = 204, points = 1459.12,
            eloRating = 1358.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "EQG", name = "赤道几内亚", confederation = "CAF", rank = 205, points = 1457.45,
            eloRating = 1355.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Central",
        ),
        TeamBaselineData(
            code = "GEQ", name = "几内亚", confederation = "CAF", rank = 206, points = 1455.78,
            eloRating = 1352.0, attackRating = 41, defenseRating = 43, midfieldRating = 42,
            preferredFormation = "4-4-2", tacticalStyle = "COUNTER_ATTACK", possession = 14.0,
            pressingIntensity = 24.0, counterAttack = 30.0, setPieceAttack = 24.0,
            setPieceDefense = 26.0, tempo = 28.0, width = 10.0, defensiveLine = 8.0,
            homeWinRate = 0.1, awayWinRate = 0.02, avgGoalsFor = 0.2,
            avgGoalsAgainst = 0.16, cleanSheetRate = 0.72, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "GUI", name = "几内亚", confederation = "CAF", rank = 207, points = 1454.12,
            eloRating = 1350.0, attackRating = 40, defenseRating = 42, midfieldRating = 41,
            preferredFormation = "4-4-2", tacticalStyle = "DIRECT", possession = 12.0,
            pressingIntensity = 22.0, counterAttack = 28.0, setPieceAttack = 22.0,
            setPieceDefense = 24.0, tempo = 26.0, width = 8.0, defensiveLine = 6.0,
            homeWinRate = 0.08, awayWinRate = 0.02, avgGoalsFor = 0.18,
            avgGoalsAgainst = 0.14, cleanSheetRate = 0.74, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "KEN2", name = "肯尼亚", confederation = "CAF", rank = 208, points = 1452.45,
            eloRating = 1348.0, attackRating = 39, defenseRating = 41, midfieldRating = 40,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 10.0,
            pressingIntensity = 20.0, counterAttack = 26.0, setPieceAttack = 20.0,
            setPieceDefense = 22.0, tempo = 24.0, width = 6.0, defensiveLine = 4.0,
            homeWinRate = 0.06, awayWinRate = 0.02, avgGoalsFor = 0.15,
            avgGoalsAgainst = 0.12, cleanSheetRate = 0.76, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "East",
        ),
        TeamBaselineData(
            code = "LIB", name = "利比里亚", confederation = "CAF", rank = 209, points = 1450.78,
            eloRating = 1345.0, attackRating = 38, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 22.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
        TeamBaselineData(
            code = "MAL", name = "马拉维", confederation = "CAF", rank = 210, points = 1449.12,
            eloRating = 1342.0, attackRating = 37, defenseRating = 39, midfieldRating = 38,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 6.0,
            pressingIntensity = 16.0, counterAttack = 22.0, setPieceAttack = 16.0,
            setPieceDefense = 18.0, tempo = 20.0, width = 2.0, defensiveLine = 0.0,
            homeWinRate = 0.02, awayWinRate = 0.02, avgGoalsFor = 0.1,
            avgGoalsAgainst = 0.08, cleanSheetRate = 0.8, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "Southern",
        ),
        TeamBaselineData(
            code = "MLI2", name = "马里", confederation = "CAF", rank = 211, points = 1447.45,
            eloRating = 1340.0, attackRating = 38, defenseRating = 40, midfieldRating = 39,
            preferredFormation = "4-5-1", tacticalStyle = "DEFENSIVE", possession = 8.0,
            pressingIntensity = 18.0, counterAttack = 24.0, setPieceAttack = 18.0,
            setPieceDefense = 20.0, tempo = 22.0, width = 4.0, defensiveLine = 2.0,
            homeWinRate = 0.04, awayWinRate = 0.02, avgGoalsFor = 0.12,
            avgGoalsAgainst = 0.1, cleanSheetRate = 0.78, bttsRate = 0.02,
            over25Rate = 0.04, continent = "Africa", region = "West",
        ),
    )

    private val byCode = ALL_TEAMS.associateBy { it.code }

    fun findByCode(code: String): TeamBaselineData? = byCode[code]

    fun findByRank(rank: Int): TeamBaselineData? = ALL_TEAMS.find { it.rank == rank }

    fun findByConfederation(confederation: String): List<TeamBaselineData> =
        ALL_TEAMS.filter { it.confederation == confederation }

    fun findByContinent(continent: String): List<TeamBaselineData> =
        ALL_TEAMS.filter { it.continent == continent }

    fun topTeams(n: Int = 20): List<TeamBaselineData> =
        ALL_TEAMS.sortedBy { it.rank }.take(n)

    fun averagePoints(): Double =
        ALL_TEAMS.map { it.points }.average()

    fun averageElo(): Double =
        ALL_TEAMS.map { it.eloRating }.average()
}
// =============================================================================
// 第十二部分：贝叶斯推断引擎
// -----------------------------------------------------------------------------
// 基于贝叶斯定理的多层概率推断系统。
// 核心公式：P(A|B) = P(B|A) * P(A) / P(B)
// 通过先验概率、似然函数和边缘概率的乘除运算，
// 逐步更新后验概率分布，实现严格的数据驱动推断。
// =============================================================================

object BayesianEngine {

    /** 贝叶斯更新：用新证据更新先验概率
     * @param prior 先验概率 P(A)
     * @param likelihood 似然 P(B|A)
     * @param evidence 边缘概率 P(B)
     * @return 后验概率 P(A|B)
     */
    fun bayesUpdate(prior: Double, likelihood: Double, evidence: Double): Double {
        if (evidence <= 0.0) return prior
        return (likelihood * prior) / evidence
    }

    /** 多证据贝叶斯顺序更新
     * 每条新证据依次修正后验概率
     */
    fun sequentialBayesUpdate(
        prior: Double,
        likelihoods: List<Double>,
        evidences: List<Double>,
    ): Double {
        var posterior = prior
        for (i in likelihoods.indices) {
            val ev = evidences.getOrElse(i) { 1.0 }
            posterior = bayesUpdate(posterior, likelihoods[i], ev)
            posterior = posterior.coerceIn(0.001, 0.999)
        }
        return posterior
    }

    /** Beta 分布先验：用于胜率的贝叶斯估计
     * Beta(alpha, beta) 作为胜率的共轭先验
     * 后验 = Beta(alpha + wins, beta + losses)
     * 均值 = alpha / (alpha + beta)
     */
    fun betaMean(alpha: Double, beta: Double): Double {
        val sum = alpha + beta
        if (sum <= 0.0) return 0.5
        return alpha / sum
    }

    /** Beta 分布方差：衡量不确定性
     * Var = alpha * beta / ((alpha + beta)^2 * (alpha + beta + 1))
     */
    fun betaVariance(alpha: Double, beta: Double): Double {
        val sum = alpha + beta
        if (sum <= 0.0) return 0.25
        val sumSq = sum * sum
        val sumPlus1 = sum + 1.0
        if (sumPlus1 <= 0.0) return 0.0
        return (alpha * beta) / (sumSq * sumPlus1)
    }

    /** Beta 分布众数：最可能的胜率值
     * Mode = (alpha - 1) / (alpha + beta - 2)  当 alpha,beta > 1
     */
    fun betaMode(alpha: Double, beta: Double): Double {
        val sum = alpha + beta - 2.0
        if (alpha <= 1.0 || beta <= 1.0 || sum <= 0.0) return betaMean(alpha, beta)
        return (alpha - 1.0) / sum
    }

    /** 从历史胜负平更新胜率先验
     * 先验 Beta(2, 2) -> 后验 Beta(2 + wins, 2 + draws + losses)
     * 通过加减运算融合历史数据
     */
    fun updateWinRate(wins: Int, draws: Int, losses: Int): Double {
        val alpha = 2.0 + wins.toDouble() + draws.toDouble() * 0.5
        val beta = 2.0 + losses.toDouble() + draws.toDouble() * 0.5
        return betaMean(alpha, beta)
    }

    /** 从历史进球更新进球率先验
     * 使用 Gamma-Poisson 共轭
     * 先验 Gamma(1, 1) -> 后验 Gamma(1 + totalGoals, 1 + matches)
     */
    fun updateGoalRate(totalGoals: Int, matches: Int): Double {
        val alpha = 1.0 + totalGoals.toDouble()
        val beta = 1.0 + matches.toDouble()
        if (beta <= 0.0) return PredictionConstants.BASE_GOALS
        return alpha / beta
    }

    /** 贝叶斯因子：比较两个假设的相对支持度
     * BF = P(Data|H1) / P(Data|H2)
     * BF > 3 表示 H1 有中等支持，> 10 为强支持
     */
    fun bayesFactor(likelihoodH1: Double, likelihoodH2: Double): Double {
        if (likelihoodH2 <= 0.0) return Double.MAX_VALUE
        return likelihoodH1 / likelihoodH2
    }

    /** 朴素贝叶斯分类器：预测比赛结果
     * P(胜|特征) 正比于 P(胜) * P(特征1|胜) * P(特征2|胜) * ...
     * 通过乘法运算组合各特征条件概率
     */
    fun naiveBayesClassify(
        classPriors: List<Double>,
        conditionalProbs: List<List<Double>>,
    ): List<Double> {
        val nClasses = classPriors.size
        if (nClasses == 0) return emptyList()
        val posteriors = ArrayList<Double>(nClasses)
        for (c in 0 until nClasses) {
            var p = classPriors[c]
            for (featureProbs in conditionalProbs) {
                if (c < featureProbs.size) {
                    p *= featureProbs[c]
                }
            }
            posteriors.add(p)
        }
        val sum = posteriors.sum()
        return if (sum > 0) posteriors.map { it / sum } else posteriors
    }

    /** Beta-Binomial 模型：小样本胜率收缩
     * 收缩后胜率 = (alpha + wins) / (alpha + beta + n)
     * 通过加法将先验与数据融合
     */
    fun betaBinomialShrink(
        observedWinRate: Double,
        sampleSize: Int,
        priorStrength: Double = 5.0,
    ): Double {
        val priorMean = 0.4
        val shrink = priorStrength / (priorStrength + sampleSize.toDouble())
        val result = observedWinRate * (1.0 - shrink) + priorMean * shrink
        return result.coerceIn(0.05, 0.95)
    }

    /** Dirichlet 分布采样：多结果概率
     * 用于胜-平-负三结果的概率推断
     * 均值: alpha_i / sum(alpha)
     */
    fun dirichletMean(alphas: List<Double>): List<Double> {
        val sum = alphas.sum()
        if (sum <= 0.0) return alphas.map { 1.0 / alphas.size }
        return alphas.map { it / sum }
    }

    /** Dirichlet 方差：衡量各结果的不确定性
     * Var(alpha_i) = alpha_i * (sum - alpha_i) / (sum^2 * (sum + 1))
     */
    fun dirichletVariance(alphas: List<Double>): List<Double> {
        val sum = alphas.sum()
        if (sum <= 0.0) return alphas.map { 0.0 }
        val sumSq = sum * sum
        val sumPlus1 = sum + 1.0
        return alphas.map { it * (sum - it) / (sumSq * sumPlus1) }
    }

    /** 三结果收缩：将历史胜平负率收缩到先验
     * 通过加权平均运算
     */
    fun shrinkTriple(
        observed: Triple<Double, Double, Double>,
        prior: Triple<Double, Double, Double>,
        sampleSize: Int,
        strength: Double = 5.0,
    ): Triple<Double, Double, Double> {
        val w = strength / (strength + sampleSize.toDouble())
        val w2 = 1.0 - w
        val r1 = observed.first * w2 + prior.first * w
        val r2 = observed.second * w2 + prior.second * w
        val r3 = observed.third * w2 + prior.third * w
        val sum = r1 + r2 + r3
        return Triple(r1 / sum, r2 / sum, r3 / sum)
    }
}

// =============================================================================
// 第十三部分：马尔可夫链比赛进程模型
// -----------------------------------------------------------------------------
// 将比赛状态建模为马尔可夫链，当前状态只依赖前一状态。
// 状态空间：比分差距 (-5 到 +5)、时间段、控球方。
// 转移概率通过历史数据统计 + 加减运算计算。
// P(next_state | current_state) = transition_matrix[state][next]
// =============================================================================

object MarkovChainModel {

    const val SCORE_DIFF_RANGE = 5  // -5 到 +5
    const val TIME_SLOTS = 9       // 0-10, 10-20, ..., 80-90
    const val NUM_STATES = (2 * SCORE_DIFF_RANGE + 1) * TIME_SLOTS

    /** 状态编码：将比分差和时间段编码为状态索引 */
    fun encodeState(scoreDiff: Int, timeSlot: Int): Int {
        val sd = scoreDiff.coerceIn(-SCORE_DIFF_RANGE, SCORE_DIFF_RANGE)
        val ts = timeSlot.coerceIn(0, TIME_SLOTS - 1)
        return (sd + SCORE_DIFF_RANGE) * TIME_SLOTS + ts
    }

    /** 状态解码：从索引还原比分差和时间段 */
    fun decodeState(state: Int): Pair<Int, Int> {
        val ts = state % TIME_SLOTS
        val sd = state / TIME_SLOTS - SCORE_DIFF_RANGE
        return sd to ts
    }

    /** 构建基础转移矩阵
     * 基于进球率和时间衰减构建
     * 转移概率通过泊松分布的加减运算得出
     */
    fun buildTransitionMatrix(xgHome: Double, xgAway: Double): Array<DoubleArray> {
        val n = NUM_STATES
        val matrix = Array(n) { DoubleArray(n) }
        val ratePerSlot = (xgHome + xgAway) / TIME_SLOTS.toDouble()
        val homeShare = if (xgHome + xgAway > 0) xgHome / (xgHome + xgAway) else 0.5

        for (s in 0 until n) {
            val (scoreDiff, timeSlot) = decodeState(s)
            if (timeSlot >= TIME_SLOTS - 1) {
                matrix[s][s] = 1.0  // 终态吸收
                continue
            }
            val noGoalProb = exp(-ratePerSlot)
            val homeGoalProb = (1.0 - noGoalProb) * homeShare
            val awayGoalProb = (1.0 - noGoalProb) * (1.0 - homeShare)
            val nextSlot = timeSlot + 1

            val stayState = encodeState(scoreDiff, nextSlot)
            matrix[s][stayState] = noGoalProb

            val newDiffUp = (scoreDiff + 1).coerceIn(-SCORE_DIFF_RANGE, SCORE_DIFF_RANGE)
            val upState = encodeState(newDiffUp, nextSlot)
            matrix[s][upState] += homeGoalProb

            val newDiffDown = (scoreDiff - 1).coerceIn(-SCORE_DIFF_RANGE, SCORE_DIFF_RANGE)
            val downState = encodeState(newDiffDown, nextSlot)
            matrix[s][downState] += awayGoalProb
        }
        return matrix
    }

    /** 矩阵乘法：推进一个时间步
     * P_next = P_current * Transition
     * 通过乘法加法运算实现状态转移
     */
    fun stepForward(
        currentDist: DoubleArray,
        transition: Array<DoubleArray>,
    ): DoubleArray {
        val n = currentDist.size
        val next = DoubleArray(n)
        for (j in 0 until n) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += currentDist[i] * transition[i][j]
            }
            next[j] = sum
        }
        return next
    }

    /** 多步预测：从初始状态推进 N 步
     * 通过反复乘法运算获得最终状态分布
     */
    fun multiStepPredict(
        initial: DoubleArray,
        transition: Array<DoubleArray>,
        steps: Int,
    ): DoubleArray {
        var dist = initial
        repeat(steps) { dist = stepForward(dist, transition) }
        return dist
    }

    /** 从状态分布提取三结果概率
     * 比分差 > 0 = 主胜，= 0 = 平，< 0 = 客胜
     * 通过条件求和运算
     */
    fun extractProbabilities(dist: DoubleArray): Triple<Double, Double, Double> {
        var pHome = 0.0; var pDraw = 0.0; var pAway = 0.0
        for (s in dist.indices) {
            val (sd, _) = decodeState(s)
            when {
                sd > 0 -> pHome += dist[s]
                sd == 0 -> pDraw += dist[s]
                else -> pAway += dist[s]
            }
        }
        val sum = pHome + pDraw + pAway
        return if (sum > 0) Triple(pHome / sum, pDraw / sum, pAway / sum)
               else Triple(0.4, 0.28, 0.32)
    }

    /** 计算稳态分布（如果有）
     * pi * P = pi
     * 通过迭代乘法逼近
     */
    fun steadyState(transition: Array<DoubleArray>, iterations: Int = 200): DoubleArray {
        val n = transition.size
        var dist = DoubleArray(n) { 1.0 / n }
        repeat(iterations) {
            dist = stepForward(dist, transition)
            val sum = dist.sum()
            if (sum > 0) for (i in dist.indices) dist[i] /= sum
        }
        return dist
    }

    /** 吸收态分析：计算从某状态到吸收态的期望时间
     * 基本矩阵 N = (I - Q)^{-1}
     * 期望步数 = N * 1 向量
     */
    fun expectedTimeToAbsorb(
        transition: Array<DoubleArray>,
        startState: Int,
    ): Double {
        val n = transition.size
        var totalProb = 1.0
        var expectedTime = 0.0
        var current = DoubleArray(n)
        current[startState] = 1.0
        for (t in 1..90) {
            current = stepForward(current, transition)
            val absorbed = current[n - 1]
            expectedTime += t.toDouble() * absorbed * totalProb
            totalProb *= (1.0 - absorbed)
            if (totalProb < 0.001) break
        }
        return expectedTime
    }
}

// =============================================================================
// 第十四部分：梯度下降参数优化器
// -----------------------------------------------------------------------------
// 通过迭代减法调整参数，最小化预测误差。
// θ_new = θ_old - learning_rate * gradient
// 梯度通过有限差分法计算：grad ≈ (f(θ+ε) - f(θ-ε)) / (2ε)
// =============================================================================

object GradientDescentOptimizer {

    data class OptimizationResult(
        val parameters: DoubleArray,
        val finalLoss: Double,
        val iterations: Int,
        val converged: Boolean,
        val history: List<Double>,
    )

    /** 均方误差损失函数
     * MSE = (1/n) * Σ(predicted - actual)^2
     * 通过减法和乘法运算
     */
    fun mseLoss(predicted: List<Double>, actual: List<Double>): Double {
        if (predicted.size != actual.size || predicted.isEmpty()) return 0.0
        var sum = 0.0
        for (i in predicted.indices) {
            val diff = predicted[i] - actual[i]
            sum += diff * diff
        }
        return sum / predicted.size
    }

    /** 交叉熵损失函数
     * CE = -Σ actual * log(predicted)
     * 通过乘法和对数运算
     */
    fun crossEntropyLoss(predicted: List<Double>, actual: List<Double>): Double {
        if (predicted.isEmpty()) return 0.0
        var sum = 0.0
        for (i in predicted.indices) {
            val p = predicted[i].coerceIn(1e-10, 1.0 - 1e-10)
            val a = actual[i]
            sum -= a * kotlin.math.ln(p)
        }
        return sum / predicted.size
    }

    /** 有限差分梯度
     * grad_i = (f(θ + ε_i) - f(θ - ε_i)) / (2 * ε)
     * 通过加减法运算近似梯度
     */
    fun numericalGradient(
        parameters: DoubleArray,
        lossFunction: (DoubleArray) -> Double,
        epsilon: Double = 1e-5,
    ): DoubleArray {
        val grad = DoubleArray(parameters.size)
        for (i in parameters.indices) {
            val original = parameters[i]
            parameters[i] = original + epsilon
            val lossPlus = lossFunction(parameters)
            parameters[i] = original - epsilon
            val lossMinus = lossFunction(parameters)
            parameters[i] = original
            grad[i] = (lossPlus - lossMinus) / (2.0 * epsilon)
        }
        return grad
    }

    /** 梯度下降优化
     * θ = θ - lr * grad
     * 通过减法迭代更新
     */
    fun optimize(
        initialParams: DoubleArray,
        lossFunction: (DoubleArray) -> Double,
        learningRate: Double = 0.01,
        maxIterations: Int = 500,
        tolerance: Double = 1e-6,
    ): OptimizationResult {
        val params = initialParams.copyOf()
        val history = ArrayList<Double>()
        var prevLoss = Double.MAX_VALUE
        var converged = false
        var iter = 0

        while (iter < maxIterations) {
            val loss = lossFunction(params)
            history.add(loss)
            if (abs(prevLoss - loss) < tolerance) {
                converged = true
                break
            }
            prevLoss = loss
            val grad = numericalGradient(params, lossFunction)
            for (i in params.indices) {
                params[i] -= learningRate * grad[i]
            }
            iter++
        }
        return OptimizationResult(params, prevLoss, iter, converged, history)
    }

    /** Adam 优化器：自适应学习率
     * m = β1*m + (1-β1)*grad
     * v = β2*v + (1-β2)*grad^2
     * θ = θ - lr * m_hat / (sqrt(v_hat) + ε)
     * 通过乘除加减混合运算
     */
    fun adamOptimize(
        initialParams: DoubleArray,
        lossFunction: (DoubleArray) -> Double,
        learningRate: Double = 0.001,
        maxIterations: Int = 500,
        beta1: Double = 0.9,
        beta2: Double = 0.999,
        epsilon: Double = 1e-8,
    ): OptimizationResult {
        val params = initialParams.copyOf()
        val m = DoubleArray(params.size)
        val v = DoubleArray(params.size)
        val history = ArrayList<Double>()
        var prevLoss = Double.MAX_VALUE
        var converged = false
        var iter = 0

        while (iter < maxIterations) {
            val loss = lossFunction(params)
            history.add(loss)
            if (abs(prevLoss - loss) < 1e-7) {
                converged = true
                break
            }
            prevLoss = loss
            val grad = numericalGradient(params, lossFunction)
            val t = iter + 1
            for (i in params.indices) {
                m[i] = beta1 * m[i] + (1.0 - beta1) * grad[i]
                v[i] = beta2 * v[i] + (1.0 - beta2) * grad[i] * grad[i]
                val mHat = m[i] / (1.0 - beta1.pow(t))
                val vHat = v[i] / (1.0 - beta2.pow(t))
                params[i] -= learningRate * mHat / (sqrt(vHat) + epsilon)
            }
            iter++
        }
        return OptimizationResult(params, prevLoss, iter, converged, history)
    }

    /** 网格搜索：寻找最优参数组合
     * 通过遍历加减步进参数空间
     */
    fun gridSearch(
        paramRanges: List<Pair<Double, Double>>,
        steps: Int,
        lossFunction: (DoubleArray) -> Double,
    ): OptimizationResult {
        val dimensions = paramRanges.size
        val stepSizes = paramRanges.map { (it.second - it.first) / steps }
        val bestParams = DoubleArray(dimensions) { paramRanges[it].first }
        var bestLoss = lossFunction(bestParams)
        val history = ArrayList<Double>()

        fun search(dim: Int, current: DoubleArray) {
            if (dim == dimensions) {
                val loss = lossFunction(current)
                history.add(loss)
                if (loss < bestLoss) {
                    bestLoss = loss
                    for (i in current.indices) bestParams[i] = current[i]
                }
                return
            }
            for (s in 0..steps) {
                current[dim] = paramRanges[dim].first + stepSizes[dim] * s
                search(dim + 1, current)
            }
        }
        search(0, DoubleArray(dimensions))
        return OptimizationResult(bestParams, bestLoss, history.size, true, history)
    }
}

// =============================================================================
// 第十五部分：经典比赛历史数据库
// -----------------------------------------------------------------------------
// 收录世界杯、洲际杯赛经典比赛的详细数据，
// 用于历史交战分析和模式识别。
// =============================================================================

data class HistoricalMatch(
    val id: String,
    val date: String,
    val competition: String,
    val homeCode: String,
    val awayCode: String,
    val homeScore: Int,
    val awayScore: Int,
    val stage: String,
    val venue: String,
    val attendance: Int,
    val homePossession: Double,
    val awayPossession: Double,
    val homeShots: Int,
    val awayShots: Int,
    val homeShotsOnTarget: Int,
    val awayShotsOnTarget: Int,
    val homeCorners: Int,
    val awayCorners: Int,
    val homeFouls: Int,
    val awayFouls: Int,
    val homeYellowCards: Int,
    val awayYellowCards: Int,
    val homeRedCards: Int,
    val awayRedCards: Int,
    val homePasses: Int,
    val awayPasses: Int,
    val homePassAccuracy: Double,
    val awayPassAccuracy: Double,
)

object HistoricalMatchDatabase {

    val CLASSIC_MATCHES: List<HistoricalMatch> = listOf(
        HistoricalMatch(
            id = "WC2022_F", date = "2022-12-18", competition = "FIFA World Cup 2022 Final",
            homeCode = "ARG", awayCode = "FRA", homeScore = 3, awayScore = 2,
            stage = "Final", venue = "Lusail Stadium", attendance = 88966,
            homePossession = 57.0, awayPossession = 43.0,
            homeShots = 17, awayShots = 10,
            homeShotsOnTarget = 7, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 3,
            homeFouls = 22, awayFouls = 19,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 583, awayPasses = 421,
            homePassAccuracy = 87.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC2018_F", date = "2018-07-15", competition = "FIFA World Cup 2018 Final",
            homeCode = "FRA", awayCode = "CRO", homeScore = 4, awayScore = 2,
            stage = "Final", venue = "Luzhniki Stadium", attendance = 78011,
            homePossession = 39.0, awayPossession = 61.0,
            homeShots = 8, awayShots = 15,
            homeShotsOnTarget = 6, awayShotsOnTarget = 6,
            homeCorners = 2, awayCorners = 5,
            homeFouls = 14, awayFouls = 21,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 294, awayPasses = 508,
            homePassAccuracy = 82.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2014_F", date = "2014-07-13", competition = "FIFA World Cup 2014 Final",
            homeCode = "GER", awayCode = "ARG", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Maracana Stadium", attendance = 74738,
            homePossession = 52.0, awayPossession = 48.0,
            homeShots = 10, awayShots = 11,
            homeShotsOnTarget = 5, awayShotsOnTarget = 5,
            homeCorners = 3, awayCorners = 3,
            homeFouls = 13, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 487, awayPasses = 376,
            homePassAccuracy = 88.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "WC2010_F", date = "2010-07-11", competition = "FIFA World Cup 2010 Final",
            homeCode = "ESP", awayCode = "NED", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Soccer City", attendance = 84490,
            homePossession = 57.0, awayPossession = 43.0,
            homeShots = 18, awayShots = 12,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 6, awayCorners = 4,
            homeFouls = 14, awayFouls = 23,
            homeYellowCards = 5, awayYellowCards = 8,
            homeRedCards = 0, awayRedCards = 1,
            homePasses = 654, awayPasses = 356,
            homePassAccuracy = 88.0, awayPassAccuracy = 79.0,
        ),
        HistoricalMatch(
            id = "WC2006_F", date = "2006-07-09", competition = "FIFA World Cup 2006 Final",
            homeCode = "ITA", awayCode = "FRA", homeScore = 1, awayScore = 1,
            stage = "Final", venue = "Olympiastadion Berlin", attendance = 64000,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 12, awayShots = 14,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 4, awayCorners = 4,
            homeFouls = 24, awayFouls = 22,
            homeYellowCards = 4, awayYellowCards = 4,
            homeRedCards = 0, awayRedCards = 1,
            homePasses = 395, awayPasses = 438,
            homePassAccuracy = 85.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "WC2002_F", date = "2002-06-30", competition = "FIFA World Cup 2002 Final",
            homeCode = "BRA", awayCode = "GER", homeScore = 2, awayScore = 0,
            stage = "Final", venue = "Yokohama Stadium", attendance = 69029,
            homePossession = 53.0, awayPossession = 47.0,
            homeShots = 15, awayShots = 12,
            homeShotsOnTarget = 7, awayShotsOnTarget = 4,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 425, awayPasses = 392,
            homePassAccuracy = 84.0, awayPassAccuracy = 83.0,
        ),
        HistoricalMatch(
            id = "WC1998_F", date = "1998-07-12", competition = "FIFA World Cup 1998 Final",
            homeCode = "FRA", awayCode = "BRA", homeScore = 3, awayScore = 0,
            stage = "Final", venue = "Stade de France", attendance = 75000,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 16, awayShots = 10,
            homeShotsOnTarget = 8, awayShotsOnTarget = 4,
            homeCorners = 6, awayCorners = 3,
            homeFouls = 18, awayFouls = 15,
            homeYellowCards = 1, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 385, awayPasses = 420,
            homePassAccuracy = 82.0, awayPassAccuracy = 80.0,
        ),
        HistoricalMatch(
            id = "WC1994_F", date = "1994-07-17", competition = "FIFA World Cup 1994 Final",
            homeCode = "BRA", awayCode = "ITA", homeScore = 0, awayScore = 0,
            stage = "Final", venue = "Rose Bowl", attendance = 94194,
            homePossession = 50.0, awayPossession = 50.0,
            homeShots = 14, awayShots = 10,
            homeShotsOnTarget = 6, awayShotsOnTarget = 4,
            homeCorners = 5, awayCorners = 5,
            homeFouls = 20, awayFouls = 22,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 350, awayPasses = 340,
            homePassAccuracy = 80.0, awayPassAccuracy = 81.0,
        ),
        HistoricalMatch(
            id = "WC1990_F", date = "1990-07-08", competition = "FIFA World Cup 1990 Final",
            homeCode = "GER", awayCode = "ARG", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Stadio Olimpico", attendance = 73603,
            homePossession = 55.0, awayPossession = 45.0,
            homeShots = 12, awayShots = 8,
            homeShotsOnTarget = 5, awayShotsOnTarget = 3,
            homeCorners = 4, awayCorners = 2,
            homeFouls = 18, awayFouls = 22,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 2,
            homePasses = 400, awayPasses = 310,
            homePassAccuracy = 85.0, awayPassAccuracy = 78.0,
        ),
        HistoricalMatch(
            id = "WC1986_F", date = "1986-06-29", competition = "FIFA World Cup 1986 Final",
            homeCode = "ARG", awayCode = "GER", homeScore = 3, awayScore = 2,
            stage = "Final", venue = "Estadio Azteca", attendance = 114600,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 11, awayShots = 13,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 3, awayCorners = 4,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 380,
            homePassAccuracy = 78.0, awayPassAccuracy = 80.0,
        ),
        HistoricalMatch(
            id = "WC2022_SF1", date = "2022-12-13", competition = "FIFA World Cup 2022 SF",
            homeCode = "ARG", awayCode = "CRO", homeScore = 3, awayScore = 0,
            stage = "Semifinal", venue = "Lusail Stadium", attendance = 88966,
            homePossession = 56.0, awayPossession = 44.0,
            homeShots = 14, awayShots = 10,
            homeShotsOnTarget = 6, awayShotsOnTarget = 4,
            homeCorners = 4, awayCorners = 3,
            homeFouls = 15, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 385,
            homePassAccuracy = 86.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "WC2022_SF2", date = "2022-12-14", competition = "FIFA World Cup 2022 SF",
            homeCode = "FRA", awayCode = "MAR", homeScore = 2, awayScore = 0,
            stage = "Semifinal", venue = "Al Bayt Stadium", attendance = 68895,
            homePossession = 58.0, awayPossession = 42.0,
            homeShots = 13, awayShots = 8,
            homeShotsOnTarget = 6, awayShotsOnTarget = 3,
            homeCorners = 5, awayCorners = 2,
            homeFouls = 12, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 510, awayPasses = 320,
            homePassAccuracy = 88.0, awayPassAccuracy = 82.0,
        ),
        HistoricalMatch(
            id = "WC2018_SF1", date = "2018-07-10", competition = "FIFA World Cup 2018 SF",
            homeCode = "FRA", awayCode = "BEL", homeScore = 1, awayScore = 0,
            stage = "Semifinal", venue = "Saint Petersburg Stadium", attendance = 64286,
            homePossession = 42.0, awayPossession = 58.0,
            homeShots = 8, awayShots = 16,
            homeShotsOnTarget = 5, awayShotsOnTarget = 7,
            homeCorners = 3, awayCorners = 6,
            homeFouls = 18, awayFouls = 14,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 340, awayPasses = 580,
            homePassAccuracy = 80.0, awayPassAccuracy = 88.0,
        ),
        HistoricalMatch(
            id = "WC2018_SF2", date = "2018-07-11", competition = "FIFA World Cup 2018 SF",
            homeCode = "CRO", awayCode = "ENG", homeScore = 2, awayScore = 1,
            stage = "Semifinal", venue = "Luzhniki Stadium", attendance = 78011,
            homePossession = 45.0, awayPossession = 55.0,
            homeShots = 15, awayShots = 12,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 20, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 420, awayPasses = 485,
            homePassAccuracy = 84.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2014_SF1", date = "2014-07-08", competition = "FIFA World Cup 2014 SF",
            homeCode = "GER", awayCode = "BRA", homeScore = 7, awayScore = 1,
            stage = "Semifinal", venue = "Mineirao Stadium", attendance = 58141,
            homePossession = 46.0, awayPossession = 54.0,
            homeShots = 14, awayShots = 13,
            homeShotsOnTarget = 10, awayShotsOnTarget = 5,
            homeCorners = 2, awayCorners = 7,
            homeFouls = 14, awayFouls = 18,
            homeYellowCards = 1, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 350, awayPasses = 420,
            homePassAccuracy = 82.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC2014_SF2", date = "2014-07-09", competition = "FIFA World Cup 2014 SF",
            homeCode = "NED", awayCode = "ARG", homeScore = 0, awayScore = 0,
            stage = "Semifinal", venue = "Arena Corinthians", attendance = 63267,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 12, awayShots = 15,
            homeShotsOnTarget = 4, awayShotsOnTarget = 6,
            homeCorners = 3, awayCorners = 4,
            homeFouls = 18, awayFouls = 20,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 380, awayPasses = 450,
            homePassAccuracy = 83.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "EURO2020_F", date = "2021-07-11", competition = "Euro 2020 Final",
            homeCode = "ITA", awayCode = "ENG", homeScore = 1, awayScore = 1,
            stage = "Final", venue = "Wembley Stadium", attendance = 67273,
            homePossession = 43.0, awayPossession = 57.0,
            homeShots = 12, awayShots = 16,
            homeShotsOnTarget = 5, awayShotsOnTarget = 7,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 18, awayFouls = 14,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 385, awayPasses = 520,
            homePassAccuracy = 84.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "EURO2016_F", date = "2016-07-10", competition = "Euro 2016 Final",
            homeCode = "POR", awayCode = "FRA", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Stade de France", attendance = 75868,
            homePossession = 42.0, awayPossession = 58.0,
            homeShots = 10, awayShots = 18,
            homeShotsOnTarget = 4, awayShotsOnTarget = 8,
            homeCorners = 3, awayCorners = 6,
            homeFouls = 16, awayFouls = 15,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 580,
            homePassAccuracy = 80.0, awayPassAccuracy = 88.0,
        ),
        HistoricalMatch(
            id = "EURO2012_F", date = "2012-07-01", competition = "Euro 2012 Final",
            homeCode = "ESP", awayCode = "ITA", homeScore = 4, awayScore = 0,
            stage = "Final", venue = "Olympic Stadium Kyiv", attendance = 63000,
            homePossession = 56.0, awayPossession = 44.0,
            homeShots = 18, awayShots = 11,
            homeShotsOnTarget = 10, awayShotsOnTarget = 5,
            homeCorners = 6, awayCorners = 4,
            homeFouls = 12, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 650, awayPasses = 380,
            homePassAccuracy = 89.0, awayPassAccuracy = 83.0,
        ),
        HistoricalMatch(
            id = "EURO2008_F", date = "2008-06-29", competition = "Euro 2008 Final",
            homeCode = "ESP", awayCode = "GER", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Ernst Happel Stadium", attendance = 51680,
            homePossession = 55.0, awayPossession = 45.0,
            homeShots = 15, awayShots = 12,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 14, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 520, awayPasses = 400,
            homePassAccuracy = 87.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "EURO2004_F", date = "2004-07-04", competition = "Euro 2004 Final",
            homeCode = "GRE", awayCode = "POR", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Estadio da Luz", attendance = 62565,
            homePossession = 35.0, awayPossession = 65.0,
            homeShots = 7, awayShots = 17,
            homeShotsOnTarget = 4, awayShotsOnTarget = 8,
            homeCorners = 2, awayCorners = 7,
            homeFouls = 20, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 250, awayPasses = 580,
            homePassAccuracy = 76.0, awayPassAccuracy = 88.0,
        ),
        HistoricalMatch(
            id = "CA2021_F", date = "2021-07-10", competition = "Copa America 2021 Final",
            homeCode = "ARG", awayCode = "BRA", homeScore = 1, awayScore = 0,
            stage = "Final", venue = "Maracana Stadium", attendance = 7819,
            homePossession = 52.0, awayPossession = 48.0,
            homeShots = 10, awayShots = 12,
            homeShotsOnTarget = 4, awayShotsOnTarget = 5,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 420, awayPasses = 380,
            homePassAccuracy = 85.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "CA2019_F", date = "2019-07-07", competition = "Copa America 2019 Final",
            homeCode = "BRA", awayCode = "PER", homeScore = 3, awayScore = 1,
            stage = "Final", venue = "Maracana Stadium", attendance = 66000,
            homePossession = 55.0, awayPossession = 45.0,
            homeShots = 14, awayShots = 10,
            homeShotsOnTarget = 7, awayShotsOnTarget = 4,
            homeCorners = 5, awayCorners = 3,
            homeFouls = 15, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 350,
            homePassAccuracy = 86.0, awayPassAccuracy = 82.0,
        ),
        HistoricalMatch(
            id = "AFCON2023_F", date = "2024-02-11", competition = "AFCON 2023 Final",
            homeCode = "CIV", awayCode = "NGA", homeScore = 2, awayScore = 1,
            stage = "Final", venue = "Alassane Ouattara Stadium", attendance = 60000,
            homePossession = 44.0, awayPossession = 56.0,
            homeShots = 12, awayShots = 14,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 350, awayPasses = 420,
            homePassAccuracy = 82.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "AFCON2021_F", date = "2022-02-06", competition = "AFCON 2021 Final",
            homeCode = "SEN", awayCode = "EGY", homeScore = 0, awayScore = 0,
            stage = "Final", venue = "Olembe Stadium", attendance = 60000,
            homePossession = 46.0, awayPossession = 54.0,
            homeShots = 10, awayShots = 12,
            homeShotsOnTarget = 4, awayShotsOnTarget = 5,
            homeCorners = 4, awayCorners = 4,
            homeFouls = 20, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 380,
            homePassAccuracy = 80.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "AC2019_F", date = "2019-02-01", competition = "Asian Cup 2019 Final",
            homeCode = "QAT", awayCode = "JPN", homeScore = 3, awayScore = 1,
            stage = "Final", venue = "Zayed Sports City Stadium", attendance = 36776,
            homePossession = 44.0, awayPossession = 56.0,
            homeShots = 13, awayShots = 11,
            homeShotsOnTarget = 6, awayShotsOnTarget = 4,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 340, awayPasses = 420,
            homePassAccuracy = 82.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2022_QF1", date = "2022-12-09", competition = "FIFA World Cup 2022 QF",
            homeCode = "CRO", awayCode = "BRA", homeScore = 1, awayScore = 1,
            stage = "Quarterfinal", venue = "Education City Stadium", attendance = 43875,
            homePossession = 42.0, awayPossession = 58.0,
            homeShots = 11, awayShots = 21,
            homeShotsOnTarget = 5, awayShotsOnTarget = 9,
            homeCorners = 3, awayCorners = 7,
            homeFouls = 20, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 620,
            homePassAccuracy = 80.0, awayPassAccuracy = 88.0,
        ),
        HistoricalMatch(
            id = "WC2022_QF2", date = "2022-12-10", competition = "FIFA World Cup 2022 QF",
            homeCode = "NED", awayCode = "ARG", homeScore = 2, awayScore = 2,
            stage = "Quarterfinal", venue = "Lusail Stadium", attendance = 88235,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 10, awayShots = 17,
            homeShotsOnTarget = 4, awayShotsOnTarget = 7,
            homeCorners = 3, awayCorners = 5,
            homeFouls = 15, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 4,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 360, awayPasses = 480,
            homePassAccuracy = 83.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2022_QF3", date = "2022-12-10", competition = "FIFA World Cup 2022 QF",
            homeCode = "MAR", awayCode = "POR", homeScore = 1, awayScore = 0,
            stage = "Quarterfinal", venue = "Al Thumama Stadium", attendance = 44247,
            homePossession = 38.0, awayPossession = 62.0,
            homeShots = 8, awayShots = 16,
            homeShotsOnTarget = 3, awayShotsOnTarget = 7,
            homeCorners = 2, awayCorners = 6,
            homeFouls = 18, awayFouls = 14,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 280, awayPasses = 540,
            homePassAccuracy = 78.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "WC2022_QF4", date = "2022-12-10", competition = "FIFA World Cup 2022 QF",
            homeCode = "ENG", awayCode = "FRA", homeScore = 1, awayScore = 2,
            stage = "Quarterfinal", venue = "Al Bayt Stadium", attendance = 68680,
            homePossession = 52.0, awayPossession = 48.0,
            homeShots = 13, awayShots = 10,
            homeShotsOnTarget = 5, awayShotsOnTarget = 4,
            homeCorners = 4, awayCorners = 3,
            homeFouls = 14, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 380,
            homePassAccuracy = 86.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "WC2022_R16_1", date = "2022-12-06", competition = "FIFA World Cup 2022 R16",
            homeCode = "MAR", awayCode = "ESP", homeScore = 0, awayScore = 0,
            stage = "Round of 16", venue = "Education City Stadium", attendance = 43875,
            homePossession = 24.0, awayPossession = 76.0,
            homeShots = 6, awayShots = 22,
            homeShotsOnTarget = 2, awayShotsOnTarget = 9,
            homeCorners = 1, awayCorners = 8,
            homeFouls = 18, awayFouls = 12,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 180, awayPasses = 820,
            homePassAccuracy = 72.0, awayPassAccuracy = 90.0,
        ),
        HistoricalMatch(
            id = "WC2022_R16_2", date = "2022-12-06", competition = "FIFA World Cup 2022 R16",
            homeCode = "POR", awayCode = "SUI", homeScore = 6, awayScore = 1,
            stage = "Round of 16", venue = "Lusail Stadium", attendance = 88103,
            homePossession = 58.0, awayPossession = 42.0,
            homeShots = 18, awayShots = 10,
            homeShotsOnTarget = 10, awayShotsOnTarget = 4,
            homeCorners = 7, awayCorners = 3,
            homeFouls = 12, awayFouls = 16,
            homeYellowCards = 1, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 580, awayPasses = 320,
            homePassAccuracy = 88.0, awayPassAccuracy = 82.0,
        ),
        HistoricalMatch(
            id = "WC2014_R16_1", date = "2014-06-28", competition = "FIFA World Cup 2014 R16",
            homeCode = "BRA", awayCode = "CHI", homeScore = 1, awayScore = 1,
            stage = "Round of 16", venue = "Mineirao Stadium", attendance = 57104,
            homePossession = 53.0, awayPossession = 47.0,
            homeShots = 13, awayShots = 11,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 20, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 450, awayPasses = 380,
            homePassAccuracy = 85.0, awayPassAccuracy = 83.0,
        ),
        HistoricalMatch(
            id = "WC2014_R16_2", date = "2014-07-01", competition = "FIFA World Cup 2014 R16",
            homeCode = "ARG", awayCode = "SUI", homeScore = 1, awayScore = 0,
            stage = "Round of 16", venue = "Arena Corinthians", attendance = 63267,
            homePossession = 60.0, awayPossession = 40.0,
            homeShots = 16, awayShots = 8,
            homeShotsOnTarget = 7, awayShotsOnTarget = 3,
            homeCorners = 6, awayCorners = 2,
            homeFouls = 14, awayFouls = 20,
            homeYellowCards = 2, awayYellowCards = 4,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 520, awayPasses = 280,
            homePassAccuracy = 87.0, awayPassAccuracy = 78.0,
        ),
        HistoricalMatch(
            id = "WC2022_G1", date = "2022-11-22", competition = "FIFA World Cup 2022 Group",
            homeCode = "ARG", awayCode = "KSA", homeScore = 1, awayScore = 2,
            stage = "Group Stage", venue = "Lusail Stadium", attendance = 88103,
            homePossession = 60.0, awayPossession = 40.0,
            homeShots = 15, awayShots = 8,
            homeShotsOnTarget = 5, awayShotsOnTarget = 3,
            homeCorners = 5, awayCorners = 2,
            homeFouls = 14, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 520, awayPasses = 280,
            homePassAccuracy = 87.0, awayPassAccuracy = 76.0,
        ),
        HistoricalMatch(
            id = "WC2022_G2", date = "2022-11-23", competition = "FIFA World Cup 2022 Group",
            homeCode = "GER", awayCode = "JPN", homeScore = 1, awayScore = 2,
            stage = "Group Stage", venue = "Khalifa Stadium", attendance = 40918,
            homePossession = 58.0, awayPossession = 42.0,
            homeShots = 14, awayShots = 10,
            homeShotsOnTarget = 6, awayShotsOnTarget = 4,
            homeCorners = 4, awayCorners = 3,
            homeFouls = 15, awayFouls = 17,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 320,
            homePassAccuracy = 86.0, awayPassAccuracy = 82.0,
        ),
        HistoricalMatch(
            id = "WC2022_G3", date = "2022-11-24", competition = "FIFA World Cup 2022 Group",
            homeCode = "URU", awayCode = "POR", homeScore = 0, awayScore = 2,
            stage = "Group Stage", venue = "Lusail Stadium", attendance = 88103,
            homePossession = 45.0, awayPossession = 55.0,
            homeShots = 12, awayShots = 14,
            homeShotsOnTarget = 4, awayShotsOnTarget = 6,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 18, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 380, awayPasses = 480,
            homePassAccuracy = 83.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2014_G1", date = "2014-06-13", competition = "FIFA World Cup 2014 Group",
            homeCode = "NED", awayCode = "ESP", homeScore = 5, awayScore = 1,
            stage = "Group Stage", venue = "Arena Fonte Nova", attendance = 51480,
            homePossession = 45.0, awayPossession = 55.0,
            homeShots = 16, awayShots = 14,
            homeShotsOnTarget = 9, awayShotsOnTarget = 6,
            homeCorners = 5, awayCorners = 5,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 380, awayPasses = 520,
            homePassAccuracy = 82.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "WC2014_G2", date = "2014-06-14", competition = "FIFA World Cup 2014 Group",
            homeCode = "ENG", awayCode = "ITA", homeScore = 1, awayScore = 2,
            stage = "Group Stage", venue = "Arena da Amazonia", attendance = 40022,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 12, awayShots = 13,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 400, awayPasses = 420,
            homePassAccuracy = 84.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC2014_G3", date = "2014-06-23", competition = "FIFA World Cup 2014 Group",
            homeCode = "GER", awayCode = "USA", homeScore = 1, awayScore = 0,
            stage = "Group Stage", venue = "Arena Pernambuco", attendance = 39480,
            homePossession = 55.0, awayPossession = 45.0,
            homeShots = 13, awayShots = 8,
            homeShotsOnTarget = 6, awayShotsOnTarget = 3,
            homeCorners = 5, awayCorners = 2,
            homeFouls = 14, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 280,
            homePassAccuracy = 87.0, awayPassAccuracy = 80.0,
        ),
        HistoricalMatch(
            id = "WC2010_G1", date = "2010-06-15", competition = "FIFA World Cup 2010 Group",
            homeCode = "BRA", awayCode = "PRK", homeScore = 2, awayScore = 1,
            stage = "Group Stage", venue = "Ellis Park Stadium", attendance = 54331,
            homePossession = 56.0, awayPossession = 44.0,
            homeShots = 15, awayShots = 10,
            homeShotsOnTarget = 7, awayShotsOnTarget = 4,
            homeCorners = 6, awayCorners = 3,
            homeFouls = 15, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 320,
            homePassAccuracy = 85.0, awayPassAccuracy = 80.0,
        ),
        HistoricalMatch(
            id = "WC2010_G2", date = "2010-06-24", competition = "FIFA World Cup 2010 Group",
            homeCode = "SVK", awayCode = "ITA", homeScore = 3, awayScore = 2,
            stage = "Group Stage", venue = "Ellis Park Stadium", attendance = 53412,
            homePossession = 42.0, awayPossession = 58.0,
            homeShots = 11, awayShots = 16,
            homeShotsOnTarget = 5, awayShotsOnTarget = 7,
            homeCorners = 4, awayCorners = 6,
            homeFouls = 18, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 520,
            homePassAccuracy = 80.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "WC2010_G3", date = "2010-06-16", competition = "FIFA World Cup 2010 Group",
            homeCode = "ESP", awayCode = "SUI", homeScore = 0, awayScore = 1,
            stage = "Group Stage", venue = "Moses Mabhida Stadium", attendance = 62453,
            homePossession = 63.0, awayPossession = 37.0,
            homeShots = 22, awayShots = 8,
            homeShotsOnTarget = 9, awayShotsOnTarget = 3,
            homeCorners = 8, awayCorners = 2,
            homeFouls = 12, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 4,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 680, awayPasses = 280,
            homePassAccuracy = 88.0, awayPassAccuracy = 76.0,
        ),
        HistoricalMatch(
            id = "WC2010_G4", date = "2010-07-02", competition = "FIFA World Cup 2010 QF",
            homeCode = "BRA", awayCode = "NED", homeScore = 1, awayScore = 2,
            stage = "Quarterfinal", venue = "Nelson Mandela Bay Stadium", attendance = 40186,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 13, awayShots = 12,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 1, awayRedCards = 0,
            homePasses = 380, awayPasses = 420,
            homePassAccuracy = 83.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC2006_G1", date = "2006-06-27", competition = "FIFA World Cup 2006 R16",
            homeCode = "ITA", awayCode = "AUS", homeScore = 1, awayScore = 0,
            stage = "Round of 16", venue = "Kaiserslautern Fritz Walter", attendance = 46120,
            homePossession = 52.0, awayPossession = 48.0,
            homeShots = 12, awayShots = 10,
            homeShotsOnTarget = 5, awayShotsOnTarget = 4,
            homeCorners = 6, awayCorners = 4,
            homeFouls = 18, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 1, awayRedCards = 0,
            homePasses = 400, awayPasses = 350,
            homePassAccuracy = 84.0, awayPassAccuracy = 82.0,
        ),
        HistoricalMatch(
            id = "WC2006_G2", date = "2006-07-01", competition = "FIFA World Cup 2006 QF",
            homeCode = "GER", awayCode = "ARG", homeScore = 1, awayScore = 1,
            stage = "Quarterfinal", venue = "Olympiastadion Berlin", attendance = 72000,
            homePossession = 50.0, awayPossession = 50.0,
            homeShots = 13, awayShots = 12,
            homeShotsOnTarget = 5, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 18, awayFouls = 20,
            homeYellowCards = 3, awayYellowCards = 4,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 420, awayPasses = 400,
            homePassAccuracy = 85.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "WC2006_G3", date = "2006-06-30", competition = "FIFA World Cup 2006 QF",
            homeCode = "ENG", awayCode = "POR", homeScore = 0, awayScore = 0,
            stage = "Quarterfinal", venue = "Gelsenkirchen Arena", attendance = 52493,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 14, awayShots = 13,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 5,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 4, awayYellowCards = 5,
            homeRedCards = 1, awayRedCards = 1,
            homePasses = 380, awayPasses = 420,
            homePassAccuracy = 84.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC2002_G1", date = "2002-06-18", competition = "FIFA World Cup 2002 R16",
            homeCode = "KOR", awayCode = "ITA", homeScore = 2, awayScore = 1,
            stage = "Round of 16", venue = "Daejeon World Cup Stadium", attendance = 38000,
            homePossession = 42.0, awayPossession = 58.0,
            homeShots = 10, awayShots = 16,
            homeShotsOnTarget = 4, awayShotsOnTarget = 7,
            homeCorners = 4, awayCorners = 6,
            homeFouls = 20, awayFouls = 15,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 1,
            homePasses = 320, awayPasses = 520,
            homePassAccuracy = 78.0, awayPassAccuracy = 86.0,
        ),
        HistoricalMatch(
            id = "WC2002_G2", date = "2002-06-22", competition = "FIFA World Cup 2002 QF",
            homeCode = "KOR", awayCode = "ESP", homeScore = 0, awayScore = 0,
            stage = "Quarterfinal", venue = "Gwangju World Cup Stadium", attendance = 42000,
            homePossession = 38.0, awayPossession = 62.0,
            homeShots = 8, awayShots = 18,
            homeShotsOnTarget = 3, awayShotsOnTarget = 8,
            homeCorners = 2, awayCorners = 7,
            homeFouls = 18, awayFouls = 14,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 280, awayPasses = 620,
            homePassAccuracy = 75.0, awayPassAccuracy = 88.0,
        ),
        HistoricalMatch(
            id = "WC2002_G3", date = "2002-06-21", competition = "FIFA World Cup 2002 QF",
            homeCode = "USA", awayCode = "GER", homeScore = 0, awayScore = 1,
            stage = "Quarterfinal", venue = "Ulsan Munsu Stadium", attendance = 37000,
            homePossession = 40.0, awayPossession = 60.0,
            homeShots = 10, awayShots = 14,
            homeShotsOnTarget = 4, awayShotsOnTarget = 6,
            homeCorners = 5, awayCorners = 5,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 480,
            homePassAccuracy = 80.0, awayPassAccuracy = 85.0,
        ),
        HistoricalMatch(
            id = "WC1998_G1", date = "1998-06-30", competition = "FIFA World Cup 1998 R16",
            homeCode = "ARG", awayCode = "ENG", homeScore = 2, awayScore = 2,
            stage = "Round of 16", venue = "Stade Geoffroy Guichard", attendance = 306000,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 14, awayShots = 12,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 400, awayPasses = 380,
            homePassAccuracy = 84.0, awayPassAccuracy = 83.0,
        ),
        HistoricalMatch(
            id = "WC1998_G2", date = "1998-07-04", competition = "FIFA World Cup 1998 QF",
            homeCode = "NED", awayCode = "ARG", homeScore = 2, awayScore = 1,
            stage = "Quarterfinal", venue = "Stade Velodrome", attendance = 55000,
            homePossession = 50.0, awayPossession = 50.0,
            homeShots = 13, awayShots = 11,
            homeShotsOnTarget = 6, awayShotsOnTarget = 5,
            homeCorners = 5, awayCorners = 4,
            homeFouls = 16, awayFouls = 18,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 420, awayPasses = 380,
            homePassAccuracy = 85.0, awayPassAccuracy = 83.0,
        ),
        HistoricalMatch(
            id = "WC1994_G1", date = "1994-07-05", competition = "FIFA World Cup 1994 R16",
            homeCode = "BRA", awayCode = "USA", homeScore = 1, awayScore = 0,
            stage = "Round of 16", venue = "Stanford Stadium", attendance = 84147,
            homePossession = 58.0, awayPossession = 42.0,
            homeShots = 15, awayShots = 8,
            homeShotsOnTarget = 7, awayShotsOnTarget = 3,
            homeCorners = 6, awayCorners = 2,
            homeFouls = 14, awayFouls = 18,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 480, awayPasses = 280,
            homePassAccuracy = 86.0, awayPassAccuracy = 78.0,
        ),
        HistoricalMatch(
            id = "WC1990_G1", date = "1990-06-25", competition = "FIFA World Cup 1990 R16",
            homeCode = "ARG", awayCode = "BRA", homeScore = 1, awayScore = 0,
            stage = "Round of 16", venue = "Stadio delle Alpi", attendance = 61000,
            homePossession = 40.0, awayPossession = 60.0,
            homeShots = 8, awayShots = 16,
            homeShotsOnTarget = 3, awayShotsOnTarget = 7,
            homeCorners = 3, awayCorners = 6,
            homeFouls = 18, awayFouls = 14,
            homeYellowCards = 3, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 280, awayPasses = 520,
            homePassAccuracy = 78.0, awayPassAccuracy = 87.0,
        ),
        HistoricalMatch(
            id = "WC1986_G1", date = "1986-06-22", competition = "FIFA World Cup 1986 QF",
            homeCode = "ARG", awayCode = "ENG", homeScore = 2, awayScore = 1,
            stage = "Quarterfinal", venue = "Estadio Azteca", attendance = 114500,
            homePossession = 48.0, awayPossession = 52.0,
            homeShots = 12, awayShots = 13,
            homeShotsOnTarget = 5, awayShotsOnTarget = 6,
            homeCorners = 4, awayCorners = 5,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 2, awayYellowCards = 2,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 380, awayPasses = 420,
            homePassAccuracy = 82.0, awayPassAccuracy = 84.0,
        ),
        HistoricalMatch(
            id = "WC1986_G2", date = "1986-06-22", competition = "FIFA World Cup 1986 QF",
            homeCode = "BEL", awayCode = "ESP", homeScore = 1, awayScore = 1,
            stage = "Quarterfinal", venue = "Estadio Neza", attendance = 35000,
            homePossession = 45.0, awayPossession = 55.0,
            homeShots = 10, awayShots = 14,
            homeShotsOnTarget = 4, awayShotsOnTarget = 6,
            homeCorners = 3, awayCorners = 5,
            homeFouls = 18, awayFouls = 16,
            homeYellowCards = 3, awayYellowCards = 3,
            homeRedCards = 0, awayRedCards = 0,
            homePasses = 320, awayPasses = 450,
            homePassAccuracy = 80.0, awayPassAccuracy = 85.0,
        ),
    )

    private val byTeamPair: Map<Pair<String, String>, List<HistoricalMatch>> =
        CLASSIC_MATCHES.groupBy { it.homeCode to it.awayCode }

    fun findMatchesBetween(teamA: String, teamB: String): List<HistoricalMatch> {
        val direct = byTeamPair[teamA to teamB] ?: emptyList()
        val reverse = byTeamPair[teamB to teamA] ?: emptyList()
        return (direct + reverse).sortedByDescending { it.date }
    }

    fun findMatchesByTeam(code: String): List<HistoricalMatch> =
        CLASSIC_MATCHES.filter { it.homeCode == code || it.awayCode == code }
            .sortedByDescending { it.date }

    fun findMatchesByCompetition(competition: String): List<HistoricalMatch> =
        CLASSIC_MATCHES.filter { it.competition.contains(competition) }
            .sortedByDescending { it.date }

    fun averageGoalsInCompetition(competition: String): Double {
        val matches = findMatchesByCompetition(competition)
        if (matches.isEmpty()) return 2.5
        val total = matches.sumOf { it.homeScore + it.awayScore }
        return total.toDouble() / matches.size
    }

    fun upsetRateInStage(stage: String): Double {
        val matches = CLASSIC_MATCHES.filter { it.stage == stage }
        if (matches.isEmpty()) return 0.15
        val upsets = matches.count {
            val homeStronger = it.homePossession > it.awayPossession
            (homeStronger && it.homeScore < it.awayScore) ||
            (!homeStronger && it.homeScore > it.awayScore)
        }
        return upsets.toDouble() / matches.size
    }

    fun averageCardsInStage(stage: String): Double {
        val matches = CLASSIC_MATCHES.filter { it.stage == stage }
        if (matches.isEmpty()) return 4.0
        val totalCards = matches.sumOf { it.homeYellowCards + it.awayYellowCards }
        return totalCards.toDouble() / matches.size
    }

    fun averageCornersInStage(stage: String): Double {
        val matches = CLASSIC_MATCHES.filter { it.stage == stage }
        if (matches.isEmpty()) return 10.0
        val totalCorners = matches.sumOf { it.homeCorners + it.awayCorners }
        return totalCorners.toDouble() / matches.size
    }
}

// =============================================================================
// 第十六部分：高级统计模型
// -----------------------------------------------------------------------------
// 包含时间序列分析、回归分析、方差分析等统计方法，
// 所有运算通过加减乘除混合实现，严格遵循统计学公式。
// =============================================================================

object StatisticalModels {

    /** 线性回归：最小二乘法
     * y = a + b*x
     * b = Σ((x-x̄)(y-ȳ)) / Σ((x-x̄)^2)
     * a = ȳ - b*x̄
     * 通过减法、乘法、加法、除法运算
     */
    data class LinearRegressionResult(
        val slope: Double,
        val intercept: Double,
        val rSquared: Double,
        val correlation: Double,
    )

    fun linearRegression(x: List<Double>, y: List<Double>): LinearRegressionResult {
        if (x.size != y.size || x.size < 2) {
            return LinearRegressionResult(0.0, y.averageOrNull(), 0.0, 0.0)
        }
        val n = x.size.toDouble()
        val xMean = x.sum() / n
        val yMean = y.sum() / n
        var numerator = 0.0; var denominator = 0.0
        for (i in x.indices) {
            val xDiff = x[i] - xMean
            val yDiff = y[i] - yMean
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }
        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean
        val correlation = numerator / (PredictMath.standardDeviation(x) *
            PredictMath.standardDeviation(y) * (n - 1))
        val rSq = correlation * correlation
        return LinearRegressionResult(slope, intercept, rSq, correlation)
    }

    private fun List<Double>.averageOrNull(): Double =
        if (isEmpty()) 0.0 else sum() / size

    /** 多项式回归（二次）
     * y = a + b*x + c*x^2
     * 通过正规方程求解
     */
    data class PolynomialRegressionResult(
        val a: Double,  // 常数项
        val b: Double,  // 一次项
        val c: Double,  // 二次项
        val rSquared: Double,
    )

    fun polynomialRegression(x: List<Double>, y: List<Double>): PolynomialRegressionResult {
        if (x.size != y.size || x.size < 3) {
            return PolynomialRegressionResult(y.averageOrNull(), 0.0, 0.0, 0.0)
        }
        val n = x.size.toDouble()
        val sumX = x.sum()
        val sumX2 = x.sumOf { it * it }
        val sumX3 = x.sumOf { it * it * it }
        val sumX4 = x.sumOf { it * it * it * it }
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2Y = x.zip(y).sumOf { it.first * it.first * it.second }

        val det = n * (sumX2 * sumX4 - sumX3 * sumX3) -
            sumX * (sumX * sumX4 - sumX2 * sumX3) +
            sumX2 * (sumX * sumX3 - sumX2 * sumX2)
        if (abs(det) < 1e-10) {
            val lr = linearRegression(x, y)
            return PolynomialRegressionResult(lr.intercept, lr.slope, 0.0, lr.rSquared)
        }
        val a = (sumY * (sumX2 * sumX4 - sumX3 * sumX3) -
            sumXY * (sumX * sumX4 - sumX2 * sumX3) +
            sumX2Y * (sumX * sumX3 - sumX2 * sumX2)) / det
        val b = (n * (sumXY * sumX4 - sumX2Y * sumX3) -
            sumX * (sumY * sumX4 - sumX2Y * sumX2) +
            sumX2 * (sumY * sumX3 - sumXY * sumX2)) / det
        val c = (n * (sumX2 * sumX2Y - sumX3 * sumXY) -
            sumX * (sumX * sumX2Y - sumX2 * sumXY) +
            sumY * (sumX * sumX3 - sumX2 * sumX2)) / det

        val yMean = sumY / n
        var ssTot = 0.0; var ssRes = 0.0
        for (i in x.indices) {
            val predicted = a + b * x[i] + c * x[i] * x[i]
            ssTot += (y[i] - yMean) * (y[i] - yMean)
            ssRes += (y[i] - predicted) * (y[i] - predicted)
        }
        val rSq = if (ssTot > 0) 1.0 - ssRes / ssTot else 0.0
        return PolynomialRegressionResult(a, b, c, rSq)
    }

    /** 指数平滑预测
     * S_t = α * Y_t + (1 - α) * S_{t-1}
     * 通过乘法加法运算
     */
    fun exponentialSmoothing(values: List<Double>, alpha: Double = 0.3): List<Double> {
        if (values.isEmpty()) return emptyList()
        val smoothed = ArrayList<Double>(values.size)
        smoothed.add(values[0])
        for (i in 1 until values.size) {
            val s = alpha * values[i] + (1.0 - alpha) * smoothed[i - 1]
            smoothed.add(s)
        }
        return smoothed
    }

    /** 双指数平滑（Holt's method）
     * Level: L_t = α*Y_t + (1-α)*(L_{t-1} + T_{t-1})
     * Trend: T_t = β*(L_t - L_{t-1}) + (1-β)*T_{t-1}
     * Forecast: F_{t+h} = L_t + h*T_t
     */
    fun doubleExponentialSmoothing(
        values: List<Double>,
        alpha: Double = 0.3,
        beta: Double = 0.1,
        forecastSteps: Int = 3,
    ): List<Double> {
        if (values.size < 2) return values
        var level = values[0]
        var trend = values[1] - values[0]
        val result = ArrayList<Double>(values.size + forecastSteps)
        result.add(level)
        for (i in 1 until values.size) {
            val newLevel = alpha * values[i] + (1.0 - alpha) * (level + trend)
            val newTrend = beta * (newLevel - level) + (1.0 - beta) * trend
            level = newLevel
            trend = newTrend
            result.add(level)
        }
        for (h in 1..forecastSteps) {
            result.add(level + h * trend)
        }
        return result
    }

    /** 移动平均
     * MA_n = (1/n) * Σ Y_{t-i}
     */
    fun movingAverage(values: List<Double>, window: Int = 5): List<Double> {
        if (values.size < window) return listOf(values.averageOrNull())
        val result = ArrayList<Double>(values.size - window + 1)
        for (i in 0..values.size - window) {
            var sum = 0.0
            for (j in 0 until window) sum += values[i + j]
            result.add(sum / window)
        }
        return result
    }

    /** 加权移动平均
     * WMA = Σ(w_i * Y_{t-i}) / Σ(w_i)
     */
    fun weightedMovingAverage(values: List<Double>, weights: List<Double>): List<Double> {
        val window = weights.size
        if (values.size < window) return listOf(values.averageOrNull())
        val weightSum = weights.sum()
        val result = ArrayList<Double>(values.size - window + 1)
        for (i in 0..values.size - window) {
            var sum = 0.0
            for (j in 0 until window) {
                sum += values[i + j] * weights[j]
            }
            result.add(sum / weightSum)
        }
        return result
    }

    /** t 检验：两组均值差异显著性
     * t = (x̄1 - x̄2) / sqrt(s1^2/n1 + s2^2/n2)
     */
    fun tTest(group1: List<Double>, group2: List<Double>): Double {
        if (group1.size < 2 || group2.size < 2) return 0.0
        val mean1 = group1.average()
        val mean2 = group2.average()
        val sd1 = PredictMath.standardDeviation(group1)
        val sd2 = PredictMath.standardDeviation(group2)
        val n1 = group1.size.toDouble()
        val n2 = group2.size.toDouble()
        val denominator = sqrt(sd1 * sd1 / n1 + sd2 * sd2 / n2)
        if (denominator <= 0.0) return 0.0
        return (mean1 - mean2) / denominator
    }

    /** 卡方检验：观测频率 vs 期望频率
     * χ² = Σ (O - E)^2 / E
     */
    fun chiSquareTest(observed: List<Double>, expected: List<Double>): Double {
        if (observed.size != expected.size || observed.isEmpty()) return 0.0
        var chiSq = 0.0
        for (i in observed.indices) {
            if (expected[i] > 0) {
                val diff = observed[i] - expected[i]
                chiSq += diff * diff / expected[i]
            }
        }
        return chiSq
    }

    /** Kolmogorov-Smirnov 检验：分布拟合优度
     * D = max|F_obs(x) - F_exp(x)|
     */
    fun ksTest(observed: List<Double>, expectedCDF: (Double) -> Double): Double {
        if (observed.isEmpty()) return 0.0
        val sorted = observed.sorted()
        val n = sorted.size.toDouble()
        var maxDiff = 0.0
        for (i in sorted.indices) {
            val empiricalCDF = (i + 1).toDouble() / n
            val expectedVal = expectedCDF(sorted[i])
            maxDiff = maxOf(maxDiff, abs(empiricalCDF - expectedVal))
        }
        return maxDiff
    }

    /** 置信区间：正态分布近似
     * CI = x̄ ± z * (s / sqrt(n))
     */
    fun confidenceInterval(
        values: List<Double>,
        zScore: Double = 1.96,
    ): Pair<Double, Double> {
        if (values.size < 2) {
            val v = values.averageOrNull()
            return v to v
        }
        val mean = values.average()
        val sd = PredictMath.standardDeviation(values)
        val se = sd / sqrt(values.size.toDouble())
        val margin = zScore * se
        return (mean - margin) to (mean + margin)
    }

    /** 皮尔逊相关系数
     * r = Σ((x-x̄)(y-ȳ)) / sqrt(Σ(x-x̄)^2 * Σ(y-ȳ)^2)
     */
    fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0
        val xMean = x.average()
        val yMean = y.average()
        var numerator = 0.0; var xSq = 0.0; var ySq = 0.0
        for (i in x.indices) {
            val xDiff = x[i] - xMean
            val yDiff = y[i] - yMean
            numerator += xDiff * yDiff
            xSq += xDiff * xDiff
            ySq += yDiff * yDiff
        }
        val denominator = sqrt(xSq * ySq)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 斯皮尔曼等级相关系数
     * rs = 1 - 6*Σd^2 / (n*(n^2-1))
     */
    fun spearmanCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0
        val xRanks = assignRanks(x)
        val yRanks = assignRanks(y)
        val n = x.size.toDouble()
        var sumDSq = 0.0
        for (i in x.indices) {
            val d = xRanks[i] - yRanks[i]
            sumDSq += d * d
        }
        return 1.0 - 6.0 * sumDSq / (n * (n * n - 1.0))
    }

    private fun assignRanks(values: List<Double>): List<Double> {
        val sorted = values.sorted()
        return values.map { v ->
            sorted.indexOf(v).toDouble() + 1.0
        }
    }

    /** 方差分析（ANOVA）单因素
     * F = MS_between / MS_within
     * MS_between = SSB / (k-1)
     * MS_within = SSW / (N-k)
     */
    fun anova(groups: List<List<Double>>): Double {
        if (groups.size < 2) return 0.0
        val allValues = groups.flatten()
        if (allValues.size < 2) return 0.0
        val grandMean = allValues.average()
        val k = groups.size.toDouble()
        val n = allValues.size.toDouble()
        var ssBetween = 0.0; var ssWithin = 0.0
        for (group in groups) {
            if (group.isEmpty()) continue
            val groupMean = group.average()
            ssBetween += group.size * (groupMean - grandMean) * (groupMean - grandMean)
            for (value in group) {
                ssWithin += (value - groupMean) * (value - groupMean)
            }
        }
        val msBetween = ssBetween / (k - 1.0)
        val msWithin = ssWithin / (n - k)
        return if (msWithin > 0) msBetween / msWithin else 0.0
    }

    /** 信息熵
     * H = -Σ p(x) * log2(p(x))
     */
    fun entropy(probabilities: List<Double>): Double {
        var h = 0.0
        for (p in probabilities) {
            if (p > 0) {
                h -= p * (kotlin.math.ln(p) / kotlin.math.ln(2.0))
            }
        }
        return h
    }

    /** KL 散度
     * D(P||Q) = Σ P(x) * log(P(x)/Q(x))
     */
    fun klDivergence(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size || p.isEmpty()) return 0.0
        var d = 0.0
        for (i in p.indices) {
            if (p[i] > 0 && q[i] > 0) {
                d += p[i] * kotlin.math.ln(p[i] / q[i])
            }
        }
        return d
    }

    /** Wasserstein 距离（一维）
     * W = ∫|F(x) - G(x)| dx
     * 离散版本：W = Σ|x_i - y_i| * ΔF
     */
    fun wassersteinDistance(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size || p.isEmpty()) return 0.0
        val pSorted = p.sorted()
        val qSorted = q.sorted()
        var dist = 0.0
        for (i in pSorted.indices) {
            dist += abs(pSorted[i] - qSorted[i])
        }
        return dist / pSorted.size
    }
}

// =============================================================================
// 第十七部分：置信度校准与验证
// -----------------------------------------------------------------------------
// 通过 Platt 缩放和温度缩放校准预测概率，
// 使预测置信度与实际命中率一致。
// =============================================================================

object ConfidenceCalibrator {

    /** Platt 缩放：用逻辑回归校准概率
     * P_calibrated = 1 / (1 + exp(A * P_raw + B))
     * 通过乘法和加法运算
     */
    fun plattScaling(
        rawProb: Double,
        paramA: Double = -1.0,
        paramB: Double = 0.5,
    ): Double {
        val logit = paramA * rawProb + paramB
        val calibrated = 1.0 / (1.0 + exp(-logit))
        return calibrated.coerceIn(0.01, 0.99)
    }

    /** 温度缩放：用温度参数调整 softmax
     * P_calibrated = softmax(logits / T)
     */
    fun temperatureScaling(logits: DoubleArray, temperature: Double): DoubleArray {
        if (temperature <= 0) return logits
        val scaled = DoubleArray(logits.size) { logits[it] / temperature }
        return PredictMath.softmax(scaled)
    }

    /** Brier 分数：衡量概率预测质量
     * BS = (1/N) * Σ(f_i - o_i)^2
     */
    fun brierScore(forecasts: List<Double>, outcomes: List<Double>): Double {
        if (forecasts.size != outcomes.size || forecasts.isEmpty()) return 1.0
        var sum = 0.0
        for (i in forecasts.indices) {
            val diff = forecasts[i] - outcomes[i]
            sum += diff * diff
        }
        return sum / forecasts.size
    }

    /** 对数损失
     * LogLoss = -(1/N) * Σ(o*log(f) + (1-o)*log(1-f))
     */
    fun logLoss(forecasts: List<Double>, outcomes: List<Double>): Double {
        if (forecasts.isEmpty()) return 1.0
        var sum = 0.0
        for (i in forecasts.indices) {
            val f = forecasts[i].coerceIn(1e-10, 1.0 - 1e-10)
            val o = outcomes[i]
            sum -= o * kotlin.math.ln(f) + (1.0 - o) * kotlin.math.ln(1.0 - f)
        }
        return sum / forecasts.size
    }

    /** 校准曲线：将预测概率分箱比较实际频率
     * 通过除法和加法计算每箱的校准误差
     */
    data class CalibrationBin(
        val lowerBound: Double,
        val upperBound: Double,
        val meanPrediction: Double,
        val observedFrequency: Double,
        val count: Int,
    )

    fun calibrationCurve(
        predictions: List<Double>,
        outcomes: List<Double>,
        numBins: Int = 10,
    ): List<CalibrationBin> {
        if (predictions.size != outcomes.size || predictions.isEmpty()) return emptyList()
        val bins = ArrayList<CalibrationBin>(numBins)
        val binSize = 1.0 / numBins
        for (b in 0 until numBins) {
            val lower = b * binSize
            val upper = (b + 1) * binSize
            val indices = predictions.indices.filter {
                predictions[it] >= lower && (predictions[it] < upper || (b == numBins - 1 && predictions[it] <= upper))
            }
            if (indices.isNotEmpty()) {
                val meanPred = indices.map { predictions[it] }.average()
                val obsFreq = indices.map { outcomes[it] }.average()
                bins.add(CalibrationBin(lower, upper, meanPred, obsFreq, indices.size))
            }
        }
        return bins
    }

    /** Expected Calibration Error (ECE)
     * ECE = Σ (|B_m| / N) * |acc(B_m) - conf(B_m)|
     */
    fun expectedCalibrationError(bins: List<CalibrationBin>): Double {
        if (bins.isEmpty()) return 0.0
        val total = bins.sumOf { it.count }.toDouble()
        if (total <= 0) return 0.0
        var ece = 0.0
        for (bin in bins) {
            val weight = bin.count.toDouble() / total
            val gap = abs(bin.observedFrequency - bin.meanPrediction)
            ece += weight * gap
        }
        return ece
    }

    /** Maximum Calibration Error (MCE)
     */
    fun maximumCalibrationError(bins: List<CalibrationBin>): Double {
        if (bins.isEmpty()) return 0.0
        return bins.maxOfOrNull { abs(it.observedFrequency - it.meanPrediction) } ?: 0.0
    }

    /**可靠性曲线斜率
     * 理想校准下斜率为 1
     */
    fun reliabilitySlope(bins: List<CalibrationBin>): Double {
        if (bins.size < 2) return 1.0
        val x = bins.map { it.meanPrediction }
        val y = bins.map { it.observedFrequency }
        val lr = StatisticalModels.linearRegression(x, y)
        return lr.slope
    }
}

// =============================================================================
// 第十八部分：高级模拟模型
// -----------------------------------------------------------------------------
// 基于智能体的模拟和随机过程，
// 通过大量迭代运算产生比分分布。
// =============================================================================

object AdvancedSimulator {

    /** Box-Muller 变换：生成正态分布随机数
     * z = sqrt(-2*ln(u1)) * cos(2π*u2)
     * 通过乘法、对数、三角函数运算
     */
    fun gaussianRandom(mean: Double = 0.0, stdDev: Double = 1.0, rand: java.util.Random = java.util.Random()): Double {
        val u1 = rand.nextDouble().coerceAtLeast(1e-10)
        val u2 = rand.nextDouble()
        val z = sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2.0 * Math.PI * u2)
        return mean + stdDev * z
    }

    /** Gamma 分布随机数（Marsaglia-Tsang 方法）
     * 用于进球数的更精确建模
     */
    fun gammaRandom(shape: Double, scale: Double, rand: java.util.Random = java.util.Random()): Double {
        if (shape < 1.0) {
            val d = shape + 1.0 - 1.0 / 3.0
            val c = 1.0 / sqrt(9.0 * d * (1.0))
            while (true) {
                val x = gaussianRandom(0.0, 1.0, rand)
                val v = 1.0 + c * x
                if (v <= 0.0) continue
                val v3 = v * v * v
                val u = rand.nextDouble()
                if (u < 1.0 - 0.0331 * x * x * x * x) return d * v3 * scale
                if (kotlin.math.ln(u) < 0.5 * x * x + d * (1.0 - v3 + kotlin.math.ln(v3))) return d * v3 * scale
            }
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            val x = gaussianRandom(0.0, 1.0, rand)
            val v = 1.0 + c * x
            if (v <= 0.0) continue
            val v3 = v * v * v
            val u = rand.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v3 * scale
            if (kotlin.math.ln(u) < 0.5 * x * x + d * (1.0 - v3 + kotlin.math.ln(v3))) return d * v3 * scale
        }
    }

    /** 负二项分布采样
     * 用于过度离散的进球数据
     */
    fun negativeBinomialRandom(r: Int, p: Double, rand: java.util.Random = java.util.Random()): Int {
        val lambda = gammaRandom(r.toDouble(), p / (1.0 - p), rand)
        return samplePoisson(lambda, rand)
    }

    fun samplePoisson(lambda: Double, rand: java.util.Random): Int {
        val L = exp(-lambda)
        var k = 0; var p = 1.0
        do {
            k++
            p *= rand.nextDouble()
        } while (p > L)
        return k - 1
    }

    /** 基于智能体的比赛模拟
     * 每分钟模拟控球、射门、进球
     */
    data class SimulationConfig(
        val homeAttackStrength: Double,
        val awayAttackStrength: Double,
        val homeDefenseStrength: Double,
        val awayDefenseStrength: Double,
        val homePossession: Double,
        val awayPossession: Double,
        val homeFinishing: Double,
        val awayFinishing: Double,
        val homeGoalkeeping: Double,
        val awayGoalkeeping: Double,
        val matchIntensity: Double = 1.0,
        val fatigueFactor: Double = 1.0,
    )

    data class SimulationResult(
        val homeScore: Int,
        val awayScore: Int,
        val homeShots: Int,
        val awayShots: Int,
        val homeShotsOnTarget: Int,
        val awayShotsOnTarget: Int,
        val homeCorners: Int,
        val awayCorners: Int,
        val homePossessionPct: Double,
        val homeFouls: Int,
        val awayFouls: Int,
        val homeYellowCards: Int,
        val awayYellowCards: Int,
        val goalEvents: List<GoalEvent>,
    )

    data class GoalEvent(
        val minute: Int,
        val team: String,
        val scoreAfter: String,
    )

    fun simulateMatch(config: SimulationConfig, rand: java.util.Random = java.util.Random()): SimulationResult {
        var homeScore = 0; var awayScore = 0
        var homeShots = 0; var awayShots = 0
        var homeSOT = 0; var awaySOT = 0
        var homeCorners = 0; var awayCorners = 0
        var homeFouls = 0; var awayFouls = 0
        var homeYellows = 0; var awayYellows = 0
        val goalEvents = ArrayList<GoalEvent>()

        for (minute in 1..90) {
            val fatigue = 1.0 + (minute / 90.0 - 0.5) * config.fatigueFactor
            val intensity = config.matchIntensity * fatigue

            val possessionRoll = rand.nextDouble()
            val isHomePossession = possessionRoll < config.homePossession

            val shotProb = if (isHomePossession) {
                config.homeAttackStrength * config.awayDefenseStrength * 0.025 * intensity
            } else {
                config.awayAttackStrength * config.homeDefenseStrength * 0.025 * intensity
            }

            if (rand.nextDouble() < shotProb) {
                if (isHomePossession) {
                    homeShots++
                    val sotProb = config.homeFinishing * 0.45
                    if (rand.nextDouble() < sotProb) {
                        homeSOT++
                        val goalProb = config.homeFinishing * (1.0 - config.awayGoalkeeping / 100.0) * 0.35
                        if (rand.nextDouble() < goalProb) {
                            homeScore++
                            goalEvents.add(GoalEvent(minute, "H", "$homeScore:$awayScore"))
                        }
                    }
                } else {
                    awayShots++
                    val sotProb = config.awayFinishing * 0.45
                    if (rand.nextDouble() < sotProb) {
                        awaySOT++
                        val goalProb = config.awayFinishing * (1.0 - config.homeGoalkeeping / 100.0) * 0.35
                        if (rand.nextDouble() < goalProb) {
                            awayScore++
                            goalEvents.add(GoalEvent(minute, "A", "$homeScore:$awayScore"))
                        }
                    }
                }
            }

            val cornerProb = if (isHomePossession) 0.04 * intensity else 0.04 * intensity
            if (rand.nextDouble() < cornerProb) {
                if (isHomePossession) homeCorners++ else awayCorners++
            }

            val foulProb = 0.022 * intensity
            if (rand.nextDouble() < foulProb) {
                if (isHomePossession) awayFouls++ else homeFouls++
                val cardProb = 0.13
                if (rand.nextDouble() < cardProb) {
                    if (isHomePossession) awayYellows++ else homeYellows++
                }
            }
        }

        val totalShots = homeShots + awayShots
        val homePossPct = if (totalShots > 0) config.homePossession * 100.0 else 50.0

        return SimulationResult(
            homeScore = homeScore, awayScore = awayScore,
            homeShots = homeShots, awayShots = awayShots,
            homeShotsOnTarget = homeSOT, awayShotsOnTarget = awaySOT,
            homeCorners = homeCorners, awayCorners = awayCorners,
            homePossessionPct = homePossPct,
            homeFouls = homeFouls, awayFouls = awayFouls,
            homeYellowCards = homeYellows, awayYellowCards = awayYellows,
            goalEvents = goalEvents,
        )
    }

    /** 批量模拟：运行 N 场比赛统计结果分布
     */
    data class BatchSimulationResult(
        val totalSimulations: Int,
        val homeWins: Int,
        val draws: Int,
        val awayWins: Int,
        val avgHomeScore: Double,
        val avgAwayScore: Double,
        val avgTotalGoals: Double,
        val over25Count: Int,
        val bttsCount: Int,
        val scoreDistribution: Map<String, Int>,
        val avgHomeShots: Double,
        val avgAwayShots: Double,
        val avgHomeCorners: Double,
        val avgAwayCorners: Double,
        val upsetCount: Int,
    )

    fun batchSimulate(
        config: SimulationConfig,
        simulations: Int = 1000,
        homeRank: Int = 1,
        awayRank: Int = 1,
    ): BatchSimulationResult {
        val rand = java.util.Random(42L)
        var homeWins = 0; var draws = 0; var awayWins = 0
        var over25 = 0; var btts = 0; var upsets = 0
        var sumHome = 0; var sumAway = 0; var sumTotal = 0
        var sumHomeShots = 0; var sumAwayShots = 0
        var sumHomeCorners = 0; var sumAwayCorners = 0
        val scoreFreq = HashMap<String, Int>()

        repeat(simulations) {
            val result = simulateMatch(config, rand)
            sumHome += result.homeScore; sumAway += result.awayScore
            sumTotal += result.homeScore + result.awayScore
            sumHomeShots += result.homeShots; sumAwayShots += result.awayShots
            sumHomeCorners += result.homeCorners; sumAwayCorners += result.awayCorners
            when {
                result.homeScore > result.awayScore -> {
                    homeWins++
                    if (homeRank > awayRank) upsets++
                }
                result.homeScore < result.awayScore -> {
                    awayWins++
                    if (awayRank > homeRank) upsets++
                }
                else -> draws++
            }
            if (result.homeScore + result.awayScore > 2) over25++
            if (result.homeScore > 0 && result.awayScore > 0) btts++
            val key = "${result.homeScore}:${result.awayScore}"
            scoreFreq[key] = (scoreFreq[key] ?: 0) + 1
        }

        return BatchSimulationResult(
            totalSimulations = simulations,
            homeWins = homeWins, draws = draws, awayWins = awayWins,
            avgHomeScore = sumHome.toDouble() / simulations,
            avgAwayScore = sumAway.toDouble() / simulations,
            avgTotalGoals = sumTotal.toDouble() / simulations,
            over25Count = over25, bttsCount = btts,
            scoreDistribution = scoreFreq,
            avgHomeShots = sumHomeShots.toDouble() / simulations,
            avgAwayShots = sumAwayShots.toDouble() / simulations,
            avgHomeCorners = sumHomeCorners.toDouble() / simulations,
            avgAwayCorners = sumAwayCorners.toDouble() / simulations,
            upsetCount = upsets,
        )
    }
}

// =============================================================================
// 第十九部分：高级权重计算系统
// -----------------------------------------------------------------------------
// 基于信息论和决策理论的权重分配，
// 通过信息增益、方差缩减等运算确定各证据源权重。
// =============================================================================

object WeightCalculator {

    /** 信息增益权重
     * IG(S, A) = H(S) - H(S|A)
     * w_i = IG_i / Σ IG_j
     */
    fun informationGainWeights(
        baseEntropy: Double,
        conditionalEntropies: List<Double>,
    ): List<Double> {
        val gains = conditionalEntropies.map { baseEntropy - it }
        val totalGain = gains.filter { it > 0 }.sum()
        if (totalGain <= 0) return gains.map { 1.0 / gains.size }
        return gains.map { maxOf(it, 0.0) / totalGain }
    }

    /** 方差缩减权重
     * w_i = (Var_before - Var_after_i) / Σ (Var_before - Var_after_j)
     */
    fun varianceReductionWeights(
        baseVariance: Double,
        conditionalVariances: List<Double>,
    ): List<Double> {
        val reductions = conditionalVariances.map {
            maxOf(baseVariance - it, 0.0)
        }
        val totalReduction = reductions.sum()
        if (totalReduction <= 0) return reductions.map { 1.0 / reductions.size }
        return reductions.map { it / totalReduction }
    }

    /** AHP 层次分析法权重
     * 通过成对比较矩阵计算权重
     * w_i = (Π a_ij)^(1/n) / Σ (Π a_kj)^(1/n)
     */
    fun ahpWeights(comparisonMatrix: Array<DoubleArray>): List<Double> {
        val n = comparisonMatrix.size
        if (n == 0) return emptyList()
        val geoMeans = DoubleArray(n)
        for (i in 0 until n) {
            var product = 1.0
            for (j in 0 until n) {
                product *= comparisonMatrix[i][j]
            }
            geoMeans[i] = product.pow(1.0 / n)
        }
        val sum = geoMeans.sum()
        return if (sum > 0) geoMeans.map { it / sum } else List(n) { 1.0 / n }
    }

    /** 熵权法
     * 基于数据离散度计算权重
     * E_j = -k * Σ p_ij * ln(p_ij)
     * w_j = (1 - E_j) / Σ (1 - E_k)
     */
    fun entropyWeights(data: Array<DoubleArray>): List<Double> {
        if (data.isEmpty()) return emptyList()
        val n = data.size
        val m = data[0].size
        val k = 1.0 / kotlin.math.ln(n.toDouble())
        val entropies = DoubleArray(m)
        for (j in 0 until m) {
            var sum = 0.0
            for (i in 0 until n) sum += data[i][j]
            var e = 0.0
            for (i in 0 until n) {
                val p = if (sum > 0) data[i][j] / sum else 0.0
                if (p > 0) e -= p * kotlin.math.ln(p)
            }
            entropies[j] = k * e
        }
        val dValues = entropies.map { 1.0 - it }
        val sumD = dValues.sum()
        return if (sumD > 0) dValues.map { it / sumD } else List(m) { 1.0 / m }
    }

    /** CRITIC 法权重
     * 结合对比强度和冲突性
     * C_j = σ_j * Σ (1 - r_jk)
     * w_j = C_j / Σ C_k
     */
    fun criticWeights(
        standardDeviations: List<Double>,
        correlationMatrix: Array<DoubleArray>,
    ): List<Double> {
        val m = standardDeviations.size
        if (m == 0) return emptyList()
        val cValues = DoubleArray(m)
        for (j in 0 until m) {
            var conflictSum = 0.0
            for (k in 0 until m) {
                conflictSum += (1.0 - correlationMatrix[j][k])
            }
            cValues[j] = standardDeviations[j] * conflictSum
        }
        val sumC = cValues.sum()
        return if (sumC > 0) cValues.map { it / sumC } else List(m) { 1.0 / m }
    }

    /** 加权平均融合
     * F = Σ (w_i * f_i) / Σ w_i
     */
    fun weightedAverage(values: List<Double>, weights: List<Double>): Double {
        if (values.size != weights.size || values.isEmpty()) return 0.0
        var sum = 0.0; var wSum = 0.0
        for (i in values.indices) {
            sum += values[i] * weights[i]
            wSum += weights[i]
        }
        return if (wSum > 0) sum / wSum else values.average()
    }

    /** Ordered Weighted Averaging (OWA)
     * 先排序再加权
     */
    fun owa(values: List<Double>, weights: List<Double>): Double {
        if (values.size != weights.size || values.isEmpty()) return 0.0
        val sorted = values.sortedDescending()
        return weightedAverage(sorted, weights)
    }
}

// =============================================================================
// 第二十部分：赛制模拟器
// -----------------------------------------------------------------------------
// 模拟小组赛、淘汰赛、联赛等不同赛制，
// 通过反复加减运算推进赛程。
// =============================================================================

object CompetitionSimulator {

    data class GroupStanding(
        val teamCode: String,
        val played: Int,
        val wins: Int,
        val draws: Int,
        val losses: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val points: Int,
    ) {
        val goalDifference: Int get() = goalsFor - goalsAgainst
        val isQualified: Boolean get() = points >= 4
    }

    fun simulateGroupStage(
        teams: List<String>,
        predictFunction: (String, String) -> Triple<Double, Double, Double>,
    ): List<GroupStanding> {
        val standings = teams.map {
            it to mutableMapOf(
                "P" to 0, "W" to 0, "D" to 0, "L" to 0,
                "GF" to 0, "GA" to 0, "Pts" to 0,
            )
        }.toMap().toMutableMap()

        for (i in teams.indices) {
            for (j in i + 1 until teams.size) {
                val home = teams[i]; val away = teams[j]
                val (pHome, pDraw, pAway) = predictFunction(home, away)
                val rand = java.util.Random()
                val roll = rand.nextDouble()
                val (hs, awayScore) = when {
                    roll < pHome -> sampleScoreFromProb(pHome, true)
                    roll < pHome + pDraw -> sampleDrawScore()
                    else -> sampleScoreFromProb(pAway, false)
                }
                standings[home]!!["P"] = standings[home]!!["P"]!! + 1
                standings[away]!!["P"] = standings[away]!!["P"]!! + 1
                standings[home]!!["GF"] = standings[home]!!["GF"]!! + hs
                standings[away]!!["GF"] = standings[away]!!["GF"]!! + awayScore
                standings[home]!!["GA"] = standings[home]!!["GA"]!! + awayScore
                standings[away]!!["GA"] = standings[away]!!["GA"]!! + hs
                when {
                    hs > awayScore -> {
                        standings[home]!!["W"] = standings[home]!!["W"]!! + 1
                        standings[away]!!["L"] = standings[away]!!["L"]!! + 1
                        standings[home]!!["Pts"] = standings[home]!!["Pts"]!! + 3
                    }
                    hs < awayScore -> {
                        standings[away]!!["W"] = standings[away]!!["W"]!! + 1
                        standings[home]!!["L"] = standings[home]!!["L"]!! + 1
                        standings[away]!!["Pts"] = standings[away]!!["Pts"]!! + 3
                    }
                    else -> {
                        standings[home]!!["D"] = standings[home]!!["D"]!! + 1
                        standings[away]!!["D"] = standings[away]!!["D"]!! + 1
                        standings[home]!!["Pts"] = standings[home]!!["Pts"]!! + 1
                        standings[away]!!["Pts"] = standings[away]!!["Pts"]!! + 1
                    }
                }
            }
        }

        return teams.map { t ->
            val s = standings[t]!!
            GroupStanding(t, s["P"]!!, s["W"]!!, s["D"]!!, s["L"]!!,
                s["GF"]!!, s["GA"]!!, s["Pts"]!!)
        }.sortedWith(
            compareByDescending<GroupStanding> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
        )
    }

    private fun sampleScoreFromProb(prob: Double, isWin: Boolean): Pair<Int, Int> {
        val rand = java.util.Random()
        val margin = when {
            prob > 0.6 -> rand.nextInt(3) + 1
            prob > 0.4 -> rand.nextInt(2) + 1
            else -> 1
        }
        val loserGoals = rand.nextInt(3)
        return if (isWin) margin to loserGoals else loserGoals to margin
    }

    private fun sampleDrawScore(): Pair<Int, Int> {
        val rand = java.util.Random()
        val goals = rand.nextInt(4)
        return goals to goals
    }

    data class KnockoutResult(
        val winner: String,
        val aggregateScore: String,
        val wentToPenalties: Boolean,
        val penaltyScore: String?,
    )

    fun simulateKnockout(
        home: String,
        away: String,
        predictFunction: (String, String) -> Triple<Double, Double, Double>,
    ): KnockoutResult {
        val (pHome, _, pAway) = predictFunction(home, away)
        val rand = java.util.Random()
        val lambdaHome = pHome * 3.5
        val lambdaAway = pAway * 3.5
        val hs = AdvancedSimulator.samplePoisson(lambdaHome, rand)
        val awayScore = AdvancedSimulator.samplePoisson(lambdaAway, rand)

        return if (hs != awayScore) {
            val winner = if (hs > awayScore) home else away
            KnockoutResult(winner, "$hs:$awayScore", false, null)
        } else {
            val penHome = rand.nextInt(5) + 3
            val penAway = if (rand.nextDouble() < 0.5) penHome else penHome - 1
            val winner = if (penHome > penAway) home else away
            KnockoutResult(winner, "$hs:$awayScore", true, "$penHome:$penAway")
        }
    }
}

// =============================================================================
// 第二十一部分：集成桥接层
// -----------------------------------------------------------------------------
// 将所有子系统整合到主预测流程中，
// 通过加权融合和校准运算输出最终预测。
// =============================================================================

object PredictionIntegration {

    /** 整合预测：融合所有子系统结果
     * 通过加权乘法和加法运算融合多模型预测
     */
    fun integratedPredict(
        home: Team,
        away: Team,
        trueHome: Boolean,
    ): PredictionOutput {
        val pHome = ProfileBuilder.buildProfile(home, away)
        val pAway = ProfileBuilder.buildProfile(away, home)
        val weather = EnvironmentAnalyzer.defaultWeather()
        val referee = EnvironmentAnalyzer.defaultReferee()

        val (xgH, xgA) = CoreEngine.expectedGoals(pHome, pAway, trueHome, weather)
        val mx = CoreEngine.scoreMatrix(xgH, xgA)
        val elo = CoreEngine.eloTriple(pHome, pAway, trueHome)
        val form = CoreEngine.formTriple(pHome, pAway)
        val h2h = CoreEngine.h2hTriple(pHome.h2h, elo)
        val homeAway = CoreEngine.homeAwayTriple(pHome, pAway, trueHome)

        val h2hWeight = if (pHome.h2h.played > 0) {
            pHome.h2h.played / (pHome.h2h.played + 4.0) * PredictionConstants.WEIGHT_H2H_MAX
        } else 0.0

        val fused = CoreEngine.fuseProbabilities(
            Triple(mx.pHome, mx.pDraw, mx.pAway),
            elo, form, h2h, homeAway, h2hWeight,
        )

        val concentration = maxOf(fused.first, fused.second, fused.third)
        val dataRichness = ((pHome.recentForm.matchesPlayed + pAway.recentForm.matchesPlayed) / 30.0)
            .coerceIn(0.0, 1.0)
        val confidence = ((concentration - 0.33) / 0.67 * 68 + dataRichness * 32)
            .coerceIn(5.0, 97.0).toInt()

        val totalXg = xgH + xgA
        val ou = MarketPredictor.overUnder(totalXg)
        val bttsProb = MarketPredictor.btts(xgH, xgA)
        val corners = MarketPredictor.corners(pHome, pAway, xgH, xgA)
        val cards = MarketPredictor.cards(pHome, pAway, referee)
        val htft = MarketPredictor.halfTimeFullTime(xgH, xgA)
        val firstGoal = MarketPredictor.firstGoalTime(totalXg)
        val mc = MonteCarloSimulator.simulate(xgH, xgA)
        val risk = MarketPredictor.riskLevel(confidence,
            pHome.recentForm.matchesPlayed + pAway.recentForm.matchesPlayed)
        val upset = MarketPredictor.upset(pHome, pAway, fused.first, fused.third)

        val teamBaselineH = TeamDatabase.findByCode(home.code)
        val teamBaselineA = TeamDatabase.findByCode(away.code)
        val bayesianWinRate = if (teamBaselineH != null) {
            BayesianEngine.updateWinRate(
                (teamBaselineH.homeWinRate * 10).toInt(),
                ((1 - teamBaselineH.homeWinRate - teamBaselineH.awayWinRate) * 10).toInt(),
                (teamBaselineH.awayWinRate * 10).toInt(),
            )
        } else 0.4

        val calibratedPHome = ConfidenceCalibrator.plattScaling(fused.first)
        val calibratedPAway = ConfidenceCalibrator.plattScaling(fused.third)
        val norm = calibratedPHome + fused.second + calibratedPAway
        val (fH, fD, fA) = if (norm > 0) {
            Triple(calibratedPHome / norm, fused.second / norm, calibratedPAway / norm)
        } else fused

        val factors = buildList {
            add("基础引擎：泊松 + Dixon-Coles + Elo + 近期状态 + 历史交战")
            add("期望进球：${"%.2f".format(xgH)} vs ${"%.2f".format(xgA)}")
            add("蒙特卡洛模拟：${mc.simulations} 次")
            add("贝叶斯校正胜率：${"%.1f".format(bayesianWinRate * 100)}%")
            add("置信度校准：Platt 缩放")
            if (teamBaselineH != null) add("数据库基准：${teamBaselineH.name} #$${teamBaselineH.rank}")
            if (teamBaselineA != null) add("数据库基准：${teamBaselineA.name} #$${teamBaselineA.rank}")
        }

        val scenarios = ScenarioAnalyzer.buildScenarios(
            pHome, pAway, fH, fD, fA, xgH, xgA, bttsProb, ou.over25, trueHome,
        )
        val recommendation = ScenarioAnalyzer.buildRecommendation(
            pHome, pAway, fH, fD, fA, confidence, mx.likelyScore, ou.over25, bttsProb, risk,
        )

        return PredictionOutput(
            pHome = fH, pDraw = fD, pAway = fA,
            likelyScore = mx.likelyScore,
            likelyScoreProbability = mx.likelyProbability,
            topScores = mx.topScores,
            xgHome = xgH, xgAway = xgA,
            confidence = confidence,
            riskLevel = risk,
            upsetProbability = upset,
            overProbabilities = ou,
            cornerPrediction = corners,
            cardPrediction = cards,
            halfTimeFullTime = htft,
            bttsProbability = bttsProb,
            firstGoalTimePrediction = firstGoal,
            monteCarlo = mc,
            factors = factors,
            scenarios = scenarios,
            recommendation = recommendation,
        )
    }
}

// =============================================================================
// 第二十二部分：时间衰减模型
// -----------------------------------------------------------------------------
// 足球比赛中近期表现比远期表现更具预测价值。
// 通过指数衰减、幂律衰减等函数给不同时期数据赋权。
// =============================================================================

object TimeDecayModels {

    /** 指数时间衰减：权重 = λ^t
     * λ 越接近 1，远期比赛权重越大
     * 通过乘法运算实现
     */
    fun exponentialDecay(lambda: Double, daysAgo: Int): Double {
        return lambda.pow(daysAgo)
    }

    /** 半衰期衰减：权重 = (1/2)^(t / halfLife)
     * 通过除法和乘法运算
     */
    fun halfLifeDecay(halfLifeDays: Double, daysAgo: Int): Double {
        if (halfLifeDays <= 0) return 1.0
        return 0.5.pow(daysAgo / halfLifeDays)
    }

    /** 幂律衰减：权重 = 1 / (1 + t)^alpha
     * alpha 越大衰减越快
     * 通过加法和乘法运算
     */
    fun powerLawDecay(alpha: Double, daysAgo: Int): Double {
        val denominator = (1.0 + daysAgo).pow(alpha)
        return if (denominator > 0) 1.0 / denominator else 0.0
    }

    /** 高斯时间衰减：权重 = exp(-t^2 / (2*sigma^2))
     * 越近的比赛权重越大，远期快速下降
     * 通过乘法、除法、指数运算
     */
    fun gaussianDecay(sigmaDays: Double, daysAgo: Int): Double {
        if (sigmaDays <= 0) return 1.0
        val t2 = daysAgo.toDouble() * daysAgo.toDouble()
        val s2 = sigmaDays * sigmaDays
        return exp(-t2 / (2.0 * s2))
    }

    /** 线性时间衰减：权重 = max(0, 1 - t / maxAge)
     * 通过减法、除法运算
     */
    fun linearDecay(maxAgeDays: Int, daysAgo: Int): Double {
        if (maxAgeDays <= 0) return 0.0
        return maxOf(0.0, 1.0 - daysAgo.toDouble() / maxAgeDays.toDouble())
    }

    /** 多项式时间衰减：权重 = 1 / (1 + (t/k)^p)
     * k 为拐点，p 为陡度
     */
    fun polynomialDecay(k: Double, p: Double, daysAgo: Int): Double {
        if (k <= 0) return 1.0
        val tDivK = daysAgo.toDouble() / k
        return 1.0 / (1.0 + tDivK.pow(p))
    }

    /** 双指数衰减：权重 = α * exp(-t/τ1) + (1-α) * exp(-t/τ2)
     * 结合长短期记忆
     */
    fun doubleExponentialDecay(
        alpha: Double,
        tau1: Double,
        tau2: Double,
        daysAgo: Int,
    ): Double {
        val w1 = if (tau1 > 0) exp(-daysAgo.toDouble() / tau1) else 0.0
        val w2 = if (tau2 > 0) exp(-daysAgo.toDouble() / tau2) else 0.0
        return alpha * w1 + (1.0 - alpha) * w2
    }

    /** 时间加权移动平均
     * TWMA = Σ(w_t * value_t) / Σ(w_t)
     */
    fun timeWeightedAverage(
        values: List<Double>,
        daysAgo: List<Int>,
        decay: (Int) -> Double,
    ): Double {
        if (values.size != daysAgo.size || values.isEmpty()) return 0.0
        var weightedSum = 0.0; var weightSum = 0.0
        for (i in values.indices) {
            val w = decay(daysAgo[i])
            weightedSum += w * values[i]
            weightSum += w
        }
        return if (weightSum > 0) weightedSum / weightSum else values.average()
    }

    /** 计算加权标准差
     * Var = Σ w_i * (x_i - μ)^2 / Σ w_i
     */
    fun weightedStandardDeviation(
        values: List<Double>,
        weights: List<Double>,
    ): Double {
        if (values.size != weights.size || values.size < 2) return 0.0
        var sumW = 0.0; var sumWX = 0.0
        for (i in values.indices) {
            sumW += weights[i]
            sumWX += weights[i] * values[i]
        }
        val mean = if (sumW > 0) sumWX / sumW else values.average()
        var sumWSq = 0.0
        for (i in values.indices) {
            val diff = values[i] - mean
            sumWSq += weights[i] * diff * diff
        }
        return if (sumW > 0) sqrt(sumWSq / sumW) else 0.0
    }

    /** 最近 N 场比赛加权得分
     * 通过时间衰减加权最近比赛结果
     */
    fun recentWeightedScore(
        scores: List<Double>,
        daysAgo: List<Int>,
        halfLifeDays: Double = 180.0,
    ): Double {
        val decay = { days: Int -> halfLifeDecay(halfLifeDays, days) }
        return timeWeightedAverage(scores, daysAgo, decay)
    }
}

// =============================================================================
// 第二十三部分：神经网络前向传播（小型多层感知器）
// -----------------------------------------------------------------------------
// 使用手动实现的前向传播，用于非线性特征组合。
// 每层：z = W*x + b, a = activation(z)
// 所有运算为矩阵乘法、加法和激活函数。
// =============================================================================

object NeuralNetworkModel {

    enum class ActivationFunction {
        RELU, SIGMOID, TANH, SOFTMAX, LINEAR
    }

    /** ReLU 激活：max(0, x) */
    fun relu(x: Double): Double = maxOf(0.0, x)

    /** Sigmoid 激活：1 / (1 + exp(-x)) */
    fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    /** Tanh 激活：(exp(x) - exp(-x)) / (exp(x) + exp(-x)) */
    fun tanh(x: Double): Double {
        val ex = exp(x)
        val enx = exp(-x)
        return (ex - enx) / (ex + enx)
    }

    /** Softmax 激活：exp(x_i) / Σ exp(x_j) */
    fun softmax(values: DoubleArray): DoubleArray = PredictMath.softmax(values)

    /** 应用激活函数 */
    fun activate(values: DoubleArray, fn: ActivationFunction): DoubleArray {
        return when (fn) {
            ActivationFunction.RELU -> DoubleArray(values.size) { relu(values[it]) }
            ActivationFunction.SIGMOID -> DoubleArray(values.size) { sigmoid(values[it]) }
            ActivationFunction.TANH -> DoubleArray(values.size) { tanh(values[it]) }
            ActivationFunction.SOFTMAX -> softmax(values)
            ActivationFunction.LINEAR -> values.copyOf()
        }
    }

    /** 矩阵向量乘法：y = W * x
     * W 是 rows x cols 矩阵，x 是 cols 维向量
     * y_i = Σ_j W[i][j] * x[j]
     */
    fun matrixVectorMultiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val rows = matrix.size
        if (rows == 0) return DoubleArray(0)
        val result = DoubleArray(rows)
        for (i in 0 until rows) {
            var sum = 0.0
            for (j in vector.indices) {
                if (j < matrix[i].size) sum += matrix[i][j] * vector[j]
            }
            result[i] = sum
        }
        return result
    }

    /** 向量加法：z = a + b */
    fun vectorAdd(a: DoubleArray, b: DoubleArray): DoubleArray {
        val size = minOf(a.size, b.size)
        return DoubleArray(size) { a[it] + b[it] }
    }

    /** 单隐藏层 MLP 前向传播
     * input -> hidden -> output
     * 通过连续两次矩阵乘法和加法
     */
    fun mlpForward(
        input: DoubleArray,
        weights1: Array<DoubleArray>,
        bias1: DoubleArray,
        weights2: Array<DoubleArray>,
        bias2: DoubleArray,
        hiddenActivation: ActivationFunction = ActivationFunction.RELU,
        outputActivation: ActivationFunction = ActivationFunction.SIGMOID,
    ): DoubleArray {
        val hiddenPre = vectorAdd(matrixVectorMultiply(weights1, input), bias1)
        val hiddenPost = activate(hiddenPre, hiddenActivation)
        val outputPre = vectorAdd(matrixVectorMultiply(weights2, hiddenPost), bias2)
        return activate(outputPre, outputActivation)
    }

    /** 从球队特征构建 MLP 输入向量
     * 特征包括：排名差、积分差、Elo差、近期状态、攻防等
     * 通过加减法归一化
     */
    fun buildFeatureVector(
        home: PredictionTeamProfile,
        away: PredictionTeamProfile,
        trueHome: Boolean,
    ): DoubleArray {
        return doubleArrayOf(
            (1500.0 - home.rank.toDouble()) / 1500.0 - (1500.0 - away.rank.toDouble()) / 1500.0,
            (home.points - away.points) / 500.0,
            (home.eloRating - away.eloRating) / 800.0,
            (home.recentForm.pointsPerGame - away.recentForm.pointsPerGame) / 3.0,
            (home.recentForm.goalsForPerGame - away.recentForm.goalsForPerGame) / 2.0,
            (away.recentForm.goalsAgainstPerGame - home.recentForm.goalsAgainstPerGame) / 2.0,
            (home.attackDefense.attackStrength - away.attackDefense.attackStrength),
            (away.attackDefense.defenseStrength - home.attackDefense.defenseStrength),
            if (trueHome) home.homeAway.homeWinRate - away.homeAway.awayWinRate else 0.0,
            (home.overallStrength - away.overallStrength) / 100.0,
        )
    }

    /** 预测三结果概率：输出层 3 个神经元 + softmax
     * 通过指数和除法运算
     */
    fun predictTriple(
        home: PredictionTeamProfile,
        away: PredictionTeamProfile,
        trueHome: Boolean,
    ): Triple<Double, Double, Double> {
        val input = buildFeatureVector(home, away, trueHome)
        val weights1 = arrayOf(
            doubleArrayOf(0.5, 0.3, 0.4, 0.2, 0.3, -0.2, 0.4, -0.3, 0.2, 0.3),
            doubleArrayOf(0.2, 0.1, 0.2, 0.3, 0.2, 0.2, 0.1, 0.1, 0.0, 0.1),
            doubleArrayOf(-0.4, -0.3, -0.4, -0.2, -0.3, 0.2, -0.4, 0.3, -0.2, -0.3),
            doubleArrayOf(0.1, 0.2, 0.1, 0.1, 0.2, 0.1, 0.2, 0.1, 0.1, 0.2),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0),
        )
        val bias1 = doubleArrayOf(0.1, -0.2, 0.1, 0.0, 0.0)
        val weights2 = arrayOf(
            doubleArrayOf(0.6, 0.2, -0.6, 0.1, 0.2),
            doubleArrayOf(0.1, 0.7, 0.1, 0.2, -0.1),
            doubleArrayOf(-0.6, 0.1, 0.6, -0.1, -0.2),
        )
        val bias2 = doubleArrayOf(0.2, -0.3, 0.2)
        val output = mlpForward(input, weights1, bias1, weights2, bias2,
            hiddenActivation = ActivationFunction.RELU,
            outputActivation = ActivationFunction.SOFTMAX)
        val sum = output.sum()
        return if (sum > 0) Triple(output[0], output[1], output[2])
               else Triple(0.4, 0.28, 0.32)
    }
}

// =============================================================================
// 第二十四部分：主成分分析（PCA）
// -----------------------------------------------------------------------------
// 将高维球队特征降维到低维主成分空间，
// 便于可视化和去噪。通过协方差矩阵和特征向量实现。
// =============================================================================

object PrincipalComponentAnalysis {

    /** 数据中心化：每个特征减去均值
     * 通过减法运算
     */
    fun centerData(data: Array<DoubleArray>): Array<DoubleArray> {
        if (data.isEmpty()) return data
        val n = data.size; val m = data[0].size
        val means = DoubleArray(m)
        for (j in 0 until m) {
            var sum = 0.0
            for (i in 0 until n) sum += data[i][j]
            means[j] = sum / n
        }
        val centered = Array(n) { DoubleArray(m) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                centered[i][j] = data[i][j] - means[j]
            }
        }
        return centered
    }

    /** 协方差矩阵
     * Cov(X,Y) = Σ((X_i - X̄)(Y_i - Ȳ)) / (n-1)
     * 通过乘法、加法、除法运算
     */
    fun covarianceMatrix(data: Array<DoubleArray>): Array<DoubleArray> {
        if (data.isEmpty()) return Array(0) { DoubleArray(0) }
        val n = data.size; val m = data[0].size
        val centered = centerData(data)
        val cov = Array(m) { DoubleArray(m) }
        for (j1 in 0 until m) {
            for (j2 in 0 until m) {
                var sum = 0.0
                for (i in 0 until n) {
                    sum += centered[i][j1] * centered[i][j2]
                }
                cov[j1][j2] = sum / (n - 1)
            }
        }
        return cov
    }

    /** 标准差归一化（Z-score）
     * z = (x - μ) / σ
     * 通过减法、除法运算
     */
    fun standardize(data: Array<DoubleArray>): Array<DoubleArray> {
        if (data.isEmpty()) return data
        val n = data.size; val m = data[0].size
        val means = DoubleArray(m); val stds = DoubleArray(m)
        for (j in 0 until m) {
            var sum = 0.0; var sumSq = 0.0
            for (i in 0 until n) {
                sum += data[i][j]
                sumSq += data[i][j] * data[i][j]
            }
            means[j] = sum / n
            stds[j] = sqrt((sumSq / n) - means[j] * means[j]).coerceAtLeast(1e-10)
        }
        val standardized = Array(n) { DoubleArray(m) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                standardized[i][j] = (data[i][j] - means[j]) / stds[j]
            }
        }
        return standardized
    }

    /** 矩阵转置 */
    fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> {
        if (matrix.isEmpty()) return matrix
        val rows = matrix.size; val cols = matrix[0].size
        val transposed = Array(cols) { DoubleArray(rows) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                transposed[j][i] = matrix[i][j]
            }
        }
        return transposed
    }

    /** 矩阵乘法 */
    fun matrixMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        if (a.isEmpty() || b.isEmpty() || a[0].size != b.size) return Array(0) { DoubleArray(0) }
        val rows = a.size; val cols = b[0].size; val inner = b.size
        val result = Array(rows) { DoubleArray(cols) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                var sum = 0.0
                for (k in 0 until inner) {
                    sum += a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    /** 特征值近似（幂迭代法）
     * 用于提取最大特征值对应的特征向量
     * 通过反复矩阵向量乘法
     */
    fun powerIteration(matrix: Array<DoubleArray>, iterations: Int = 100): Pair<Double, DoubleArray> {
        if (matrix.isEmpty()) return 0.0 to DoubleArray(0)
        val n = matrix.size
        var vector = DoubleArray(n) { 1.0 / n }
        var eigenvalue = 0.0
        repeat(iterations) {
            val newVector = matrixVectorMultiplyLocal(matrix, vector)
            val norm = sqrt(newVector.sumOf { it * it })
            vector = if (norm > 0) DoubleArray(n) { newVector[it] / norm } else newVector
            eigenvalue = rayleighQuotient(matrix, vector)
        }
        return eigenvalue to vector
    }

    private fun matrixVectorMultiplyLocal(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        return NeuralNetworkModel.matrixVectorMultiply(matrix, vector)
    }

    /** Rayleigh 商：λ = (v^T * A * v) / (v^T * v)
     * 通过乘法、加法、除法运算
     */
    private fun rayleighQuotient(matrix: Array<DoubleArray>, vector: DoubleArray): Double {
        val n = vector.size
        var numerator = 0.0; var denominator = 0.0
        for (i in 0 until n) {
            var sum = 0.0
            for (j in 0 until n) {
                if (j < matrix[i].size) sum += matrix[i][j] * vector[j]
            }
            numerator += vector[i] * sum
            denominator += vector[i] * vector[i]
        }
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 计算解释方差比
     * ratio = λ_i / Σλ_i
     * 通过除法运算
     */
    fun explainedVarianceRatio(eigenvalues: List<Double>): List<Double> {
        val total = eigenvalues.sum()
        return if (total > 0) eigenvalues.map { it / total } else eigenvalues.map { 0.0 }
    }

    /** 累计解释方差
     * 通过连续加法
     */
    fun cumulativeExplainedVariance(eigenvalues: List<Double>): List<Double> {
        val ratios = explainedVarianceRatio(eigenvalues)
        val cumulative = ArrayList<Double>(ratios.size)
        var sum = 0.0
        for (r in ratios) {
            sum += r
            cumulative.add(sum)
        }
        return cumulative
    }

    /** 选择主成分数量（达到阈值）
     * 通过比较累计方差
     */
    fun selectComponents(eigenvalues: List<Double>, threshold: Double = 0.9): Int {
        val cumulative = cumulativeExplainedVariance(eigenvalues)
        for (i in cumulative.indices) {
            if (cumulative[i] >= threshold) return i + 1
        }
        return cumulative.size
    }

    /** 将球队数据投影到主成分空间
     * 通过矩阵向量乘法
     */
    fun project(data: Array<DoubleArray>, components: Array<DoubleArray>): Array<DoubleArray> {
        val standardized = standardize(data)
        return matrixMultiply(standardized, transpose(components))
    }
}

// =============================================================================
// 第二十五部分：聚类分析
// -----------------------------------------------------------------------------
// k-Means 聚类用于识别相似风格的球队，
// 通过迭代最近中心分配和中心更新。
// =============================================================================

object ClusteringAnalysis {

    /** 欧氏距离
     * d = sqrt(Σ(x_i - y_i)^2)
     * 通过减法、乘法、加法、开方
     */
    fun euclideanDistance(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        var sum = 0.0
        for (i in 0 until n) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /** 曼哈顿距离
     * d = Σ|x_i - y_i|
     */
    fun manhattanDistance(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        var sum = 0.0
        for (i in 0 until n) sum += abs(a[i] - b[i])
        return sum
    }

    /** 闵可夫斯基距离
     * d = (Σ|x_i - y_i|^p)^(1/p)
     */
    fun minkowskiDistance(a: DoubleArray, b: DoubleArray, p: Double): Double {
        val n = minOf(a.size, b.size)
        if (p <= 0) return euclideanDistance(a, b)
        var sum = 0.0
        for (i in 0 until n) sum += abs(a[i] - b[i]).pow(p)
        return sum.pow(1.0 / p)
    }

    /** 余弦相似度
     * sim = Σ(x_i * y_i) / (sqrt(Σx_i^2) * sqrt(Σy_i^2))
     */
    fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0; var normA = 0.0; var normB = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA * normB)
        return if (denom > 0) dot / denom else 0.0
    }

    /** K-Means 聚类算法
     * 1. 随机初始化 K 个中心
     * 2. 分配每个点到最近中心
     * 3. 更新中心为均值
     * 4. 重复直到收敛
     * 通过减法、比较、加法、除法运算
     */
    data class KMeansResult(
        val centroids: Array<DoubleArray>,
        val assignments: IntArray,
        val iterations: Int,
        val inertia: Double,
    )

    fun kMeans(
        data: Array<DoubleArray>,
        k: Int,
        maxIterations: Int = 100,
        tolerance: Double = 1e-4,
    ): KMeansResult {
        if (data.isEmpty() || k <= 0) {
            return KMeansResult(Array(0) { DoubleArray(0) }, IntArray(0), 0, 0.0)
        }
        val n = data.size; val dim = data[0].size
        val centroids = Array(k) { idx -> data[(idx * n / k).coerceIn(0, n - 1)].copyOf() }
        val assignments = IntArray(n)
        var inertia = Double.MAX_VALUE
        var iter = 0
        var converged = false

        while (iter < maxIterations && !converged) {
            var newInertia = 0.0
            for (i in 0 until n) {
                var minDist = Double.MAX_VALUE
                var bestCluster = 0
                for (c in 0 until k) {
                    val d = euclideanDistance(data[i], centroids[c])
                    if (d < minDist) {
                        minDist = d
                        bestCluster = c
                    }
                }
                assignments[i] = bestCluster
                newInertia += minDist * minDist
            }

            val counts = IntArray(k)
            val newCentroids = Array(k) { DoubleArray(dim) }
            for (i in 0 until n) {
                val c = assignments[i]
                counts[c]++
                for (d in 0 until dim) {
                    newCentroids[c][d] += data[i][d]
                }
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    for (d in 0 until dim) {
                        newCentroids[c][d] = newCentroids[c][d] / counts[c].toDouble()
                    }
                }
            }

            val shift = (0 until k).sumOf { c -> euclideanDistance(centroids[c], newCentroids[c]) }
            for (c in 0 until k) centroids[c] = newCentroids[c]
            converged = abs(inertia - newInertia) < tolerance || shift < tolerance
            inertia = newInertia
            iter++
        }
        return KMeansResult(centroids, assignments, iter, inertia)
    }

    /** 肘部法则：选择最佳聚类数
     * 通过惯性下降率判断拐点
     */
    fun elbowMethod(data: Array<DoubleArray>, maxK: Int = 10): Int {
        val inertias = ArrayList<Double>(maxK)
        for (k in 1..maxK) {
            val result = kMeans(data, k, maxIterations = 30)
            inertias.add(result.inertia)
        }
        if (inertias.size < 3) return 2
        var bestK = 1
        var maxDrop = 0.0
        for (k in 1 until inertias.size - 1) {
            val drop = inertias[k - 1] - inertias[k]
            val nextDrop = inertias[k] - inertias[k + 1]
            val elbow = drop - nextDrop
            if (elbow > maxDrop) {
                maxDrop = elbow
                bestK = k
            }
        }
        return bestK
    }

    /** 轮廓系数（简化版）
     * 衡量聚类质量：s = (b - a) / max(a, b)
     * a 为同类平均距离，b 为最近异类平均距离
     */
    fun silhouetteScore(data: Array<DoubleArray>, assignments: IntArray): Double {
        if (data.size < 3) return 0.0
        val n = data.size
        val clusters = assignments.toSet()
        var totalScore = 0.0
        var valid = 0
        for (i in 0 until n) {
            val ownCluster = assignments[i]
            val ownMembers = (0 until n).filter { assignments[it] == ownCluster && it != i }
            if (ownMembers.size < 1) continue
            val a = ownMembers.map { euclideanDistance(data[i], data[it]) }.average()
            var minB = Double.MAX_VALUE
            for (c in clusters) {
                if (c == ownCluster) continue
                val otherMembers = (0 until n).filter { assignments[it] == c }
                if (otherMembers.isEmpty()) continue
                val b = otherMembers.map { euclideanDistance(data[i], data[it]) }.average()
                if (b < minB) minB = b
            }
            if (minB < Double.MAX_VALUE) {
                totalScore += (minB - a) / maxOf(a, minB)
                valid++
            }
        }
        return if (valid > 0) totalScore / valid else 0.0
    }

    /** 为球队聚类：根据攻防风格分组
     * 输入：attack, defense, possession, pressing 等特征
     */
    fun clusterTeams(
        teams: List<TeamBaselineData>,
        k: Int = 5,
    ): Map<String, Int> {
        val features = teams.map { t ->
            doubleArrayOf(
                t.attackRating / 100.0,
                t.defenseRating / 100.0,
                t.possession / 100.0,
                t.pressingIntensity / 100.0,
                t.avgGoalsFor / 3.0,
                t.avgGoalsAgainst / 3.0,
            )
        }.toTypedArray()
        val result = kMeans(features, k)
        return teams.mapIndexed { idx, team -> team.code to result.assignments[idx] }.toMap()
    }
}

// =============================================================================
// 第二十六部分：特征工程
// -----------------------------------------------------------------------------
// 从原始比赛数据中提取、构造、选择预测特征，
// 所有转换基于加减乘除等基础运算。
// =============================================================================

object FeatureEngineering {

    /** 基础特征归一化：min-max
     * x' = (x - min) / (max - min)
     * 通过减法和除法
     */
    fun minMaxNormalize(value: Double, min: Double, max: Double): Double {
        val range = max - min
        return if (range > 0) (value - min) / range else 0.0
    }

    /** Z-score 标准化
     * z = (x - μ) / σ
     */
    fun zScore(value: Double, mean: Double, stdDev: Double): Double {
        return if (stdDev > 0) (value - mean) / stdDev else 0.0
    }

    /** 小数定标归一化
     * x' = x / 10^k
     */
    fun decimalScaling(values: List<Double>): List<Double> {
        if (values.isEmpty()) return values
        val maxAbs = values.maxOfOrNull { abs(it) } ?: 1.0
        if (maxAbs <= 0) return values
        val k = kotlin.math.ceil(kotlin.math.log10(maxAbs)).toInt()
        val scale = 10.0.pow(k)
        return values.map { it / scale }
    }

    /** 排名差距特征
     * 排名差对胜率的影响是非线性的
     * 通过除法和对数运算
     */
    fun rankGapFeature(rankA: Int, rankB: Int): Double {
        val gap = rankB - rankA
        return gap.toDouble() / (1.0 + kotlin.math.log10(1.0 + minOf(rankA, rankB).toDouble()))
    }

    /** 积分差距特征
     * 标准化到 [-1, 1]
     */
    fun pointsGapFeature(pointsA: Double, pointsB: Double): Double {
        val gap = pointsA - pointsB
        return (gap / 500.0).coerceIn(-1.0, 1.0)
    }

    /** 攻防比率特征
     * attack / defense
     * 通过除法运算
     */
    fun attackDefenseRatio(attack: Double, defense: Double): Double {
        return if (defense > 0) attack / defense else attack
    }

    /** 主客场优势特征
     * 主场胜率 / 客场胜率
     */
    fun homeAdvantageFeature(homeWinRate: Double, awayWinRate: Double): Double {
        return if (awayWinRate > 0.05) homeWinRate / awayWinRate else homeWinRate / 0.05
    }

    /** 进球预期差异
     * (主队进攻 / 客队防守) - (客队进攻 / 主队防守)
     */
    fun goalExpectancyDiff(
        homeAttack: Double,
        homeDefense: Double,
        awayAttack: Double,
        awayDefense: Double,
    ): Double {
        val homeExp = if (awayDefense > 0) homeAttack / awayDefense else homeAttack
        val awayExp = if (homeDefense > 0) awayAttack / homeDefense else awayAttack
        return homeExp - awayExp
    }

    /** 交互特征：两特征的乘积
     * 用于捕捉非线性组合
     */
    fun interactionFeature(a: Double, b: Double): Double = a * b

    /** 多项式特征：平方
     * x^2
     */
    fun squaredFeature(x: Double): Double = x * x

    /** 比率特征
     * a / (a + b)
     */
    fun proportionFeature(a: Double, b: Double): Double {
        val sum = a + b
        return if (sum > 0) a / sum else 0.5
    }

    /** 动量特征：近期变化率
     * (current - previous) / previous
     */
    fun momentumFeature(current: Double, previous: Double): Double {
        return if (previous != 0.0) (current - previous) / previous else 0.0
    }

    /** 综合特征向量构建 */
    fun buildMatchFeatures(
        home: TeamBaselineData,
        away: TeamBaselineData,
        trueHome: Boolean,
    ): DoubleArray {
        val rankGap = rankGapFeature(home.rank, away.rank)
        val pointsGap = pointsGapFeature(home.points, away.points)
        val eloGap = pointsGapFeature(home.eloRating, away.eloRating)
        val attackRatioH = attackDefenseRatio(home.attackRating.toDouble(), away.defenseRating.toDouble())
        val attackRatioA = attackDefenseRatio(away.attackRating.toDouble(), home.defenseRating.toDouble())
        val goalExpDiff = goalExpectancyDiff(
            home.attackRating.toDouble(), home.defenseRating.toDouble(),
            away.attackRating.toDouble(), away.defenseRating.toDouble(),
        )
        val homeAdv = if (trueHome) homeAdvantageFeature(home.homeWinRate, away.awayWinRate) else 1.0
        val possessionGap = (home.possession - away.possession) / 100.0
        val pressingGap = (home.pressingIntensity - away.pressingIntensity) / 100.0
        val goalFormGap = (home.avgGoalsFor - away.avgGoalsFor) / 2.0
        val defenseFormGap = (away.avgGoalsAgainst - home.avgGoalsAgainst) / 2.0
        return doubleArrayOf(
            rankGap, pointsGap, eloGap,
            attackRatioH / 2.0, attackRatioA / 2.0, goalExpDiff / 2.0,
            homeAdv / 3.0, possessionGap, pressingGap,
            goalFormGap, defenseFormGap,
            interactionFeature(rankGap, pointsGap),
            squaredFeature(pointsGap),
        )
    }
}

// =============================================================================
// 第二十七部分：异常检测系统
// -----------------------------------------------------------------------------
// 识别冷门比赛和异常结果，
// 基于统计距离和概率阈值。
// =============================================================================

object AnomalyDetection {

    /** Z-score 异常检测
     * |z| > threshold 判定为异常
     * 通过减法和除法
     */
    fun zScoreAnomaly(value: Double, mean: Double, stdDev: Double, threshold: Double = 2.5): Boolean {
        if (stdDev <= 0) return false
        val z = abs(value - mean) / stdDev
        return z > threshold
    }

    /** IQR 异常检测
     * 超出 [Q1 - 1.5*IQR, Q3 + 1.5*IQR] 为异常
     * 通过减法和加法
     */
    fun iqrAnomaly(value: Double, q1: Double, q3: Double): Boolean {
        val iqr = q3 - q1
        val lower = q1 - 1.5 * iqr
        val upper = q3 + 1.5 * iqr
        return value < lower || value > upper
    }

    /** 百分位数计算
     * 排序后根据位置取值
     */
    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = (p / 100.0 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    /** 马氏距离（使用协方差矩阵）
     * D^2 = (x - μ)^T * Σ^{-1} * (x - μ)
     * 这里使用对角近似
     */
    fun mahalanobisDistanceSquared(
        point: DoubleArray,
        mean: DoubleArray,
        stdDevs: DoubleArray,
    ): Double {
        val n = minOf(point.size, mean.size, stdDevs.size)
        var dist = 0.0
        for (i in 0 until n) {
            if (stdDevs[i] > 0) {
                val diff = point[i] - mean[i]
                dist += diff * diff / (stdDevs[i] * stdDevs[i])
            }
        }
        return dist
    }

    /** 基于概率的异常检测
     * 如果某结果的概率极低，判定为冷门
     */
    fun probabilityAnomaly(probability: Double, threshold: Double = 0.15): Boolean {
        return probability < threshold
    }

    /** 冷门检测：基于排名差和结果
     * 排名低（数字大）的球队击败排名高（数字小）的球队
     * 通过比较运算
     */
    fun upsetAnomaly(homeRank: Int, awayRank: Int, homeScore: Int, awayScore: Int): Boolean {
        return if (homeScore > awayScore) homeRank > awayRank + 10
        else if (homeScore < awayScore) awayRank > homeRank + 10
        else false
    }

    /** 进球异常检测：异常大比分或零进球
     * 通过阈值比较
     */
    fun scoreAnomaly(homeScore: Int, awayScore: Int, avgGoals: Double = 2.5): Boolean {
        val total = homeScore + awayScore
        val z = abs(total - avgGoals) / sqrt(avgGoals)
        return z > 2.5 || total == 0
    }

    /** 检测历史比赛中的异常结果
     * 返回冷门场次数
     */
    fun detectHistoricalUpsets(matches: List<HistoricalMatch>): List<HistoricalMatch> {
        val baseline = TeamDatabase.ALL_TEAMS.associateBy { it.code }
        return matches.filter { m ->
            val homeRank = baseline[m.homeCode]?.rank ?: 100
            val awayRank = baseline[m.awayCode]?.rank ?: 100
            upsetAnomaly(homeRank, awayRank, m.homeScore, m.awayScore)
        }
    }
}

// =============================================================================
// 第二十八部分：关联规则挖掘
// -----------------------------------------------------------------------------
// 发现比赛结果与特征之间的关联关系，
// 通过支持度、置信度和提升度计算。
// =============================================================================

object AssociationRuleMining {

    data class AssociationRule(
        val antecedent: String,
        val consequent: String,
        val support: Double,
        val confidence: Double,
        val lift: Double,
    )

    /** 支持度：P(A∪B)
     * 通过除法运算
     */
    fun support(
        antecedent: (HistoricalMatch) -> Boolean,
        consequent: (HistoricalMatch) -> Boolean,
        matches: List<HistoricalMatch>,
    ): Double {
        if (matches.isEmpty()) return 0.0
        val both = matches.count { antecedent(it) && consequent(it) }
        return both.toDouble() / matches.size
    }

    /** 置信度：P(B|A) = P(A∩B) / P(A)
     * 通过除法运算
     */
    fun confidence(
        antecedent: (HistoricalMatch) -> Boolean,
        consequent: (HistoricalMatch) -> Boolean,
        matches: List<HistoricalMatch>,
    ): Double {
        if (matches.isEmpty()) return 0.0
        val antecedentCount = matches.count { antecedent(it) }
        if (antecedentCount == 0) return 0.0
        val both = matches.count { antecedent(it) && consequent(it) }
        return both.toDouble() / antecedentCount
    }

    /** 提升度：Lift = P(B|A) / P(B)
     * 通过除法运算
     */
    fun lift(
        antecedent: (HistoricalMatch) -> Boolean,
        consequent: (HistoricalMatch) -> Boolean,
        matches: List<HistoricalMatch>,
    ): Double {
        val conf = confidence(antecedent, consequent, matches)
        if (matches.isEmpty()) return 0.0
        val consequentProb = matches.count { consequent(it) }.toDouble() / matches.size
        return if (consequentProb > 0) conf / consequentProb else 0.0
    }

    /** 挖掘常见关联规则
     * 例如：强队主场 -> 大球
     */
    fun mineRules(matches: List<HistoricalMatch>): List<AssociationRule> {
        val rules = ArrayList<AssociationRule>()
        val isStrongHome: (HistoricalMatch) -> Boolean = { it.homePossession > 55 }
        val isHomeWin: (HistoricalMatch) -> Boolean = { it.homeScore > it.awayScore }
        val isOver25: (HistoricalMatch) -> Boolean = { it.homeScore + it.awayScore > 2 }
        val isBtts: (HistoricalMatch) -> Boolean = { it.homeScore > 0 && it.awayScore > 0 }
        val isHighShots: (HistoricalMatch) -> Boolean = { it.homeShots + it.awayShots > 25 }
        val isManyCards: (HistoricalMatch) -> Boolean = { it.homeYellowCards + it.awayYellowCards >= 4 }

        val ruleConfigs = listOf(
            "强队主场" to isStrongHome,
            "主队获胜" to isHomeWin,
            "大2.5球" to isOver25,
            "双方进球" to isBtts,
            "高射门数" to isHighShots,
            "多张黄牌" to isManyCards,
        )

        for ((anteName, anteFn) in ruleConfigs) {
            for ((consName, consFn) in ruleConfigs) {
                if (anteName == consName) continue
                val sup = support(anteFn, consFn, matches)
                val conf = confidence(anteFn, consFn, matches)
                val l = lift(anteFn, consFn, matches)
                if (sup >= 0.05 && conf >= 0.5) {
                    rules.add(AssociationRule(anteName, consName, sup, conf, l))
                }
            }
        }
        return rules.sortedByDescending { it.lift }
    }
}

// =============================================================================
// 第二十九部分：遗传算法优化器
// -----------------------------------------------------------------------------
// 用于优化预测模型权重，
// 通过选择、交叉、变异等运算进化种群。
// =============================================================================

object GeneticAlgorithmOptimizer {

    data class Individual(
        val genes: DoubleArray,
        val fitness: Double,
    )

    /** 生成随机个体
     * 通过随机数与范围乘法
     */
    fun randomIndividual(size: Int, range: Pair<Double, Double>, rand: java.util.Random): DoubleArray {
        return DoubleArray(size) { range.first + rand.nextDouble() * (range.second - range.first) }
    }

    /** 选择：轮盘赌选择
     * 根据适应度比例选择
     * 通过累加和除法
     */
    fun rouletteWheelSelection(
        population: List<Individual>,
        rand: java.util.Random,
    ): Individual {
        val totalFitness = population.sumOf { it.fitness }
        if (totalFitness <= 0) return population[rand.nextInt(population.size)]
        val threshold = rand.nextDouble() * totalFitness
        var sum = 0.0
        for (individual in population) {
            sum += individual.fitness
            if (sum >= threshold) return individual
        }
        return population.last()
    }

    /** 交叉：单点交叉
     * 交换两个个体的部分基因
     * 通过索引和复制
     */
    fun singlePointCrossover(
        parent1: DoubleArray,
        parent2: DoubleArray,
        rand: java.util.Random,
    ): Pair<DoubleArray, DoubleArray> {
        val size = minOf(parent1.size, parent2.size)
        if (size <= 1) return parent1.copyOf() to parent2.copyOf()
        val point = rand.nextInt(size - 1) + 1
        val child1 = DoubleArray(size)
        val child2 = DoubleArray(size)
        for (i in 0 until size) {
            if (i < point) {
                child1[i] = parent1[i]
                child2[i] = parent2[i]
            } else {
                child1[i] = parent2[i]
                child2[i] = parent1[i]
            }
        }
        return child1 to child2
    }

    /** 均匀交叉
     * 每个基因独立选择父本
     * 通过随机比较
     */
    fun uniformCrossover(
        parent1: DoubleArray,
        parent2: DoubleArray,
        rand: java.util.Random,
    ): Pair<DoubleArray, DoubleArray> {
        val size = minOf(parent1.size, parent2.size)
        val child1 = DoubleArray(size)
        val child2 = DoubleArray(size)
        for (i in 0 until size) {
            if (rand.nextBoolean()) {
                child1[i] = parent1[i]; child2[i] = parent2[i]
            } else {
                child1[i] = parent2[i]; child2[i] = parent1[i]
            }
        }
        return child1 to child2
    }

    /** 变异：高斯扰动
     * x' = x + N(0, σ)
     * 通过加法
     */
    fun mutate(
        genes: DoubleArray,
        mutationRate: Double,
        mutationStrength: Double,
        rand: java.util.Random,
    ): DoubleArray {
        val mutated = genes.copyOf()
        for (i in mutated.indices) {
            if (rand.nextDouble() < mutationRate) {
                mutated[i] += AdvancedSimulator.gaussianRandom(0.0, mutationStrength, rand)
            }
        }
        return mutated
    }

    /** 进化一代
     * 选择 -> 交叉 -> 变异
     */
    fun evolve(
        population: List<Individual>,
        fitnessFunction: (DoubleArray) -> Double,
        rand: java.util.Random,
        crossoverRate: Double = 0.8,
        mutationRate: Double = 0.1,
    ): List<Individual> {
        val newPopulation = ArrayList<Individual>(population.size)
        val sorted = population.sortedByDescending { it.fitness }
        newPopulation.add(sorted[0]) // 精英保留
        newPopulation.add(sorted[1])

        while (newPopulation.size < population.size) {
            val parent1 = rouletteWheelSelection(population, rand)
            val parent2 = rouletteWheelSelection(population, rand)
            val (child1Genes, child2Genes) = if (rand.nextDouble() < crossoverRate) {
                uniformCrossover(parent1.genes, parent2.genes, rand)
            } else {
                parent1.genes.copyOf() to parent2.genes.copyOf()
            }
            val mutated1 = mutate(child1Genes, mutationRate, 0.1, rand)
            val mutated2 = mutate(child2Genes, mutationRate, 0.1, rand)
            newPopulation.add(Individual(mutated1, fitnessFunction(mutated1)))
            if (newPopulation.size < population.size) {
                newPopulation.add(Individual(mutated2, fitnessFunction(mutated2)))
            }
        }
        return newPopulation
    }

    /** 运行遗传算法
     * 通过多代进化找到最优参数
     */
    fun optimize(
        populationSize: Int,
        geneSize: Int,
        generations: Int,
        fitnessFunction: (DoubleArray) -> Double,
        geneRange: Pair<Double, Double> = -1.0 to 1.0,
    ): Individual {
        val rand = java.util.Random(42L)
        var population = List(populationSize) {
            val genes = randomIndividual(geneSize, geneRange, rand)
            Individual(genes, fitnessFunction(genes))
        }
        repeat(generations) {
            population = evolve(population, fitnessFunction, rand)
        }
        return population.maxByOrNull { it.fitness } ?: population[0]
    }
}

// =============================================================================
// 第三十部分：粒子群优化（PSO）
// -----------------------------------------------------------------------------
// 通过模拟鸟群觅食寻找最优权重参数，
// 每个粒子根据自身最佳和全局最佳更新速度。
// v = w*v + c1*r1*(pbest - x) + c2*r2*(gbest - x)
// x = x + v
// =============================================================================

object ParticleSwarmOptimizer {

    data class Particle(
        val position: DoubleArray,
        val velocity: DoubleArray,
        val bestPosition: DoubleArray,
        val bestFitness: Double,
    )

    /** 初始化粒子群
     * 随机位置和速度
     * 通过随机数与范围乘法
     */
    fun initializeSwarm(
        numParticles: Int,
        dimensions: Int,
        positionRange: Pair<Double, Double>,
        velocityRange: Pair<Double, Double>,
        rand: java.util.Random,
    ): List<Particle> {
        return List(numParticles) {
            val pos = DoubleArray(dimensions) {
                positionRange.first + rand.nextDouble() * (positionRange.second - positionRange.first)
            }
            val vel = DoubleArray(dimensions) {
                velocityRange.first + rand.nextDouble() * (velocityRange.second - velocityRange.first)
            }
            Particle(pos.copyOf(), vel.copyOf(), pos.copyOf(), Double.NEGATIVE_INFINITY)
        }
    }

    /** 更新粒子速度和位置
     * 通过乘法、减法、加法混合运算
     */
    fun updateParticle(
        particle: Particle,
        globalBest: DoubleArray,
        w: Double,
        c1: Double,
        c2: Double,
        rand: java.util.Random,
    ): Particle {
        val n = particle.position.size
        val newVelocity = DoubleArray(n)
        val newPosition = DoubleArray(n)
        for (i in 0 until n) {
            val r1 = rand.nextDouble(); val r2 = rand.nextDouble()
            newVelocity[i] = w * particle.velocity[i] +
                c1 * r1 * (particle.bestPosition[i] - particle.position[i]) +
                c2 * r2 * (globalBest[i] - particle.position[i])
            newPosition[i] = particle.position[i] + newVelocity[i]
        }
        return Particle(newPosition, newVelocity, particle.bestPosition.copyOf(), particle.bestFitness)
    }

    /** PSO 优化主循环
     * 通过多代迭代找到最优解
     */
    fun optimize(
        numParticles: Int,
        dimensions: Int,
        iterations: Int,
        fitnessFunction: (DoubleArray) -> Double,
        positionRange: Pair<Double, Double> = -1.0 to 1.0,
        w: Double = 0.7,
        c1: Double = 1.5,
        c2: Double = 1.5,
    ): DoubleArray {
        val rand = java.util.Random(42L)
        var swarm = initializeSwarm(numParticles, dimensions, positionRange,
            -0.5 to 0.5, rand)
        var globalBest = swarm[0].position.copyOf()
        var globalBestFitness = Double.NEGATIVE_INFINITY

        swarm = swarm.map { p ->
            val f = fitnessFunction(p.position)
            if (f > globalBestFitness) {
                globalBestFitness = f
                globalBest = p.position.copyOf()
            }
            if (f > p.bestFitness) Particle(p.position, p.velocity, p.position.copyOf(), f)
            else p
        }

        repeat(iterations) {
            swarm = swarm.map { p ->
                val updated = updateParticle(p, globalBest, w, c1, c2, rand)
                val f = fitnessFunction(updated.position)
                if (f > updated.bestFitness) {
                    val newBest = updated.position.copyOf()
                    if (f > globalBestFitness) {
                        globalBestFitness = f
                        globalBest = newBest
                    }
                    Particle(updated.position, updated.velocity, newBest, f)
                } else updated
            }
        }
        return globalBest
    }
}

// =============================================================================
// 第三十一部分：赛事强度指数
// -----------------------------------------------------------------------------
// 量化不同赛事的竞争水平和重要性，
// 用于调整预测权重。
// =============================================================================

object TournamentStrengthIndex {

    /** 赛事权重系数
     * 数值越大表示赛事越重要
     */
    val TOURNAMENT_WEIGHTS = mapOf(
        "FIFA World Cup" to 2.50,
        "Euro" to 2.20,
        "Copa America" to 2.10,
        "AFCON" to 1.80,
        "Asian Cup" to 1.80,
        "World Cup Qualifier" to 1.50,
        "Euro Qualifier" to 1.40,
        "Nations League" to 1.30,
        "Confederations Cup" to 1.60,
        "Olympic Games" to 1.20,
        "Friendly" to 0.70,
        "Club Friendly" to 0.50,
    )

    /** 阶段权重
     * 淘汰赛阶段权重更高
     */
    val STAGE_WEIGHTS = mapOf(
        "Group Stage" to 1.0,
        "Round of 16" to 1.2,
        "Quarterfinal" to 1.4,
        "Semifinal" to 1.6,
        "Final" to 2.0,
        "Third Place" to 1.3,
        "League" to 1.0,
    )

    /** 获取赛事权重
     * 通过字符串匹配
     */
    fun getTournamentWeight(competition: String): Double {
        for ((key, value) in TOURNAMENT_WEIGHTS) {
            if (competition.contains(key)) return value
        }
        return 1.0
    }

    /** 获取阶段权重
     * 通过字符串匹配
     */
    fun getStageWeight(stage: String): Double {
        for ((key, value) in STAGE_WEIGHTS) {
            if (stage.contains(key)) return value
        }
        return 1.0
    }

    /** 综合赛事强度指数
     * TSI = tournamentWeight * stageWeight
     * 通过乘法运算
     */
    fun strengthIndex(competition: String, stage: String): Double {
        return getTournamentWeight(competition) * getStageWeight(stage)
    }

    /** 根据赛事强度调整积分变化
     * 高赛事强度对积分影响更大
     */
    fun adjustPointsForTournament(
        basePoints: Double,
        competition: String,
        stage: String,
    ): Double {
        val tsi = strengthIndex(competition, stage)
        return basePoints * tsi
    }

    /** 历史数据库平均赛事强度
     * 通过加权平均
     */
    fun averageTournamentStrength(matches: List<HistoricalMatch>): Double {
        if (matches.isEmpty()) return 1.0
        val total = matches.sumOf { strengthIndex(it.competition, it.stage) }
        return total / matches.size
    }
}

// =============================================================================
// 第三十二部分：阵容深度与球员因素估算
// -----------------------------------------------------------------------------
// 基于球队历史数据估算阵容强度、
// 伤病影响和关键球员可用性。
// =============================================================================

object SquadEstimator {

    /** 阵容深度指数
     * 基于球队积分和比赛稳定性
     * 通过乘法和加法
     */
    fun squadDepthIndex(
        team: TeamBaselineData,
        recentScoreStdDev: Double,
    ): Double {
        val baseDepth = (team.midfieldRating + team.defenseRating + team.attackRating) / 3.0
        val stabilityFactor = maxOf(0.0, 1.0 - recentScoreStdDev / 3.0)
        return (baseDepth * 0.6 + stabilityFactor * 40.0) / 100.0
    }

    /** 关键球员影响估计
     * 强队更依赖关键球员
     */
    fun keyPlayerImpact(team: TeamBaselineData): Double {
        return (team.attackRating / 100.0) * 0.15 + 0.05
    }

    /** 伤病影响分数
     * injuryCount / 11 * keyPlayerImpact
     * 通过除法和乘法
     */
    fun injuryImpact(team: TeamBaselineData, injuryCount: Int): Double {
        val ratio = injuryCount.toDouble() / 11.0
        return ratio * keyPlayerImpact(team)
    }

    /** 疲劳指数
     * 基于近期比赛密度（假设）
     * matchesPerMonth / 4.0
     */
    fun fatigueIndex(matchesPerMonth: Int): Double {
        return (matchesPerMonth.toDouble() / 4.0).coerceIn(0.5, 2.0)
    }

    /** 旅行距离影响
     * 通过大洲区域差异估算
     */
    fun travelImpact(homeContinent: String, awayContinent: String): Double {
        return if (homeContinent == awayContinent) 1.0 else 0.95
    }

    /** 阵容完整度评分
     * 100 - 伤病影响 - 疲劳影响
     * 通过减法
     */
    fun squadCompleteness(
        team: TeamBaselineData,
        injuryCount: Int,
        matchesPerMonth: Int,
    ): Double {
        val injury = injuryImpact(team, injuryCount)
        val fatigue = (fatigueIndex(matchesPerMonth) - 1.0) * 0.05
        return (1.0 - injury - fatigue).coerceIn(0.5, 1.0)
    }

    /** 估算双方阵容完整度差异对胜率的影响
     * delta = completenessHome - completenessAway
     * 通过减法
     */
    fun squadAdvantage(
        home: TeamBaselineData,
        away: TeamBaselineData,
        homeInjuries: Int = 0,
        awayInjuries: Int = 0,
        homeMatchesPerMonth: Int = 3,
        awayMatchesPerMonth: Int = 3,
    ): Double {
        val cHome = squadCompleteness(home, homeInjuries, homeMatchesPerMonth)
        val cAway = squadCompleteness(away, awayInjuries, awayMatchesPerMonth)
        return (cHome - cAway) * 0.1
    }
}

// =============================================================================
// 第三十三部分：相似比赛检索
// -----------------------------------------------------------------------------
// 找到历史上与当前对阵最相似的比赛，
// 通过特征距离计算相似度。
// =============================================================================

object MatchSimilaritySearch {

    data class SimilarMatch(
        val match: HistoricalMatch,
        val similarity: Double,
    )

    /** 构建历史比赛特征向量
     * 用于相似度计算
     */
    private fun matchFeatureVector(match: HistoricalMatch): DoubleArray {
        return doubleArrayOf(
            match.homePossession / 100.0,
            match.awayPossession / 100.0,
            match.homeShots / 30.0,
            match.awayShots / 30.0,
            match.homeShotsOnTarget / 15.0,
            match.awayShotsOnTarget / 15.0,
            match.homeCorners / 15.0,
            match.awayCorners / 15.0,
            (match.homeFouls + match.awayFouls) / 50.0,
            (match.homeYellowCards + match.awayYellowCards) / 10.0,
            match.homePassAccuracy / 100.0,
            match.awayPassAccuracy / 100.0,
        )
    }

    /** 计算当前比赛预测特征与历史比赛的相似度
     * 使用欧氏距离的反比
     * 通过减法、平方、开方、除法
     */
    fun similarity(
        predictedHomeXg: Double,
        predictedAwayXg: Double,
        predictedHomePossession: Double,
        predictedAwayPossession: Double,
        match: HistoricalMatch,
    ): Double {
        val predictedTotal = predictedHomeXg + predictedAwayXg
        val historicalTotal = (match.homeScore + match.awayScore).toDouble()
        val diffXg = (predictedHomeXg - predictedAwayXg) - (match.homeScore - match.awayScore).toDouble()
        val diffTotal = predictedTotal - historicalTotal
        val diffPossession = (predictedHomePossession - predictedAwayPossession) - (match.homePossession - match.awayPossession)
        val distance = sqrt(diffXg * diffXg + diffTotal * diffTotal + diffPossession * diffPossession)
        return 1.0 / (1.0 + distance)
    }

    /** 查找最相似的 N 场比赛
     * 通过排序和取前 N
     */
    fun findSimilarMatches(
        predictedHomeXg: Double,
        predictedAwayXg: Double,
        predictedHomePossession: Double,
        predictedAwayPossession: Double,
        topN: Int = 5,
    ): List<SimilarMatch> {
        return HistoricalMatchDatabase.CLASSIC_MATCHES.map { m ->
            val sim = similarity(predictedHomeXg, predictedAwayXg,
                predictedHomePossession, predictedAwayPossession, m)
            SimilarMatch(m, sim)
        }.sortedByDescending { it.similarity }.take(topN)
    }

    /** 基于相似比赛修正预测
     * 用相似历史结果加权修正
     */
    fun adjustPredictionBySimilarMatches(
        predictedHomeWinProb: Double,
        predictedDrawProb: Double,
        predictedAwayWinProb: Double,
        similarMatches: List<SimilarMatch>,
    ): Triple<Double, Double, Double> {
        if (similarMatches.isEmpty()) {
            return Triple(predictedHomeWinProb, predictedDrawProb, predictedAwayWinProb)
        }
        var weightSum = 0.0
        var homeWins = 0.0; var draws = 0.0; var awayWins = 0.0
        for (sm in similarMatches) {
            val w = sm.similarity
            weightSum += w
            when {
                sm.match.homeScore > sm.match.awayScore -> homeWins += w
                sm.match.homeScore < sm.match.awayScore -> awayWins += w
                else -> draws += w
            }
        }
        if (weightSum <= 0) return Triple(predictedHomeWinProb, predictedDrawProb, predictedAwayWinProb)
        val historicalHome = homeWins / weightSum
        val historicalDraw = draws / weightSum
        val historicalAway = awayWins / weightSum
        val blend = 0.75
        val rHome = predictedHomeWinProb * blend + historicalHome * (1 - blend)
        val rDraw = predictedDrawProb * blend + historicalDraw * (1 - blend)
        val rAway = predictedAwayWinProb * blend + historicalAway * (1 - blend)
        val sum = rHome + rDraw + rAway
        return Triple(rHome / sum, rDraw / sum, rAway / sum)
    }
}

// =============================================================================
// 第三十四部分：模型验证框架
// -----------------------------------------------------------------------------
// 提供交叉验证、回测和性能评估，
// 所有指标通过基础运算计算。
// =============================================================================

object ModelValidation {

    data class ConfusionMatrix(
        val trueHomeWins: Int,
        val trueDraws: Int,
        val trueAwayWins: Int,
        val predictedHomeWins: Int,
        val predictedDraws: Int,
        val predictedAwayWins: Int,
        val correct: Int,
    ) {
        val accuracy: Double get() = if (total > 0) correct.toDouble() / total else 0.0
        val total: Int get() = trueHomeWins + trueDraws + trueAwayWins
    }

    /** 准确率
     * accuracy = correct / total
     * 通过除法
     */
    fun accuracy(predicted: List<Int>, actual: List<Int>): Double {
        if (predicted.size != actual.size || predicted.isEmpty()) return 0.0
        val correct = predicted.indices.count { predicted[it] == actual[it] }
        return correct.toDouble() / predicted.size
    }

    /** 精确率
     * precision = TP / (TP + FP)
     */
    fun precision(truePositives: Int, falsePositives: Int): Double {
        val denom = truePositives + falsePositives
        return if (denom > 0) truePositives.toDouble() / denom else 0.0
    }

    /** 召回率
     * recall = TP / (TP + FN)
     */
    fun recall(truePositives: Int, falseNegatives: Int): Double {
        val denom = truePositives + falseNegatives
        return if (denom > 0) truePositives.toDouble() / denom else 0.0
    }

    /** F1 分数
     * F1 = 2 * precision * recall / (precision + recall)
     */
    fun f1Score(precision: Double, recall: Double): Double {
        val sum = precision + recall
        return if (sum > 0) 2.0 * precision * recall / sum else 0.0
    }

    /** ROC AUC 近似计算（Mann-Whitney U）
     * AUC = (正例排序和 - n_pos*(n_pos+1)/2) / (n_pos * n_neg)
     */
    fun approximateAuc(scores: List<Double>, labels: List<Int>): Double {
        if (scores.size != labels.size || scores.isEmpty()) return 0.5
        val indexed = scores.zip(labels).sortedByDescending { it.first }
        var rankSum = 0.0; var posCount = 0; var negCount = 0
        for ((rank, pair) in indexed.withIndex()) {
            if (pair.second == 1) {
                rankSum += rank + 1
                posCount++
            } else {
                negCount++
            }
        }
        if (posCount == 0 || negCount == 0) return 0.5
        val auc = (rankSum - posCount * (posCount + 1) / 2.0) / (posCount * negCount)
        return auc.coerceIn(0.0, 1.0)
    }

    /** K 折交叉验证
     * 将数据分成 K 份轮流验证
     */
    fun kFoldSplit(data: List<Pair<DoubleArray, Int>>, k: Int): List<Pair<List<Pair<DoubleArray, Int>>, List<Pair<DoubleArray, Int>>>> {
        if (k <= 1 || data.isEmpty()) return listOf(data to emptyList())
        val foldSize = data.size / k
        val result = ArrayList<Pair<List<Pair<DoubleArray, Int>>, List<Pair<DoubleArray, Int>>>>(k)
        for (i in 0 until k) {
            val start = i * foldSize
            val end = if (i == k - 1) data.size else (i + 1) * foldSize
            val test = data.subList(start, end)
            val train = data.subList(0, start) + data.subList(end, data.size)
            result.add(train to test)
        }
        return result
    }

    /** 简单回测：基于历史比赛的胜率预测准确率
     */
    fun backtest(
        matches: List<HistoricalMatch>,
        predictFunction: (HistoricalMatch) -> Triple<Double, Double, Double>,
    ): ConfusionMatrix {
        var correct = 0
        var trueHomeWins = 0; var trueDraws = 0; var trueAwayWins = 0
        var predictedHomeWins = 0; var predictedDraws = 0; var predictedAwayWins = 0
        for (m in matches) {
            val actual = when {
                m.homeScore > m.awayScore -> 1
                m.homeScore < m.awayScore -> -1
                else -> 0
            }
            val (pH, pD, pA) = predictFunction(m)
            val predicted = when {
                pH >= pD && pH >= pA -> 1
                pA >= pH && pA >= pD -> -1
                else -> 0
            }
            when (actual) {
                1 -> trueHomeWins++
                0 -> trueDraws++
                -1 -> trueAwayWins++
            }
            when (predicted) {
                1 -> predictedHomeWins++
                0 -> predictedDraws++
                -1 -> predictedAwayWins++
            }
            if (actual == predicted) correct++
        }
        return ConfusionMatrix(trueHomeWins, trueDraws, trueAwayWins,
            predictedHomeWins, predictedDraws, predictedAwayWins, correct)
    }

    /** 赔率价值评估：比较预测概率与隐含概率
     * value = predicted_prob * odds - 1
     * 通过乘法减法
     */
    fun bettingValue(predictedProbability: Double, odds: Double): Double {
        return predictedProbability * odds - 1.0
    }

    /** 凯利公式：最优下注比例
     * f = (p*b - q) / b
     * p 为胜率，b 为净赔率，q = 1 - p
     * 通过乘法、减法、除法
     */
    fun kellyCriterion(probability: Double, odds: Double): Double {
        val b = odds - 1.0
        if (b <= 0) return 0.0
        val q = 1.0 - probability
        return (probability * b - q) / b
    }
}

// =============================================================================
// 第三十五部分：天气与场地调整
// -----------------------------------------------------------------------------
// 根据不同天气、海拔、场地类型调整预测，
// 通过加减乘除运算实现。
// =============================================================================

object WeatherVenueAdjustment {

    /** 海拔影响
     * 高海拔增加主队优势（若主队习惯高海拔）
     * 通过乘法
     */
    fun altitudeEffect(altitudeMeters: Double, isHomeAdapted: Boolean): Double {
        val base = 1.0 + altitudeMeters / 4000.0 * 0.1
        return if (isHomeAdapted) base else 1.0 / base
    }

    /** 场地类型影响
     * 人工草皮、天然草皮影响控球率
     * 通过乘法
     */
    fun pitchEffect(pitchType: String, isPossessionTeam: Boolean): Double {
        return when (pitchType.lowercase()) {
            "artificial" -> if (isPossessionTeam) 0.96 else 1.02
            "hybrid" -> 1.0
            "dirt" -> 0.92
            else -> 1.0
        }
    }

    /** 温度影响
     * 极热或极冷降低总进球
     * 通过减法和乘法
     */
    fun temperatureEffect(temperatureCelsius: Double): Double {
        val optimal = 18.0
        val deviation = abs(temperatureCelsius - optimal)
        return maxOf(0.85, 1.0 - deviation / 60.0)
    }

    /** 湿度影响
     * 高湿度降低比赛节奏
     */
    fun humidityEffect(humidityPercent: Double): Double {
        return maxOf(0.9, 1.0 - (humidityPercent - 50.0) / 200.0)
    }

    /** 风速影响
     * 强风影响长传和长射
     */
    fun windEffect(windSpeedKmh: Double): Double {
        return maxOf(0.88, 1.0 - windSpeedKmh / 100.0)
    }

    /** 综合环境调整系数
     * 各因素相乘
     */
    fun environmentMultiplier(
        weather: WeatherFactor,
        altitudeMeters: Double = 0.0,
        isHomeAdapted: Boolean = false,
    ): Double {
        val alt = altitudeEffect(altitudeMeters, isHomeAdapted)
        val temp = temperatureEffect(weather.temperature)
        val hum = humidityEffect(weather.humidity)
        val wind = windEffect(weather.windSpeed)
        return alt * temp * hum * wind * weather.impactOnGoals
    }

    /** 调整期望进球
     * xg' = xg * multiplier
     */
    fun adjustExpectedGoals(
        xgHome: Double,
        xgAway: Double,
        weather: WeatherFactor,
        altitudeMeters: Double = 0.0,
        isHomeAdapted: Boolean = false,
    ): Pair<Double, Double> {
        val multiplier = environmentMultiplier(weather, altitudeMeters, isHomeAdapted)
        return xgHome * multiplier to xgAway * multiplier
    }
}

// =============================================================================
// 第三十六部分：实时更新系统
// -----------------------------------------------------------------------------
// 根据比赛实时事件动态调整预测概率，
// 通过贝叶斯更新和剩余时间折算。
// =============================================================================

object RealTimeUpdateSystem {

    data class MatchEvent(
        val minute: Int,
        val type: MatchEventType,
        val team: String, // H or A
    )

    enum class MatchEventType {
        GOAL, RED_CARD, YELLOW_CARD, SUBSTITUTION, PENALTY_MISSED, OWN_GOAL
    }

    /** 进球事件对期望进球的调整
     * 领先后期望进球结构改变
     * 通过乘法调整
     */
    fun adjustAfterGoal(
        xgHome: Double,
        xgAway: Double,
        scoringTeam: String,
        minute: Int,
    ): Pair<Double, Double> {
        val remainingFactor = (90 - minute) / 90.0
        return if (scoringTeam == "H") {
            val newXgH = xgHome * (0.6 + 0.4 * remainingFactor)
            val newXgA = xgAway * (1.15 + 0.1 * remainingFactor)
            newXgH to newXgA
        } else {
            val newXgH = xgHome * (1.15 + 0.1 * remainingFactor)
            val newXgA = xgAway * (0.6 + 0.4 * remainingFactor)
            newXgH to newXgA
        }
    }

    /** 红牌事件影响
     * 红牌方期望进球下降
     * 通过乘法
     */
    fun adjustAfterRedCard(
        xgHome: Double,
        xgAway: Double,
        sentOffTeam: String,
        minute: Int,
    ): Pair<Double, Double> {
        val remainingFactor = (90 - minute) / 90.0
        val reduction = 0.25 * remainingFactor
        return if (sentOffTeam == "H") {
            xgHome * (1.0 - reduction) to xgAway * (1.0 + reduction * 0.5)
        } else {
            xgHome * (1.0 + reduction * 0.5) to xgAway * (1.0 - reduction)
        }
    }

    /** 点球未进影响
     * 未进点球方士气受挫
     */
    fun adjustAfterPenaltyMissed(
        xgHome: Double,
        xgAway: Double,
        missingTeam: String,
    ): Pair<Double, Double> {
        return if (missingTeam == "H") {
            xgHome * 0.92 to xgAway * 1.05
        } else {
            xgHome * 1.05 to xgAway * 0.92
        }
    }

    /** 应用事件序列更新期望进球
     * 顺序应用每个事件
     */
    fun applyEvents(
        initialXgHome: Double,
        initialXgAway: Double,
        events: List<MatchEvent>,
    ): Pair<Double, Double> {
        var xgH = initialXgHome
        var xgA = initialXgAway
        for (event in events) {
            val (nh, na) = when (event.type) {
                MatchEventType.GOAL -> adjustAfterGoal(xgH, xgA, event.team, event.minute)
                MatchEventType.RED_CARD -> adjustAfterRedCard(xgH, xgA, event.team, event.minute)
                MatchEventType.PENALTY_MISSED -> adjustAfterPenaltyMissed(xgH, xgA, event.team)
                else -> xgH to xgA
            }
            xgH = nh.coerceAtLeast(0.05)
            xgA = na.coerceAtLeast(0.05)
        }
        return xgH to xgA
    }

    /** 实时胜率更新
     * 基于当前比分和剩余期望进球
     */
    fun updateLiveProbabilities(
        pHome: Double,
        pDraw: Double,
        pAway: Double,
        currentHomeScore: Int,
        currentAwayScore: Int,
        remainingXgHome: Double,
        remainingXgAway: Double,
        minute: Int,
    ): Triple<Double, Double, Double> {
        val matrix = CoreEngine.scoreMatrix(remainingXgHome, remainingXgAway,
            baseH = currentHomeScore, baseA = currentAwayScore)
        val timeWeight = maxOf(0.0, (minute - 60) / 30.0) // 60分钟后更依赖比分
        val blendedH = matrix.pHome * (1 - 0.2 * timeWeight) + pHome * 0.2 * timeWeight
        val blendedD = matrix.pDraw * (1 - 0.2 * timeWeight) + pDraw * 0.2 * timeWeight
        val blendedA = matrix.pAway * (1 - 0.2 * timeWeight) + pAway * 0.2 * timeWeight
        val sum = blendedH + blendedD + blendedA
        return Triple(blendedH / sum, blendedD / sum, blendedA / sum)
    }
}

// =============================================================================
// 第三十七部分：扩展常量和算法参考
// -----------------------------------------------------------------------------
// 收录预测系统中使用的扩展参数、
// 默认配置和算法说明文档。
// =============================================================================

object ExtendedPredictionConstants {

    // ---- 时间衰减参数 ----
    const val FORM_HALF_LIFE_DAYS = 180.0
    const val RANKING_HALF_LIFE_DAYS = 365.0
    const val ELO_HALF_LIFE_DAYS = 270.0

    // ---- 神经网络默认结构 ----
    const val MLP_INPUT_DIM = 10
    const val MLP_HIDDEN_DIM = 5
    const val MLP_OUTPUT_DIM = 3

    // ---- 聚类参数 ----
    const val DEFAULT_K_MEANS_CLUSTERS = 5
    const val K_MEANS_MAX_ITERATIONS = 100
    const val K_MEANS_TOLERANCE = 1e-4

    // ---- 遗传算法参数 ----
    const val GA_POPULATION_SIZE = 50
    const val GA_GENERATIONS = 100
    const val GA_CROSSOVER_RATE = 0.8
    const val GA_MUTATION_RATE = 0.1

    // ---- 粒子群参数 ----
    const val PSO_PARTICLES = 30
    const val PSO_ITERATIONS = 100
    const val PSO_W = 0.7
    const val PSO_C1 = 1.5
    const val PSO_C2 = 1.5

    // ---- 异常检测阈值 ----
    const val Z_SCORE_ANOMALY_THRESHOLD = 2.5
    const val PROBABILITY_ANOMALY_THRESHOLD = 0.15
    const val UPSET_RANK_GAP_THRESHOLD = 10

    // ---- 模型验证参数 ----
    const val DEFAULT_K_FOLDS = 5
    const val BACKTEST_MIN_SAMPLES = 30

    // ---- 相似比赛参数 ----
    const val DEFAULT_SIMILAR_MATCHES = 5
    const val SIMILARITY_BLEND_WEIGHT = 0.75

    // ---- 实时更新参数 ----
    const val GOAL_LEAD_TIME_FACTOR = 0.6
    const val RED_CARD_REDUCTION = 0.25
    const val PENALTY_MISS_MORALE_DROP = 0.08

    // ---- 环境参数默认值 ----
    const val DEFAULT_TEMPERATURE = 18.0
    const val DEFAULT_HUMIDITY = 55.0
    const val DEFAULT_WIND_SPEED = 12.0
    const val DEFAULT_ALTITUDE = 0.0

    /** 获取所有可调参数说明 */
    fun parameterDocumentation(): List<String> = listOf(
        "FORM_HALF_LIFE_DAYS: 近期状态半衰期，默认 180 天",
        "WEIGHT_ELO: Elo 证据权重，默认 0.28",
        "WEIGHT_MATRIX: 泊松矩阵证据权重，默认 0.34",
        "WEIGHT_FORM: 近期状态证据权重，默认 0.14",
        "WEIGHT_H2H_MAX: 历史交战最大权重，默认 0.12",
        "WEIGHT_HOME_AWAY: 主客场证据权重，默认 0.12",
        "BASE_GOALS: 比赛基准总进球率，默认 1.40 球/队",
        "HOME_ADVANTAGE: 主场优势系数，默认 1.18",
        "MONTE_CARLO_SIMULATIONS: 蒙特卡洛模拟次数，默认 10000",
        "ELO_K_FACTOR: Elo 评分 K 因子，默认 25",
        "PSO_ITERATIONS: 粒子群优化迭代次数，默认 100",
        "GA_GENERATIONS: 遗传算法进化代数，默认 100",
        "DEFAULT_K_FOLDS: 交叉验证折数，默认 5",
        "Z_SCORE_ANOMALY_THRESHOLD: Z-score 异常阈值，默认 2.5",
    )
}

// =============================================================================
// 第三十八部分：便捷工厂函数
// -----------------------------------------------------------------------------
// 提供简洁的 API 入口，便于 UI 层调用。
// =============================================================================

object PredictionFactories {

    /** 基于球队编码快速预测（使用数据库基准）
     * 便于 UI 层直接调用
     */
    fun predictByCode(
        homeCode: String,
        awayCode: String,
        trueHome: Boolean = true,
    ): PredictionOutput? {
        val homeBaseline = TeamDatabase.findByCode(homeCode) ?: return null
        val awayBaseline = TeamDatabase.findByCode(awayCode) ?: return null
        val home = baselineToTeam(homeBaseline)
        val away = baselineToTeam(awayBaseline)
        return PredictionSystem.predictPreMatch(home, away, trueHome)
    }

    /** 将数据库基准数据转换为 Team 对象 */
    private fun baselineToTeam(baseline: TeamBaselineData): Team {
        return Team(
            idTeam = baseline.code,
            rank = baseline.rank,
            name = baseline.name,
            code = baseline.code,
            confederation = baseline.confederation,
            points = baseline.points,
            prevRank = baseline.rank,
            rankChange = 0,
        )
    }

    /** 快速获取两队的相似历史比赛 */
    fun similarMatches(
        homeCode: String,
        awayCode: String,
        topN: Int = 5,
    ): List<MatchSimilaritySearch.SimilarMatch> {
        val homeBaseline = TeamDatabase.findByCode(homeCode)
        val awayBaseline = TeamDatabase.findByCode(awayCode)
        if (homeBaseline == null || awayBaseline == null) return emptyList()
        val features = FeatureEngineering.buildMatchFeatures(homeBaseline, awayBaseline, true)
        val xgDiff = features[2] * 1.5
        val possessionDiff = features[7]
        return MatchSimilaritySearch.findSimilarMatches(
            predictedHomeXg = maxOf(0.5, 1.4 + xgDiff),
            predictedAwayXg = maxOf(0.5, 1.4 - xgDiff),
            predictedHomePossession = 50.0 + possessionDiff * 50.0,
            predictedAwayPossession = 50.0 - possessionDiff * 50.0,
            topN = topN,
        )
    }

    /** 快速聚类所有球队 */
    fun clusterAllTeams(k: Int = 5): Map<String, Int> {
        return ClusteringAnalysis.clusterTeams(TeamDatabase.ALL_TEAMS, k)
    }

    /** 快速异常检测
     * 输入预测概率，输出是否可能是冷门
     */
    fun isPotentialUpset(
        homeCode: String,
        awayCode: String,
    ): Boolean {
        val homeBaseline = TeamDatabase.findByCode(homeCode)
        val awayBaseline = TeamDatabase.findByCode(awayCode)
        if (homeBaseline == null || awayBaseline == null) return false
        val output = predictByCode(homeCode, awayCode) ?: return false
        val favoredIsHome = homeBaseline.rank <= awayBaseline.rank
        val upsetProb = if (favoredIsHome) output.pAway else output.pHome
        return upsetProb > 0.35
    }

    /** 预测分享文本生成 */
    fun generateShareText(output: PredictionOutput, homeName: String, awayName: String): String {
        return buildString {
            append("FifaGlass AI 预测\n")
            append("$homeName vs $awayName\n")
            append("主胜 ${(output.pHome * 100).toInt()}% | 平 ${(output.pDraw * 100).toInt()}% | 客胜 ${(output.pAway * 100).toInt()}%\n")
            append("最可能比分：${output.likelyScore} (${(output.likelyScoreProbability * 100).toInt()}%)\n")
            append("期望进球：${"%.2f".format(output.xgHome)} - ${"%.2f".format(output.xgAway)}\n")
            append("信心指数：${output.confidence}/100 | 风险：${output.riskLevel}\n")
            append("大2.5：${(output.overProbabilities.over25 * 100).toInt()}% | BTTS：${(output.bttsProbability * 100).toInt()}%\n")
            append(output.recommendation)
        }
    }
}

// =============================================================================
// 第四十部分：扩展概率分布模型
// ------------------------------------------------------------------------------
// 在泊松、负二项之外，引入更多分布刻画进球与比分。
// 所有概率质量函数 / 密度函数仅使用加减乘除、指数、对数实现。
// =============================================================================

object ExtendedDistributions {

    /** 几何分布：P(X=k) = (1-p)^k * p
     * 描述直到第一次成功所需的失败次数。
     */
    fun geometricProbability(p: Double, k: Int): Double {
        if (p <= 0.0 || p > 1.0 || k < 0) return 0.0
        return (1.0 - p).pow(k) * p
    }

    /** 二项分布：P(X=k) = C(n,k) * p^k * (1-p)^(n-k) */
    fun binomialProbability(n: Int, k: Int, p: Double): Double {
        if (k < 0 || k > n || p < 0.0 || p > 1.0) return 0.0
        val c = PredictMath.combination(n, k)
        return c * p.pow(k) * (1.0 - p).pow(n - k)
    }

    /** 超几何分布：不放回抽样概率 */
    fun hypergeometricProbability(
        populationSize: Int,
        successStates: Int,
        draws: Int,
        observedSuccess: Int,
    ): Double {
        if (observedSuccess < 0 || observedSuccess > draws) return 0.0
        val c1 = PredictMath.combination(successStates, observedSuccess)
        val c2 = PredictMath.combination(populationSize - successStates, draws - observedSuccess)
        val total = PredictMath.combination(populationSize, draws)
        return if (total > 0) (c1 * c2) / total else 0.0
    }

    /** 对数正态分布密度：用于建模偏态进球数据 */
    fun logNormalDensity(x: Double, mu: Double, sigma: Double): Double {
        if (x <= 0.0 || sigma <= 0.0) return 0.0
        val lnX = ln(x)
        val diff = lnX - mu
        return (1.0 / (x * sigma * sqrt(2.0 * PI))) * exp(-(diff * diff) / (2.0 * sigma * sigma))
    }

    /** 威布尔分布密度 */
    fun weibullDensity(x: Double, shape: Double, scale: Double): Double {
        if (x < 0.0 || shape <= 0.0 || scale <= 0.0) return 0.0
        if (x == 0.0 && shape < 1.0) return 0.0
        val tOverScale = x / scale
        return (shape / scale) * tOverScale.pow(shape - 1.0) * exp(-tOverScale.pow(shape))
    }

    /** 伽马分布密度 */
    fun gammaDensity(x: Double, alpha: Double, beta: Double): Double {
        if (x <= 0.0 || alpha <= 0.0 || beta <= 0.0) return 0.0
        val numerator = beta.pow(alpha) * x.pow(alpha - 1.0) * exp(-beta * x)
        val denominator = PredictMath.gamma(alpha)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 贝塔分布密度 */
    fun betaDensity(x: Double, alpha: Double, betaParam: Double): Double {
        if (x < 0.0 || x > 1.0 || alpha <= 0.0 || betaParam <= 0.0) return 0.0
        val numerator = x.pow(alpha - 1.0) * (1.0 - x).pow(betaParam - 1.0)
        val denominator = PredictMath.betaFunction(alpha, betaParam)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 学生 t 分布密度（简化，自由度 v） */
    fun studentTDensity(x: Double, nu: Double): Double {
        if (nu <= 0.0) return 0.0
        val coef = PredictMath.gamma((nu + 1.0) / 2.0) / (sqrt(nu * PI) * PredictMath.gamma(nu / 2.0))
        return coef * (1.0 + (x * x) / nu).pow(-(nu + 1.0) / 2.0)
    }

    /** 累积正态分布近似（Abramowitz & Stegun） */
    fun normalCdf(x: Double): Double {
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429
        val p = 0.2316419
        val t = 1.0 / (1.0 + p * abs(x))
        val phi = (1.0 / sqrt(2.0 * PI)) * exp(-0.5 * x * x)
        val poly = t * (b1 + t * (b2 + t * (b3 + t * (b4 + t * b5))))
        val result = 1.0 - phi * poly
        return if (x >= 0.0) result else 1.0 - result
    }

    /** 逆累积正态分布近似 */
    fun inverseNormalCdf(p: Double): Double {
        if (p <= 0.0) return -4.0
        if (p >= 1.0) return 4.0
        val a0 = 2.50662823884
        val a1 = -18.61500062529
        val a2 = 41.39119773534
        val a3 = -25.44106049637
        val b1 = -8.47351093090
        val b2 = 23.08336743743
        val b3 = -21.06224101826
        val b4 = 3.13082909833
        val c0 = 0.3374754822726147
        val c1 = 0.9761690190917186
        val c2 = 0.1607979714918209
        val c3 = 0.0276438810333863
        val c4 = 0.0038405729373609
        val c5 = 0.0003951896511919
        val c6 = 0.0000321767881768
        val c7 = 0.0000002888167364
        val c8 = 0.0000003960315187
        val q = p - 0.5
        return if (abs(q) <= 0.42) {
            val r = q * q
            q * (((a3 * r + a2) * r + a1) * r + a0) / ((((b4 * r + b3) * r + b2) * r + b1) * r + 1.0)
        } else {
            val r = if (q < 0) p else 1.0 - p
            val s = ln(-ln(r))
            val t = c0 + s * (c1 + s * (c2 + s * (c3 + s * (c4 + s * (c5 + s * (c6 + s * (c7 + s * c8)))))))
            if (q < 0) -t else t
        }
    }

    /** 离散均匀分布 */
    fun discreteUniformProbability(n: Int): Double {
        return if (n > 0) 1.0 / n else 0.0
    }

    /** 零膨胀泊松（ZIP）概率 */
    fun zeroInflatedPoissonProbability(
        lambda: Double,
        pi: Double,
        k: Int,
    ): Double {
        if (lambda <= 0.0 || pi < 0.0 || pi > 1.0) return 0.0
        return if (k == 0) {
            pi + (1.0 - pi) * exp(-lambda)
        } else {
            (1.0 - pi) * exp(-lambda) * lambda.pow(k) / PredictMath.factorial(k)
        }
    }

    /** 零膨胀负二项概率 */
    fun zeroInflatedNegativeBinomialProbability(
        r: Double,
        p: Double,
        pi: Double,
        k: Int,
    ): Double {
        if (r <= 0.0 || p <= 0.0 || p > 1.0 || pi < 0.0 || pi > 1.0) return 0.0
        return if (k == 0) {
            pi + (1.0 - pi) * p.pow(r)
        } else {
            (1.0 - pi) * PredictMath.combination((k + r - 1).toInt(), k) * (1.0 - p).pow(k) * p.pow(r)
        }
    }

    /** 互补重对数分布 */
    fun logLogisticDensity(x: Double, alpha: Double, betaParam: Double): Double {
        if (x <= 0.0 || alpha <= 0.0 || betaParam <= 0.0) return 0.0
        val z = x / alpha
        val zBeta = z.pow(betaParam)
        return (betaParam / alpha) * zBeta / (x * (1.0 + zBeta).pow(2.0))
    }

    /** 经验分布函数（ECDF）在某点的值 */
    fun empiricalCdf(samples: List<Double>, x: Double): Double {
        if (samples.isEmpty()) return 0.0
        val count = samples.count { it <= x }.toDouble()
        return count / samples.size
    }

    /** 核密度估计（KDE）在某点的值，高斯核 */
    fun kernelDensityEstimate(samples: List<Double>, x: Double, bandwidth: Double): Double {
        if (samples.isEmpty() || bandwidth <= 0.0) return 0.0
        var sum = 0.0
        for (xi in samples) {
            val z = (x - xi) / bandwidth
            sum += (1.0 / sqrt(2.0 * PI)) * exp(-0.5 * z * z)
        }
        return sum / (samples.size * bandwidth)
    }

    /** 分位数函数：从有序样本中插值得到第 q 分位数 */
    fun quantile(samples: List<Double>, q: Double): Double {
        if (samples.isEmpty() || q < 0.0 || q > 1.0) return 0.0
        val sorted = samples.sorted()
        if (q == 0.0) return sorted.first()
        if (q == 1.0) return sorted.last()
        val index = q * (sorted.size - 1)
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val frac = index - lower
        return sorted[lower] * (1.0 - frac) + sorted[upper] * frac
    }

    /** 置信区间（基于正态近似） */
    fun confidenceInterval(
        mean: Double,
        stdError: Double,
        confidence: Double,
    ): Pair<Double, Double> {
        val z = when {
            confidence >= 0.99 -> 2.576
            confidence >= 0.95 -> 1.96
            confidence >= 0.90 -> 1.645
            else -> 1.0
        }
        val margin = z * stdError
        return (mean - margin) to (mean + margin)
    }

    /** 假设检验：单样本 Z 检验统计量 */
    fun oneSampleZStatistic(sampleMean: Double, populationMean: Double, stdError: Double): Double {
        if (stdError <= 0.0) return 0.0
        return (sampleMean - populationMean) / stdError
    }

    /** 两样本 T 检验统计量 */
    fun twoSampleTStatistic(
        mean1: Double,
        mean2: Double,
        var1: Double,
        var2: Double,
        n1: Int,
        n2: Int,
    ): Double {
        if (n1 <= 0 || n2 <= 0) return 0.0
        val pooledVar = ((n1 - 1) * var1 + (n2 - 1) * var2) / (n1 + n2 - 2)
        val se = sqrt(pooledVar * (1.0 / n1 + 1.0 / n2))
        return if (se > 0) (mean1 - mean2) / se else 0.0
    }

    /** 卡方检验统计量（观测 vs 期望） */
    fun chiSquareStatistic(observed: List<Double>, expected: List<Double>): Double {
        if (observed.size != expected.size || observed.isEmpty()) return 0.0
        var sum = 0.0
        for (i in observed.indices) {
            val e = expected[i]
            if (e > 0) sum += (observed[i] - e).pow(2) / e
        }
        return sum
    }

    /** Kolmogorov-Smirnov 统计量 */
    fun ksStatistic(samples1: List<Double>, samples2: List<Double>): Double {
        if (samples1.isEmpty() || samples2.isEmpty()) return 0.0
        val all = (samples1 + samples2).sorted().distinct()
        var maxDiff = 0.0
        for (x in all) {
            val f1 = empiricalCdf(samples1, x)
            val f2 = empiricalCdf(samples2, x)
            val diff = abs(f1 - f2)
            if (diff > maxDiff) maxDiff = diff
        }
        return maxDiff
    }

    /** Mann-Whitney U 统计量（简化） */
    fun mannWhitneyU(samples1: List<Double>, samples2: List<Double>): Double {
        val ranked = (samples1.map { it to 1 } + samples2.map { it to 2 })
            .sortedBy { it.first }
            .mapIndexed { index, pair -> index + 1 to pair.second }
        val rankSum1 = ranked.filter { it.second == 1 }.sumOf { it.first.toDouble() }
        val n1 = samples1.size.toDouble()
        val n2 = samples2.size.toDouble()
        val u1 = rankSum1 - n1 * (n1 + 1.0) / 2.0
        return minOf(u1, n1 * n2 - u1)
    }

    /** Anderson-Darling 统计量（简化，对正态） */
    fun andersonDarlingStatistic(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val sorted = samples.sorted()
        val mean = sorted.average()
        val std = PredictMath.standardDeviation(sorted)
        if (std <= 0) return 0.0
        val n = sorted.size
        var sum = 0.0
        for (i in sorted.indices) {
            val z = (sorted[i] - mean) / std
            val f = normalCdf(z)
            val term1 = (2.0 * (i + 1) - 1.0) * ln(maxOf(f, 1e-12))
            val term2 = (2.0 * (n - i) - 1.0) * ln(maxOf(1.0 - f, 1e-12))
            sum += term1 + term2
        }
        return -n - sum / n
    }

    /** Jarque-Bera 正态性检验统计量 */
    fun jarqueBeraStatistic(samples: List<Double>): Double {
        val n = samples.size.toDouble()
        if (n < 3) return 0.0
        val mean = samples.average()
        val m2 = samples.sumOf { (it - mean).pow(2) } / n
        val m3 = samples.sumOf { (it - mean).pow(3) } / n
        val m4 = samples.sumOf { (it - mean).pow(4) } / n
        val skew = m3 / m2.pow(1.5)
        val kurt = m4 / m2.pow(2)
        return (n / 6.0) * (skew * skew + (kurt - 3.0).pow(2) / 4.0)
    }

    /** Cramér-von Mises 统计量 */
    fun cramerVonMisesStatistic(samples: List<Double>): Double {
        val n = samples.size
        if (n == 0) return 0.0
        val sorted = samples.sorted()
        val mean = sorted.average()
        val std = PredictMath.standardDeviation(sorted)
        if (std <= 0) return 0.0
        var sum = 0.0
        for (i in sorted.indices) {
            val z = (sorted[i] - mean) / std
            val f = normalCdf(z)
            val expected = (2.0 * (i + 1) - 1.0) / (2.0 * n)
            sum += (f - expected).pow(2)
        }
        return sum + 1.0 / (12.0 * n)
    }

    /** 熵（信息论） */
    fun shannonEntropy(probabilities: List<Double>): Double {
        var sum = 0.0
        for (p in probabilities) {
            if (p > 0.0) sum += p * ln(p)
        }
        return -sum / ln(2.0)
    }

    /** 交叉熵 */
    fun crossEntropy(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size) return 0.0
        var sum = 0.0
        for (i in p.indices) {
            if (p[i] > 0.0) sum += p[i] * ln(maxOf(q[i], 1e-12))
        }
        return -sum
    }

    /** KL 散度 */
    fun klDivergence(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size) return 0.0
        var sum = 0.0
        for (i in p.indices) {
            if (p[i] > 0.0 && q[i] > 0.0) sum += p[i] * ln(p[i] / q[i])
        }
        return sum
    }

    /** JS 散度 */
    fun jensenShannonDivergence(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size) return 0.0
        val m = p.mapIndexed { i, pi -> (pi + q[i]) / 2.0 }
        return 0.5 * klDivergence(p, m) + 0.5 * klDivergence(q, m)
    }

    /** 总变差距离 */
    fun totalVariationDistance(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size) return 0.0
        return 0.5 * p.indices.sumOf { abs(p[it] - q[it]) }
    }

    /** 海林格距离 */
    fun hellingerDistance(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size) return 0.0
        var sum = 0.0
        for (i in p.indices) {
            val diff = sqrt(p[i]) - sqrt(q[i])
            sum += diff * diff
        }
        return sqrt(sum) / sqrt(2.0)
    }

    /** 推土机距离（一维，简化） */
    fun earthMoverDistance(p: List<Double>, q: List<Double>): Double {
        if (p.size != q.size || p.isEmpty()) return 0.0
        var distance = 0.0
        var cumulative = 0.0
        for (i in p.indices) {
            cumulative += p[i] - q[i]
            distance += abs(cumulative)
        }
        return distance
    }
}

// =============================================================================
// 第四十一部分：赔率与市场模型
// ------------------------------------------------------------------------------
// 将博彩赔率转换为概率，并检测市场偏差。
// =============================================================================

object BettingMarketModels {

    /** 赔率转概率（小数赔率） */
    fun oddsToProbability(decimalOdds: Double): Double {
        return if (decimalOdds > 1.0) 1.0 / decimalOdds else 0.0
    }

    /** 概率转赔率 */
    fun probabilityToOdds(probability: Double): Double {
        return if (probability > 0.0 && probability <= 1.0) 1.0 / probability else 0.0
    }

    /** 赔率隐含概率，并去除 overround */
    fun normalizeOddsProbabilities(
        homeOdds: Double,
        drawOdds: Double,
        awayOdds: Double,
    ): Triple<Double, Double, Double> {
        val pHome = oddsToProbability(homeOdds)
        val pDraw = oddsToProbability(drawOdds)
        val pAway = oddsToProbability(awayOdds)
        val total = pHome + pDraw + pAway
        return if (total > 0) {
            Triple(pHome / total, pDraw / total, pAway / total)
        } else Triple(0.33, 0.34, 0.33)
    }

    /** 计算 overround（庄家抽水） */
    fun overround(homeOdds: Double, drawOdds: Double, awayOdds: Double): Double {
        val pHome = oddsToProbability(homeOdds)
        val pDraw = oddsToProbability(drawOdds)
        val pAway = oddsToProbability(awayOdds)
        return pHome + pDraw + pAway - 1.0
    }

    /** 凯利准则投注比例：f* = (bp - q) / b */
    fun kellyCriterion(b: Double, p: Double): Double {
        val q = 1.0 - p
        return (b * p - q) / b
    }

    /** 半凯利投注比例 */
    fun fractionalKelly(b: Double, p: Double, fraction: Double): Double {
        return kellyCriterion(b, p) * fraction
    }

    /** 期望值（EV） */
    fun expectedValue(stake: Double, odds: Double, probability: Double): Double {
        return stake * (odds * probability - 1.0)
    }

    /** 收益率（ROI） */
    fun returnOnInvestment(profit: Double, stake: Double): Double {
        return if (stake > 0) profit / stake else 0.0
    }

    /** 赔率偏差：市场概率 vs 模型概率 */
    fun oddsEdge(marketProbability: Double, modelProbability: Double): Double {
        return modelProbability - marketProbability
    }

    /** 价值投注检测 */
    fun isValueBet(marketProbability: Double, modelProbability: Double, threshold: Double = 0.05): Boolean {
        return (modelProbability - marketProbability) > threshold
    }

    /** 亚盘赔率转让球概率 */
    fun asianHandicapProbability(spread: Double, homeXg: Double, awayXg: Double): Double {
        val diff = homeXg - awayXg - spread
        return ExtendedDistributions.normalCdf(diff * 1.5)
    }

    /** 大小球概率（基于总进球期望） */
    fun overUnderProbability(totalXg: Double, line: Double, over: Boolean): Double {
        val mean = totalXg
        val variance = mean
        val std = sqrt(variance)
        val z = (line + 0.5 - mean) / std
        return if (over) 1.0 - ExtendedDistributions.normalCdf(z) else ExtendedDistributions.normalCdf(z)
    }

    /** 赔率变化动量：近期赔率变化率 */
    fun oddsMomentum(oddsHistory: List<Double>): Double {
        if (oddsHistory.size < 2) return 0.0
        val first = oddsHistory.first()
        val last = oddsHistory.last()
        return if (first > 0) (last - first) / first else 0.0
    }

    /** 市场共识强度：赔率收敛速度 */
    fun marketConvergence(openingOdds: Double, closingOdds: Double): Double {
        return if (openingOdds > 0) 1.0 - abs(closingOdds - openingOdds) / openingOdds else 0.0
    }

    /** 夏普比率（基于历史投注收益） */
    fun sharpeRatio(returns: List<Double>, riskFreeRate: Double = 0.0): Double {
        if (returns.size < 2) return 0.0
        val excess = returns.map { it - riskFreeRate }
        val mean = excess.average()
        val std = PredictMath.standardDeviation(excess)
        return if (std > 0) mean / std else 0.0
    }

    /** 最大回撤 */
    fun maxDrawdown(equityCurve: List<Double>): Double {
        if (equityCurve.isEmpty()) return 0.0
        var peak = equityCurve[0]
        var maxDd = 0.0
        for (value in equityCurve) {
            if (value > peak) peak = value
            val dd = (peak - value) / peak
            if (dd > maxDd) maxDd = dd
        }
        return maxDd
    }

    /** 投注组合凯利优化（多结果） */
    fun diversifiedKelly(
        probabilities: List<Double>,
        odds: List<Double>,
    ): List<Double> {
        if (probabilities.size != odds.size || probabilities.isEmpty()) return probabilities
        val raw = probabilities.mapIndexed { i, p -> maxOf(0.0, kellyCriterion(odds[i] - 1.0, p)) }
        val total = raw.sum()
        return if (total > 0) raw.map { it / total } else probabilities.map { 0.0 }
    }

    /** 蒙特卡洛投注模拟 */
    fun simulateBankroll(
        initial: Double,
        bets: List<Pair<Double, Double>>,
        rng: java.util.Random,
    ): List<Double> {
        val equity = mutableListOf(initial)
        var current = initial
        for ((stake, prob) in bets) {
            val win = rng.nextDouble() < prob
            current += if (win) stake * 0.9 else -stake
            equity.add(current)
        }
        return equity
    }
}

// =============================================================================
// 第四十二部分：联赛与赛事强度调整
// ------------------------------------------------------------------------------
// 不同联赛和杯赛的竞技水平不同，需对球队强度进行换算。
// =============================================================================

object CompetitionAdjustments {

    /** 联赛强度系数（示例，基于欧战积分） */
    private val LEAGUE_STRENGTH = mapOf(
        "英超" to 1.10,
        "西甲" to 1.08,
        "德甲" to 1.07,
        "意甲" to 1.06,
        "法甲" to 1.04,
        "葡超" to 0.98,
        "荷甲" to 0.96,
        "巴西甲" to 0.94,
        "阿甲" to 0.92,
        "中超" to 0.78,
        "美职联" to 0.82,
        "世界杯" to 1.00,
        "欧洲杯" to 1.00,
        "美洲杯" to 0.98,
        "亚洲杯" to 0.82,
        "非洲杯" to 0.85,
        "世俱杯" to 1.00,
    )

    /** 获取联赛强度系数 */
    fun leagueStrength(leagueName: String): Double {
        return LEAGUE_STRENGTH[leagueName] ?: 0.90
    }

    /** 跨联赛强度换算：将球队实力映射到统一尺度 */
    fun normalizeTeamStrength(
        rating: Double,
        sourceLeague: String,
        targetLeague: String,
    ): Double {
        val src = leagueStrength(sourceLeague)
        val tgt = leagueStrength(targetLeague)
        return if (src > 0) rating * (tgt / src) else rating
    }

    /** 赛事类型权重 */
    fun competitionTypeWeight(type: String): Double {
        return when (type.uppercase()) {
            "FRIENDLY" -> 0.70
            "QUALIFIER" -> 0.95
            "GROUP_STAGE" -> 1.00
            "KNOCKOUT" -> 1.10
            "FINAL" -> 1.15
            else -> 1.00
        }
    }

    /** 主客场中立场地调整 */
    fun neutralVenueAdjustment(isNeutral: Boolean, homeAdvantage: Double): Double {
        return if (isNeutral) 1.0 else homeAdvantage
    }

    /** 杯赛淘汰赛进球期望值提升系数 */
    fun knockoutIntensityAdjustment(stage: String): Double {
        return when (stage.uppercase()) {
            "ROUND_OF_16" -> 1.02
            "QUARTER_FINAL" -> 1.04
            "SEMI_FINAL" -> 1.06
            "FINAL" -> 1.08
            else -> 1.00
        }
    }

    /** 两回合比赛客场进球规则影响（旧规则参考） */
    fun awayGoalsWeight(isSecondLeg: Boolean): Double {
        return if (isSecondLeg) 1.05 else 1.00
    }

    /** 高原主场优势调整 */
    fun altitudeAdjustment(altitudeMeters: Double): Double {
        return 1.0 + (altitudeMeters / 3000.0) * 0.03
    }

    /** 气候带差异调整 */
    fun climateAdjustment(homeClimate: String, awayClimate: String): Double {
        return if (homeClimate == awayClimate) 1.0 else 0.98
    }

    /** 旅行距离疲劳系数 */
    fun travelFatigue(distanceKm: Double): Double {
        return maxOf(0.92, 1.0 - distanceKm / 20000.0)
    }

    /** 时区差异影响 */
    fun timezoneAdjustment(hoursDiff: Double): Double {
        return maxOf(0.96, 1.0 - abs(hoursDiff) * 0.01)
    }

    /** 比赛重要程度综合系数 */
    fun matchImportanceIndex(
        competitionWeight: Double,
        stageWeight: Double,
        rivalryWeight: Double,
    ): Double {
        return (competitionWeight * 0.5 + stageWeight * 0.3 + rivalryWeight * 0.2)
            .coerceIn(0.7, 1.2)
    }

    /** 联赛排名压力系数 */
    fun tablePressureIndex(
        homePosition: Int,
        awayPosition: Int,
        totalTeams: Int,
    ): Double {
        if (totalTeams <= 1) return 1.0
        val homeNorm = (homePosition - 1.0) / (totalTeams - 1.0)
        val awayNorm = (awayPosition - 1.0) / (totalTeams - 1.0)
        return 1.0 + abs(homeNorm - awayNorm) * 0.1
    }

    /** 洲际比赛经验系数 */
    fun continentalExperienceIndex(experienceScore: Double): Double {
        return 1.0 + (experienceScore / 100.0) * 0.06
    }

    /** 赛季阶段调整（初/中/末段） */
    fun seasonPhaseFactor(matchWeek: Int, totalWeeks: Int): Double {
        if (totalWeeks <= 0) return 1.0
        val ratio = matchWeek.toDouble() / totalWeeks
        return when {
            ratio < 0.25 -> 0.98
            ratio < 0.75 -> 1.00
            else -> 1.03
        }
    }

    /** 周中 vs 周末比赛影响 */
    fun fixtureCongestionFactor(daysSinceLastMatch: Double): Double {
        return when {
            daysSinceLastMatch < 3.0 -> 0.94
            daysSinceLastMatch < 5.0 -> 0.97
            daysSinceLastMatch < 7.0 -> 1.00
            else -> 1.02
        }
    }

    /** 球队阵容深度惩罚（多线作战） */
    fun squadDepthPenalty(squadSize: Int, starterQuality: Double): Double {
        val idealSize = 25.0
        val sizeFactor = 1.0 - abs(squadSize - idealSize) / 50.0
        return (sizeFactor * 0.3 + (starterQuality / 100.0) * 0.7).coerceIn(0.8, 1.1)
    }

    /** 主教练经验系数 */
    fun managerExperienceFactor(years: Double): Double {
        return (1.0 + years / 100.0).coerceIn(0.95, 1.08)
    }
}

// =============================================================================
// 第四十三部分：伤病、停赛与阵容估计
// ------------------------------------------------------------------------------
// 根据缺阵球员估算球队实力折损。
// =============================================================================

object SquadAvailabilityModels {

    data class PlayerImpact(
        val name: String,
        val positionWeight: Double,
        val qualityRating: Double,
        val isStarter: Boolean,
    )

    /** 根据缺阵球员列表估算实力折损 */
    fun calculateAbsenceImpact(
        absentPlayers: List<PlayerImpact>,
        baselineStrength: Double,
    ): Double {
        if (baselineStrength <= 0.0) return 0.0
        var totalImpact = 0.0
        for (player in absentPlayers) {
            val starterMultiplier = if (player.isStarter) 1.0 else 0.4
            val impact = player.positionWeight * player.qualityRating * starterMultiplier / 100.0
            totalImpact += impact
        }
        return totalImpact.coerceIn(0.0, 0.5)
    }

    /** 位置权重 */
    fun positionWeight(position: String): Double {
        return when (position.uppercase()) {
            "GK" -> 0.18
            "CB" -> 0.15
            "FB" -> 0.12
            "DM" -> 0.13
            "CM" -> 0.14
            "AM" -> 0.13
            "WING" -> 0.12
            "FW" -> 0.16
            else -> 0.10
        }
    }

    /** 关键球员缺阵影响 */
    fun keyPlayerAbsenceImpact(
        keyPlayersOut: Int,
        totalKeyPlayers: Int,
    ): Double {
        if (totalKeyPlayers <= 0) return 0.0
        return (keyPlayersOut.toDouble() / totalKeyPlayers).coerceIn(0.0, 0.4)
    }

    /** 防线完整性 */
    fun defensiveLineIntegrity(
        missingDefenders: Int,
        missingMidfielders: Int,
    ): Double {
        val defensePenalty = missingDefenders * 0.08
        val midfieldShieldPenalty = missingMidfielders * 0.04
        return maxOf(0.5, 1.0 - defensePenalty - midfieldShieldPenalty)
    }

    /** 进攻火力折损 */
    fun attackingPowerLoss(
        missingForwards: Int,
        missingCreators: Int,
    ): Double {
        val forwardPenalty = missingForwards * 0.09
        val creatorPenalty = missingCreators * 0.07
        return maxOf(0.5, 1.0 - forwardPenalty - creatorPenalty)
    }

    /** 伤病潮综合惩罚 */
    fun injuryCrisisPenalty(absentPlayers: Int): Double {
        return maxOf(0.6, 1.0 - absentPlayers * 0.03)
    }

    /** 阵容轮换猜测：周中比赛后周末联赛 */
    fun rotationFactor(recentMatches: Int, daysRest: Double): Double {
        val fatigue = recentMatches * 0.02
        val restBonus = if (daysRest < 3.0) -0.05 else 0.0
        return maxOf(0.85, 1.0 - fatigue + restBonus)
    }

    /** 黄牌停赛风险 */
    fun suspensionRisk(yellowCards: Int, threshold: Int): Double {
        if (threshold <= 0) return 0.0
        return (yellowCards.toDouble() / threshold).coerceIn(0.0, 1.0)
    }

    /** 复出球员增益 */
    fun returningPlayerBoost(
        returningPlayers: List<PlayerImpact>,
    ): Double {
        var boost = 0.0
        for (player in returningPlayers) {
            boost += player.positionWeight * player.qualityRating / 200.0
        }
        return boost.coerceIn(0.0, 0.15)
    }

    /** 预计首发强度 */
    fun expectedLineupStrength(
        baseline: TeamBaselineData,
        absentPlayers: List<PlayerImpact>,
    ): Double {
        val base = (baseline.attackRating + baseline.defenseRating + baseline.midfieldRating) / 3.0
        val impact = calculateAbsenceImpact(absentPlayers, base)
        return base * (1.0 - impact)
    }
}

// =============================================================================
// 第四十四部分：时间序列与动量模型
// ------------------------------------------------------------------------------
// 利用近期比赛结果的时间序列特征预测未来表现。
// =============================================================================

object TimeSeriesModels {

    /** 简单移动平均（SMA） */
    fun simpleMovingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 0 || values.size < window) return emptyList()
        val result = mutableListOf<Double>()
        for (i in window - 1 until values.size) {
            val windowValues = values.subList(i - window + 1, i + 1)
            result.add(windowValues.average())
        }
        return result
    }

    /** 指数移动平均（EMA） */
    fun exponentialMovingAverage(values: List<Double>, alpha: Double): List<Double> {
        if (values.isEmpty() || alpha < 0.0 || alpha > 1.0) return emptyList()
        val result = mutableListOf(values[0])
        for (i in 1 until values.size) {
            val ema = alpha * values[i] + (1.0 - alpha) * result[i - 1]
            result.add(ema)
        }
        return result
    }

    /** 加权移动平均（WMA） */
    fun weightedMovingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 0 || values.size < window) return emptyList()
        val result = mutableListOf<Double>()
        for (i in window - 1 until values.size) {
            var weightedSum = 0.0
            var weightSum = 0.0
            for (j in 0 until window) {
                val weight = (j + 1).toDouble()
                weightedSum += weight * values[i - window + 1 + j]
                weightSum += weight
            }
            result.add(weightedSum / weightSum)
        }
        return result
    }

    /** 一阶差分 */
    fun firstDifference(values: List<Double>): List<Double> {
        if (values.size < 2) return emptyList()
        return values.zipWithNext { a, b -> b - a }
    }

    /** 对数收益率 */
    fun logReturns(values: List<Double>): List<Double> {
        if (values.size < 2) return emptyList()
        return values.zipWithNext { a, b -> if (a > 0) ln(b / a) else 0.0 }
    }

    /** 自相关函数（ACF） */
    fun autocorrelation(values: List<Double>, lag: Int): Double {
        if (lag <= 0 || values.size <= lag) return 0.0
        val mean = values.average()
        val n = values.size
        var numerator = 0.0
        var denominator = 0.0
        for (i in lag until n) {
            numerator += (values[i] - mean) * (values[i - lag] - mean)
        }
        for (v in values) denominator += (v - mean).pow(2)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 简单线性趋势斜率 */
    fun linearTrendSlope(values: List<Double>): Double {
        val n = values.size.toDouble()
        if (n < 2) return 0.0
        val xMean = (n - 1.0) / 2.0
        val yMean = values.average()
        var num = 0.0
        var den = 0.0
        for (i in values.indices) {
            val dx = i.toDouble() - xMean
            val dy = values[i] - yMean
            num += dx * dy
            den += dx * dx
        }
        return if (den > 0) num / den else 0.0
    }

    /** 动量评分：近期趋势 + 波动 */
    fun momentumScore(
        values: List<Double>,
        recentWindow: Int = 5,
    ): Double {
        if (values.size < recentWindow) return 0.0
        val recent = values.takeLast(recentWindow)
        val slope = linearTrendSlope(recent)
        val mean = recent.average()
        val std = PredictMath.standardDeviation(recent)
        val stability = if (mean > 0) 1.0 - (std / mean).coerceIn(0.0, 1.0) else 0.0
        return slope * stability
    }

    /** 连胜/连败检测 */
    fun streakLength(results: List<Double>, threshold: Double): Int {
        var maxStreak = 0
        var current = 0
        for (r in results) {
            if (r >= threshold) {
                current++
                if (current > maxStreak) maxStreak = current
            } else {
                current = 0
            }
        }
        return maxStreak
    }

    /**  form 曲线曲率 */
    fun formCurvature(values: List<Double>): Double {
        if (values.size < 3) return 0.0
        var sum = 0.0
        for (i in 1 until values.size - 1) {
            sum += values[i + 1] - 2.0 * values[i] + values[i - 1]
        }
        return sum / (values.size - 2)
    }

    /** 相对强弱指数（RSI 式） */
    fun relativeStrengthIndex(values: List<Double>, window: Int): Double {
        if (values.size < window + 1) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in 1 until window + 1) {
            val diff = values[values.size - window + i - 1] - values[values.size - window + i - 2]
            if (diff > 0) gains += diff else losses += abs(diff)
        }
        val avgGain = gains / window
        val avgLoss = losses / window
        return if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }

    /** MACD 式交叉信号 */
    fun macdSignal(values: List<Double>, fast: Int, slow: Int): Double {
        val fastEma = exponentialMovingAverage(values, 2.0 / (fast + 1)).lastOrNull() ?: 0.0
        val slowEma = exponentialMovingAverage(values, 2.0 / (slow + 1)).lastOrNull() ?: 0.0
        return fastEma - slowEma
    }

    /** 布林带式上下轨 */
    fun bollingerBands(values: List<Double>, window: Int, multiplier: Double): Triple<Double, Double, Double> {
        if (values.size < window) return Triple(0.0, 0.0, 0.0)
        val recent = values.takeLast(window)
        val mean = recent.average()
        val std = PredictMath.standardDeviation(recent)
        return Triple(mean - multiplier * std, mean, mean + multiplier * std)
    }

    /** 均值回归强度 */
    fun meanReversionStrength(values: List<Double>, window: Int): Double {
        if (values.size < window) return 0.0
        val recent = values.takeLast(window)
        val mean = recent.average()
        val last = recent.last()
        return if (mean != 0.0) (last - mean) / mean else 0.0
    }
}

// =============================================================================
// 第四十五部分：高级模拟与情景分析
// ------------------------------------------------------------------------------
// 蒙特卡洛、Bootstrap 等多种模拟手段评估不确定性。
// =============================================================================

object SimulationEngines {

    /** Bootstrap 重采样均值分布 */
    fun bootstrapMeans(
        samples: List<Double>,
        iterations: Int,
        rng: java.util.Random,
    ): List<Double> {
        val means = mutableListOf<Double>()
        repeat(iterations) {
            val resample = List(samples.size) { samples[rng.nextInt(samples.size)] }
            means.add(resample.average())
        }
        return means
    }

    /** Bootstrap 置信区间 */
    fun bootstrapConfidenceInterval(
        samples: List<Double>,
        iterations: Int,
        confidence: Double,
        rng: java.util.Random,
    ): Pair<Double, Double> {
        val means = bootstrapMeans(samples, iterations, rng).sorted()
        val lowerIndex = ((1.0 - confidence) / 2.0 * means.size).toInt().coerceAtLeast(0)
        val upperIndex = ((1.0 + confidence) / 2.0 * means.size).toInt().coerceAtMost(means.size - 1)
        return means[lowerIndex] to means[upperIndex]
    }

    /** 参数扰动敏感性分析 */
    fun parameterSensitivity(
        baseParams: DoubleArray,
        perturbationScale: Double,
        evaluator: (DoubleArray) -> Double,
        rng: java.util.Random,
    ): Double {
        val baseValue = evaluator(baseParams)
        var totalDelta = 0.0
        repeat(50) {
            val perturbed = baseParams.map { it + (rng.nextGaussian() * perturbationScale) }.toDoubleArray()
            totalDelta += abs(evaluator(perturbed) - baseValue)
        }
        return totalDelta / 50.0
    }

    /** 情景压力测试：改变某一参数看结果变化 */
    fun scenarioStressTest(
        baseParams: DoubleArray,
        paramIndex: Int,
        multipliers: List<Double>,
        evaluator: (DoubleArray) -> Double,
    ): List<Double> {
        return multipliers.map { m ->
            val scenario = baseParams.copyOf()
            scenario[paramIndex] *= m
            evaluator(scenario)
        }
    }

    /** 龙卷风图分析：各参数对结果的影响幅度 */
    fun tornadoAnalysis(
        baseParams: DoubleArray,
        perturbation: Double,
        evaluator: (DoubleArray) -> Double,
    ): List<Double> {
        val baseValue = evaluator(baseParams)
        return baseParams.indices.map { i ->
            val up = baseParams.copyOf()
            up[i] *= (1.0 + perturbation)
            val down = baseParams.copyOf()
            down[i] *= (1.0 - perturbation)
            abs(evaluator(up) - baseValue) + abs(evaluator(down) - baseValue)
        }
    }

    /** 比分矩阵热区概率 */
    fun scoreMatrixHotZone(
        matrix: Array<DoubleArray>,
        homeRange: IntRange,
        awayRange: IntRange,
    ): Double {
        var sum = 0.0
        for (h in homeRange) {
            for (a in awayRange) {
                if (h < matrix.size && a < matrix[h].size) sum += matrix[h][a]
            }
        }
        return sum
    }

    /** 模拟夺冠概率（联赛积分制） */
    fun simulateLeagueWinner(
        teams: List<Pair<String, Double>>,
        remainingMatches: Int,
        iterations: Int,
        rng: java.util.Random,
    ): Map<String, Double> {
        val wins = mutableMapOf<String, Int>()
        repeat(iterations) {
            val table = teams.map { it.first to rng.nextGaussian() * it.second }.toMap().toMutableMap()
            repeat(remainingMatches) {
                val shuffled = teams.shuffled(rng)
                for (i in shuffled.indices step 2) {
                    if (i + 1 < shuffled.size) {
                        val homeTeam = shuffled[i].first
                        val awayTeam = shuffled[i + 1].first
                        val homeBase = table[homeTeam] ?: 0.0
                        val awayBase = table[awayTeam] ?: 0.0
                        val homeStrength = homeBase + rng.nextGaussian() * 0.5
                        val awayStrength = awayBase + rng.nextGaussian() * 0.5
                        val diff = homeStrength - awayStrength
                        when {
                            diff > 0.3 -> table[homeTeam] = homeBase + 3.0
                            diff < -0.3 -> table[awayTeam] = awayBase + 3.0
                            else -> {
                                table[homeTeam] = homeBase + 1.0
                                table[awayTeam] = awayBase + 1.0
                            }
                        }
                    }
                }
            }
            val winner = table.maxByOrNull { it.value }?.key ?: ""
            wins[winner] = wins.getOrDefault(winner, 0) + 1
        }
        return wins.mapValues { it.value.toDouble() / iterations }
    }

    /** 模拟淘汰赛晋级概率 */
    fun simulateKnockoutAdvancement(
        teamStrength: Double,
        opponentStrength: Double,
        isTwoLegged: Boolean,
        iterations: Int,
        rng: java.util.Random,
    ): Double {
        var advances = 0
        repeat(iterations) {
            val homeLeg = teamStrength + rng.nextGaussian() * 0.5 > opponentStrength + rng.nextGaussian() * 0.5
            if (!isTwoLegged) {
                if (homeLeg) advances++
            } else {
                val awayLeg = opponentStrength + rng.nextGaussian() * 0.5 < teamStrength + rng.nextGaussian() * 0.5
                if (homeLeg && awayLeg) advances++
                else if (homeLeg != awayLeg) advances++
            }
        }
        return advances.toDouble() / iterations
    }

    /** 点球大战模拟 */
    fun simulatePenaltyShootout(
        homeConversion: Double,
        awayConversion: Double,
        iterations: Int,
        rng: java.util.Random,
    ): Double {
        var homeWins = 0
        repeat(iterations) {
            var homeScore = 0; var awayScore = 0
            for (round in 0 until 5) {
                if (rng.nextDouble() < homeConversion) homeScore++
                if (rng.nextDouble() < awayConversion) awayScore++
            }
            while (homeScore == awayScore) {
                if (rng.nextDouble() < homeConversion) homeScore++
                if (rng.nextDouble() < awayConversion) awayScore++
            }
            if (homeScore > awayScore) homeWins++
        }
        return homeWins.toDouble() / iterations
    }

    /** 极端比分情景分析 */
    fun extremeScoreScenario(
        xgHome: Double,
        xgAway: Double,
        stdDev: Double,
        rng: java.util.Random,
    ): Pair<Int, Int> {
        val homeGoals = maxOf(0, (xgHome + rng.nextGaussian() * stdDev).toInt())
        val awayGoals = maxOf(0, (xgAway + rng.nextGaussian() * stdDev).toInt())
        return homeGoals to awayGoals
    }

    /** 关键事件影响模拟（红牌、点球） */
    fun criticalEventImpact(
        baseXg: Double,
        eventMinute: Int,
        eventType: String,
    ): Double {
        val remaining = (90 - eventMinute) / 90.0
        return when (eventType.uppercase()) {
            "RED_CARD" -> baseXg * (1.0 - 0.5 * remaining)
            "PENALTY" -> baseXg + 0.76 * remaining
            "OWN_GOAL" -> baseXg * 1.1
            else -> baseXg
        }
    }
}

// =============================================================================
// 第四十六部分：预测质量与校准评估
// ------------------------------------------------------------------------------
// 评估预测系统长期表现，确保概率校准良好。
// =============================================================================

object PredictionQualityMetrics {

    /** Brier 分数（越低越好） */
    fun brierScore(predicted: Double, actual: Boolean): Double {
        val outcome = if (actual) 1.0 else 0.0
        return (predicted - outcome).pow(2)
    }

    /** 多类 Brier 分数 */
    fun multiClassBrierScore(predicted: List<Double>, actualIndex: Int): Double {
        val target = MutableList(predicted.size) { 0.0 }
        if (actualIndex in target.indices) target[actualIndex] = 1.0
        return predicted.indices.sumOf { (predicted[it] - target[it]).pow(2) } / predicted.size
    }

    /** 对数损失 */
    fun logLoss(predicted: Double, actual: Boolean): Double {
        val p = predicted.coerceIn(1e-6, 1.0 - 1e-6)
        return if (actual) -ln(p) else -ln(1.0 - p)
    }

    /** 多类对数损失 */
    fun multiClassLogLoss(predicted: List<Double>, actualIndex: Int): Double {
        val p = predicted.getOrElse(actualIndex) { 1e-6 }.coerceIn(1e-6, 1.0)
        return -ln(p)
    }

    /** 排名概率分数（RPS） */
    fun rankedProbabilityScore(predicted: List<Double>, actualIndex: Int): Double {
        if (actualIndex !in predicted.indices) return 0.0
        var sum = 0.0
        var cumPred = 0.0
        var cumActual = 0.0
        for (i in predicted.indices) {
            cumPred += predicted[i]
            cumActual += if (i == actualIndex) 1.0 else 0.0
            sum += (cumPred - cumActual).pow(2)
        }
        return sum / (predicted.size - 1)
    }

    /** 准确率（最高概率类别） */
    fun top1Accuracy(predictions: List<List<Double>>, actuals: List<Int>): Double {
        if (predictions.isEmpty()) return 0.0
        var correct = 0
        for (i in predictions.indices) {
            if (predictions[i].indexOfMax() == actuals.getOrElse(i) { -1 }) correct++
        }
        return correct.toDouble() / predictions.size
    }

    /** 预测收益（按赔率） */
    fun bettingProfit(
        predictions: List<Double>,
        outcomes: List<Boolean>,
        odds: List<Double>,
        stake: Double,
    ): Double {
        var profit = 0.0
        for (i in predictions.indices) {
            if (outcomes.getOrElse(i) { false }) {
                profit += stake * (odds.getOrElse(i) { 0.0 } - 1.0)
            } else {
                profit -= stake
            }
        }
        return profit
    }

    /** 校准曲线：按预测概率分桶 */
    fun calibrationCurve(
        predicted: List<Double>,
        actual: List<Boolean>,
        bins: Int,
    ): List<Pair<Double, Double>> {
        val binSize = 1.0 / bins
        val result = mutableListOf<Pair<Double, Double>>()
        for (b in 0 until bins) {
            val lower = b * binSize
            val upper = (b + 1) * binSize
            val indices = predicted.indices.filter { predicted[it] in lower..upper }
            val meanPred = if (indices.isNotEmpty()) indices.map { predicted[it] }.average() else (lower + upper) / 2.0
            val actualRate = if (indices.isNotEmpty()) indices.count { actual[it] }.toDouble() / indices.size else 0.0
            result.add(meanPred to actualRate)
        }
        return result
    }

    /** 期望校准误差（ECE） */
    fun expectedCalibrationError(
        predicted: List<Double>,
        actual: List<Boolean>,
        bins: Int,
    ): Double {
        val binSize = 1.0 / bins
        var ece = 0.0
        for (b in 0 until bins) {
            val lower = b * binSize
            val upper = (b + 1) * binSize
            val indices = predicted.indices.filter { predicted[it] in lower..upper }
            if (indices.isEmpty()) continue
            val meanPred = indices.map { predicted[it] }.average()
            val actualRate = indices.count { actual[it] }.toDouble() / indices.size
            ece += (indices.size.toDouble() / predicted.size) * abs(meanPred - actualRate)
        }
        return ece
    }

    /** 最大平均差异（MMD，简化） */
    fun maximumMeanDiscrepancy(samples1: List<Double>, samples2: List<Double>): Double {
        if (samples1.isEmpty() || samples2.isEmpty()) return 0.0
        val mean1 = samples1.average()
        val mean2 = samples2.average()
        return abs(mean1 - mean2)
    }

    /** 预测置信度与准确率关系 */
    fun confidenceAccuracyRelation(
        predictions: List<Double>,
        actuals: List<Boolean>,
    ): Pair<Double, Double> {
        val highConf = predictions.indices.filter { predictions[it] > 0.7 }
        val acc = if (highConf.isNotEmpty()) highConf.count { actuals[it] }.toDouble() / highConf.size else 0.0
        return highConf.size.toDouble() / predictions.size to acc
    }

    /** ROC AUC（梯形法近似） */
    fun approximateAuc(scores: List<Double>, labels: List<Boolean>): Double {
        val pairs = scores.zip(labels).sortedByDescending { it.first }
        var tp = 0.0; var fp = 0.0
        var tpPrev = 0.0; var fpPrev = 0.0
        var auc = 0.0
        val totalPos = labels.count { it }.toDouble()
        val totalNeg = labels.size - totalPos
        if (totalPos == 0.0 || totalNeg == 0.0) return 0.5
        for ((_, label) in pairs) {
            if (label) tp++ else fp++
            auc += (fp - fpPrev) * (tp + tpPrev) / 2.0
            tpPrev = tp; fpPrev = fp
        }
        return auc / (totalPos * totalNeg)
    }

    /** 基尼系数（用于不平等度量） */
    fun giniCoefficient(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size.toDouble()
        val mean = sorted.average()
        if (mean == 0.0) return 0.0
        var sum = 0.0
        for (i in sorted.indices) sum += (2.0 * (i + 1) - n - 1.0) * sorted[i]
        return sum / (n * n * mean)
    }

    /** 模拟集成一致性 */
    fun ensembleAgreement(models: List<List<Double>>): Double {
        if (models.size < 2 || models.any { it.size != models[0].size }) return 0.0
        val n = models[0].size
        var agreement = 0.0
        for (i in 0 until n) {
            val column = models.map { it[i] }
            val votes = models.map { it.indexOfMax() }
            val mode = votes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.value ?: 0
            agreement += mode.toDouble() / models.size
        }
        return agreement / n
    }
}

// =============================================================================
// 第四十七部分：工具函数与常量
// ------------------------------------------------------------------------------
// 预测系统通用辅助函数。
// =============================================================================

object PredictionSystemConstants {

    const val MAX_GOALS = 10
    const val DEFAULT_HALF_LIFE_DAYS = 180.0
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.65
    const val DEFAULT_UPSET_THRESHOLD = 0.35
    const val HOME_ADVANTAGE_XG = 0.25
    const val AWAY_DISADVANTAGE_XG = 0.15
    const val SET_PIECE_XG_HOME = 0.35
    const val SET_PIECE_XG_AWAY = 0.25
    const val COUNTER_ATTACK_XG_HOME = 0.20
    const val COUNTER_ATTACK_XG_AWAY = 0.18
    const val DEFAULT_TEMPERATURE_C = 18.0
    const val DEFAULT_WIND_KMH = 10.0
    const val DEFAULT_HUMIDITY = 0.60
    const val RAIN_IMPACT_FACTOR = 0.92
    const val HEAT_IMPACT_FACTOR = 0.94
    const val COLD_IMPACT_FACTOR = 0.96
    const val WIND_IMPACT_FACTOR = 0.95
    const val ALTITUDE_STANDARD_M = 0.0
    const val PITCH_SIZE_STANDARD_M2 = 7140.0
    const val WORLD_CUP_WEIGHT = 1.15
    const val CONTINENTAL_WEIGHT = 1.08
    const val LEAGUE_WEIGHT = 1.00
    const val FRIENDLY_WEIGHT = 0.75
    const val DEFAULT_MC_ITERATIONS = 10000
    const val DEFAULT_BOOTSTRAP_ITERATIONS = 5000
    const val ELO_K_BASE = 32.0
    const val ELO_K_WORLD_CUP = 48.0
    const val ELO_K_FRIENDLY = 16.0
    const val RATING_SCALE_MIN = 0.0
    const val RATING_SCALE_MAX = 100.0
    const val FORM_WINDOW_SHORT = 3
    const val FORM_WINDOW_MEDIUM = 5
    const val FORM_WINDOW_LONG = 10
    const val HEAD_TO_HEAD_MAX_MATCHES = 10
    const val DEFAULT_SIMULATION_SEED = 42L
}

/** 列表求最大索引扩展 */
fun List<Double>.indexOfMax(): Int {
    if (isEmpty()) return -1
    var maxIndex = 0
    for (i in indices) {
        if (this[i] > this[maxIndex]) maxIndex = i
    }
    return maxIndex
}

/** 安全除法 */
fun safeDivide(a: Double, b: Double, default: Double = 0.0): Double {
    return if (b != 0.0) a / b else default
}

/** 限制在 [0,1] */
fun coerceProbability(p: Double): Double {
    return p.coerceIn(0.0, 1.0)
}

/** 将整数比分转换为字符串 */
fun scoreToString(home: Int, away: Int): String {
    return "$home-$away"
}

/** 计算两队综合评分差 */
fun compositeRatingDifference(
    home: TeamBaselineData,
    away: TeamBaselineData,
): Double {
    val homeComposite = (home.attackRating + home.midfieldRating + home.defenseRating) / 3.0
    val awayComposite = (away.attackRating + away.midfieldRating + away.defenseRating) / 3.0
    return homeComposite - awayComposite
}

/** 将预测输出归一化为概率 */
fun normalizePredictions(home: Double, draw: Double, away: Double): Triple<Double, Double, Double> {
    val total = home + draw + away
    return if (total > 0) Triple(home / total, draw / total, away / total) else Triple(0.33, 0.34, 0.33)
}

/** 综合多个模型预测（加权平均） */
fun ensembleWeightedAverage(
    predictions: List<Triple<Double, Double, Double>>,
    weights: List<Double>,
): Triple<Double, Double, Double> {
    if (predictions.isEmpty() || predictions.size != weights.size) return Triple(0.33, 0.34, 0.33)
    var h = 0.0; var d = 0.0; var a = 0.0; var wSum = 0.0
    for (i in predictions.indices) {
        h += predictions[i].first * weights[i]
        d += predictions[i].second * weights[i]
        a += predictions[i].third * weights[i]
        wSum += weights[i]
    }
    return if (wSum > 0) Triple(h / wSum, d / wSum, a / wSum) else Triple(0.33, 0.34, 0.33)
}

