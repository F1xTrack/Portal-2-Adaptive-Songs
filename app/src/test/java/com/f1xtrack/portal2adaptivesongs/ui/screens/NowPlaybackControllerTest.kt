package com.f1xtrack.portal2adaptivesongs.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlaybackControllerTest {

    @Test
    fun resolvePlaybackMode_entersSuperSpeedWhenThresholdReached() {
        assertEquals(
            PlaybackMode.SuperSpeed,
            resolvePlaybackMode(
                speedKmh = 12f,
                thresholdKmh = 10,
                hysteresisKmh = 3,
                wasSuperSpeed = false,
            ),
        )
    }

    @Test
    fun resolvePlaybackMode_keepsSuperSpeedInsideHysteresisWindow() {
        assertEquals(
            PlaybackMode.SuperSpeed,
            resolvePlaybackMode(
                speedKmh = 8.5f,
                thresholdKmh = 10,
                hysteresisKmh = 3,
                wasSuperSpeed = true,
            ),
        )
    }

    @Test
    fun resolvePlaybackMode_leavesSuperSpeedBelowCooldownBoundary() {
        assertEquals(
            PlaybackMode.Normal,
            resolvePlaybackMode(
                speedKmh = 6f,
                thresholdKmh = 10,
                hysteresisKmh = 3,
                wasSuperSpeed = true,
            ),
        )
    }

    @Test
    fun formatDurationLabel_formatsMinutesAndSeconds() {
        assertEquals("2:05", formatDurationLabel(125_000))
    }

    @Test
    fun formatDurationLabel_handlesUnknownDuration() {
        assertEquals("—", formatDurationLabel(0))
    }
}
