package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.BrainStatusPill
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.StatTile
import com.example.viewmodel.StudioUiState

@Composable
fun HomeScreen(
    uiState: StudioUiState,
    onCreateProject: (name: String, pkg: String, desc: String) -> Unit,
    onSelectProject: (String) -> Unit,
    onImportPdfUri: (Uri) -> Unit,
    onRunPipeline: () -> Unit,
    onAskAi: (prompt: String, addTasks: Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToProject: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf("") }
    var alsoQueueTasks by remember { mutableStateOf(true) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPdfUri(uri)
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, pkg, desc ->
                onCreateProject(name, pkg, desc)
                showCreateDialog = false
            }
        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.studio_hero_banner_1795398284750),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI DEV STUDIO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                BrainStatusPill(
                                    brainState = uiState.brainState,
                                    onClick = onNavigateToSettings
                                )
                            }
                            Column {
                                Text(
                                    text = uiState.activeProject?.name ?: "Offline Android AI Builder",
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = uiState.activeProject?.packageName
                                        ?: "Create a workspace, import a PDF spec, and run the 15-task local pipeline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Workspace Switcher & Create Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Project Workspaces",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("new_workspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_create_project),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("New Project")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.projects.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "Empty workspaces",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No Android workspace yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Initialize a local Android Gradle + Compose workspace to import PDFs, plan tasks, generate English Kotlin code, and verify builds.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.testTag("empty_create_workspace_button")
                            ) {
                                Text("Create First Workspace")
                            }
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.projects, key = { it.id }) { proj ->
                            val selected = proj.id == uiState.activeProject?.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectProject(proj.id) },
                                label = { Text(proj.name) },
                                modifier = Modifier.testTag("workspace_chip_${proj.id}")
                            )
                        }
                    }
                }
            }

            // Metrics & Quick Actions when a project is active
            if (uiState.activeProject != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = "Files",
                            value = uiState.projectFiles.count { !it.isDirectory }.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "PDFs",
                            value = uiState.pdfs.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Tasks",
                            value = "${uiState.tasks.count { it.status == "COMPLETED" }}/${uiState.tasks.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Build",
                            value = uiState.activeProject.lastBuildStatus.take(7),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onRunPipeline()
                                onNavigateToProgress()
                            },
                            enabled = !uiState.isPipelineRunning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_pipeline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_run_pipeline),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (uiState.isPipelineRunning) "Running..." else "Run 15-Step Build")
                        }

                        OutlinedButton(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_import_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.UploadFile,
                                contentDescription = stringResource(R.string.cd_import_pdf),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Import PDF")
                        }
                    }
                }
            }

            // Bilingual Local AI Assistant Card (BN -> BN, EN -> EN)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Local AI Assistant",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On-Device AI Assistant (EN / বাংলা)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Replies in Bengali for বাংলা prompts and English for English prompts. Code stays English.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.activeProject != null) {
                                OutlinedButton(
                                    onClick = onNavigateToProject,
                                    modifier = Modifier.testTag("home_open_files_button")
                                ) {
                                    Text("Files")
                                }
                            }
                        }

                        if (uiState.chatMessages.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.chatMessages.takeLast(4).forEach { msg ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (msg.isUser) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (msg.isUser) "You" else msg.engineTag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.activeProject != null) {
                            FilterChip(
                                selected = alsoQueueTasks,
                                onClick = { alsoQueueTasks = !alsoQueueTasks },
                                label = { Text("Also add instruction to Task Planner") },
                                modifier = Modifier.testTag("queue_tasks_toggle_chip")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                placeholder = {
                                    Text("Ask or specify a feature (English or বাংলা)...")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_prompt_input"),
                                maxLines = 3
                            )
                            IconButton(
                                onClick = {
                                    val text = promptInput.trim()
                                    if (text.isNotEmpty()) {
                                        onAskAi(text, alsoQueueTasks)
                                        promptInput = ""
                                    }
                                },
                                enabled = !uiState.isAiReplying,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("send_ai_prompt_button")
                            ) {
                                if (uiState.isAiReplying) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send prompt to Local AI",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
