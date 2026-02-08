package chess.game

import chess.core.*
import chess.engine.SimpleEngine

data class GameSummary(
    val lesson: String,
    val evalDescription: String,
    val tips: List<String>,
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

        val lesson = when {
            playerEval > 5.0 -> "Excellent! You have a commanding lead. Focus on trading pieces to simplify — when you're ahead in material, fewer pieces on the board makes it easier to convert your advantage."
            playerEval > 2.0 -> "Well played! You're ahead. Look for safe exchanges — trading pieces of equal value keeps your advantage while reducing your opponent's counterplay."
            playerEval > 0.5 -> "You have a small edge. Keep building your position — look for ways to improve your worst-placed piece, or create weaknesses in your opponent's camp."
            playerEval > -0.5 -> "The position is balanced. This is where strategy matters most — try to control the center, develop your pieces to active squares, and keep your king safe."
            playerEval > -2.0 -> "You're slightly behind. Stay alert for tactical opportunities — sometimes a well-timed combination can turn the tables. Avoid further trades when you're down material."
            playerEval > -5.0 -> "You're at a disadvantage. Look for complications — your best chance is to create threats and put pressure on your opponent. Quiet play favours the side that's ahead."
            else -> "Tough position. Don't give up — set traps, create threats, and make your opponent work for the win. Surprising moves can lead to blunders from the other side."
        }

        val evalDescription = when {
            playerEval > 8.0 -> "Winning position"
            playerEval > 3.0 -> "Major advantage"
            playerEval > 1.5 -> "Clear advantage"
            playerEval > 0.5 -> "Slight advantage"
            playerEval > -0.5 -> "Equal position"
            playerEval > -1.5 -> "Slight disadvantage"
            playerEval > -3.0 -> "Clear disadvantage"
            playerEval > -8.0 -> "Major disadvantage"
            else -> "Losing position"
        }

        val tips = mutableListOf<String>()

        if (MoveGenerator.isInCheck(board, playerColor)) {
            tips.add("Your king is in check — you must block, capture, or move your king. Always look for a response that also improves your position.")
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
            tips.add("Hanging pieces: ${hangingPieces.joinToString(", ")}. A piece is \"hanging\" when it's attacked but not defended — either move it, defend it, or make sure you have a stronger threat elsewhere.")
        }

        // Castling advice
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
                tips.add("Consider castling soon. Castling tucks your king behind pawns for safety and connects your rooks — it's usually best to castle before move 10.")
            }
        }

        // Development advice
        val pieces = board.allPieces(playerColor)
        val homeRank = if (playerColor == PieceColor.WHITE) 0 else 7
        val undeveloped = pieces.filter {
            it.second.type in listOf(PieceType.KNIGHT, PieceType.BISHOP) &&
                it.first.rank == homeRank
        }
        if (undeveloped.size >= 2 && moveNumber < 12) {
            tips.add("You still have ${undeveloped.size} minor pieces on the back rank. In the opening, aim to develop knights and bishops to active squares before launching attacks.")
        }

        // Center control
        val centerSquares = listOf(Square(3,3), Square(3,4), Square(4,3), Square(4,4))
        val centerPawns = centerSquares.count { sq ->
            val p = board[sq]; p != null && p.type == PieceType.PAWN && p.color == playerColor
        }
        val opponentCenterPawns = centerSquares.count { sq ->
            val p = board[sq]; p != null && p.type == PieceType.PAWN && p.color == opponent
        }
        if (centerPawns == 0 && opponentCenterPawns >= 1 && moveNumber in 3..15) {
            tips.add("Your opponent controls the center with pawns. Central control gives pieces more scope — try to challenge with pawn breaks or place pieces that contest those squares.")
        }

        // Suggestion: best move in human-readable form
        val suggestion = generateSuggestion(board, playerColor, engine)

        val phase = when {
            moveNumber <= 10 -> "Opening"
            totalPieceCount(board) <= 12 -> "Endgame"
            else -> "Middlegame"
        }

        return GameSummary(
            lesson = lesson,
            evalDescription = evalDescription,
            tips = tips.take(3),
            suggestion = suggestion,
            phase = phase,
            moveNumber = moveNumber
        )
    }

    private fun generateSuggestion(board: Board, playerColor: PieceColor, engine: SimpleEngine): String {
        if (board.activeColor != playerColor) return "Watch how your opponent responds — try to predict their move and think about why they chose it."

        val moves = MoveGenerator.generateLegalMoves(board)
        if (moves.isEmpty()) return ""

        var bestMove: Move? = null
        var bestEval = Double.NEGATIVE_INFINITY

        for (move in moves) {
            val newBoard = board.makeMove(move)
            val eval = engine.evaluate(newBoard)
            val playerEval = if (playerColor == PieceColor.WHITE) eval else -eval
            if (playerEval > bestEval) {
                bestEval = playerEval
                bestMove = move
            }
        }

        if (bestMove == null) return ""

        val piece = board[bestMove.from] ?: return ""
        val captured = board[bestMove.to]

        return buildString {
            append("Try: ")
            if (captured != null) {
                append("capture the ${pieceName(captured.type)} with your ${pieceName(piece.type)}")
                append(" (${bestMove.from.toAlgebraic()}→${bestMove.to.toAlgebraic()})")
            } else if (piece.type == PieceType.KING &&
                kotlin.math.abs(bestMove.to.file - bestMove.from.file) == 2
            ) {
                append(if (bestMove.to.file > bestMove.from.file) "castle kingside" else "castle queenside")
            } else {
                append("move your ${pieceName(piece.type)} to ${bestMove.to.toAlgebraic()}")
            }
            val reason = moveReason(board, bestMove, piece, playerColor)
            if (reason.isNotEmpty()) append(" — this $reason")
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
