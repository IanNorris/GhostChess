package chess.audio

import chess.core.Board
import chess.core.PieceColor
import chess.core.PieceType

/**
 * Detects the current game phase based on board state.
 */
object GamePhaseDetector {

    /**
     * Determine game phase from move count and piece material.
     * - Opening: first 10 full moves (20 half-moves)
     * - Endgame: few pieces remaining (≤ 12 total, or both sides have no queens)
     * - Midgame: everything else
     */
    fun detect(board: Board, moveCount: Int): GamePhase {
        if (moveCount < 20) return GamePhase.OPENING

        val whitePieces = board.allPieces(PieceColor.WHITE)
        val blackPieces = board.allPieces(PieceColor.BLACK)
        val totalPieces = whitePieces.size + blackPieces.size

        val whiteHasQueen = whitePieces.any { it.second.type == PieceType.QUEEN }
        val blackHasQueen = blackPieces.any { it.second.type == PieceType.QUEEN }

        if (totalPieces <= 12 || (!whiteHasQueen && !blackHasQueen)) {
            return GamePhase.ENDGAME
        }

        return GamePhase.MIDGAME
    }
}
