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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.ForumComment
import com.fifaglass.app.data.ForumPost
import com.fifaglass.app.data.ForumRepository
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

@Composable
fun PostDetailScreen(
    post: ForumPost,
    onBack: () -> Unit,
) {
    var currentPost by remember { mutableStateOf(post) }
    var comments by remember { mutableStateOf(ForumRepository.getCommentsForPost(post.id)) }
    var commentText by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    val currentUser = remember { UserRepository.getCurrentUser() }

    LaunchedEffect(post.id) {
        ForumRepository.incrementViewCount(post.id)
        currentPost = ForumRepository.getPostById(post.id) ?: post
        comments = ForumRepository.getCommentsForPost(post.id)
        liked = currentUser?.let { post.id in ForumRepository.getAllPosts().find { it.id == post.id }?.likedBy?.toList().orEmpty() } == true
    }

    val u = currentUser
    if (u == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先登录", color = GlassColors.textSecondary, fontSize = 15.sp)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("← 返回", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.weight(1f)
        ) {
            item(key = "post") {
                GlassCard(Modifier.fillMaxWidth(), corner = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(GlassColors.accentMint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(UserRepository.avatarEmoji(currentPost.authorAvatar), fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(currentPost.authorName, color = GlassColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(formatTimeAgo(currentPost.createdAt), color = GlassColors.textSecondary, fontSize = 12.sp)
                        }
                        val categoryLabel = ForumRepository.categories.find { it.first == currentPost.category }?.second ?: "其他"
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassColors.accentBlue.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(categoryLabel, color = GlassColors.accentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        currentPost.title,
                        color = GlassColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        currentPost.content,
                        color = GlassColors.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    if (currentPost.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            currentPost.tags.forEach { tag ->
                                Text("#$tag", color = GlassColors.accentViolet, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val isLiking = ForumRepository.togglePostLike(currentPost.id, u.id)
                                liked = isLiking
                                currentPost = ForumRepository.getPostById(currentPost.id) ?: currentPost
                            }
                        ) {
                            Text(
                                if (liked) "♥" else "♡",
                                color = if (liked) GlassColors.down else GlassColors.textSecondary,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("${currentPost.likeCount}", color = GlassColors.textSecondary, fontSize = 13.sp)
                        }
                        Text("评论 ${comments.size}", color = GlassColors.textSecondary, fontSize = 13.sp)
                        Text("阅读 ${currentPost.viewCount}", color = GlassColors.textSecondary, fontSize = 13.sp)
                    }
                }
            }

            item(key = "comment_header") {
                Text("评论 (${comments.size})", color = GlassColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            items(comments, key = { it.id }) { comment ->
                CommentItem(comment, u.id) {
                    ForumRepository.toggleCommentLike(comment.id, u.id)
                    comments = ForumRepository.getCommentsForPost(post.id)
                }
            }

            if (comments.isEmpty()) {
                item(key = "no_comments") {
                    Text("还没有评论，来说点什么吧", color = GlassColors.textSecondary, fontSize = 13.sp)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier.weight(1f).glass(18.dp),
                placeholder = { Text("写评论…", color = GlassColors.textSecondary, fontSize = 14.sp) },
                singleLine = true,
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
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (commentText.isBlank()) Color.Gray.copy(alpha = 0.2f)
                        else GlassColors.accentMint.copy(alpha = 0.2f)
                    )
                    .clickable(enabled = commentText.isNotBlank()) {
                        ForumRepository.addComment(
                            postId = post.id,
                            authorId = u.id,
                            authorName = u.displayName,
                            authorAvatar = u.avatar,
                            content = commentText.trim(),
                        )
                        commentText = ""
                        comments = ForumRepository.getCommentsForPost(post.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("发送", color = GlassColors.accentMint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun CommentItem(comment: ForumComment, currentUserId: String, onLikeToggle: () -> Unit) {
    var liked by remember(comment.id) {
        mutableStateOf(currentUserId in comment.likedBy)
    }

    GlassCard(Modifier.fillMaxWidth(), corner = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(GlassColors.accentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(UserRepository.avatarEmoji(comment.authorAvatar), fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.authorName, color = GlassColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (comment.authorId == currentUserId) {
                        Spacer(Modifier.width(6.dp))
                        Text("(我)", color = GlassColors.accentMint, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(formatTimeAgo(comment.createdAt), color = GlassColors.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(comment.content, color = GlassColors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (liked) "♥" else "♡",
                        color = if (liked) GlassColors.down else GlassColors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            onLikeToggle()
                            liked = !liked
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${comment.likeCount}", color = GlassColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
