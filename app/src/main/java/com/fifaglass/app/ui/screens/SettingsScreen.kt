package com.fifaglass.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.Cache
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors

object SettingsStore {
    private const val PREF = "fifaglass_settings"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    var themeMode: String
        get() = if (::prefs.isInitialized) prefs.getString("theme_mode", "dark") ?: "dark" else "dark"
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("theme_mode", v).apply() }

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1.0f)
        set(v) { prefs.edit().putFloat("font_scale", v).apply() }

    var notifyMatchStart: Boolean
        get() = prefs.getBoolean("notify_match_start", true)
        set(v) { prefs.edit().putBoolean("notify_match_start", v).apply() }

    var notifyGoals: Boolean
        get() = prefs.getBoolean("notify_goals", true)
        set(v) { prefs.edit().putBoolean("notify_goals", v).apply() }

    var notifyFullTime: Boolean
        get() = prefs.getBoolean("notify_full_time", false)
        set(v) { prefs.edit().putBoolean("notify_full_time", v).apply() }

    var notifyUpset: Boolean
        get() = prefs.getBoolean("notify_upset", true)
        set(v) { prefs.edit().putBoolean("notify_upset", v).apply() }

    var autoRefresh: Boolean
        get() = prefs.getBoolean("auto_refresh", true)
        set(v) { prefs.edit().putBoolean("auto_refresh", v).apply() }

    var dataSaver: Boolean
        get() = prefs.getBoolean("data_saver", false)
        set(v) { prefs.edit().putBoolean("data_saver", v).apply() }

    var dataUsageBytes: Long
        get() = prefs.getLong("data_usage", 0L)
        set(v) { prefs.edit().putLong("data_usage", v).apply() }

    fun addDataUsage(bytes: Long) {
        dataUsageBytes = dataUsageBytes + bytes
    }

    var language: String
        get() = prefs.getString("language", "zh") ?: "zh"
        set(v) { prefs.edit().putString("language", v).apply() }

    var lastOpenTime: Long
        get() = prefs.getLong("last_open", 0L)
        set(v) { prefs.edit().putLong("last_open", v).apply() }

    var openCount: Int
        get() = prefs.getInt("open_count", 0)
        set(v) { prefs.edit().putInt("open_count", v).apply() }

    fun recordOpen() {
        openCount = openCount + 1
        lastOpenTime = System.currentTimeMillis()
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(SettingsStore.themeMode) }
    var fontScale by remember { mutableFloatStateOf(SettingsStore.fontScale) }
    var notifyStart by remember { mutableStateOf(SettingsStore.notifyMatchStart) }
    var notifyGoals by remember { mutableStateOf(SettingsStore.notifyGoals) }
    var notifyFT by remember { mutableStateOf(SettingsStore.notifyFullTime) }
    var notifyUpset by remember { mutableStateOf(SettingsStore.notifyUpset) }
    var autoRefresh by remember { mutableStateOf(SettingsStore.autoRefresh) }
    var dataSaver by remember { mutableStateOf(SettingsStore.dataSaver) }
    var cacheSize by remember { mutableIntStateOf(Cache.estimateSize()) }
    var language by remember { mutableStateOf(SettingsStore.language) }
    var dataUsage by remember { mutableLongStateOf(SettingsStore.dataUsageBytes) }
    var showAbout by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("设置", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("个性化 · 通知 · 数据 · 关于", color = GlassColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("外观", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("主题模式", color = GlassColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "深色", "light" to "浅色", "auto" to "跟随系统").forEach { (mode, label) ->
                    val selected = themeMode == mode
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GlassColors.accentBlue.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f))
                            .clickable { themeMode = mode; SettingsStore.themeMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) GlassColors.accentBlue else GlassColors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("字体大小", color = GlassColors.textSecondary, fontSize = 13.sp)
            Text("${"%.1f".format(fontScale)}x", color = GlassColors.accentMint, fontSize = 12.sp)
            Slider(
                value = fontScale,
                onValueChange = { fontScale = it; SettingsStore.fontScale = it },
                valueRange = 0.8f..1.5f
            )
            Spacer(Modifier.height(8.dp))
            Text("语言 / Language", color = GlassColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh" to "中文", "en" to "English").forEach { (code, label) ->
                    val selected = language == code
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GlassColors.accentMint.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                            .clickable { language = code; SettingsStore.language = code }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = if (selected) GlassColors.accentMint else GlassColors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("通知偏好", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SwitchRow("比赛开始提醒", notifyStart) { notifyStart = it; SettingsStore.notifyMatchStart = it }
            SwitchRow("进球提醒", notifyGoals) { notifyGoals = it; SettingsStore.notifyGoals = it }
            SwitchRow("终场提醒", notifyFT) { notifyFT = it; SettingsStore.notifyFullTime = it }
            SwitchRow("冷门预警通知", notifyUpset) { notifyUpset = it; SettingsStore.notifyUpset = it }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("数据与缓存", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SwitchRow("自动刷新（30秒）", autoRefresh) { autoRefresh = it; SettingsStore.autoRefresh = it }
            SwitchRow("省流模式", dataSaver) { dataSaver = it; SettingsStore.dataSaver = it }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("缓存大小", color = GlassColors.textSecondary, fontSize = 13.sp)
                Text(formatSize(cacheSize), color = GlassColors.accentMint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("累计流量", color = GlassColors.textSecondary, fontSize = 13.sp)
                Text(formatSizeLong(dataUsage), color = GlassColors.accentGold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.down.copy(alpha = 0.15f))
                    .clickable { Cache.clear(); cacheSize = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("清除缓存", color = GlassColors.down, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关于", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("v2.1.0", color = GlassColors.textSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            AboutRow("版本号", "2.1.0 (build 8)")
            AboutRow("数据来源", "FIFA 官方接口")
            AboutRow("开源协议", "MIT License")
            AboutRow("启动次数", "${SettingsStore.openCount}")
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.15f))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yoyo636/FifaGlassApp"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("GitHub 仓库", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GlassColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = GlassColors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatSize(bytes: Int): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) "${"%.1f".format(kb)} KB" else "${"%.1f".format(kb / 1024)} MB"
}

private fun formatSizeLong(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> "${"%.1f".format(kb)} KB"
        kb < 1024 * 1024 -> "${"%.1f".format(kb / 1024)} MB"
        else -> "${"%.2f".format(kb / 1024 / 1024)} GB"
    }
}
