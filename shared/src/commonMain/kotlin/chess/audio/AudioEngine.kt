package chess.audio

/**
 * Platform-agnostic audio engine interface.
 * Each platform provides its own implementation.
 */
interface AudioEngine {
    /** Play a sound effect. */
    fun playSound(sound: SoundEffect)

    /** Start or transition background music for the given game phase. */
    fun setMusicPhase(phase: GamePhase)

    /** Stop all audio (SFX and music). */
    fun stopAll()

    /** Whether sound effects are enabled. */
    var sfxEnabled: Boolean

    /** Whether background music is enabled. */
    var musicEnabled: Boolean

    /** Music volume (0.0 to 1.0). */
    var musicVolume: Float
}

/**
 * No-op implementation for testing and platforms without audio.
 */
class NoOpAudioEngine : AudioEngine {
    override var sfxEnabled: Boolean = false
    override var musicEnabled: Boolean = false
    override var musicVolume: Float = 0.3f
    override fun playSound(sound: SoundEffect) {}
    override fun setMusicPhase(phase: GamePhase) {}
    override fun stopAll() {}
}
