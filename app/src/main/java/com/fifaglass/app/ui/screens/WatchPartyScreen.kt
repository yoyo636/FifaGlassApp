package com.fifaglass.app.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.MessageType
import com.fifaglass.app.data.PartyMessage
import com.fifaglass.app.data.PartyStatus
import com.fifaglass.app.data.WatchParty
import com.fifaglass.app.data.WatchPartyRepository
import com.fifaglass.app.ui.Aurora
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.GlassTheme
import com.fifaglass.app.ui.LocalGlassTheme
import com.fifaglass.app.ui.PrimaryButton
import com.fifaglass.app.ui.glowBorder
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val USER_NAME = "我"
private const val USER_AVATAR = "😎"

@Composable
fun WatchPartyScreen(
    matchId: String?,
    matchTitle: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalGlassTheme.current

    remember(context) { WatchPartyRepository.init(context); true }

    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(matchId) {
        if (matchId != null && selectedPartyId == null) {
            val existing = WatchPartyRepository.getActiveParties().find { it.matchId == matchId }
            if (existing != null) {
                selectedPartyId = existing.id
            }
        }
    }

    val currentParty by remember(selectedPartyId) {
        derivedStateOf {
            selectedPartyId?.let { WatchPartyRepository.getParty(it) }
        }
    }

    if (currentParty != null) {
        PartyDetailContent(
            party = currentParty!!,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    WatchPartyRepository.sendMessage(
                        partyId = currentParty!!.id,
                        senderName = USER_NAME,
                        senderAvatar = USER_AVATAR,
                        content = inputText.trim(),
                    )
                    inputText = ""
                }
            },
            onReaction = { emoji ->
                WatchPartyRepository.addReaction(currentParty!!.id, emoji, USER_NAME)
            },
            onBack = {
                if (matchId != null) onBack()
                else selectedPartyId = null
            },
            theme = theme,
        )
    } else {
        PartyListContent(
            matchId = matchId,
            matchTitle = matchTitle,
            onPartyClick = { selectedPartyId = it },
            onCreateParty = {
                val party = WatchPartyRepository.createParty(
                    matchId = matchId ?: "m_custom_${System.currentTimeMillis()}",
                    matchTitle = matchTitle ?: "自定义观赛派对",
                    hostName = USER_NAME,
                    hostAvatar = USER_AVATAR,
                )
                selectedPartyId = party.id
            },
            onBack = onBack,
            theme = theme,
        )
    }
}

@Composable
private fun PartyListContent(
    matchId: String?,
    matchTitle: String?,
    onPartyClick: (String) -> Unit,
    onCreateParty: () -> Unit,
    onBack: () -> Unit,
    theme: GlassTheme,
) {
    val parties by remember { derivedStateOf { WatchPartyRepository.getActiveParties() } }
    val totalOnline = parties.sumOf { it.memberCount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Aurora.warm())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("观赛派对", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("在线 $totalOnline 人 · ${parties.size} 个活跃派对", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉", fontSize = 22.sp)
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrimaryButton(
                text = if (matchId != null) "为「${matchTitle ?: "本场比赛"}」创建派对" else "创建观赛派对",
                onClick = onCreateParty,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(parties, key = { it.id }) { party ->
                PartyCard(
                    party = party,
                    onClick = { onPartyClick(party.id) },
                    theme = theme,
                )
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PartyCard(
    party: WatchParty,
    onClick: () -> Unit,
    theme: GlassTheme,
) {
    val statusInfo = getStatusInfo(party.status)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .glowBorder(glow = statusInfo.color, intensity = 0.2f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Aurora.primary(isDark = false)),
                contentAlignment = Alignment.Center
            ) {
                Text(party.hostAvatar, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(party.matchTitle, color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text("主持人: ${party.hostName}", color = theme.textSecondary, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusInfo.color.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(statusInfo.label, color = statusInfo.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (party.status == PartyStatus.LIVE) GlassColors.up else theme.textTertiary)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${party.memberCount}/${party.maxMembers} 人",
                color = theme.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            if (party.reactions.isNotEmpty()) {
                val topReactions = party.reactions.entries.sortedByDescending { it.value }.take(3)
                Row {
                    topReactions.forEach { (emoji, count) ->
                        Text("$emoji $count", color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                    }
                }
            }
            Text("加入 →", color = GlassColors.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PartyDetailContent(
    party: WatchParty,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onReaction: (String) -> Unit,
    onBack: () -> Unit,
    theme: GlassTheme,
) {
    val listState = rememberLazyListState()
    val statusInfo = getStatusInfo(party.status)

    LaunchedEffect(party.messages.size) {
        if (party.messages.isNotEmpty()) {
            listState.animateScrollToItem(party.messages.lastIndex)
        }
    }

    LaunchedEffect(party.id) {
        while (true) {
            delay(2500L + Random.nextLong(3500))
            WatchPartyRepository.generateRandomChat(party.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Aurora.danger())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(party.matchTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${party.memberCount} 人在线", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                if (party.status == PartyStatus.LIVE) {
                    val pulseTransition = rememberInfiniteTransition(label = "party_pulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "party_pulse_alpha",
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassColors.up.copy(alpha = pulseAlpha * 0.3f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GlassColors.up.copy(alpha = pulseAlpha))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("LIVE", color = GlassColors.up, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(party.messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, theme = theme)
            }
            item {
                Spacer(Modifier.height(4.dp))
            }
        }

        QuickReactionsBar(onReaction = onReaction, theme = theme)

        MessageInputBar(
            text = inputText,
            onTextChange = onInputChange,
            onSend = onSend,
            theme = theme,
        )
    }
}

@Composable
private fun MessageBubble(msg: PartyMessage, theme: GlassTheme) {
    when (msg.type) {
        MessageType.SYSTEM, MessageType.REACTION -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(msg.content, color = theme.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }

        MessageType.GOAL -> {
            val scaleTransition = rememberInfiniteTransition(label = "goal_scale")
            val scale by scaleTransition.animateFloat(
                initialValue = 0.97f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "goal_scale_val",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                GlassColors.accentGold.copy(alpha = 0.25f),
                                GlassColors.accentGold.copy(alpha = 0.1f),
                            )
                        )
                    )
                    .glowBorder(glow = GlassColors.accentGold, intensity = 0.4f)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg.senderAvatar, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(msg.senderName, color = GlassColors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(msg.content, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("⚽", fontSize = 28.sp)
                }
            }
        }

        MessageType.CARD, MessageType.SUB -> {
            val iconColor = if (msg.type == MessageType.CARD) GlassColors.accentGold else GlassColors.accentTeal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.1f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg.senderAvatar, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(msg.senderName, color = iconColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(msg.content, color = theme.textPrimary, fontSize = 13.sp)
                }
            }
        }

        MessageType.TEXT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Aurora.card(theme.isDark)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(msg.senderAvatar, fontSize = 18.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(msg.senderName, color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        if (msg.senderName == USER_NAME) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GlassColors.accentBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("我", color = GlassColors.accentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(msg.content, color = theme.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReactionsBar(
    onReaction: (String) -> Unit,
    theme: GlassTheme,
) {
    val reactions = listOf("⚽", "🔥", "👏", "😢", "😡")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        reactions.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(theme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onReaction(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    theme: GlassTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("发送消息...", color = theme.textTertiary, fontSize = 13.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.surfaceVariant,
                unfocusedContainerColor = theme.surfaceVariant,
                disabledContainerColor = theme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                cursorColor = GlassColors.accentBlue,
            ),
            shape = RoundedCornerShape(20.dp),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (text.isNotBlank()) Aurora.primary(isDark = false)
                    else SolidColor(theme.surfaceVariant)
                )
                .clickable(enabled = text.isNotBlank()) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↑",
                color = if (text.isNotBlank()) Color.White else theme.textTertiary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class StatusInfo(val label: String, val color: Color)

private fun getStatusInfo(status: PartyStatus): StatusInfo {
    return when (status) {
        PartyStatus.WAITING -> StatusInfo("等待中", GlassColors.accentBlue)
        PartyStatus.LIVE -> StatusInfo("LIVE", GlassColors.up)
        PartyStatus.FINISHED -> StatusInfo("已结束", GlassColors.textTertiary)
    }
}
