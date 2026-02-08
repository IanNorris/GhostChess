package chess.web

import chess.audio.AudioEngine
import chess.audio.GamePhase
import chess.audio.SoundEffect
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Audio
import org.w3c.dom.events.Event

/**
 * Web Audio implementation of AudioEngine.
 * Uses HTML5 Audio with mobile autoplay policy workaround.
 * Music uses RandomMind's Medieval CC0 tracks mapped to game phases.
 */
class BrowserAudioEngine : AudioEngine {
    override var sfxEnabled: Boolean = true
    override var musicEnabled: Boolean = true
        set(value) {
            field = value
            if (value) startMusic() else stopMusic()
        }
    override var musicVolume: Float = 0.3f
        set(value) {
            field = value
            currentMusic?.volume = value.toDouble()
        }

    private val sfxCache = mutableMapOf<SoundEffect, Audio>()
    private var currentPhase: GamePhase = GamePhase.MENU
    private var currentMusic: Audio? = null
    private var crossfadeInterval: Int? = null
    private var audioUnlocked = false

    // Medieval tracks by phase
    private val calmTracks = listOf(
        "audio/music-medieval-exploration.mp3",
        "audio/music-medieval-harvest-season.mp3",
        "audio/music-medieval-the-old-tower-inn.mp3",
        "audio/music-medieval-the-bards-tale.mp3",
        "audio/music-medieval-market-day.mp3"
    )
    private val intenseTracks = listOf(
        "audio/music-medieval-battle.mp3",
        "audio/music-medieval-minstrel-dance.mp3",
        "audio/music-medieval-kings-feast.mp3",
        "audio/music-medieval-rejoicing.mp3"
    )

    init {
        val unlock: (Event) -> Unit = { _ ->
            if (!audioUnlocked) {
                audioUnlocked = true
                SoundEffect.entries.forEach { sound ->
                    sfxCache.getOrPut(sound) { Audio(sfxPath(sound)) }
                }
            }
        }
        document.addEventListener("click", unlock)
        document.addEventListener("touchstart", unlock)
    }

    private fun sfxPath(sound: SoundEffect): String = when (sound) {
        SoundEffect.MOVE -> "audio/move.mp3"
        SoundEffect.CAPTURE -> "audio/capture.mp3"
        SoundEffect.CHECK -> "audio/check.mp3"
        SoundEffect.CHECKMATE -> "audio/checkmate.mp3"
        SoundEffect.CASTLE -> "audio/castle.mp3"
        SoundEffect.ILLEGAL -> "audio/illegal.mp3"
        SoundEffect.UNDO -> "audio/undo.mp3"
        SoundEffect.GAME_START -> "audio/game-start.mp3"
        SoundEffect.DRAW -> "audio/draw.mp3"
    }

    override fun playSound(sound: SoundEffect) {
        if (!sfxEnabled) return
        try {
            val cached = sfxCache.getOrPut(sound) { Audio(sfxPath(sound)) }
            cached.currentTime = 0.0
            cached.volume = 0.6
            cached.play()
        } catch (_: Exception) {}
    }

    override fun setMusicPhase(phase: GamePhase) {
        if (currentPhase == phase) return
        currentPhase = phase
        if (!musicEnabled) return
        transitionTo(phase)
    }

    private fun pickTrack(phase: GamePhase): String {
        val pool = when (phase) {
            GamePhase.MENU, GamePhase.OPENING -> calmTracks
            GamePhase.MIDGAME, GamePhase.ENDGAME -> intenseTracks
        }
        return pool[(js("Math.random()") as Double * pool.size).toInt().coerceIn(0, pool.size - 1)]
    }

    private fun transitionTo(phase: GamePhase) {
        crossfadeInterval?.let { window.clearInterval(it) }

        val old = currentMusic
        val track = pickTrack(phase)
        val next = Audio(track).apply {
            loop = true
            volume = 0.0
        }
        next.play()
        currentMusic = next

        // Crossfade: fade out old, fade in new over ~2 seconds
        val steps = 40
        var step = 0
        val startOldVol = old?.volume ?: 0.0

        crossfadeInterval = window.setInterval({
            step++
            val progress = step.toDouble() / steps
            next.volume = musicVolume.toDouble() * progress
            old?.let { it.volume = startOldVol * (1.0 - progress) }

            if (step >= steps) {
                crossfadeInterval?.let { window.clearInterval(it) }
                crossfadeInterval = null
                old?.pause()
            }
        }, 50)
    }

    fun startMusic() {
        if (!musicEnabled) return
        transitionTo(currentPhase)
    }

    fun stopMusic() {
        crossfadeInterval?.let { window.clearInterval(it) }
        crossfadeInterval = null
        currentMusic?.pause()
        currentMusic?.let { it.volume = 0.0 }
        currentMusic = null
    }

    override fun stopAll() {
        stopMusic()
    }
}
