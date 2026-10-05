package com.guardianlink.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianlink.app.ui.theme.AlertRed
import com.guardianlink.app.ui.theme.AlertRedDark
import com.guardianlink.app.ui.theme.PulseRingColor
import com.guardianlink.app.ui.theme.PulseRingOuterColor

@Composable
fun SOSButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "sos_radar")

    // Outer radar wave 1
    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_alpha"
    )

    // Outer radar wave 2 (offset)
    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 450, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 450, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_alpha"
    )

    // Breathing pulse for core button
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else corePulse,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press_scale"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar Wave 1
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(wave1Scale)
                .clip(CircleShape)
                .background(AlertRed.copy(alpha = wave1Alpha))
        )

        // Radar Wave 2
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(wave2Scale)
                .clip(CircleShape)
                .background(AlertRed.copy(alpha = wave2Alpha))
        )

        // Core Glowing SOS Button
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEF4444),
                            AlertRed,
                            AlertRedDark
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Emergency SOS",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "EMERGENCY",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "TAP TO TRIGGER",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}