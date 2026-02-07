package chess

import android.content.Context
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chess_${System.currentTimeMillis()}")
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
        speechEngine = AndroidSpeechEngine(this)
        val settingsStore = AndroidSettingsStore(this)
        val modelManager = GemmaModelManager(this)
        banterEngine = GemmaBanterEngine(modelManager)

        // Initialize if model already downloaded
        if (modelManager.status.value == ModelStatus.READY) {
            banterEngine?.initialize(this)
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
    val progress by modelManager.downloadProgress.collectAsState()
    val error by modelManager.errorMessage.collectAsState()

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
            "Uses Gemma 3 270M on-device for witty banter",
            color = ChessColors.OnSurface.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (status) {
            ModelStatus.NOT_DOWNLOADED -> {
                Button(
                    onClick = {
                        scope.launch {
                            modelManager.downloadModel()
                            if (modelManager.status.value == ModelStatus.READY) {
                                banterEngine.initialize(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary),
                    modifier = Modifier.testTag("download-ai-btn")
                ) {
                    Text("Download AI Model (~270MB)")
                }
            }
            ModelStatus.DOWNLOADING -> {
                Text("Downloading...", color = ChessColors.Accent, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("download-progress"),
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
            }
            ModelStatus.ERROR -> {
                Text("❌ ${error ?: "Download failed"}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        scope.launch {
                            modelManager.downloadModel()
                            if (modelManager.status.value == ModelStatus.READY) {
                                banterEngine.initialize(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary)
                ) {
                    Text("Retry Download")
                }
            }
        }
    }
}
