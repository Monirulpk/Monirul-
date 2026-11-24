package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.PipelineStepStatus
import com.example.viewmodel.StudioUiState

@Composable
fun ProgressScreen(
    uiState: StudioUiState,
    onRunFullPipeline: () -> Unit,
    onRunBuildAndFix: () -> Unit,
    onAddInstructionTasks: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearTasks: () -> Unit,
    onCreateShareBundleIntent: () -> Intent?,
    onCreateShareReportIntent: () -> Intent?
) {
    val context = LocalContext.current
    val project = uiState.activeProject
    var customInstruction by remember { mutableStateOf("") }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select or create a workspace on Home to plan tasks and run the 15-step pipeline.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "15-Step AI Pipeline & Delivery",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRunFullPipeline,
                            enabled = !uiState.isPipelineRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pipeline_run_all_button")
                        ) {
                            if (uiState.isPipelineRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_run_pipeline),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.isPipelineRunning) "Executing..." else "Run All 15 Steps")
                        }
                        OutlinedButton(
                            onClick = onRunBuildAndFix,
                            enabled = !uiState.isPipelineRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pipeline_build_fix_button")
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = "Build and auto-fix", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Build & Fix")
                        }
                    }
                }
            }

            // Delivery Package Card (shown prominently when generated)
            uiState.deliveryResult?.let { delivery ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified delivery bundle",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Verified Project Bundle & Report Ready",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "${delivery.bundleFile.name} • ${delivery.entryCount} files • ${delivery.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "SHA-256: ${delivery.sha256.take(24)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Export the verified Android Gradle + Compose ZIP to build/sign the final .apk with `gradle assembleDebug` or Android Studio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        onCreateShareBundleIntent()?.let { intent ->
                                            context.startActivity(Intent.createChooser(intent, "Share Project Bundle"))
                                        }
                                    },
                                    modifier = Modifier.testTag("share_bundle_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = stringResource(R.string.cd_share_bundle),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share Project ZIP")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onCreateShareReportIntent()?.let { intent ->
                                            context.startActivity(Intent.createChooser(intent, "Share Completion Report"))
                                        }
                                    },
                                    modifier = Modifier.testTag("share_report_button")
                                ) {
                                    Text("Share Report")
                                }
                            }
                        }
                    }
                }
            }

            // Task Planner Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Task Planner (${uiState.tasks.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (uiState.tasks.isNotEmpty()) {
                                TextButton(onClick = onClearTasks) {
                                    Text("Clear")
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customInstruction,
                                onValueChange = { customInstruction = it },
                                placeholder = { Text("Add feature task (EN or বাংলা)...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("task_planner_input")
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (customInstruction.isNotBlank()) {
                                        onAddInstructionTasks(customInstruction)
                                        customInstruction = ""
                                    }
                                },
                                modifier = Modifier.testTag("add_task_button")
                            ) {
                                Icon(Icons.Filled.AddTask, contentDescription = "Add task", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            items(uiState.tasks, key = { it.id }) { task ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${task.stepOrder}. ${task.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${task.category} • ${task.status} • ${task.targetFile.substringAfterLast('/')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            task.duplicateWarning?.let { warn ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = warn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteTask(task.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                        }
                    }
                }
            }

            // 15 Core Steps Progress Tracker
            item {
                Text(
                    text = "15 Core Tasks Status",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(uiState.pipelineSteps, key = { it.number }) { step ->
                val (icon, tint) = when (step.status) {
                    PipelineStepStatus.PASSED, PipelineStepStatus.FIXED ->
                        Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                    PipelineStepStatus.FAILED ->
                        Icons.Filled.Error to MaterialTheme.colorScheme.error
                    PipelineStepStatus.RUNNING ->
                        Icons.Filled.PlayArrow to MaterialTheme.colorScheme.secondary
                    PipelineStepStatus.IDLE ->
                        Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = step.status.name, tint = tint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${step.number}. ${step.title}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (step.detail.isNotBlank()) {
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = step.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint
                        )
                    }
                }
            }

            // Automated Test Suite Results
            uiState.testReport?.let { tests ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Automatic Testing System (${tests.passedCount}/${tests.totalCount})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            tests.cases.forEach { tc ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (tc.passed) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                        contentDescription = tc.name,
                                        tint = if (tc.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(tc.name, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            tc.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Console Logs
            if (uiState.consoleLogs.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Build & Error Fixer Console",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            uiState.consoleLogs.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
