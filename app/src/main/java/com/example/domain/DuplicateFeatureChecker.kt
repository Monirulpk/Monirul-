package com.example.domain

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WorkspaceSymbolIndex(
    val composables: Set<String>,
    val classesAndObjects: Set<String>,
    val permissions: Set<String>,
    val stringKeys: Set<String>,
    val fileNames: Set<String>
)

data class DuplicateCheckResult(
    val proposedFeature: String,
    val isDuplicate: Boolean,
    val matchedSymbols: List<String>,
    val recommendation: String
)

class DuplicateFeatureChecker {

    private val composableRegex = Regex("@Composable\\s+(?:private\\s+|internal\\s+)?fun\\s+([A-Za-z0-9_]+)")
    private val typeRegex = Regex("(?:class|interface|object|enum class|data class)\\s+([A-Za-z0-9_]+)")
    private val permissionRegex = Regex("android:name=\"(android\\.permission\\.[A-Z0-9_]+)\"")
    private val stringKeyRegex = Regex("<string\\s+name=\"([a-zA-Z0-9_]+)\"")

    suspend fun indexWorkspace(projectRoot: File): WorkspaceSymbolIndex =
        withContext(Dispatchers.IO) {
            val composables = linkedSetOf<String>()
            val types = linkedSetOf<String>()
            val permissions = linkedSetOf<String>()
            val stringKeys = linkedSetOf<String>()
            val fileNames = linkedSetOf<String>()

            if (!projectRoot.exists()) {
                return@withContext WorkspaceSymbolIndex(composables, types, permissions, stringKeys, fileNames)
            }

            projectRoot.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    fileNames.add(file.name)
                    when (file.extension.lowercase()) {
                        "kt" -> {
                            val text = runCatching { file.readText() }.getOrDefault("")
                            composableRegex.findAll(text).forEach { composables.add(it.groupValues[1]) }
                            typeRegex.findAll(text).forEach { types.add(it.groupValues[1]) }
                        }
                        "xml" -> {
                            val text = runCatching { file.readText() }.getOrDefault("")
                            permissionRegex.findAll(text).forEach { permissions.add(it.groupValues[1]) }
                            stringKeyRegex.findAll(text).forEach { stringKeys.add(it.groupValues[1]) }
                        }
                    }
                }

            WorkspaceSymbolIndex(
                composables = composables,
                classesAndObjects = types,
                permissions = permissions,
                stringKeys = stringKeys,
                fileNames = fileNames
            )
        }

    fun checkFeature(
        proposedName: String,
        targetFile: String,
        index: WorkspaceSymbolIndex,
        language: AppLanguage = AppLanguage.ENGLISH
    ): DuplicateCheckResult {
        val normalizedProposed = normalizeSymbol(proposedName)
        val candidateFileName = File(targetFile).name
        val matches = linkedSetOf<String>()

        (index.composables + index.classesAndObjects).forEach { existing ->
            val normExisting = normalizeSymbol(existing)
            if (normExisting == normalizedProposed ||
                (normalizedProposed.length >= 5 && normExisting.contains(normalizedProposed)) ||
                (normExisting.length >= 5 && normalizedProposed.contains(normExisting))
            ) {
                matches.add(existing)
            }
        }

        if (candidateFileName.isNotBlank() &&
            candidateFileName != "MainActivity.kt" &&
            candidateFileName != "AndroidManifest.xml" &&
            candidateFileName != "strings.xml" &&
            index.fileNames.any { it.equals(candidateFileName, ignoreCase = true) }
        ) {
            matches.add("File:$candidateFileName")
        }

        val isDup = matches.isNotEmpty()
        val rec = if (isDup) {
            LanguageHelper.bilingual(
                language,
                english = "Potential duplicate detected (${matches.joinToString(", ")}). Update existing module instead of creating a duplicate.",
                bengali = "সম্ভাব্য ডুপ্লিকেট পাওয়া গেছে (${matches.joinToString(", ")})। নতুন ফাইল না বানিয়ে বিদ্যমান কোড আপডেট করা হবে।"
            )
        } else {
            LanguageHelper.bilingual(
                language,
                english = "No duplicate found. Safe to implement as a new module.",
                bengali = "কোনো ডুপ্লিকেট পাওয়া যায়নি। নতুন ফিচার হিসেবে নিরাপদে যুক্ত করা যাবে।"
            )
        }

        return DuplicateCheckResult(
            proposedFeature = proposedName,
            isDuplicate = isDup,
            matchedSymbols = matches.toList(),
            recommendation = rec
        )
    }

    private fun normalizeSymbol(raw: String): String {
        return raw.lowercase()
            .replace("screen", "")
            .replace("system", "")
            .replace("manager", "")
            .replace("feature", "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
