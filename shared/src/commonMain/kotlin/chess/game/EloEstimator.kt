package chess.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates player ELO based on game results against difficulty levels.
 * Uses standard ELO calculation with K-factor adjustment.
 */
object EloEstimator {

    const val DEFAULT_ELO = 800
    private const val K_FACTOR = 32

    /** Approximate engine ELO for each difficulty level */
    fun engineElo(difficulty: Difficulty): Int = when (difficulty.level) {
        1 -> 400
        2 -> 550
        3 -> 700
        4 -> 850
        5 -> 1000
        6 -> 1150
        7 -> 1300
        8 -> 1450
        9 -> 1600
        10 -> 1750
        11 -> 1900
        12 -> 2050
        else -> 1150
    }

    /** Skill level label for an ELO rating */
    fun skillLabel(elo: Int): String = when {
        elo < 600 -> "Beginner"
        elo < 800 -> "Novice"
        elo < 1000 -> "Casual"
        elo < 1200 -> "Intermediate"
        elo < 1400 -> "Skilled"
        elo < 1600 -> "Advanced"
        elo < 1800 -> "Expert"
        elo < 2000 -> "Master"
        else -> "Grandmaster"
    }

    /**
     * Calculate new ELO after a game result.
     * @param playerElo current player ELO
     * @param opponentElo engine ELO for the difficulty played
     * @param result 1.0 for win, 0.5 for draw, 0.0 for loss
     * @return new player ELO
     */
    fun calculateNewElo(playerElo: Int, opponentElo: Int, result: Double): Int {
        val expected = 1.0 / (1.0 + 10.0.pow((opponentElo - playerElo) / 400.0))
        val newElo = playerElo + (K_FACTOR * (result - expected)).roundToInt()
        return maxOf(100, newElo) // floor at 100
    }

    /** Suggest a difficulty level based on current ELO */
    fun suggestedDifficulty(elo: Int): Difficulty {
        val level = Difficulty.entries.minByOrNull {
            kotlin.math.abs(engineElo(it) - elo)
        } ?: Difficulty.LEVEL_6
        return level
    }
}
