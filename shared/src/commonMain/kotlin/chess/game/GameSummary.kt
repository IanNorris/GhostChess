package chess.game

import chess.core.*
import chess.engine.SimpleEngine

data class GameSummary(
    val whoIsWinning: String,
    val evalDescription: String,
    val risks: List<String>,
    val suggestion: String,
    val phase: String,
    val moveNumber: Int
)

object GameSummaryGenerator {

    fun generate(board: Board, playerColor: PieceColor, engine: SimpleEngine): GameSummary {
        val eval = engine.evaluate(board)
        val playerEval = if (playerColor == PieceColor.WHITE) eval else -eval
        val moveNumber = board.fullMoveNumber
        val opponent = playerColor.opposite()

        val whoIsWinning = when {
            playerEval > 5.0 -> "You're dominating"
            playerEval > 2.0 -> "You're winning"
            playerEval > 0.5 -> "You have a slight edge"
            playerEval > -0.5 -> "Position is roughly equal"
            playerEval > -2.0 -> "Your opponent has a slight edge"
            playerEval > -5.0 -> "Your opponent is winning"
            else -> "Your opponent is dominating"
        }

        val evalDescription = when {
            playerEval > 8.0 -> "Overwhelming advantage"
            playerEval > 3.0 -> "Major piece advantage"
            playerEval > 1.5 -> "About a minor piece ahead"
            playerEval > 0.5 -> "Slightly better position"
            playerEval > -0.5 -> "Balanced game"
            playerEval > -1.5 -> "Slightly worse position"
            playerEval > -3.0 -> "About a minor piece behind"
            playerEval > -8.0 -> "Major piece deficit"
            else -> "Overwhelming disadvantage"
        }

        // Risks: pieces under attack, undefended pieces, king exposure
        val risks = mutableListOf<String>()

        if (MoveGenerator.isInCheck(board, playerColor)) {
            risks.add("Your king is in check!")
        }

        val hangingPieces = mutableListOf<String>()
        for ((sq, piece) in board.allPieces(playerColor)) {
            if (piece.type == PieceType.KING) continue
            val attacked = MoveGenerator.isSquareAttacked(board, sq, opponent)
            val defended = MoveGenerator.isSquareAttacked(board, sq, playerColor)
            if (attacked && !defended) {
                hangingPieces.add("${pieceName(piece.type)} on ${sq.toAlgebraic()}")
            }
        }
        if (hangingPieces.isNotEmpty()) {
            risks.add("Undefended: ${hangingPieces.joinToString(", ")}")
        }

        // Check for opponent threats (pieces attacking toward king area)
        val playerKingSq = board.allPieces(playerColor)
            .firstOrNull { it.second.type == PieceType.KING }?.first
        if (playerKingSq != null) {
            val canCastle = if (playerColor == PieceColor.WHITE) {
                board.castlingRights.whiteKingSide || board.castlingRights.whiteQueenSide
            } else {
                board.castlingRights.blackKingSide || board.castlingRights.blackQueenSide
            }
            val hasCastled = !canCastle && moveNumber > 1
            if (!hasCastled && moveNumber in 4..15 && canCastle) {
                risks.add("King hasn't castled yet")
            }
        }

        // Suggestion: best move in human-readable form
        val suggestion = generateSuggestion(board, playerColor, engine)

        val phase = when {
            moveNumber <= 10 -> "Opening"
            totalPieceCount(board) <= 12 -> "Endgame"
            else -> "Middlegame"
        }

        return GameSummary(
            whoIsWinning = whoIsWinning,
            evalDescription = evalDescription,
            risks = risks.take(3),
            suggestion = suggestion,
            phase = phase,
            moveNumber = moveNumber
        )
    }

    private fun generateSuggestion(board: Board, playerColor: PieceColor, engine: SimpleEngine): String {
        // Only suggest if it's the player's turn
        if (board.activeColor != playerColor) return "Waiting for opponent's move…"

        val moves = MoveGenerator.generateLegalMoves(board)
        if (moves.isEmpty()) return "No legal moves"

        // Find the best move using engine at shallow depth
        var bestMove: Move? = null
        var bestEval = Double.NEGATIVE_INFINITY

        for (move in moves) {
            val newBoard = board.makeMove(move)
            val eval = engine.evaluate(newBoard)
            // Flip sign: engine.evaluate returns positive = white advantage
            val playerEval = if (playerColor == PieceColor.WHITE) eval else -eval
            if (playerEval > bestEval) {
                bestEval = playerEval
                bestMove = move
            }
        }

        if (bestMove == null) return "No clear suggestion"

        val piece = board[bestMove.from] ?: return "No clear suggestion"
        val captured = board[bestMove.to]

        return buildString {
            if (captured != null) {
                append("Capture ${pieceName(captured.type)} with ${pieceName(piece.type)}")
                append(" (${bestMove.from.toAlgebraic()}→${bestMove.to.toAlgebraic()})")
            } else if (piece.type == PieceType.KING &&
                kotlin.math.abs(bestMove.to.file - bestMove.from.file) == 2
            ) {
                append(if (bestMove.to.file > bestMove.from.file) "Castle kingside" else "Castle queenside")
            } else {
                append("Move ${pieceName(piece.type)} to ${bestMove.to.toAlgebraic()}")
                val reason = moveReason(board, bestMove, piece, playerColor)
                if (reason.isNotEmpty()) append(" — $reason")
            }
        }
    }

    private fun moveReason(board: Board, move: Move, piece: Piece, playerColor: PieceColor): String {
        val opponent = playerColor.opposite()
        val newBoard = board.makeMove(move)

        if (MoveGenerator.isInCheck(newBoard, opponent)) return "gives check"

        // Check if piece was under attack and is now safe
        val wasAttacked = MoveGenerator.isSquareAttacked(board, move.from, opponent)
        val nowAttacked = MoveGenerator.isSquareAttacked(newBoard, move.to, opponent)
        if (wasAttacked && !nowAttacked) return "escapes attack"

        // Center control
        if (piece.type == PieceType.PAWN && move.to.file in 3..4 && move.to.rank in 3..4) {
            return "controls the center"
        }

        // Development
        if (piece.type in listOf(PieceType.KNIGHT, PieceType.BISHOP)) {
            val homeRank = if (playerColor == PieceColor.WHITE) 0 else 7
            if (move.from.rank == homeRank) return "develops a piece"
        }

        return ""
    }

    private fun pieceName(type: PieceType): String = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
    }

    private fun totalPieceCount(board: Board): Int {
        return board.allPieces(PieceColor.WHITE).count() + board.allPieces(PieceColor.BLACK).count()
    }
}
