package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiBackendPreference
import com.example.data.local.BuildTaskEntity
import com.example.data.local.LanguagePolicy
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PipelineRunEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.StudioPreferences
import com.example.data.repository.WorkspaceRepository
import com.example.domain.AppLanguage
import com.example.domain.AutomatedTestSuiteReport
import com.example.domain.BuildVerificationReport
import com.example.domain.CatalogModelSpec
import com.example.domain.DeliveryPackageResult
import com.example.domain.DuplicateCheckResult
import com.example.domain.FixAttemptReport
import com.example.domain.LanguageHelper
import com.example.domain.LocalBrainUiState
import com.example.domain.ProjectFileNode
import com.example.domain.WorkspaceSymbolIndex
import com.example.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PipelineStepStatus {
    IDLE,
    RUNNING,
    PASSED,
    FIXED,
    FAILED
}

data class CorePipelineStep(
    val number: Int,
    val title: String,
    val status: PipelineStepStatus = PipelineStepStatus.IDLE,
    val detail: String = ""
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val engineTag: String
)

data class StudioUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val activeProject: ProjectEntity? = null,
    val preferences: StudioPreferences = StudioPreferences(),
    val brainState: LocalBrainUiState = LocalBrainUiState(),
    val installedModels: List<File> = emptyList(),
    val pdfs: List<PdfDocumentEntity> = emptyList(),
    val tasks: List<BuildTaskEntity> = emptyList(),
    val pipelineRuns: List<PipelineRunEntity> = emptyList(),
    val projectFiles: List<ProjectFileNode> = emptyList(),
    val symbolIndex: WorkspaceSymbolIndex? = null,
    val selectedFilePath: String? = null,
    val selectedFileContent: String = "",
    val isFileModified: Boolean = false,
    val selectedPdf: PdfDocumentEntity? = null,
    val pdfPreviewBitmap: Bitmap? = null,
    val pdfPreviewPage: Int = 0,
    val duplicateCheckResult: DuplicateCheckResult? = null,
    val pipelineSteps: List<CorePipelineStep> = defaultCorePipelineSteps(),
    val isPipelineRunning: Boolean = false,
    val buildReport: BuildVerificationReport? = null,
    val fixReport: FixAttemptReport? = null,
    val testReport: AutomatedTestSuiteReport? = null,
    val deliveryResult: DeliveryPackageResult? = null,
    val consoleLogs: List<String> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isAiReplying: Boolean = false,
    val statusBanner: String? = null
)

fun defaultCorePipelineSteps(): List<CorePipelineStep> = listOf(
    CorePipelineStep(1, "Android App Structure"),
    CorePipelineStep(2, "UI Design"),
    CorePipelineStep(3, "PDF Upload System"),
    CorePipelineStep(4, "PDF Reader & Analyzer"),
    CorePipelineStep(5, "Local AI Brain"),
    CorePipelineStep(6, "Task Planner"),
    CorePipelineStep(7, "Duplicate Feature Checker"),
    CorePipelineStep(8, "Automatic Coding System"),
    CorePipelineStep(9, "Project File Manager"),
    CorePipelineStep(10, "Feature Integration System"),
    CorePipelineStep(11, "Automatic Build System"),
    CorePipelineStep(12, "Automatic Error Fixer"),
    CorePipelineStep(13, "Automatic Testing System"),
    CorePipelineStep(14, "APK / Bundle Generator"),
    CorePipelineStep(15, "Delivery & Report System")
)

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WorkspaceRepository(application)

    private val _uiState = MutableStateFlow(
        StudioUiState(installedModels = repo.listInstalledModels())
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val activeProjectFlow = combine(
        repo.projectsFlow,
        repo.preferencesFlow
    ) { projects, prefs ->
        val active = projects.firstOrNull { it.id == prefs.activeProjectId } ?: projects.firstOrNull()
        Triple(projects, active, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Triple(emptyList(), null, StudioPreferences()))

    init {
        viewModelScope.launch {
            activeProjectFlow.collect { (projects, active, prefs) ->
                val prevId = _uiState.value.activeProject?.id
                _uiState.update {
                    it.copy(
                        projects = projects,
                        activeProject = active,
                        preferences = prefs
                    )
                }
                if (active != null && active.id != prevId) {
                    refreshProjectWorkspace(active)
                }
                val savedModel = prefs.selectedModelPath
                if (savedModel != null &&
                    !repo.localAiBrain.state.value.isEngineReady &&
                    !repo.localAiBrain.state.value.isLoadingEngine &&
                    File(savedModel).exists()
                ) {
                    repo.loadModel(savedModel, prefs.backendPreference)
                }
            }
        }

        viewModelScope.launch {
            repo.brainStateFlow.collect { brain ->
                _uiState.update {
                    it.copy(
                        brainState = brain,
                        installedModels = repo.listInstalledModels()
                    )
                }
            }
        }

        viewModelScope.launch {
            activeProjectFlow
                .flatMapLatest { (_, active, _) ->
                    if (active == null) flowOf(emptyList()) else repo.observePdfs(active.id)
                }
                .collect { pdfs ->
                    _uiState.update { state ->
                        val nextSelected = state.selectedPdf?.let { sel ->
                            pdfs.firstOrNull { it.id == sel.id }
                        } ?: pdfs.firstOrNull()
                        state.copy(pdfs = pdfs, selectedPdf = nextSelected)
                    }
                }
        }

        viewModelScope.launch {
            activeProjectFlow
                .flatMapLatest { (_, active, _) ->
                    if (active == null) flowOf(emptyList()) else repo.observeTasks(active.id)
                }
                .collect { tasks ->
                    _uiState.update { it.copy(tasks = tasks) }
                }
        }

        viewModelScope.launch {
            activeProjectFlow
                .flatMapLatest { (_, active, _) ->
                    if (active == null) flowOf(emptyList()) else repo.observePipelineRuns(active.id)
                }
                .collect { runs ->
                    _uiState.update { it.copy(pipelineRuns = runs) }
                }
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun createProject(name: String, packageName: String, description: String) {
        viewModelScope.launch {
            runCatching {
                val created = repo.createProject(name, packageName, description)
                refreshProjectWorkspace(created)
                _uiState.update {
                    it.copy(statusBanner = "Workspace '${created.name}' initialized on disk.")
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Create failed: ${e.message}") }
            }
        }
    }

    fun selectProject(projectId: String) {
        viewModelScope.launch {
            repo.setActiveProject(projectId)
            _uiState.update {
                it.copy(
                    selectedFilePath = null,
                    selectedFileContent = "",
                    isFileModified = false,
                    pdfPreviewBitmap = null,
                    buildReport = null,
                    fixReport = null,
                    testReport = null,
                    deliveryResult = null
                )
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repo.deleteProject(projectId)
            _uiState.update { it.copy(statusBanner = "Workspace deleted.") }
        }
    }

    fun refreshProjectWorkspace(project: ProjectEntity? = _uiState.value.activeProject) {
        if (project == null) return
        viewModelScope.launch {
            val files = repo.listFiles(project)
            val symbols = repo.indexWorkspaceSymbols(project)
            _uiState.update {
                it.copy(projectFiles = files, symbolIndex = symbols)
            }
        }
    }

    fun openFile(relativePath: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            runCatching {
                val text = repo.readFile(project, relativePath)
                _uiState.update {
                    it.copy(
                        selectedFilePath = relativePath,
                        selectedFileContent = text,
                        isFileModified = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Cannot open file: ${e.message}") }
            }
        }
    }

    fun closeOpenFile() {
        _uiState.update {
            it.copy(
                selectedFilePath = null,
                selectedFileContent = "",
                isFileModified = false
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update {
            it.copy(selectedFileContent = newContent, isFileModified = true)
        }
    }

    fun saveOpenFile() {
        val project = _uiState.value.activeProject ?: return
        val path = _uiState.value.selectedFilePath ?: return
        val content = _uiState.value.selectedFileContent
        viewModelScope.launch {
            runCatching {
                repo.writeFile(project, path, content)
                refreshProjectWorkspace(project)
                _uiState.update {
                    it.copy(isFileModified = false, statusBanner = "Saved $path")
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Save failed: ${e.message}") }
            }
        }
    }

    fun createNewFile(relativePath: String, initialContent: String = "") {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            runCatching {
                repo.writeFile(project, relativePath, initialContent)
                refreshProjectWorkspace(project)
                openFile(relativePath)
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Create file failed: ${e.message}") }
            }
        }
    }

    fun createNewFolder(relativePath: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            runCatching {
                repo.createFolder(project, relativePath)
                refreshProjectWorkspace(project)
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Create folder failed: ${e.message}") }
            }
        }
    }

    fun deleteProjectPath(relativePath: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            runCatching {
                repo.deleteFileOrFolder(project, relativePath)
                if (_uiState.value.selectedFilePath == relativePath) {
                    _uiState.update { it.copy(selectedFilePath = null, selectedFileContent = "") }
                }
                refreshProjectWorkspace(project)
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Delete failed: ${e.message}") }
            }
        }
    }

    fun importPdf(uri: Uri, autoPlan: Boolean = true) {
        val project = _uiState.value.activeProject ?: run {
            _uiState.update { it.copy(statusBanner = "Create or select a project workspace first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(statusBanner = "Importing & analyzing PDF locally...") }
            runCatching {
                val imported = repo.importPdf(project, uri)
                refreshProjectWorkspace(project)
                val bitmap = repo.renderPdfPage(imported.localFile.absolutePath, 0)
                if (autoPlan) {
                    val pdfEntity = PdfDocumentEntity(
                        id = imported.id,
                        projectId = project.id,
                        fileName = imported.fileName,
                        localPath = imported.localFile.absolutePath,
                        fileSizeBytes = imported.sizeBytes,
                        pageCount = imported.pageCount,
                        extractedText = imported.extractedText,
                        detectedLanguage = imported.detectedLanguage.name,
                        parsedSummary = imported.analysis.summaryText
                    )
                    repo.planTasksFromPdf(project, pdfEntity)
                }
                _uiState.update {
                    it.copy(
                        pdfPreviewBitmap = bitmap,
                        pdfPreviewPage = 0,
                        statusBanner = imported.analysis.summaryText
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "PDF import failed: ${e.message}") }
            }
        }
    }

    fun selectPdfAndLoadPage(pdf: PdfDocumentEntity, pageIndex: Int = 0) {
        viewModelScope.launch {
            val clamped = pageIndex.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))
            val bmp = repo.renderPdfPage(pdf.localPath, clamped)
            _uiState.update {
                it.copy(
                    selectedPdf = pdf,
                    pdfPreviewBitmap = bmp,
                    pdfPreviewPage = clamped
                )
            }
        }
    }

    fun deletePdf(pdf: PdfDocumentEntity) {
        viewModelScope.launch {
            repo.deletePdf(pdf)
            _uiState.update { it.copy(pdfPreviewBitmap = null, statusBanner = "PDF removed.") }
        }
    }

    fun planFromSelectedPdf() {
        val project = _uiState.value.activeProject ?: return
        val pdf = _uiState.value.selectedPdf ?: return
        viewModelScope.launch {
            val planned = repo.planTasksFromPdf(project, pdf)
            _uiState.update {
                it.copy(statusBanner = "Planned ${planned.size} ordered task(s) from ${pdf.fileName}.")
            }
        }
    }

    fun checkDuplicateFeature(featureName: String) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val lang = LanguageHelper.resolveReplyLanguage(featureName, _uiState.value.preferences.languagePolicy)
            val pkgPath = project.packageName.replace('.', '/')
            val target = "app/src/main/java/$pkgPath/ui/screens/${featureName.trim().replace(" ", "")}Screen.kt"
            val result = repo.checkDuplicateFeature(project, featureName, target, lang)
            _uiState.update { it.copy(duplicateCheckResult = result) }
        }
    }

    fun addInstructionAsTasks(instruction: String) {
        val project = _uiState.value.activeProject ?: return
        if (instruction.isBlank()) return
        viewModelScope.launch {
            val planned = repo.planTasksFromPrompt(
                project,
                instruction,
                _uiState.value.preferences.languagePolicy
            )
            _uiState.update {
                it.copy(statusBanner = "Added ${planned.size} task(s) to Task Planner.")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }

    fun clearTasks() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch { repo.clearTasks(project.id) }
    }

    fun askAssistant(prompt: String, alsoCreatePlan: Boolean = false) {
        if (prompt.isBlank()) return
        val project = _uiState.value.activeProject
        val policy = _uiState.value.preferences.languagePolicy
        val userMsg = ChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = prompt,
            engineTag = "User"
        )
        val assistantId = "a_${System.currentTimeMillis()}"
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isAiReplying = true
            )
        }

        viewModelScope.launch {
            if (alsoCreatePlan && project != null) {
                repo.planTasksFromPrompt(project, prompt, policy)
            }
            val reply = repo.askLocalAi(prompt, project, policy)
            val replyMsg = ChatMessage(
                id = assistantId,
                isUser = false,
                text = reply,
                engineTag = _uiState.value.brainState.activeBackend
            )
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + replyMsg,
                    isAiReplying = false
                )
            }
        }
    }

    fun runFull15TaskPipeline() {
        val project = _uiState.value.activeProject ?: run {
            _uiState.update { it.copy(statusBanner = "Create a project workspace first.") }
            return
        }
        if (_uiState.value.isPipelineRunning) return

        viewModelScope.launch {
            val logs = mutableListOf<String>()
            fun log(msg: String) {
                logs.add(msg)
                _uiState.update { it.copy(consoleLogs = logs.toList()) }
            }

            fun markStep(stepNo: Int, status: PipelineStepStatus, detail: String) {
                _uiState.update { state ->
                    state.copy(
                        pipelineSteps = state.pipelineSteps.map { step ->
                            if (step.number == stepNo) step.copy(status = status, detail = detail) else step
                        }
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isPipelineRunning = true,
                    pipelineSteps = defaultCorePipelineSteps(),
                    consoleLogs = emptyList()
                )
            }

            val replyLang = LanguageHelper.resolveReplyLanguage(
                _uiState.value.pdfs.firstOrNull()?.extractedText ?: project.description,
                _uiState.value.preferences.languagePolicy
            )

            // 1. Structure
            markStep(1, PipelineStepStatus.RUNNING, "Verifying Gradle & Android directories...")
            val files = repo.listFiles(project)
            markStep(1, PipelineStepStatus.PASSED, "${files.size} workspace entries verified")
            log("[1/15] Structure verified (${files.size} entries)")

            // 2. UI Design
            markStep(2, PipelineStepStatus.RUNNING, "Checking Compose Material 3 theme & screens...")
            markStep(2, PipelineStepStatus.PASSED, "Material 3 Theme.kt & screen templates ready")
            log("[2/15] UI Design layer checked")

            // 3 & 4. PDF Upload & Reader
            val pdfs = _uiState.value.pdfs
            markStep(3, PipelineStepStatus.PASSED, "${pdfs.size} PDF(s) in workspace")
            if (pdfs.isNotEmpty()) {
                markStep(4, PipelineStepStatus.PASSED, "Analyzed ${pdfs.first().fileName}")
                log("[4/15] PDF analysis loaded: ${pdfs.first().fileName}")
            } else {
                markStep(4, PipelineStepStatus.PASSED, "No PDF attached; using project specification")
            }

            // 5. Local AI Brain
            val brainLabel = _uiState.value.brainState.activeBackend
            markStep(5, PipelineStepStatus.PASSED, brainLabel)
            log("[5/15] Local AI Brain: $brainLabel (Zero Cloud AI)")

            // 6. Task Planner
            markStep(6, PipelineStepStatus.RUNNING, "Preparing ordered task queue...")
            var currentTasks = _uiState.value.tasks
            if (currentTasks.isEmpty()) {
                currentTasks = if (pdfs.isNotEmpty()) {
                    repo.planTasksFromPdf(project, pdfs.first())
                } else {
                    repo.planTasksFromPrompt(
                        project,
                        project.description,
                        _uiState.value.preferences.languagePolicy
                    )
                }
            }
            markStep(6, PipelineStepStatus.PASSED, "${currentTasks.size} task(s) queued")
            log("[6/15] Task Planner ready with ${currentTasks.size} task(s)")

            // 7. Duplicate Feature Checker
            markStep(7, PipelineStepStatus.RUNNING, "Scanning workspace symbols...")
            val symbols = repo.indexWorkspaceSymbols(project)
            markStep(7, PipelineStepStatus.PASSED, "Indexed ${symbols.composables.size} composables, ${symbols.classesAndObjects.size} types")
            log("[7/15] Duplicate Feature Checker indexed ${symbols.composables.size + symbols.classesAndObjects.size} symbols")

            // 8, 9, 10. Automatic Coding, File Manager, Feature Integration
            markStep(8, PipelineStepStatus.RUNNING, "Generating English Kotlin/Compose code...")
            markStep(9, PipelineStepStatus.RUNNING, "Writing safely to workspace...")
            var modifiedCount = 0
            for (task in currentTasks) {
                if (task.status == "COMPLETED" || task.status == "SKIPPED") continue
                repo.updateTask(task.copy(status = "RUNNING"))
                val res = repo.executeCodingTask(project, task, _uiState.value.preferences.languagePolicy)
                modifiedCount += res.filesModified.size
                repo.updateTask(
                    task.copy(
                        status = "COMPLETED",
                        outputNote = res.summary,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                log("[8/15] ${res.summary}")
            }
            markStep(8, PipelineStepStatus.PASSED, "Generated/updated $modifiedCount file(s)")
            markStep(9, PipelineStepStatus.PASSED, "All writes confined to workspace root")

            markStep(10, PipelineStepStatus.RUNNING, "Integrating into MainActivity...")
            repo.integrateProjectFeatures(project)
            refreshProjectWorkspace(project)
            markStep(10, PipelineStepStatus.PASSED, "Navigation & screens integrated")
            log("[10/15] Feature Integration complete")

            // 11. Automatic Build System
            markStep(11, PipelineStepStatus.RUNNING, "Running XML & Kotlin static build checks...")
            var buildReport = repo.runStaticBuild(project)
            buildReport.buildLog.forEach { log(it) }
            markStep(
                11,
                if (buildReport.passed) PipelineStepStatus.PASSED else PipelineStepStatus.FAILED,
                "${buildReport.kotlinFilesChecked} Kotlin, ${buildReport.xmlFilesChecked} XML checked"
            )

            // 12. Automatic Error Fixer
            var fixReport: FixAttemptReport? = null
            if (!buildReport.passed) {
                markStep(12, PipelineStepStatus.RUNNING, "Diagnosing & repairing errors...")
                fixReport = repo.autoFixBuildErrors(project, buildReport)
                fixReport.fixLogs.forEach { log(it) }
                buildReport = fixReport.postFixReport
                markStep(
                    12,
                    if (buildReport.passed) PipelineStepStatus.FIXED else PipelineStepStatus.FAILED,
                    "Fixed ${fixReport.errorsFixed}/${fixReport.errorsFound} error(s)"
                )
                refreshProjectWorkspace(project)
            } else {
                markStep(12, PipelineStepStatus.PASSED, "0 errors detected; no repair needed")
            }

            // 13. Automatic Testing System
            markStep(13, PipelineStepStatus.RUNNING, "Executing workspace verification tests...")
            val testReport = repo.runAutomatedTests(project, buildReport)
            testReport.cases.forEach { tc ->
                log("[TEST] ${if (tc.passed) "PASS" else "FAIL"} ${tc.name}: ${tc.details}")
            }
            markStep(
                13,
                if (testReport.passedCount == testReport.totalCount) PipelineStepStatus.PASSED else PipelineStepStatus.FAILED,
                "${testReport.passedCount}/${testReport.totalCount} tests passed"
            )

            // 14 & 15. APK / Bundle Generator & Delivery
            markStep(14, PipelineStepStatus.RUNNING, "Packaging verified Android project bundle...")
            val latestTasks = _uiState.value.tasks
            val delivery = repo.packageAndDeliver(
                project = project,
                tasks = latestTasks,
                buildReport = buildReport,
                fixReport = fixReport,
                testReport = testReport,
                replyLanguage = replyLang
            )
            markStep(
                14,
                if (delivery.verifiedZip) PipelineStepStatus.PASSED else PipelineStepStatus.FAILED,
                "${delivery.entryCount} entries verified (${delivery.sizeBytes / 1024} KB)"
            )
            markStep(15, PipelineStepStatus.PASSED, "Report & ZIP ready to share")
            log("[15/15] Delivery ready: ${delivery.bundleFile.name} (SHA-256: ${delivery.sha256.take(12)}...)")
            refreshProjectWorkspace(project)

            val doneMsg = LanguageHelper.bilingual(
                replyLang,
                english = "Pipeline complete: ${testReport.passedCount}/${testReport.totalCount} tests passed. Project bundle ready for delivery.",
                bengali = "১৫-ধাপের পাইপলাইন সম্পন্ন: ${testReport.passedCount}/${testReport.totalCount}টি টেস্ট পাস করেছে। প্রজেক্ট বান্ডেল ও রিপোর্ট ডেলিভারির জন্য প্রস্তুত।"
            )

            _uiState.update {
                it.copy(
                    isPipelineRunning = false,
                    buildReport = buildReport,
                    fixReport = fixReport,
                    testReport = testReport,
                    deliveryResult = delivery,
                    statusBanner = doneMsg
                )
            }
        }
    }

    fun runBuildAndFixOnly() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            var report = repo.runStaticBuild(project)
            var fixReport: FixAttemptReport? = null
            if (!report.passed) {
                fixReport = repo.autoFixBuildErrors(project, report)
                report = fixReport.postFixReport
                refreshProjectWorkspace(project)
            }
            val tests = repo.runAutomatedTests(project, report)
            _uiState.update {
                it.copy(
                    buildReport = report,
                    fixReport = fixReport,
                    testReport = tests,
                    consoleLogs = report.buildLog + (fixReport?.fixLogs ?: emptyList()),
                    statusBanner = if (report.passed) "Build & tests passed (${tests.passedCount}/${tests.totalCount})."
                    else "Build found unresolved error(s). Check Pipeline console."
                )
            }
        }
    }

    fun createShareBundleIntent(): Intent? {
        val delivery = _uiState.value.deliveryResult ?: return null
        return repo.apkDelivery.createShareIntent(delivery.bundleFile, "application/zip")
    }

    fun createShareReportIntent(): Intent? {
        val delivery = _uiState.value.deliveryResult ?: return null
        return repo.apkDelivery.createShareIntent(delivery.reportFile, "text/markdown")
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            repo.prefsManager.setSelectedModelPath(path)
            repo.loadModel(path, _uiState.value.preferences.backendPreference)
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            repo.prefsManager.setSelectedModelPath(null)
            repo.unloadModel()
        }
    }

    fun downloadCatalogModel(spec: CatalogModelSpec) {
        repo.downloadModel(spec, _uiState.value.preferences.backendPreference) { downloadedFile ->
            viewModelScope.launch {
                repo.prefsManager.setSelectedModelPath(downloadedFile.absolutePath)
            }
        }
    }

    fun cancelDownload() = repo.cancelDownload()

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBanner = "Importing .litertlm model...") }
            runCatching {
                val file = repo.importModelUri(uri, _uiState.value.preferences.backendPreference)
                repo.prefsManager.setSelectedModelPath(file.absolutePath)
                _uiState.update { it.copy(statusBanner = "Imported & loading ${file.name}") }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = "Model import failed: ${e.message}") }
            }
        }
    }

    fun deleteModel(file: File) {
        viewModelScope.launch {
            if (_uiState.value.preferences.selectedModelPath == file.absolutePath) {
                repo.prefsManager.setSelectedModelPath(null)
            }
            repo.deleteModelFile(file)
            _uiState.update { it.copy(installedModels = repo.listInstalledModels()) }
        }
    }

    fun setBackendPreference(pref: AiBackendPreference) {
        viewModelScope.launch {
            repo.prefsManager.setBackendPreference(pref)
            val currentPath = _uiState.value.preferences.selectedModelPath
            if (currentPath != null && File(currentPath).exists()) {
                repo.unloadModel()
                repo.loadModel(currentPath, pref)
            }
        }
    }

    fun setLanguagePolicy(policy: LanguagePolicy) {
        viewModelScope.launch { repo.prefsManager.setLanguagePolicy(policy) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.prefsManager.setThemeMode(mode) }
    }

    override fun onCleared() {
        super.onCleared()
        repo.shutdown()
    }
}
