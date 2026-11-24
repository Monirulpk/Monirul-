package com.example.domain

import com.example.data.local.BuildTaskEntity
import java.util.UUID

class TaskPlanner {

    fun createPlanFromPdfAnalysis(
        projectId: String,
        packageName: String,
        analysis: PdfAnalysisResult,
        existingTasks: List<BuildTaskEntity>,
        symbolIndex: WorkspaceSymbolIndex
    ): List<BuildTaskEntity> {
        val pkgPath = packageName.replace('.', '/')
        val checker = DuplicateFeatureChecker()
        val tasks = mutableListOf<BuildTaskEntity>()
        var order = (existingTasks.maxOfOrNull { it.stepOrder } ?: 0) + 1

        // 1. UI Screen tasks
        analysis.detectedScreens.forEach { screenName ->
            val cleanName = toPascalCase(screenName).let {
                if (it.endsWith("Screen")) it else "${it}Screen"
            }
            val targetFile = "app/src/main/java/$pkgPath/ui/screens/$cleanName.kt"
            val dupCheck = checker.checkFeature(cleanName, targetFile, symbolIndex)
            tasks.add(
                BuildTaskEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    stepOrder = order++,
                    title = "Implement $cleanName",
                    description = "Generate Jetpack Compose Material 3 UI for $cleanName based on PDF requirements.",
                    category = "UI_SCREEN",
                    targetFile = targetFile,
                    status = "PENDING",
                    duplicateWarning = if (dupCheck.isDuplicate) dupCheck.recommendation else null
                )
            )
        }

        // 2. Functional feature / domain tasks
        analysis.detectedFeatures
            .filter { feature ->
                val norm = feature.lowercase()
                analysis.detectedScreens.none { scr -> norm.contains(scr.lowercase().removeSuffix("screen")) }
            }
            .take(8)
            .forEach { featureTitle ->
                val className = toPascalCase(featureTitle).ifBlank { "FeatureModule" }
                val targetFile = "app/src/main/java/$pkgPath/data/${className}.kt"
                val dupCheck = checker.checkFeature(className, targetFile, symbolIndex)
                tasks.add(
                    BuildTaskEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        stepOrder = order++,
                        title = featureTitle.take(60),
                        description = "Implement English-only Kotlin logic and state handling for: $featureTitle",
                        category = "FEATURE",
                        targetFile = targetFile,
                        status = "PENDING",
                        duplicateWarning = if (dupCheck.isDuplicate) dupCheck.recommendation else null
                    )
                )
            }

        // 3. Manifest permission tasks
        if (analysis.detectedPermissions.isNotEmpty()) {
            tasks.add(
                BuildTaskEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    stepOrder = order++,
                    title = "Configure AndroidManifest Permissions",
                    description = "Declare required permissions: ${analysis.detectedPermissions.joinToString(", ")}",
                    category = "MANIFEST",
                    targetFile = "app/src/main/AndroidManifest.xml",
                    status = "PENDING"
                )
            )
        }

        // 4. Feature Integration task
        tasks.add(
            BuildTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                stepOrder = order++,
                title = "Integrate Navigation & MainActivity",
                description = "Wire generated screens and features cleanly into MainActivity.kt without duplicates.",
                category = "INTEGRATION",
                targetFile = "app/src/main/java/$pkgPath/MainActivity.kt",
                status = "PENDING"
            )
        )

        return tasks
    }

    fun createPlanFromInstruction(
        projectId: String,
        packageName: String,
        instruction: String,
        existingTasks: List<BuildTaskEntity>,
        symbolIndex: WorkspaceSymbolIndex,
        language: AppLanguage
    ): List<BuildTaskEntity> {
        val pkgPath = packageName.replace('.', '/')
        val checker = DuplicateFeatureChecker()
        var order = (existingTasks.maxOfOrNull { it.stepOrder } ?: 0) + 1
        val lower = instruction.lowercase()

        val inferredScreenName = when {
            lower.contains("login") || lower.contains("auth") || lower.contains("লগইন") -> "AuthScreen"
            lower.contains("profile") || lower.contains("প্রোফাইল") -> "ProfileScreen"
            lower.contains("setting") || lower.contains("সেটিংস") -> "SettingsScreen"
            lower.contains("note") || lower.contains("নোট") -> "NotesScreen"
            lower.contains("task") || lower.contains("todo") || lower.contains("কাজ") -> "TaskBoardScreen"
            lower.contains("calc") || lower.contains("ক্যালকুলেটর") -> "CalculatorScreen"
            else -> {
                val asciiWords = instruction.replace(Regex("[^A-Za-z0-9 ]"), " ")
                    .trim()
                    .split(Regex("\\s+"))
                    .filter { it.length >= 3 }
                    .take(2)
                if (asciiWords.isNotEmpty()) {
                    toPascalCase(asciiWords.joinToString(" ")) + "Screen"
                } else {
                    "CustomFeatureScreen"
                }
            }
        }

        val screenFile = "app/src/main/java/$pkgPath/ui/screens/$inferredScreenName.kt"
        val dupCheck = checker.checkFeature(inferredScreenName, screenFile, symbolIndex, language)
        val modelName = inferredScreenName.removeSuffix("Screen") + "ViewModel"
        val vmFile = "app/src/main/java/$pkgPath/viewmodel/$modelName.kt"

        return listOf(
            BuildTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                stepOrder = order++,
                title = "Build $modelName State Holder",
                description = "Create StateFlow ViewModel for request: ${instruction.take(100)}",
                category = "FEATURE",
                targetFile = vmFile,
                status = "PENDING"
            ),
            BuildTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                stepOrder = order++,
                title = "Design & Code $inferredScreenName",
                description = "Implement Material 3 Compose screen for: ${instruction.take(100)}",
                category = "UI_SCREEN",
                targetFile = screenFile,
                status = "PENDING",
                duplicateWarning = if (dupCheck.isDuplicate) dupCheck.recommendation else null
            ),
            BuildTaskEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                stepOrder = order,
                title = "Integrate $inferredScreenName into App Navigation",
                description = "Update MainActivity.kt and strings.xml safely.",
                category = "INTEGRATION",
                targetFile = "app/src/main/java/$pkgPath/MainActivity.kt",
                status = "PENDING"
            )
        )
    }

    private fun toPascalCase(input: String): String {
        return input
            .replace(Regex("[^A-Za-z0-9 ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
