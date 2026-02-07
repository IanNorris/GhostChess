package chess

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import chess.speech.SpeechEngine
import chess.ui.game.App
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

class MainActivity : ComponentActivity() {
    private var speechEngine: AndroidSpeechEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechEngine = AndroidSpeechEngine(this)
        setContent {
            App(speechEngine = speechEngine!!)
        }
    }

    override fun onDestroy() {
        speechEngine?.shutdown()
        super.onDestroy()
    }
}
