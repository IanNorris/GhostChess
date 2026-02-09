package chess.game

import chess.core.Board
import chess.engine.ChessEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tracks player move quality and adjusts difficulty dynamically.
 * Uses engine evaluation to compare player's chosen move against the best move.
 */
class DynamicDifficultyManager(
    private val engine: ChessEngine,
    startingDifficulty: Difficulty = Difficulty.LEVEL_6
) {
    var currentLevel: Difficulty = startingDifficulty
        private set

    // Rolling window of recent move quality scores (0.0 = blunder, 1.0 = best move)
    private val moveQualities = mutableListOf<Double>()
    private val windowSize = 6
    private var lastMoveWasUndone = false

    /** Mark that the last player move was undone — it won't count */
    fun markUndone() {
        if (moveQualities.isNotEmpty()) {
            moveQualities.removeAt(moveQualities.lastIndex)
            lastMoveWasUndone = true
        }
    }

    /**
     * Evaluate a player's move quality by comparing it to the engine's best.
     * Call this after the player makes a move.
     * @param boardBeforeMove the board state before the player moved
     * @param playerMoveFen FEN after the player's move
     */
    suspend fun recordPlayerMove(boardBeforeMove: Board, playerMoveFen: String) {
        lastMoveWasUndone = false
        val depth = 3 // quick eval for comparison
        try {
            val bestAnalysis = withContext(Dispatchers.Default) {
                engine.getBestLine(boardBeforeMove.toFen(), depth)
            }
            val bestEval = bestAnalysis.evaluation

            // Evaluate the position after the player's actual move
            val playerAnalysis = withContext(Dispatchers.Default) {
                engine.getBestLine(playerMoveFen, depth)
            }
            // Note: eval is from the perspective of the side to move AFTER the player's move,
            // so we negate it to get the eval from the player's perspective
            val playerEval = -playerAnalysis.evaluation

            // Quality: how close was the player's move to the best?
            // Difference in centipawns (eval is in pawns)
            val evalLoss = bestEval - playerEval
            val quality = when {
                evalLoss <= 0.1 -> 1.0   // Excellent — within 0.1 pawn
                evalLoss <= 0.5 -> 0.8   // Good
                evalLoss <= 1.0 -> 0.6   // Decent
                evalLoss <= 2.0 -> 0.3   // Inaccuracy
                else -> 0.0              // Blunder
            }

            moveQualities.add(quality)
            if (moveQualities.size > windowSize) {
                moveQualities.removeAt(0)
            }

            adjustDifficulty()
        } catch (_: Exception) {
            // Don't crash if eval fails
        }
    }

    private fun adjustDifficulty() {
        if (moveQualities.size < 3) return // Need a few moves before adjusting

        val avgQuality = moveQualities.average()
        val currentLevel = this.currentLevel.level

        val newLevel = when {
            avgQuality >= 0.75 && currentLevel < 12 -> currentLevel + 1
            avgQuality <= 0.35 && currentLevel > 1 -> currentLevel - 1
            else -> currentLevel
        }

        this.currentLevel = Difficulty.fromLevel(newLevel)
    }

    /** Get a summary of recent move quality for display */
    fun averageQuality(): Double = if (moveQualities.isEmpty()) 0.5 else moveQualities.average()
}
