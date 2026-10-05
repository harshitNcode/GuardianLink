package com.guardianlink.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.guardianlink.app.components.ClaySquircle
import com.guardianlink.app.components.FluidWaveCanvas
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
    alertViewModel: AlertViewModel = viewModel()
) {
    val alerts by alertViewModel.alerts.collectAsState()

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
        // Dual-tone fluid wave background
        FluidWaveCanvas(
            topColor = CosmicNavyDark,
            bottomColor = ClayCanvasLight,
            waveYRatio = 0.22f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header in Cosmic Navy section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Incident Alerts",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Real-Time Safety Feed",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }

                if (alerts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CoralEmergency.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, CoralEmergency.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${alerts.size} Active",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Filter Pills in Porcelain Canvas
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AlertFilter.values()) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) ElectricBlueAccent else PorcelainWhite,
                        border = BorderStroke(1.dp, if (isSelected) ElectricBlueAccent else Color.White),
                        shadowElevation = if (isSelected) 4.dp else 2.dp,
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) Color.White else CosmicNavyDark,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
                    ClayEmptyAlertsState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAlerts) { alert ->
                        val alertKey = "${alert.title}_${alert.time}"
                        val isAcked = acknowledgedAlerts.contains(alertKey)
                        ClayAlertCard(
                            alert = alert,
                            isAcknowledged = isAcked,
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
private fun ClayAlertCard(
    alert: Alert,
    isAcknowledged: Boolean,
    onAcknowledge: () -> Unit
) {
    val (icon, badgeColor) = when (alert.type) {
        "danger" -> Icons.Default.Warning to CoralEmergency
        "warning" -> Icons.Default.WarningAmber to AmberAccent
        "success" -> Icons.Default.CheckCircle to GlowingMint
        else -> Icons.Default.Info to ElectricBlueAccent
    }

    ClaySquircle(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = if (isAcknowledged) Color(0xFFF3F5FA) else PorcelainWhite,
        borderColor = if (isAcknowledged) Color(0xFFE2E8F0) else Color.White,
        elevation = if (isAcknowledged) 2.dp else 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CosmicNavyDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${alert.type.uppercase()} • ${alert.time}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onAcknowledge,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isAcknowledged) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    contentDescription = "Acknowledge",
                    tint = if (isAcknowledged) GlowingMint else OfflineGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ClayEmptyAlertsState() {
    ClaySquircle(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 26.dp,
        backgroundColor = PorcelainWhite,
        borderColor = Color.White,
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(GlowingMint, NeonCyanAccent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "All Systems Safe",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicNavyDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "No safety violations or geofence breaches detected. Wearable telemetry is normal.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}