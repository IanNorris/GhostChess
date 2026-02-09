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
    private var selectedVoiceName: String? = null
    private var voiceMap: Map<String, android.speech.tts.Voice> = emptyMap()

    init {
        tts = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
                buildVoiceMap()
            }
        }
    }

    private fun buildVoiceMap() {
        val deviceLocale = Locale.getDefault()
        val localVoices = tts?.voices?.filter { voice ->
            !voice.isNetworkConnectionRequired &&
            voice.locale.language == deviceLocale.language
        } ?: emptyList()

        voiceMap = localVoices.associate { voice ->
            friendlyName(voice) to voice
        }
    }

    private fun friendlyName(voice: android.speech.tts.Voice): String {
        val locale = voice.locale
        val country = locale.displayCountry.ifEmpty { locale.country }
        val quality = when {
            voice.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> "Very High"
            voice.quality >= android.speech.tts.Voice.QUALITY_HIGH -> "High"
            voice.quality >= android.speech.tts.Voice.QUALITY_NORMAL -> "Normal"
            else -> "Low"
        }
        // Extract a short label from the voice name (last segment is often the variant)
        val nameParts = voice.name.split("-", "_")
        val variant = nameParts.lastOrNull()?.takeIf { it.length > 1 && !it.startsWith("x") }
            ?: nameParts.getOrNull(nameParts.size - 2) ?: ""
        val label = variant.replaceFirstChar { it.uppercase() }
        return if (country.isNotEmpty()) "$label ($country, $quality)" else "$label ($quality)"
    }

    override fun speak(text: String) {
        if (!enabled || !ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "chess_${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun getVoices(): List<String> {
        if (!ready) return emptyList()
        return voiceMap.keys.sorted()
    }

    override fun getSelectedVoice(): String? = selectedVoiceName

    override fun setVoice(name: String?) {
        selectedVoiceName = name
        if (!ready) return
        if (name == null) {
            tts?.language = Locale.getDefault()
        } else {
            val voice = voiceMap[name]
            if (voice != null) tts?.voice = voice
        }
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

    override fun onStop() {
        audioEngine?.stopAll()
        super.onStop()
    }

    override fun onDestroy() {
        speechEngine?.shutdown()
        audioEngine?.release()
        super.onDestroy()
    }
}
