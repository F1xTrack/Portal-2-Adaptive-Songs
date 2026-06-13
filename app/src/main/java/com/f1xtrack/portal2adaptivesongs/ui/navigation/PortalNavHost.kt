package com.f1xtrack.portal2adaptivesongs.ui.navigation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.f1xtrack.portal2adaptivesongs.HistoryActivity
import com.f1xtrack.portal2adaptivesongs.MainActivity
import com.f1xtrack.portal2adaptivesongs.SettingsActivity
import com.f1xtrack.portal2adaptivesongs.StorageActivity
import com.f1xtrack.portal2adaptivesongs.ui.screens.LibraryScreenEntry
import com.f1xtrack.portal2adaptivesongs.ui.screens.NowPlaybackController
import com.f1xtrack.portal2adaptivesongs.ui.screens.NowScreen
import com.f1xtrack.portal2adaptivesongs.ui.screens.ProfileScreenEntry
import com.f1xtrack.portal2adaptivesongs.ui.screens.RoutesScreenEntry
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalThemeTokens

@Composable
fun PortalNavHost(
    navController: NavHostController,
    playbackController: NowPlaybackController,
    onAnimationIntensityChanged: (AnimationIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = PortalThemeTokens.colors.background,
        bottomBar = {
            NavigationBar {
                portalTopLevelDestinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = {
                            Text(text = stringResource(destination.labelRes))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalThemeTokens.colors.background)
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = PortalDestination.Now.route,
            ) {
                composable(PortalDestination.Now.route) {
                    NowScreen(
                        controller = playbackController,
                        onOpenLegacyPlayer = {
                            context.startActivity(Intent(context, MainActivity::class.java))
                        },
                        onOpenImport = {
                            context.startActivity(Intent(context, StorageActivity::class.java))
                        },
                        onOpenHistory = {
                            context.startActivity(Intent(context, HistoryActivity::class.java))
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        },
                    )
                }
                composable(PortalDestination.Routes.route) {
                    RoutesScreenEntry()
                }
                composable(PortalDestination.Library.route) {
                    LibraryScreenEntry()
                }
                composable(PortalDestination.Profile.route) {
                    ProfileScreenEntry(
                        onAnimationIntensityChanged = onAnimationIntensityChanged,
                    )
                }
            }
        }
    }
}
