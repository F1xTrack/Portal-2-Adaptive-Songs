package com.f1xtrack.portal2adaptivesongs

import android.view.LayoutInflater
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.random.Random

class TimeAttackManager(
    private val host: AppCompatActivity,
    private val onStartRandomTrack: () -> Unit,
    private val thresholdProvider: () -> Int
) {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledRunnable: Runnable? = null

    private var dialog: AlertDialog? = null
    private var progress: LinearProgressIndicator? = null
    private var desc: TextView? = null

    private var active = false
    private var targetSeconds = 0
    private var elapsedSeconds = 0
    private var tickRunnable: Runnable? = null
    private var currentSpeed: Float = 0f

    fun scheduleRandomAfterMinutes(minMinutes: Int) {
        cancelSchedule()
        val extra = Random.nextInt(0, 11) // +0..10 минут случайно
        val delayMs = (minMinutes + extra) * 60_000L
        val r = Runnable { startNow() }
        scheduledRunnable = r
        handler.postDelayed(r, delayMs)
    }

    fun startNow() {
        cancelSchedule()
        if (active) return
        // Запускаем случайный трек для испытания
        onStartRandomTrack()
        // Настройка сложности
        val prefs = host.getSharedPreferences("time_attack_prefs", AppCompatActivity.MODE_PRIVATE)
        val diff = prefs.getString("difficulty", "normal")
        targetSeconds = when (diff) {
            "easy" -> 30
            "hard" -> 120
            else -> 60
        }
        elapsedSeconds = 0
        showDialog()
        startTicks()
    }

    fun onSpeed(speedKmh: Float) {
        currentSpeed = speedKmh
    }

    fun dispose() {
        cancelSchedule()
        stopTicks()
        dialog?.dismiss()
        dialog = null
    }

    private fun cancelSchedule() {
        scheduledRunnable?.let { handler.removeCallbacks(it) }
        scheduledRunnable = null
    }

    private fun startTicks() {
        active = true
        stopTicks()
        val r = object : Runnable {
            override fun run() {
                if (!active) return
                val threshold = thresholdProvider()
                // Провал, если скорость ниже порога
                if (currentSpeed < threshold) {
                    finish(false)
                    return
                }
                elapsedSeconds += 2
                val pct = (elapsedSeconds * 100 / targetSeconds).coerceIn(0, 100)
                progress?.progress = pct
                updateDesc(threshold, (targetSeconds - elapsedSeconds).coerceAtLeast(0))
                if (elapsedSeconds >= targetSeconds) {
                    finish(true)
                } else {
                    handler.postDelayed(this, 2000L)
                }
            }
        }
        tickRunnable = r
        handler.postDelayed(r, 2000L)
    }

    private fun stopTicks() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun finish(success: Boolean) {
        active = false
        stopTicks()
        try {
            dialog?.dismiss()
        } catch (_: Exception) {}
        dialog = null
        val msg = if (success) R.string.time_attack_success else R.string.time_attack_fail
        android.widget.Toast.makeText(host, host.getString(msg), android.widget.Toast.LENGTH_LONG).show()
        // Планируем следующий запуск через 10 минут минимум
        scheduleRandomAfterMinutes(minMinutes = 10)
    }

    private fun showDialog() {
        val inflater: LayoutInflater = host.layoutInflater
        val view = inflater.inflate(R.layout.dialog_time_attack, null)
        progress = view.findViewById(R.id.progressTimeAttack)
        desc = view.findViewById(R.id.textTimeAttackDesc)
        progress?.max = 100
        progress?.progress = 0
        updateDesc(thresholdProvider(), targetSeconds)

        dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(host, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.time_attack_title)
            .setView(view)
            .setCancelable(false)
            .setNegativeButton(R.string.time_attack_cancel) { d, _ ->
                finish(false)
                d.dismiss()
            }
            .create()
        dialog?.show()
    }

    private fun updateDesc(threshold: Int, remainSec: Int) {
        desc?.text = host.getString(R.string.time_attack_desc_keep_above, threshold, remainSec)
    }
}
