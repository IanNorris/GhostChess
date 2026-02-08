package chess

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import chess.core.PieceColor
import chess.game.Difficulty
import chess.game.GameMode
import chess.speech.SpeechEngine
import chess.ui.game.App
import chess.ui.game.SettingsStore
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
    private var audioEngine: AndroidAudioEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechEngine = AndroidSpeechEngine(this)
        audioEngine = AndroidAudioEngine(this)
        val settingsStore = AndroidSettingsStore(this)
        setContent {
            App(speechEngine = speechEngine!!, settingsStore = settingsStore, audioEngine = audioEngine!!)
        }
    }

    override fun onDestroy() {
        speechEngine?.shutdown()
        audioEngine?.release()
        super.onDestroy()
    }
}
