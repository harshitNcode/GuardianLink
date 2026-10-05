package com.guardianlink.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.guardianlink.app.screens.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.GeofenceViewModel

private val routesWithoutBottomBar = setOf("splash", "login")

@Composable
fun AppNavigation(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {

    val navController = rememberNavController()
    val geofenceViewModel: GeofenceViewModel = viewModel()

    val items = listOf(
        BottomNav.Home,
        BottomNav.Map,
        BottomNav.Contacts,
        BottomNav.Alerts,
        BottomNav.Profile
    )

    val currentRoute by navController.currentBackStackEntryAsState()
    val showBottomBar = currentRoute?.destination?.route !in routesWithoutBottomBar

    Scaffold(
        containerColor = ClayCanvasLight,
        bottomBar = {
            if (showBottomBar) {
                // Floating Claymorphic Pill Dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(32.dp),
                                ambientColor = ClayShadowAmbient,
                                spotColor = ClayShadowSpot
                            ),
                        shape = RoundedCornerShape(32.dp),
                        color = PorcelainWhite,
                        border = BorderStroke(1.dp, Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentDestination = currentRoute?.destination?.route

                            items.forEach { item ->
                                val selected = currentDestination == item.route

                                FloatingDockItem(
                                    item = item,
                                    selected = selected,
                                    onClick = {
                                        if (currentDestination != item.route) {
                                            if (item.route == "dashboard") {
                                                if (!navController.popBackStack("dashboard", inclusive = false)) {
                                                    navController.navigate("dashboard") {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            } else {
                                                navController.navigate(item.route) {
                                                    popUpTo("dashboard") {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(padding)
        ) {

            composable("splash") {
                SplashScreen(navController)
            }

            composable("login") {
                LoginScreen(navController)
            }

            composable("dashboard") {
                DashboardScreen(navController = navController)
            }

            composable("map") {
                MapScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    },
                    geofenceViewModel = geofenceViewModel
                )
            }

            composable("contacts") {
                ContactsScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    }
                )
            }

            composable("alerts") {
                AlertsScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    }
                )
            }

            composable("profile") {
                ProfileScreen(
                    isDarkMode = isDarkMode,
                    onToggleTheme = onToggleTheme,
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun FloatingDockItem(
    item: BottomNav,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                if (selected) ElectricBlueAccent.copy(alpha = 0.12f) else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = if (selected) ElectricBlueAccent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.title,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) ElectricBlueAccent else TextSecondary
            )
        }
    }
}