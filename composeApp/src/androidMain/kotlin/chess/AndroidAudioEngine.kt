package chess

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import chess.audio.AudioEngine
import chess.audio.GamePhase
import chess.audio.SoundEffect
import chess.simulator.R

/**
 * Android implementation of AudioEngine using SoundPool for SFX and MediaPlayer for music.
 */
class AndroidAudioEngine(private val context: Context) : AudioEngine {
    override var sfxEnabled: Boolean = true
    override var musicEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                transitionTo(currentPhase)
            } else {
                stopMusic()
            }
        }
    override var musicVolume: Float = 0.3f
        set(value) {
            field = value
            currentPlayer?.setVolume(value, value)
        }

    private var currentPhase: GamePhase = GamePhase.MENU
    private var currentPlayer: MediaPlayer? = null

    // Medieval tracks by phase
    private val calmTracks = listOf(
        R.raw.music_medieval_exploration,
        R.raw.music_medieval_harvest_season,
        R.raw.music_medieval_the_old_tower_inn,
        R.raw.music_medieval_the_bards_tale,
        R.raw.music_medieval_market_day
    )
    private val intenseTracks = listOf(
        R.raw.music_medieval_battle,
        R.raw.music_medieval_minstrel_dance,
        R.raw.music_medieval_kings_feast,
        R.raw.music_medieval_rejoicing
    )

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = mutableMapOf<SoundEffect, Int>()
    private val loadedSounds = mutableSetOf<Int>()

    private val moveSoundIds = mutableListOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSounds.add(sampleId)
        }
        moveSoundIds.add(soundPool.load(context, R.raw.move_1, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_2, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_3, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_4, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_5, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_6, 1))
        moveSoundIds.add(soundPool.load(context, R.raw.move_7, 1))
        soundIds[SoundEffect.CAPTURE] = soundPool.load(context, R.raw.capture, 1)
        soundIds[SoundEffect.CHECK] = soundPool.load(context, R.raw.check, 1)
        soundIds[SoundEffect.CHECKMATE] = soundPool.load(context, R.raw.checkmate, 1)
        soundIds[SoundEffect.CASTLE] = soundPool.load(context, R.raw.castle, 1)
        soundIds[SoundEffect.ILLEGAL] = soundPool.load(context, R.raw.illegal, 1)
        soundIds[SoundEffect.UNDO] = soundPool.load(context, R.raw.undo, 1)
        soundIds[SoundEffect.GAME_START] = soundPool.load(context, R.raw.game_start, 1)
        soundIds[SoundEffect.DRAW] = soundPool.load(context, R.raw.draw, 1)
    }

    override fun playSound(sound: SoundEffect) {
        if (!sfxEnabled) return
        val id = if (sound == SoundEffect.MOVE) {
            moveSoundIds.random()
        } else {
            soundIds[sound] ?: return
        }
        if (id in loadedSounds) {
            soundPool.play(id, 0.6f, 0.6f, 1, 0, 1.0f)
        }
    }

    override fun setMusicPhase(phase: GamePhase) {
        if (currentPhase == phase) return
        currentPhase = phase
        if (!musicEnabled) return
        transitionTo(phase)
    }

    private fun pickTrack(phase: GamePhase): Int {
        val pool = when (phase) {
            GamePhase.MENU, GamePhase.OPENING -> calmTracks
            GamePhase.MIDGAME, GamePhase.ENDGAME -> intenseTracks
        }
        return pool.random()
    }

    private fun transitionTo(phase: GamePhase) {
        currentPlayer?.let {
            it.setVolume(0f, 0f)
            it.pause()
            it.release()
        }
        val resId = pickTrack(phase)
        currentPlayer = MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
            start()
        }
    }

    fun startMusic() {
        if (!musicEnabled) return
        transitionTo(currentPhase)
    }

    fun stopMusic() {
        currentPlayer?.pause()
    }

    override fun stopAll() {
        stopMusic()
    }

    fun release() {
        soundPool.release()
        currentPlayer?.release()
        currentPlayer = null
    }
}
