package com.fifaglass.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.ForumPost
import com.fifaglass.app.data.ForumRepository
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassColors

@Composable
fun MyPostsScreen(
    onPostClick: (ForumPost) -> Unit,
    onBack: () -> Unit,
) {
    val user = remember { UserRepository.getCurrentUser() }
    val posts = remember(user?.id) {
        user?.let { ForumRepository.getPostsByUser(it.id) } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("← 返回", color = GlassColors.accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("我的帖子", color = GlassColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text("${posts.size} 篇帖子", color = GlassColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))

        if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有发过帖子，去论坛发第一帖吧", color = GlassColors.textSecondary, fontSize = 14.sp)
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
