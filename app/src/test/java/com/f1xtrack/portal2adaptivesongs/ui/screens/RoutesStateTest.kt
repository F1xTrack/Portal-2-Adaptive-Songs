package com.f1xtrack.portal2adaptivesongs.ui.screens

import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesStateTest {

    @Test
    fun calculateRouteMetrics_aggregatesSessionDistanceAndSpeed() {
        val points = listOf(
            point("sid-1", 0L, 55.0, 37.0, 9.0, 120.0, "Portal Run", "normal"),
            point("sid-1", 60_000L, 55.0, 37.001, 11.0, 126.0, "Portal Run", "superspeed"),
            point("sid-1", 120_000L, 55.0, 37.002, 13.0, 135.0, "Portal Run", "superspeed"),
        )

        val metrics = calculateRouteMetrics(points)

        assertEquals(1, metrics.sessions.size)
        assertTrue(metrics.totalDistanceKm > 0f)
        assertEquals(2, metrics.longestSessionMinutes)
        assertEquals(11, metrics.averageSpeedKmh)
        assertEquals(2, metrics.sessions.first().superspeedMoments)
    }

    @Test
    fun buildGpsStatus_reflectsDisabledRecordingAndProviderChoice() {
        val status = buildGpsStatus(
            hasPermission = true,
            isRecordingEnabled = false,
            usesNetworkLocation = false,
            updateIntervalSeconds = 9,
        )

        assertEquals(9, status.updateIntervalSeconds)
        assertEquals(GpsQuality.RecordingDisabled, status.quality)
        assertEquals(false, status.isRecordingEnabled)
        assertEquals(false, status.usesNetworkLocation)
    }

    @Test
    fun loadRoutesUiState_buildsRecentSessionsAndChart() {
        val raw = listOf(
            point("s1", 1_000L, 55.0, 37.0, 8.0, 100.0, "A", "normal"),
            point("s1", 61_000L, 55.0, 37.001, 10.0, 104.0, "A", "superspeed"),
            point("s2", 121_000L, 55.1, 37.1, 6.0, 90.0, "B", "normal"),
            point("s2", 181_000L, 55.1, 37.101, 7.0, 91.0, "B", "normal"),
        )
        val metrics = calculateRouteMetrics(raw)

        val state = RoutesUiState(
            totalSessions = metrics.sessions.size,
            recentSessions = metrics.sessions.toImmutableList(),
            chartEntries = metrics.sessions.map {
                RouteChartEntry(it.startedAtLabel.takeLast(5), it.distanceKm)
            }.toImmutableList(),
            hasAnyRoutes = true,
        )

        assertEquals(2, state.totalSessions)
        assertEquals(2, state.recentSessions.size)
        assertEquals(2, state.chartEntries.size)
        assertTrue(state.hasAnyRoutes)
    }

    private fun point(
        sid: String,
        time: Long,
        lat: Double,
        lon: Double,
        speed: Double,
        altitude: Double,
        track: String,
        mode: String,
    ): RoutePoint {
        return RoutePoint(
            timestamp = time,
            sessionId = sid,
            latitude = lat,
            longitude = lon,
            track = track,
            mode = mode,
            speedKmh = speed,
            altitudeMeters = altitude,
        )
    }
}
