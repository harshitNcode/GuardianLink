package com.guardianlink.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianlink.app.ui.theme.*
import kotlin.math.sin

/**
 * Ambient Liquid Caustic Canvas.
 * In Light Mode: Recreates the warm studio pearlescent surface with delicate prismatic
 * cyan/violet/magenta light refractions seen in reference photo.
 * In Dark Mode: Deep obsidian canvas with glowing neon light blooms.
 */
@Composable
fun LiquidGlassCanvas(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkMode.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "caustics")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "caustic_phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base Canvas Fill
        drawRect(color = if (isDark) LiquidCanvasDark else LiquidCanvasLight)

        if (!isDark) {
            // Soft studio directional ambient lighting from top-left
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.70f),
                        Color(0xFFE8E9F2).copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h * 0.8f)
                )
            )
        }

        // Caustic Glow Orb 1 - Cyan Top Right
        val orb1X = w * 0.78f + sin(animOffset) * 22f
        val orb1Y = h * 0.16f + sin(animOffset * 0.7f) * 18f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) LiquidNeonCyan.copy(alpha = 0.32f) else LiquidNeonCyan.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(orb1X, orb1Y),
                radius = w * 0.55f
            ),
            center = Offset(orb1X, orb1Y),
            radius = w * 0.55f
        )

        // Caustic Glow Orb 2 - Violet / Purple Mid Left
        val orb2X = w * 0.18f + sin(animOffset * 0.8f + 1.2f) * 20f
        val orb2Y = h * 0.44f + sin(animOffset + 0.5f) * 26f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) LiquidNeonPurple.copy(alpha = 0.30f) else LiquidNeonPurple.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(orb2X, orb2Y),
                radius = w * 0.65f
            ),
            center = Offset(orb2X, orb2Y),
            radius = w * 0.65f
        )

        // Caustic Glow Orb 3 - Magenta / Ruby Bottom Right
        val orb3X = w * 0.65f + sin(animOffset * 0.5f + 2.5f) * 28f
        val orb3Y = h * 0.80f + sin(animOffset * 0.9f) * 22f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isDark) LiquidNeonMagenta.copy(alpha = 0.26f) else LiquidNeonPink.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(orb3X, orb3Y),
                radius = w * 0.60f
            ),
            center = Offset(orb3X, orb3Y),
            radius = w * 0.60f
        )

        if (!isDark) {
            // Prismatic Caustic Edge Dispersion Glint (matching rainbow refraction at bottom of photo)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LiquidPrismaticCyan.copy(alpha = 0.18f),
                        LiquidPrismaticMagenta.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.90f),
                    radius = w * 0.35f
                ),
                center = Offset(w * 0.85f, h * 0.90f),
                radius = w * 0.35f
            )
        }
    }
}

/**
 * Signature Translucent Frosted Glass Card with Specular Gloss Edge & Convex Top Sheen.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 0.dp,
    isDark: Boolean = LocalIsDarkMode.current,
    glowColor: Color = if (isDark) LiquidNeonCyan.copy(alpha = 0.22f) else Color(0x246366F1),
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    val surfaceBgBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0x73161F36),
                Color(0x5910172A),
                Color(0x661A2440)
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 600f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xF2FFFFFF),
                Color(0xD9FFFFFF),
                Color(0xE6F8FAFF)
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 600f)
        )
    }

    val defaultBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.65f else 0.98f),
            Color.White.copy(alpha = if (isDark) 0.18f else 0.45f),
            if (isDark) LiquidNeonCyan.copy(alpha = 0.40f) else LiquidPrismaticCyan.copy(alpha = 0.35f),
            if (isDark) LiquidNeonPurple.copy(alpha = 0.30f) else LiquidPrismaticMagenta.copy(alpha = 0.25f)
        ),
        start = Offset(0f, 0f),
        end = Offset(400f, 550f)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_press"
    )

    val shadowModifier = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = glowColor,
            spotColor = Color.Transparent
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(pressScale)
            .then(shadowModifier)
            .clip(shape)
            .background(surfaceBgBrush)
            .clickable(
                enabled = onClick != null,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick?.invoke() }
            )
    ) {
        // Specular Top Sheen & Radiant Accent Caustic Highlight Overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            // 1. Radiant Ambient Accent Glow (blooms softly from behind the icon/top-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = if (isDark) 0.32f else 0.20f),
                        glowColor.copy(alpha = if (isDark) 0.08f else 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.28f),
                    radius = size.width * 0.70f
                ),
                center = Offset(size.width * 0.25f, size.height * 0.28f),
                radius = size.width * 0.70f
            )

            // 2. Draw subtle convex specular light across top 45% of card
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.25f else 0.72f),
                    Color.White.copy(alpha = if (isDark) 0.04f else 0.14f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.45f
            )
            drawRect(
                brush = sheenBrush,
                size = Size(size.width, size.height * 0.45f)
            )

            // 3. Draw Refractive Luminous Glass Border
            val borderStrokeWidth = 1.3.dp.toPx()
            if (borderColor != null) {
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = borderStrokeWidth)
                )
            } else {
                drawRoundRect(
                    brush = defaultBorderBrush,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = borderStrokeWidth)
                )
            }
        }

        Box(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Signature Liquid Glass Circular Floating Button (Zero shadow, pure optical clarity).
 */
@Composable
fun LiquidGlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isDark: Boolean = LocalIsDarkMode.current,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "circle_btn_press"
    )

    val bgColor = containerColor ?: if (isDark) LiquidGlassBgDarkElevated else Color.White.copy(alpha = 0.92f)

    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.55f else 0.95f),
                        Color.White.copy(alpha = if (isDark) 0.15f else 0.40f),
                        if (isDark) LiquidNeonCyan.copy(alpha = 0.35f) else LiquidPrismaticCyan.copy(alpha = 0.30f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(150f, 150f)
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Specular top convex sheen
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.35f else 0.65f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = this.size.height * 0.55f
                )
            )
        }

        CompositionLocalProvider(
            LocalContentColor provides (contentColor ?: if (isDark) LiquidNeonCyan else PrimaryBlue)
        ) {
            content()
        }
    }
}

/**
 * Liquid Glass Pill Button Variants
 */
enum class LiquidButtonVariant {
    PRIMARY,    // Radiant Purple/Violet Gradient with Specular Sheen (Image 1)
    SECONDARY,  // Radiant Aqua/Cyan Gradient (Image 1)
    EMERGENCY,  // Radiant Ruby Red Glass
    FROSTED     // Translucent White Frosted Acrylic Glass
}

/**
 * Signature Convex Jelly / Liquid Glass Pill Button (from Image 1 & 2).
 */
@Composable
fun LiquidPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LiquidButtonVariant = LiquidButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isDark: Boolean = LocalIsDarkMode.current,
    height: Dp = 50.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pill_press"
    )

    val gradientColors = when (variant) {
        LiquidButtonVariant.PRIMARY -> listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
        LiquidButtonVariant.SECONDARY -> listOf(Color(0xFF06B6D4), Color(0xFF14B8A6))
        LiquidButtonVariant.EMERGENCY -> LiquidGradientEmergency
        LiquidButtonVariant.FROSTED -> if (isDark) listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)) else listOf(Color(0xEEFFFFFF), Color(0xCCFFFFFF))
    }

    val glowColor = when (variant) {
        LiquidButtonVariant.PRIMARY -> LiquidNeonPurple.copy(alpha = 0.40f)
        LiquidButtonVariant.SECONDARY -> LiquidNeonCyan.copy(alpha = 0.40f)
        LiquidButtonVariant.EMERGENCY -> LiquidRubyEmergency.copy(alpha = 0.50f)
        LiquidButtonVariant.FROSTED -> if (isDark) Color.White.copy(alpha = 0.20f) else Color(0x206366F1)
    }

    val contentColor = when (variant) {
        LiquidButtonVariant.FROSTED -> if (isDark) Color.White else TextPrimary
        else -> Color.White
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .shadow(
                elevation = if (isPressed) 4.dp else 9.dp,
                shape = CircleShape,
                ambientColor = glowColor,
                spotColor = Color.Transparent
            )
            .clip(CircleShape)
            .background(Brush.horizontalGradient(gradientColors))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Specular Gloss Convex Sheen Arc
        Canvas(modifier = Modifier.matchParentSize()) {
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.55f
            )
            drawRoundRect(
                brush = sheenBrush,
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )

            // Inner glass rim highlight
            drawRoundRect(
                color = Color.White.copy(alpha = 0.40f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Liquid Glass Squircle Telemetry Tile (matches 6 module tiles in reference image).
 */
@Composable
fun LiquidGlassTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconGradient: List<Color>,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkMode.current,
    onClick: (() -> Unit)? = null
) {
    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        elevation = 0.dp,
        isDark = isDark,
        glowColor = iconGradient.firstOrNull() ?: LiquidNeonCyan,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 3D Jelly Icon Squircle Badge (Refined Radiant Glass Specular)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(iconGradient)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.60f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.55f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White.copy(alpha = 0.70f) else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Signature Liquid Neon Sine Wave Chart (inspired by "Creative Mood" in Image 4).
 */
@Composable
fun LiquidWaveGraph(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkMode.current,
    frequency: Float = 1.0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h * 0.55f
        val amp = h * 0.28f

        // Wave 1: Neon Cyan Frequency
        val pathCyan = Path()
        val fillPathCyan = Path()
        fillPathCyan.moveTo(0f, h)

        for (x in 0..w.toInt() step 4) {
            val xf = x.toFloat()
            val y = midY + sin((xf / w) * 3f * Math.PI.toFloat() * frequency + phase) * amp *
                    sin((xf / w) * Math.PI.toFloat() + 0.2f)

            if (x == 0) {
                pathCyan.moveTo(xf, y)
                fillPathCyan.lineTo(xf, y)
            } else {
                pathCyan.lineTo(xf, y)
                fillPathCyan.lineTo(xf, y)
            }
        }
        fillPathCyan.lineTo(w, h)
        fillPathCyan.close()

        drawPath(
            path = fillPathCyan,
            brush = Brush.verticalGradient(
                colors = listOf(
                    LiquidNeonCyan.copy(alpha = if (isDark) 0.22f else 0.18f),
                    Color.Transparent
                ),
                startY = midY - amp,
                endY = h
            )
        )

        drawPath(
            path = pathCyan,
            color = if (isDark) LiquidNeonCyan else PrimaryBlue,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Wave 2: Neon Magenta / Pink Harmony Wave
        val pathMagenta = Path()
        for (x in 0..w.toInt() step 4) {
            val xf = x.toFloat()
            val y = midY + sin((xf / w) * 2.5f * Math.PI.toFloat() * frequency + phase + 1.8f) * (amp * 0.85f) *
                    sin((xf / w) * Math.PI.toFloat() + 0.5f)

            if (x == 0) pathMagenta.moveTo(xf, y) else pathMagenta.lineTo(xf, y)
        }

        drawPath(
            path = pathMagenta,
            color = LiquidNeonMagenta.copy(alpha = 0.85f),
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Liquid Glass Search Bar Capsule (matching Image 1 & 2).
 */
@Composable
fun LiquidGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search projects...",
    isDark: Boolean = LocalIsDarkMode.current,
    onAddClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null
) {
    val shape = CircleShape
    val surfaceColor = if (isDark) LiquidGlassBgDarkElevated else Color.White.copy(alpha = 0.82f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = shape,
        color = surfaceColor,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.40f else 0.95f),
                    if (isDark) LiquidNeonCyan.copy(alpha = 0.2f) else LiquidPrismaticCyan.copy(alpha = 0.25f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else TextSecondary,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch?.invoke() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = if (isDark) Color.White else TextPrimary,
                    unfocusedTextColor = if (isDark) Color.White else TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            if (onAddClick != null) {
                // Action Plus Bead (Image 1)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))))
                        .clickable(onClick = onAddClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Liquid Glass Segmented Tabs Control (Image 1 "Toast / Tabs").
 */
@Composable
fun <T> LiquidGlassSegmentedTabs(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkMode.current,
    labelProvider: (T) -> String = { it.toString() }
) {
    val shape = RoundedCornerShape(22.dp)
    val containerBg = if (isDark) LiquidGlassBgDark else Color.White.copy(alpha = 0.65f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = shape,
        color = containerBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.20f else 0.80f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val tabShape = RoundedCornerShape(18.dp)

                val tabModifier = if (isSelected) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                if (isDark) listOf(Color(0xFF2E3B68), Color(0xFF1E284A))
                                else listOf(Color.White, Color(0xFFF8FAFC))
                            )
                        )
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(tabShape)
                        .then(tabModifier)
                        .clickable { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            if (isDark) LiquidNeonCyan else PrimaryBlue
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.70f) else TextSecondary
                        }
                    )
                }
            }
        }
    }
}

/**
 * Liquid Glass Switch Toggle (Image 1 & 2).
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDarkMode.current
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumb_pos"
    )

    val trackBrush = if (checked) {
        Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF14B8A6)))
    } else {
        Brush.horizontalGradient(
            listOf(
                if (isDark) Color(0xFF1E263E) else Color(0xFFE2E8F0),
                if (isDark) Color(0xFF141A2D) else Color(0xFFCBD5E1)
            )
        )
    }

    Box(
        modifier = modifier
            .width(54.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(trackBrush)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Specular 3D Glass Thumb Knob
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFE2E8F0)),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
        }
    }
}

/**
 * Top Liquid Island Notch / Header
 */
@Composable
fun LiquidGlassTopNotch(
    userName: String = "YourName",
    isOnline: Boolean = true,
    isDark: Boolean = LocalIsDarkMode.current,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Status Glass Bead (Cloud sync & Refresh)
        Surface(
            shape = CircleShape,
            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.25f else 0.85f)),
            modifier = Modifier
                .size(42.dp)
                .clickable(enabled = onRefreshClick != null) { onRefreshClick?.invoke() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRefreshing) Icons.Default.Refresh else Icons.Default.CloudQueue,
                    contentDescription = "Cloud Status and Refresh",
                    tint = if (isRefreshing) LiquidNeonCyan else (if (isOnline) LiquidNeonGreen else OfflineGray),
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            if (isRefreshing) {
                                rotationZ = spinAngle
                            }
                        }
                )
            }
        }

        // Center Liquid Notch Pill
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isDark) LiquidGlassBgDarkElevated else Color.White.copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.45f else 0.95f),
                        if (isDark) LiquidNeonCyan.copy(alpha = 0.25f) else LiquidPrismaticCyan.copy(alpha = 0.25f)
                    )
                )
            ),
            modifier = Modifier
                .height(44.dp)
                .clickable { onAvatarClick?.invoke() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Jelly Avatar Bead
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(9.dp))

                Text(
                    text = userName,
                    color = if (isDark) Color.White else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Pulsing Green Live Beacon
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) LiquidNeonGreen else OfflineGray)
                )
            }
        }

        // Right Menu Glass Bead
        Surface(
            shape = CircleShape,
            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.25f else 0.85f)),
            modifier = Modifier
                .size(42.dp)
                .clickable { onMenuClick?.invoke() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = if (isDark) Color.White else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 3D Liquid Glass Emergency SOS Button.
 */
@Composable
fun LiquidGlassSOSButton(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "sos_radar")

    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.25f else 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1_scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.55f else 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1_alpha"
    )

    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 2400, delayMillis = if (isActive) 350 else 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2_scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.50f else 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1800 else 2400, delayMillis = if (isActive) 350 else 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2_alpha"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.02f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1500 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else corePulse,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sos_press"
    )

    Box(
        modifier = modifier
            .size(230.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radar Glow 1
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(wave1Scale)
                .clip(CircleShape)
                .background(
                    if (isActive) Color(0xFFFF1744).copy(alpha = wave1Alpha)
                    else LiquidRubyEmergency.copy(alpha = wave1Alpha)
                )
        )

        // Outer Radar Glow 2
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(wave2Scale)
                .clip(CircleShape)
                .background(
                    if (isActive) Color.White.copy(alpha = wave2Alpha * 0.7f)
                    else LiquidNeonMagenta.copy(alpha = wave2Alpha)
                )
        )

        // Core 3D Liquid Glass Ruby Orb
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isActive) listOf(
                            Color(0xFFFF859B),
                            Color(0xFFFF1744),
                            Color(0xFFD50000),
                            Color(0xFF8B0000)
                        ) else listOf(
                            Color(0xFFFF5277),
                            LiquidRubyEmergency,
                            Color(0xFFB91C1C),
                            Color(0xFF7F1D1D)
                        ),
                        center = Offset(0.35f, 0.35f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Convex 3D Specular Glass Crescent Reflection
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.5f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.25f),
                    radius = size.width * 0.38f
                )

                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = size.width * 0.49f,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.NotificationsActive else Icons.Filled.Warning,
                    contentDescription = "Emergency SOS",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isActive) "ACTIVE" else "EMERGENCY",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.5.sp
                )
                Text(
                    text = if (isActive) "TAP TO CANCEL" else "TAP TO TRIGGER",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
