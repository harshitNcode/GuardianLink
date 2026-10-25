package com.guardianlink.app.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.guardianlink.app.screens.*
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.AlertViewModel
import com.guardianlink.app.viewmodel.DeviceViewModel
import com.guardianlink.app.viewmodel.GeofenceViewModel
import org.osmdroid.util.GeoPoint

private val routesWithoutBottomBar = setOf("splash", "login")

@Composable
fun AppNavigation(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val geofenceViewModel: GeofenceViewModel = viewModel()
    val alertViewModel: AlertViewModel = viewModel()
    val deviceViewModel: DeviceViewModel = viewModel()
    val alertsList by alertViewModel.alerts.collectAsState()
    val deviceStatus by deviceViewModel.status.collectAsState()

    // Global Continuous Safe Zone Perimeter Breach Monitor
    // Runs persistently across Dashboard (Recent Activity), Map, and Alerts screens
    LaunchedEffect(
        deviceStatus.latitude,
        deviceStatus.longitude,
        geofenceViewModel.geofences.size
    ) {
        val lat = deviceStatus.latitude
        val lng = deviceStatus.longitude
        if (lat != null && lng != null && deviceStatus.hasValidLocation && geofenceViewModel.geofences.isNotEmpty()) {
            val devPoint = GeoPoint(lat, lng)
            geofenceViewModel.checkBreach(devPoint) { breachedZone, breachDist ->
                alertViewModel.recordSafeZoneBreach(breachedZone, breachDist, lat, lng)
            }
        }
    }

    val items = listOf(
        BottomNav.Home,
        BottomNav.Map,
        BottomNav.Contacts,
        BottomNav.Alerts,
        BottomNav.Profile
    )

    val currentRoute by navController.currentBackStackEntryAsState()
    val showBottomBar = currentRoute?.destination?.route !in routesWithoutBottomBar

    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -10f) {
                    // Scrolling down: glide dock down
                    isBottomBarVisible = false
                } else if (delta > 10f) {
                    // Scrolling up: reveal dock
                    isBottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentRoute?.destination?.route) {
        // Always reset to visible on destination change
        isBottomBarVisible = true
    }

    val bottomBarTranslationY by animateDpAsState(
        targetValue = if (isBottomBarVisible) 0.dp else 120.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarOffset"
    )

    // Edge-to-edge root Box: eliminates any solid white bar cutting off the screen!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(if (isDarkMode) LiquidCanvasDark else LiquidCanvasLight)
    ) {
        // NavHost flows edge-to-edge behind the floating dock
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.fillMaxSize()
        ) {

            composable("splash") {
                SplashScreen(navController)
            }

            composable("login") {
                LoginScreen(navController)
            }

            composable("dashboard") {
                DashboardScreen(
                    navController = navController,
                    isDarkMode = isDarkMode
                )
            }

            composable("map") {
                MapScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    },
                    geofenceViewModel = geofenceViewModel,
                    alertViewModel = alertViewModel,
                    isDarkMode = isDarkMode
                )
            }

            composable("contacts") {
                ContactsScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    },
                    isDarkMode = isDarkMode
                )
            }

            composable("alerts") {
                AlertsScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate("dashboard") { launchSingleTop = true }
                        }
                    },
                    alertViewModel = alertViewModel,
                    isDarkMode = isDarkMode
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

        // True Floating Liquid Glass Dock: floats above the canvas or map with no solid white background!
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = bottomBarTranslationY)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val dockShape = RoundedCornerShape(32.dp)
                val dockIsDark = !isDarkMode
                val dockBg = if (dockIsDark) Color(0xF20F172A) else Color(0xF2FFFFFF)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(dockShape)
                        .background(dockBg)
                ) {
                    // Specular Glass Sheen & Refractive Border
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (dockIsDark) 0.25f else 0.65f),
                                    Color.White.copy(alpha = 0.04f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.5f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx())
                        )

                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (dockIsDark) 0.60f else 0.95f),
                                    Color.White.copy(alpha = 0.15f),
                                    if (dockIsDark) LiquidNeonCyan.copy(alpha = 0.35f) else LiquidPrismaticCyan.copy(alpha = 0.35f)
                                )
                            ),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()),
                            style = Stroke(width = 1.3.dp.toPx())
                        )
                    }

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
                            val isAlertsItem = item == BottomNav.Alerts
                            val showBadge = isAlertsItem && alertsList.isNotEmpty()

                            LiquidDockItem(
                                item = item,
                                selected = selected,
                                isDark = dockIsDark,
                                showBadge = showBadge,
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
}

/**
 * Liquid Glass Dock Navigation Pill Item with high-contrast visibility.
 */
@Composable
private fun LiquidDockItem(
    item: BottomNav,
    selected: Boolean,
    isDark: Boolean,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .clip(pillShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.linearGradient(
                                if (isDark) listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                else listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
                            )
                        )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.6f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                )
            }
        }

        Box(contentAlignment = Alignment.TopEnd) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = if (selected) {
                        Color.White
                    } else {
                        // High-contrast unselected icon (easily visible over any map or background!)
                        if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF334155)
                    },
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.title,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        Color.White
                    } else {
                        if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF334155)
                    }
                )
            }

            if (showBadge) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = 4.dp, y = (-2).dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}