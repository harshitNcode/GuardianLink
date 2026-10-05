package com.guardianlink.app.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.guardianlink.app.components.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.AlertViewModel
import com.guardianlink.app.viewmodel.DeviceViewModel
import java.util.Calendar

@Composable
fun DashboardScreen(
    navController: NavController? = null
) {
    val alertViewModel = remember { AlertViewModel() }
    val deviceViewModel = remember { DeviceViewModel() }
    val status by deviceViewModel.status.collectAsState()
    val alerts by alertViewModel.alerts.collectAsState()

    var showSosConfirmDialog by remember { mutableStateOf(false) }

    // Dynamic greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning ☀️"
            in 12..16 -> "Good Afternoon 🌤️"
            in 17..20 -> "Good Evening 🌆"
            else -> "Good Night 🌙"
        }
    }

    // Battery calculation for neon progress bar
    val batteryProgress = remember(status.battery) {
        val digits = status.battery.filter { it.isDigit() }
        val pct = digits.toFloatOrNull() ?: 90f
        (pct / 100f).coerceIn(0.1f, 1f)
    }

    // SOS pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val sosPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )

    fun navigateToTab(route: String) {
        navController?.navigate(route) {
            popUpTo("dashboard") {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dual-tone fluid wave background
        FluidWaveCanvas(
            topColor = CosmicNavyDark,
            bottomColor = ClayCanvasLight,
            waveYRatio = 0.40f
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Top Status Island Notch
            item {
                TopIslandNotch(
                    userName = "Harshit",
                    isOnline = status.device.contains("Online", ignoreCase = true),
                    onAvatarClick = { navigateToTab("profile") },
                    onMenuClick = { navigateToTab("alerts") }
                )
            }

            // Top Cosmic Hero Section (Title + MyDocs-styled Hero Card)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = greeting,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Harshit",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // "MyDocs" styled primary telemetry hero card
                    CosmicHeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3D-styled Squircle Badge
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PastelPink, PastelPurple)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Guardian IoT Core",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Status: ${status.device.ifBlank { "Online" }} • ${status.network.ifBlank { "4G LTE" }}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Glowing Neon Progress Bar
                        GlowProgressBar(
                            progress = batteryProgress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Battery: ${status.battery}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "GPS: ${status.gps}",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Pill to Map
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navigateToTab("map") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = NeonCyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View Live Map & Safe Zones →",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Porcelain Canvas Section: System Modules Grid
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "System Modules",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicNavyDark
                        )
                        Surface(
                            shape = CircleShape,
                            color = GlowingMint.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GlowingMint.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "LIVE TELEMETRY",
                                color = Color(0xFF047857),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6 Claymorphic Squircle Modules (Matching 3x2 grid from reference image)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.BatteryChargingFull,
                            iconGradient = listOf(GlowingMint, NeonCyanAccent),
                            title = "Battery",
                            subtitle = status.battery
                        )

                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.GpsFixed,
                            iconGradient = listOf(ElectricBlueAccent, NeonCyanAccent),
                            title = "GPS Tracker",
                            subtitle = status.gps,
                            onClick = { navigateToTab("map") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CellTower,
                            iconGradient = listOf(PastelPurple, ElectricBlueAccent),
                            title = "Network",
                            subtitle = status.network
                        )

                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Security,
                            iconGradient = listOf(PastelOrange, AmberAccent),
                            title = "Safe Zones",
                            subtitle = "Geofencing",
                            onClick = { navigateToTab("map") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.ContactPhone,
                            iconGradient = listOf(PastelPink, CoralEmergency),
                            title = "Guardians",
                            subtitle = "Emergency Contacts",
                            onClick = { navigateToTab("contacts") }
                        )

                        ClayModuleTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.NotificationsActive,
                            iconGradient = listOf(AmberAccent, CoralEmergency),
                            title = "Alerts",
                            subtitle = "${alerts.size} incidents",
                            onClick = { navigateToTab("alerts") }
                        )
                    }
                }
            }

            // Neumorphic Circular Emergency SOS Button
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Ambient Breathing Pulse Ring
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .scale(sosPulseScale)
                                .clip(CircleShape)
                                .background(CoralEmergency.copy(alpha = 0.2f))
                        )

                        // Tactile Neumorphic SOS Circle
                        Surface(
                            shape = CircleShape,
                            color = CoralEmergency,
                            shadowElevation = 8.dp,
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier
                                .size(76.dp)
                                .clickable { showSosConfirmDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFFFF5277),
                                                CoralEmergency
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "SOS",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "SOS",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Instant emergency broadcast to all guardians",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Recent Activity Section
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = CosmicNavyDark
                        )
                        TextButton(onClick = { navigateToTab("alerts") }) {
                            Text("See all", fontSize = 13.sp, color = ElectricBlueAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (alerts.isNotEmpty()) {
                        alerts.take(3).forEach { alert ->
                            ClayActivityItem(
                                title = alert.title,
                                time = alert.time,
                                type = alert.type
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    } else {
                        ClayActivityItem(
                            title = "GPS Telemetry Synchronized",
                            time = "Just now",
                            type = "success"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ClayActivityItem(
                            title = "Active Safe Zone Monitoring",
                            time = "5 min ago",
                            type = "info"
                        )
                    }
                }
            }
        }
    }

    // Emergency Confirmation Dialog
    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CoralEmergency,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Confirm Emergency SOS", fontWeight = FontWeight.Bold, color = CosmicNavyDark)
            },
            text = {
                Text(
                    "Are you sure you want to trigger an Emergency SOS? This will immediately notify all designated guardians with your live GPS location (${status.gps}).",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        alertViewModel.triggerSOS()
                        showSosConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralEmergency)
                ) {
                    Text("TRIGGER SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSosConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * 3D-styled Squircle Module Tile matching the reference photo.
 */
@Composable
private fun ClayModuleTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconGradient: List<Color>,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ClaySquircle(
        modifier = modifier.height(116.dp), // Increased from 100.dp so text never clips
        cornerRadius = 22.dp,
        backgroundColor = PorcelainWhite,
        borderColor = Color.White,
        elevation = 6.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp), // Optimized padding
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(iconGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicNavyDark,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ClayActivityItem(
    title: String,
    time: String,
    type: String
) {
    val (icon, color) = when (type) {
        "danger" -> Icons.Default.Warning to CoralEmergency
        "warning" -> Icons.Default.WarningAmber to AmberAccent
        "success" -> Icons.Default.CheckCircle to GlowingMint
        else -> Icons.Default.Info to ElectricBlueAccent
    }

    ClaySquircle(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = PorcelainWhite,
        borderColor = Color.White,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = CosmicNavyDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = time,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}