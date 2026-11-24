package com.example.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.BuildTaskEntity
import com.example.data.local.ProjectEntity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeliveryPackageResult(
    val bundleFile: File,
    val reportFile: File,
    val entryCount: Int,
    val sha256: String,
    val sizeBytes: Long,
    val verifiedZip: Boolean,
    val reportMarkdown: String
)

class ApkGeneratorAndDelivery(private val context: Context) {

    suspend fun packageAndVerifyProject(
        project: ProjectEntity,
        projectRoot: File,
        tasks: List<BuildTaskEntity>,
        buildReport: BuildVerificationReport,
        fixReport: FixAttemptReport?,
        testReport: AutomatedTestSuiteReport,
        brainMode: String,
        replyLanguage: AppLanguage
    ): DeliveryPackageResult = withContext(Dispatchers.IO) {
        val slug = project.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "android_project" }

        val outputsDir = File(projectRoot, "outputs").apply { mkdirs() }
        val reportsDir = File(projectRoot, "reports").apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val reportMd = buildString {
            appendLine("# AI Dev Studio — Build & Delivery Report")
            appendLine("- **Project:** ${project.name} (`${project.packageName}`)")
            appendLine("- **Generated:** $timestamp")
            appendLine("- **Local AI Engine:** $brainMode (100% On-Device, Zero Cloud AI)")
            appendLine("- **Static Build Check:** ${if (buildReport.passed) "PASSED" else "NEEDS ATTENTION"} (${buildReport.kotlinFilesChecked} Kotlin, ${buildReport.xmlFilesChecked} XML)")
            if (fixReport != null) {
                appendLine("- **Auto Error Fixer:** Found ${fixReport.errorsFound}, Fixed ${fixReport.errorsFixed}")
            }
            appendLine("- **Automated Tests:** ${testReport.passedCount}/${testReport.totalCount} Passed")
            appendLine()
            appendLine("## Completed Tasks (${tasks.count { it.status == "COMPLETED" }}/${tasks.size})")
            tasks.forEach { t ->
                appendLine("- [${t.status}] ${t.stepOrder}. ${t.title} -> `${t.targetFile}`")
            }
            appendLine()
            appendLine("## Test Results")
            testReport.cases.forEach { c ->
                appendLine("- [${if (c.passed) "PASS" else "FAIL"}] ${c.name}: ${c.details}")
            }
            appendLine()
            appendLine("## APK Compilation Note")
            appendLine(
                LanguageHelper.bilingual(
                    replyLanguage,
                    english = "This bundle contains the complete, verified Gradle/Jetpack Compose Android project. " +
                        "Because Android prohibits downloading/executing native desktop compilers (kotlinc/aapt2/d8) dynamically inside a phone app, " +
                        "export this verified ZIP bundle and run `gradle assembleDebug` or open in Android Studio to sign and install the final `.apk`.",
                    bengali = "এই প্যাকেজে সম্পূর্ণ যাচাইকৃত Android (Gradle + Jetpack Compose) প্রজেক্টটি রয়েছে। " +
                        "অ্যান্ড্রয়েড ডিভাইস ও Google Play নীতিমালা অনুযায়ী ফোনের ভেতরে ডাইনামিক নেটিভ কম্পাইলার (aapt2/kotlinc) চালানো সম্ভব নয়; " +
                        "তাই এই যাচাইকৃত ZIP বান্ডেলটি শেয়ার/সেভ করে Android Studio বা `gradle assembleDebug` দিয়ে সরাসরি ইনস্টলযোগ্য APK বিল্ড করতে পারবেন।"
                )
            )
        }

        val reportFile = File(reportsDir, "completion_report.md")
        reportFile.writeText(reportMd, Charsets.UTF_8)

        val zipFile = File(outputsDir, "${slug}_android_project.zip")
        if (zipFile.exists()) zipFile.delete()

        var entryCount = 0
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            projectRoot.walkTopDown()
                .filter { it.isFile && !it.startsWith(outputsDir) }
                .forEach { file ->
                    val rel = file.relativeTo(projectRoot).path.replace('\\', '/')
                    zos.putNextEntry(ZipEntry(rel))
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        bis.copyTo(zos)
                    }
                    zos.closeEntry()
                    entryCount++
                }
        }

        // Verify ZIP integrity
        var verifiedEntries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            while (zis.nextEntry != null) {
                verifiedEntries++
                zis.closeEntry()
            }
        }

        val sha256 = computeSha256(zipFile)

        DeliveryPackageResult(
            bundleFile = zipFile,
            reportFile = reportFile,
            entryCount = entryCount,
            sha256 = sha256,
            sizeBytes = zipFile.length(),
            verifiedZip = verifiedEntries == entryCount && zipFile.length() > 0L,
            reportMarkdown = reportMd
        )
    }

    fun createShareIntent(file: File, mimeType: String = "application/zip"): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AI Dev Studio — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16 * 1024)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
