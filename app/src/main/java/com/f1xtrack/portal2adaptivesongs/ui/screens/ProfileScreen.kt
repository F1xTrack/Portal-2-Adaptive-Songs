package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f1xtrack.portal2adaptivesongs.AchievementsActivity
import com.f1xtrack.portal2adaptivesongs.MainActivity
import com.f1xtrack.portal2adaptivesongs.OnboardingActivity
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.SettingsActivity
import com.f1xtrack.portal2adaptivesongs.TimeAttackSettingsActivity
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalThemeTokens

@Composable
fun ProfileScreenEntry(
    onAnimationIntensityChanged: (AnimationIntensity) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(context) { ProfileController(context.applicationContext) }
    val uiState by controller.uiState.collectAsState()

    ProfileScreen(
        uiState = uiState,
        onToggleAmoled = {
            controller.setAmoledEnabled(it)
            context.findActivity()?.recreate()
        },
        onToggleKeepScreenOn = {
            controller.setKeepScreenOnEnabled(it)
            controller.refresh()
            context.findActivity()?.window?.let { window ->
                if (it) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        },
        onAnimationIntensitySelected = {
            controller.setAnimationIntensity(it)
            onAnimationIntensityChanged(it)
        },
        onOpenAdvancedSettings = {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        },
        onOpenOnboarding = {
            context.startActivity(Intent(context, OnboardingActivity::class.java))
        },
        onOpenTimeAttackSettings = {
            context.startActivity(Intent(context, TimeAttackSettingsActivity::class.java))
        },
        onOpenAchievements = {
            context.startActivity(Intent(context, AchievementsActivity::class.java))
        },
        onTimeAttackDifficultySelected = controller::setTimeAttackDifficulty,
        onStartTimeAttackNow = {
            controller.queueTimeAttackStart()
            context.startActivity(Intent(context, MainActivity::class.java))
        },
    )
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onToggleAmoled: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onAnimationIntensitySelected: (AnimationIntensity) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenTimeAttackSettings: () -> Unit,
    onOpenAchievements: () -> Unit,
    onTimeAttackDifficultySelected: (String) -> Unit,
    onStartTimeAttackNow: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            ShellHeader(
                title = stringResource(R.string.profile_screen_title),
                subtitle = stringResource(R.string.profile_screen_subtitle),
                badgeText = stringResource(R.string.profile_screen_badge),
            )
        }
        item {
            ProfileHighlightsCard(uiState = uiState)
        }
        item {
            ProfilePreferencesCard(
                uiState = uiState,
                onToggleAmoled = onToggleAmoled,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                onAnimationIntensitySelected = onAnimationIntensitySelected,
                onOpenAdvancedSettings = onOpenAdvancedSettings,
            )
        }
        item {
            TimeAttackCard(
                difficultyLabel = uiState.timeAttackDifficultyLabel,
                onDifficultySelected = onTimeAttackDifficultySelected,
                onOpenTimeAttackSettings = onOpenTimeAttackSettings,
                onStartTimeAttackNow = onStartTimeAttackNow,
            )
        }
        item {
            OnboardingCard(
                onboardingCompleted = uiState.onboardingCompleted,
                onOpenOnboarding = onOpenOnboarding,
            )
        }
        item {
            AchievementsCard(
                uiState = uiState,
                onOpenAchievements = onOpenAchievements,
            )
        }
        item {
            QuickActionsCard(uiState.quickActions)
        }
        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProfileHighlightsCard(uiState: ProfileUiState) {
    SurfaceCard {
        Text(
            text = stringResource(R.string.profile_settings_hub_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                R.string.profile_settings_hub_body,
                uiState.themeLabel,
                uiState.languageLabel,
                uiState.timeAttackDifficultyLabel,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = PortalThemeTokens.colors.contentSecondary,
        )
    }
}

@Composable
private fun ProfilePreferencesCard(
    uiState: ProfileUiState,
    onToggleAmoled: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onAnimationIntensitySelected: (AnimationIntensity) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
) {
    SurfaceCard {
        SectionTitle(
            title = stringResource(R.string.profile_preferences_title),
            body = stringResource(R.string.profile_preferences_body),
        )
        Spacer(modifier = Modifier.height(16.dp))
        PreferenceToggleRow(
            title = stringResource(R.string.settings_amoled_theme),
            subtitle = stringResource(R.string.settings_amoled_theme_hint),
            checked = uiState.isAmoledEnabled,
            onCheckedChange = onToggleAmoled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggleRow(
            title = stringResource(R.string.settings_keep_screen_on),
            subtitle = stringResource(R.string.settings_keep_screen_on_hint),
            checked = uiState.isKeepScreenOnEnabled,
            onCheckedChange = onToggleKeepScreenOn,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_animation_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AnimationIntensity.entries.forEach { intensity ->
            MotionOptionRow(
                intensity = intensity,
                selected = uiState.animationIntensity == intensity,
                onSelect = { onAnimationIntensitySelected(intensity) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinkRow(
            title = stringResource(R.string.profile_open_advanced_settings),
            body = stringResource(R.string.profile_open_advanced_settings_body),
            onClick = onOpenAdvancedSettings,
        )
    }
}

@Composable
private fun TimeAttackCard(
    difficultyLabel: String,
    onDifficultySelected: (String) -> Unit,
    onOpenTimeAttackSettings: () -> Unit,
    onStartTimeAttackNow: () -> Unit,
) {
    SurfaceCard {
        SectionTitle(
            title = stringResource(R.string.profile_time_attack_title),
            body = stringResource(R.string.profile_time_attack_body, difficultyLabel),
        )
        Spacer(modifier = Modifier.height(14.dp))
        DifficultyChipRow(
            selectedDifficulty = difficultyLabel,
            onDifficultySelected = onDifficultySelected,
        )
        Spacer(modifier = Modifier.height(14.dp))
        LinkRow(
            title = stringResource(R.string.profile_time_attack_open_settings),
            body = stringResource(R.string.profile_time_attack_open_settings_body),
            onClick = onOpenTimeAttackSettings,
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinkRow(
            title = stringResource(R.string.profile_time_attack_start_now),
            body = stringResource(R.string.profile_time_attack_start_now_body),
            onClick = onStartTimeAttackNow,
        )
    }
}

@Composable
private fun OnboardingCard(
    onboardingCompleted: Boolean,
    onOpenOnboarding: () -> Unit,
) {
    SurfaceCard {
        val status = if (onboardingCompleted) {
            stringResource(R.string.profile_onboarding_completed)
        } else {
            stringResource(R.string.profile_onboarding_pending)
        }
        SectionTitle(
            title = stringResource(R.string.profile_onboarding_title),
            body = stringResource(R.string.profile_onboarding_body, status),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinkRow(
            title = stringResource(R.string.profile_onboarding_open),
            body = stringResource(R.string.profile_onboarding_open_body),
            onClick = onOpenOnboarding,
        )
    }
}

@Composable
private fun AchievementsCard(
    uiState: ProfileUiState,
    onOpenAchievements: () -> Unit,
) {
    SurfaceCard {
        SectionTitle(
            title = stringResource(R.string.profile_achievements_title),
            body = stringResource(
                R.string.profile_achievements_body,
                uiState.achievementsUnlocked,
                uiState.achievementsTotal,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { uiState.achievementsProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                title = stringResource(R.string.profile_metric_distance),
                value = stringResource(R.string.profile_metric_distance_value, uiState.totalDistanceKm),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = stringResource(R.string.profile_metric_superspeed),
                value = stringResource(R.string.profile_metric_superspeed_value, uiState.superSpeedMinutes),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        MetricTile(
            title = stringResource(R.string.profile_metric_library),
            value = stringResource(R.string.profile_metric_library_value, uiState.importedTracks),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        LinkRow(
            title = stringResource(R.string.profile_achievements_open),
            body = stringResource(R.string.profile_achievements_open_body),
            onClick = onOpenAchievements,
        )
    }
}

@Composable
private fun QuickActionsCard(actions: List<ProfileQuickAction>) {
    SurfaceCard {
        SectionTitle(
            title = stringResource(R.string.profile_quick_actions_title),
            body = stringResource(R.string.profile_quick_actions_body),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { action ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PortalThemeTokens.colors.contentSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyChipRow(
    selectedDifficulty: String,
    onDifficultySelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DifficultyOptionRow(
            title = stringResource(R.string.difficulty_easy),
            body = stringResource(R.string.profile_time_attack_easy_body),
            selected = selectedDifficulty == stringResource(R.string.difficulty_easy),
            onClick = { onDifficultySelected("easy") },
        )
        DifficultyOptionRow(
            title = stringResource(R.string.difficulty_normal),
            body = stringResource(R.string.profile_time_attack_normal_body),
            selected = selectedDifficulty == stringResource(R.string.difficulty_normal),
            onClick = { onDifficultySelected("normal") },
        )
        DifficultyOptionRow(
            title = stringResource(R.string.difficulty_hard),
            body = stringResource(R.string.profile_time_attack_hard_body),
            selected = selectedDifficulty == stringResource(R.string.difficulty_hard),
            onClick = { onDifficultySelected("hard") },
        )
        DifficultyOptionRow(
            title = stringResource(R.string.profile_time_attack_difficulty_extreme),
            body = stringResource(R.string.profile_time_attack_extreme_body),
            selected = selectedDifficulty == stringResource(R.string.profile_time_attack_difficulty_extreme),
            onClick = { onDifficultySelected("extreme") },
        )
    }
}

@Composable
private fun DifficultyOptionRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) PortalThemeTokens.colors.accentBlue else PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(20.dp),
            )
            .background(
                if (selected) {
                    PortalThemeTokens.colors.surfaceHighlight.copy(alpha = 0.55f)
                } else {
                    PortalThemeTokens.colors.surfaceElevated.copy(alpha = 0.35f)
                },
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PortalThemeTokens.colors.contentSecondary)
        }
    }
}

@Composable
private fun MotionOptionRow(
    intensity: AnimationIntensity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val titleRes = when (intensity) {
        AnimationIntensity.Relaxed -> R.string.profile_animation_relaxed_title
        AnimationIntensity.Moderate -> R.string.profile_animation_moderate_title
        AnimationIntensity.Expressive -> R.string.profile_animation_expressive_title
    }
    val bodyRes = when (intensity) {
        AnimationIntensity.Relaxed -> R.string.profile_animation_relaxed_body
        AnimationIntensity.Moderate -> R.string.profile_animation_moderate_body
        AnimationIntensity.Expressive -> R.string.profile_animation_expressive_body
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) PortalThemeTokens.colors.accentOrange else PortalThemeTokens.colors.outline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium, color = PortalThemeTokens.colors.contentSecondary)
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = PortalThemeTokens.colors.contentSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LinkRow(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PortalThemeTokens.colors.contentSecondary)
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = PortalThemeTokens.colors.contentSecondary)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = PortalThemeTokens.colors.contentSecondary)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PortalThemeTokens.colors.surface, RoundedCornerShape(28.dp))
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        content = content,
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
