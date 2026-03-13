package com.f1xtrack.portal2adaptivesongs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.f1xtrack.portal2adaptivesongs.ui.navigation.PortalNavHost
import com.f1xtrack.portal2adaptivesongs.ui.screens.rememberNowPlaybackController

@Composable
fun PortalApp(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val playbackController = rememberNowPlaybackController(context.applicationContext)

    DisposableEffect(playbackController) {
        onDispose {
            playbackController.release()
        }
    }

    PortalNavHost(
        navController = navController,
        playbackController = playbackController,
        modifier = modifier,
    )
}
