package com.guardianlink.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.guardianlink.app.components.*
import com.guardianlink.app.model.Alert
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.AlertViewModel

enum class AlertFilter(val label: String) {
    ALL("All Alerts"),
    SOS("🚨 SOS Only"),
    GEOFENCE("🛡️ Safe Zones"),
    INFO("ℹ️ System")
}

@Composable
fun AlertsScreen(
    onNavigateBack: () -> Unit = {},
    alertViewModel: AlertViewModel = viewModel(),
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val alerts by alertViewModel.alerts.collectAsState()
    val isDark = isDarkMode

    LaunchedEffect(Unit) {
        alertViewModel.refresh()
    }

    var selectedFilter by remember { mutableStateOf(AlertFilter.ALL) }
    val acknowledgedAlerts = remember { mutableStateListOf<String>() }

    val filteredAlerts = remember(alerts, selectedFilter) {
        when (selectedFilter) {
            AlertFilter.ALL -> alerts
            AlertFilter.SOS -> alerts.filter { it.type == "danger" || it.title.contains("SOS", ignoreCase = true) }
            AlertFilter.GEOFENCE -> alerts.filter {
                it.title.contains("Breach", ignoreCase = true) || it.title.contains("Safe Zone", ignoreCase = true)
            }
            AlertFilter.INFO -> alerts.filter {
                it.type != "danger" && !it.title.contains("Breach", ignoreCase = true) && !it.title.contains("SOS", ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Liquid Glass Ambient Caustic Canvas
        LiquidGlassCanvas(isDark = isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Liquid Glass Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Back Glass Button Pill
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = onNavigateBack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isDark) Color.White else TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Incident Alerts",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else TextPrimary
                        )
                        Text(
                            text = "Real-Time Safety Feed",
                            fontSize = 12.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh Alerts Button
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { alertViewModel.refresh() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Alerts",
                                tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (alerts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = LiquidRubyEmergency.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, LiquidRubyEmergency.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${alerts.size} Active",
                                color = if (isDark) Color.White else LiquidRubyEmergency,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Liquid Filter Capsule Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AlertFilter.values()) { filter ->
                    val isSelected = selectedFilter == filter
                    val pillShape = RoundedCornerShape(18.dp)

                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(
                                if (isSelected) {
                                    Brush.linearGradient(LiquidGradientPrimary)
                                } else {
                                    if (isDark) Brush.linearGradient(listOf(LiquidGlassBgDarkElevated, LiquidGlassBgDark))
                                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.6f)))
                                }
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidEmptyAlertsState(isDark = isDark)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAlerts) { alert ->
                        val alertKey = "${alert.title}_${alert.time}"
                        val isAcked = acknowledgedAlerts.contains(alertKey)
                        LiquidAlertCard(
                            alert = alert,
                            isAcknowledged = isAcked,
                            isDark = isDark,
                            onAcknowledge = {
                                if (isAcked) acknowledgedAlerts.remove(alertKey)
                                else acknowledgedAlerts.add(alertKey)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidAlertCard(
    alert: Alert,
    isAcknowledged: Boolean,
    isDark: Boolean,
    onAcknowledge: () -> Unit
) {
    val (icon, badgeColor) = when (alert.type) {
        "danger" -> Icons.Default.Warning to LiquidRubyEmergency
        "warning" -> Icons.Default.WarningAmber to LiquidHoneyGold
        "success" -> Icons.Default.CheckCircle to LiquidNeonGreen
        else -> Icons.Default.Info to LiquidNeonCyan
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = if (isAcknowledged) 3.dp else 8.dp,
        isDark = isDark,
        glowColor = if (isAcknowledged) Color.Transparent else badgeColor.copy(alpha = 0.22f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3D Jelly Status Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(badgeColor, badgeColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${alert.type.uppercase()} • ${alert.time}",
                    fontSize = 11.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onAcknowledge,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isAcknowledged) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    contentDescription = "Acknowledge",
                    tint = if (isAcknowledged) LiquidNeonGreen else (if (isDark) Color.White.copy(alpha = 0.4f) else OfflineGray),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun LiquidEmptyAlertsState(isDark: Boolean) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 26.dp,
        elevation = 10.dp,
        isDark = isDark,
        glowColor = LiquidNeonGreen.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(LiquidNeonGreen, LiquidNeonCyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "All Systems Safe",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color.White else TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "No safety violations or geofence breaches detected. Wearable telemetry is secure.",
                fontSize = 12.sp,
                color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}