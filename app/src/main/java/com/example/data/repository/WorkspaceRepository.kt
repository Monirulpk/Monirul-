package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.AiBackendPreference
import com.example.data.local.AppDatabase
import com.example.data.local.BuildTaskEntity
import com.example.data.local.LanguagePolicy
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PipelineRunEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.ProjectEntity
import com.example.data.local.StudioPreferences
import com.example.domain.ApkGeneratorAndDelivery
import com.example.domain.AppLanguage
import com.example.domain.AutomatedTestSuiteReport
import com.example.domain.AutomaticBuildAndFixSystem
import com.example.domain.AutomaticCodingAndIntegration
import com.example.domain.BuildVerificationReport
import com.example.domain.CatalogModelSpec
import com.example.domain.DeliveryPackageResult
import com.example.domain.DuplicateCheckResult
import com.example.domain.DuplicateFeatureChecker
import com.example.domain.FixAttemptReport
import com.example.domain.LanguageHelper
import com.example.domain.LocalAiBrain
import com.example.domain.LocalBrainUiState
import com.example.domain.PdfImportResult
import com.example.domain.PdfReaderAnalyzer
import com.example.domain.ProjectFileManager
import com.example.domain.ProjectFileNode
import com.example.domain.TaskPlanner
import com.example.domain.WorkspaceSymbolIndex
import com.example.ui.theme.ThemeMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class WorkspaceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val projectDao = db.projectDao()
    private val pdfDao = db.pdfDocumentDao()
    private val taskDao = db.buildTaskDao()
    private val runDao = db.pipelineRunDao()

    val prefsManager = PreferencesManager(appContext)
    val fileManager = ProjectFileManager(appContext)
    val pdfAnalyzer = PdfReaderAnalyzer(appContext)
    val localAiBrain = LocalAiBrain(appContext)
    val duplicateChecker = DuplicateFeatureChecker()
    val taskPlanner = TaskPlanner()
    val codingAndIntegration = AutomaticCodingAndIntegration(fileManager, localAiBrain)
    val buildAndFixSystem = AutomaticBuildAndFixSystem()
    val apkDelivery = ApkGeneratorAndDelivery(appContext)

    val projectsFlow: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()
    val preferencesFlow: Flow<StudioPreferences> = prefsManager.preferencesFlow
    val brainStateFlow: StateFlow<LocalBrainUiState> = localAiBrain.state

    fun observePdfs(projectId: String): Flow<List<PdfDocumentEntity>> =
        pdfDao.observePdfsForProject(projectId)

    fun observeTasks(projectId: String): Flow<List<BuildTaskEntity>> =
        taskDao.observeTasksForProject(projectId)

    fun observePipelineRuns(projectId: String): Flow<List<PipelineRunEntity>> =
        runDao.observeRunsForProject(projectId)

    suspend fun createProject(
        name: String,
        packageName: String,
        description: String
    ): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val cleanPkg = packageName.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9.]"), "")
            .ifBlank { "com.example.generatedapp" }
        val root = fileManager.initializeProjectStructure(id, name.trim(), cleanPkg)
        val entity = ProjectEntity(
            id = id,
            name = name.trim(),
            packageName = cleanPkg,
            description = description.trim().ifBlank { "Android Compose application workspace" },
            rootPath = root.absolutePath
        )
        projectDao.insertProject(entity)
        prefsManager.setActiveProjectId(id)
        return entity
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
        fileManager.deleteWorkspace(projectId)
    }

    suspend fun setActiveProject(projectId: String) {
        prefsManager.setActiveProjectId(projectId)
    }

    suspend fun listFiles(project: ProjectEntity): List<ProjectFileNode> =
        fileManager.listProjectTree(File(project.rootPath))

    suspend fun readFile(project: ProjectEntity, relativePath: String): String =
        fileManager.readFileSafe(File(project.rootPath), relativePath)

    suspend fun writeFile(project: ProjectEntity, relativePath: String, content: String) {
        fileManager.writeFileSafe(File(project.rootPath), relativePath, content)
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun createFolder(project: ProjectEntity, relativePath: String) {
        fileManager.createDirectorySafe(File(project.rootPath), relativePath)
    }

    suspend fun deleteFileOrFolder(project: ProjectEntity, relativePath: String) {
        fileManager.deleteSafe(File(project.rootPath), relativePath)
    }

    suspend fun importPdf(project: ProjectEntity, uri: Uri): PdfImportResult {
        val result = pdfAnalyzer.importAndAnalyzePdf(uri, File(project.rootPath))
        val entity = PdfDocumentEntity(
            id = result.id,
            projectId = project.id,
            fileName = result.fileName,
            localPath = result.localFile.absolutePath,
            fileSizeBytes = result.sizeBytes,
            pageCount = result.pageCount,
            extractedText = result.extractedText,
            detectedLanguage = result.detectedLanguage.name,
            parsedSummary = result.analysis.summaryText
        )
        pdfDao.insertPdf(entity)
        return result
    }

    suspend fun renderPdfPage(pdfPath: String, pageIndex: Int): Bitmap? =
        pdfAnalyzer.renderPageBitmap(File(pdfPath), pageIndex)

    suspend fun deletePdf(pdf: PdfDocumentEntity) {
        pdfDao.deletePdfById(pdf.id)
        runCatching { File(pdf.localPath).delete() }
    }

    suspend fun indexWorkspaceSymbols(project: ProjectEntity): WorkspaceSymbolIndex =
        duplicateChecker.indexWorkspace(File(project.rootPath))

    suspend fun checkDuplicateFeature(
        project: ProjectEntity,
        featureName: String,
        targetFile: String,
        language: AppLanguage
    ): DuplicateCheckResult {
        val index = indexWorkspaceSymbols(project)
        return duplicateChecker.checkFeature(featureName, targetFile, index, language)
    }

    suspend fun planTasksFromPdf(
        project: ProjectEntity,
        pdf: PdfDocumentEntity
    ): List<BuildTaskEntity> {
        val lang = runCatching { AppLanguage.valueOf(pdf.detectedLanguage) }
            .getOrDefault(AppLanguage.ENGLISH)
        val analysis = pdfAnalyzer.analyzeExtractedText(
            pdf.extractedText,
            pdf.fileName,
            pdf.pageCount,
            lang
        )
        val existing = taskDao.getTasksForProject(project.id)
        val symbols = indexWorkspaceSymbols(project)
        val planned = taskPlanner.createPlanFromPdfAnalysis(
            projectId = project.id,
            packageName = project.packageName,
            analysis = analysis,
            existingTasks = existing,
            symbolIndex = symbols
        )
        taskDao.insertTasks(planned)
        return planned
    }

    suspend fun planTasksFromPrompt(
        project: ProjectEntity,
        instruction: String,
        policy: LanguagePolicy
    ): List<BuildTaskEntity> {
        val lang = LanguageHelper.resolveReplyLanguage(instruction, policy)
        val existing = taskDao.getTasksForProject(project.id)
        val symbols = indexWorkspaceSymbols(project)
        val planned = taskPlanner.createPlanFromInstruction(
            projectId = project.id,
            packageName = project.packageName,
            instruction = instruction,
            existingTasks = existing,
            symbolIndex = symbols,
            language = lang
        )
        taskDao.insertTasks(planned)
        return planned
    }

    suspend fun addManualTask(
        project: ProjectEntity,
        title: String,
        description: String,
        category: String,
        targetFile: String
    ) {
        val existing = taskDao.getTasksForProject(project.id)
        val order = (existing.maxOfOrNull { it.stepOrder } ?: 0) + 1
        taskDao.insertTask(
            BuildTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                stepOrder = order,
                title = title,
                description = description,
                category = category,
                targetFile = targetFile
            )
        )
    }

    suspend fun updateTask(task: BuildTaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(taskId: String) = taskDao.deleteTaskById(taskId)

    suspend fun clearTasks(projectId: String) = taskDao.clearTasksForProject(projectId)

    suspend fun executeCodingTask(
        project: ProjectEntity,
        task: BuildTaskEntity,
        policy: LanguagePolicy
    ) = codingAndIntegration.executeTask(
        projectRoot = File(project.rootPath),
        packageName = project.packageName,
        task = task,
        languagePolicy = policy
    )

    suspend fun integrateProjectFeatures(project: ProjectEntity): String? =
        codingAndIntegration.integrateAllScreensIntoMainActivity(
            File(project.rootPath),
            project.packageName
        )

    suspend fun runStaticBuild(project: ProjectEntity): BuildVerificationReport {
        val report = buildAndFixSystem.runBuildVerification(File(project.rootPath))
        projectDao.updateBuildStatus(
            project.id,
            if (report.passed) "SUCCESS" else "FAILED"
        )
        return report
    }

    suspend fun autoFixBuildErrors(
        project: ProjectEntity,
        report: BuildVerificationReport
    ): FixAttemptReport {
        val fixReport = buildAndFixSystem.autoFixAndRetry(
            File(project.rootPath),
            project.packageName,
            report
        )
        val status = when {
            fixReport.postFixReport.passed && fixReport.errorsFixed > 0 -> "FIXED"
            fixReport.postFixReport.passed -> "SUCCESS"
            else -> "FAILED"
        }
        projectDao.updateBuildStatus(project.id, status)
        return fixReport
    }

    suspend fun runAutomatedTests(
        project: ProjectEntity,
        buildReport: BuildVerificationReport
    ): AutomatedTestSuiteReport =
        buildAndFixSystem.runAutomatedTests(File(project.rootPath), buildReport)

    suspend fun packageAndDeliver(
        project: ProjectEntity,
        tasks: List<BuildTaskEntity>,
        buildReport: BuildVerificationReport,
        fixReport: FixAttemptReport?,
        testReport: AutomatedTestSuiteReport,
        replyLanguage: AppLanguage
    ): DeliveryPackageResult {
        val result = apkDelivery.packageAndVerifyProject(
            project = project,
            projectRoot = File(project.rootPath),
            tasks = tasks,
            buildReport = buildReport,
            fixReport = fixReport,
            testReport = testReport,
            brainMode = localAiBrain.state.value.activeBackend,
            replyLanguage = replyLanguage
        )
        runDao.insertRun(
            PipelineRunEntity(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                startedAt = System.currentTimeMillis(),
                finishedAt = System.currentTimeMillis(),
                status = if (buildReport.passed && result.verifiedZip) "DELIVERED" else "COMPLETED_WITH_WARNINGS",
                brainMode = localAiBrain.state.value.activeBackend,
                buildPassed = buildReport.passed,
                testsPassed = testReport.passedCount,
                testsTotal = testReport.totalCount,
                errorsFound = fixReport?.errorsFound ?: buildReport.issues.count { it.severity == "ERROR" },
                errorsFixed = fixReport?.errorsFixed ?: 0,
                packagePath = result.bundleFile.absolutePath,
                packageSha256 = result.sha256,
                reportMarkdown = result.reportMarkdown
            )
        )
        return result
    }

    suspend fun askLocalAi(
        prompt: String,
        project: ProjectEntity?,
        policy: LanguagePolicy,
        onChunk: ((String) -> Unit)? = null
    ): String {
        val contextHeader = if (project != null) {
            "Active Project: ${project.name} (${project.packageName}). "
        } else ""
        return localAiBrain.generateText(contextHeader + prompt, policy, onChunk)
    }

    fun listInstalledModels(): List<File> = localAiBrain.listInstalledModels()

    fun loadModel(path: String, backend: AiBackendPreference) =
        localAiBrain.loadModel(path, backend)

    fun unloadModel() = localAiBrain.unloadModel()

    fun downloadModel(
        spec: CatalogModelSpec,
        backend: AiBackendPreference,
        onComplete: (File) -> Unit
    ) = localAiBrain.downloadCatalogModel(spec, backend, onComplete)

    fun cancelDownload() = localAiBrain.cancelDownload()

    suspend fun importModelUri(uri: Uri, backend: AiBackendPreference): File =
        localAiBrain.importLocalModelUri(uri, backend)

    fun deleteModelFile(file: File) = localAiBrain.deleteModelFile(file)

    fun shutdown() = localAiBrain.shutdown()
}
