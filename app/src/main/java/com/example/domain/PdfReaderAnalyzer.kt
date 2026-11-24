package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class PdfImportResult(
    val id: String,
    val fileName: String,
    val localFile: File,
    val sizeBytes: Long,
    val pageCount: Int,
    val extractedText: String,
    val detectedLanguage: AppLanguage,
    val usedOcrFallback: Boolean,
    val analysis: PdfAnalysisResult
)

data class PdfAnalysisResult(
    val projectPurpose: String,
    val detectedScreens: List<String>,
    val detectedFeatures: List<String>,
    val detectedPermissions: List<String>,
    val requirementLines: List<String>,
    val summaryText: String
)

class PdfReaderAnalyzer(private val context: Context) {

    @Volatile
    private var pdfBoxInitialized = false

    private fun ensurePdfBoxInitialized() {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    suspend fun importAndAnalyzePdf(
        uri: Uri,
        projectRoot: File
    ): PdfImportResult = withContext(Dispatchers.IO) {
        ensurePdfBoxInitialized()
        val displayName = queryDisplayName(uri) ?: "document_${System.currentTimeMillis()}.pdf"
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val pdfDir = File(projectRoot, "pdfs").apply { mkdirs() }
        val destFile = File(pdfDir, safeName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected PDF stream")

        val extractedPages = mutableListOf<String>()
        var pageCount = 0
        var usedOcrFallback = false

        PDDocument.load(destFile).use { document ->
            pageCount = document.numberOfPages
            val stripper = PDFTextStripper()
            for (pageIndex in 1..pageCount) {
                stripper.startPage = pageIndex
                stripper.endPage = pageIndex
                val pageText = stripper.getText(document)?.trim().orEmpty()
                if (cleanReadableChars(pageText) >= 20) {
                    extractedPages.add("--- Page $pageIndex ---\n$pageText")
                } else {
                    // Fallback to on-device ML Kit OCR on rendered bitmap for scanned/custom-font pages
                    val bitmap = renderPageBitmap(destFile, pageIndex - 1, maxWidth = 1400)
                    val ocrText = if (bitmap != null) {
                        val recognized = recognizeTextFromBitmap(bitmap)
                        bitmap.recycle()
                        recognized
                    } else ""
                    if (ocrText.isNotBlank()) {
                        usedOcrFallback = true
                        extractedPages.add("--- Page $pageIndex (OCR) ---\n$ocrText")
                    } else if (pageText.isNotBlank()) {
                        extractedPages.add("--- Page $pageIndex ---\n$pageText")
                    }
                }
            }
        }

        val combinedText = extractedPages.joinToString("\n\n").ifBlank {
            "No selectable or OCR text found in $safeName ($pageCount pages)."
        }
        val detectedLang = LanguageHelper.detectLanguage(combinedText)
        val analysis = analyzeExtractedText(combinedText, safeName, pageCount, detectedLang)

        PdfImportResult(
            id = UUID.randomUUID().toString(),
            fileName = safeName,
            localFile = destFile,
            sizeBytes = destFile.length(),
            pageCount = pageCount,
            extractedText = combinedText,
            detectedLanguage = detectedLang,
            usedOcrFallback = usedOcrFallback,
            analysis = analysis
        )
    }

    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        maxWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@use null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = maxWidth.toFloat() / page.width.coerceAtLeast(1)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    fun analyzeExtractedText(
        rawText: String,
        fileName: String,
        pageCount: Int,
        language: AppLanguage
    ): PdfAnalysisResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 4 && !it.startsWith("--- Page") }

        val lower = rawText.lowercase()
        val screens = linkedSetOf<String>()
        val screenKeywords = mapOf(
            "home" to "HomeScreen",
            "dashboard" to "DashboardScreen",
            "project" to "ProjectScreen",
            "upload" to "PdfUploadScreen",
            "pdf" to "PdfReaderScreen",
            "progress" to "ProgressScreen",
            "setting" to "SettingsScreen",
            "login" to "AuthScreen",
            "profile" to "ProfileScreen",
            "detail" to "DetailScreen",
            "chat" to "AssistantScreen",
            "হোম" to "HomeScreen",
            "প্রজেক্ট" to "ProjectScreen",
            "আপলোড" to "PdfUploadScreen",
            "প্রগ্রেস" to "ProgressScreen",
            "সেটিংস" to "SettingsScreen"
        )
        screenKeywords.forEach { (kw, screenName) ->
            if (lower.contains(kw)) screens.add(screenName)
        }
        if (screens.isEmpty()) screens.add("MainFeatureScreen")

        val features = linkedSetOf<String>()
        val numberedRowRegex = Regex("^(\\d{1,2})[.)\\s]+([A-Za-z][A-Za-z0-9 &/_-]{3,55})")
        for (line in lines) {
            val match = numberedRowRegex.find(line)
            if (match != null) {
                val candidate = match.groupValues[2].trim()
                if (candidate.length >= 4) features.add(candidate)
            }
        }
        if (features.isEmpty()) {
            lines.take(8).forEach { line ->
                features.add(line.take(64))
            }
        }

        val permissions = linkedSetOf<String>()
        if (lower.contains("camera") || lower.contains("ক্যামেরা")) {
            permissions.add("android.permission.CAMERA")
        }
        if (lower.contains("location") || lower.contains("gps") || lower.contains("লোকেশন")) {
            permissions.add("android.permission.ACCESS_COARSE_LOCATION")
        }
        if (lower.contains("notification") || lower.contains("নোটিফিকেশন")) {
            permissions.add("android.permission.POST_NOTIFICATIONS")
        }
        if (lower.contains("audio") || lower.contains("microphone") || lower.contains("voice")) {
            permissions.add("android.permission.RECORD_AUDIO")
        }

        val purpose = lines.firstOrNull { !it.startsWith("No.") && !it.startsWith("ক্রম") }
            ?.take(120)
            ?: "Android application specification from $fileName"

        val summary = LanguageHelper.bilingual(
            language,
            english = "Analyzed $fileName ($pageCount pages): detected ${screens.size} UI screens, ${features.size} functional tasks, and ${permissions.size} hardware permissions.",
            bengali = "$fileName ($pageCount পৃষ্ঠা) বিশ্লেষণ সম্পন্ন: ${screens.size}টি UI স্ক্রিন, ${features.size}টি কাজের ধাপ এবং ${permissions.size}টি পারমিশন শনাক্ত হয়েছে।"
        )

        return PdfAnalysisResult(
            projectPurpose = purpose,
            detectedScreens = screens.toList(),
            detectedFeatures = features.toList().take(18),
            detectedPermissions = permissions.toList(),
            requirementLines = lines.take(40),
            summaryText = summary
        )
    }

    private fun cleanReadableChars(text: String): Int {
        return text.count { it.isLetterOrDigit() && it != '\uFFFD' }
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    if (cont.isActive) cont.resume(result.text.trim())
                }
                .addOnFailureListener {
                    recognizer.close()
                    if (cont.isActive) cont.resume("")
                }
        }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
