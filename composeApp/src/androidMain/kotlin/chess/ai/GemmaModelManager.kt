package chess.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelStatus {
    NOT_DOWNLOADED,
    EXTRACTING,
    READY,
    ERROR
}

/**
 * Manages the Gemma 3 1B model lifecycle.
 * The model is bundled as an APK asset and extracted to internal storage on first use.
 */
class GemmaModelManager(private val context: Context) {

    companion object {
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
    }

    private val _status = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val status: StateFlow<ModelStatus> = _status

    private val _extractProgress = MutableStateFlow(0f)
    val extractProgress: StateFlow<Float> = _extractProgress

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val modelPath: String
        get() = File(context.filesDir, MODEL_FILENAME).absolutePath

    init {
        if (File(context.filesDir, MODEL_FILENAME).exists()) {
            _status.value = ModelStatus.READY
        }
    }

    /** Extracts the bundled model from APK assets to internal storage. */
    suspend fun extractModel() {
        if (_status.value == ModelStatus.EXTRACTING) return

        val destFile = File(context.filesDir, MODEL_FILENAME)
        if (destFile.exists()) {
            _status.value = ModelStatus.READY
            _extractProgress.value = 1f
            return
        }

        _status.value = ModelStatus.EXTRACTING
        _extractProgress.value = 0f
        _errorMessage.value = null

        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")

                // Use openFd to get exact size (requires noCompress in build.gradle)
                val totalBytes = try {
                    val fd = context.assets.openFd(MODEL_FILENAME)
                    val len = fd.length
                    fd.close()
                    len
                } catch (_: Exception) { -1L }

                var copiedBytes = 0L
                context.assets.open(MODEL_FILENAME).use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            copiedBytes += bytesRead
                            if (totalBytes > 0) {
                                _extractProgress.value = copiedBytes.toFloat() / totalBytes
                            }
                        }
                    }
                }

                tempFile.renameTo(destFile)
                _status.value = ModelStatus.READY
                _extractProgress.value = 1f
            } catch (e: Exception) {
                _status.value = ModelStatus.ERROR
                _errorMessage.value = "Failed to extract model: ${e.message?.take(100) ?: "Unknown error"}"
                File(context.filesDir, "$MODEL_FILENAME.tmp").delete()
            }
        }
    }

    fun deleteModel() {
        File(context.filesDir, MODEL_FILENAME).delete()
        File(context.filesDir, "$MODEL_FILENAME.tmp").delete()
        _status.value = ModelStatus.NOT_DOWNLOADED
        _extractProgress.value = 0f
    }
}
