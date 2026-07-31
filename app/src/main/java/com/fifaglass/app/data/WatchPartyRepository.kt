package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf

data class WatchParty(
    val id: String,
    val matchId: String,
    val matchTitle: String,
    val hostName: String,
    val hostAvatar: String,
    val memberCount: Int,
    val maxMembers: Int,
    val createdAt: Long,
    val status: PartyStatus,
    val messages: List<PartyMessage>,
    val reactions: Map<String, Int>,
)

data class PartyMessage(
    val id: String,
    val senderName: String,
    val senderAvatar: String,
    val content: String,
    val timestamp: Long,
    val type: MessageType,
)

data class Reaction(
    val emoji: String,
    val count: Int,
    val recentUser: String,
)

enum class PartyStatus { WAITING, LIVE, FINISHED }
enum class MessageType { TEXT, GOAL, CARD, SUB, SYSTEM, REACTION }

object WatchPartyRepository {

    private val parties = mutableStateListOf<WatchParty>()
    private var prefs: SharedPreferences? = null

    private val presetParties = listOf(
        PartySeed("m_bra_arg", "巴西 vs 阿根廷", 12, PartyStatus.LIVE, "足球达人", "⚽"),
        PartySeed("m_rma_fcb", "皇马 vs 巴萨经典德比", 28, PartyStatus.LIVE, "解说员李明", "🎙"),
        PartySeed("m_liv_epl", "英超利物浦观赛团", 8, PartyStatus.WAITING, "狂热球迷", "🔥"),
        PartySeed("m_wc_final", "世界杯决赛派对", 45, PartyStatus.WAITING, "战术分析师", "📊"),
        PartySeed("m_ucl_sf", "欧冠半决赛观赛", 19, PartyStatus.LIVE, "资深球迷老王", "🏆"),
    )

    private data class PartySeed(
        val matchId: String,
        val title: String,
        val members: Int,
        val status: PartyStatus,
        val hostName: String,
        val hostAvatar: String,
    )

    private val autoMessagePool = listOf(
        AutoMsg("足球迷小王", "👨", "这个球太精彩了！"),
        AutoMsg("解说员张三", "🎙", "防守有问题啊，中场失位了"),
        AutoMsg("球迷老李", "🧔", "换人及时！教练这手换得妙"),
        AutoMsg("战术大师", "🎯", "裁判判罚有争议，这球该给牌"),
        AutoMsg("中立观众", "🙂", "这比赛真好看，势均力敌"),
        AutoMsg("红魔死忠", "😡", "必须赢下这场比赛！冲啊！"),
        AutoMsg("蓝月亮", "💙", "控球率占优但没用，得进球才行"),
        AutoMsg("足球少女", "👧", "好紧张啊心跳加速，手心都出汗了"),
        AutoMsg("数据控", "📊", "根据xG数据，主队应该早就领先了"),
        AutoMsg("老球迷", "👴", "看了三十年球，这种场面见多了"),
        AutoMsg("新球迷", "🧒", "越位是什么意思啊？谁能解释下"),
        AutoMsg("赌球分析师", "💰", "盘口变了，庄家看好客队反超"),
    )

    private data class AutoMsg(val name: String, val avatar: String, val content: String)

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("fifaglass_watch_party", Context.MODE_PRIVATE)
        if (parties.isEmpty()) {
            seedPresetParties()
        }
    }

    private fun seedPresetParties() {
        val now = System.currentTimeMillis()
        presetParties.forEachIndexed { index, seed ->
            val messages = mutableListOf<PartyMessage>()
            messages.add(
                PartyMessage(
                    id = "msg_${index}_sys_0",
                    senderName = "系统",
                    senderAvatar = "🤖",
                    content = "欢迎来到「${seed.title}」观赛派对！",
                    timestamp = now - 3600000,
                    type = MessageType.SYSTEM,
                )
            )
            messages.add(
                PartyMessage(
                    id = "msg_${index}_host_1",
                    senderName = seed.hostName,
                    senderAvatar = seed.hostAvatar,
                    content = "欢迎大家，比赛马上开始！准备好了吗",
                    timestamp = now - 3000000,
                    type = MessageType.TEXT,
                )
            )
            val chatCount = minOf(4, seed.members / 4)
            for (i in 0 until chatCount) {
                val template = autoMessagePool[(index * 4 + i) % autoMessagePool.size]
                messages.add(
                    PartyMessage(
                        id = "msg_${index}_auto_${i}",
                        senderName = template.name,
                        senderAvatar = template.avatar,
                        content = template.content,
                        timestamp = now - 2400000 + i * 60000,
                        type = MessageType.TEXT,
                    )
                )
            }
            if (seed.status == PartyStatus.LIVE) {
                messages.add(
                    PartyMessage(
                        id = "msg_${index}_goal_1",
                        senderName = "系统",
                        senderAvatar = "🤖",
                        content = "⚽ 第38分钟进球！比分变了！",
                        timestamp = now - 1200000,
                        type = MessageType.GOAL,
                    )
                )
                messages.add(
                    PartyMessage(
                        id = "msg_${index}_auto_goal",
                        senderName = "足球迷小王",
                        senderAvatar = "👨",
                        content = "进球了！！！太漂亮了！",
                        timestamp = now - 1180000,
                        type = MessageType.TEXT,
                    )
                )
            }
            val reactions = if (seed.status == PartyStatus.LIVE) {
                mapOf("⚽" to 8, "🔥" to 5, "👏" to 3, "😢" to 1)
            } else {
                mapOf("⚽" to 2, "🔥" to 1)
            }
            parties.add(
                WatchParty(
                    id = "party_${index + 1}",
                    matchId = seed.matchId,
                    matchTitle = seed.title,
                    hostName = seed.hostName,
                    hostAvatar = seed.hostAvatar,
                    memberCount = seed.members,
                    maxMembers = 50,
                    createdAt = now - 3600000,
                    status = seed.status,
                    messages = messages,
                    reactions = reactions,
                )
            )
        }
    }

    fun getActiveParties(): List<WatchParty> =
        parties.filter { it.status != PartyStatus.FINISHED }

    fun getAllParties(): List<WatchParty> = parties.toList()

    fun createParty(matchId: String, matchTitle: String, hostName: String, hostAvatar: String): WatchParty {
        val now = System.currentTimeMillis()
        val party = WatchParty(
            id = "party_${now}",
            matchId = matchId,
            matchTitle = matchTitle,
            hostName = hostName,
            hostAvatar = hostAvatar,
            memberCount = 1,
            maxMembers = 50,
            createdAt = now,
            status = PartyStatus.WAITING,
            messages = listOf(
                PartyMessage(
                    id = "msg_${now}_sys_0",
                    senderName = "系统",
                    senderAvatar = "🤖",
                    content = "$hostName 创建了「$matchTitle」观赛派对，快来加入吧！",
                    timestamp = now,
                    type = MessageType.SYSTEM,
                )
            ),
            reactions = emptyMap(),
        )
        parties.add(0, party)
        prefs?.edit()?.putString("last_created_party", party.id)?.apply()
        return party
    }

    fun joinParty(partyId: String, userName: String, userAvatar: String): Boolean {
        val index = parties.indexOfFirst { it.id == partyId }
        if (index < 0) return false
        val party = parties[index]
        if (party.memberCount >= party.maxMembers) return false
        val now = System.currentTimeMillis()
        val joinMsg = PartyMessage(
            id = "msg_${partyId}_${now}",
            senderName = "系统",
            senderAvatar = "🤖",
            content = "$userName 加入了派对",
            timestamp = now,
            type = MessageType.SYSTEM,
        )
        parties[index] = party.copy(
            memberCount = party.memberCount + 1,
            messages = party.messages + joinMsg,
        )
        return true
    }

    fun leaveParty(partyId: String, userName: String) {
        val index = parties.indexOfFirst { it.id == partyId }
        if (index < 0) return
        val party = parties[index]
        val now = System.currentTimeMillis()
        val leaveMsg = PartyMessage(
            id = "msg_${partyId}_${now}",
            senderName = "系统",
            senderAvatar = "🤖",
            content = "$userName 离开了派对",
            timestamp = now,
            type = MessageType.SYSTEM,
        )
        parties[index] = party.copy(
            memberCount = maxOf(1, party.memberCount - 1),
            messages = party.messages + leaveMsg,
        )
    }

    fun sendMessage(
        partyId: String,
        senderName: String,
        senderAvatar: String,
        content: String,
        type: MessageType = MessageType.TEXT,
    ): PartyMessage {
        val now = System.currentTimeMillis()
        val message = PartyMessage(
            id = "msg_${partyId}_${now}",
            senderName = senderName,
            senderAvatar = senderAvatar,
            content = content,
            timestamp = now,
            type = type,
        )
        val index = parties.indexOfFirst { it.id == partyId }
        if (index >= 0) {
            val party = parties[index]
            parties[index] = party.copy(messages = party.messages + message)
        }
        return message
    }

    fun addReaction(partyId: String, emoji: String, userName: String) {
        val index = parties.indexOfFirst { it.id == partyId }
        if (index < 0) return
        val party = parties[index]
        val currentCount = party.reactions[emoji] ?: 0
        parties[index] = party.copy(
            reactions = party.reactions + (emoji to currentCount + 1),
        )
        val now = System.currentTimeMillis()
        val reactionMsg = PartyMessage(
            id = "msg_${partyId}_${now}",
            senderName = userName,
            senderAvatar = "🙂",
            content = "$userName 发送了 $emoji",
            timestamp = now,
            type = MessageType.REACTION,
        )
        parties[index] = parties[index].copy(messages = parties[index].messages + reactionMsg)
    }

    fun getParty(partyId: String): WatchParty? = parties.find { it.id == partyId }

    fun generateAutoMessages(partyId: String, eventType: String, minute: Int): PartyMessage {
        val now = System.currentTimeMillis()
        val (content, type) = when (eventType.uppercase()) {
            "GOAL" -> "⚽ 第${minute}分钟进球！太精彩了！" to MessageType.GOAL
            "YELLOW" -> "🟨 第${minute}分钟黄牌！犯规战术" to MessageType.CARD
            "RED" -> "🟥 第${minute}分钟红牌！局势突变！" to MessageType.CARD
            "SUB" -> "🔄 第${minute}分钟换人调整，教练做出改变" to MessageType.SUB
            "KICKOFF" -> "🏁 比赛开始！大家准备好了吗" to MessageType.SYSTEM
            "HALF_TIME" -> "⏸ 半场结束，稍作休息" to MessageType.SYSTEM
            "FULL_TIME" -> "🏁 全场结束！感谢大家的陪伴" to MessageType.SYSTEM
            else -> "📢 第${minute}分钟比赛进行中" to MessageType.SYSTEM
        }
        val sender = autoMessagePool[(minute % autoMessagePool.size)]
        val message = PartyMessage(
            id = "msg_${partyId}_auto_${now}",
            senderName = sender.name,
            senderAvatar = sender.avatar,
            content = content,
            timestamp = now,
            type = type,
        )
        val index = parties.indexOfFirst { it.id == partyId }
        if (index >= 0) {
            val party = parties[index]
            parties[index] = party.copy(messages = party.messages + message)
        }
        return message
    }

    fun generateRandomChat(partyId: String): PartyMessage {
        val now = System.currentTimeMillis()
        val template = autoMessagePool.random()
        val message = PartyMessage(
            id = "msg_${partyId}_rand_${now}",
            senderName = template.name,
            senderAvatar = template.avatar,
            content = template.content,
            timestamp = now,
            type = MessageType.TEXT,
        )
        val index = parties.indexOfFirst { it.id == partyId }
        if (index >= 0) {
            val party = parties[index]
            parties[index] = party.copy(messages = party.messages + message)
        }
        return message
    }
}
