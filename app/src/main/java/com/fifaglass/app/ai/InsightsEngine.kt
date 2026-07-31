package com.fifaglass.app.ai

import com.fifaglass.app.data.MatchDetail
import com.fifaglass.app.data.Team
import com.fifaglass.app.rating.PredictionOutput
import java.util.UUID

data class InsightCard(
    val id: String,
    val title: String,
    val description: String,
    val category: InsightCategory,
    val icon: String,
    val severity: InsightSeverity,
    val stat: String,
)

enum class InsightCategory { STATISTICAL, TACTICAL, HISTORICAL, PERFORMANCE, PREDICTION }
enum class InsightSeverity { INFO, INTERESTING, SURPRISING, EXTRAORDINARY }

object InsightsEngine {

    fun generateInsights(home: Team, away: Team, prediction: PredictionOutput): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        if (prediction.upsetProbability > 0.25) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "冷门预警",
                description = "${away.name}有${(prediction.upsetProbability * 100).toInt()}%的概率爆冷击败${home.name}。排名差距${kotlin.math.abs(home.rank - away.rank)}位，历史数据显示此类对决冷门率偏高。",
                category = InsightCategory.PREDICTION,
                icon = "⚠️",
                severity = if (prediction.upsetProbability > 0.35) InsightSeverity.EXTRAORDINARY else InsightSeverity.SURPRISING,
                stat = "${(prediction.upsetProbability * 100).toInt()}%",
            ))
        }

        val totalXg = prediction.xgHome + prediction.xgAway
        if (totalXg > 3.0) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "进球大战预兆",
                description = "双方预期总进球${String.format("%.2f", totalXg)}，超过2.5球大球线。${home.name}进攻端预期${String.format("%.2f", prediction.xgHome)}球，${away.name}预期${String.format("%.2f", prediction.xgAway)}球。",
                category = InsightCategory.STATISTICAL,
                icon = "⚽",
                severity = InsightSeverity.INTERESTING,
                stat = String.format("%.2f", totalXg),
            ))
        }

        val probDiff = kotlin.math.abs(prediction.pHome - prediction.pAway)
        if (probDiff > 0.50) {
            val dominant = if (prediction.pHome > prediction.pAway) home.name else away.name
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "实力差距明显",
                description = "${dominant}胜率${(kotlin.math.max(prediction.pHome, prediction.pAway) * 100).toInt()}%，双方实力差距${(probDiff * 100).toInt()}%。预计比赛节奏将由强队掌控。",
                category = InsightCategory.PERFORMANCE,
                icon = "📊",
                severity = InsightSeverity.INFO,
                stat = "${(probDiff * 100).toInt()}%",
            ))
        }

        if (prediction.pDraw > 0.30) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "势均力敌",
                description = "平局概率${(prediction.pDraw * 100).toInt()}%，高于平均水平。双方实力接近，比赛可能陷入胶着。",
                category = InsightCategory.STATISTICAL,
                icon = "⚖️",
                severity = InsightSeverity.INTERESTING,
                stat = "${(prediction.pDraw * 100).toInt()}%",
            ))
        }

        if (prediction.bttsProbability > 0.60) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "双方进球概率高",
                description = "双方都有进球的概率为${(prediction.bttsProbability * 100).toInt()}。攻强守弱的对决特征明显。",
                category = InsightCategory.STATISTICAL,
                icon = "🎯",
                severity = InsightSeverity.INTERESTING,
                stat = "${(prediction.bttsProbability * 100).toInt()}%",
            ))
        }

        val rankDiff = kotlin.math.abs(home.rank - away.rank)
        if (rankDiff >= 20) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "排名悬殊对决",
                description = "${home.name}排名第${home.rank}，${away.name}排名第${away.rank}，差距${rankDiff}位。历史数据显示排名差距20+的对决爆冷率约15%。",
                category = InsightCategory.HISTORICAL,
                icon = "📈",
                severity = InsightSeverity.SURPRISING,
                stat = "差${rankDiff}位",
            ))
        }

        if (home.confederation != away.confederation) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "洲际对决",
                description = "${home.confederation}对阵${away.confederation}，不同洲际足球风格的碰撞。战术体系差异可能带来意外结果。",
                category = InsightCategory.TACTICAL,
                icon = "🌍",
                severity = InsightSeverity.INTERESTING,
                stat = "${home.confederation} vs ${away.confederation}",
            ))
        }

        cards.add(InsightCard(
            id = UUID.randomUUID().toString(),
            title = "最可能比分",
            description = "蒙特卡洛10000次模拟后，${prediction.likelyScore}出现频率最高，概率为${(prediction.likelyScoreProbability * 100).toInt()}%。",
            category = InsightCategory.PREDICTION,
            icon = "🔮",
            severity = InsightSeverity.INFO,
            stat = prediction.likelyScore,
        ))

        if (prediction.confidence >= 80) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "高信心预测",
                description = "模型信心指数${prediction.confidence}/100，数据支撑充分，预测可靠性高。",
                category = InsightCategory.PREDICTION,
                icon = "✅",
                severity = InsightSeverity.INFO,
                stat = "${prediction.confidence}/100",
            ))
        }

        return cards
    }

    fun generateLiveInsights(
        detail: MatchDetail, scoreHome: Int, scoreAway: Int, minute: Int,
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        val goals = detail.events.count { it.type == com.fifaglass.app.data.EventType.GOAL }
        val redCards = detail.events.count { it.type == com.fifaglass.app.data.EventType.RED }
        val yellowCards = detail.events.count { it.type == com.fifaglass.app.data.EventType.YELLOW }

        if (goals >= 3) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "进球盛宴",
                description = "已产生${goals}个进球，比赛精彩程度远超预期。平均每${minute / goals.coerceAtLeast(1)}分钟一球。",
                category = InsightCategory.STATISTICAL,
                icon = "🎉",
                severity = InsightSeverity.EXTRAORDINARY,
                stat = "${goals}球",
            ))
        }

        if (redCards > 0) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "红牌改变战局",
                description = "已出现${redCards}张红牌，人数优势方胜率大幅提升。历史数据显示少一人球队胜率下降40%。",
                category = InsightCategory.STATISTICAL,
                icon = "🟥",
                severity = InsightSeverity.SURPRISING,
                stat = "${redCards}红",
            ))
        }

        if (minute > 60 && kotlin.math.abs(scoreHome - scoreAway) <= 1) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "关键时刻",
                description = "比赛进入最后${90 - minute}分钟，比分接近。此时进球概率比平均值高出25%。",
                category = InsightCategory.PREDICTION,
                icon = "⏰",
                severity = InsightSeverity.INTERESTING,
                stat = "${90 - minute}分钟",
            ))
        }

        if (scoreHome + scoreAway == 0 && minute > 30) {
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "防守对决",
                description = "${minute}分钟无进球，双方防守表现突出。0-0概率随时间推移持续上升。",
                category = InsightCategory.STATISTICAL,
                icon = "🛡️",
                severity = InsightSeverity.INFO,
                stat = "0-0",
            ))
        }

        if (kotlin.math.abs(scoreHome - scoreAway) >= 2) {
            val leader = if (scoreHome > scoreAway) "主队" else "客队"
            cards.add(InsightCard(
                id = UUID.randomUUID().toString(),
                title = "大比分领先",
                description = "${leader}领先${kotlin.math.abs(scoreHome - scoreAway)}球，胜局基本已定。历史数据表明两球以上领先方胜率超过95%。",
                category = InsightCategory.PREDICTION,
                icon = "🏆",
                severity = InsightSeverity.INTERESTING,
                stat = "+${kotlin.math.abs(scoreHome - scoreAway)}",
            ))
        }

        return cards
    }
}
