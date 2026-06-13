package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.TrackZipManifestSupport
import com.f1xtrack.portal2adaptivesongs.ui.theme.PortalThemeTokens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LibraryScreenEntry() {
    val appContext = LocalContext.current.applicationContext
    val controller = remember(appContext) {
        LibraryController(appContext)
    }
    LibraryScreen(controller = controller)
}

@Composable
fun LibraryScreen(
    controller: LibraryController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by controller.uiState.collectAsStateWithLifecycle()

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val displayName = queryDisplayName(context, uri)
            coroutineScope.launch {
                controller.prepareZipImport(uri, displayName)
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                controller.importZipPack(uri)
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var importName by rememberSaveable(uiState.pendingImport?.sourceLabel) {
        mutableStateOf(uiState.pendingImport?.suggestedName.orEmpty())
    }

    LaunchedEffect(uiState.pendingImport?.sourceLabel) {
        importName = uiState.pendingImport?.suggestedName.orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                ShellHeader(
                    title = stringResource(R.string.library_screen_title),
                    subtitle = stringResource(R.string.library_screen_subtitle),
                    badgeText = stringResource(R.string.library_screen_badge),
                )
            }
            item {
                LibrarySummaryCard(uiState = uiState)
            }
            item {
                LibraryImportCard(
                    onImportZip = { zipLauncher.launch("application/zip") },
                    onImportPack = { folderLauncher.launch(null) },
                )
            }
            uiState.importFeedback?.let { feedback ->
                item {
                    ImportFeedbackCard(
                        feedback = feedback,
                        onDismiss = controller::dismissFeedback,
                    )
                }
            }
            if (uiState.problemPackages.isNotEmpty()) {
                item {
                    ProblemPackagesCard(problemPackages = uiState.problemPackages)
                }
            }
            item {
                LibraryMaintenanceCard(
                    hiddenBuiltInCount = uiState.hiddenBuiltInCount,
                    onUnhideAll = controller::unhideAllBuiltInTracks,
                    onClearStats = controller::clearTrackStats,
                )
            }
            item {
                SectionTitle(title = stringResource(R.string.library_section_builtin))
            }
            if (uiState.builtInTracks.isEmpty()) {
                item {
                    ShellInfoCard(
                        title = stringResource(R.string.library_empty_builtin_title),
                        body = stringResource(R.string.library_empty_builtin_body),
                    )
                }
            } else {
                items(uiState.builtInTracks, key = { item -> "builtin-${item.name}" }) { item ->
                    LibraryTrackRow(
                        item = item,
                        actionLabel = stringResource(R.string.storage_hide),
                        onAction = { controller.toggleBuiltInVisibility(item.name) },
                    )
                }
            }
            item {
                SectionTitle(title = stringResource(R.string.library_section_imported))
            }
            if (uiState.importedTracks.isEmpty()) {
                item {
                    ShellInfoCard(
                        title = stringResource(R.string.library_empty_imported_title),
                        body = stringResource(R.string.library_empty_imported_body),
                    )
                }
            } else {
                items(uiState.importedTracks, key = { item -> "user-${item.name}" }) { item ->
                    LibraryTrackRow(
                        item = item,
                        actionLabel = stringResource(R.string.storage_delete),
                        onAction = { controller.deleteImportedTrack(item.name) },
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (uiState.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PortalThemeTokens.colors.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    uiState.pendingImport?.let { pendingImport ->
        ImportPreviewDialog(
            pendingImport = pendingImport,
            importName = importName,
            onImportNameChange = { importName = it },
            onDismiss = controller::dismissPendingImport,
            onConfirm = {
                coroutineScope.launch {
                    controller.importPendingZip(importName)
                }
            },
        )
    }
}

@Composable
private fun LibrarySummaryCard(
    uiState: LibraryUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.library_summary_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.library_summary_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                title = stringResource(R.string.library_metric_total),
                value = uiState.totalVisibleTracks.toString(),
            )
            MiniMetric(
                title = stringResource(R.string.library_metric_imported),
                value = uiState.importedVisibleCount.toString(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                title = stringResource(R.string.library_metric_hidden),
                value = uiState.hiddenBuiltInCount.toString(),
            )
            MiniMetric(
                title = stringResource(R.string.library_metric_problematic),
                value = uiState.problemPackageCount.toString(),
            )
        }
    }
}

@Composable
private fun LibraryImportCard(
    onImportZip: () -> Unit,
    onImportPack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.library_import_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.library_import_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        OutlinedButton(onClick = onImportZip, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.library_import_zip_entry))
        }
        OutlinedButton(onClick = onImportPack, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.library_import_pack_entry))
        }
        Text(
            text = stringResource(R.string.library_import_manifest_hint),
            style = MaterialTheme.typography.bodySmall,
            color = PortalThemeTokens.colors.contentSecondary,
        )
    }
}

@Composable
private fun ImportFeedbackCard(
    feedback: LibraryImportFeedback,
    onDismiss: () -> Unit,
) {
    val accentColor = when (feedback.severity) {
        LibraryFeedbackSeverity.Info -> PortalThemeTokens.colors.statusInfo
        LibraryFeedbackSeverity.Success -> PortalThemeTokens.colors.statusSuccess
        LibraryFeedbackSeverity.Warning -> PortalThemeTokens.colors.statusWarning
        LibraryFeedbackSeverity.Error -> PortalThemeTokens.colors.accentOrange
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, accentColor, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = feedback.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
        )
        Text(
            text = feedback.body,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(text = stringResource(android.R.string.ok))
        }
    }
}

@Composable
private fun ProblemPackagesCard(
    problemPackages: ImmutableList<LibraryProblemPackage>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.statusWarning, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.library_problem_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.library_problem_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        problemPackages.forEach { problem ->
            val reasonRes = when (problem.problemKind) {
                LibraryProblemKind.MissingNormal -> R.string.library_problem_missing_normal
                LibraryProblemKind.MissingSuperSpeed -> R.string.library_problem_missing_superspeed
                LibraryProblemKind.MissingBoth -> R.string.library_problem_missing_both
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = problem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(reasonRes, problem.fileCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = PortalThemeTokens.colors.contentSecondary,
                )
            }
        }
    }
}

@Composable
private fun LibraryMaintenanceCard(
    hiddenBuiltInCount: Int,
    onUnhideAll: () -> Unit,
    onClearStats: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.library_maintenance_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.library_maintenance_body, hiddenBuiltInCount),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        OutlinedButton(onClick = onUnhideAll, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.storage_unhide_all))
        }
        OutlinedButton(onClick = onClearStats, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.storage_clear_stats))
        }
    }
}

@Composable
private fun LibraryTrackRow(
    item: LibraryTrackItem,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PortalThemeTokens.colors.surface)
            .border(1.dp, PortalThemeTokens.colors.outline, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniMetric(
                title = stringResource(R.string.library_track_duration),
                value = formatDuration(item.durationMs),
            )
            MiniMetric(
                title = stringResource(R.string.library_track_plays),
                value = pluralStringResource(R.plurals.plays_count, item.plays, item.plays),
            )
        }
        Text(
            text = stringResource(
                if (item.isUserTrack) {
                    R.string.library_track_source_imported
                } else {
                    R.string.library_track_source_builtin
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PortalThemeTokens.colors.surfaceHighlight),
        ) {
            Text(text = actionLabel)
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    pendingImport: PendingLibraryImport,
    importName: String,
    onImportNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = pendingImport.preview
    val manifestText = when (preview.manifestSupport) {
        TrackZipManifestSupport.NotPresent -> stringResource(R.string.library_manifest_missing_status)
        TrackZipManifestSupport.PresentButIgnored -> stringResource(R.string.library_manifest_present_status)
    }
    val requirementsText = if (preview.isImportable) {
        stringResource(R.string.library_import_requirements_ok)
    } else {
        stringResource(R.string.library_import_requirements_fail)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = preview.isImportable,
            ) {
                Text(text = stringResource(R.string.library_import_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.library_import_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.library_import_dialog_source, pendingImport.sourceLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = PortalThemeTokens.colors.contentSecondary,
                )
                OutlinedTextField(
                    value = importName,
                    onValueChange = onImportNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.library_import_name_label)) },
                    singleLine = true,
                )
                Text(text = manifestText, style = MaterialTheme.typography.bodyMedium)
                Text(text = requirementsText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(
                        R.string.library_import_dialog_files,
                        preview.importedAudioCandidates,
                        preview.ignoredEntries,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PortalThemeTokens.colors.contentSecondary,
                )
            }
        },
    )
}

@Composable
private fun MiniMetric(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PortalThemeTokens.colors.surfaceHighlight)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = PortalThemeTokens.colors.contentSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else {
                null
            }
        }
        ?: uri.lastPathSegment
        ?: context.getString(R.string.library_import_unknown_source)
}
