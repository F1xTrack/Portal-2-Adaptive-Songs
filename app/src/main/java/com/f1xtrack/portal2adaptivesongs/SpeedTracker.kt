package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log

class SpeedTracker(
    context: Context,
    private val onSpeedBurst: (Float) -> Unit,
    private val onSpeedChange: (Float) -> Unit
) {
    companion object {
        private const val TAG = "SpeedTracker"
        private const val UPDATE_INTERVAL = 1000L
        private const val MIN_DISTANCE = 0f
        private const val BURST_COOLDOWN = 10_000L
        private const val SPEED_MULTIPLIER = 3.6f // м/с в км/ч
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastSpeed = 0f
    private var lastBurstTime = 0L
    private var speedThreshold: Float = 10f
    private var isTracking = false

    private val locationListener = LocationListener { location ->
        val speedKmh = location.speed * SPEED_MULTIPLIER
        val currentTime = System.currentTimeMillis()
        
        Log.d(TAG, "Location update: provider=${location.provider}, speed=$speedKmh km/h")
        
        // Уведомляем об изменении скорости
        onSpeedChange(speedKmh)
        
        // Проверяем условие для burst события
        if (shouldTriggerBurst(speedKmh, currentTime)) {
            lastBurstTime = currentTime
            onSpeedBurst(speedKmh)
        }
        
        lastSpeed = speedKmh
    }

    fun setThreshold(value: Float) {
        speedThreshold = value
        Log.d(TAG, "Speed threshold set to: $value km/h")
    }

    fun getThreshold(): Float = speedThreshold

    fun start() {
        if (isTracking) {
            Log.w(TAG, "Speed tracking is already active")
            return
        }

        try {
            // Подписываемся на оба провайдера для лучшего покрытия
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL,
                MIN_DISTANCE,
                locationListener
            )
            
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                UPDATE_INTERVAL,
                MIN_DISTANCE,
                locationListener
            )
            
            isTracking = true
            Log.d(TAG, "Speed tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speed tracking", e)
        }
    }

    fun stop() {
        if (!isTracking) {
            return
        }

        try {
            locationManager.removeUpdates(locationListener)
            isTracking = false
            Log.d(TAG, "Speed tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speed tracking", e)
        }
    }

    private fun shouldTriggerBurst(currentSpeed: Float, currentTime: Long): Boolean {
        val speedIncrease = currentSpeed - lastSpeed
        val timeSinceLastBurst = currentTime - lastBurstTime
        
        return speedIncrease >= speedThreshold && timeSinceLastBurst > BURST_COOLDOWN
    }
}
