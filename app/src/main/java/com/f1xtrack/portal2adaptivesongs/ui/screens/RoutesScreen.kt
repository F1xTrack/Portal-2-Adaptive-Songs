package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f1xtrack.portal2adaptivesongs.HistoryActivity
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.RouteSettingsActivity
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalThemeTokens

@Composable
fun RoutesScreen(
    controller: RoutesController,
    onOpenLegacyHistory: () -> Unit,
    onOpenRouteSettings: () -> Unit,
) {
    val uiState by controller.uiState.collectAsState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ShellHeader(
                title = stringResource(R.string.routes_screen_title),
                subtitle = stringResource(R.string.routes_screen_subtitle),
                badgeText = stringResource(R.string.routes_screen_badge),
            )
        }
        item {
            GpsStatusCard(
                status = uiState.gpsStatus,
                onOpenRouteSettings = onOpenRouteSettings,
            )
        }
        item {
            SummaryGrid(uiState = uiState)
        }
        item {
            SessionChartCard(uiState = uiState)
        }
        item {
            RoutesActionsCard(
                onOpenLegacyHistory = onOpenLegacyHistory,
                onOpenRouteSettings = onOpenRouteSettings,
            )
        }
        if (!uiState.hasAnyRoutes) {
            item {
                ShellInfoCard(
                    title = stringResource(R.string.routes_screen_empty_title),
                    body = stringResource(R.string.routes_screen_empty_body),
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.routes_screen_recent_sessions),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(uiState.recentSessions, key = { it.sessionId }) { session ->
                SessionCard(session = session)
            }
        }
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun rememberRoutesController(): RoutesController {
    val context = LocalContext.current.applicationContext
    return remember(context) { RoutesController(context) }
}

@Composable
private fun GpsStatusCard(
    status: RoutesGpsStatus,
    onOpenRouteSettings: () -> Unit,
) {
    val indicatorColor = when (status.quality) {
        GpsQuality.PermissionRequired -> PortalThemeTokens.colors.statusWarning
        GpsQuality.RecordingDisabled -> PortalThemeTokens.colors.contentSecondary
        GpsQuality.Balanced -> PortalThemeTokens.colors.accentBlue
        GpsQuality.Precise -> PortalThemeTokens.colors.statusSuccess
    }
    val title = when (status.quality) {
        GpsQuality.PermissionRequired -> stringResource(R.string.routes_gps_permission_required)
        GpsQuality.RecordingDisabled -> stringResource(R.string.routes_gps_recording_disabled)
        GpsQuality.Balanced -> stringResource(R.string.routes_gps_balanced)
        GpsQuality.Precise -> stringResource(R.string.routes_gps_precise)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Column {
                Text(
                    text = stringResource(R.string.routes_gps_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Text(
            text = status.statusLine,
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        OutlinedButton(onClick = onOpenRouteSettings) {
            Text(text = stringResource(R.string.routes_open_route_settings))
        }
    }
}

@Composable
private fun SummaryGrid(uiState: RoutesUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.routes_summary_sessions),
                value = uiState.totalSessions.toString(),
                detail = stringResource(R.string.routes_summary_sessions_hint),
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.routes_summary_distance),
                value = stringResource(R.string.routes_distance_km, uiState.totalDistanceKm),
                detail = stringResource(R.string.routes_summary_distance_hint),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.routes_summary_longest),
                value = stringResource(R.string.routes_minutes_value, uiState.longestSessionMinutes),
                detail = stringResource(R.string.routes_summary_longest_hint),
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.routes_summary_avg_speed),
                value = stringResource(R.string.now_screen_speed_value, uiState.averageSpeedKmh),
                detail = stringResource(R.string.routes_summary_avg_speed_hint),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PortalThemeTokens.colors.surfaceElevated)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = PortalThemeTokens.colors.contentSecondary,
        )
    }
}

@Composable
private fun SessionChartCard(uiState: RoutesUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.routes_chart_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.routes_chart_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        if (uiState.chartEntries.isEmpty()) {
            Text(
                text = stringResource(R.string.routes_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = PortalThemeTokens.colors.contentSecondary,
            )
        } else {
            SessionBarChart(entries = uiState.chartEntries)
        }
        uiState.latestSession?.let { session ->
            ShellInfoCard(
                title = stringResource(R.string.routes_latest_session_title, session.title),
                body = stringResource(
                    R.string.routes_latest_session_body,
                    session.startedAtLabel,
                    session.sampleCount,
                    session.superspeedMoments,
                ),
            )
        }
    }
}

@Composable
private fun SessionBarChart(entries: List<RouteChartEntry>) {
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val gap = 18.dp.toPx()
            val barWidth = ((size.width - gap * (entries.size - 1)) / entries.size).coerceAtLeast(14.dp.toPx())
            entries.forEachIndexed { index, entry ->
                val left = index * (barWidth + gap)
                val heightRatio = (entry.value / maxValue).coerceIn(0f, 1f)
                val barHeight = size.height * heightRatio
                drawRoundRect(
                    color = PortalThemeTokens.colors.accentBlue,
                    topLeft = Offset(left, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(18f, 18f),
                )
                drawRoundRect(
                    color = PortalThemeTokens.colors.outline,
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 2f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            entries.forEach { entry ->
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = PortalThemeTokens.colors.contentSecondary,
                )
            }
        }
    }
}

@Composable
private fun RoutesActionsCard(
    onOpenLegacyHistory: () -> Unit,
    onOpenRouteSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.routes_actions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.routes_actions_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onOpenLegacyHistory,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.routes_open_history_map))
            }
            OutlinedButton(
                onClick = onOpenRouteSettings,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.routes_open_route_settings))
            }
        }
    }
}

@Composable
private fun SessionCard(session: RouteSessionSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = session.startedAtLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PortalThemeTokens.colors.contentSecondary,
                )
            }
            Text(
                text = stringResource(R.string.routes_distance_km, session.distanceKm),
                style = MaterialTheme.typography.titleMedium,
                color = PortalThemeTokens.colors.accentOrange,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetricChip(
                label = stringResource(R.string.routes_session_duration),
                value = stringResource(R.string.routes_minutes_value, session.durationMinutes),
                modifier = Modifier.weight(1f),
            )
            MiniMetricChip(
                label = stringResource(R.string.routes_session_avg_speed),
                value = stringResource(R.string.now_screen_speed_value, session.averageSpeedKmh),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetricChip(
                label = stringResource(R.string.routes_session_peak_speed),
                value = stringResource(R.string.now_screen_speed_value, session.peakSpeedKmh),
                modifier = Modifier.weight(1f),
            )
            MiniMetricChip(
                label = stringResource(R.string.routes_session_altitude),
                value = stringResource(R.string.routes_altitude_meters, session.altitudeRangeMeters),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                R.string.routes_session_footer,
                session.sampleCount,
                session.superspeedMoments,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = PortalThemeTokens.colors.contentSecondary,
        )
    }
}

@Composable
private fun MiniMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(PortalThemeTokens.colors.surfaceElevated)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
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
internal fun RoutesScreenEntry() {
    val context = LocalContext.current
    val controller = rememberRoutesController()
    RoutesScreen(
        controller = controller,
        onOpenLegacyHistory = {
            context.startActivity(Intent(context, HistoryActivity::class.java))
        },
        onOpenRouteSettings = {
            context.startActivity(Intent(context, RouteSettingsActivity::class.java))
        },
    )
}
