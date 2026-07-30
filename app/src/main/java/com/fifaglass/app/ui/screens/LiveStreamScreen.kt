package com.fifaglass.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.fifaglass.app.data.MatchInfo
import com.fifaglass.app.data.StreamChannel
import com.fifaglass.app.data.StreamRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LiveStreamScreen(match: MatchInfo?) {
    val context = LocalContext.current
    var allChannels by remember { mutableStateOf<List<StreamChannel>>(StreamRepository.quickChannels()) }
    var filteredChannels by remember { mutableStateOf<List<StreamChannel>>(allChannels) }
    var selectedChannel by remember { mutableStateOf<StreamChannel?>(null) }
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

    val current = selectedChannel

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (match != null) "直播观看" else "体育直播",
            color = GlassColors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (match != null) "${match.homeName} vs ${match.awayName}"
                else "实时体育频道 · 快速加载",
                color = GlassColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            if (refreshing) {
                CircularProgressIndicator(color = GlassColors.accentMint, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassColors.accentBlue.copy(alpha = 0.15f))
                        .clickable {
                            refreshing = true
                            // fire-and-forget refresh
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("刷新", color = GlassColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (current != null && current.url.endsWith(".m3u8")) {
            VideoPlayerCard(current)
            Spacer(Modifier.height(12.dp))
        } else if (current != null && !current.url.endsWith(".m3u8")) {
            LaunchedEffect(current.url) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(current.url))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
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
                    CircularProgressIndicator(color = GlassColors.accentMint, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("正在获取体育直播源…", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val favUrls = remember(favTick) { StreamRepository.getFavoriteUrls() }
            val favChannels = allChannels.filter { it.url in favUrls }
            if (favChannels.isNotEmpty() && query.isBlank()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("收藏频道", color = GlassColors.accentGold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${favChannels.size} 个", color = GlassColors.textSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        favChannels.take(5).forEach { ch ->
                            ChannelRow(ch, selectedChannel?.url == ch.url) { selectedChannel = ch }
                        }
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.url }) { ch ->
                        ChannelRow(ch, selectedChannel?.url == ch.url) {
                            selectedChannel = ch
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun VideoPlayerCard(channel: StreamChannel) {
    val context = LocalContext.current

    val exoPlayer = remember(channel.url) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(channel.url)
                val mediaSource = HlsMediaSource.Factory(DefaultDataSource.Factory(context))
                    .createMediaSource(mediaItem)
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
    }

    DisposableEffect(channel.url) {
        onDispose {
            exoPlayer?.release()
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正在播放", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(channel.quality.ifEmpty { "Live" }, color = GlassColors.accentGold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("播放器初始化失败", color = Color.White, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(channel.name, color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (channel.country.isNotEmpty()) {
            Text("${channel.country} · ${channel.category}", color = GlassColors.textSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel.referrer.isNotEmpty()) {
                Text("需 Referer", color = GlassColors.accentGold, fontSize = 10.sp)
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.15f))
                    .clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.url))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("外部播放器", color = GlassColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: StreamChannel, isSelected: Boolean, onClick: () -> Unit) {
    var isFav by remember(channel.url) { mutableStateOf(StreamRepository.isFavorite(channel.url)) }
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        corner = 16.dp
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
                        .background(GlassColors.accentMint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TV", fontSize = 14.sp, color = GlassColors.accentMint, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = if (isSelected) GlassColors.accentMint else GlassColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
            if (isSelected) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(GlassColors.accentMint.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("播放中", color = GlassColors.accentMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(GlassColors.accentGold.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("播放", color = GlassColors.accentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
