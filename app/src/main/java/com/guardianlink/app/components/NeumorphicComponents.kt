package com.guardianlink.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianlink.app.ui.theme.*

/**
 * Draws the signature dual-tone S-curve fluid wave separating the
 * dark cosmic top section and the soft porcelain bottom section.
 */
@Composable
fun FluidWaveCanvas(
    modifier: Modifier = Modifier,
    topColor: Color = CosmicNavyDark,
    bottomColor: Color = PorcelainWhite,
    waveYRatio: Float = 0.42f,
    waveAmplitude: Float = 65f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val waveY = h * waveYRatio

        // Top dark cosmic background
        drawRect(color = topColor, size = size)

        // Fluid S-curve path for the bottom porcelain section
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, waveY - waveAmplitude)
            cubicTo(
                w * 0.25f, waveY - waveAmplitude * 1.6f,
                w * 0.45f, waveY + waveAmplitude * 1.5f,
                w, waveY + waveAmplitude * 0.4f
            )
            lineTo(w, h)
            close()
        }

        // Soft curved drop shadow under the wave edge
        drawIntoCanvas { canvas ->
            val shadowPaint = Paint().apply {
                color = Color(0x33000000)
                asFrameworkPaint().apply {
                    maskFilter = android.graphics.BlurMaskFilter(
                        16f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            canvas.drawPath(path, shadowPaint)
        }

        // Draw porcelain clay bottom wave
        drawPath(
            path = path,
            color = bottomColor
        )
    }
}

/**
 * Soft claymorphic squircle tile with multi-layered soft drop shadows,
 * subtle glossy rim highlight, and rich tactile press feel.
 */
@Composable
fun ClaySquircle(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    backgroundColor: Color = ClayTileSurface,
    borderColor: Color = ClayBorderHighlight,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = ClayShadowAmbient,
                spotColor = ClayShadowSpot
            )
            .then(clickableModifier),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(content = content)
    }
}

/**
 * Deep cosmic glass card for the top hero section.
 */
@Composable
fun CosmicHeroCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = Color(0x55000000),
                spotColor = Color(0x66000000)
            )
            .then(clickableModifier),
        shape = shape,
        color = CosmicCardElevated,
        border = BorderStroke(1.dp, CosmicBorderHighlight)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * Glowing Neon Progress Bar (inspired by the MyDocs storage bar in the reference image).
 */
@Composable
fun GlowProgressBar(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = Color(0xFF0F1020),
    gradientColors: List<Color> = listOf(ElectricBlueAccent, NeonCyanAccent)
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
        )
    }
}

/**
 * Top Island status notch matching the reference design header.
 */
@Composable
fun TopIslandNotch(
    userName: String = "Harshit",
    isOnline: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Status Icon (Cloud sync)
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = "Cloud status",
                    tint = if (isOnline) GlowingMint else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center Notch Pill with Avatar & Status
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .height(40.dp)
                .clickable { onAvatarClick?.invoke() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricBlueAccent, PastelPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) GlowingMint else OfflineGray)
                )
            }
        }

        // Right Menu Icon
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .size(40.dp)
                .clickable { onMenuClick?.invoke() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
