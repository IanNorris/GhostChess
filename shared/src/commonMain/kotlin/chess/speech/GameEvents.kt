package chess.speech

import chess.core.*

/**
 * Events that trigger speech commentary.
 */
sealed class GameEvent {
    data object GameStarted : GameEvent()
    data object GameStartedAsBlack : GameEvent()
    data class PlayerMoved(val move: Move, val board: Board) : GameEvent()
    data class ComputerMoved(val move: Move, val board: Board) : GameEvent()
    data class PieceCaptured(val capturedType: PieceType, val capturerColor: PieceColor, val isPlayerCapture: Boolean) : GameEvent()
    data class Check(val checkedColor: PieceColor) : GameEvent()
    data class Checkmate(val playerWon: Boolean) : GameEvent()
    data object Stalemate : GameEvent()
    data class Promotion(val pieceType: PieceType) : GameEvent()
    data object Castling : GameEvent()
    data object GhostPreviewStarted : GameEvent()
    data object GhostAccepted : GameEvent()
    data object GhostDismissed : GameEvent()
    data object MoveUndone : GameEvent()
    data class Blunder(val evalDrop: Double) : GameEvent()
    data class GoodMove(val evalGain: Double) : GameEvent()
}

/**
 * Detects game events by comparing board state before and after a move.
 */
object GameEventDetector {

    fun detectMoveEvents(
        move: Move,
        boardBefore: Board,
        boardAfter: Board,
        playerColor: PieceColor,
        isPlayerMove: Boolean
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val movedPiece = boardBefore[move.from]

        // Capture detection
        val capturedPiece = boardBefore[move.to]
        if (capturedPiece != null) {
            events.add(
                GameEvent.PieceCaptured(
                    capturedType = capturedPiece.type,
                    capturerColor = movedPiece?.color ?: playerColor,
                    isPlayerCapture = isPlayerMove
                )
            )
        }

        // En passant capture
        if (movedPiece?.type == PieceType.PAWN && move.from.file != move.to.file && capturedPiece == null) {
            events.add(
                GameEvent.PieceCaptured(
                    capturedType = PieceType.PAWN,
                    capturerColor = movedPiece.color,
                    isPlayerCapture = isPlayerMove
                )
            )
        }

        // Castling detection
        if (movedPiece?.type == PieceType.KING && kotlin.math.abs(move.from.file - move.to.file) == 2) {
            events.add(GameEvent.Castling)
        }

        // Promotion
        if (move.promotion != null) {
            events.add(GameEvent.Promotion(move.promotion))
        }

        // Check detection
        if (MoveGenerator.isInCheck(boardAfter, boardAfter.activeColor)) {
            events.add(GameEvent.Check(boardAfter.activeColor))
        }

        // Checkmate detection
        if (MoveGenerator.isCheckmate(boardAfter)) {
            val playerWon = boardAfter.activeColor != playerColor
            events.add(GameEvent.Checkmate(playerWon))
        }

        // Stalemate detection
        if (MoveGenerator.isDraw(boardAfter)) {
            events.add(GameEvent.Stalemate)
        }

        // Report the move itself
        if (isPlayerMove) {
            events.add(GameEvent.PlayerMoved(move, boardAfter))
        } else {
            events.add(GameEvent.ComputerMoved(move, boardAfter))
        }

        return events
    }
}
