package com.example.domain

import com.example.data.local.BuildTaskEntity
import com.example.data.local.LanguagePolicy
import java.io.File

data class CodingExecutionResult(
    val filesModified: List<String>,
    val summary: String,
    val usedNeuralModel: Boolean
)

class AutomaticCodingAndIntegration(
    private val fileManager: ProjectFileManager,
    private val localAiBrain: LocalAiBrain
) {

    suspend fun executeTask(
        projectRoot: File,
        packageName: String,
        task: BuildTaskEntity,
        languagePolicy: LanguagePolicy
    ): CodingExecutionResult {
        val modified = mutableListOf<String>()
        var usedNeural = false

        when (task.category) {
            "UI_SCREEN" -> {
                val screenName = File(task.targetFile).nameWithoutExtension
                val code = if (localAiBrain.state.value.isEngineReady) {
                    val prompt = buildString {
                        appendLine("Write a valid Jetpack Compose Material 3 Kotlin file.")
                        appendLine("Package: $packageName.ui.screens")
                        appendLine("Composable name: $screenName")
                        appendLine("Requirement: ${task.description}")
                        appendLine("Rules: English source code only. Output ONLY valid Kotlin code.")
                    }
                    val aiOut = localAiBrain.generateText(prompt, languagePolicy)
                    val extracted = extractKotlinCodeBlock(aiOut)
                    if (isAcceptableKotlinScreen(extracted, packageName, screenName)) {
                        usedNeural = true
                        extracted
                    } else {
                        generateComposeScreenTemplate(packageName, screenName, task.title, task.description)
                    }
                } else {
                    generateComposeScreenTemplate(packageName, screenName, task.title, task.description)
                }
                fileManager.writeFileSafe(projectRoot, task.targetFile, code)
                modified.add(task.targetFile)
            }

            "FEATURE" -> {
                val className = File(task.targetFile).nameWithoutExtension
                val isVm = className.endsWith("ViewModel")
                val code = if (isVm) {
                    generateViewModelTemplate(packageName, className, task.description)
                } else {
                    generateFeatureModuleTemplate(packageName, className, task.title, task.description)
                }
                fileManager.writeFileSafe(projectRoot, task.targetFile, code)
                modified.add(task.targetFile)
            }

            "MANIFEST" -> {
                val manifestPath = "app/src/main/AndroidManifest.xml"
                val existing = runCatching {
                    fileManager.readFileSafe(projectRoot, manifestPath)
                }.getOrDefault("")
                if (existing.isNotBlank()) {
                    val perms = Regex("android\\.permission\\.[A-Z0-9_]+")
                        .findAll(task.description)
                        .map { it.value }
                        .distinct()
                        .toList()
                    var updated = existing
                    perms.forEach { perm ->
                        if (!updated.contains(perm)) {
                            updated = updated.replace(
                                "<application",
                                "    <uses-permission android:name=\"$perm\" />\n\n    <application"
                            )
                        }
                    }
                    if (updated != existing) {
                        fileManager.writeFileSafe(projectRoot, manifestPath, updated)
                        modified.add(manifestPath)
                    }
                }
            }

            "INTEGRATION" -> {
                integrateAllScreensIntoMainActivity(projectRoot, packageName)?.let {
                    modified.add(it)
                }
            }
        }

        return CodingExecutionResult(
            filesModified = modified,
            summary = "Executed ${task.title}: ${modified.size} file(s) updated.",
            usedNeuralModel = usedNeural
        )
    }

    suspend fun integrateAllScreensIntoMainActivity(
        projectRoot: File,
        packageName: String
    ): String? {
        val pkgPath = packageName.replace('.', '/')
        val screensDir = File(projectRoot, "app/src/main/java/$pkgPath/ui/screens")
        val screenNames = screensDir.listFiles()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

        if (screenNames.isEmpty()) return null

        val imports = screenNames.joinToString("\n") { "import $packageName.ui.screens.$it" }
        val tabsList = screenNames.joinToString(", ") { "\"${it.removeSuffix("Screen")}\"" }
        val whenBranches = screenNames.mapIndexed { idx, name ->
            "                    $idx -> $name()"
        }.joinToString("\n")

        val mainActivityContent = """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.ExperimentalMaterial3Api
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.PrimaryScrollableTabRow
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Tab
            import androidx.compose.material3.Text
            import androidx.compose.material3.TopAppBar
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Modifier
            import $packageName.ui.theme.AppTheme
            $imports

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        AppTheme {
                            AppRootNavigation()
                        }
                    }
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun AppRootNavigation() {
                val destinations = listOf($tabsList)
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = destinations.getOrElse(selectedTab) { "App" }) }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (destinations.size > 1) {
                            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                                destinations.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title) }
                                    )
                                }
                            }
                        }
                        when (selectedTab) {
            $whenBranches
                            else -> Text(
                                text = "Select a destination",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        """.trimIndent() + "\n"

        val mainPath = "app/src/main/java/$pkgPath/MainActivity.kt"
        fileManager.writeFileSafe(projectRoot, mainPath, mainActivityContent)
        return mainPath
    }

    private fun extractKotlinCodeBlock(raw: String): String {
        val fenceRegex = Regex("```(?:kotlin)?\\s*([\\s\\S]*?)```")
        return fenceRegex.find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
    }

    private fun isAcceptableKotlinScreen(
        code: String,
        packageName: String,
        screenName: String
    ): Boolean {
        if (!code.contains("package $packageName")) return false
        if (!code.contains("fun $screenName")) return false
        val openBraces = code.count { it == '{' }
        val closeBraces = code.count { it == '}' }
        return openBraces > 0 && openBraces == closeBraces
    }

    private fun generateComposeScreenTemplate(
        packageName: String,
        screenName: String,
        title: String,
        description: String
    ): String {
        val safeTitle = title.replace("\"", "'")
        val safeDesc = description.replace("\"", "'").replace("\n", " ")
        return """
            package $packageName.ui.screens

            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.fillMaxWidth
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Button
            import androidx.compose.material3.Card
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.platform.testTag
            import androidx.compose.ui.unit.dp

            @Composable
            fun $screenName(modifier: Modifier = Modifier) {
                var actionCount by remember { mutableIntStateOf(0) }

                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$safeTitle",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$safeDesc",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Interactions recorded: ${'$'}actionCount",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Button(
                        onClick = { actionCount++ },
                        modifier = Modifier.testTag("${screenName.lowercase()}_action_button")
                    ) {
                        Text("Run Feature Action")
                    }
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateViewModelTemplate(
        packageName: String,
        className: String,
        description: String
    ): String {
        val safeDesc = description.replace("\"", "'").replace("\n", " ")
        return """
            package $packageName.viewmodel

            import androidx.lifecycle.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow
            import kotlinx.coroutines.flow.asStateFlow
            import kotlinx.coroutines.flow.update

            data class ${className}State(
                val isReady: Boolean = true,
                val summary: String = "$safeDesc",
                val executionCount: Int = 0
            )

            class $className : ViewModel() {
                private val _uiState = MutableStateFlow(${className}State())
                val uiState: StateFlow<${className}State> = _uiState.asStateFlow()

                fun triggerAction() {
                    _uiState.update { it.copy(executionCount = it.executionCount + 1) }
                }
            }
        """.trimIndent() + "\n"
    }

    private fun generateFeatureModuleTemplate(
        packageName: String,
        className: String,
        title: String,
        description: String
    ): String {
        val safeTitle = title.replace("\"", "'")
        val safeDesc = description.replace("\"", "'").replace("\n", " ")
        return """
            package $packageName.data

            data class ${className}Result(
                val moduleName: String,
                val success: Boolean,
                val details: String
            )

            class $className {
                fun execute(): ${className}Result {
                    return ${className}Result(
                        moduleName = "$safeTitle",
                        success = true,
                        details = "$safeDesc"
                    )
                }
            }
        """.trimIndent() + "\n"
    }
}
