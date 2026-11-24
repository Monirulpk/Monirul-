package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.StudioUiState

@Composable
fun ProjectScreen(
    uiState: StudioUiState,
    isExpandedScreen: Boolean,
    onOpenFile: (String) -> Unit,
    onCloseFile: () -> Unit,
    onUpdateEditor: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeletePath: (String) -> Unit,
    onCheckDuplicate: (String) -> Unit,
    onRunBuildAndFix: () -> Unit
) {
    val project = uiState.activeProject
    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select or create a project workspace on the Home tab first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var duplicateQuery by remember { mutableStateOf("") }

    if (!isExpandedScreen && uiState.selectedFilePath != null) {
        BackHandler { onCloseFile() }
    }

    if (showNewFileDialog) {
        var relPath by remember {
            mutableStateOf("app/src/main/java/${project.packageName.replace('.', '/')}/NewFeature.kt")
        }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New Workspace File") },
            text = {
                OutlinedTextField(
                    value = relPath,
                    onValueChange = { relPath = it },
                    label = { Text("Relative path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_file_path_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (relPath.isNotBlank()) {
                            val defaultBody = if (relPath.endsWith(".kt")) {
                                "package ${project.packageName}\n\nclass ${relPath.substringAfterLast('/').removeSuffix(".kt")} {\n}\n"
                            } else ""
                            onCreateFile(relPath.trim(), defaultBody)
                        }
                        showNewFileDialog = false
                    },
                    modifier = Modifier.testTag("confirm_new_file_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFolderDialog) {
        var folderPath by remember { mutableStateOf("app/src/main/java/${project.packageName.replace('.', '/')}/utils") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    label = { Text("Folder relative path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderPath.isNotBlank()) onCreateFolder(folderPath.trim())
                        showNewFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    val fileTreePane: @Composable (Modifier) -> Unit = { paneModifier ->
        LazyColumn(
            modifier = paneModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${uiState.projectFiles.count { !it.isDirectory }} files • ${project.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { showNewFileDialog = true },
                            modifier = Modifier.testTag("create_file_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Create new file")
                        }
                        IconButton(
                            onClick = { showNewFolderDialog = true },
                            modifier = Modifier.testTag("create_folder_button")
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create new folder")
                        }
                    }
                }
            }

            // Duplicate Feature Checker Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Duplicate Feature Checker",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val idx = uiState.symbolIndex
                        Text(
                            text = "Indexed ${idx?.composables?.size ?: 0} @Composable screens, ${idx?.classesAndObjects?.size ?: 0} classes, ${idx?.permissions?.size ?: 0} permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = duplicateQuery,
                                onValueChange = { duplicateQuery = it },
                                placeholder = { Text("Feature or screen name...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("duplicate_check_input")
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (duplicateQuery.isNotBlank()) onCheckDuplicate(duplicateQuery)
                                },
                                modifier = Modifier.testTag("duplicate_check_button")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Check duplicate", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check")
                            }
                        }
                        uiState.duplicateCheckResult?.let { dup ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (dup.isDuplicate) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                }
                            ) {
                                Text(
                                    text = dup.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // File Tree List
            items(uiState.projectFiles, key = { it.relativePath }) { node ->
                val isSelected = node.relativePath == uiState.selectedFilePath
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!node.isDirectory) onOpenFile(node.relativePath)
                        }
                        .testTag("file_node_${node.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = (12 + node.depth * 14).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = if (node.isDirectory) "Folder" else "File",
                            tint = if (node.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!node.isDirectory) {
                                Text(
                                    text = "${node.sizeBytes} B",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (node.relativePath != "app" && node.relativePath != "pdfs") {
                            IconButton(
                                onClick = { onDeletePath(node.relativePath) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete ${node.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val editorPane: @Composable (Modifier) -> Unit = { paneModifier ->
        val openPath = uiState.selectedFilePath
        if (openPath == null) {
            Box(
                modifier = paneModifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a Kotlin, XML, or Gradle file from the tree to inspect or edit it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = paneModifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isExpandedScreen) {
                        IconButton(
                            onClick = onCloseFile,
                            modifier = Modifier.testTag("editor_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to file tree")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = openPath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = openPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onRunBuildAndFix,
                        modifier = Modifier.testTag("editor_verify_button")
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = "Verify workspace", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify")
                    }
                    Button(
                        onClick = onSaveFile,
                        enabled = uiState.isFileModified,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save file", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }

                OutlinedTextField(
                    value = uiState.selectedFileContent,
                    onValueChange = onUpdateEditor,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("code_editor_textfield")
                )
            }
        }
    }

    if (isExpandedScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            fileTreePane(
                Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            )
            editorPane(
                Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            )
        }
    } else {
        if (uiState.selectedFilePath != null) {
            editorPane(Modifier.fillMaxSize())
        } else {
            fileTreePane(Modifier.fillMaxSize())
        }
    }
}
