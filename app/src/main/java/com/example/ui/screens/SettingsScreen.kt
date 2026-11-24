package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AiBackendPreference
import com.example.data.local.LanguagePolicy
import com.example.domain.CatalogModelSpec
import com.example.domain.LocalAiBrain
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.StudioUiState
import java.io.File

@Composable
fun SettingsScreen(
    uiState: StudioUiState,
    onSetBackend: (AiBackendPreference) -> Unit,
    onSetLanguagePolicy: (LanguagePolicy) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onDownloadCatalogModel: (CatalogModelSpec) -> Unit,
    onCancelDownload: () -> Unit,
    onImportLocalModel: (Uri) -> Unit,
    onLoadModel: (String) -> Unit,
    onUnloadModel: () -> Unit,
    onDeleteModel: (File) -> Unit,
    onDeleteActiveProject: (String) -> Unit
) {
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalModel(uri)
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
            item {
                Text(
                    text = "Settings & Local AI Brain",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            // Local AI Brain Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                Icons.Filled.Memory,
                                contentDescription = "LiteRT-LM Engine",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LiteRT-LM On-Device Engine",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Active: ${uiState.brainState.activeBackend} • Zero Cloud AI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.brainState.isEngineReady) {
                                OutlinedButton(
                                    onClick = onUnloadModel,
                                    modifier = Modifier.testTag("unload_model_button")
                                ) {
                                    Text("Unload")
                                }
                            }
                        }

                        uiState.brainState.lastError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Text(
                            text = "Execution Backend",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.backendPreference == AiBackendPreference.GPU,
                                onClick = { onSetBackend(AiBackendPreference.GPU) },
                                label = { Text("GPU (OpenCL)") },
                                modifier = Modifier.testTag("backend_gpu_chip")
                            )
                            FilterChip(
                                selected = uiState.preferences.backendPreference == AiBackendPreference.CPU,
                                onClick = { onSetBackend(AiBackendPreference.CPU) },
                                label = { Text("CPU") },
                                modifier = Modifier.testTag("backend_cpu_chip")
                            )
                        }

                        OutlinedButton(
                            onClick = { modelPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_local_model_button")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import local .litertlm model", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Local .litertlm File")
                        }
                    }
                }
            }

            // Open-Source On-Device Model Catalog
            item {
                Text(
                    text = "Open On-Device Models (litert-community)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(LocalAiBrain.MODEL_CATALOG, key = { it.id }) { spec ->
                val isDownloading = uiState.brainState.downloadingModelId == spec.id
                val alreadyInstalled = uiState.installedModels.any { it.name == spec.fileName }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${spec.expectedSizeLabel} • ${spec.fileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (isDownloading) {
                                OutlinedButton(onClick = onCancelDownload) {
                                    Text("Cancel")
                                }
                            } else if (!alreadyInstalled) {
                                FilledTonalButton(
                                    onClick = { onDownloadCatalogModel(spec) },
                                    enabled = uiState.brainState.downloadingModelId == null,
                                    modifier = Modifier.testTag("download_model_${spec.id}")
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = "Download model", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download")
                                }
                            } else {
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = spec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isDownloading) {
                            val progress = uiState.brainState.downloadProgress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}% (${uiState.brainState.downloadedBytes / (1024 * 1024)} MB)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            // Installed Models on Device
            if (uiState.installedModels.isNotEmpty()) {
                item {
                    Text(
                        text = "Installed Models on Device",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(uiState.installedModels, key = { it.absolutePath }) { file ->
                    val isLoaded = uiState.brainState.activeModelPath == file.absolutePath && uiState.brainState.isEngineReady
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${file.length() / (1024 * 1024)} MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isLoaded) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                FilledTonalButton(onClick = { onLoadModel(file.absolutePath) }) {
                                    Text("Load")
                                }
                            }
                            IconButton(onClick = { onDeleteModel(file) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${file.name}")
                            }
                        }
                    }
                }
            }

            // Language Policy
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Language Policy", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Source code is always generated in English. AI replies match the user's language (Auto: বাংলা -> বাংলা, English -> English).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.languagePolicy == LanguagePolicy.AUTO_DETECT,
                                onClick = { onSetLanguagePolicy(LanguagePolicy.AUTO_DETECT) },
                                label = { Text("Auto (EN/BN)") }
                            )
                            FilterChip(
                                selected = uiState.preferences.languagePolicy == LanguagePolicy.ENGLISH,
                                onClick = { onSetLanguagePolicy(LanguagePolicy.ENGLISH) },
                                label = { Text("English") }
                            )
                            FilterChip(
                                selected = uiState.preferences.languagePolicy == LanguagePolicy.BENGALI,
                                onClick = { onSetLanguagePolicy(LanguagePolicy.BENGALI) },
                                label = { Text("বাংলা") }
                            )
                        }
                    }
                }
            }

            // Appearance
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Studio Appearance", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.themeMode == ThemeMode.DARK,
                                onClick = { onSetThemeMode(ThemeMode.DARK) },
                                label = { Text("Dark IDE") }
                            )
                            FilterChip(
                                selected = uiState.preferences.themeMode == ThemeMode.LIGHT,
                                onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                                label = { Text("Light Paper") }
                            )
                            FilterChip(
                                selected = uiState.preferences.themeMode == ThemeMode.SYSTEM,
                                onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                                label = { Text("System") }
                            )
                        }
                    }
                }
            }

            // Workspace Cleanup
            uiState.activeProject?.let { active ->
                item {
                    Button(
                        onClick = { onDeleteActiveProject(active.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_active_workspace_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete active workspace", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Workspace '${active.name}'")
                    }
                }
            }
        }
    }
}
