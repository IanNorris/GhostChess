package chess.speech

import chess.core.*

/**
 * Tracks captured pieces and material balance throughout the game.
 */
class CapturedPiecesTracker {

    data class MaterialState(
        val whiteCaptured: List<PieceType>,  // Pieces white has captured (black pieces taken)
        val blackCaptured: List<PieceType>,  // Pieces black has captured (white pieces taken)
        val whiteValue: Int,
        val blackValue: Int,
        val advantage: Int  // Positive = white advantage, negative = black
    )

    private val capturedByWhite = mutableListOf<PieceType>()  // Black pieces taken by white
    private val capturedByBlack = mutableListOf<PieceType>()  // White pieces taken by black

    fun reset() {
        capturedByWhite.clear()
        capturedByBlack.clear()
    }

    fun onCapture(capturedPiece: PieceType, capturedBy: PieceColor) {
        if (capturedBy == PieceColor.WHITE) {
            capturedByWhite.add(capturedPiece)
        } else {
            capturedByBlack.add(capturedPiece)
        }
    }

    fun undoCapture(capturedPiece: PieceType, capturedBy: PieceColor) {
        if (capturedBy == PieceColor.WHITE) {
            val idx = capturedByWhite.lastIndexOf(capturedPiece)
            if (idx >= 0) capturedByWhite.removeAt(idx)
        } else {
            val idx = capturedByBlack.lastIndexOf(capturedPiece)
            if (idx >= 0) capturedByBlack.removeAt(idx)
        }
    }

    fun getState(): MaterialState {
        val whiteVal = capturedByWhite.sumOf { pieceValue(it) }
        val blackVal = capturedByBlack.sumOf { pieceValue(it) }
        return MaterialState(
            whiteCaptured = capturedByWhite.sortedByDescending { pieceValue(it) },
            blackCaptured = capturedByBlack.sortedByDescending { pieceValue(it) },
            whiteValue = whiteVal,
            blackValue = blackVal,
            advantage = whiteVal - blackVal
        )
    }

    companion object {
        fun pieceValue(type: PieceType): Int = when (type) {
            PieceType.PAWN -> 1
            PieceType.KNIGHT -> 3
            PieceType.BISHOP -> 3
            PieceType.ROOK -> 5
            PieceType.QUEEN -> 9
            PieceType.KING -> 0
        }

        fun pieceUnicode(type: PieceType, color: PieceColor): String = when (color) {
            PieceColor.WHITE -> when (type) {
                PieceType.PAWN -> "♙"
                PieceType.KNIGHT -> "♘"
                PieceType.BISHOP -> "♗"
                PieceType.ROOK -> "♖"
                PieceType.QUEEN -> "♕"
                PieceType.KING -> "♔"
            }
            PieceColor.BLACK -> when (type) {
                PieceType.PAWN -> "♟"
                PieceType.KNIGHT -> "♞"
                PieceType.BISHOP -> "♝"
                PieceType.ROOK -> "♜"
                PieceType.QUEEN -> "♛"
                PieceType.KING -> "♚"
            }
        }
    }
}
