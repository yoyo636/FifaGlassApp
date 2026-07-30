package com.fifaglass.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fifaglass.app.data.UserRepository
import com.fifaglass.app.ui.GlassCard
import com.fifaglass.app.ui.GlassColors
import com.fifaglass.app.ui.glass

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(80.dp))

        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(GlassColors.accentMint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("⚽", fontSize = 36.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("FifaGlass", color = GlassColors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (isLogin) "欢迎回来，请登录" else "加入球迷社区",
            color = GlassColors.textSecondary,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(32.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .glass(20.dp)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(true to "登录", false to "注册").forEach { (isLoginTab, label) ->
                val selected = isLogin == isLoginTab
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (selected) Modifier.glass(16.dp) else Modifier)
                        .clickable { isLogin = isLoginTab; error = null }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) GlassColors.textPrimary else GlassColors.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text("用户名", color = GlassColors.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("密码", color = GlassColors.textSecondary) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(visible = !isLogin) {
                Column {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it; error = null },
                        label = { Text("昵称（可选）", color = GlassColors.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            error?.let {
                Text(it, color = GlassColors.down, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (loading) GlassColors.textSecondary.copy(alpha = 0.3f)
                        else GlassColors.accentMint.copy(alpha = 0.2f)
                    )
                    .clickable(enabled = !loading) {
                        loading = true
                        error = null
                        if (isLogin) {
                            val user = UserRepository.login(username.trim(), password)
                            if (user != null) {
                                onAuthSuccess()
                            } else {
                                error = "用户名或密码错误"
                            }
                        } else {
                            if (username.trim().length < 2) {
                                error = "用户名至少 2 个字符"
                            } else if (password.length < 4) {
                                error = "密码至少 4 位"
                            } else {
                                val user = UserRepository.register(
                                    username.trim(), password, displayName.trim()
                                )
                                if (user != null) {
                                    UserRepository.login(username.trim(), password)
                                    onAuthSuccess()
                                } else {
                                    error = "用户名已存在"
                                }
                            }
                        }
                        loading = false
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = GlassColors.accentMint,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (isLogin) "登录" else "注册并登录",
                        color = GlassColors.accentMint,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isLogin) {
            Text(
                "试试体验账号：footballking / 123456",
                color = GlassColors.textSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
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
