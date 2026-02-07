package chess

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import chess.ai.GemmaBanterEngine
import chess.ai.GemmaModelManager
import chess.ai.ModelStatus
import chess.core.PieceColor
import chess.game.Difficulty
import chess.game.GameMode
import chess.speech.SpeechEngine
import chess.ui.game.App
import chess.ui.game.SettingsStore
import chess.ui.theme.ChessColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class AndroidSpeechEngine(private val activity: ComponentActivity) : SpeechEngine {
    override var enabled: Boolean = false
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ready = true
            }
        }
    }

    override fun speak(text: String) {
        if (!enabled || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "chess_${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}

class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("ghostchess_settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

class MainActivity : ComponentActivity() {
    private var speechEngine: AndroidSpeechEngine? = null
    private var banterEngine: GemmaBanterEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash handler — writes crash log to internal storage
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = java.io.File(filesDir, "crash_log.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())
                logFile.appendText("=== CRASH $timestamp ===\n")
                logFile.appendText("Thread: ${thread.name}\n")
                logFile.appendText(throwable.stackTraceToString())
                logFile.appendText("\n\n")
                android.util.Log.e("GhostChess", "UNCAUGHT EXCEPTION", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        speechEngine = AndroidSpeechEngine(this)
        val settingsStore = AndroidSettingsStore(this)
        val modelManager = GemmaModelManager(this)
        banterEngine = GemmaBanterEngine(modelManager)

        // Initialize LLM off the main thread to avoid blocking UI
        if (modelManager.status.value == ModelStatus.READY) {
            CoroutineScope(Dispatchers.IO).launch {
                banterEngine?.initialize(this@MainActivity)
            }
        }

        setContent {
            App(
                speechEngine = speechEngine!!,
                settingsStore = settingsStore,
                banterGenerator = banterEngine,
                banterSettingsContent = {
                    GemmaBanterSettings(modelManager, banterEngine!!, this@MainActivity)
                }
            )
        }
    }

    override fun onDestroy() {
        speechEngine?.shutdown()
        banterEngine?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun GemmaBanterSettings(
    modelManager: GemmaModelManager,
    banterEngine: GemmaBanterEngine,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val status by modelManager.status.collectAsState()
    val progress by modelManager.extractProgress.collectAsState()
    val error by modelManager.errorMessage.collectAsState()

    // Auto-extract model from bundled assets on first launch
    LaunchedEffect(Unit) {
        if (status == ModelStatus.NOT_DOWNLOADED) {
            modelManager.extractModel()
        }
        // initialize() is idempotent — skips if already created by onCreate
        if (modelManager.status.value == ModelStatus.READY) {
            banterEngine.initialize(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChessColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("ai-banter-settings"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤖 AI Commentary", color = ChessColors.OnSurface, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Uses Gemma 3 1B on-device for chess advice",
            color = ChessColors.OnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (status) {
            ModelStatus.NOT_DOWNLOADED -> {
                Button(
                    onClick = {
                        scope.launch {
                            modelManager.extractModel()
                            if (modelManager.status.value == ModelStatus.READY) {
                                banterEngine.initialize(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary),
                    modifier = Modifier.testTag("setup-ai-btn")
                ) {
                    Text("Set Up AI Model")
                }
            }
            ModelStatus.EXTRACTING -> {
                Text("Setting up AI model...", color = ChessColors.Accent, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("extract-progress"),
                    color = ChessColors.Primary
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    color = ChessColors.OnSurface,
                    fontSize = 12.sp
                )
            }
            ModelStatus.READY -> {
                Text("✅ AI Model Ready", color = ChessColors.Primary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        banterEngine.shutdown()
                        modelManager.deleteModel()
                    },
                    modifier = Modifier.testTag("delete-ai-btn")
                ) {
                    Text("Delete Model", color = ChessColors.OnSurface, fontSize = 12.sp)
                }

                // Diagnostics
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // Merge logs and share via Android share sheet
                            val merged = java.io.File(context.filesDir, "merged_log.txt")
                            val sb = StringBuilder()
                            val crashLog = java.io.File(context.filesDir, "crash_log.txt")
                            val gemmaLog = java.io.File(context.filesDir, "gemma_log.txt")
                            if (crashLog.exists()) {
                                sb.appendLine("=== CRASH LOG ===")
                                sb.appendLine(crashLog.readText())
                            }
                            if (gemmaLog.exists()) {
                                sb.appendLine("=== GEMMA LOG ===")
                                sb.appendLine(gemmaLog.readText())
                            }
                            if (sb.isEmpty()) sb.appendLine("No logs found")
                            merged.writeText(sb.toString())
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sb.toString().takeLast(4000))
                                putExtra(Intent.EXTRA_SUBJECT, "GhostChess Logs")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Logs"))
                        }
                    ) {
                        Text("📤 Share Logs", color = ChessColors.OnSurface, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            java.io.File(context.filesDir, "crash_log.txt").delete()
                            java.io.File(context.filesDir, "gemma_log.txt").delete()
                            java.io.File(context.filesDir, "merged_log.txt").delete()
                        }
                    ) {
                        Text("🗑 Clear Logs", color = ChessColors.OnSurface, fontSize = 12.sp)
                    }
                }
            }
            ModelStatus.ERROR -> {
                Text(
                    "❌ ${error ?: "Setup failed"}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            modelManager.extractModel()
                            if (modelManager.status.value == ModelStatus.READY) {
                                banterEngine.initialize(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary)
                ) {
                    Text("Retry Setup")
                }
            }
        }
    }
}
