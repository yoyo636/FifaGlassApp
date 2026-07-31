package com.fifaglass.app.ai

import com.fifaglass.app.data.EventType
import com.fifaglass.app.data.MatchDetail
import com.fifaglass.app.data.MatchEvent
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.PredictionOutput

data class AnalysisReport(
    val title: String,
    val summary: String,
    val tacticalBreakdown: List<String>,
    val keyFactors: List<String>,
    val playerToWatch: String,
    val predictionReasoning: String,
    val riskAssessment: String,
    val recommendation: String,
    val confidence: Int,
    val sentiment: MatchSentiment,
)

data class LiveInsight(
    val minute: Int,
    val type: InsightType,
    val headline: String,
    val detail: String,
    val impact: Float,
)

data class PostMatchReport(
    val manOfTheMatch: String,
    val turningPoint: String,
    val tacticalSummary: String,
    val statisticalHighlights: List<String>,
    val grade: Char,
)

enum class MatchSentiment { HIGH_SCORING, TIGHT, ONE_SIDED, UPSET_WATCH, NEUTRAL }
enum class InsightType { GOAL, MOMENTUM_SHIFT, TACTICAL_CHANGE, CARD_IMPACT, NEAR_MISS, SUBSTITUTION }

object MatchAnalyst {

    fun generatePreMatchReport(home: Team, away: Team, prediction: PredictionOutput): AnalysisReport {
        val sentiment = when {
            prediction.upsetProbability > 0.30 -> MatchSentiment.UPSET_WATCH
            prediction.xgHome + prediction.xgAway > 3.0 -> MatchSentiment.HIGH_SCORING
            kotlin.math.abs(prediction.pHome - prediction.pAway) > 0.50 -> MatchSentiment.ONE_SIDED
            prediction.pDraw > 0.30 -> MatchSentiment.TIGHT
            else -> MatchSentiment.NEUTRAL
        }

        val summary = buildString {
            append("${home.name}主场迎战${away.name}。")
            append("AI预测主队胜率${(prediction.pHome * 100).toInt()}%，")
            append("平局${(prediction.pDraw * 100).toInt()}%，")
            append("客队胜率${(prediction.pAway * 100).toInt()}%。")
            append("最可能比分${prediction.likelyScore}，")
            append("预期进球${String.format("%.2f", prediction.xgHome)}-${String.format("%.2f", prediction.xgAway)}。")
            append("信心指数${prediction.confidence}/100。")
        }

        val tacticalBreakdown = mutableListOf<String>().apply {
            add("主队预期进球${String.format("%.2f", prediction.xgHome)}，${if (prediction.xgHome > 1.5) "进攻火力充沛，有望多次攻破对手大门" else "进攻效率一般，需把握有限机会"}")
            add("客队预期进球${String.format("%.2f", prediction.xgAway)}，${if (prediction.xgAway > 1.5) "反击威胁极大" else "进攻端可能受限"}")
            val totalXg = prediction.xgHome + prediction.xgAway
            add("双方合计预期进球${String.format("%.2f", totalXg)}，${if (totalXg > 2.5) "大球概率较高" else "小球可能性大"}")
            add("主队FIFA排名${home.rank}位（${home.points}分），${if (home.rank < away.rank) "排名占优" else "排名处于劣势"}")
            add("双方均进球概率${(prediction.bttsProbability * 100).toInt()}%，${if (prediction.bttsProbability > 0.5) "双方都有进球可能" else "至少一方可能零封"}")
        }

        val keyFactors = mutableListOf<String>().apply {
            add("🏠 主场优势：${home.name}主场作战，球迷支持+熟悉环境")
            add("📊 FIFA排名：${home.name}第${home.rank}位 vs ${away.name}第${away.rank}位")
            add("⚖️ 实力差距：${String.format("%.0f", kotlin.math.abs(prediction.pHome - prediction.pAway) * 100)}%胜率差")
            add("⚠️ 冷门概率：${(prediction.upsetProbability * 100).toInt()}%")
            add("🎯 信心指数：${prediction.confidence}/100")
            prediction.factors.take(3).forEach { add("📌 $it") }
        }

        val playerToWatch = when {
            prediction.pHome > prediction.pAway -> "${home.name}核心球员需担起进攻重任"
            prediction.pAway > prediction.pHome -> "${away.name}反击尖兵值得重点关注"
            else -> "双方中场组织者是比赛关键"
        }

        val predictionReasoning = buildString {
            append("AI模型基于FIFA排名、Elo评分、近期状态、主客场效应、历史交战等多维度数据综合分析。")
            append("蒙特卡洛模拟10000次后，${prediction.likelyScore}出现频率最高。")
            append("主队${String.format("%.2f", prediction.xgHome)}的预期进球来自进攻效率模型，")
            append("客队${String.format("%.2f", prediction.xgAway)}来自防守反击推演。")
            if (prediction.upsetProbability > 0.25) {
                append("注意：冷门概率${(prediction.upsetProbability * 100).toInt()}%较高，存在爆冷可能。")
            }
        }

        val riskAssessment = when {
            prediction.upsetProbability > 0.35 -> "高风险比赛，冷门概率极大。排名较低的一方有显著爆冷可能，建议谨慎看待赔率。"
            prediction.upsetProbability > 0.20 -> "中等风险，存在一定冷门可能。关注临场状态和伤病情况。"
            else -> "低风险，预计结果与预测基本一致。"
        }

        return AnalysisReport(
            title = "⚡ ${home.name} vs ${away.name} 赛前深度分析",
            summary = summary,
            tacticalBreakdown = tacticalBreakdown,
            keyFactors = keyFactors,
            playerToWatch = playerToWatch,
            predictionReasoning = predictionReasoning,
            riskAssessment = riskAssessment,
            recommendation = prediction.recommendation.ifEmpty { "综合分析建议关注主队表现" },
            confidence = prediction.confidence,
            sentiment = sentiment,
        )
    }

    fun generateLiveInsights(
        home: Team, away: Team, detail: MatchDetail,
        currentScore: Pair<Int, Int>, minute: Int,
    ): List<LiveInsight> {
        val insights = mutableListOf<LiveInsight>()
        val (scoreH, scoreA) = currentScore

        for (event in detail.events) {
            val eventMinute = event.sortKey % 1000
            if (eventMinute > minute) continue

            when (event.type) {
                EventType.GOAL -> {
                    val team = if (event.isHome) home.name else away.name
                    insights.add(LiveInsight(
                        minute = eventMinute,
                        type = InsightType.GOAL,
                        headline = "⚽ 进球！${team}改变比分",
                        detail = "第${eventMinute}分钟，${event.title}。当前比分 $scoreH-$scoreA",
                        impact = 1.0f,
                    ))
                }
                EventType.RED -> {
                    insights.add(LiveInsight(
                        minute = eventMinute,
                        type = InsightType.CARD_IMPACT,
                        headline = "🟥 红牌！${if (event.isHome) home.name else away.name}少一人作战",
                        detail = "第${eventMinute}分钟${event.title}，人数劣势将显著影响比赛走势",
                        impact = 0.9f,
                    ))
                }
                EventType.YELLOW -> {
                    insights.add(LiveInsight(
                        minute = eventMinute,
                        type = InsightType.CARD_IMPACT,
                        headline = "🟨 黄牌警告",
                        detail = "${event.title}在第${eventMinute}分钟吃到黄牌",
                        impact = 0.3f,
                    ))
                }
                EventType.SUB -> {
                    insights.add(LiveInsight(
                        minute = eventMinute,
                        type = InsightType.TACTICAL_CHANGE,
                        headline = "🔁 战术换人",
                        detail = "第${eventMinute}分钟${event.title}，教练调整阵容",
                        impact = 0.5f,
                    ))
                }
                else -> {}
            }
        }

        if (minute in 60..80 && kotlin.math.abs(scoreH - scoreA) <= 1) {
            insights.add(LiveInsight(
                minute = minute,
                type = InsightType.MOMENTUM_SHIFT,
                headline = "🔥 比赛进入白热化",
                detail = "第${minute}分钟，比分接近，双方都在寻找制胜机会",
                impact = 0.7f,
            ))
        }

        if (minute > 75 && scoreH == scoreA) {
            insights.add(LiveInsight(
                minute = minute,
                type = InsightType.MOMENTUM_SHIFT,
                headline = "⏰ 进入关键时刻",
                detail = "比赛仅剩${90 - minute}分钟，平局局面随时可能被打破",
                impact = 0.8f,
            ))
        }

        if (minute > 80 && kotlin.math.abs(scoreH - scoreA) == 1) {
            val leading = if (scoreH > scoreA) home.name else away.name
            insights.add(LiveInsight(
                minute = minute,
                type = InsightType.MOMENTUM_SHIFT,
                headline = "🛡️ 顽强防守",
                detail = "${leading}领先一球，进入防守模式",
                impact = 0.6f,
            ))
        }

        return insights.sortedByDescending { it.impact }
    }

    fun generatePostMatchReport(
        home: Team, away: Team, detail: MatchDetail,
        finalScore: Pair<Int, Int>,
    ): PostMatchReport {
        val (scoreH, scoreA) = finalScore
        val winner = when {
            scoreH > scoreA -> home.name
            scoreA > scoreH -> away.name
            else -> "双方"
        }

        val manOfTheMatch = when {
            scoreH > scoreA -> "${home.name}进球功臣，关键进球决定比赛"
            scoreA > scoreH -> "${away.name}客场英雄，逆转或制胜表现"
            else -> "双方门将，力保城门不失"
        }

        val turningPoint = when {
            detail.events.any { it.type == EventType.RED } -> "红牌改变了比赛平衡，少一方陷入被动"
            detail.events.any { it.type == EventType.GOAL } -> {
                val lastGoal = detail.events.last { it.type == EventType.GOAL }
                "第${lastGoal.sortKey % 1000}分钟的进球成为转折点"
            }
            else -> "整场比赛节奏均匀，无明显转折"
        }

        val tacticalSummary = buildString {
            append("${home.name} ${scoreH}-${scoreA} ${away.name}。")
            append("全场${detail.events.size}个关键事件，")
            val goals = detail.events.count { it.type == EventType.GOAL }
            append("${goals}个进球。")
            val cards = detail.events.count { it.type == EventType.YELLOW || it.type == EventType.RED }
            append("${cards}张牌。")
            if (detail.homePossession != null) {
                append("主队控球率${detail.homePossession}%。")
            }
        }

        val statisticalHighlights = mutableListOf<String>().apply {
            add("最终比分：${home.name} ${scoreH} - ${scoreA} ${away.name}")
            add("总进球数：${scoreH + scoreA}")
            add("关键事件数：${detail.events.size}")
            detail.events.count { it.type == EventType.GOAL }.let { add("进球数：$it") }
            detail.events.count { it.type == EventType.YELLOW }.let { add("黄牌：$it") }
            detail.events.count { it.type == EventType.RED }.let { add("红牌：$it") }
            if (detail.attendance > 0) add("上座人数：${detail.attendance}")
        }

        val grade = when {
            scoreH + scoreA >= 5 -> 'S'
            scoreH + scoreA >= 3 && kotlin.math.abs(scoreH - scoreA) <= 1 -> 'A'
            kotlin.math.abs(scoreH - scoreA) >= 3 -> 'B'
            scoreH + scoreA >= 2 -> 'B'
            scoreH == scoreA -> 'C'
            else -> 'D'
        }

        return PostMatchReport(
            manOfTheMatch = manOfTheMatch,
            turningPoint = turningPoint,
            tacticalSummary = tacticalSummary,
            statisticalHighlights = statisticalHighlights,
            grade = grade,
        )
    }
}
