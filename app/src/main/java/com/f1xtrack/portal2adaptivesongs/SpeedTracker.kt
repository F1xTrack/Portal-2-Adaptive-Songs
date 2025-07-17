package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log

/**
 * Класс для отслеживания скорости движения пользователя
 * 
 * Основные функции:
 * - Получение данных о скорости через GPS и сетевые провайдеры
 * - Обнаружение резких изменений скорости (burst)
 * - Предотвращение частых срабатываний через cooldown
 * - Уведомление о изменениях скорости через колбэки
 * 
 * Используется для адаптивного переключения музыки в зависимости от скорости движения
 */
class SpeedTracker(
    context: Context,
    private val onSpeedBurst: (Float) -> Unit,      // Колбэк при резком изменении скорости
    private val onSpeedChange: ((Float) -> Unit)? = null  // Колбэк при любом изменении скорости
) {
    
    // Менеджер местоположения для получения GPS данных
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    // Состояние отслеживания
    private var lastSpeed = 0f      // Последняя зафиксированная скорость
    private var lastTrigger = 0L    // Время последнего срабатывания burst
    private var threshold: Float = 10f  // Порог для срабатывания burst (км/ч)

    /**
     * Установка порога скорости для срабатывания burst
     * @param value Порог в км/ч
     */
    fun setThreshold(value: Float) {
        threshold = value
    }
    
    /**
     * Получение текущего порога скорости
     * @return Порог в км/ч
     */
    fun getThreshold(): Float = threshold

    /**
     * Запуск отслеживания скорости
     * Подписывается на обновления от GPS и сетевых провайдеров
     */
    fun start() {
        val listener = LocationListener { loc: Location ->
            // Конвертируем скорость из м/с в км/ч
            val speedKmh = loc.speed * 3.6f
            val now = System.currentTimeMillis()
            
            Log.d("SpeedTracker", "Provider=${loc.provider} speed=$speedKmh")
            
            // Уведомляем о любом изменении скорости
            onSpeedChange?.invoke(speedKmh)
            
            // Проверяем условия для срабатывания burst:
            // 1. Увеличение скорости на величину порога или больше
            // 2. Прошло достаточно времени с последнего срабатывания (10 секунд)
            if (speedKmh - lastSpeed >= threshold && now - lastTrigger > 10_000) {
                lastTrigger = now
                onSpeedBurst(speedKmh)
            }
            
            // Сохраняем текущую скорость для следующего сравнения
            lastSpeed = speedKmh
        }

        // Подписываемся на оба провайдера для максимальной точности
        // GPS_PROVIDER - более точный, но может быть недоступен в помещении
        // NETWORK_PROVIDER - менее точный, но работает везде
        // Также поддерживает mock-локации для тестирования
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
    }
}
