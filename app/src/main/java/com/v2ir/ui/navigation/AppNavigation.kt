package com.v2ir.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.v2ir.R
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.components.StaticAppBackground
import com.v2ir.ui.screens.configs.ConfigsScreen
import com.v2ir.ui.screens.home.HomeScreen
import com.v2ir.ui.screens.logs.LogsScreen
import com.v2ir.ui.screens.settings.SettingsScreen
import com.v2ir.ui.theme.DeepNavy
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.TextHint

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    data object Configs : Screen("configs", R.string.nav_configs, Icons.Default.List)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    data object Logs : Screen("logs", R.string.nav_logs, Icons.Default.Terminal)
    data object CloudflareScanner : Screen("cf_scanner", R.string.configs_cf_scanner_title, Icons.Default.Cloud)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Configs,
    Screen.CloudflareScanner,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    StaticAppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                // Simplified Glassmorphism Bottom Bar
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .height(80.dp),
                    shape = RoundedCornerShape(28.dp),
                    backgroundAlpha = 0.45f,
                    blurRadius = 20.dp,
                    borderColor = NeonCyan.copy(alpha = 0.25f)
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentDestination?.route != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = stringResource(screen.labelRes),
                                        modifier = Modifier.size(26.dp),
                                        tint = if (screen == Screen.CloudflareScanner && !selected) com.v2ir.ui.theme.CloudflareOrange else if (selected) NeonCyan else TextHint
                                    )
                                },
                                label = {
                                    if (screen != Screen.CloudflareScanner) {
                                        Text(
                                            text = stringResource(screen.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    } else if (selected) {
                                        Text(
                                            text = "CF",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    unselectedIconColor = TextHint,
                                    unselectedTextColor = TextHint,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        )
{ innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onSelectConfig = {
                                // Use the same navigation logic as the bottom bar to ensure correct backstack behavior
                                navController.navigate(Screen.Configs.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigateToScanner = {
                                navController.navigate(Screen.CloudflareScanner.route)
                            }
                        )
                    }
                    composable(Screen.Configs.route) {
                        ConfigsScreen()
                    }
                    composable(Screen.Logs.route) {
                        LogsScreen()
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateToLogs = {
                                navController.navigate(Screen.Logs.route)
                            },
                            onNavigateToScanner = {
                                navController.navigate(Screen.CloudflareScanner.route)
                            }
                        )
                    }
                    composable(Screen.CloudflareScanner.route) {
                        com.v2ir.ui.screens.scanner.CloudflareScannerScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}




