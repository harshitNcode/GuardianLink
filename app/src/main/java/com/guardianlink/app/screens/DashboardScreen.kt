package com.guardianlink.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.guardianlink.app.components.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.AlertViewModel
import com.guardianlink.app.viewmodel.DeviceViewModel
import com.guardianlink.app.viewmodel.UserViewModel
import java.util.Calendar

@Composable
fun DashboardScreen(
    navController: NavController? = null,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val alertViewModel = remember { AlertViewModel() }
    val deviceViewModel = remember { DeviceViewModel() }
    val userViewModel: UserViewModel = viewModel()
    val userProfile by userViewModel.profile.collectAsState()
    val status by deviceViewModel.status.collectAsState()
    val isRefreshing by deviceViewModel.isRefreshing.collectAsState()
    val alerts by alertViewModel.alerts.collectAsState()
    val isDark = isDarkMode

    var searchQuery by remember { mutableStateOf("") }
    var isSosTriggering by remember { mutableStateOf(false) }
    val isSosActive = status.sos || isSosTriggering
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSosConfirmDialog by remember { mutableStateOf(false) }
    var showSosCancelDialog by remember { mutableStateOf(false) }
    var sosTargetPhone by remember { mutableStateOf(userProfile.emergencyContact.ifBlank { com.guardianlink.app.util.AuthPreference.getEmergencyContact(context) }) }

    LaunchedEffect(userProfile.emergencyContact) {
        if (userProfile.emergencyContact.isNotBlank()) {
            sosTargetPhone = userProfile.emergencyContact
        }
    }

    LaunchedEffect(Unit) {
        alertViewModel.refresh()
    }

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
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Top Status Island Notch
            item {
                LiquidGlassTopNotch(
                    userName = userProfile.name.ifBlank { "Harshit" },
                    isOnline = status.device.contains("Online", ignoreCase = true) && !status.isDisconnected,
                    isDark = isDark,
                    isRefreshing = isRefreshing,
                    onRefreshClick = {
                        deviceViewModel.refresh()
                        alertViewModel.refresh()
                    },
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
                        text = userProfile.name.ifBlank { "Harshit" },
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

            // Real-Time Active SOS Emergency Banner
            if (isSosActive) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            elevation = 0.dp,
                            isDark = isDark,
                            glowColor = LiquidRubyEmergency,
                            borderColor = LiquidRubyEmergency.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(LiquidRubyEmergency),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "EMERGENCY SOS ACTIVE",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = LiquidRubyEmergency
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Broadcasting live GPS (${status.gps}) to all guardians",
                                        fontSize = 11.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.80f) else TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                TextButton(onClick = {
                                    isSosTriggering = false
                                    alertViewModel.cancelSOS {
                                        deviceViewModel.refresh()
                                        alertViewModel.refresh()
                                    }
                                }) {
                                    Text(
                                        "CANCEL",
                                        color = LiquidRubyEmergency,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
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
                                        text = "Status: ${status.device.ifBlank { "Online" }} • ${status.network.ifBlank { "Wi-Fi" }}",
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

            // Biometric Health Vitals Section (ESP32 Live Sensors)

            item {

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Text(

                                text = "Biometric Vitals",

                                fontSize = 18.sp,

                                fontWeight = FontWeight.Bold,

                                color = if (isDark) Color.White else TextPrimary

                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(

                                modifier = Modifier

                                    .size(8.dp)

                                    .clip(CircleShape)

                                    .background(LiquidNeonGreen)

                            )

                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = LiquidNeonCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LiquidNeonCyan.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable { deviceViewModel.refresh() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = LiquidNeonCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isRefreshing) "SYNCING..." else "REFRESH",
                                        color = LiquidNeonCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = LiquidNeonPink.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LiquidNeonPink.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "ESP32 LIVE",
                                    color = if (isDark) LiquidNeonPink else LiquidRubyEmergency,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dual Vitals Cards: Heart Rate & Temperature (Equal 182.dp Height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Heart Rate Monitor
                        LiquidGlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(182.dp),
                            cornerRadius = 24.dp,
                            isDark = isDark,
                            glowColor = LiquidRubyEmergency.copy(alpha = 0.25f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(LiquidRubyEmergency, LiquidNeonPink)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Heart Rate",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            status.heartRate <= 0 -> Color.Gray.copy(alpha = 0.2f)
                                            status.isDisconnected -> LiquidSunsetAmber.copy(alpha = 0.2f)
                                            status.heartRate > 120 -> LiquidRubyEmergency.copy(alpha = 0.2f)
                                            status.heartRate < 55 -> LiquidSunsetAmber.copy(alpha = 0.2f)
                                            else -> LiquidNeonGreen.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = status.heartRateStatus,
                                            color = when {
                                                status.heartRate <= 0 -> if (isDark) Color.LightGray else TextSecondary
                                                status.isDisconnected -> LiquidSunsetAmber
                                                status.heartRate > 120 -> LiquidRubyEmergency
                                                status.heartRate < 55 -> LiquidSunsetAmber
                                                else -> LiquidNeonGreen
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = "Heart Rate",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (status.heartRate <= 0) "--" else "${status.heartRate}",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color.White else TextPrimary
                                        )

                                        if (status.heartRate > 0) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "BPM",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LiquidNeonPink,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Target: 60-100 BPM",
                                    fontSize = 10.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else TextSecondary
                                )
                            }
                        }

                        // Body Temperature Monitor
                        LiquidGlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(182.dp),
                            cornerRadius = 24.dp,
                            isDark = isDark,
                            glowColor = LiquidSunsetAmber.copy(alpha = 0.25f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(LiquidSunsetAmber, LiquidHoneyGold)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Thermostat,
                                            contentDescription = "Temperature",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            status.temperature <= 0f -> Color.Gray.copy(alpha = 0.2f)
                                            status.isDisconnected -> LiquidSunsetAmber.copy(alpha = 0.2f)
                                            status.temperature >= 38.0f -> LiquidRubyEmergency.copy(alpha = 0.2f)
                                            status.temperature >= 37.5f -> LiquidSunsetAmber.copy(alpha = 0.2f)
                                            else -> LiquidNeonGreen.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = status.temperatureStatus,
                                            color = when {
                                                status.temperature <= 0f -> if (isDark) Color.LightGray else TextSecondary
                                                status.isDisconnected -> LiquidSunsetAmber
                                                status.temperature >= 38.0f -> LiquidRubyEmergency
                                                status.temperature >= 37.5f -> LiquidSunsetAmber
                                                else -> LiquidNeonGreen
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = "Body Temp & Humidity",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (status.temperature <= 0f) "--" else "%.1f".format(status.temperature),
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color.White else TextPrimary
                                        )

                                        if (status.temperature > 0f) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "°C",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = LiquidSunsetAmber,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(${status.displayTemperatureFahrenheit})",
                                                fontSize = 10.sp,
                                                color = if (isDark) Color.White.copy(alpha = 0.5f) else TextSecondary,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WaterDrop,
                                            contentDescription = "Humidity",
                                            tint = if (status.humidity <= 0f) (if (isDark) Color.LightGray else TextSecondary) else LiquidNeonCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (status.humidity <= 0f) "Awaiting Sensor" else "${status.displayHumidity} (${status.humidityStatus})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (status.humidity <= 0f) (if (isDark) Color.LightGray else TextSecondary) else (if (isDark) LiquidNeonCyan else PrimaryBlue)
                                        )
                                    }
                                }

                                Text(
                                    text = "Normal: 36.5°C - 37.2°C",
                                    fontSize = 10.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else TextSecondary
                                )
                            }
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

                    // Row 2: Wi-Fi Network & Safe Zones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LiquidGlassTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Wifi,
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
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LiquidGlassSOSButton(
                        isActive = isSosActive,
                        onClick = {
                            if (!isSosActive) {
                                showSosConfirmDialog = true
                            } else {
                                showSosCancelDialog = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isSosActive) "🚨 SOS BROADCAST ACTIVE • TAP TO CANCEL" else "Instant emergency broadcast to all guardians",
                        fontSize = 12.sp,
                        color = if (isSosActive) LiquidRubyEmergency else if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
                        fontWeight = if (isSosActive) FontWeight.Bold else FontWeight.Medium
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

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // SOS Confirmation Dialog (Saved number from Contacts is dispatched automatically)
    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LiquidRubyEmergency,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Trigger Emergency SOS?",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color.White else TextPrimary
                    )
                }
            },
            text = {
                val targetPhone = userProfile.emergencyContact.ifBlank { com.guardianlink.app.util.AuthPreference.getEmergencyContact(context) }
                Text(
                    text = if (targetPhone.isNotBlank()) {
                        "Are you sure you want to trigger Emergency SOS? This will broadcast an alert and immediately dispatch your live GPS coordinates to your saved emergency contact ($targetPhone)."
                    } else {
                        "Are you sure you want to trigger Emergency SOS? This will immediately broadcast an emergency alert to all guardians. (Tip: You can set an automatic emergency contact in Contacts)."
                    },
                    color = if (isDark) Color.White.copy(alpha = 0.85f) else TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                LiquidPillButton(
                    text = "CONFIRM SOS",
                    onClick = {
                        val targetPhone = userProfile.emergencyContact.ifBlank { com.guardianlink.app.util.AuthPreference.getEmergencyContact(context) }
                        showSosConfirmDialog = false
                        isSosTriggering = true

                        // 1. Log alert in backend and update UI
                        alertViewModel.triggerSOS {
                            alertViewModel.refresh()
                            isSosTriggering = false
                        }

                        // 2. Dispatch Emergency Alert (Background SMS + WhatsApp) to saved contact
                        if (targetPhone.isNotBlank()) {
                            com.guardianlink.app.util.EmergencyDispatcher.dispatchEmergencyAlert(
                                context = context,
                                phoneNumber = targetPhone,
                                latitude = status.latitude,
                                longitude = status.longitude
                            )
                        }
                    },
                    variant = LiquidButtonVariant.EMERGENCY,
                    height = 46.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { showSosConfirmDialog = false },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 46.dp
                )
            }
        )
    }

    // Emergency Stand-Down Dialog
    if (showSosCancelDialog) {
        AlertDialog(
            onDismissRequest = { showSosCancelDialog = false },
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
                    "Stand Down Emergency SOS?",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Text(
                    "Are you safe? Standing down will cancel the emergency beacon sent to all guardians.",
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary
                )
            },
            confirmButton = {
                LiquidPillButton(
                    text = "STAND DOWN",
                    onClick = {
                        showSosCancelDialog = false
                        isSosTriggering = false
                        alertViewModel.cancelSOS {
                            deviceViewModel.refresh()
                            alertViewModel.refresh()
                        }
                    },
                    variant = LiquidButtonVariant.EMERGENCY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Keep Active",
                    onClick = { showSosCancelDialog = false },
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