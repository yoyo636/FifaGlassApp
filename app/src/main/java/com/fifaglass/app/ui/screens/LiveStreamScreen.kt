package com.fifaglass.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.StreamChannel
import com.fifaglass.app.data.StreamRepository
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LivePlatform(
    val name: String,
    val url: String,
    val icon: String,
    val desc: String,
    val color: Color,
)

private val domesticPlatforms = listOf(
    LivePlatform("CCTV-5 央视频", "https://tv.cctv.com/live/cctv5/m/", "📺", "央视体育频道 24小时直播", Color(0xFFFF375F)),
    LivePlatform("哔哩哔哩体育", "https://www.bilibili.com/v/game/sport/", "🎬", "B站体育赛事直播区", Color(0xFF00A1D6)),
    LivePlatform("优酷体育", "https://sports.youku.com/", "🎥", "优酷体育赛事频道", Color(0xFF1989FA)),
    LivePlatform("腾讯体育", "https://sports.qq.com/", "🏀", "腾讯体育赛事直播", Color(0xFF00C896)),
    LivePlatform("咪咕视频", "https://www.miguvideo.com/", "📱", "咪咕视频体育直播", Color(0xFFE9382A)),
    LivePlatform("抖音体育", "https://www.douyin.com/channel/sport", "🎵", "抖音体育赛事直播", Color(0xFF000000)),
    LivePlatform("快手体育", "https://www.kuaishou.com/profile/3x9x9x9x", "⚡", "快手体育赛事", Color(0xFFFF6600)),
)

@Composable
fun LiveStreamScreen(match: MatchInfo?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allChannels by remember { mutableStateOf<List<StreamChannel>>(StreamRepository.quickChannels()) }
    var filteredChannels by remember { mutableStateOf<List<StreamChannel>>(allChannels) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var favTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val list = withContext(Dispatchers.IO) {
                StreamRepository.loadAllAsync()
            }
            allChannels = list
            filteredChannels = if (match != null) {
                StreamRepository.matchChannelsForMatch(list, match)
            } else list
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    fun applyFilter(q: String) {
        filteredChannels = if (q.isBlank()) {
            if (match != null) StreamRepository.matchChannelsForMatch(allChannels, match)
            else allChannels
        } else {
            StreamRepository.searchChannels(allChannels, q)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (match != null) "直播观看" else "体育直播",
                    color = GlassColors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (match != null) "${match.homeName} vs ${match.awayName}"
                    else "选择平台 · 一键跳转观看",
                    color = GlassColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            if (refreshing) {
                CircularProgressIndicator(color = GlassColors.accentMint, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                AuroraRefreshButton {
                    refreshing = true
                    scope.launch {
                        try {
                            val list = withContext(Dispatchers.IO) {
                                StreamRepository.loadAllAsync()
                            }
                            allChannels = list
                            filteredChannels = if (match != null) {
                                StreamRepository.matchChannelsForMatch(list, match)
                            } else list
                            applyFilter(query)
                        } catch (_: Exception) {
                        } finally {
                            refreshing = false
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        PlatformCardList(domesticPlatforms) { platform ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(platform.url))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (match != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("国际直播源", color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("以下为国际体育频道，点击可在外部浏览器中打开", color = GlassColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        TextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                applyFilter(newQuery)
            },
            modifier = Modifier.fillMaxWidth().glass(20.dp),
            placeholder = { Text("搜索频道 / 国家", color = GlassColors.textSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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

        if (loading && allChannels.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val transition = rememberInfiniteTransition(label = "loading")
                    val scale by transition.animateFloat(
                        initialValue = 0.8f, targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "loading-scale"
                    )
                    CircularProgressIndicator(
                        color = GlassColors.accentMint,
                        modifier = Modifier.size(32.dp).scale(scale)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("正在获取体育直播源…", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val favUrls = remember(favTick) { StreamRepository.getFavoriteUrls() }
            val favChannels = allChannels.filter { it.url in favUrls }
            if (favChannels.isNotEmpty() && query.isBlank()) {
                AuroraFavCard(favChannels) { ch ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ch.url))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            val list = filteredChannels
            if (list.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("未找到频道", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id + "_" + it.url }) { ch ->
                        AuroraChannelRow(ch) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ch.url))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AuroraRefreshButton(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Aurora.cool(), alpha = 0.20f)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("⟳ 刷新", color = GlassColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlatformCardList(platforms: List<LivePlatform>, onClick: (LivePlatform) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("国内直播平台", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("点击直接跳转到对应平台观看直播", color = GlassColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            platforms.forEach { p ->
                PlatformRow(p) { onClick(p) }
            }
        }
    }
}

@Composable
private fun PlatformRow(platform: LivePlatform, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(platform.color.copy(alpha = 0.08f), platform.color.copy(alpha = 0.03f))
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(platform.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(platform.icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    platform.name,
                    color = GlassColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    platform.desc,
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("→", color = platform.color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuroraFavCard(channels: List<StreamChannel>, onSelect: (StreamChannel) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = GlassColors.accentGold.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFFF7E0).copy(alpha = 0.9f),
                        Color.White.copy(alpha = 0.85f),
                    )
                )
            )
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★", color = GlassColors.accentGold, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("收藏频道", color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${channels.size} 个", color = GlassColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                channels.take(5).forEach { ch ->
                    AuroraChannelRow(ch) { onSelect(ch) }
                }
            }
        }
    }
}

@Composable
private fun AuroraChannelRow(channel: StreamChannel, onClick: () -> Unit) {
    var isFav by remember(channel.url) { mutableStateOf(StreamRepository.isFavorite(channel.url)) }
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.82f)))
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel.logo.isNotEmpty()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Aurora.cool(), alpha = 0.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TV", fontSize = 14.sp, color = GlassColors.accentBlue, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = GlassColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${channel.country.ifEmpty { "—" }} · ${channel.quality.ifEmpty { "—" }}",
                    color = GlassColors.textSecondary,
                    fontSize = 11.sp
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable {
                        StreamRepository.toggleFavorite(channel.url)
                        isFav = !isFav
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isFav) "★" else "☆",
                    color = if (isFav) GlassColors.accentGold else GlassColors.textSecondary,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.accentGold.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("打开", color = GlassColors.accentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
