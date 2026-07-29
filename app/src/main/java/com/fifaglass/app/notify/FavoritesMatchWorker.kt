package com.fifaglass.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fifaglass.app.MainActivity
import com.fifaglass.app.R
import com.fifaglass.app.data.FifaApi
import com.fifaglass.app.ui.Favorites

/**
 * 收藏球队比赛动态监控：每 15 分钟拉取一次，
 * 当发现比赛状态切换（即将开始 / 已开始 / 进球 / 终场）时
 * 推送一条干净、无广告的本地通知。
 */
class FavoritesMatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        ensureChannel(ctx)
        Favorites.init(ctx)
        val codes = Favorites.codes
        if (codes.isEmpty()) return Result.success()

        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allCodes = codes.joinToString(",") { it }
        val matches = runCatching {
            codes.flatMap { code ->
                FifaApi.fetchTeamMatchesRange(code, fromDays = -1, toDays = 1)
            }
        }.getOrElse {
            return Result.retry()
        }

        for (m in matches) {
            val hs = m.homeScore ?: -1
            val as_ = m.awayScore ?: -1
            val key = STATE_PREFIX + m.id
            val prev = prefs.getString(key, null)
            val now = "${m.status}|$hs|$as_"

            if (prev == null) {
                // 首次发现：不通知，仅记录（避免重复打扰）
                prefs.edit().putString(key, now).apply()
                continue
            }
            if (prev == now) continue

            // 状态变化：决定是否通知
            val teamCode = if (m.homeCode in codes) m.homeCode else
                if (m.awayCode in codes) m.awayCode else null
            if (teamCode == null) {
                prefs.edit().putString(key, now).apply()
                continue
            }

            val (title, body) = buildMessage(m, teamCode, prev, now)
            if (title != null && body != null) post(ctx, m.id.hashCode(), title, body)
            prefs.edit().putString(key, now).apply()
        }
        return Result.success()
    }

    private fun buildMessage(
        m: com.fifaglass.app.data.MatchInfo,
        teamCode: String,
        prev: String,
        now: String,
    ): Pair<String?, String?> {
        val parts = prev.split("|")
        val oldStatus = parts.getOrNull(0)?.toIntOrNull() ?: -1
        val newStatus = m.status

        val teamName = if (m.homeCode == teamCode) m.homeName else m.awayName
        val oppName = if (m.homeCode == teamCode) m.awayName else m.homeName

        // 状态转移语义
        return when {
            // 即将开始：开赛前 3 小时内的 "scheduled"，第一次出现
            newStatus == 1 && oldStatus != 1 -> "你关注的 ${teamCode} 即将开赛" to
                "${m.competition}\n${m.homeName} vs ${m.awayName}"

            // 开始：进入 live（status 非 0/1 即进行中）
            newStatus !in listOf(0, 1) && oldStatus in listOf(0, 1) -> "比赛开始：${teamCode}" to
                "${m.homeName} vs ${m.awayName}\n${m.competition}"

            // 比分变化（进行中状态）
            newStatus !in listOf(0, 1) && oldStatus == newStatus &&
                prev.substringAfter("|") != now.substringAfter("|") -> "比分变化：${teamCode}" to
                "${m.homeName} ${m.homeScore} - ${m.awayScore} ${m.awayName}（${m.matchTime}）"

            // 终场
            newStatus == 0 && oldStatus != 0 -> "终场：${teamCode}" to
                "${m.homeName} ${m.homeScore} - ${m.awayScore} ${m.awayName}\n${m.competition}"

            else -> null to null
        }
    }

    private fun post(context: Context, id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    companion object {
        const val CHANNEL_ID = "fifaglass_match_alerts"
        private const val PREFS_NAME = "fifaglass_prefs"
        private const val STATE_PREFIX = "match_state_"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "收藏比赛动态",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply {
                            description = "收藏球队比赛开始、进球、终场提醒"
                            setShowBadge(true)
                        }
                    )
                }
            }
        }
    }
}