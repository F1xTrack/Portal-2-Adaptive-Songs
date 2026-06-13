package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.f1xtrack.portal2adaptivesongs.RouteRecorder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val GPS_PREFS = "gps_prefs"
private const val EARTH_RADIUS_METERS = 6_371_000.0

@Immutable
data class RoutesUiState(
    val totalSessions: Int = 0,
    val totalDistanceKm: Float = 0f,
    val longestSessionMinutes: Int = 0,
    val averageSpeedKmh: Int = 0,
    val chartEntries: ImmutableList<RouteChartEntry> = persistentListOf(),
    val recentSessions: ImmutableList<RouteSessionSummary> = persistentListOf(),
    val gpsStatus: RoutesGpsStatus = RoutesGpsStatus(),
    val hasAnyRoutes: Boolean = false,
) {
    val latestSession: RouteSessionSummary?
        get() = recentSessions.firstOrNull()
}

@Immutable
data class RouteChartEntry(
    val label: String,
    val value: Float,
)

@Immutable
data class RouteSessionSummary(
    val sessionId: String,
    val title: String,
    val startedAtLabel: String,
    val durationMinutes: Int,
    val distanceKm: Float,
    val averageSpeedKmh: Int,
    val peakSpeedKmh: Int,
    val altitudeRangeMeters: Int,
    val superspeedMoments: Int,
    val sampleCount: Int,
)

@Immutable
data class RoutesGpsStatus(
    val quality: GpsQuality = GpsQuality.PermissionRequired,
    val isRecordingEnabled: Boolean = true,
    val usesNetworkLocation: Boolean = true,
    val updateIntervalSeconds: Int = 2,
    val statusLine: String = "",
)

enum class GpsQuality {
    PermissionRequired,
    RecordingDisabled,
    Balanced,
    Precise,
}

internal data class RoutePoint(
    val timestamp: Long,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val track: String,
    val mode: String,
    val speedKmh: Double,
    val altitudeMeters: Double?,
)

internal data class RouteMetrics(
    val sessions: List<RouteSessionSummary>,
    val totalDistanceKm: Float,
    val longestSessionMinutes: Int,
    val averageSpeedKmh: Int,
)

class RoutesController(
    private val appContext: Context,
) {
    private val _uiState = MutableStateFlow(loadRoutesUiState(appContext))
    val uiState: StateFlow<RoutesUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.update { loadRoutesUiState(appContext) }
    }
}

internal fun loadRoutesUiState(context: Context): RoutesUiState {
    val rawPoints = RouteRecorder.readAll(context)
        .mapNotNull(::jsonToRoutePoint)
        .sortedByDescending { it.timestamp }
    val metrics = calculateRouteMetrics(rawPoints)
    val gpsStatus = loadGpsStatus(context)
    return RoutesUiState(
        totalSessions = metrics.sessions.size,
        totalDistanceKm = metrics.totalDistanceKm,
        longestSessionMinutes = metrics.longestSessionMinutes,
        averageSpeedKmh = metrics.averageSpeedKmh,
        chartEntries = metrics.sessions
            .take(7)
            .reversed()
            .map { session ->
                RouteChartEntry(
                    label = session.startedAtLabel.takeLast(5),
                    value = session.distanceKm,
                )
            }
            .toImmutableList(),
        recentSessions = metrics.sessions.take(8).toImmutableList(),
        gpsStatus = gpsStatus,
        hasAnyRoutes = metrics.sessions.isNotEmpty(),
    )
}

internal fun calculateRouteMetrics(points: List<RoutePoint>): RouteMetrics {
    if (points.isEmpty()) {
        return RouteMetrics(
            sessions = emptyList(),
            totalDistanceKm = 0f,
            longestSessionMinutes = 0,
            averageSpeedKmh = 0,
        )
    }

    val sessions = points
        .groupBy { it.sessionId }
        .values
        .map { sessionPoints -> buildSessionSummary(sessionPoints.sortedBy { it.timestamp }) }
        .sortedByDescending { it.startedAtLabel }

    val totalDistanceKm = sessions.sumOf { it.distanceKm.toDouble() }.toFloat()
    val longestSessionMinutes = sessions.maxOfOrNull { it.durationMinutes } ?: 0
    val averageSpeedKmh = sessions
        .map { it.averageSpeedKmh }
        .average()
        .takeIf { !it.isNaN() }
        ?.roundToInt()
        ?: 0

    return RouteMetrics(
        sessions = sessions,
        totalDistanceKm = ((totalDistanceKm * 10).roundToInt() / 10f),
        longestSessionMinutes = longestSessionMinutes,
        averageSpeedKmh = averageSpeedKmh,
    )
}

internal fun loadGpsStatus(context: Context): RoutesGpsStatus {
    val prefs = context.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
    val hasPermission = hasRoutesLocationPermission(context)
    val isRecordingEnabled = prefs.getBoolean("record_routes", true)
    val usesNetworkLocation = prefs.getBoolean("use_network_location", true)
    val updateIntervalSeconds = prefs.getInt("interval_sec", 2).coerceIn(1, 60)
    return buildGpsStatus(
        hasPermission = hasPermission,
        isRecordingEnabled = isRecordingEnabled,
        usesNetworkLocation = usesNetworkLocation,
        updateIntervalSeconds = updateIntervalSeconds,
    )
}

internal fun buildGpsStatus(
    hasPermission: Boolean,
    isRecordingEnabled: Boolean,
    usesNetworkLocation: Boolean,
    updateIntervalSeconds: Int,
): RoutesGpsStatus {
    val quality = when {
        !hasPermission -> GpsQuality.PermissionRequired
        !isRecordingEnabled -> GpsQuality.RecordingDisabled
        usesNetworkLocation -> GpsQuality.Balanced
        else -> GpsQuality.Precise
    }
    val statusLine = when (quality) {
        GpsQuality.PermissionRequired -> "Разрешение на геолокацию не выдано"
        GpsQuality.RecordingDisabled -> "Запись маршрутов отключена в настройках"
        GpsQuality.Balanced -> "GPS + сети, интервал $updateIntervalSeconds с"
        GpsQuality.Precise -> "Только GPS, интервал $updateIntervalSeconds с"
    }
    return RoutesGpsStatus(
        quality = quality,
        isRecordingEnabled = isRecordingEnabled,
        usesNetworkLocation = usesNetworkLocation,
        updateIntervalSeconds = updateIntervalSeconds.coerceIn(1, 60),
        statusLine = statusLine,
    )
}

internal fun hasRoutesLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun buildSessionSummary(points: List<RoutePoint>): RouteSessionSummary {
    val first = points.first()
    val last = points.last()
    val durationMinutes = (((last.timestamp - first.timestamp).coerceAtLeast(0L)) / 60_000L)
        .toInt()
        .coerceAtLeast(1)
    val distanceMeters = points.zipWithNext().sumOf { (a, b) ->
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }
    val distanceKm = ((distanceMeters / 100.0).roundToInt() / 10f).coerceAtLeast(0f)
    val speeds = points.map { it.speedKmh }.filter { it >= 0.0 }
    val averageSpeedKmh = speeds.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
    val peakSpeedKmh = speeds.maxOrNull()?.roundToInt() ?: 0
    val altitudeValues = points.mapNotNull { it.altitudeMeters }
    val altitudeRangeMeters = if (altitudeValues.isEmpty()) {
        0
    } else {
        (altitudeValues.maxOrNull()!! - altitudeValues.minOrNull()!!).roundToInt()
    }
    val superspeedMoments = points.count { it.mode.equals("superspeed", ignoreCase = true) }
    return RouteSessionSummary(
        sessionId = first.sessionId,
        title = first.track.ifBlank { "Session ${first.sessionId.takeLast(4)}" },
        startedAtLabel = formatTimestamp(first.timestamp),
        durationMinutes = durationMinutes,
        distanceKm = distanceKm,
        averageSpeedKmh = averageSpeedKmh,
        peakSpeedKmh = peakSpeedKmh,
        altitudeRangeMeters = altitudeRangeMeters,
        superspeedMoments = superspeedMoments,
        sampleCount = points.size,
    )
}

private fun jsonToRoutePoint(obj: JSONObject): RoutePoint? {
    val sessionId = obj.optString("sid")
    val track = obj.optString("track")
    if (sessionId.isBlank() || track.isBlank()) return null
    return RoutePoint(
        timestamp = obj.optLong("t"),
        sessionId = sessionId,
        latitude = obj.optDouble("lat"),
        longitude = obj.optDouble("lon"),
        track = track,
        mode = obj.optString("mode"),
        speedKmh = obj.optDouble("speed", 0.0),
        altitudeMeters = if (obj.isNull("alt")) null else obj.optDouble("alt"),
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val zoned = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    val day = zoned.dayOfMonth.toString().padStart(2, '0')
    val month = zoned.monthValue.toString().padStart(2, '0')
    val hour = zoned.hour.toString().padStart(2, '0')
    val minute = zoned.minute.toString().padStart(2, '0')
    return "$day.$month $hour:$minute"
}

private fun haversineMeters(
    startLat: Double,
    startLon: Double,
    endLat: Double,
    endLon: Double,
): Double {
    val dLat = Math.toRadians(endLat - startLat)
    val dLon = Math.toRadians(endLon - startLon)
    val originLat = Math.toRadians(startLat)
    val destinationLat = Math.toRadians(endLat)
    val a = sin(dLat / 2).let { it * it } +
        cos(originLat) * cos(destinationLat) * sin(dLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}
