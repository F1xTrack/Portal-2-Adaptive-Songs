package com.f1xtrack.portal2adaptivesongs

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * Вспомогательный объект для работы с разрешениями приложения
 * 
 * Основные функции:
 * - Проверка наличия разрешений
 * - Запрос разрешений у пользователя
 * - Обработка результатов запроса разрешений
 * 
 * Используется для получения разрешений на доступ к местоположению,
 * необходимых для отслеживания скорости движения
 */
object PermissionsHelper {
    
    /**
     * Запрос разрешения на доступ к точному местоположению
     * 
     * Проверяет, есть ли уже разрешение. Если нет - запрашивает его у пользователя.
     * Если разрешение уже есть - сразу вызывает колбэк onGranted.
     * 
     * @param activity Активность, которая запрашивает разрешение
     * @param onGranted Колбэк, который вызывается при получении разрешения
     */
    fun requestLocation(activity: Activity, onGranted: () -> Unit) {
        // Проверяем, есть ли уже разрешение на точное местоположение
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Если разрешения нет - запрашиваем его у пользователя
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001 // Код запроса для идентификации в onRequestPermissionsResult
            )
        } else {
            // Если разрешение уже есть - сразу вызываем колбэк
            onGranted()
        }
    }
}
