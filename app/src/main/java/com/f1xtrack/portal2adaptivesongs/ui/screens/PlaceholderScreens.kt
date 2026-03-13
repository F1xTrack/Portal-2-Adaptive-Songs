package com.f1xtrack.portal2adaptivesongs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f1xtrack.portal2adaptivesongs.R

@Composable
fun RoutesPlaceholderScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.shell_placeholder_routes_title),
        body = stringResource(R.string.shell_placeholder_routes_body),
    )
}

@Composable
fun LibraryPlaceholderScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.shell_placeholder_library_title),
        body = stringResource(R.string.shell_placeholder_library_body),
    )
}

@Composable
fun ProfilePlaceholderScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.shell_placeholder_profile_title),
        body = stringResource(R.string.shell_placeholder_profile_body),
    )
}

@Composable
private fun PlaceholderScreen(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        ShellHeader(
            title = title,
            subtitle = stringResource(R.string.shell_subtitle),
        )
        ShellInfoCard(
            title = title,
            body = body,
        )
        Text(
            text = stringResource(R.string.shell_legacy_status),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
