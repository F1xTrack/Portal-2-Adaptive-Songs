package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalThemeTokens
import kotlin.math.roundToInt

@Composable
fun NowScreen(
    controller: NowPlaybackController,
    onOpenLegacyPlayer: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by controller.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        controller.onLocationPermissionChanged(granted)
    }

    LaunchedEffect(controller, context) {
        val granted = hasLocationPermission(context)
        controller.onLocationPermissionChanged(granted)
        if (!granted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    DisposableEffect(lifecycleOwner, controller, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.refreshTracks()
                controller.onLocationPermissionChanged(hasLocationPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            ShellHeader(
                title = stringResource(R.string.now_screen_title),
                subtitle = stringResource(R.string.now_screen_subtitle),
                badgeText = stringResource(R.string.now_screen_badge),
            )
        }
        item {
            HeroPlaybackCard(
                uiState = uiState,
                onTogglePlayback = controller::togglePlayback,
                onOpenImport = onOpenImport,
            )
        }
        if (!uiState.hasLocationPermission) {
            item {
                PermissionCard(
                    onGrantPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
            }
        }
        item {
            AdaptationCard(
                uiState = uiState,
                onThresholdChange = { controller.setThreshold(it.roundToInt()) },
                onHysteresisChange = { controller.setHysteresis(it.roundToInt()) },
            )
        }
        item {
            VolumeCard(
                volumePercent = uiState.volumePercent,
                onVolumeChange = { controller.setVolume(it.roundToInt()) },
            )
        }
        item {
            QuickActionsCard(
                onOpenLegacyPlayer = onOpenLegacyPlayer,
                onOpenImport = onOpenImport,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            SectionTitle(title = stringResource(R.string.now_screen_track_list_title))
        }
        if (uiState.availableTracks.isEmpty()) {
            item {
                ShellInfoCard(
                    title = stringResource(R.string.now_screen_no_tracks_title),
                    body = stringResource(R.string.now_screen_no_tracks_body),
                )
            }
        } else {
            items(
                count = uiState.availableTracks.size,
                key = { index -> uiState.availableTracks[index].name },
            ) { index ->
                val track = uiState.availableTracks[index]
                TrackRow(
                    track = track,
                    onSelect = { controller.onTrackSelected(track.name) },
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeroPlaybackCard(
    uiState: NowPlaybackUiState,
    onTogglePlayback: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val selectedTrack = uiState.selectedTrackName
    val playsText = pluralStringResource(
        R.plurals.plays_count,
        uiState.currentTrackPlays,
        uiState.currentTrackPlays,
    )
    val durationText = formatDurationLabel(uiState.currentTrackDurationMs)
    val canTogglePlayback = selectedTrack != null
    val primaryActionLabel = if (uiState.isPlaying) {
        stringResource(R.string.now_screen_action_pause)
    } else {
        stringResource(R.string.now_screen_action_play)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(
                width = 1.dp,
                color = PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusPills(
            isPlaying = uiState.isPlaying,
            isSuperSpeed = uiState.isSuperSpeed,
            isTrackingActive = uiState.isTrackingActive,
        )
        Text(
            text = stringResource(R.string.now_screen_selected_track),
            style = MaterialTheme.typography.labelLarge,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Text(
            text = selectedTrack ?: stringResource(R.string.main_now_playing_none),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.main_library_title),
                value = durationText,
            )
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.sort_freq),
                value = playsText,
            )
        }
        if (canTogglePlayback) {
            Button(
                onClick = onTogglePlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = primaryActionLabel)
            }
        } else {
            OutlinedButton(
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.now_screen_action_pick_track))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onGrantPermission: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShellInfoCard(
            title = stringResource(R.string.now_screen_permission_title),
            body = stringResource(R.string.now_screen_permission_body),
        )
        OutlinedButton(
            onClick = onGrantPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.now_screen_permission_action))
        }
    }
}

@Composable
private fun AdaptationCard(
    uiState: NowPlaybackUiState,
    onThresholdChange: (Float) -> Unit,
    onHysteresisChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(
                width = 1.dp,
                color = PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.now_screen_speed_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.now_screen_current_speed),
                value = stringResource(
                    R.string.now_screen_speed_value,
                    uiState.currentSpeedKmh.roundToInt(),
                ),
            )
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.now_screen_current_mode),
                value = stringResource(
                    if (uiState.isSuperSpeed) {
                        R.string.now_screen_mode_superspeed
                    } else {
                        R.string.now_screen_mode_normal
                    },
                ),
            )
        }
        SliderMetric(
            title = stringResource(R.string.faith_plate_threshold_label),
            value = uiState.thresholdKmh.toFloat(),
            valueLabel = stringResource(R.string.faith_plate_threshold, uiState.thresholdKmh),
            valueRange = 5f..60f,
            onValueChange = onThresholdChange,
        )
        SliderMetric(
            title = stringResource(R.string.hysteresis_label),
            value = uiState.hysteresisKmh.toFloat(),
            valueLabel = stringResource(R.string.hysteresis_value, uiState.hysteresisKmh),
            valueRange = 1f..20f,
            onValueChange = onHysteresisChange,
        )
    }
}

@Composable
private fun VolumeCard(
    volumePercent: Int,
    onVolumeChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(
                width = 1.dp,
                color = PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.now_screen_volume_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.now_screen_volume_value, volumePercent),
            style = MaterialTheme.typography.bodyLarge,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Slider(
            value = volumePercent.toFloat(),
            onValueChange = onVolumeChange,
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun QuickActionsCard(
    onOpenLegacyPlayer: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(
                width = 1.dp,
                color = PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.now_screen_quick_actions),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = onOpenLegacyPlayer, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.shell_open_legacy_player))
        }
        OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.shell_open_import))
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.shell_open_history))
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.shell_open_settings))
        }
    }
}

@Composable
private fun TrackRow(
    track: NowPlaybackUiState.TrackItem,
    onSelect: () -> Unit,
) {
    val durationText = formatDurationLabel(track.durationMs)
    val playsText = pluralStringResource(R.plurals.plays_count, track.plays, track.plays)
    val containerColor = if (track.isSelected) {
        PortalThemeTokens.colors.surfaceHighlight
    } else {
        PortalThemeTokens.colors.surface
    }
    val borderColor = if (track.isSelected) {
        PortalThemeTokens.colors.accentBlue
    } else {
        PortalThemeTokens.colors.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onSelect)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = track.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.SemiBold,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (track.isUserTrack) {
                    PortalThemeTokens.colors.accentOrange.copy(alpha = 0.18f)
                } else {
                    PortalThemeTokens.colors.accentBlue.copy(alpha = 0.18f)
                },
                contentColor = if (track.isUserTrack) {
                    PortalThemeTokens.colors.accentOrange
                } else {
                    PortalThemeTokens.colors.accentBlue
                },
            ) {
                Text(
                    text = stringResource(
                        if (track.isUserTrack) R.string.storage_imported_label else R.string.storage_builtin_label,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.sort_duration),
                value = durationText,
            )
            MiniMetric(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.sort_freq),
                value = playsText,
            )
        }
        Button(
            onClick = onSelect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (track.isSelected) {
                    PortalThemeTokens.colors.accentOrange
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = androidx.compose.ui.graphics.Color.Black,
            ),
        ) {
            Text(
                text = stringResource(
                    if (track.isSelected) R.string.now_screen_action_pause else R.string.now_screen_action_play,
                ),
            )
        }
    }
}

@Composable
private fun StatusPills(
    isPlaying: Boolean,
    isSuperSpeed: Boolean,
    isTrackingActive: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = stringResource(
                if (isPlaying) R.string.now_screen_status_playing else R.string.now_screen_status_paused,
            ),
            background = PortalThemeTokens.colors.accentBlue.copy(alpha = 0.18f),
            content = PortalThemeTokens.colors.accentBlue,
        )
        StatusPill(
            text = stringResource(
                if (isSuperSpeed) R.string.now_screen_mode_superspeed else R.string.now_screen_mode_normal,
            ),
            background = PortalThemeTokens.colors.accentOrange.copy(alpha = 0.18f),
            content = PortalThemeTokens.colors.accentOrange,
        )
        StatusPill(
            text = stringResource(
                if (isTrackingActive) R.string.now_screen_tracking_active else R.string.now_screen_tracking_inactive,
            ),
            background = PortalThemeTokens.colors.statusSuccess.copy(alpha = 0.18f),
            content = PortalThemeTokens.colors.statusSuccess,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        contentColor = content,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MiniMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PortalThemeTokens.colors.surfaceElevated)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SliderMetric(
    title: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}
