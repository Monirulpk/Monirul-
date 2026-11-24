package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val rootPath: String,
    val lastBuildStatus: String = "NOT_BUILT",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pdf_documents",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val fileName: String,
    val localPath: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val extractedText: String,
    val detectedLanguage: String,
    val parsedSummary: String,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "build_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class BuildTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val stepOrder: Int,
    val title: String,
    val description: String,
    val category: String,
    val targetFile: String,
    val status: String = "PENDING",
    val duplicateWarning: String? = null,
    val outputNote: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pipeline_runs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class PipelineRunEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    val brainMode: String,
    val buildPassed: Boolean = false,
    val testsPassed: Int = 0,
    val testsTotal: Int = 0,
    val errorsFound: Int = 0,
    val errorsFixed: Int = 0,
    val packagePath: String? = null,
    val packageSha256: String? = null,
    val reportMarkdown: String = ""
)
