package com.guardianlink.app.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.guardianlink.app.components.ClaySquircle
import com.guardianlink.app.components.FluidWaveCanvas
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

    var showPairDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var newDeviceId by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = copyImageToInternalStorage(context, uri)
            if (savedPath != null) {
                viewModel.saveProfilePhoto(savedPath)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dual-tone fluid wave background
        FluidWaveCanvas(
            topColor = CosmicNavyDark,
            bottomColor = ClayCanvasLight,
            waveYRatio = 0.30f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Luminous 3D Avatar Squircle
            ClaySquircle(
                modifier = Modifier
                    .size(100.dp)
                    .clickable {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                cornerRadius = 28.dp,
                backgroundColor = CosmicIndigoSurface,
                borderColor = ElectricBlueAccent.copy(alpha = 0.6f),
                elevation = 12.dp
            ) {
                if (profile.photoLocalPath.isNotEmpty()) {
                    AsyncImage(
                        model = File(profile.photoLocalPath),
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(ElectricBlueAccent, PastelPurple))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default avatar",
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = profile.name.ifBlank { "Harshit" },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Tap photo to customize",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Profile Details Clay Card
            ClaySquircle(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                backgroundColor = PorcelainWhite,
                borderColor = Color.White,
                elevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Guardian Profile",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicNavyDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileDetailRow(icon = Icons.Default.Badge, label = "Full Name", value = profile.name.ifBlank { "Harshit" })
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileDetailRow(icon = Icons.Default.Phone, label = "Phone", value = profile.phoneNumber.ifBlank { "+91 99999 99999" })
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileDetailRow(icon = Icons.Default.Devices, label = "Paired Device", value = profile.deviceId.ifBlank { "demo_device" })
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileDetailRow(icon = Icons.Default.Security, label = "Emergency Role", value = "Primary Guardian")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dark Mode Clay Tile
            ClaySquircle(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                backgroundColor = PorcelainWhite,
                borderColor = Color.White,
                elevation = 5.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PastelPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = PastelPurple, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Dark Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = CosmicNavyDark
                        )
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlueAccent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pair Wearable Device Button
            Button(
                onClick = {
                    newDeviceId = profile.deviceId
                    showPairDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (profile.deviceId.isEmpty()) "Pair Wearable Device" else "Change Device ID (${profile.deviceId})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Out Button
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralEmergency),
                border = BorderStroke(1.dp, CoralEmergency.copy(alpha = 0.4f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    // Pair Device Dialog
    if (showPairDialog) {
        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Pair Wearable Device", fontWeight = FontWeight.Bold, color = CosmicNavyDark) },
            text = {
                Column {
                    Text("Enter the unique hardware ID printed on your ESP32 tracker:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
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
                Button(
                    onClick = {
                        if (newDeviceId.isNotBlank()) {
                            val updatedProfile = profile.copy(deviceId = newDeviceId.trim())
                            viewModel.updateProfile(updatedProfile)
                        }
                        showPairDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Log Out Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", fontWeight = FontWeight.Bold, color = CosmicNavyDark) },
            text = { Text("Are you sure you want to log out of GuardianLink?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        FirebaseAuth.getInstance().signOut()
                        AuthPreference.clear(context)
                        navController?.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralEmergency)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ElectricBlueAccent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ElectricBlueAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, fontSize = 11.sp, color = TextSecondary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CosmicNavyDark)
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