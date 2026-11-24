package com.example.domain

import java.io.File
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class VerificationIssue(
    val filePath: String,
    val line: Int,
    val severity: String, // "ERROR" or "WARNING"
    val message: String,
    val autoFixable: Boolean
)

data class BuildVerificationReport(
    val passed: Boolean,
    val issues: List<VerificationIssue>,
    val kotlinFilesChecked: Int,
    val xmlFilesChecked: Int,
    val buildLog: List<String>
)

data class FixAttemptReport(
    val errorsFound: Int,
    val errorsFixed: Int,
    val fixLogs: List<String>,
    val postFixReport: BuildVerificationReport
)

data class AutomatedTestCaseResult(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class AutomatedTestSuiteReport(
    val passedCount: Int,
    val totalCount: Int,
    val cases: List<AutomatedTestCaseResult>
)

class AutomaticBuildAndFixSystem {

    suspend fun runBuildVerification(projectRoot: File): BuildVerificationReport =
        withContext(Dispatchers.IO) {
            val issues = mutableListOf<VerificationIssue>()
            val logs = mutableListOf<String>()
            var ktCount = 0
            var xmlCount = 0

            logs.add("[BUILD] Scanning workspace: ${projectRoot.name}")

            if (!File(projectRoot, "settings.gradle.kts").exists()) {
                issues.add(VerificationIssue("settings.gradle.kts", 1, "ERROR", "Missing settings.gradle.kts", true))
            }
            if (!File(projectRoot, "app/build.gradle.kts").exists()) {
                issues.add(VerificationIssue("app/build.gradle.kts", 1, "ERROR", "Missing app/build.gradle.kts", false))
            }

            // 1. Validate XML files using XmlPullParser
            val xmlFiles = projectRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                .toList()

            xmlFiles.forEach { file ->
                xmlCount++
                val rel = file.relativeTo(projectRoot).path.replace('\\', '/')
                val xmlError = validateXmlWellFormed(file.readText())
                if (xmlError != null) {
                    issues.add(VerificationIssue(rel, xmlError.first, "ERROR", "XML error: ${xmlError.second}", false))
                }
                if (file.name == "AndroidManifest.xml") {
                    val manifestText = file.readText()
                    if (!manifestText.contains("<application")) {
                        issues.add(VerificationIssue(rel, 1, "ERROR", "AndroidManifest missing <application> tag", false))
                    }
                    if (!manifestText.contains("android.intent.action.MAIN")) {
                        issues.add(VerificationIssue(rel, 1, "WARNING", "No MAIN launcher activity declared", false))
                    }
                }
            }
            logs.add("[XML] Parsed $xmlCount XML file(s)")

            // 2. Validate Kotlin files (lexical braces, parens, strings, package header, English policy)
            val ktFiles = projectRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                .toList()

            val declaredSymbols = mutableMapOf<String, String>()
            ktFiles.forEach { file ->
                ktCount++
                val rel = file.relativeTo(projectRoot).path.replace('\\', '/')
                val content = file.readText()
                val lines = content.lines()

                if (lines.none { it.trim().startsWith("package ") }) {
                    issues.add(VerificationIssue(rel, 1, "ERROR", "Missing package declaration", true))
                }

                // Brace & paren balance ignoring string literals and comments
                val balanceIssue = checkKotlinDelimiters(content)
                if (balanceIssue != null) {
                    issues.add(
                        VerificationIssue(
                            rel,
                            balanceIssue.first,
                            "ERROR",
                            balanceIssue.second,
                            autoFixable = balanceIssue.second.contains("Missing closing '}'")
                        )
                    )
                }

                // English-only code rule check
                if (Regex("[\\u0980-\\u09FF]").containsMatchIn(content)) {
                    issues.add(
                        VerificationIssue(
                            rel,
                            1,
                            "ERROR",
                            "Non-English (Bengali) characters found inside Kotlin source file",
                            autoFixable = false
                        )
                    )
                }

                // Duplicate top-level Composable / class check
                Regex("(?:@Composable\\s+fun|class|object|interface)\\s+([A-Za-z0-9_]+)")
                    .findAll(content)
                    .forEach { match ->
                        val sym = match.groupValues[1]
                        val prev = declaredSymbols[sym]
                        if (prev != null && prev != rel) {
                            issues.add(
                                VerificationIssue(
                                    rel,
                                    1,
                                    "WARNING",
                                    "Duplicate symbol '$sym' also declared in $prev",
                                    autoFixable = false
                                )
                            )
                        } else {
                            declaredSymbols[sym] = rel
                        }
                    }
            }
            logs.add("[KOTLIN] Checked $ktCount Kotlin file(s)")

            val errorCount = issues.count { it.severity == "ERROR" }
            val warnCount = issues.count { it.severity == "WARNING" }
            logs.add("[RESULT] Errors: $errorCount, Warnings: $warnCount")

            BuildVerificationReport(
                passed = errorCount == 0,
                issues = issues,
                kotlinFilesChecked = ktCount,
                xmlFilesChecked = xmlCount,
                buildLog = logs
            )
        }

    suspend fun autoFixAndRetry(
        projectRoot: File,
        packageName: String,
        initialReport: BuildVerificationReport
    ): FixAttemptReport = withContext(Dispatchers.IO) {
        val fixLogs = mutableListOf<String>()
        val initialErrors = initialReport.issues.count { it.severity == "ERROR" }
        var fixedCount = 0

        initialReport.issues.filter { it.severity == "ERROR" }.forEach { issue ->
            val target = File(projectRoot, issue.filePath)
            if (issue.filePath == "settings.gradle.kts" && !target.exists()) {
                target.writeText("rootProject.name = \"${projectRoot.name}\"\ninclude(\":app\")\n")
                fixedCount++
                fixLogs.add("[FIX] Recreated missing settings.gradle.kts")
            } else if (target.exists() && issue.message == "Missing package declaration") {
                val original = target.readText()
                target.writeText("package $packageName\n\n$original")
                fixedCount++
                fixLogs.add("[FIX] Added missing package header to ${issue.filePath}")
            } else if (target.exists() && issue.message.startsWith("Missing closing '}'")) {
                val original = target.readText().trimEnd()
                val diff = countUnclosedBraces(original)
                if (diff > 0) {
                    target.writeText(original + "\n" + "}".repeat(diff) + "\n")
                    fixedCount++
                    fixLogs.add("[FIX] Appended $diff missing closing brace(s) to ${issue.filePath}")
                }
            } else {
                fixLogs.add("[UNRESOLVED] Manual review needed at ${issue.filePath}:${issue.line} -> ${issue.message}")
            }
        }

        val retryReport = runBuildVerification(projectRoot)
        FixAttemptReport(
            errorsFound = initialErrors,
            errorsFixed = fixedCount,
            fixLogs = fixLogs,
            postFixReport = retryReport
        )
    }

    suspend fun runAutomatedTests(
        projectRoot: File,
        buildReport: BuildVerificationReport
    ): AutomatedTestSuiteReport = withContext(Dispatchers.IO) {
        val cases = mutableListOf<AutomatedTestCaseResult>()

        // Test 1: Required Gradle & Manifest files exist
        val hasCoreFiles = listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml"
        ).all { File(projectRoot, it).exists() }
        cases.add(
            AutomatedTestCaseResult(
                name = "ProjectStructureTest",
                passed = hasCoreFiles,
                details = if (hasCoreFiles) "All Gradle & Manifest descriptors present." else "Missing core Gradle or Manifest files."
            )
        )

        // Test 2: Static Build Verification Clean
        cases.add(
            AutomatedTestCaseResult(
                name = "StaticCompileCheckTest",
                passed = buildReport.passed,
                details = if (buildReport.passed) "0 static errors across ${buildReport.kotlinFilesChecked} Kotlin & ${buildReport.xmlFilesChecked} XML files."
                else "${buildReport.issues.count { it.severity == "ERROR" }} error(s) remain."
            )
        )

        // Test 3: English Source Code Policy
        val ktFiles = projectRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val allEnglish = ktFiles.none { Regex("[\\u0980-\\u09FF]").containsMatchIn(it.readText()) }
        cases.add(
            AutomatedTestCaseResult(
                name = "EnglishSourcePolicyTest",
                passed = allEnglish,
                details = if (allEnglish) "All ${ktFiles.size} Kotlin files strictly use English source code." else "Non-English text detected in Kotlin code."
            )
        )

        // Test 4: No Duplicate Top-Level Symbols
        val noDupWarnings = buildReport.issues.none { it.message.startsWith("Duplicate symbol") }
        cases.add(
            AutomatedTestCaseResult(
                name = "DuplicateFeatureGuardTest",
                passed = noDupWarnings,
                details = if (noDupWarnings) "No duplicate Composable or class declarations found." else "Duplicate symbols detected."
            )
        )

        // Test 5: Launcher Activity & Theme Presence
        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml")
        val hasLauncher = manifest.exists() && manifest.readText().contains("android.intent.action.LAUNCHER")
        cases.add(
            AutomatedTestCaseResult(
                name = "ManifestLauncherTest",
                passed = hasLauncher,
                details = if (hasLauncher) "Launcher intent-filter verified in AndroidManifest.xml." else "Launcher intent-filter missing."
            )
        )

        val passed = cases.count { it.passed }
        AutomatedTestSuiteReport(
            passedCount = passed,
            totalCount = cases.size,
            cases = cases
        )
    }

    private fun validateXmlWellFormed(xmlContent: String): Pair<Int, String>? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                // parse entire document
            }
            null
        } catch (e: Exception) {
            val line = (e as? org.xmlpull.v1.XmlPullParserException)?.lineNumber ?: 1
            line to (e.message ?: "Malformed XML")
        }
    }

    private fun checkKotlinDelimiters(source: String): Pair<Int, String>? {
        var curly = 0
        var paren = 0
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        var escaped = false
        var line = 1

        var i = 0
        while (i < source.length) {
            val ch = source[i]
            val next = source.getOrNull(i + 1)

            if (ch == '\n') {
                line++
                inLineComment = false
                i++
                continue
            }

            if (inLineComment) {
                i++
                continue
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }

            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            if (ch == '/' && next == '/') {
                inLineComment = true
                i += 2
                continue
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true
                i += 2
                continue
            }
            if (ch == '"') {
                inString = true
                i++
                continue
            }

            when (ch) {
                '{' -> curly++
                '}' -> {
                    curly--
                    if (curly < 0) return line to "Unexpected closing '}'"
                }
                '(' -> paren++
                ')' -> {
                    paren--
                    if (paren < 0) return line to "Unexpected closing ')'"
                }
            }
            i++
        }

        if (inString) return line to "Unclosed string literal"
        if (curly > 0) return line to "Missing closing '}' ($curly unclosed)"
        if (paren > 0) return line to "Missing closing ')' ($paren unclosed)"
        return null
    }

    private fun countUnclosedBraces(source: String): Int {
        var curly = 0
        var inString = false
        var escaped = false
        for (ch in source) {
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> curly++
                '}' -> curly--
            }
        }
        return curly.coerceAtLeast(0)
    }
}
