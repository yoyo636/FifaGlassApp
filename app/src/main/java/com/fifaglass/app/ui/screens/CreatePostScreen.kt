package com.fifaglass.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.ForumRepository
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

@Composable
fun CreatePostScreen(
    onPublished: () -> Unit,
    onBack: () -> Unit,
) {
    val currentUser = remember { UserRepository.getCurrentUser() }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("chat") }
    var tagsText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val u = currentUser
    if (u == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先登录", color = GlassColors.textSecondary, fontSize = 15.sp)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("← 取消", color = GlassColors.textSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("发帖", color = GlassColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("分类", color = GlassColors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForumRepository.categories.drop(1).forEach { (code, label) ->
                    val selected = category == code
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (selected) Modifier.glass(10.dp) else Modifier)
                            .background(if (selected) Color.Transparent else Color.White.copy(alpha = 0.06f))
                            .clickable { category = code }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            label,
                            color = if (selected) GlassColors.accentMint else GlassColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text("标题", color = GlassColors.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; error = null },
                label = { Text("正文内容", color = GlassColors.textSecondary) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("标签（空格分隔）", color = GlassColors.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
        }
        Spacer(Modifier.height(12.dp))

        error?.let {
            Text(it, color = GlassColors.down, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (title.isBlank() || content.isBlank())
                        Color.Gray.copy(alpha = 0.2f)
                    else
                        GlassColors.accentMint.copy(alpha = 0.2f)
                )
                .clickable(enabled = title.isNotBlank() && content.isNotBlank()) {
                    if (title.length < 2) {
                        error = "标题至少 2 个字"
                    } else if (content.length < 5) {
                        error = "正文至少 5 个字"
                    } else {
                        val tags = tagsText.split(" ", "  ", "\t")
                            .map { it.trim() }.filter { it.isNotEmpty() }
                        ForumRepository.createPost(
                            authorId = u.id,
                            authorName = u.displayName,
                            authorAvatar = u.avatar,
                            title = title.trim(),
                            content = content.trim(),
                            category = category,
                            tags = tags,
                        )
                        onPublished()
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("发布", color = GlassColors.accentMint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = GlassColors.accentMint,
    unfocusedIndicatorColor = GlassColors.textSecondary.copy(alpha = 0.3f),
    focusedTextColor = GlassColors.textPrimary,
    unfocusedTextColor = GlassColors.textPrimary,
    cursorColor = GlassColors.accentMint,
    focusedLabelColor = GlassColors.accentMint,
    unfocusedLabelColor = GlassColors.textSecondary,
)
