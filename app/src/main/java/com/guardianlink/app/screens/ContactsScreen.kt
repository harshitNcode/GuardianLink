package com.guardianlink.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.guardianlink.app.components.ClaySquircle
import com.guardianlink.app.components.FluidWaveCanvas
import com.guardianlink.app.model.Contact
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.ContactViewModel

@Composable
fun ContactsScreen(
    onNavigateBack: () -> Unit = {},
    contactViewModel: ContactViewModel = viewModel()
) {
    val context = LocalContext.current
    var showAddEditDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

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

            // Top Header in cosmic navy section
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
                            text = "Guardians",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Emergency Contacts Network",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }

                // Add Contact Clay Squircle Button
                Button(
                    onClick = {
                        editingContact = null
                        newName = ""
                        newPhone = ""
                        showAddEditDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Guardian",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Contact List in porcelain clay section
            if (contactViewModel.contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ClayEmptyContactsState(onAddClick = {
                        editingContact = null
                        newName = ""
                        newPhone = ""
                        showAddEditDialog = true
                    })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(contactViewModel.contacts) { contact ->
                        ClayContactCard(
                            contact = contact,
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
            title = {
                Text(
                    text = if (editingContact != null) "Edit Guardian" else "Add Emergency Contact",
                    fontWeight = FontWeight.Bold,
                    color = CosmicNavyDark
                )
            },
            text = {
                Column {
                    Text(
                        text = "Designated guardians receive your live GPS coordinate updates during an SOS broadcast.",
                        fontSize = 13.sp,
                        color = TextSecondary
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
                Button(
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
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent)
                ) {
                    Text(if (editingContact != null) "Update" else "Add Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Remove Contact", fontWeight = FontWeight.Bold, color = CosmicNavyDark) },
            text = {
                Text("Are you sure you want to remove ${contactToDelete?.name} from your emergency contacts?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        contactToDelete?.let { contactViewModel.deleteContact(it) }
                        contactToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralEmergency)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ClayContactCard(
    contact: Contact,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ClaySquircle(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = PorcelainWhite,
        borderColor = Color.White,
        elevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3D Avatar Squircle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(ElectricBlueAccent, NeonCyanAccent))),
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
                    color = CosmicNavyDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phone,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Tactile Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = GlowingMint.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                    onClick = onCall
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF047857), modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ElectricBlueAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp),
                    onClick = onMessage
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Message, contentDescription = "SMS", tint = ElectricBlueAccent, modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = CoralEmergency.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp),
                    onClick = onDelete
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralEmergency, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClayEmptyContactsState(onAddClick: () -> Unit) {
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(PastelPink, PastelPurple))),
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
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CosmicNavyDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add trusted guardians who will receive instantaneous SMS coordinates whenever an emergency SOS is triggered.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Guardian", fontWeight = FontWeight.Bold)
            }
        }
    }
}
