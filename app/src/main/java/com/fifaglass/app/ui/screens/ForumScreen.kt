package com.fifaglass.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.ForumPost
import com.fifaglass.app.data.ForumRepository
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

@Composable
fun ForumScreen(
    onPostClick: (ForumPost) -> Unit,
    onCreatePost: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("all") }
    var posts by remember { mutableStateOf(ForumRepository.getPostsByCategory(selectedCategory)) }
    var refreshTick by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("球迷社区", color = GlassColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${posts.size} 个帖子 · 一起聊球", color = GlassColors.textSecondary, fontSize = 13.sp)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassColors.accentMint.copy(alpha = 0.15f))
                    .clickable { onCreatePost() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("+ 发帖", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ForumRepository.categories) { (code, label) ->
                val selected = selectedCategory == code
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (selected) Modifier.glass(14.dp) else Modifier)
                        .background(
                            if (selected) Color.Transparent else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable {
                            selectedCategory = code
                            posts = ForumRepository.getPostsByCategory(code)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
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
        Spacer(Modifier.height(10.dp))

        if (posts.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("还没有帖子，快来发第一帖吧！", color = GlassColors.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    PostCard(post) { onPostClick(post) }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: ForumPost, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        corner = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(GlassColors.accentMint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(UserRepository.avatarEmoji(post.authorAvatar), fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.authorName, color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(formatTimeAgo(post.createdAt), color = GlassColors.textSecondary, fontSize = 11.sp)
            }
            val categoryLabel = ForumRepository.categories.find { it.first == post.category }?.second ?: "其他"
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.accentBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(categoryLabel, color = GlassColors.accentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            post.title,
            color = GlassColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            post.content,
            color = GlassColors.textSecondary,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        if (post.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                post.tags.take(3).forEach { tag ->
                    Text(
                        "#$tag",
                        color = GlassColors.accentViolet,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("♡ ${post.likeCount}", color = GlassColors.textSecondary, fontSize = 12.sp)
            val commentCount = remember(post.id) { ForumRepository.getCommentsForPost(post.id).size }
            Text("评论 $commentCount", color = GlassColors.textSecondary, fontSize = 12.sp)
            Text("阅读 ${post.viewCount}", color = GlassColors.textSecondary, fontSize = 12.sp)
        }
    }
}
