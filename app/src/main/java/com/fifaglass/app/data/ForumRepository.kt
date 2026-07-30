package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ForumPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val title: String,
    val content: String,
    val category: String,
    val createdAt: Long,
    val likeCount: Int,
    val likedBy: Set<String>,
    val viewCount: Int,
    val tags: List<String>,
)

data class ForumComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val createdAt: Long,
    val likeCount: Int,
    val likedBy: Set<String>,
)

object ForumRepository {

    private const val PREF = "fifaglass_forum"
    private const val KEY_POSTS = "posts_json"
    private const val KEY_COMMENTS = "comments_json"

    private lateinit var prefs: SharedPreferences

    val categories = listOf(
        "all" to "全部",
        "match" to "赛事讨论",
        "prediction" to "预测分析",
        "chat" to "侃球闲聊",
        "report" to "战报分享",
    )

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (getAllPosts().isEmpty()) seedMockPosts()
    }

    private fun seedMockPosts() {
        val mockUsers = UserRepository.getAllUsers()
        val now = System.currentTimeMillis()

        val mockPosts = listOf(
            ForumPost(
                id = UUID.randomUUID().toString(),
                authorId = mockUsers.getOrNull(0)?.id ?: "",
                authorName = mockUsers.getOrNull(0)?.displayName ?: "足球之王",
                authorAvatar = mockUsers.getOrNull(0)?.avatar ?: "soccer",
                title = "今晚巴西 vs 阿根廷，谁赢？",
                content = "南美德比大战在即！巴西主场作战，但梅西状态火热。大家觉得谁能拿下这场？\n\n巴西近期防守稳固，但进攻端缺少内马尔。阿根廷的中场控制力更强，梅西+劳塔罗的组合很有威胁。\n\n我预测 2:1 阿根廷小胜。",
                category = "match",
                createdAt = now - 3600000L * 2,
                likeCount = 15,
                likedBy = setOf("u2", "u3"),
                viewCount = 128,
                tags = listOf("巴西", "阿根廷", "南美德比"),
            ),
            ForumPost(
                id = UUID.randomUUID().toString(),
                authorId = mockUsers.getOrNull(1)?.id ?: "",
                authorName = mockUsers.getOrNull(1)?.displayName ?: "梅西的小迷弟",
                authorAvatar = mockUsers.getOrNull(1)?.avatar ?: "star",
                title = "FIFA排名预测系统准不准？亲测分析",
                content = "用了这个APP的预测系统一个月了，准确率大概在65%左右。\n\n强队打弱队的预测很准，但冷门比赛的预测偏差较大。贝叶斯推断模型在处理黑天鹅事件时确实有局限。\n\n不过蒙特卡洛模拟的进球数预测还挺有意思的，跟大家分享一下我的使用心得。",
                category = "prediction",
                createdAt = now - 3600000L * 8,
                likeCount = 23,
                likedBy = setOf("u0", "u3"),
                viewCount = 256,
                tags = listOf("预测系统", "数据分析"),
            ),
            ForumPost(
                id = UUID.randomUUID().toString(),
                authorId = mockUsers.getOrNull(2)?.id ?: "",
                authorName = mockUsers.getOrNull(2)?.displayName ?: "战术大师",
                authorAvatar = mockUsers.getOrNull(2)?.avatar ?: "chart",
                title = "433 vs 352 — 现代足球阵型终极对比",
                content = "近年来3后卫体系越来越流行，但433仍然是主流。\n\n433优势：边路进攻空间大，中场三人组控制力强\n352优势：翼卫提供宽度，中场人数优势，防守更灵活\n\n个人认为352更适合中场实力不强的球队，433更适合有顶级边锋的球队。大家怎么看？",
                category = "chat",
                createdAt = now - 3600000L * 24,
                likeCount = 31,
                likedBy = setOf("u0", "u1"),
                viewCount = 189,
                tags = listOf("战术", "阵型"),
            ),
            ForumPost(
                id = UUID.randomUUID().toString(),
                authorId = mockUsers.getOrNull(3)?.id ?: "",
                authorName = mockUsers.getOrNull(3)?.displayName ?: "熬夜看球星人",
                authorAvatar = mockUsers.getOrNull(3)?.avatar ?: "moon",
                title = "昨晚凌晨3点看球，值了！",
                content = "虽然困得要死，但那场比赛真的是这赛季最佳！\n\n最后10分钟连进2球逆转，心脏都要跳出来了。足球的魅力就在于此吧。\n\n有没有同款熬夜党？你们都怎么扛过第二天的？",
                category = "chat",
                createdAt = now - 3600000L * 12,
                likeCount = 18,
                likedBy = setOf("u0"),
                viewCount = 95,
                tags = listOf("熬夜", "逆转"),
            ),
        )

        val arr = JSONArray()
        mockPosts.forEach { arr.put(postToJson(it)) }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply()

        val mockComments = listOf(
            ForumComment(UUID.randomUUID().toString(), mockPosts[0].id, mockUsers.getOrNull(1)?.id ?: "", mockUsers.getOrNull(1)?.displayName ?: "梅西的小迷弟", mockUsers.getOrNull(1)?.avatar ?: "star", "梅西必胜！GOAT！", now - 3600000L, 5, setOf()),
            ForumComment(UUID.randomUUID().toString(), mockPosts[0].id, mockUsers.getOrNull(2)?.id ?: "", mockUsers.getOrNull(2)?.displayName ?: "战术大师", mockUsers.getOrNull(2)?.avatar ?: "chart", "巴西主场不好打，我觉得平局可能性更大", now - 3000000L, 3, setOf()),
            ForumComment(UUID.randomUUID().toString(), mockPosts[1].id, mockUsers.getOrNull(0)?.id ?: "", mockUsers.getOrNull(0)?.displayName ?: "足球之王", mockUsers.getOrNull(0)?.avatar ?: "soccer", "65%已经很不错了，博彩公司也就这个水平", now - 7000000L, 8, setOf()),
            ForumComment(UUID.randomUUID().toString(), mockPosts[2].id, mockUsers.getOrNull(0)?.id ?: "", mockUsers.getOrNull(0)?.displayName ?: "足球之王", mockUsers.getOrNull(0)?.avatar ?: "soccer", "433永远的神！", now - 20000000L, 2, setOf()),
        )
        val cArr = JSONArray()
        mockComments.forEach { cArr.put(commentToJson(it)) }
        prefs.edit().putString(KEY_COMMENTS, cArr.toString()).apply()
    }

    private fun postToJson(p: ForumPost): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("authorId", p.authorId)
        put("authorName", p.authorName)
        put("authorAvatar", p.authorAvatar)
        put("title", p.title)
        put("content", p.content)
        put("category", p.category)
        put("createdAt", p.createdAt)
        put("likeCount", p.likeCount)
        put("likedBy", JSONArray(p.likedBy))
        put("viewCount", p.viewCount)
        put("tags", JSONArray(p.tags))
    }

    private fun jsonToPost(o: JSONObject): ForumPost = ForumPost(
        id = o.optString("id"),
        authorId = o.optString("authorId"),
        authorName = o.optString("authorName"),
        authorAvatar = o.optString("authorAvatar"),
        title = o.optString("title"),
        content = o.optString("content"),
        category = o.optString("category"),
        createdAt = o.optLong("createdAt"),
        likeCount = o.optInt("likeCount"),
        likedBy = runCatching {
            val arr = o.optJSONArray("likedBy") ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }.getOrDefault(emptySet()),
        viewCount = o.optInt("viewCount"),
        tags = runCatching {
            val arr = o.optJSONArray("tags") ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }
        }.getOrDefault(emptyList()),
    )

    private fun commentToJson(c: ForumComment): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("postId", c.postId)
        put("authorId", c.authorId)
        put("authorName", c.authorName)
        put("authorAvatar", c.authorAvatar)
        put("content", c.content)
        put("createdAt", c.createdAt)
        put("likeCount", c.likeCount)
        put("likedBy", JSONArray(c.likedBy))
    }

    private fun jsonToComment(o: JSONObject): ForumComment = ForumComment(
        id = o.optString("id"),
        postId = o.optString("postId"),
        authorId = o.optString("authorId"),
        authorName = o.optString("authorName"),
        authorAvatar = o.optString("authorAvatar"),
        content = o.optString("content"),
        createdAt = o.optLong("createdAt"),
        likeCount = o.optInt("likeCount"),
        likedBy = runCatching {
            val arr = o.optJSONArray("likedBy") ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }.getOrDefault(emptySet()),
    )

    fun getAllPosts(): List<ForumPost> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY_POSTS, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { jsonToPost(arr.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList())
    }

    fun getPostsByCategory(category: String): List<ForumPost> {
        val posts = getAllPosts()
        return if (category == "all") posts.sortedByDescending { it.createdAt }
        else posts.filter { it.category == category }.sortedByDescending { it.createdAt }
    }

    fun getPostById(id: String): ForumPost? = getAllPosts().find { it.id == id }

    fun createPost(
        authorId: String,
        authorName: String,
        authorAvatar: String,
        title: String,
        content: String,
        category: String,
        tags: List<String> = emptyList(),
    ): ForumPost {
        val post = ForumPost(
            id = UUID.randomUUID().toString(),
            authorId = authorId,
            authorName = authorName,
            authorAvatar = authorAvatar,
            title = title,
            content = content,
            category = category,
            createdAt = System.currentTimeMillis(),
            likeCount = 0,
            likedBy = emptySet(),
            viewCount = 0,
            tags = tags,
        )
        val posts = getAllPosts().toMutableList()
        posts.add(post)
        val arr = JSONArray()
        posts.forEach { arr.put(postToJson(it)) }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply()
        UserRepository.incrementUserStat(authorId, postDelta = 1)
        return post
    }

    fun deletePost(id: String) {
        val posts = getAllPosts().filter { it.id != id }
        val arr = JSONArray()
        posts.forEach { arr.put(postToJson(it)) }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply()

        val comments = getAllComments().filter { it.postId != id }
        val cArr = JSONArray()
        comments.forEach { cArr.put(commentToJson(it)) }
        prefs.edit().putString(KEY_COMMENTS, cArr.toString()).apply()
    }

    fun togglePostLike(postId: String, userId: String): Boolean {
        val posts = getAllPosts().toMutableList()
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx < 0) return false
        val post = posts[idx]
        val likedBy = post.likedBy.toMutableSet()
        val isLiking = userId !in likedBy
        if (isLiking) likedBy.add(userId) else likedBy.remove(userId)
        val updated = post.copy(
            likedBy = likedBy,
            likeCount = likedBy.size,
        )
        posts[idx] = updated
        val arr = JSONArray()
        posts.forEach { arr.put(postToJson(it)) }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply()
        return isLiking
    }

    fun incrementViewCount(postId: String) {
        val posts = getAllPosts().toMutableList()
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx < 0) return
        posts[idx] = posts[idx].copy(viewCount = posts[idx].viewCount + 1)
        val arr = JSONArray()
        posts.forEach { arr.put(postToJson(it)) }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply()
    }

    fun getAllComments(): List<ForumComment> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY_COMMENTS, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { jsonToComment(arr.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList())
    }

    fun getCommentsForPost(postId: String): List<ForumComment> =
        getAllComments().filter { it.postId == postId }.sortedBy { it.createdAt }

    fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        authorAvatar: String,
        content: String,
    ): ForumComment {
        val comment = ForumComment(
            id = UUID.randomUUID().toString(),
            postId = postId,
            authorId = authorId,
            authorName = authorName,
            authorAvatar = authorAvatar,
            content = content,
            createdAt = System.currentTimeMillis(),
            likeCount = 0,
            likedBy = emptySet(),
        )
        val comments = getAllComments().toMutableList()
        comments.add(comment)
        val arr = JSONArray()
        comments.forEach { arr.put(commentToJson(it)) }
        prefs.edit().putString(KEY_COMMENTS, arr.toString()).apply()
        UserRepository.incrementUserStat(authorId, commentDelta = 1)
        return comment
    }

    fun toggleCommentLike(commentId: String, userId: String): Boolean {
        val comments = getAllComments().toMutableList()
        val idx = comments.indexOfFirst { it.id == commentId }
        if (idx < 0) return false
        val comment = comments[idx]
        val likedBy = comment.likedBy.toMutableSet()
        val isLiking = userId !in likedBy
        if (isLiking) likedBy.add(userId) else likedBy.remove(userId)
        comments[idx] = comment.copy(likedBy = likedBy, likeCount = likedBy.size)
        val arr = JSONArray()
        comments.forEach { arr.put(commentToJson(it)) }
        prefs.edit().putString(KEY_COMMENTS, arr.toString()).apply()
        return isLiking
    }

    fun getPostsByUser(userId: String): List<ForumPost> =
        getAllPosts().filter { it.authorId == userId }.sortedByDescending { it.createdAt }

    fun getCommentCountForUser(userId: String): Int =
        getAllComments().count { it.authorId == userId }
}
