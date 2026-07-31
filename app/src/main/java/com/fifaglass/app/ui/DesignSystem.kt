package com.fifaglass.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Aurora {
    fun primary(isDark: Boolean): Brush {
        val a = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        val b = if (isDark) Color(0xFF5E5CE6) else Color(0xFF5E5CE6)
        val c = if (isDark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
        return Brush.linearGradient(listOf(a, b, c))
    }
    fun success(): Brush = Brush.linearGradient(
        listOf(Color(0xFF00C896), Color(0xFF30D158))
    )
    fun danger(): Brush = Brush.linearGradient(
        listOf(Color(0xFFFF375F), Color(0xFFFF9F0A))
    )
    fun warm(): Brush = Brush.linearGradient(
        listOf(Color(0xFFFF9F0A), Color(0xFFFF375F))
    )
    fun cool(): Brush = Brush.linearGradient(
        listOf(Color(0xFF64D2FF), Color(0xFF0A84FF))
    )
    fun card(isDark: Boolean): Brush {
        return Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.08f else 0.92f),
                Color.White.copy(alpha = if (isDark) 0.04f else 0.78f),
            )
        )
    }
    fun tabBar(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.92f),
            Color(0xFFF2F2F7).copy(alpha = 0.88f),
        )
    )
    fun tabBarDark(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1A24).copy(alpha = 0.92f),
            Color(0xFF0F0F18).copy(alpha = 0.90f),
        )
    )
}

object Shadows {
    fun soft(d: Dp = 12.dp) = d
    fun medium(d: Dp = 20.dp) = d
    fun deep(d: Dp = 32.dp) = d
}

@Composable
fun Modifier.auroraCard(
    corner: Dp = 22.dp,
    isDark: Boolean = false,
    glow: Color? = null,
): Modifier {
    val shape = RoundedCornerShape(corner)
    val baseMod = this
        .shadow(if (glow != null) 18.dp else 8.dp, shape, ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.10f))
        .clip(shape)
        .background(Aurora.card(isDark))
    return if (glow != null) {
        baseMod.then(
            Modifier
                .shadow(24.dp, shape, ambientColor = glow.copy(alpha = 0.35f))
        )
    } else baseMod
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = Aurora.primary(isDark = false),
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "btn-scale"
    )
    val finalMod = if (enabled) modifier
        .clip(RoundedCornerShape(16.dp))
        .background(brush)
        .clickable(interactionSource = interaction, indication = null) { onClick() }
        .padding(vertical = 14.dp, horizontal = 24.dp)
    else modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color.Gray.copy(alpha = 0.3f))
        .padding(vertical = 14.dp, horizontal = 24.dp)

    Box(
        modifier = finalMod,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GlassColors.accentBlue,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn2-scale"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.16f))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 12.dp, horizontal = 18.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text,
            color = tint,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

