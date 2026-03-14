package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.content.Context
import androidx.compose.runtime.Immutable
import com.f1xtrack.portal2adaptivesongs.AchievementRepository
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.readAnimationIntensityPreference
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val UI_PREFS = "ui_prefs"
private const val ONBOARDING_PREFS = "onboarding_prefs"
private const val TIME_ATTACK_PREFS = "time_attack_prefs"

@Immutable
data class ProfileUiState(
    val themeLabel: String = "",
    val languageLabel: String = "",
    val isAmoledEnabled: Boolean = false,
    val isKeepScreenOnEnabled: Boolean = false,
    val animationIntensity: AnimationIntensity = AnimationIntensity.Moderate,
    val onboardingCompleted: Boolean = false,
    val timeAttackDifficultyLabel: String = "",
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = 0,
    val totalDistanceKm: Int = 0,
    val superSpeedMinutes: Int = 0,
    val importedTracks: Int = 0,
    val quickActions: ImmutableList<ProfileQuickAction> = persistentListOf(),
) {
    val achievementsProgress: Float
        get() = if (achievementsTotal == 0) 0f else achievementsUnlocked.toFloat() / achievementsTotal.toFloat()
}

@Immutable
data class ProfileQuickAction(
    val title: String,
    val description: String,
)

@Immutable
data class ProfileSnapshot(
    val themeKey: String,
    val languageCode: String,
    val isAmoledEnabled: Boolean,
    val isKeepScreenOnEnabled: Boolean,
    val animationIntensity: AnimationIntensity,
    val onboardingCompleted: Boolean,
    val timeAttackDifficulty: String,
    val achievementsUnlocked: Int,
    val achievementsTotal: Int,
    val totalDistanceKm: Int,
    val superSpeedMinutes: Int,
    val importedTracks: Int,
)

class ProfileController(
    private val appContext: Context,
) {
    private val _uiState = MutableStateFlow(loadProfileUiState(appContext))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = loadProfileUiState(appContext)
    }

    fun setAmoledEnabled(enabled: Boolean) {
        prefs(UI_PREFS).edit().putBoolean("amoled_mode", enabled).apply()
        refresh()
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        prefs(UI_PREFS).edit().putBoolean("keep_screen_on", enabled).apply()
        refresh()
    }

    fun setAnimationIntensity(intensity: AnimationIntensity) {
        prefs(UI_PREFS).edit().putString("animation_intensity", intensity.prefValue).apply()
        _uiState.update { it.copy(animationIntensity = intensity) }
    }

    fun setTimeAttackDifficulty(difficulty: String) {
        prefs(TIME_ATTACK_PREFS).edit().putString("difficulty", difficulty).apply()
        refresh()
    }

    fun queueTimeAttackStart() {
        prefs(TIME_ATTACK_PREFS).edit().putBoolean("start_now", true).apply()
    }

    private fun prefs(name: String) = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}

internal fun loadProfileUiState(context: Context): ProfileUiState {
    val achievements = AchievementRepository(context).getAll()
    val snapshot = ProfileSnapshot(
        themeKey = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getString("app_theme", "portal2")
            .orEmpty(),
        languageCode = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getString("app_lang", "system")
            .orEmpty(),
        isAmoledEnabled = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getBoolean("amoled_mode", false),
        isKeepScreenOnEnabled = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getBoolean("keep_screen_on", false),
        animationIntensity = readAnimationIntensityPreference(context),
        onboardingCompleted = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
            .getBoolean("onboarding_completed", false),
        timeAttackDifficulty = context.getSharedPreferences(TIME_ATTACK_PREFS, Context.MODE_PRIVATE)
            .getString("difficulty", "normal")
            .orEmpty(),
        achievementsUnlocked = achievements.count { it.current >= it.target },
        achievementsTotal = achievements.size,
        totalDistanceKm = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
            .getInt("distance_km", 0),
        superSpeedMinutes = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
            .getInt("superspeed_minutes", 0),
        importedTracks = achievementsImportCount(context),
    )
    return buildProfileUiState(context, snapshot)
}

internal fun buildProfileUiState(
    context: Context,
    snapshot: ProfileSnapshot,
): ProfileUiState {
    return ProfileUiState(
        themeLabel = resolveThemeLabel(context, snapshot.themeKey, snapshot.isAmoledEnabled),
        languageLabel = resolveLanguageLabel(context, snapshot.languageCode),
        isAmoledEnabled = snapshot.isAmoledEnabled,
        isKeepScreenOnEnabled = snapshot.isKeepScreenOnEnabled,
        animationIntensity = snapshot.animationIntensity,
        onboardingCompleted = snapshot.onboardingCompleted,
        timeAttackDifficultyLabel = resolveTimeAttackDifficultyLabel(context, snapshot.timeAttackDifficulty),
        achievementsUnlocked = snapshot.achievementsUnlocked,
        achievementsTotal = snapshot.achievementsTotal,
        totalDistanceKm = snapshot.totalDistanceKm,
        superSpeedMinutes = snapshot.superSpeedMinutes,
        importedTracks = snapshot.importedTracks,
        quickActions = listOf(
            ProfileQuickAction(
                title = context.getString(R.string.profile_quick_time_attack_title),
                description = context.getString(R.string.profile_quick_time_attack_body),
            ),
            ProfileQuickAction(
                title = context.getString(R.string.profile_quick_onboarding_title),
                description = context.getString(R.string.profile_quick_onboarding_body),
            ),
            ProfileQuickAction(
                title = context.getString(R.string.profile_quick_achievements_title),
                description = context.getString(R.string.profile_quick_achievements_body),
            ),
        ).toImmutableList(),
    )
}

internal fun resolveThemeLabel(
    context: Context,
    themeKey: String,
    isAmoledEnabled: Boolean,
): String {
    if (isAmoledEnabled) return context.getString(R.string.settings_amoled_theme)
    return when (themeKey) {
        "asi" -> context.getString(R.string.theme_asi)
        "portal2_overgrowth" -> context.getString(R.string.theme_portal2_overgrowth)
        "portal1" -> context.getString(R.string.theme_portal1)
        else -> context.getString(R.string.theme_portal2_default)
    }
}

internal fun resolveLanguageLabel(
    context: Context,
    languageCode: String,
): String {
    return when (languageCode) {
        "ar" -> context.getString(R.string.language_arabic_native)
        "de" -> context.getString(R.string.language_german_native)
        "en" -> context.getString(R.string.language_english)
        "es" -> context.getString(R.string.language_spanish_native)
        "hi" -> context.getString(R.string.language_hindi_native)
        "ja" -> context.getString(R.string.language_japanese_native)
        "kk" -> context.getString(R.string.language_kazakh)
        "ko" -> context.getString(R.string.language_korean_native)
        "pl" -> context.getString(R.string.language_polish_native)
        "pt" -> context.getString(R.string.language_portuguese_native)
        "ru" -> context.getString(R.string.language_russian)
        "tr" -> context.getString(R.string.language_turkish_native)
        "zh" -> context.getString(R.string.language_chinese_native)
        else -> context.getString(R.string.language_system)
    }
}

internal fun resolveTimeAttackDifficultyLabel(
    context: Context,
    difficulty: String,
): String {
    return when (difficulty) {
        "easy" -> context.getString(R.string.difficulty_easy)
        "hard" -> context.getString(R.string.difficulty_hard)
        "extreme" -> context.getString(R.string.profile_time_attack_difficulty_extreme)
        else -> context.getString(R.string.difficulty_normal)
    }
}

internal val AnimationIntensity.prefValue: String
    get() = when (this) {
        AnimationIntensity.Relaxed -> "relaxed"
        AnimationIntensity.Moderate -> "moderate"
        AnimationIntensity.Expressive -> "expressive"
    }

private fun achievementsImportCount(context: Context): Int {
    return AchievementRepository(context).getAll()
        .firstOrNull { it.id == "lib_100" }
        ?.current
        ?: 0
}
