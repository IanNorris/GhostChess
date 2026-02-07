package chess.speech

/**
 * Platform-agnostic text-to-speech interface.
 * Each platform provides its own implementation.
 */
interface SpeechEngine {
    /** Speak the given text. Interrupts any current speech. */
    fun speak(text: String)

    /** Stop any current speech. */
    fun stop()

    /** Whether speech is currently enabled. */
    var enabled: Boolean
}

/**
 * No-op implementation for testing and platforms without TTS.
 */
class NoOpSpeechEngine : SpeechEngine {
    override var enabled: Boolean = false
    override fun speak(text: String) {}
    override fun stop() {}
}
