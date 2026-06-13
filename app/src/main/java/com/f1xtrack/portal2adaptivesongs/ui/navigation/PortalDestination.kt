package com.f1xtrack.portal2adaptivesongs.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.f1xtrack.portal2adaptivesongs.R

enum class PortalDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Now(
        route = "now",
        labelRes = R.string.shell_now,
        icon = Icons.Outlined.PlayCircleOutline,
    ),
    Routes(
        route = "routes",
        labelRes = R.string.shell_routes,
        icon = Icons.Outlined.Map,
    ),
    Library(
        route = "library",
        labelRes = R.string.shell_library,
        icon = Icons.Outlined.LibraryMusic,
    ),
    Profile(
        route = "profile",
        labelRes = R.string.shell_profile,
        icon = Icons.Outlined.AccountCircle,
    ),
}

val portalTopLevelDestinations = PortalDestination.entries
