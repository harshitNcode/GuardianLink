package com.guardianlink.app.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.guardianlink.app.components.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.util.AuthPreference
import com.guardianlink.app.util.PhoneGpsManager
import com.guardianlink.app.viewmodel.UserViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val viewModel: UserViewModel = viewModel()
    val profile by viewModel.profile.collectAsState()
    val isDark = isDarkMode

    // Phone GPS Broadcasting State
    val isBroadcastingGps by PhoneGpsManager.isBroadcasting.collectAsState()
    val lastBroadcastText by PhoneGpsManager.lastBroadcastText.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var newDeviceId by remember { mutableStateOf("") }

    // Form inputs for Settings
    var settingsName by remember { mutableStateOf("") }
    var settingsPhone by remember { mutableStateOf("") }
    var settingsEmergencyContact by remember { mutableStateOf("") }
    var settingsDeviceId by remember { mutableStateOf("") }

    // Permission launcher for GPS broadcasting
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            PhoneGpsManager.startBroadcasting(context, profile.deviceId.ifBlank { "demo_device" })
            Toast.makeText(context, "Phone GPS broadcast activated", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission required to broadcast GPS", Toast.LENGTH_LONG).show()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = copyImageToInternalStorage(context, uri)
            if (localPath != null) {
                viewModel.updateProfile(profile.copy(photoLocalPath = localPath))
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Screen Title & Settings Quick Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Guardian Profile",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else TextPrimary
                )

                LiquidGlassCircleButton(
                    onClick = {
                        settingsName = profile.name.ifBlank { "Harshit" }
                        settingsPhone = profile.phoneNumber
                        settingsEmergencyContact = profile.emergencyContact
                        settingsDeviceId = profile.deviceId.ifBlank { "demo_device" }
                        showSettingsSheet = true
                    },
                    size = 42.dp,
                    isDark = isDark
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Luminous 3D Glass Avatar Lens
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(LiquidGradientPrimary))
                    .clickable {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isDark) LiquidSurfaceDark else LiquidSurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.photoLocalPath.isNotEmpty()) {
                        AsyncImage(
                            model = File(profile.photoLocalPath),
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default avatar",
                            tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = profile.name.ifBlank { "Harshit" },
                color = if (isDark) Color.White else TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Tap photo to customize avatar",
                color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // GUARDIAN PROFILE CARD - KEPT VISIBLE OUTSIDE SETTINGS
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp,
                isDark = isDark,
                glowColor = LiquidNeonCyan.copy(alpha = 0.18f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LiquidNeonCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Guardian Profile",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else TextPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                settingsName = profile.name.ifBlank { "Harshit" }
                                settingsPhone = profile.phoneNumber
                                settingsEmergencyContact = profile.emergencyContact
                                settingsDeviceId = profile.deviceId.ifBlank { "demo_device" }
                                showSettingsSheet = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile via Settings",
                                tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LiquidProfileDetailRow(icon = Icons.Default.Badge, label = "Full Name", value = profile.name.ifBlank { "Harshit" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Phone, label = "Phone", value = profile.phoneNumber.ifBlank { "Not set (Tap Settings)" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.ContactPhone, label = "Emergency Contact (WhatsApp SOS)", value = profile.emergencyContact.ifBlank { "Not set (Tap Settings)" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Devices, label = "Paired Device", value = profile.deviceId.ifBlank { "demo_device" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Security, label = "Emergency Role", value = "Primary Guardian", isDark = isDark)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SETTINGS ENTRY OPTION CARD
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        settingsName = profile.name.ifBlank { "Harshit" }
                        settingsPhone = profile.phoneNumber
                        settingsEmergencyContact = profile.emergencyContact
                        settingsDeviceId = profile.deviceId.ifBlank { "demo_device" }
                        showSettingsSheet = true
                    },
                cornerRadius = 22.dp,
                elevation = 6.dp,
                isDark = isDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(LiquidGradientPrimary)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isDark) Color.White else TextPrimary
                            )
                            Text(
                                text = "Edit profile, share GPS, theme & device",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Settings",
                        tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pair Wearable Device Liquid Pill Button
            LiquidPillButton(
                text = if (profile.deviceId.isEmpty()) "Pair Wearable Device" else "Change Device ID (${profile.deviceId})",
                icon = Icons.Default.Sync,
                onClick = {
                    newDeviceId = profile.deviceId
                    showPairDialog = true
                },
                variant = LiquidButtonVariant.FROSTED,
                modifier = Modifier.fillMaxWidth(),
                height = 50.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Log Out Liquid Pill Button
            LiquidPillButton(
                text = "Log Out",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = { showLogoutDialog = true },
                variant = LiquidButtonVariant.EMERGENCY,
                modifier = Modifier.fillMaxWidth(),
                height = 50.dp
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // COMPREHENSIVE SETTINGS BOTTOM SHEET
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(LiquidGradientPrimary)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = if (isDark) Color.White.copy(alpha = 0.7f) else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // SECTION 1: BASIC PROFILE SETTINGS
                Text(
                    text = "GUARDIAN DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isDark) LiquidNeonCyan else PrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = settingsName,
                    onValueChange = { settingsName = it },
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. Harshit") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = settingsPhone,
                    onValueChange = { settingsPhone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("e.g. +91 98765 43210") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = settingsEmergencyContact,
                    onValueChange = { settingsEmergencyContact = it },
                    label = { Text("Emergency Contact (WhatsApp SOS)") },
                    placeholder = { Text("e.g. +91 91234 56789") },
                    leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null, tint = PrimaryBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = settingsDeviceId,
                    onValueChange = { settingsDeviceId = it },
                    label = { Text("Paired Wearable Device ID") },
                    placeholder = { Text("e.g. demo_device") },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null, tint = PrimaryBlue) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Save Profile Button
                LiquidPillButton(
                    text = "Save Profile Changes",
                    icon = Icons.Default.Check,
                    onClick = {
                        val updated = profile.copy(
                            name = settingsName.trim().ifBlank { "Harshit" },
                            phoneNumber = settingsPhone.trim(),
                            emergencyContact = settingsEmergencyContact.trim(),
                            deviceId = settingsDeviceId.trim().ifBlank { "demo_device" }
                        )
                        viewModel.updateProfile(updated)
                        Toast.makeText(context, "Profile details updated successfully", Toast.LENGTH_SHORT).show()
                    },
                    variant = LiquidButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // SECTION 2: SHARE THIS PHONE'S GPS OPTION (MOVED FROM MAP)
                Text(
                    text = "LOCATION BROADCAST SERVICE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isDark) LiquidNeonCyan else PrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isBroadcastingGps)
                        LiquidNeonGreen.copy(alpha = 0.14f)
                    else
                        (if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.85f)),
                    border = BorderStroke(
                        1.dp,
                        if (isBroadcastingGps) LiquidNeonGreen.copy(alpha = 0.65f) else (if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.1f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isBroadcastingGps) LiquidNeonGreen.copy(alpha = 0.25f)
                                        else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBroadcastingGps) Icons.Default.Sensors else Icons.Default.SensorsOff,
                                    contentDescription = null,
                                    tint = if (isBroadcastingGps) LiquidNeonGreen else (if (isDark) Color.LightGray else TextSecondary),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isBroadcastingGps) "Broadcasting Phone GPS" else "Share This Phone's GPS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isBroadcastingGps) LiquidNeonGreen else (if (isDark) Color.White else TextPrimary)
                                )
                                Text(
                                    text = if (isBroadcastingGps)
                                        "Live: ${lastBroadcastText.ifBlank { "Transmitting location..." }}"
                                    else
                                        "Broadcast phone GPS for live wearable tracking",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isBroadcastingGps,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine || hasCoarse) {
                                        PhoneGpsManager.startBroadcasting(context, profile.deviceId.ifBlank { "demo_device" })
                                        Toast.makeText(context, "Broadcasting this phone's GPS", Toast.LENGTH_SHORT).show()
                                    } else {
                                        gpsPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                } else {
                                    PhoneGpsManager.stopBroadcasting()
                                    Toast.makeText(context, "Phone GPS broadcast stopped", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = LiquidNeonGreen
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // SECTION 3: THEME & DISPLAY
                Text(
                    text = "APPEARANCE & THEME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isDark) LiquidNeonCyan else PrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(LiquidGradientMagenta)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Dark Theme",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else TextPrimary
                                )
                                Text(
                                    text = if (isDark) "Liquid Obsidian" else "Liquid Crystal",
                                    fontSize = 11.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary
                                )
                            }
                        }

                        LiquidGlassSwitch(
                            checked = isDarkMode,
                            onCheckedChange = { onToggleTheme() },
                            isDark = isDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // SECTION 4: APP INFORMATION
                Text(
                    text = "ABOUT & SYSTEM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isDark) LiquidNeonCyan else PrimaryBlue
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "App Version", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary)
                            Text(text = "GuardianLink v2.4.0 (2026)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Hardware Integration", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary)
                            Text(text = "ESP32 Cellular/WiFi Dual-Link", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Cloud Telemetry", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary)
                            Text(text = "Online & Synced", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiquidNeonGreen)
                        }
                    }
                }
            }
        }
    }

    // Pair Device Dialog
    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "Pair Wearable Device",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Enter the unique hardware ID printed on your ESP32 tracker:",
                        fontSize = 13.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newDeviceId,
                        onValueChange = { newDeviceId = it },
                        label = { Text("Device ID") },
                        placeholder = { Text("e.g. demo_device") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                LiquidPillButton(
                    text = "Save",
                    onClick = {
                        if (newDeviceId.isNotBlank()) {
                            val updatedProfile = profile.copy(deviceId = newDeviceId.trim())
                            viewModel.updateProfile(updatedProfile)
                        }
                        showPairDialog = false
                    },
                    variant = LiquidButtonVariant.PRIMARY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { showPairDialog = false },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 44.dp
                )
            }
        )
    }

    // Log Out Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "Log Out",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to log out of GuardianLink?",
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary
                )
            },
            confirmButton = {
                LiquidPillButton(
                    text = "Log Out",
                    onClick = {
                        showLogoutDialog = false
                        FirebaseAuth.getInstance().signOut()
                        AuthPreference.clear(context)
                        navController?.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    variant = LiquidButtonVariant.EMERGENCY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { showLogoutDialog = false },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 44.dp
                )
            }
        )
    }
}

@Composable
private fun LiquidProfileDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LiquidNeonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) LiquidNeonCyan else PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else TextPrimary
            )
        }
    }
}

private fun copyImageToInternalStorage(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "profile_photo.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}
