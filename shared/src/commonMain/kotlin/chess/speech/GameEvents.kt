package chess.speech

import chess.core.*
import chess.speech.OpeningBook

/**
 * Events that trigger speech commentary.
 */
sealed class GameEvent {
    data object GameStarted : GameEvent()
    data object GameStartedAsBlack : GameEvent()
    data class PlayerMoved(val move: Move, val board: Board) : GameEvent()
    data class ComputerMoved(val move: Move, val board: Board) : GameEvent()
    data class PieceCaptured(val capturedType: PieceType, val capturerColor: PieceColor, val isPlayerCapture: Boolean) : GameEvent()
    data class Check(val checkedColor: PieceColor, val isPlayerChecked: Boolean) : GameEvent()
    data class Checkmate(val playerWon: Boolean) : GameEvent()
    data object Stalemate : GameEvent()
    data class Promotion(val pieceType: PieceType, val isPlayerPromotion: Boolean) : GameEvent()
    data class Castling(val isPlayerCastling: Boolean) : GameEvent()
    data object GhostPreviewStarted : GameEvent()
    data object GhostAccepted : GameEvent()
    data object GhostDismissed : GameEvent()
    data object MoveUndone : GameEvent()
    data class Blunder(val evalDrop: Double, val isPlayerBlunder: Boolean = true) : GameEvent()
    data class GoodMove(val evalGain: Double, val isPlayerMove: Boolean = true) : GameEvent()
    data class OpeningDetected(val openingName: String) : GameEvent()
    data class AdvantageShift(val playerLeading: Boolean, val evalDelta: Double) : GameEvent()
    data class HangingPiece(val pieceType: PieceType, val square: Square, val isPlayerPiece: Boolean) : GameEvent()
    data class UnclaimedCapture(val pieceType: PieceType, val square: Square) : GameEvent()
    data class Fork(val attackerType: PieceType, val attackerSquare: Square, val targets: List<PieceType>, val isPlayerFork: Boolean) : GameEvent()
    data class WinGuaranteed(val playerWinning: Boolean, val eval: Double) : GameEvent()
    data class IllegalMoveAttempt(val inCheck: Boolean) : GameEvent()
}

/**
 * Detects game events by comparing board state before and after a move.
 */
object GameEventDetector {

    private fun pieceValue(type: PieceType): Double = when (type) {
        PieceType.PAWN -> 1.0
        PieceType.KNIGHT -> 3.0
        PieceType.BISHOP -> 3.0
        PieceType.ROOK -> 5.0
        PieceType.QUEEN -> 9.0
        PieceType.KING -> 0.0
    }

    fun detectMoveEvents(
        move: Move,
        boardBefore: Board,
        boardAfter: Board,
        playerColor: PieceColor,
        isPlayerMove: Boolean
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val movedPiece = boardBefore[move.from]
        val movingColor = movedPiece?.color ?: playerColor

        // Capture detection
        val capturedPiece = boardBefore[move.to]
        if (capturedPiece != null) {
            events.add(
                GameEvent.PieceCaptured(
                    capturedType = capturedPiece.type,
                    capturerColor = movingColor,
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
            events.add(GameEvent.Castling(isPlayerCastling = isPlayerMove))
        }

        // Promotion
        if (move.promotion != null) {
            events.add(GameEvent.Promotion(move.promotion, isPlayerPromotion = isPlayerMove))
        }

        // Check detection
        if (MoveGenerator.isInCheck(boardAfter, boardAfter.activeColor)) {
            events.add(GameEvent.Check(boardAfter.activeColor, isPlayerChecked = !isPlayerMove))
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

        // Hanging piece detection: check if the moving side left a piece undefended and attacked
        val opponentColor = if (movingColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        var worstHanging: GameEvent.HangingPiece? = null
        var worstHangingValue = 0.0
        for ((sq, piece) in boardAfter.allPieces(movingColor)) {
            if (piece.type == PieceType.KING) continue
            val attacked = MoveGenerator.isSquareAttacked(boardAfter, sq, opponentColor)
            val defended = MoveGenerator.isSquareAttacked(boardAfter, sq, movingColor)
            if (attacked && !defended) {
                val value = pieceValue(piece.type)
                if (value > worstHangingValue) {
                    worstHangingValue = value
                    worstHanging = GameEvent.HangingPiece(
                        pieceType = piece.type,
                        square = sq,
                        isPlayerPiece = (movingColor == playerColor)
                    )
                }
            }
        }
        if (worstHanging != null) {
            events.add(worstHanging)
        }

        // Unclaimed capture detection: if the player moved but missed a hanging opponent piece
        if (isPlayerMove) {
            var bestUnclaimed: GameEvent.UnclaimedCapture? = null
            var bestUnclaimedValue = 0.0
            for ((sq, piece) in boardBefore.allPieces(opponentColor)) {
                if (piece.type == PieceType.KING) continue
                val value = pieceValue(piece.type)
                if (value < 3.0) continue
                val attackedByPlayer = MoveGenerator.isSquareAttacked(boardBefore, sq, playerColor)
                val defendedByOpponent = MoveGenerator.isSquareAttacked(boardBefore, sq, opponentColor)
                if (attackedByPlayer && !defendedByOpponent && value > bestUnclaimedValue) {
                    // Make sure the player didn't actually capture this piece
                    if (move.to != sq) {
                        bestUnclaimedValue = value
                        bestUnclaimed = GameEvent.UnclaimedCapture(pieceType = piece.type, square = sq)
                    }
                }
            }
            if (bestUnclaimed != null) {
                events.add(bestUnclaimed)
            }
        }

        // Fork detection: check if the piece that just moved now attacks 2+ valuable enemy pieces
        val landingPiece = boardAfter[move.to]
        if (landingPiece != null && landingPiece.type != PieceType.KING) {
            val enemyColor = if (movingColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
            val threatenedPieces = mutableListOf<PieceType>()
            for ((sq, piece) in boardAfter.allPieces(enemyColor)) {
                if (piece.type == PieceType.PAWN) continue
                if (MoveGenerator.isSquareAttacked(boardAfter, sq, movingColor)) {
                    // Check this specific piece is attacked by the moved piece's square
                    val defended = MoveGenerator.isSquareAttacked(boardAfter, sq, enemyColor)
                    if (!defended || pieceValue(piece.type) > pieceValue(landingPiece.type)) {
                        threatenedPieces.add(piece.type)
                    }
                }
            }
            if (threatenedPieces.size >= 2) {
                events.add(GameEvent.Fork(
                    attackerType = landingPiece.type,
                    attackerSquare = move.to,
                    targets = threatenedPieces,
                    isPlayerFork = isPlayerMove
                ))
            }
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
