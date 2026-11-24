package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.StudioUiState

@Composable
fun PdfUploadScreen(
    uiState: StudioUiState,
    onImportPdf: (Uri) -> Unit,
    onSelectPdfPage: (PdfDocumentEntity, Int) -> Unit,
    onDeletePdf: (PdfDocumentEntity) -> Unit,
    onPlanTasksFromPdf: () -> Unit,
    onNavigateToProgress: () -> Unit
) {
    val project = uiState.activeProject
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPdf(uri)
    }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Create or select a project workspace on Home before importing PDFs.",
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF Upload & Analyzer",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Target: ${project.name} (stored locally in pdfs/)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("upload_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = stringResource(R.string.cd_import_pdf),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import PDF")
                    }
                }
            }

            if (uiState.pdfs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = "No PDF imported",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No PDF specifications uploaded yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Select any PDF file from your phone. AI Dev Studio will extract text (with on-device ML Kit OCR fallback for scanned pages), render page previews, and create an ordered Task Plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.pdfs, key = { it.id }) { pdf ->
                    val selected = pdf.id == uiState.selectedPdf?.id
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPdfPage(pdf, 0) }
                            .testTag("pdf_item_${pdf.fileName}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = "PDF file",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pdf.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pdf.pageCount} page(s) • ${pdf.fileSizeBytes / 1024} KB • ${pdf.detectedLanguage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeletePdf(pdf) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete ${pdf.fileName}"
                                )
                            }
                        }
                    }
                }
            }

            // Selected PDF Preview & Extracted Requirements
            uiState.selectedPdf?.let { pdf ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Requirement Summary",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = pdf.parsedSummary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        onPlanTasksFromPdf()
                                        onNavigateToProgress()
                                    },
                                    modifier = Modifier.testTag("send_pdf_to_planner_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoFixHigh,
                                        contentDescription = "Generate task plan",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Task Plan")
                                }
                            }
                        }
                    }
                }

                // Native PdfRenderer Page Preview
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    text = "Page Preview (${uiState.pdfPreviewPage + 1}/${pdf.pageCount.coerceAtLeast(1)})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (uiState.pdfPreviewPage > 0) {
                                                onSelectPdfPage(pdf, uiState.pdfPreviewPage - 1)
                                            }
                                        },
                                        enabled = uiState.pdfPreviewPage > 0
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Previous page"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (uiState.pdfPreviewPage + 1 < pdf.pageCount) {
                                                onSelectPdfPage(pdf, uiState.pdfPreviewPage + 1)
                                            }
                                        },
                                        enabled = uiState.pdfPreviewPage + 1 < pdf.pageCount
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Next page"
                                        )
                                    }
                                }
                            }

                            val bmp = uiState.pdfPreviewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Rendered PDF page preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(340.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                FilledTonalButton(onClick = { onSelectPdfPage(pdf, 0) }) {
                                    Text("Render Page Preview")
                                }
                            }
                        }
                    }
                }

                // Extracted Text Block
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
                                text = "Extracted PDF Text",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = pdf.extractedText.take(4000),
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
