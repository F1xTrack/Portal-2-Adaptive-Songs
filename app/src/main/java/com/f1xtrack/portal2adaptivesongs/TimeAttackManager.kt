package com.f1xtrack.portal2adaptivesongs

import android.view.LayoutInflater
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.random.Random
import com.f1xtrack.portal2adaptivesongs.data.timeattack.TimeAttackDatabase
import com.f1xtrack.portal2adaptivesongs.data.timeattack.TimeAttackResultEntity

class TimeAttackManager(
    private val host: AppCompatActivity,
    private val onStartRandomTrack: () -> Unit,
    private val thresholdProvider: () -> Int
) {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledRunnable: Runnable? = null

    private var dialog: AlertDialog? = null
    private var progress: LinearProgressIndicator? = null
    private var titleView: TextView? = null
    private var desc: TextView? = null

    private var active = false
    private var tickRunnable: Runnable? = null
    private var currentSpeed: Float = 0f
    private var startedAtEpochSec: Long = 0L
    private var lastTickMs: Long = 0L
    private var leftoverMs: Int = 0
    private val completedThisSession = mutableSetOf<String>()
    private var firstChallengeShown = false

    // --- Challenge model ---
    private enum class Difficulty { EASY, NORMAL, HARD, EXTREME }

    private enum class Regime { NORMAL, SUPERSPEED }

    private enum class ChallengeType {
        HOLD_SS,                    // Удержание superspeed непрерывно
        HOLD_NORMAL_NO_SS,          // Движение только в normal без входа в SS
        INTERVALS,                  // Интервалы N<->SS по шаблону
        TOTAL_DISTANCE_TIME,        // Пройти дистанцию за время (режимы не важны)
        LIMIT_SWITCHES,             // Ограничение количества переключений за период
        BURSTS_SS,                  // Количество SS-спринтов по min длительности
        NO_STOPS,                   // Без остановок
        SS_THEN_HOLD_NORMAL,        // Сначала SS, потом удержание normal
        SUM_SS_TIME,                // Суммарное время в SS за период
        EXACT_SWITCHES,             // Ровно N переключений за период
        LADDER_SS,                  // Лесенка SS: 10→15→20→...
        NEGATIVE_SPLIT_DISTANCE     // Негативный сплит на дистанции
    }

    private data class ChallengeSpec(
        val id: String,
        val difficulty: Difficulty,
        val type: ChallengeType,
        // Общие параметры
        val timeLimitSec: Int? = null,           // общий лимит времени испытания
        val distanceMeters: Int? = null,         // целевая дистанция
        // HOLD_SS
        val minHoldSsSec: Int? = null,
        val allowedDipSec: Int = 2,
        // HOLD_NORMAL_NO_SS
        val holdNormalSec: Int? = null,
        // INTERVALS
        val intervalsCycles: Int? = null,
        val intervalNormalSec: Int? = null,
        val intervalSsSec: Int? = null,
        // LIMIT_SWITCHES
        val periodSec: Int? = null,
        val maxSwitches: Int? = null,
        // BURSTS_SS
        val burstsCount: Int? = null,
        val burstMinSsSec: Int? = null,
        val burstMaxGapSec: Int? = null,
        // NO_STOPS
        val noStopsSec: Int? = null,
        // SS_THEN_HOLD_NORMAL
        val firstSsSec: Int? = null,
        val thenNormalSec: Int? = null,
        // SUM_SS_TIME
        val minSumSsSec: Int? = null,
        // EXACT_SWITCHES
        val exactSwitches: Int? = null,
        // LADDER_SS
        val ladderTargets: IntArray? = null,     // список секунд для последовательных SS-отрезков
        val ladderMaxRestSec: Int? = null,
        // NEGATIVE_SPLIT_DISTANCE
        val negativeSplitDistanceM: Int? = null,
        val negativeSplitDeltaPct: Int? = null
    )

    // Внутреннее состояние выполнения испытания
    private data class RuntimeState(
        var elapsedSec: Int = 0,
        var distanceM: Double = 0.0,
        var ssTimeSec: Int = 0,
        var normalTimeSec: Int = 0,
        var switches: Int = 0,
        var lastRegime: Regime = Regime.NORMAL,
        var currentRegime: Regime = Regime.NORMAL,
        var currentRegimeDurSec: Int = 0,
        var stoppedDurSec: Int = 0,
        // для BURSTS
        var burstsDone: Int = 0,
        var lastBurstTimeSecAgo: Int = Int.MAX_VALUE,
        // для INTERVALS
        var intervalStageIndex: Int = 0, // 0..(cycles*2-1)
        var intervalStageRemain: Int = 0,
        // для SS_THEN_HOLD_NORMAL
        var phase: Int = 0, // 0=до первого SS, 1=держим SS, 2=держим normal
        // для LADDER
        var ladderIndex: Int = 0,
        var ladderRestSec: Int = 0,
        var ladderHoldSec: Int = 0,
        // для NEGATIVE_SPLIT
        var splitHalfPassed: Boolean = false,
        var splitFirstHalfSec: Int = 0,
        var splitSecondHalfSec: Int = 0,
        var splitFirstHalfDist: Double = 0.0,
        var splitSecondHalfDist: Double = 0.0
    )

    private var currentChallenge: ChallengeSpec? = null
    private var state = RuntimeState()

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
        // Выбор испытания по сложности
        val prefs = host.getSharedPreferences("time_attack_prefs", AppCompatActivity.MODE_PRIVATE)
        val diff = when (prefs.getString("difficulty", "normal")) {
            "easy" -> Difficulty.EASY
            "hard" -> Difficulty.HARD
            "extreme" -> Difficulty.EXTREME
            else -> Difficulty.NORMAL
        }
        // Первая тайм-атака в сессии — всегда «Ровный старт» (E2), но только когда это уместно
        if (!firstChallengeShown) {
            val soft = CHALLENGES.firstOrNull { it.id == "E2" }
            if (soft != null && soft.id !in completedThisSession && isEligibleNow(soft)) {
                currentChallenge = soft
            } else {
                // Отложим до подходящего контекста
                scheduleRandomAfterMinutes(minMinutes = 5)
                return
            }
        } else {
            currentChallenge = pickRandomChallenge(diff)
            if (currentChallenge == null) {
                // Нечего запускать сейчас — попробуем позже
                scheduleRandomAfterMinutes(minMinutes = 5)
                return
            }
        }
        // Запускаем случайный трек для испытания только если есть задача
        onStartRandomTrack()
        state = RuntimeState()
        startedAtEpochSec = System.currentTimeMillis() / 1000L
        showDialog()
        startTicks()
        firstChallengeShown = true
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
                val ch = currentChallenge
                if (ch == null) {
                    finish(false)
                    return
                }
                // Реальное время и накопление секунд для корректной логики
                if (lastTickMs == 0L) lastTickMs = SystemClock.elapsedRealtime()
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - lastTickMs).toInt().coerceAtLeast(0)
                lastTickMs = now
                leftoverMs += elapsedMs
                var advancedSec = 0
                while (leftoverMs >= 1000) {
                    leftoverMs -= 1000
                    advancedSec += 1
                }

                val threshold = thresholdProvider().toFloat()

                val regime = if (currentSpeed >= threshold) Regime.SUPERSPEED else Regime.NORMAL
                if (advancedSec > 0) {
                    // Обновление состояния целыми секундами
                    updateStateForTick(state, regime, advancedSec, currentSpeed)
                }

                // Оценка испытания (даже если advancedSec == 0, для обновления UI)
                val eval = evaluate(ch, state, advancedSec)

                // Плавное UI-обновление прогресса с учётом доли секунды
                val frac = (leftoverMs.coerceAtLeast(0)).toFloat() / 1000f
                val uiPct = computeUiProgress(ch, state, eval.progressPct, frac)
                progress?.setProgressCompat(uiPct.coerceIn(0, 100), true)
                updateDesc(eval.description)

                when (eval.outcome) {
                    Outcome.SUCCESS -> { finish(true); return }
                    Outcome.FAIL -> { finish(false); return }
                    Outcome.CONTINUE -> { /* продолжить */ }
                }

                handler.postDelayed(this, 100L)
            }
        }
        tickRunnable = r
        lastTickMs = 0L
        leftoverMs = 0
        handler.postDelayed(r, 100L)
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

        // Сохранить результат в Room
        val ch = currentChallenge
        if (ch != null) {
            val entity = TimeAttackResultEntity(
                challengeId = ch.id,
                difficulty = when (ch.difficulty) { Difficulty.EASY -> "easy"; Difficulty.NORMAL -> "normal"; Difficulty.HARD -> "hard"; Difficulty.EXTREME -> "extreme" },
                type = ch.type.name,
                success = success,
                startedAtEpochSec = startedAtEpochSec,
                durationSec = state.elapsedSec,
                distanceM = state.distanceM.toInt(),
                ssTimeSec = state.ssTimeSec,
                normalTimeSec = state.normalTimeSec,
                switches = state.switches
            )
            host.lifecycleScope.launch {
                runCatching {
                    TimeAttackDatabase.get(host.applicationContext).timeAttackResultDao().insert(entity)
                }
            }
            if (success) completedThisSession.add(ch.id)
        }
        // Планируем следующий запуск через 10 минут минимум
        scheduleRandomAfterMinutes(minMinutes = 10)
    }

    private fun showDialog() {
        val inflater: LayoutInflater = host.layoutInflater
        val view = inflater.inflate(R.layout.dialog_time_attack, null)
        progress = view.findViewById(R.id.progressTimeAttack)
        titleView = view.findViewById(R.id.textTimeAttackTitle)
        desc = view.findViewById(R.id.textTimeAttackDesc)
        progress?.max = 100
        progress?.progress = 0
        val ch = currentChallenge
        val title = ch?.let { getChallengeTitle(it) } ?: host.getString(R.string.time_attack_title)
        titleView?.text = title
        titleView?.setTextSize(24f) // Increase title prominence
        updateDesc(ch?.let { getChallengeDesc(it) } ?: "")

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

    private fun updateDesc(text: String) {
        desc?.text = text
    }

    // --- Оценка прогресса ---
    private enum class Outcome { CONTINUE, SUCCESS, FAIL }

    private data class Eval(
        val progressPct: Int,
        val outcome: Outcome,
        val description: String
    )

    private fun updateStateForTick(state: RuntimeState, regime: Regime, dtSec: Int, speedKmh: Float) {
        state.elapsedSec += dtSec
        val was = state.currentRegime
        state.currentRegime = regime
        if (regime != was) {
            state.switches += 1
            state.currentRegimeDurSec = 0
        }
        state.currentRegimeDurSec += dtSec
        if (regime == Regime.SUPERSPEED) state.ssTimeSec += dtSec else state.normalTimeSec += dtSec
        // расстояние (приближённо)
        val mps = (speedKmh * 1000.0 / 3600.0)
        state.distanceM += mps * dtSec
        // остановки
        if (speedKmh < 0.5f) state.stoppedDurSec += dtSec else state.stoppedDurSec = 0
        // для BURSTS учтём «время с момента последнего SS» (если сейчас N)
        if (regime == Regime.SUPERSPEED) {
            state.lastBurstTimeSecAgo = 0
        } else {
            if (state.lastBurstTimeSecAgo < Int.MAX_VALUE) state.lastBurstTimeSecAgo += dtSec
        }
    }

    private fun evaluate(spec: ChallengeSpec, s: RuntimeState, dtSec: Int): Eval {
        // Прогресс/описание по умолчанию
        fun progressPct(done: Int, total: Int) = (done * 100 / total).coerceIn(0, 100)
        val threshold = thresholdProvider()

        when (spec.type) {
            ChallengeType.HOLD_SS -> {
                val hold = spec.minHoldSsSec ?: 0
                val timeLimit = spec.timeLimitSec ?: (hold * 4)
                // Непрерывность с допуском
                val inSS = s.currentRegime == Regime.SUPERSPEED
                val ok = if (inSS) s.currentRegimeDurSec else 0
                val dipsTooLong = !inSS && (s.currentRegimeDurSec > spec.allowedDipSec)
                if (dipsTooLong) return Eval(progressPct(0, 1), Outcome.FAIL, textBase(spec, s, threshold))
                if (ok >= hold) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                if (s.elapsedSec >= timeLimit) return Eval(progressPct(ok, hold), Outcome.FAIL, textBase(spec, s, threshold))
                return Eval(progressPct(ok, hold), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.HOLD_NORMAL_NO_SS -> {
                val need = spec.holdNormalSec ?: 0
                val timeLimit = spec.timeLimitSec ?: need
                if (s.currentRegime == Regime.SUPERSPEED) return Eval(progressPct(0, 1), Outcome.FAIL, textBase(spec, s, threshold))
                if (s.normalTimeSec >= need) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                if (s.elapsedSec >= timeLimit) return Eval(progressPct(s.normalTimeSec, need), Outcome.FAIL, textBase(spec, s, threshold))
                return Eval(progressPct(s.normalTimeSec, need), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.INTERVALS -> {
                val cycles = spec.intervalsCycles ?: 0
                val nSec = spec.intervalNormalSec ?: 0
                val ssSec = spec.intervalSsSec ?: 0
                if (s.intervalStageIndex == 0 && s.intervalStageRemain == 0) {
                    s.intervalStageIndex = 0
                    s.intervalStageRemain = nSec
                    s.currentRegime = s.currentRegime // keep
                }
                val totalStages = cycles * 2
                val needRegime = if (s.intervalStageIndex % 2 == 0) Regime.NORMAL else Regime.SUPERSPEED
                // Допуск 2 сек
                val good = if (s.currentRegime == needRegime || s.currentRegimeDurSec <= 2) true else false
                if (!good) return Eval(progressPct(s.intervalStageIndex, totalStages), Outcome.FAIL, textBase(spec, s, threshold))
                s.intervalStageRemain -= dtSec
                if (s.intervalStageRemain <= 0) {
                    s.intervalStageIndex += 1
                    if (s.intervalStageIndex >= totalStages) {
                        return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                    }
                    s.intervalStageRemain = if (s.intervalStageIndex % 2 == 0) nSec else ssSec
                }
                val done = s.intervalStageIndex
                return Eval(progressPct(done, totalStages), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.TOTAL_DISTANCE_TIME -> {
                val needM = spec.distanceMeters ?: 0
                val limit = spec.timeLimitSec ?: (needM / 1.5).toInt() // грубая оценка
                if (s.distanceM >= needM) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                if (s.elapsedSec >= limit) return Eval(progressPct(s.distanceM.toInt(), needM), Outcome.FAIL, textBase(spec, s, threshold))
                return Eval(progressPct(s.distanceM.toInt(), needM), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.LIMIT_SWITCHES -> {
                val limitTime = spec.periodSec ?: 0
                val maxSw = spec.maxSwitches ?: 0
                if (s.elapsedSec >= limitTime) {
                    val ok = s.switches <= maxSw
                    return Eval(100, if (ok) Outcome.SUCCESS else Outcome.FAIL, textBase(spec, s, threshold))
                }
                return Eval(progressPct(s.elapsedSec, limitTime), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.BURSTS_SS -> {
                val needCount = spec.burstsCount ?: 0
                val minSec = spec.burstMinSsSec ?: 0
                val maxGap = spec.burstMaxGapSec ?: 60
                // Завершённый спринт: переход из SS в N при длительности SS >= minSec
                if (s.currentRegime == Regime.SUPERSPEED && s.currentRegimeDurSec >= minSec) {
                    // ждём выхода в normal, чтобы засчитать burst
                } else if (s.currentRegime == Regime.NORMAL && s.lastRegime == Regime.SUPERSPEED && s.currentRegimeDurSec <= dtSec) {
                    // только что переключились SS->N
                    if (s.currentRegimeDurSec <= dtSec && s.switches >= 1) {
                        // Проверка длительности предыдущего SS по currentRegimeDurSec недоступна, поэтому приблизим: если последняя SS длилась >= minSec, она уже добавлена
                    }
                }
                // Упрощение: считаем burst, когда текущая длительность в SS достигает minSec и затем сбрасывается при выходе в N
                if (s.currentRegime == Regime.SUPERSPEED && s.currentRegimeDurSec == minSec) {
                    s.burstsDone += 1
                    s.lastBurstTimeSecAgo = 0
                }
                if (s.lastBurstTimeSecAgo > maxGap) return Eval(progressPct(s.burstsDone, needCount), Outcome.FAIL, textBase(spec, s, threshold))
                if (s.burstsDone >= needCount) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                val progress = (s.burstsDone * 100 / needCount)
                return Eval(progress, Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.NO_STOPS -> {
                val need = spec.noStopsSec ?: 0
                if (s.stoppedDurSec > 2) return Eval(progressPct(0, 1), Outcome.FAIL, textBase(spec, s, threshold))
                if (s.elapsedSec >= need) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                return Eval(progressPct(s.elapsedSec, need), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.SS_THEN_HOLD_NORMAL -> {
                val ssNeed = spec.firstSsSec ?: 0
                val nNeed = spec.thenNormalSec ?: 0
                when (s.phase) {
                    0 -> if (s.currentRegime == Regime.SUPERSPEED) s.phase = 1
                    1 -> if (s.currentRegime == Regime.SUPERSPEED && s.currentRegimeDurSec >= ssNeed) s.phase = 2
                    2 -> if (s.currentRegime == Regime.NORMAL && s.currentRegimeDurSec >= nNeed) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                }
                val all = (ssNeed + nNeed).coerceAtLeast(1)
                val done = when (s.phase) { 0 -> 0; 1 -> (s.currentRegimeDurSec).coerceAtMost(ssNeed); else -> ssNeed + (s.currentRegimeDurSec).coerceAtMost(nNeed) }
                return Eval(progressPct(done, all), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.SUM_SS_TIME -> {
                val need = spec.minSumSsSec ?: 0
                val limit = spec.timeLimitSec ?: (need * 3)
                if (s.ssTimeSec >= need) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                if (s.elapsedSec >= limit) return Eval(progressPct(s.ssTimeSec, need), Outcome.FAIL, textBase(spec, s, threshold))
                return Eval(progressPct(s.ssTimeSec, need), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.EXACT_SWITCHES -> {
                val need = spec.exactSwitches ?: 0
                val limit = spec.timeLimitSec ?: 420
                if (s.switches > need) return Eval(100, Outcome.FAIL, textBase(spec, s, threshold))
                if (s.elapsedSec >= limit) return Eval(100, if (s.switches == need) Outcome.SUCCESS else Outcome.FAIL, textBase(spec, s, threshold))
                return Eval(progressPct(s.switches, need.coerceAtLeast(1)), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.LADDER_SS -> {
                val targets = spec.ladderTargets ?: intArrayOf()
                val maxRest = spec.ladderMaxRestSec ?: 5
                val idx = s.ladderIndex
                if (idx >= targets.size) return Eval(100, Outcome.SUCCESS, textBase(spec, s, threshold))
                if (s.currentRegime == Regime.SUPERSPEED) {
                    s.ladderHoldSec += dtSec
                    s.ladderRestSec = 0
                    if (s.ladderHoldSec >= targets[idx]) {
                        s.ladderIndex = idx + 1
                        s.ladderHoldSec = 0
                    }
                } else {
                    s.ladderRestSec += dtSec
                    s.ladderHoldSec = 0
                    if (s.ladderRestSec > maxRest) return Eval(progressPct(idx, targets.size), Outcome.FAIL, textBase(spec, s, threshold))
                }
                return Eval(progressPct(s.ladderIndex, targets.size), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
            ChallengeType.NEGATIVE_SPLIT_DISTANCE -> {
                val needDist = spec.negativeSplitDistanceM ?: 1000
                val deltaPct = (spec.negativeSplitDeltaPct ?: 10)
                if (!s.splitHalfPassed && s.distanceM >= needDist / 2.0) {
                    s.splitHalfPassed = true
                    s.splitFirstHalfSec = s.elapsedSec
                    s.splitFirstHalfDist = s.distanceM
                }
                if (s.splitHalfPassed) {
                    s.splitSecondHalfSec = s.elapsedSec - s.splitFirstHalfSec
                    s.splitSecondHalfDist = (s.distanceM - s.splitFirstHalfDist).coerceAtLeast(0.0)
                }
                if (s.distanceM >= needDist) {
                    val v1 = (s.splitFirstHalfDist / s.splitFirstHalfSec.coerceAtLeast(1))
                    val v2 = (s.splitSecondHalfDist / s.splitSecondHalfSec.coerceAtLeast(1))
                    val ok = v2 >= v1 * (1.0 + deltaPct / 100.0)
                    return Eval(100, if (ok) Outcome.SUCCESS else Outcome.FAIL, textBase(spec, s, threshold))
                }
                return Eval(progressPct(s.distanceM.toInt(), needDist), Outcome.CONTINUE, textBase(spec, s, threshold))
            }
        }
    }

    private fun textBase(spec: ChallengeSpec, s: RuntimeState, threshold: Int): String {
        val d = getChallengeDesc(spec)
        return buildString {
            appendLine(d)
            append("Время: ").append(s.elapsedSec).append(" с  •  ")
            append("Дист: ").append(s.distanceM.toInt()).append(" м  •  ")
            append("SS: ").append(s.ssTimeSec).append(" с  •  ")
            append("Перекл: ").append(s.switches).append("  •  Порог: ").append(threshold)
        }
    }

    // UI-only сглаживание прогресса (плавное движение ползунка между изменениями состояния)
    private fun computeUiProgress(spec: ChallengeSpec, s: RuntimeState, basePct: Int, fracSec: Float): Int {
        if (basePct >= 100) return 100
        val type = spec.type
        var pct = basePct.toFloat()
        when (type) {
            ChallengeType.HOLD_SS -> {
                val hold = (spec.minHoldSsSec ?: 0).toFloat()
                if (hold > 0f && s.currentRegime == Regime.SUPERSPEED) {
                    val num = (s.currentRegimeDurSec.toFloat() + fracSec).coerceAtMost(hold)
                    pct = (num / hold) * 100f
                }
            }
            ChallengeType.HOLD_NORMAL_NO_SS -> {
                val need = (spec.holdNormalSec ?: 0).toFloat()
                if (need > 0f && s.currentRegime == Regime.NORMAL) {
                    val num = (s.normalTimeSec.toFloat() + fracSec).coerceAtMost(need)
                    pct = (num / need) * 100f
                }
            }
            ChallengeType.LIMIT_SWITCHES -> {
                val limit = (spec.periodSec ?: 0).toFloat()
                if (limit > 0f) {
                    val num = (s.elapsedSec.toFloat() + fracSec).coerceAtMost(limit)
                    pct = (num / limit) * 100f
                }
            }
            ChallengeType.NO_STOPS -> {
                val need = (spec.noStopsSec ?: 0).toFloat()
                if (need > 0f) {
                    val num = (s.elapsedSec.toFloat() + fracSec).coerceAtMost(need)
                    pct = (num / need) * 100f
                }
            }
            ChallengeType.SS_THEN_HOLD_NORMAL -> {
                val ssNeed = (spec.firstSsSec ?: 0).toFloat()
                val nNeed = (spec.thenNormalSec ?: 0).toFloat()
                val all = (ssNeed + nNeed).coerceAtLeast(1f)
                val done = when (s.phase) {
                    0 -> 0f
                    1 -> (s.currentRegimeDurSec.toFloat() + if (s.currentRegime == Regime.SUPERSPEED) fracSec else 0f).coerceAtMost(ssNeed)
                    else -> ssNeed + (s.currentRegimeDurSec.toFloat() + if (s.currentRegime == Regime.NORMAL) fracSec else 0f).coerceAtMost(nNeed)
                }
                pct = (done / all) * 100f
            }
            ChallengeType.SUM_SS_TIME -> {
                val need = (spec.minSumSsSec ?: 0).toFloat()
                if (need > 0f && s.currentRegime == Regime.SUPERSPEED) {
                    val num = (s.ssTimeSec.toFloat() + fracSec).coerceAtMost(need)
                    pct = (num / need) * 100f
                }
            }
            ChallengeType.TOTAL_DISTANCE_TIME, ChallengeType.NEGATIVE_SPLIT_DISTANCE -> {
                val needM = when (type) {
                    ChallengeType.TOTAL_DISTANCE_TIME -> (spec.distanceMeters ?: 0).toFloat()
                    else -> (spec.negativeSplitDistanceM ?: 1000).toFloat()
                }
                if (needM > 0f) {
                    val mps = (currentSpeed * 1000f / 3600f)
                    val uiDist = (s.distanceM + (mps * fracSec)).toFloat()
                    pct = (uiDist / needM) * 100f
                }
            }
            else -> {
                // INTERVALS, BURSTS_SS, EXACT_SWITCHES, LADDER_SS — оставим базовый процент
            }
        }
        // Не допускаем уменьшения и выход за 100
        val ui = pct.toInt().coerceAtLeast(basePct).coerceAtMost(100)
        return ui
    }

    private fun pickRandomChallenge(diff: Difficulty): ChallengeSpec? {
        val base = CHALLENGES.filter { it.difficulty == diff && it.id !in completedThisSession }
        // Сначала фильтр по контексту
        val eligible = base.filter { isEligibleNow(it) }
        if (eligible.isEmpty()) return null
        val chosenFrom = eligible
        val weighted = mutableListOf<ChallengeSpec>()
        chosenFrom.forEach { spec ->
            val w = WEIGHTS[spec.id] ?: 1
            repeat(w.coerceAtLeast(1)) { weighted.add(spec) }
        }
        return if (weighted.isNotEmpty()) weighted.random() else chosenFrom.random()
    }

    // Проверка уместности запуска испытания в текущем контексте
    private fun isEligibleNow(spec: ChallengeSpec): Boolean {
        val threshold = thresholdProvider().toFloat()
        val inSS = currentSpeed >= threshold
        return when (spec.type) {
            ChallengeType.HOLD_SS -> inSS
            ChallengeType.SUM_SS_TIME -> inSS
            ChallengeType.BURSTS_SS -> inSS
            ChallengeType.SS_THEN_HOLD_NORMAL -> inSS
            ChallengeType.HOLD_NORMAL_NO_SS -> !inSS
            // Остальные — можно запускать в любом режиме
            else -> true
        }
    }

    private fun getChallengeTitle(spec: ChallengeSpec): String {
        val resId = TITLE_RES[spec.id]
        return if (resId != null) host.getString(resId) else spec.id
    }
    private fun getChallengeDesc(spec: ChallengeSpec): String {
        val resId = DESC_RES[spec.id]
        return if (resId != null) host.getString(resId) else ""
    }

    private val TITLE_RES = mapOf(
        // EASY
        "E1" to R.string.ta_E1_title,
        "E2" to R.string.ta_E2_title,
        "E3" to R.string.ta_E3_title,
        "E4" to R.string.ta_E4_title,
        "E5" to R.string.ta_E5_title,
        "E6" to R.string.ta_E6_title,
        "E7" to R.string.ta_E7_title,
        "E8" to R.string.ta_E8_title,
        "E9" to R.string.ta_E9_title,
        "E10" to R.string.ta_E10_title,
        // NORMAL
        "N1" to R.string.ta_N1_title,
        "N2" to R.string.ta_N2_title,
        "N3" to R.string.ta_N3_title,
        "N4" to R.string.ta_N4_title,
        "N5" to R.string.ta_N5_title,
        "N6" to R.string.ta_N6_title,
        "N7" to R.string.ta_N7_title,
        "N8" to R.string.ta_N8_title,
        "N9" to R.string.ta_N9_title,
        "N10" to R.string.ta_N10_title,
        // HARD
        "H1" to R.string.ta_H1_title,
        "H2" to R.string.ta_H2_title,
        "H3" to R.string.ta_H3_title,
        "H4" to R.string.ta_H4_title,
        "H5" to R.string.ta_H5_title,
        "H6" to R.string.ta_H6_title,
        "H7" to R.string.ta_H7_title,
        "H8" to R.string.ta_H8_title,
        "H9" to R.string.ta_H9_title,
        "H10" to R.string.ta_H10_title,
        // EXTREME
        "X1" to R.string.ta_X1_title,
        "X2" to R.string.ta_X2_title,
        "X3" to R.string.ta_X3_title,
        "X4" to R.string.ta_X4_title,
    )
    private val DESC_RES = mapOf(
        // EASY
        "E1" to R.string.ta_E1_desc,
        "E2" to R.string.ta_E2_desc,
        "E3" to R.string.ta_E3_desc,
        "E4" to R.string.ta_E4_desc,
        "E5" to R.string.ta_E5_desc,
        "E6" to R.string.ta_E6_desc,
        "E7" to R.string.ta_E7_desc,
        "E8" to R.string.ta_E8_desc,
        "E9" to R.string.ta_E9_desc,
        "E10" to R.string.ta_E10_desc,
        // NORMAL
        "N1" to R.string.ta_N1_desc,
        "N2" to R.string.ta_N2_desc,
        "N3" to R.string.ta_N3_desc,
        "N4" to R.string.ta_N4_desc,
        "N5" to R.string.ta_N5_desc,
        "N6" to R.string.ta_N6_desc,
        "N7" to R.string.ta_N7_desc,
        "N8" to R.string.ta_N8_desc,
        "N9" to R.string.ta_N9_desc,
        "N10" to R.string.ta_N10_desc,
        // HARD
        "H1" to R.string.ta_H1_desc,
        "H2" to R.string.ta_H2_desc,
        "H3" to R.string.ta_H3_desc,
        "H4" to R.string.ta_H4_desc,
        "H5" to R.string.ta_H5_desc,
        "H6" to R.string.ta_H6_desc,
        "H7" to R.string.ta_H7_desc,
        "H8" to R.string.ta_H8_desc,
        "H9" to R.string.ta_H9_desc,
        "H10" to R.string.ta_H10_desc,
        // EXTREME
        "X1" to R.string.ta_X1_desc,
        "X2" to R.string.ta_X2_desc,
        "X3" to R.string.ta_X3_desc,
        "X4" to R.string.ta_X4_desc,
    )

    // Веса выпадения челленджей внутри одной сложности
    private val WEIGHTS = mapOf(
        // EASY: базово чаще (3) для самых простых, реже (2) для остальных
        "E1" to 3, "E2" to 3, "E3" to 3,
        "E4" to 2, "E5" to 2, "E6" to 2, "E7" to 2, "E8" to 2, "E9" to 2, "E10" to 2,
        // NORMAL: умеренные чаще (2), длинные/сложные реже (1)
        "N1" to 2, "N2" to 2, "N3" to 1, "N4" to 1, "N5" to 1, "N6" to 2, "N7" to 1, "N8" to 2, "N9" to 1, "N10" to 1,
        // HARD: сложные чаще 1, более динамичные 2
        "H1" to 1, "H2" to 2, "H3" to 1, "H4" to 1, "H5" to 1, "H6" to 1, "H7" to 1, "H8" to 1, "H9" to 1, "H10" to 1,
        // EXTREME (реже всего)
        "X1" to 1,
        "X2" to 1,
        "X3" to 1,
        "X4" to 1,
    )

    // 30 испытаний (10 на сложность)
    private val CHALLENGES: List<ChallengeSpec> = listOf(
        // --- EASY (10) ---
        ChallengeSpec(
            id = "E1",
            difficulty = Difficulty.EASY,
            type = ChallengeType.HOLD_SS,
            minHoldSsSec = 10,
            timeLimitSec = 120,
            allowedDipSec = 2
        ),
        ChallengeSpec(
            id = "E2",
            difficulty = Difficulty.EASY,
            type = ChallengeType.HOLD_NORMAL_NO_SS,
            holdNormalSec = 60,
            timeLimitSec = 60
        ),
        ChallengeSpec(
            id = "E3",
            difficulty = Difficulty.EASY,
            type = ChallengeType.SUM_SS_TIME,
            minSumSsSec = 5,
            timeLimitSec = 60
        ),
        ChallengeSpec(
            id = "E4",
            difficulty = Difficulty.EASY,
            type = ChallengeType.INTERVALS,
            intervalsCycles = 2,
            intervalNormalSec = 10,
            intervalSsSec = 10,
            timeLimitSec = 60
        ),
        ChallengeSpec(
            id = "E5",
            difficulty = Difficulty.EASY,
            type = ChallengeType.TOTAL_DISTANCE_TIME,
            distanceMeters = 500,
            timeLimitSec = 480
        ),
        ChallengeSpec(
            id = "E6",
            difficulty = Difficulty.EASY,
            type = ChallengeType.LIMIT_SWITCHES,
            periodSec = 180,
            maxSwitches = 3
        ),
        ChallengeSpec(
            id = "E7",
            difficulty = Difficulty.EASY,
            type = ChallengeType.BURSTS_SS,
            burstsCount = 3,
            burstMinSsSec = 3,
            burstMaxGapSec = 45,
            timeLimitSec = 300
        ),
        ChallengeSpec(
            id = "E8",
            difficulty = Difficulty.EASY,
            type = ChallengeType.NO_STOPS,
            noStopsSec = 240,
            timeLimitSec = 240
        ),
        ChallengeSpec(
            id = "E9",
            difficulty = Difficulty.EASY,
            type = ChallengeType.SS_THEN_HOLD_NORMAL,
            firstSsSec = 5,
            thenNormalSec = 20,
            timeLimitSec = 240
        ),
        ChallengeSpec(
            id = "E10",
            difficulty = Difficulty.EASY,
            type = ChallengeType.BURSTS_SS,
            burstsCount = 4,
            burstMinSsSec = 2,
            burstMaxGapSec = 35,
            timeLimitSec = 120
        ),

        // --- NORMAL (10) ---
        ChallengeSpec(
            id = "N1",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.INTERVALS,
            intervalsCycles = 4,
            intervalNormalSec = 20,
            intervalSsSec = 20,
            timeLimitSec = 300
        ),
        ChallengeSpec(
            id = "N2",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.INTERVALS,
            intervalsCycles = 3, // упростим до 3 SS-этапов
            intervalNormalSec = 20, // нормальные отрезки ~20/25, оставим 20 для простоты
            intervalSsSec = 20,
            timeLimitSec = 360
        ),
        ChallengeSpec(
            id = "N3",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.HOLD_SS,
            minHoldSsSec = 30,
            timeLimitSec = 240
        ),
        ChallengeSpec(
            id = "N4",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.NEGATIVE_SPLIT_DISTANCE,
            negativeSplitDistanceM = 1000,
            negativeSplitDeltaPct = 10,
            timeLimitSec = 1800
        ),
        ChallengeSpec(
            id = "N5",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.SUM_SS_TIME,
            minSumSsSec = 90,
            timeLimitSec = 360
        ),
        ChallengeSpec(
            id = "N6",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.LIMIT_SWITCHES,
            periodSec = 120,
            maxSwitches = 12
        ),
        ChallengeSpec(
            id = "N7",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.HOLD_SS,
            minHoldSsSec = 60, // прокси: суммарно SS ≥60 с за 2 минуты
            timeLimitSec = 120,
            allowedDipSec = 2
        ),
        ChallengeSpec(
            id = "N8",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.BURSTS_SS,
            burstsCount = 5,
            burstMinSsSec = 6,
            burstMaxGapSec = 30,
            timeLimitSec = 420
        ),
        ChallengeSpec(
            id = "N9",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.TOTAL_DISTANCE_TIME,
            distanceMeters = 1000,
            timeLimitSec = 540
        ),
        ChallengeSpec(
            id = "N10",
            difficulty = Difficulty.NORMAL,
            type = ChallengeType.NO_STOPS,
            noStopsSec = 480,
            timeLimitSec = 480
        ),

        // --- HARD (10) ---
        ChallengeSpec(
            id = "H1",
            difficulty = Difficulty.HARD,
            type = ChallengeType.HOLD_SS,
            minHoldSsSec = 60,
            timeLimitSec = 600
        ),
        ChallengeSpec(
            id = "H2",
            difficulty = Difficulty.HARD,
            type = ChallengeType.INTERVALS,
            intervalsCycles = 8,
            intervalNormalSec = 15,
            intervalSsSec = 15,
            timeLimitSec = 600
        ),
        ChallengeSpec(
            id = "H3",
            difficulty = Difficulty.HARD,
            type = ChallengeType.SUM_SS_TIME,
            minSumSsSec = 180,
            timeLimitSec = 600
        ),
        ChallengeSpec(
            id = "H4",
            difficulty = Difficulty.HARD,
            type = ChallengeType.TOTAL_DISTANCE_TIME,
            distanceMeters = 1000,
            timeLimitSec = 360
        ),
        ChallengeSpec(
            id = "H5",
            difficulty = Difficulty.HARD,
            type = ChallengeType.TOTAL_DISTANCE_TIME,
            distanceMeters = 500,
            timeLimitSec = 150
        ),
        ChallengeSpec(
            id = "H6",
            difficulty = Difficulty.HARD,
            type = ChallengeType.EXACT_SWITCHES,
            exactSwitches = 10,
            timeLimitSec = 420
        ),
        ChallengeSpec(
            id = "H7",
            difficulty = Difficulty.HARD,
            type = ChallengeType.LADDER_SS,
            ladderTargets = intArrayOf(10, 15, 20, 25),
            ladderMaxRestSec = 3,
            timeLimitSec = 600
        ),
        ChallengeSpec(
            id = "H8",
            difficulty = Difficulty.HARD,
            type = ChallengeType.SS_THEN_HOLD_NORMAL,
            firstSsSec = 8,
            thenNormalSec = 30,
            timeLimitSec = 720
        ),
        ChallengeSpec(
            id = "H9",
            difficulty = Difficulty.HARD,
            type = ChallengeType.LIMIT_SWITCHES,
            periodSec = 300,
            maxSwitches = 5,
            timeLimitSec = 300
        ),
        ChallengeSpec(
            id = "H10",
            difficulty = Difficulty.HARD,
            type = ChallengeType.BURSTS_SS,
            burstsCount = 8,
            burstMinSsSec = 8,
            burstMaxGapSec = 60,
            timeLimitSec = 720
        ),
        // EXTREME
        ChallengeSpec(
            id = "X1",
            difficulty = Difficulty.EXTREME,
            type = ChallengeType.HOLD_SS,
            minHoldSsSec = 120,
            allowedDipSec = 1,
            timeLimitSec = 300
        ),
        ChallengeSpec(
            id = "X2",
            difficulty = Difficulty.EXTREME,
            type = ChallengeType.SUM_SS_TIME,
            minSumSsSec = 300,
            timeLimitSec = 600
        ),
        ChallengeSpec(
            id = "X3",
            difficulty = Difficulty.EXTREME,
            type = ChallengeType.LADDER_SS,
            ladderTargets = intArrayOf(20, 25, 30, 35),
            ladderMaxRestSec = 2,
            timeLimitSec = 900
        ),
        ChallengeSpec(
            id = "X4",
            difficulty = Difficulty.EXTREME,
            type = ChallengeType.EXACT_SWITCHES,
            exactSwitches = 20,
            timeLimitSec = 600
        )
    )
}
