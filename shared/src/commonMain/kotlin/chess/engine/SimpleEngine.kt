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

        repeat(depth) {
            val moves = MoveGenerator.generateLegalMoves(currentBoard)
            if (moves.isEmpty()) return@repeat
            val bestMove = moves.maxByOrNull { evaluateMove(currentBoard, it) } ?: return@repeat
            line.add(bestMove)
            currentBoard = currentBoard.makeMove(bestMove)
        }

        val eval = evaluate(currentBoard)
        return EngineAnalysis(
            bestLine = line,
            evaluation = eval,
            depth = depth,
            commentary = generateCommentary(board, line, eval)
        )
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

    private fun evaluateMove(board: Board, move: Move): Double {
        val newBoard = board.makeMove(move)

        // Checkmate is the best possible outcome
        if (MoveGenerator.isCheckmate(newBoard)) return 10000.0

        val eval = evaluate(newBoard)
        // Negate for the side that just moved (minimax)
        return if (board.activeColor == PieceColor.WHITE) eval else -eval
    }

    internal fun evaluate(board: Board): Double {
        if (MoveGenerator.isCheckmate(board)) {
            return if (board.activeColor == PieceColor.WHITE) -9999.0 else 9999.0
        }
        if (MoveGenerator.isDraw(board)) return 0.0

        var score = 0.0

        // Material
        for ((square, piece) in board.allPieces(PieceColor.WHITE)) {
            score += pieceValue(piece.type) + positionalBonus(piece, square)
        }
        for ((square, piece) in board.allPieces(PieceColor.BLACK)) {
            score -= pieceValue(piece.type) + positionalBonus(piece, square)
        }

        // Mobility
        val whiteMobility = if (board.activeColor == PieceColor.WHITE)
            MoveGenerator.generateLegalMoves(board).size.toDouble() * 0.02
        else 0.0

        score += whiteMobility

        return score
    }

    private fun pieceValue(type: PieceType): Double = when (type) {
        PieceType.PAWN -> 1.0
        PieceType.KNIGHT -> 3.0
        PieceType.BISHOP -> 3.25
        PieceType.ROOK -> 5.0
        PieceType.QUEEN -> 9.0
        PieceType.KING -> 0.0
    }

    private fun positionalBonus(piece: Piece, square: Square): Double {
        // Center control bonus
        val centerBonus = if (square.file in 2..5 && square.rank in 2..5) 0.1 else 0.0

        // Pawn advancement bonus
        val pawnBonus = if (piece.type == PieceType.PAWN) {
            if (piece.color == PieceColor.WHITE) square.rank * 0.05
            else (7 - square.rank) * 0.05
        } else 0.0

        return centerBonus + pawnBonus
    }

    private fun detectThreats(board: Board): List<String> {
        val threats = mutableListOf<String>()
        val activeColor = board.activeColor
        val opponent = activeColor.opposite()

        if (MoveGenerator.isInCheck(board, activeColor)) {
            threats.add("King is in check")
        }

        // Check for hanging pieces (undefended pieces under attack)
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

        // Check castling
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
}
