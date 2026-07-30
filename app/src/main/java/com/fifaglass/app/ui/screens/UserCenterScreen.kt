package com.fifaglass.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.fifaglass.app.data.User
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

@Composable
fun UserCenterScreen(
    onOpenForum: () -> Unit,
    onOpenMyPosts: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var user by remember { mutableStateOf(UserRepository.getCurrentUser()) }
    val context = LocalContext.current
    var showEditProfile by remember { mutableStateOf(false) }

    val u = user
    if (u == null) {
        AuthScreen(onAuthSuccess = { user = UserRepository.getCurrentUser() })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("我的", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GlassColors.accentMint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        UserRepository.avatarEmoji(u.avatar),
                        fontSize = 28.sp
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        u.displayName,
                        color = GlassColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "@${u.username}",
                        color = GlassColors.textSecondary,
                        fontSize = 13.sp
                    )
                    if (u.bio.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            u.bio,
                            color = GlassColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("发帖", u.postCount)
                StatItem("评论", u.commentCount)
                StatItem("获赞", u.likeCount)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassColors.accentBlue.copy(alpha = 0.12f))
                        .clickable { showEditProfile = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("编辑资料", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassColors.accentGold.copy(alpha = 0.12f))
                        .clickable { onOpenMyPosts() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("我的帖子", color = GlassColors.accentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("社区", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("→", color = GlassColors.textSecondary, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            ActionRow("论坛广场", "和大家一起聊球") { onOpenForum() }
            ActionRow("我的帖子", "查看我发布的内容") { onOpenMyPosts() }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("应用", color = GlassColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            ActionRow("设置", "主题·通知·缓存") { onOpenSettings() }
            ActionRow("GitHub 仓库", "v2.1.0") {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yoyo636/FifaGlassApp"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassColors.down.copy(alpha = 0.1f))
                .clickable {
                    UserRepository.logout()
                    user = UserRepository.getCurrentUser()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("退出登录", color = GlassColors.down, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(100.dp))
    }

    if (showEditProfile) {
        EditProfileDialog(
            user = u,
            onDismiss = { showEditProfile = false },
            onSave = { name, avatar, bio, favCode ->
                UserRepository.updateProfile(
                    userId = u.id,
                    displayName = name,
                    avatar = avatar,
                    bio = bio,
                    favoriteTeamCode = favCode,
                )
                user = UserRepository.getCurrentUser()
                showEditProfile = false
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            color = GlassColors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = GlassColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = GlassColors.textSecondary, fontSize = 12.sp)
        }
        Text("→", color = GlassColors.textSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun EditProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var avatar by remember { mutableStateOf(user.avatar) }
    var bio by remember { mutableStateOf(user.bio) }
    var favCode by remember { mutableStateOf(user.favoriteTeamCode) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) { }
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(Modifier.fillMaxWidth(), corner = 24.dp) {
            Text("编辑资料", color = GlassColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text("头像", color = GlassColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserRepository.avatarOptions.forEach { (code, label) ->
                    val selected = avatar == code
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (selected) Modifier.glass(12.dp) else Modifier)
                            .background(if (selected) Color.Transparent else Color.White.copy(alpha = 0.06f))
                            .clickable { avatar = code }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(UserRepository.avatarEmoji(code), fontSize = 20.sp)
                            Text(label, color = if (selected) GlassColors.accentMint else GlassColors.textSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("昵称", color = GlassColors.textSecondary, fontSize = 13.sp)
            androidx.compose.material3.OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = GlassColors.accentMint,
                    unfocusedIndicatorColor = GlassColors.textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = GlassColors.textPrimary,
                    unfocusedTextColor = GlassColors.textPrimary,
                    cursorColor = GlassColors.accentMint,
                )
            )
            Spacer(Modifier.height(8.dp))

            Text("简介", color = GlassColors.textSecondary, fontSize = 13.sp)
            androidx.compose.material3.OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = GlassColors.accentMint,
                    unfocusedIndicatorColor = GlassColors.textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = GlassColors.textPrimary,
                    unfocusedTextColor = GlassColors.textPrimary,
                    cursorColor = GlassColors.accentMint,
                )
            )
            Spacer(Modifier.height(8.dp))

            Text("主队代码（如 BRA/ARG/FRA）", color = GlassColors.textSecondary, fontSize = 13.sp)
            androidx.compose.material3.OutlinedTextField(
                value = favCode,
                onValueChange = { favCode = it.uppercase().take(3) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = GlassColors.accentMint,
                    unfocusedIndicatorColor = GlassColors.textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = GlassColors.textPrimary,
                    unfocusedTextColor = GlassColors.textPrimary,
                    cursorColor = GlassColors.accentMint,
                )
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = GlassColors.textSecondary, fontSize = 14.sp)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassColors.accentMint.copy(alpha = 0.2f))
                        .clickable { onSave(displayName, avatar, bio, favCode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
