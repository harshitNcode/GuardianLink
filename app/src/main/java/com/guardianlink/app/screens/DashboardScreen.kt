package com.guardianlink.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    navController: NavController? = null,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val alertViewModel = remember { AlertViewModel() }
    val deviceViewModel = remember { DeviceViewModel() }
    val status by deviceViewModel.status.collectAsState()
    val alerts by alertViewModel.alerts.collectAsState()
    val isDark = isDarkMode

    var searchQuery by remember { mutableStateOf("") }
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
        // Ambient Caustic Liquid Glass Canvas
        LiquidGlassCanvas(isDark = isDark)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Top Status Island Notch
            item {
                LiquidGlassTopNotch(
                    userName = "Harshit",
                    isOnline = status.device.contains("Online", ignoreCase = true),
                    isDark = isDark,
                    onAvatarClick = { navigateToTab("profile") },
                    onMenuClick = { navigateToTab("alerts") }
                )
            }

            // Greeting & Header
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = greeting,
                        color = if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Harshit",
                        color = if (isDark) Color.White else TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Liquid Glass Search Capsule (matching Image 1 & 2)
                    LiquidGlassSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search projects or safe zones...",
                        isDark = isDark,
                        onAddClick = { navigateToTab("map") }
                    )
                }
            }

            // Hero Telemetry Liquid Glass Card (Guardian IoT Core + Live Wave)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        elevation = 0.dp,
                        isDark = isDark,
                        glowColor = LiquidNeonCyan.copy(alpha = 0.22f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 3D Jelly Sensor Badge
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(LiquidGradientMagenta)),
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
                                        color = if (isDark) Color.White else TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Status: ${status.device.ifBlank { "Online" }} • ${status.network.ifBlank { "4G LTE" }}",
                                        color = if (isDark) Color.White.copy(alpha = 0.7f) else TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                // Live Online Beacon
                                Surface(
                                    shape = CircleShape,
                                    color = LiquidNeonGreen.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, LiquidNeonGreen.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "SYNCED",
                                        color = LiquidNeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Glowing Progress Bar
                            GlowProgressBar(
                                progress = batteryProgress,
                                modifier = Modifier.fillMaxWidth(),
                                gradientColors = listOf(LiquidNeonCyan, LiquidNeonGreen)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Battery: ${status.battery}",
                                    color = if (isDark) Color.White.copy(alpha = 0.9f) else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "GPS: ${status.gps}",
                                    color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Live Liquid Wave Telemetry Signal (Creative Mood style from Image 4)
                            Text(
                                text = "IoT Signal Waveform",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LiquidWaveGraph(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Pill Button to Live Map
                            LiquidPillButton(
                                text = "View Live Map & Safe Zones",
                                onClick = { navigateToTab("map") },
                                variant = LiquidButtonVariant.SECONDARY,
                                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                                modifier = Modifier.fillMaxWidth(),
                                height = 46.dp
                            )
                        }
                    }
                }
            }

            // System Modules Section (6 Liquid Glass Tiles in 3x2 Grid)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "System Modules",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextPrimary
                        )
                        Surface(
                            shape = CircleShape,
                            color = LiquidNeonCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LiquidNeonCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "6 ACTIVE",
                                color = if (isDark) LiquidNeonCyan else PrimaryBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 1: Battery & GPS Tracker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.BatteryChargingFull,
                            iconGradient = listOf(LiquidNeonGreen, LiquidNeonCyan),
                            title = "Battery",
                            subtitle = status.battery,
                            isDark = isDark
                        )

                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.GpsFixed,
                            iconGradient = listOf(LiquidNeonCyan, PrimaryBlue),
                            title = "GPS Tracker",
                            subtitle = status.gps,
                            isDark = isDark,
                            onClick = { navigateToTab("map") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 2: 4G LTE Network & Safe Zones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CellTower,
                            iconGradient = listOf(LiquidNeonPurple, LiquidNeonViolet),
                            title = "Network",
                            subtitle = status.network,
                            isDark = isDark
                        )

                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Security,
                            iconGradient = listOf(LiquidSunsetAmber, LiquidHoneyGold),
                            title = "Safe Zones",
                            subtitle = "Geofencing",
                            isDark = isDark,
                            onClick = { navigateToTab("map") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 3: Guardians & Incident Alerts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.ContactPhone,
                            iconGradient = listOf(LiquidNeonPink, LiquidNeonMagenta),
                            title = "Guardians",
                            subtitle = "Contacts",
                            isDark = isDark,
                            onClick = { navigateToTab("contacts") }
                        )

                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.NotificationsActive,
                            iconGradient = listOf(LiquidRubyEmergency, LiquidRubyDark),
                            title = "Alerts",
                            subtitle = "${alerts.size} incidents",
                            isDark = isDark,
                            onClick = { navigateToTab("alerts") }
                        )
                    }
                }
            }

            // 3D Liquid Glass Emergency SOS Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LiquidGlassSOSButton(
                        onClick = { showSosConfirmDialog = true }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Instant emergency broadcast to all guardians",
                        fontSize = 12.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextPrimary
                        )
                        TextButton(onClick = { navigateToTab("alerts") }) {
                            Text(
                                "See all",
                                fontSize = 13.sp,
                                color = if (isDark) LiquidNeonCyan else PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (alerts.isNotEmpty()) {
                        alerts.take(3).forEach { alert ->
                            LiquidActivityCard(
                                title = alert.title,
                                time = alert.time,
                                type = alert.type,
                                isDark = isDark
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    } else {
                        LiquidActivityCard(
                            title = "GPS Telemetry Synchronized",
                            time = "Just now",
                            type = "success",
                            isDark = isDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LiquidActivityCard(
                            title = "Active Safe Zone Monitoring",
                            time = "5 min ago",
                            type = "info",
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }

    // Emergency Confirmation Dialog (Styled in Liquid Glass)
    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(LiquidRubyEmergency.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LiquidRubyEmergency,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    "Confirm Emergency SOS",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to trigger an Emergency SOS? This will immediately notify all designated guardians with your live GPS location (${status.gps}).",
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary
                )
            },
            confirmButton = {
                LiquidPillButton(
                    text = "TRIGGER SOS",
                    onClick = {
                        alertViewModel.triggerSOS()
                        showSosConfirmDialog = false
                    },
                    variant = LiquidButtonVariant.EMERGENCY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { showSosConfirmDialog = false },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 44.dp
                )
            }
        )
    }
}

@Composable
private fun LiquidActivityCard(
    title: String,
    time: String,
    type: String,
    isDark: Boolean
) {
    val (icon, badgeColor) = when (type) {
        "danger" -> Icons.Default.Warning to LiquidRubyEmergency
        "warning" -> Icons.Default.WarningAmber to LiquidHoneyGold
        "success" -> Icons.Default.CheckCircle to LiquidNeonGreen
        else -> Icons.Default.Info to LiquidNeonCyan
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevation = 0.dp,
        isDark = isDark,
        glowColor = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isDark) Color.White else TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = time,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}