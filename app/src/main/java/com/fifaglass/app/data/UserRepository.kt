package com.fifaglass.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
    val bio: String,
    val favoriteTeamCode: String,
    val joinDate: Long,
    val postCount: Int = 0,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
)

data class UserSession(
    val userId: String?,
    val username: String?,
    val isLoggedIn: Boolean,
)

object UserRepository {

    private const val PREF = "fifaglass_users"
    private const val KEY_USERS = "users_json"
    private const val KEY_SESSION = "current_session"
    private const val KEY_PASS_PREFIX = "pass_"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (getAllUsers().isEmpty()) seedMockUsers()
    }

    private fun seedMockUsers() {
        val mockUsers = listOf(
            User(UUID.randomUUID().toString(), "footballking", "足球之王", "soccer", "20年老球迷，皇马死忠", "ESP", System.currentTimeMillis() - 86400000L * 365, 12, 45, 89),
            User(UUID.randomUUID().toString(), "messi_fan", "梅西的小迷弟", "star", " Messi > Ronaldo，不服来辩", "ARG", System.currentTimeMillis() - 86400000L * 200, 8, 32, 67),
            User(UUID.randomUUID().toString(), "tactical_master", "战术大师", "chart", "442才是永恒的经典", "BRA", System.currentTimeMillis() - 86400000L * 100, 15, 52, 103),
            User(UUID.randomUUID().toString(), "night_owl", "熬夜看球星人", "moon", "世界杯期间昼夜颠倒", "FRA", System.currentTimeMillis() - 86400000L * 50, 3, 18, 25),
        )
        val arr = JSONArray()
        mockUsers.forEach { arr.put(userToJson(it)) }
        prefs.edit().putString(KEY_USERS, arr.toString()).apply()
        mockUsers.forEach { 
            prefs.edit().putString(KEY_PASS_PREFIX + it.username, "123456").apply()
        }
    }

    private fun userToJson(u: User): JSONObject = JSONObject().apply {
        put("id", u.id)
        put("username", u.username)
        put("displayName", u.displayName)
        put("avatar", u.avatar)
        put("bio", u.bio)
        put("favoriteTeamCode", u.favoriteTeamCode)
        put("joinDate", u.joinDate)
        put("postCount", u.postCount)
        put("commentCount", u.commentCount)
        put("likeCount", u.likeCount)
    }

    private fun jsonToUser(o: JSONObject): User = User(
        id = o.optString("id"),
        username = o.optString("username"),
        displayName = o.optString("displayName"),
        avatar = o.optString("avatar"),
        bio = o.optString("bio"),
        favoriteTeamCode = o.optString("favoriteTeamCode"),
        joinDate = o.optLong("joinDate"),
        postCount = o.optInt("postCount"),
        commentCount = o.optInt("commentCount"),
        likeCount = o.optInt("likeCount"),
    )

    fun getAllUsers(): List<User> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { jsonToUser(arr.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList())
    }

    fun getUserById(id: String): User? = getAllUsers().find { it.id == id }

    fun getUserByUsername(username: String): User? = getAllUsers().find { it.username == username }

    fun register(username: String, password: String, displayName: String): User? {
        if (username.isBlank() || password.length < 4) return null
        if (getUserByUsername(username) != null) return null
        val user = User(
            id = UUID.randomUUID().toString(),
            username = username,
            displayName = displayName.ifBlank { username },
            avatar = "person",
            bio = "",
            favoriteTeamCode = "",
            joinDate = System.currentTimeMillis(),
        )
        val users = getAllUsers().toMutableList()
        users.add(user)
        val arr = JSONArray()
        users.forEach { arr.put(userToJson(it)) }
        prefs.edit()
            .putString(KEY_USERS, arr.toString())
            .putString(KEY_PASS_PREFIX + username, password)
            .apply()
        return user
    }

    fun login(username: String, password: String): User? {
        val storedPass = prefs.getString(KEY_PASS_PREFIX + username, null) ?: return null
        if (storedPass != password) return null
        val user = getUserByUsername(username) ?: return null
        prefs.edit().putString(KEY_SESSION, user.id).apply()
        return user
    }

    fun logout() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun getCurrentUser(): User? {
        if (!::prefs.isInitialized) return null
        val sessionId = prefs.getString(KEY_SESSION, null) ?: return null
        return getUserById(sessionId)
    }

    fun isLoggedIn(): Boolean = getCurrentUser() != null

    fun updateProfile(
        userId: String,
        displayName: String? = null,
        avatar: String? = null,
        bio: String? = null,
        favoriteTeamCode: String? = null,
    ): User? {
        val users = getAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.id == userId }
        if (idx < 0) return null
        val old = users[idx]
        val updated = old.copy(
            displayName = displayName ?: old.displayName,
            avatar = avatar ?: old.avatar,
            bio = bio ?: old.bio,
            favoriteTeamCode = favoriteTeamCode ?: old.favoriteTeamCode,
        )
        users[idx] = updated
        val arr = JSONArray()
        users.forEach { arr.put(userToJson(it)) }
        prefs.edit().putString(KEY_USERS, arr.toString()).apply()
        return updated
    }

    fun incrementUserStat(userId: String, postDelta: Int = 0, commentDelta: Int = 0, likeDelta: Int = 0) {
        val users = getAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.id == userId }
        if (idx < 0) return
        val old = users[idx]
        val updated = old.copy(
            postCount = old.postCount + postDelta,
            commentCount = old.commentCount + commentDelta,
            likeCount = old.likeCount + likeDelta,
        )
        users[idx] = updated
        val arr = JSONArray()
        users.forEach { arr.put(userToJson(it)) }
        prefs.edit().putString(KEY_USERS, arr.toString()).apply()
    }

    val avatarOptions = listOf(
        "person" to "默认",
        "soccer" to "足球",
        "star" to "球星",
        "chart" to "战术板",
        "moon" to "夜猫",
        "fire" to "热血",
        "crown" to "皇冠",
        "heart" to "爱心",
    )

    fun avatarEmoji(avatar: String): String = when (avatar) {
        "person" -> "👤"
        "soccer" -> "⚽"
        "star" -> "⭐"
        "chart" -> "📊"
        "moon" -> "🌙"
        "fire" -> "🔥"
        "crown" -> "👑"
        "heart" -> "❤️"
        else -> "👤"
    }
}
