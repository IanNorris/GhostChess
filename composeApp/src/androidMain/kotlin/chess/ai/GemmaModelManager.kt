package chess.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}

/**
 * Manages the Gemma 3 270M model lifecycle: download, storage, status.
 */
class GemmaModelManager(private val context: Context) {

    companion object {
        private const val MODEL_FILENAME = "gemma3-270m.task"
        private const val MODEL_URL = "https://huggingface.co/google/gemma-3-270m-it-int4-mediapipe/resolve/main/gemma3-270m-it-int4.task"
    }

    private val _status = MutableStateFlow(ModelStatus.NOT_DOWNLOADED)
    val status: StateFlow<ModelStatus> = _status

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val modelPath: String
        get() = File(context.filesDir, MODEL_FILENAME).absolutePath

    init {
        if (File(context.filesDir, MODEL_FILENAME).exists()) {
            _status.value = ModelStatus.READY
        }
    }

    suspend fun downloadModel() {
        if (_status.value == ModelStatus.DOWNLOADING) return
        _status.value = ModelStatus.DOWNLOADING
        _downloadProgress.value = 0f
        _errorMessage.value = null

        withContext(Dispatchers.IO) {
            try {
                val destFile = File(context.filesDir, MODEL_FILENAME)
                val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")

                val connection = URL(MODEL_URL).openConnection()
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.getInputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                _downloadProgress.value = downloadedBytes.toFloat() / totalBytes
                            }
                        }
                    }
                }

                tempFile.renameTo(destFile)
                _status.value = ModelStatus.READY
                _downloadProgress.value = 1f
            } catch (e: Exception) {
                _status.value = ModelStatus.ERROR
                _errorMessage.value = e.message ?: "Download failed"
                // Clean up partial download
                File(context.filesDir, "$MODEL_FILENAME.tmp").delete()
            }
        }
    }

    fun deleteModel() {
        File(context.filesDir, MODEL_FILENAME).delete()
        File(context.filesDir, "$MODEL_FILENAME.tmp").delete()
        _status.value = ModelStatus.NOT_DOWNLOADED
        _downloadProgress.value = 0f
    }
}
