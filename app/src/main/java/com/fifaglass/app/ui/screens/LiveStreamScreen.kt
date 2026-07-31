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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
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

private fun isHlsUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains(".m3u?") || lower.contains(".m3u8?")
}

@Composable
fun LiveStreamScreen(match: MatchInfo?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                    else "实时体育频道 · 快速加载",
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

        if (current != null && isHlsUrl(current.url)) {
            VideoPlayerCard(current)
            Spacer(Modifier.height(12.dp))
        } else if (current != null && !isHlsUrl(current.url)) {
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
                AuroraFavCard(favChannels, selectedChannel?.url) { selectedChannel = it }
                Spacer(Modifier.height(10.dp))
            }

            val list = filteredChannels
            if (list.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("未找到频道", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.url }) { ch ->
                        AuroraChannelRow(ch, selectedChannel?.url == ch.url) {
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
private fun AuroraFavCard(channels: List<StreamChannel>, selectedUrl: String?, onSelect: (StreamChannel) -> Unit) {
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
                    AuroraChannelRow(ch, selectedUrl == ch.url) { onSelect(ch) }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerCard(channel: StreamChannel) {
    val context = LocalContext.current

    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    val exoPlayer = remember(channel.url) {
        runCatching {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(channel.userAgent.ifEmpty { "Mozilla/5.0 (Linux; Android 11) ExoPlayer" })
                .setDefaultRequestProperties(
                    buildMap {
                        if (channel.referrer.isNotEmpty()) put("Referer", channel.referrer)
                    }
                )
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(10000)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            ExoPlayer.Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(5000, 30000, 1000, 1000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()
                )
                .build().apply {
                    val mediaItem = MediaItem.Builder()
                        .setUri(channel.url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }
        }.getOrNull()
    }

    LaunchedEffect(channel.url) {
        playbackError = null
        retryCount = 0
    }

    DisposableEffect(channel.url, exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "网络连接失败，正在重试…"
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "不支持的视频格式"
                    else -> "播放失败: ${error.errorCodeName}"
                }
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
        }
    }

    LaunchedEffect(playbackError) {
        if (playbackError != null && retryCount < 3) {
            kotlinx.coroutines.delay(2000L)
            retryCount++
            exoPlayer?.prepare()
        }
    }

    DisposableEffect(channel.url) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1A1A2E), Color(0xFF0F0F18))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "live-pulse")
                val pulseAlpha by transition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "pulse-alpha"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF375F).copy(alpha = pulseAlpha))
                )
                Spacer(Modifier.width(8.dp))
                Text("LIVE", color = Color(0xFFFF375F), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(channel.quality.ifEmpty { "Live" }, color = GlassColors.accentGold, fontSize = 12.sp)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (playbackError != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(playbackError ?: "", color = Color.White, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(GlassColors.accentMint.copy(alpha = 0.25f))
                                        .clickable {
                                            retryCount++
                                            exoPlayer.prepare()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("重试", color = GlassColors.accentMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("播放器初始化失败", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(channel.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (channel.country.isNotEmpty()) {
                    Text("${channel.country} · ${channel.category}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (channel.referrer.isNotEmpty()) {
                        Text("需 Referer", color = GlassColors.accentGold, fontSize = 10.sp)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.url))
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("外部播放器", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraChannelRow(channel: StreamChannel, isSelected: Boolean, onClick: () -> Unit) {
    var isFav by remember(channel.url) { mutableStateOf(StreamRepository.isFavorite(channel.url)) }
    val cardMod = if (isSelected) {
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = GlassColors.accentMint.copy(alpha = 0.3f))
    } else {
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
    }
    Box(
        cardMod
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Aurora.success()
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.82f))),
                alpha = if (isSelected) 0.15f else 1f
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
