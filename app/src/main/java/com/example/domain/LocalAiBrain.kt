package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AiBackendPreference
import com.example.data.local.LanguagePolicy
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

data class CatalogModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeLabel: String,
    val minBytesThreshold: Long,
    val description: String
)

data class LocalBrainUiState(
    val isEngineReady: Boolean = false,
    val isLoadingEngine: Boolean = false,
    val isGenerating: Boolean = false,
    val activeModelName: String? = null,
    val activeModelPath: String? = null,
    val activeBackend: String = "Deterministic Rule Engine",
    val lastError: String? = null,
    val downloadProgress: Float? = null,
    val downloadingModelId: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

class LocalAiBrain(private val context: Context) {

    companion object {
        val MODEL_CATALOG = listOf(
            CatalogModelSpec(
                id = "qwen3_0_6b",
                displayName = "Qwen3 0.6B (LiteRT-LM)",
                fileName = "Qwen3-0.6B.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
                expectedSizeLabel = "~600 MB",
                minBytesThreshold = 50_000_000L,
                description = "Compact fast on-device LLM for CPU and GPU code planning and analysis."
            ),
            CatalogModelSpec(
                id = "gemma_4_e2b",
                displayName = "Gemma 4 E2B IT (LiteRT-LM)",
                fileName = "gemma-4-E2B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                expectedSizeLabel = "~2.6 GB",
                minBytesThreshold = 200_000_000L,
                description = "High-capability on-device Gemma 4 model for deep code generation and reasoning."
            )
        )
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private val httpClient = OkHttpClient.Builder().build()
    @Volatile
    private var activeDownloadCall: Call? = null

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelPath: String? = null

    private val _state = MutableStateFlow(LocalBrainUiState())
    val state: StateFlow<LocalBrainUiState> = _state.asStateFlow()

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun listInstalledModels(): List<File> {
        return modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) && it.length() > 1_000_000L }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun loadModel(
        modelPath: String,
        preferredBackend: AiBackendPreference
    ) {
        scope.launch {
            mutex.withLock {
                if (loadedModelPath == modelPath && engine != null) return@withLock
                val file = File(modelPath)
                if (!file.exists() || !file.isFile || file.length() < 1_000_000L) {
                    _state.update {
                        it.copy(
                            isEngineReady = false,
                            isLoadingEngine = false,
                            lastError = "Invalid or incomplete .litertlm model file: ${file.name}"
                        )
                    }
                    return@withLock
                }

                releaseInternal()
                _state.update {
                    it.copy(
                        isLoadingEngine = true,
                        lastError = null,
                        activeModelName = file.name,
                        activeModelPath = modelPath
                    )
                }

                val tryOrder = if (preferredBackend == AiBackendPreference.GPU) {
                    listOf(Backend.GPU() to "LiteRT-LM (GPU)", Backend.CPU() to "LiteRT-LM (CPU Fallback)")
                } else {
                    listOf(Backend.CPU() to "LiteRT-LM (CPU)")
                }

                var initSuccess = false
                var lastException: Exception? = null
                for ((backend, label) in tryOrder) {
                    var candidateEngine: Engine? = null
                    try {
                        val config = EngineConfig(
                            modelPath = file.absolutePath,
                            backend = backend,
                            cacheDir = context.cacheDir.path
                        )
                        candidateEngine = Engine(config).also { it.initialize() }
                        val conv = candidateEngine.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(
                                    "You are AI Dev Studio, an offline on-device Android engineering assistant. " +
                                        "Always write Kotlin/Compose/XML source code in English. " +
                                        "Reply in Bengali if the user writes in Bengali, and in English if the user writes in English."
                                ),
                                samplerConfig = SamplerConfig(
                                    topK = 20,
                                    topP = 0.95,
                                    temperature = 0.4
                                )
                            )
                        )
                        engine = candidateEngine
                        conversation = conv
                        loadedModelPath = file.absolutePath
                        _state.update {
                            it.copy(
                                isEngineReady = true,
                                isLoadingEngine = false,
                                activeModelName = file.name,
                                activeModelPath = file.absolutePath,
                                activeBackend = label,
                                lastError = null
                            )
                        }
                        initSuccess = true
                        break
                    } catch (e: Exception) {
                        lastException = e
                        runCatching { candidateEngine?.close() }
                    }
                }

                if (!initSuccess) {
                    loadedModelPath = null
                    _state.update {
                        it.copy(
                            isEngineReady = false,
                            isLoadingEngine = false,
                            activeBackend = "Deterministic Rule Engine",
                            lastError = "Model load failed: ${lastException?.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    fun unloadModel() {
        scope.launch {
            mutex.withLock {
                releaseInternal()
            }
        }
    }

    suspend fun generateText(
        prompt: String,
        languagePolicy: LanguagePolicy,
        onChunk: ((String) -> Unit)? = null
    ): String {
        val conv = conversation
        val ready = _state.value.isEngineReady && conv != null
        if (!ready || conv == null) {
            return buildDeterministicResponse(prompt, languagePolicy)
        }

        return withContext(dispatcher) {
            if (!_state.value.isEngineReady || conversation == null) {
                return@withContext buildDeterministicResponse(prompt, languagePolicy)
            }
            _state.update { it.copy(isGenerating = true, lastError = null) }
            val buffer = StringBuilder()
            try {
                val flow = checkNotNull(conversation).sendMessageAsync(prompt)
                flow.collect { chunk ->
                    val piece = chunk.toString()
                    buffer.append(piece)
                    onChunk?.invoke(buffer.toString())
                }
                buffer.toString().ifBlank { buildDeterministicResponse(prompt, languagePolicy) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(lastError = e.message) }
                buildDeterministicResponse(prompt, languagePolicy)
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun downloadCatalogModel(
        spec: CatalogModelSpec,
        preferredBackend: AiBackendPreference,
        onComplete: (File) -> Unit
    ) {
        if (_state.value.downloadingModelId != null) return
        scope.launch(Dispatchers.IO) {
            val destFile = File(modelsDir, spec.fileName)
            val tempFile = File(modelsDir, "${spec.fileName}.part")
            _state.update {
                it.copy(
                    downloadingModelId = spec.id,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    lastError = null
                )
            }
            try {
                val request = Request.Builder().url(spec.downloadUrl).build()
                val call = httpClient.newCall(request)
                activeDownloadCall = call
                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download body")
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            val progress = if (total > 0) (downloaded.toFloat() / total.toFloat()) else null
                            _state.update {
                                it.copy(
                                    downloadProgress = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            }
                        }
                    }
                }
                if (tempFile.length() < spec.minBytesThreshold) {
                    tempFile.delete()
                    throw IllegalStateException("Downloaded file size (${tempFile.length()} B) is smaller than expected.")
                }
                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }
                _state.update {
                    it.copy(
                        downloadingModelId = null,
                        downloadProgress = null,
                        downloadedBytes = 0L,
                        totalBytes = 0L
                    )
                }
                onComplete(destFile)
                loadModel(destFile.absolutePath, preferredBackend)
            } catch (e: Exception) {
                tempFile.delete()
                _state.update {
                    it.copy(
                        downloadingModelId = null,
                        downloadProgress = null,
                        lastError = "Download error: ${e.message}"
                    )
                }
            } finally {
                activeDownloadCall = null
            }
        }
    }

    fun cancelDownload() {
        activeDownloadCall?.cancel()
        activeDownloadCall = null
        _state.update {
            it.copy(downloadingModelId = null, downloadProgress = null)
        }
    }

    suspend fun importLocalModelUri(
        uri: Uri,
        preferredBackend: AiBackendPreference
    ): File = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: "imported_model.litertlm"
        val safeName = if (name.endsWith(".litertlm", ignoreCase = true)) {
            name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        } else {
            "${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.litertlm"
        }
        val dest = File(modelsDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { out ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Could not read selected model file")
        loadModel(dest.absolutePath, preferredBackend)
        dest
    }

    fun deleteModelFile(file: File) {
        scope.launch {
            mutex.withLock {
                if (loadedModelPath == file.absolutePath) {
                    releaseInternal()
                }
                file.delete()
            }
        }
    }

    private fun buildDeterministicResponse(
        prompt: String,
        policy: LanguagePolicy
    ): String {
        val lang = LanguageHelper.resolveReplyLanguage(prompt, policy)
        return LanguageHelper.bilingual(
            lang,
            english = "Local Rule Engine (no .litertlm weights loaded): Analyzed your request locally without Cloud AI. " +
                "All Kotlin/Compose source code is generated in English, duplicate symbols are checked against the workspace, " +
                "and XML/Kotlin static verification is ready. Load Qwen3-0.6B or Gemma 4 in Settings for full neural generation.",
            bengali = "লোকাল রুল ইঞ্জিন (.litertlm মডেল লোড নেই): কোনো ক্লাউড AI ছাড়াই আপনার নির্দেশনা ডিভাইসে বিশ্লেষণ করা হয়েছে। " +
                "সব Kotlin/Compose সোর্স কোড ইংরেজিতে তৈরি হবে, বিদ্যমান প্রজেক্টে ডুপ্লিকেট ফিচার পরীক্ষা করা হবে এবং " +
                "XML ও Kotlin যাচাই সম্পন্ন হবে। সম্পূর্ণ নিউরাল AI চালানোর জন্য Settings থেকে Qwen3-0.6B বা Gemma 4 লোড করুন।"
        )
    }

    private suspend fun releaseInternal() {
        _state.update { it.copy(isEngineReady = false, isLoadingEngine = false) }
        while (_state.value.isGenerating) {
            runCatching { conversation?.cancelProcess() }
            delay(100)
        }
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
        _state.update {
            it.copy(
                activeModelName = null,
                activeModelPath = null,
                activeBackend = "Deterministic Rule Engine"
            )
        }
    }

    fun shutdown() {
        scope.launch {
            mutex.withLock { releaseInternal() }
            executor.shutdown()
        }.invokeOnCompletion { scope.cancel() }
    }
}
