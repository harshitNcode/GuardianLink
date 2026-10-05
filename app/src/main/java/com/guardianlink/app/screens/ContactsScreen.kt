package com.guardianlink.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guardianlink.app.components.*
import com.guardianlink.app.model.Contact
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.ContactViewModel

@Composable
fun ContactsScreen(
    onNavigateBack: () -> Unit = {},
    contactViewModel: ContactViewModel = viewModel(),
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val isDark = isDarkMode

    var showAddEditDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

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

            // Liquid Glass Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                            text = "Guardians",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else TextPrimary
                        )
                        Text(
                            text = "Emergency Contacts Network",
                            fontSize = 12.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary
                        )
                    }
                }

                // Add Contact Liquid Pill Button
                LiquidPillButton(
                    text = "Add",
                    icon = Icons.Default.PersonAdd,
                    onClick = {
                        editingContact = null
                        newName = ""
                        newPhone = ""
                        showAddEditDialog = true
                    },
                    variant = LiquidButtonVariant.PRIMARY,
                    height = 42.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact List
            if (contactViewModel.contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidEmptyContactsState(
                        isDark = isDark,
                        onAddClick = {
                            editingContact = null
                            newName = ""
                            newPhone = ""
                            showAddEditDialog = true
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(contactViewModel.contacts) { contact ->
                        LiquidContactCard(
                            contact = contact,
                            isDark = isDark,
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                context.startActivity(intent)
                            },
                            onMessage = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.phone}"))
                                context.startActivity(intent)
                            },
                            onEdit = {
                                editingContact = contact
                                newName = contact.name
                                newPhone = contact.phone
                                showAddEditDialog = true
                            },
                            onDelete = {
                                contactToDelete = contact
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Contact Dialog
    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = if (editingContact != null) "Edit Guardian" else "Add Emergency Contact",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Designated guardians receive your live GPS coordinate updates during an SOS broadcast.",
                        fontSize = 13.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Papa, Sister, Doctor") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+91 98765 43210") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                LiquidPillButton(
                    text = if (editingContact != null) "Update" else "Add Contact",
                    onClick = {
                        val trimmedName = newName.trim()
                        val trimmedPhone = newPhone.trim()
                        if (trimmedName.isNotBlank() && trimmedPhone.isNotBlank()) {
                            if (editingContact != null) {
                                contactViewModel.updateContact(editingContact!!, trimmedName, trimmedPhone)
                            } else {
                                contactViewModel.addContact(trimmedName, trimmedPhone)
                            }
                            showAddEditDialog = false
                        }
                    },
                    variant = LiquidButtonVariant.PRIMARY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { showAddEditDialog = false },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 44.dp
                )
            }
        )
    }

    // Delete Confirmation Dialog
    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            containerColor = if (isDark) LiquidSurfaceDark else LiquidSurfaceLight,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "Remove Contact",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove ${contactToDelete?.name} from your emergency contacts?",
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else TextSecondary
                )
            },
            confirmButton = {
                LiquidPillButton(
                    text = "Remove",
                    onClick = {
                        contactToDelete?.let { contactViewModel.deleteContact(it) }
                        contactToDelete = null
                    },
                    variant = LiquidButtonVariant.EMERGENCY,
                    height = 44.dp
                )
            },
            dismissButton = {
                LiquidPillButton(
                    text = "Cancel",
                    onClick = { contactToDelete = null },
                    variant = LiquidButtonVariant.FROSTED,
                    height = 44.dp
                )
            }
        )
    }
}

@Composable
private fun LiquidContactCard(
    contact: Contact,
    isDark: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
        isDark = isDark,
        glowColor = LiquidNeonCyan.copy(alpha = 0.15f),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3D Jelly Avatar Squircle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(LiquidGradientPrimary)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phone,
                    fontSize = 12.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else TextSecondary
                )
            }

            // Tactile Action Glass Beads
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Call Glass Bead (Glowing Mint)
                Surface(
                    shape = CircleShape,
                    color = LiquidNeonGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, LiquidNeonGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(onClick = onCall)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = LiquidNeonGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // SMS Glass Bead (Neon Cyan)
                Surface(
                    shape = CircleShape,
                    color = LiquidNeonCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, LiquidNeonCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(onClick = onMessage)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "SMS",
                            tint = LiquidNeonCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Delete Glass Bead (Emergency Ruby)
                Surface(
                    shape = CircleShape,
                    color = LiquidRubyEmergency.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, LiquidRubyEmergency.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(onClick = onDelete)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = LiquidRubyEmergency,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidEmptyContactsState(
    isDark: Boolean,
    onAddClick: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 26.dp,
        elevation = 8.dp,
        isDark = isDark,
        glowColor = LiquidNeonPurple.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(LiquidGradientMagenta)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PeopleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Guardians Added",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color.White else TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add trusted guardians who will receive instantaneous SMS coordinates whenever an emergency SOS is triggered.",
                fontSize = 12.sp,
                color = if (isDark) Color.White.copy(alpha = 0.65f) else TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            LiquidPillButton(
                text = "Add Guardian",
                icon = Icons.Default.Add,
                onClick = onAddClick,
                variant = LiquidButtonVariant.PRIMARY
            )
        }
    }
}
