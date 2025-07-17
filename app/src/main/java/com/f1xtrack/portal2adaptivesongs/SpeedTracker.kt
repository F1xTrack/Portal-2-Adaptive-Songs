package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log

class SpeedTracker(
    context: Context,
    private val onSpeedBurst: (Float) -> Unit,
    private val onSpeedChange: ((Float) -> Unit)? = null
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastSpeed = 0f
    private var lastTrigger = 0L
    private var threshold: Float = 10f

    fun setThreshold(value: Float) {
        threshold = value
    }
    fun getThreshold(): Float = threshold

    fun start() {
        val listener = LocationListener { loc: Location ->
            val speedKmh = loc.speed * 3.6f
            val now = System.currentTimeMillis()
            Log.d("SpeedTracker", "Provider=${loc.provider} speed=$speedKmh")
            onSpeedChange?.invoke(speedKmh)
            if (speedKmh - lastSpeed >= threshold && now - lastTrigger > 10_000) {
                lastTrigger = now
                onSpeedBurst(speedKmh)
            }
            lastSpeed = speedKmh
        }

        // Подписываемся на оба провайдера для mock‑локаций
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
    }
}
