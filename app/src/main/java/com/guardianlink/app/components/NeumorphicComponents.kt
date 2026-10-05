package com.guardianlink.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianlink.app.ui.theme.*

/**
 * Liquid Glass Dual-Tone / Caustic Canvas.
 * Blends liquid neon caustics with translucent glass refraction.
 */
@Composable
fun FluidWaveCanvas(
    modifier: Modifier = Modifier,
    topColor: Color = LiquidCanvasDark,
    bottomColor: Color = LiquidCanvasLight,
    waveYRatio: Float = 0.40f,
    waveAmplitude: Float = 55f
) {
    LiquidGlassCanvas(modifier = modifier)
}

/**
 * Frosted Liquid Glass Squircle Tile (replaces ClaySquircle with Liquid Glass).
 */
@Composable
fun ClaySquircle(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    backgroundColor: Color = LiquidGlassBgLightElevated,
    borderColor: Color = LiquidGlassBorderLight,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        elevation = elevation,
        onClick = onClick,
        content = content
    )
}

/**
 * Liquid Glass Hero Card with Specular Sheen (replaces CosmicHeroCard).
 */
@Composable
fun CosmicHeroCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        elevation = 12.dp,
        glowColor = LiquidNeonCyan.copy(alpha = 0.25f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            content = content
        )
    }
}

/**
 * Glowing Liquid Progress Bar with Convex Glass Bead.
 */
@Composable
fun GlowProgressBar(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    gradientColors: List<Color> = listOf(LiquidNeonCyan, LiquidNeonPurple)
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(gradientColors))
        ) {
            // Specular bead on progress bar
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.6f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                )
            }
        }
    }
}

/**
 * Top Island status notch matching Liquid Glass header.
 */
@Composable
fun TopIslandNotch(
    userName: String = "Harshit",
    isOnline: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null
) {
    LiquidGlassTopNotch(
        userName = userName,
        isOnline = isOnline,
        onMenuClick = onMenuClick,
        onAvatarClick = onAvatarClick
    )
}
