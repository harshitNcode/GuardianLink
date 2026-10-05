package com.guardianlink.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNav(
    val route: String,
    val title: String,
    val icon: ImageVector
) {

    object Home : BottomNav(
        "dashboard",
        "Home",
        Icons.Default.Home
    )

    object Map : BottomNav(
        "map",
        "Map",
        Icons.Default.LocationOn
    )

    object Contacts : BottomNav(
        "contacts",
        "Contacts",
        Icons.Default.Contacts
    )

    object Alerts : BottomNav(
        "alerts",
        "Alerts",
        Icons.Default.Notifications
    )

    object Profile : BottomNav(
        "profile",
        "Profile",
        Icons.Default.Person
    )
}