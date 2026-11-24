package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET lastBuildStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBuildStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents WHERE projectId = :projectId ORDER BY importedAt DESC")
    fun observePdfsForProject(projectId: String): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getPdfById(id: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePdfById(id: String)
}

@Dao
interface BuildTaskDao {
    @Query("SELECT * FROM build_tasks WHERE projectId = :projectId ORDER BY stepOrder ASC")
    fun observeTasksForProject(projectId: String): Flow<List<BuildTaskEntity>>

    @Query("SELECT * FROM build_tasks WHERE projectId = :projectId ORDER BY stepOrder ASC")
    suspend fun getTasksForProject(projectId: String): List<BuildTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<BuildTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: BuildTaskEntity)

    @Update
    suspend fun updateTask(task: BuildTaskEntity)

    @Query("DELETE FROM build_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM build_tasks WHERE projectId = :projectId")
    suspend fun clearTasksForProject(projectId: String)
}

@Dao
interface PipelineRunDao {
    @Query("SELECT * FROM pipeline_runs WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeRunsForProject(projectId: String): Flow<List<PipelineRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: PipelineRunEntity)

    @Update
    suspend fun updateRun(run: PipelineRunEntity)
}
