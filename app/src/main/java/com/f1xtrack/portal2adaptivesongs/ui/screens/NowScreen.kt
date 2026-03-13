package com.f1xtrack.portal2adaptivesongs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import com.f1xtrack.portal2adaptivesongs.ui.theme.LocalAnimationIntensity

@Composable
fun NowScreen(
    onOpenLegacyPlayer: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val intensity = LocalAnimationIntensity.current
    val actions = listOf(
        stringResource(R.string.shell_open_legacy_player) to onOpenLegacyPlayer,
        stringResource(R.string.shell_open_import) to onOpenImport,
        stringResource(R.string.shell_open_history) to onOpenHistory,
        stringResource(R.string.shell_open_settings) to onOpenSettings,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            ShellHeader(
                title = stringResource(R.string.shell_title),
                subtitle = stringResource(R.string.shell_subtitle),
            )
        }
        item {
            ShellInfoCard(
                title = stringResource(R.string.shell_motion_label),
                body = when (intensity) {
                    AnimationIntensity.Relaxed -> "Relaxed"
                    AnimationIntensity.Moderate -> stringResource(R.string.shell_motion_moderate)
                    AnimationIntensity.Expressive -> "Expressive"
                },
            )
        }
        item {
            ShellInfoCard(
                title = stringResource(R.string.shell_now),
                body = stringResource(R.string.shell_legacy_status),
            )
        }
        items(actions) { (label, action) ->
            Button(
                onClick = action,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
