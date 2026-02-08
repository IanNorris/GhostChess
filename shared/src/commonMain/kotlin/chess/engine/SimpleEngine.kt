package chess.engine

import chess.core.*

class SimpleEngine : ChessEngine {

    private var initialized = false

    override suspend fun initialize() {
        initialized = true
    }

    override fun isReady(): Boolean = initialized

    override fun shutdown() {
        initialized = false
    }

    override suspend fun getBestLine(fen: String, depth: Int): EngineAnalysis {
        require(initialized) { "Engine not initialized" }
        val board = Board.fromFen(fen)
        val line = mutableListOf<Move>()
        var currentBoard = board

        // Find the best move using alpha-beta search
        val moves = MoveGenerator.generateLegalMoves(currentBoard)
        if (moves.isEmpty()) {
            return EngineAnalysis(emptyList(), evaluate(currentBoard), depth, "No moves available.")
        }

        val isMaximizing = currentBoard.activeColor == PieceColor.WHITE
        var bestMove: Move? = null
        var bestEval = if (isMaximizing) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY

        // Order moves for better pruning: captures first, then others
        val orderedMoves = orderMoves(currentBoard, moves)

        for (move in orderedMoves) {
            val newBoard = currentBoard.makeMove(move)
            val eval = alphaBeta(newBoard, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, !isMaximizing)
            if (isMaximizing) {
                if (eval > bestEval) { bestEval = eval; bestMove = move }
            } else {
                if (eval < bestEval) { bestEval = eval; bestMove = move }
            }
        }

        if (bestMove != null) {
            line.add(bestMove)
            currentBoard = currentBoard.makeMove(bestMove)

            // Build the rest of the line (principal variation)
            repeat(depth - 1) {
                val nextMoves = MoveGenerator.generateLegalMoves(currentBoard)
                if (nextMoves.isEmpty()) return@repeat
                val nextIsMax = currentBoard.activeColor == PieceColor.WHITE
                var nextBest: Move? = null
                var nextBestEval = if (nextIsMax) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
                for (m in orderMoves(currentBoard, nextMoves)) {
                    val nb = currentBoard.makeMove(m)
                    val e = alphaBeta(nb, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, !nextIsMax)
                    if (nextIsMax) {
                        if (e > nextBestEval) { nextBestEval = e; nextBest = m }
                    } else {
                        if (e < nextBestEval) { nextBestEval = e; nextBest = m }
                    }
                }
                if (nextBest != null) {
                    line.add(nextBest)
                    currentBoard = currentBoard.makeMove(nextBest)
                }
            }
        }

        val eval = evaluate(currentBoard)
        return EngineAnalysis(
            bestLine = line,
            evaluation = eval,
            depth = depth,
            commentary = generateCommentary(board, line, eval)
        )
    }

    /** Alpha-beta pruning minimax search */
    private fun alphaBeta(board: Board, depth: Int, alphaIn: Double, betaIn: Double, maximizing: Boolean): Double {
        if (depth == 0) return quiescenceSearch(board, alphaIn, betaIn, maximizing, 4)

        val moves = MoveGenerator.generateLegalMoves(board)
        if (moves.isEmpty()) {
            return if (MoveGenerator.isInCheck(board, board.activeColor)) {
                if (maximizing) -99999.0 + (10 - depth) else 99999.0 - (10 - depth)
            } else 0.0 // stalemate
        }

        var alpha = alphaIn
        var beta = betaIn

        if (maximizing) {
            var maxEval = Double.NEGATIVE_INFINITY
            for (move in orderMoves(board, moves)) {
                val eval = alphaBeta(board.makeMove(move), depth - 1, alpha, beta, false)
                maxEval = maxOf(maxEval, eval)
                alpha = maxOf(alpha, eval)
                if (beta <= alpha) break // prune
            }
            return maxEval
        } else {
            var minEval = Double.POSITIVE_INFINITY
            for (move in orderMoves(board, moves)) {
                val eval = alphaBeta(board.makeMove(move), depth - 1, alpha, beta, true)
                minEval = minOf(minEval, eval)
                beta = minOf(beta, eval)
                if (beta <= alpha) break // prune
            }
            return minEval
        }
    }

    /** Quiescence search: continue searching captures to avoid horizon effect */
    private fun quiescenceSearch(board: Board, alphaIn: Double, betaIn: Double, maximizing: Boolean, depthLeft: Int): Double {
        val standPat = evaluate(board)

        if (depthLeft == 0) return standPat

        var alpha = alphaIn
        var beta = betaIn

        if (maximizing) {
            if (standPat >= beta) return beta
            alpha = maxOf(alpha, standPat)

            val captures = MoveGenerator.generateLegalMoves(board).filter { board[it.to] != null }
            for (move in orderMoves(board, captures)) {
                val eval = quiescenceSearch(board.makeMove(move), alpha, beta, false, depthLeft - 1)
                alpha = maxOf(alpha, eval)
                if (beta <= alpha) break
            }
            return alpha
        } else {
            if (standPat <= alpha) return alpha
            beta = minOf(beta, standPat)

            val captures = MoveGenerator.generateLegalMoves(board).filter { board[it.to] != null }
            for (move in orderMoves(board, captures)) {
                val eval = quiescenceSearch(board.makeMove(move), alpha, beta, true, depthLeft - 1)
                beta = minOf(beta, eval)
                if (beta <= alpha) break
            }
            return beta
        }
    }

    /** Move ordering: captures (high value victim first), then non-captures */
    private fun orderMoves(board: Board, moves: List<Move>): List<Move> {
        return moves.sortedByDescending { move ->
            val victim = board[move.to]
            val attacker = board[move.from]
            when {
                victim != null && attacker != null -> pieceValue(victim.type) * 10 - pieceValue(attacker.type) + 1000
                move.promotion != null -> 900.0
                else -> 0.0
            }
        }
    }

    override suspend fun getThinking(fen: String, depth: Int): EngineThought {
        require(initialized) { "Engine not initialized" }
        val board = Board.fromFen(fen)
        val eval = evaluate(board)
        val threats = detectThreats(board)
        val strategy = analyzeStrategy(board)

        val description = buildString {
            if (eval > 0.5) append("White has an advantage. ")
            else if (eval < -0.5) append("Black has an advantage. ")
            else append("Position is roughly equal. ")

            if (threats.isNotEmpty()) {
                append("Key threats: ${threats.joinToString(", ")}. ")
            }
            if (strategy.isNotEmpty()) {
                append(strategy.joinToString(". ") + ".")
            }
        }

        return EngineThought(
            description = description,
            evaluation = eval,
            threats = threats,
            strategicNotes = strategy
        )
    }

    fun evaluate(board: Board): Double {
        if (MoveGenerator.isCheckmate(board)) {
            return if (board.activeColor == PieceColor.WHITE) -9999.0 else 9999.0
        }
        if (MoveGenerator.isDraw(board)) return 0.0

        var score = 0.0

        // Material + piece-square tables
        for ((square, piece) in board.allPieces(PieceColor.WHITE)) {
            score += pieceValue(piece.type) + pieceSquareValue(piece.type, square, PieceColor.WHITE)
        }
        for ((square, piece) in board.allPieces(PieceColor.BLACK)) {
            score -= pieceValue(piece.type) + pieceSquareValue(piece.type, square, PieceColor.BLACK)
        }

        // Mobility for both sides
        val currentMoves = MoveGenerator.generateLegalMoves(board).size
        if (board.activeColor == PieceColor.WHITE) {
            score += currentMoves * 0.02
        } else {
            score -= currentMoves * 0.02
        }

        // King safety: penalise exposed kings (no pawns in front)
        score += kingSafety(board, PieceColor.WHITE)
        score -= kingSafety(board, PieceColor.BLACK)

        return score
    }

    private fun pieceValue(type: PieceType): Double = when (type) {
        PieceType.PAWN -> 1.0
        PieceType.KNIGHT -> 3.2
        PieceType.BISHOP -> 3.3
        PieceType.ROOK -> 5.0
        PieceType.QUEEN -> 9.0
        PieceType.KING -> 0.0
    }

    // Piece-square tables (from White's perspective, rank 0 = White's back rank)
    private fun pieceSquareValue(type: PieceType, square: Square, color: PieceColor): Double {
        val rank = if (color == PieceColor.WHITE) square.rank else 7 - square.rank
        val file = square.file
        return when (type) {
            PieceType.PAWN -> PAWN_TABLE[rank][file]
            PieceType.KNIGHT -> KNIGHT_TABLE[rank][file]
            PieceType.BISHOP -> BISHOP_TABLE[rank][file]
            PieceType.ROOK -> ROOK_TABLE[rank][file]
            PieceType.QUEEN -> QUEEN_TABLE[rank][file]
            PieceType.KING -> KING_TABLE[rank][file]
        }
    }

    private fun kingSafety(board: Board, color: PieceColor): Double {
        val kingSquare = board.allPieces(color).firstOrNull { it.second.type == PieceType.KING }?.first
            ?: return 0.0
        var safety = 0.0
        val pawnDir = if (color == PieceColor.WHITE) 1 else -1

        // Check for pawn shield in front of king
        for (df in -1..1) {
            val shieldFile = kingSquare.file + df
            val shieldRank = kingSquare.rank + pawnDir
            if (shieldFile in 0..7 && shieldRank in 0..7) {
                val sq = Square(shieldFile, shieldRank)
                val piece = board[sq]
                if (piece != null && piece.type == PieceType.PAWN && piece.color == color) {
                    safety += 0.15
                }
            }
        }
        return safety
    }

    private fun detectThreats(board: Board): List<String> {
        val threats = mutableListOf<String>()
        val activeColor = board.activeColor
        val opponent = activeColor.opposite()

        if (MoveGenerator.isInCheck(board, activeColor)) {
            threats.add("King is in check")
        }

        for ((square, piece) in board.allPieces(activeColor)) {
            if (MoveGenerator.isSquareAttacked(board, square, opponent)) {
                threats.add("${piece.type.name.lowercase()} on ${square.toAlgebraic()} is under attack")
            }
        }

        return threats.take(3)
    }

    private fun analyzeStrategy(board: Board): List<String> {
        val notes = mutableListOf<String>()
        val color = board.activeColor

        val pieces = board.allPieces(color)
        val pawns = pieces.filter { it.second.type == PieceType.PAWN }
        val developed = pieces.filter {
            it.second.type in listOf(PieceType.KNIGHT, PieceType.BISHOP) &&
                it.first.rank != (if (color == PieceColor.WHITE) 0 else 7)
        }

        if (developed.size < 2 && board.fullMoveNumber < 10) {
            notes.add("Consider developing minor pieces")
        }

        val centerPawns = pawns.filter { it.first.file in 3..4 && it.first.rank in 3..4 }
        if (centerPawns.isNotEmpty()) {
            notes.add("Good central pawn presence")
        }

        val hasCastled = if (color == PieceColor.WHITE) {
            !board.castlingRights.whiteKingSide && !board.castlingRights.whiteQueenSide
        } else {
            !board.castlingRights.blackKingSide && !board.castlingRights.blackQueenSide
        }
        if (!hasCastled && board.fullMoveNumber < 15) {
            val canCastle = if (color == PieceColor.WHITE) {
                board.castlingRights.whiteKingSide || board.castlingRights.whiteQueenSide
            } else {
                board.castlingRights.blackKingSide || board.castlingRights.blackQueenSide
            }
            if (canCastle) {
                notes.add("Consider castling for king safety")
            }
        }

        return notes.take(3)
    }

    private fun formatEval(value: Double): String {
        val rounded = (value * 100).toLong() / 100.0
        val str = rounded.toString()
        val dotIndex = str.indexOf('.')
        return if (dotIndex < 0) "$str.00"
        else {
            val decimals = str.length - dotIndex - 1
            when {
                decimals >= 2 -> str.substring(0, dotIndex + 3)
                decimals == 1 -> "${str}0"
                else -> "${str}00"
            }
        }
    }

    private fun generateCommentary(board: Board, line: List<Move>, eval: Double): String {
        if (line.isEmpty()) return "No moves available."

        return buildString {
            append("Best line: ")
            append(line.joinToString(" ") { it.toAlgebraic() })
            append(". Evaluation: ")
            append(if (eval > 0) "+${formatEval(eval)}" else formatEval(eval))
            append(" (")
            append(
                when {
                    eval > 2.0 -> "White is winning"
                    eval > 0.5 -> "White is better"
                    eval > -0.5 -> "roughly equal"
                    eval > -2.0 -> "Black is better"
                    else -> "Black is winning"
                }
            )
            append(")")
        }
    }

    companion object {
        // Piece-square tables (values in centipawns / 100, from white's perspective, rank 0 = back rank)
        private val PAWN_TABLE = arrayOf(
            doubleArrayOf(0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00),
            doubleArrayOf(0.05, 0.10, 0.10,-0.20,-0.20, 0.10, 0.10, 0.05),
            doubleArrayOf(0.05,-0.05,-0.10, 0.00, 0.00,-0.10,-0.05, 0.05),
            doubleArrayOf(0.00, 0.00, 0.00, 0.25, 0.25, 0.00, 0.00, 0.00),
            doubleArrayOf(0.05, 0.05, 0.10, 0.30, 0.30, 0.10, 0.05, 0.05),
            doubleArrayOf(0.10, 0.10, 0.20, 0.35, 0.35, 0.20, 0.10, 0.10),
            doubleArrayOf(0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50, 0.50),
            doubleArrayOf(0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00)
        )
        private val KNIGHT_TABLE = arrayOf(
            doubleArrayOf(-0.50,-0.40,-0.30,-0.30,-0.30,-0.30,-0.40,-0.50),
            doubleArrayOf(-0.40,-0.20, 0.00, 0.05, 0.05, 0.00,-0.20,-0.40),
            doubleArrayOf(-0.30, 0.05, 0.10, 0.15, 0.15, 0.10, 0.05,-0.30),
            doubleArrayOf(-0.30, 0.00, 0.15, 0.20, 0.20, 0.15, 0.00,-0.30),
            doubleArrayOf(-0.30, 0.05, 0.15, 0.20, 0.20, 0.15, 0.05,-0.30),
            doubleArrayOf(-0.30, 0.00, 0.10, 0.15, 0.15, 0.10, 0.00,-0.30),
            doubleArrayOf(-0.40,-0.20, 0.00, 0.00, 0.00, 0.00,-0.20,-0.40),
            doubleArrayOf(-0.50,-0.40,-0.30,-0.30,-0.30,-0.30,-0.40,-0.50)
        )
        private val BISHOP_TABLE = arrayOf(
            doubleArrayOf(-0.20,-0.10,-0.10,-0.10,-0.10,-0.10,-0.10,-0.20),
            doubleArrayOf(-0.10, 0.05, 0.00, 0.00, 0.00, 0.00, 0.05,-0.10),
            doubleArrayOf(-0.10, 0.10, 0.10, 0.10, 0.10, 0.10, 0.10,-0.10),
            doubleArrayOf(-0.10, 0.00, 0.10, 0.10, 0.10, 0.10, 0.00,-0.10),
            doubleArrayOf(-0.10, 0.05, 0.05, 0.10, 0.10, 0.05, 0.05,-0.10),
            doubleArrayOf(-0.10, 0.00, 0.05, 0.10, 0.10, 0.05, 0.00,-0.10),
            doubleArrayOf(-0.10, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.10),
            doubleArrayOf(-0.20,-0.10,-0.10,-0.10,-0.10,-0.10,-0.10,-0.20)
        )
        private val ROOK_TABLE = arrayOf(
            doubleArrayOf( 0.00, 0.00, 0.00, 0.05, 0.05, 0.00, 0.00, 0.00),
            doubleArrayOf(-0.05, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.05),
            doubleArrayOf(-0.05, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.05),
            doubleArrayOf(-0.05, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.05),
            doubleArrayOf(-0.05, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.05),
            doubleArrayOf(-0.05, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.05),
            doubleArrayOf( 0.05, 0.10, 0.10, 0.10, 0.10, 0.10, 0.10, 0.05),
            doubleArrayOf( 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00)
        )
        private val QUEEN_TABLE = arrayOf(
            doubleArrayOf(-0.20,-0.10,-0.10,-0.05,-0.05,-0.10,-0.10,-0.20),
            doubleArrayOf(-0.10, 0.00, 0.00, 0.00, 0.00, 0.00, 0.00,-0.10),
            doubleArrayOf(-0.10, 0.00, 0.05, 0.05, 0.05, 0.05, 0.00,-0.10),
            doubleArrayOf(-0.05, 0.00, 0.05, 0.05, 0.05, 0.05, 0.00,-0.05),
            doubleArrayOf( 0.00, 0.00, 0.05, 0.05, 0.05, 0.05, 0.00,-0.05),
            doubleArrayOf(-0.10, 0.05, 0.05, 0.05, 0.05, 0.05, 0.00,-0.10),
            doubleArrayOf(-0.10, 0.00, 0.05, 0.00, 0.00, 0.00, 0.00,-0.10),
            doubleArrayOf(-0.20,-0.10,-0.10,-0.05,-0.05,-0.10,-0.10,-0.20)
        )
        private val KING_TABLE = arrayOf(
            doubleArrayOf( 0.20, 0.30, 0.10, 0.00, 0.00, 0.10, 0.30, 0.20),
            doubleArrayOf( 0.20, 0.20, 0.00, 0.00, 0.00, 0.00, 0.20, 0.20),
            doubleArrayOf(-0.10,-0.20,-0.20,-0.20,-0.20,-0.20,-0.20,-0.10),
            doubleArrayOf(-0.20,-0.30,-0.30,-0.40,-0.40,-0.30,-0.30,-0.20),
            doubleArrayOf(-0.30,-0.40,-0.40,-0.50,-0.50,-0.40,-0.40,-0.30),
            doubleArrayOf(-0.30,-0.40,-0.40,-0.50,-0.50,-0.40,-0.40,-0.30),
            doubleArrayOf(-0.30,-0.40,-0.40,-0.50,-0.50,-0.40,-0.40,-0.30),
            doubleArrayOf(-0.30,-0.40,-0.40,-0.50,-0.50,-0.40,-0.40,-0.30)
        )
    }
}
