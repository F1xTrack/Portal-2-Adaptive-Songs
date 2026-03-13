package com.f1xtrack.portal2adaptivesongs.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.f1xtrack.portal2adaptivesongs.ui.navigation.PortalNavHost

@Composable
fun PortalApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    PortalNavHost(
        navController = navController,
        modifier = modifier,
    )
}
