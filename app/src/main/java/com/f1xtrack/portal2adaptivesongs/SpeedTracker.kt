package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log

class SpeedTracker(
    context: Context,
    private val onSpeedBurst: (Float) -> Unit,
    private val onSpeedChange: ((Float) -> Unit)? = null,
    private val onDistanceMeters: ((Float) -> Unit)? = null,
    private val onLocation: ((Location) -> Unit)? = null
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastSpeed = 0f
    private var lastTrigger = 0L
    private var threshold: Float = 10f
    private var lastLocation: Location? = null
    private var listener: LocationListener? = null
    // Конфигурация провайдеров и частоты обновлений
    private var useNetworkProvider: Boolean = true
    private var updateIntervalMs: Long = 2000L

    fun setThreshold(value: Float) {
        threshold = value
    }
    fun getThreshold(): Float = threshold

    fun start() {
        // Stop previous listener if any
        listener?.let {
            try {
                lm.removeUpdates(it)
            } catch (_: Exception) {}
        }

        val newListener = LocationListener { loc: Location ->
            val speedKmh = loc.speed * 3.6f
            val now = System.currentTimeMillis()
            // Logging disabled per user request
            onSpeedChange?.invoke(speedKmh)
            if (speedKmh - lastSpeed >= threshold && now - lastTrigger > 10_000) {
                lastTrigger = now
                onSpeedBurst(speedKmh)
            }
            // distance
            lastLocation?.let { prev ->
                val delta = prev.distanceTo(loc)
                if (delta > 0f) onDistanceMeters?.invoke(delta)
            }
            lastLocation = loc
            lastSpeed = speedKmh
            onLocation?.invoke(loc)
        }
        listener = newListener
        // Подписки на провайдеры:
        // Всегда пробуем GPS; при включённой опции — добавляем провайдер сети (Wi‑Fi/мобильные сети)
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, updateIntervalMs, 0f, newListener) } catch (_: Exception) {}
        if (useNetworkProvider) {
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, updateIntervalMs, 0f, newListener) } catch (_: Exception) {}
        }
    }

    fun stop() {
        listener?.let {
            try { lm.removeUpdates(it) } catch (_: Exception) {}
        }
        listener = null
    }

    fun setUseNetworkLocation(enabled: Boolean) {
        useNetworkProvider = enabled
    }

    fun setUpdateIntervalMillis(ms: Long) {
        updateIntervalMs = if (ms < 1000L) 1000L else ms
    }

    fun setUpdateIntervalSeconds(sec: Int) {
        val clamped = if (sec < 1) 1 else if (sec > 60) 60 else sec
        updateIntervalMs = clamped * 1000L
    }
}
