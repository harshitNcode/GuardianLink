package com.guardianlink.app.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.guardianlink.app.components.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.util.AuthPreference
import com.guardianlink.app.viewmodel.UserViewModel
import java.io.File
import java.io.FileOutputStream

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

    var showPairDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var newDeviceId by remember { mutableStateOf("") }

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
            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(28.dp))

            // Profile Details Liquid Glass Card
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp,
                isDark = isDark,
                glowColor = LiquidNeonCyan.copy(alpha = 0.18f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Guardian Profile",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LiquidProfileDetailRow(icon = Icons.Default.Badge, label = "Full Name", value = profile.name.ifBlank { "Harshit" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Phone, label = "Phone", value = profile.phoneNumber.ifBlank { "+91 99999 99999" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Devices, label = "Paired Device", value = profile.deviceId.ifBlank { "demo_device" }, isDark = isDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidProfileDetailRow(icon = Icons.Default.Security, label = "Emergency Role", value = "Primary Guardian", isDark = isDark)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dark Mode Liquid Glass Card with Liquid Switch
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                elevation = 6.dp,
                isDark = isDark
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(LiquidGradientMagenta)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
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

            Spacer(modifier = Modifier.height(18.dp))

            // Pair Wearable Device Liquid Pill Button
            LiquidPillButton(
                text = if (profile.deviceId.isEmpty()) "Pair Wearable Device" else "Change Device ID (${profile.deviceId})",
                icon = Icons.Default.Sync,
                onClick = {
                    newDeviceId = profile.deviceId
                    showPairDialog = true
                },
                variant = LiquidButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Log Out Liquid Pill Button
            LiquidPillButton(
                text = "Log Out",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = { showLogoutDialog = true },
                variant = LiquidButtonVariant.EMERGENCY,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp
            )

            Spacer(modifier = Modifier.height(100.dp))
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