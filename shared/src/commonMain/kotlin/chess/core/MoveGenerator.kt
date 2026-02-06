package chess.core

object MoveGenerator {

    fun generateLegalMoves(board: Board): List<Move> {
        return generatePseudoLegalMoves(board).filter { move ->
            val newBoard = board.makeMove(move)
            !isInCheck(newBoard, board.activeColor)
        }
    }

    fun isInCheck(board: Board, color: PieceColor): Boolean {
        val kingSquare = board.findKing(color)
        return isSquareAttacked(board, kingSquare, color.opposite())
    }

    fun isCheckmate(board: Board): Boolean {
        return isInCheck(board, board.activeColor) && generateLegalMoves(board).isEmpty()
    }

    fun isStalemate(board: Board): Boolean {
        return !isInCheck(board, board.activeColor) && generateLegalMoves(board).isEmpty()
    }

    fun isDraw(board: Board): Boolean {
        return isStalemate(board) || board.halfMoveClock >= 100 || isInsufficientMaterial(board)
    }

    fun isInsufficientMaterial(board: Board): Boolean {
        val whites = board.allPieces(PieceColor.WHITE)
        val blacks = board.allPieces(PieceColor.BLACK)
        val whitePieces = whites.map { it.second.type }
        val blackPieces = blacks.map { it.second.type }

        // K vs K
        if (whitePieces.size == 1 && blackPieces.size == 1) return true
        // K+B vs K or K+N vs K
        if (whitePieces.size == 1 && blackPieces.size == 2) {
            return blackPieces.any { it == PieceType.BISHOP || it == PieceType.KNIGHT }
        }
        if (blackPieces.size == 1 && whitePieces.size == 2) {
            return whitePieces.any { it == PieceType.BISHOP || it == PieceType.KNIGHT }
        }
        return false
    }

    fun isSquareAttacked(board: Board, square: Square, byColor: PieceColor): Boolean {
        // Check pawn attacks
        val pawnDir = if (byColor == PieceColor.WHITE) -1 else 1
        for (df in listOf(-1, 1)) {
            val f = square.file + df
            val r = square.rank + pawnDir
            if (Square.isValid(f, r)) {
                val piece = board[f, r]
                if (piece != null && piece.color == byColor && piece.type == PieceType.PAWN) return true
            }
        }

        // Check knight attacks
        val knightOffsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((df, dr) in knightOffsets) {
            val f = square.file + df
            val r = square.rank + dr
            if (Square.isValid(f, r)) {
                val piece = board[f, r]
                if (piece != null && piece.color == byColor && piece.type == PieceType.KNIGHT) return true
            }
        }

        // Check king attacks
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = square.file + df
                val r = square.rank + dr
                if (Square.isValid(f, r)) {
                    val piece = board[f, r]
                    if (piece != null && piece.color == byColor && piece.type == PieceType.KING) return true
                }
            }
        }

        // Check sliding pieces (bishop/rook/queen)
        val bishopDirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        val rookDirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for ((df, dr) in bishopDirs) {
            if (checkSlidingAttack(board, square, df, dr, byColor, PieceType.BISHOP)) return true
        }
        for ((df, dr) in rookDirs) {
            if (checkSlidingAttack(board, square, df, dr, byColor, PieceType.ROOK)) return true
        }

        return false
    }

    private fun checkSlidingAttack(
        board: Board, square: Square, df: Int, dr: Int,
        byColor: PieceColor, pieceType: PieceType
    ): Boolean {
        var f = square.file + df
        var r = square.rank + dr
        while (Square.isValid(f, r)) {
            val piece = board[f, r]
            if (piece != null) {
                if (piece.color == byColor && (piece.type == pieceType || piece.type == PieceType.QUEEN)) {
                    return true
                }
                break
            }
            f += df
            r += dr
        }
        return false
    }

    private fun generatePseudoLegalMoves(board: Board): List<Move> {
        val moves = mutableListOf<Move>()
        for ((square, piece) in board.allPieces(board.activeColor)) {
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(board, square, piece.color, moves)
                PieceType.KNIGHT -> generateKnightMoves(board, square, piece.color, moves)
                PieceType.BISHOP -> generateSlidingMoves(board, square, piece.color, bishopDirs, moves)
                PieceType.ROOK -> generateSlidingMoves(board, square, piece.color, rookDirs, moves)
                PieceType.QUEEN -> generateSlidingMoves(board, square, piece.color, queenDirs, moves)
                PieceType.KING -> generateKingMoves(board, square, piece.color, moves)
            }
        }
        return moves
    }

    private val bishopDirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val rookDirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val queenDirs = bishopDirs + rookDirs

    private fun generatePawnMoves(board: Board, from: Square, color: PieceColor, moves: MutableList<Move>) {
        val dir = if (color == PieceColor.WHITE) 1 else -1
        val startRank = if (color == PieceColor.WHITE) 1 else 6
        val promoRank = if (color == PieceColor.WHITE) 7 else 0

        // Single push
        val oneStep = from.rank + dir
        if (Square.isValid(from.file, oneStep) && board[from.file, oneStep] == null) {
            if (oneStep == promoRank) {
                addPromotions(from, Square(from.file, oneStep), moves)
            } else {
                moves.add(Move(from, Square(from.file, oneStep)))
            }

            // Double push
            if (from.rank == startRank) {
                val twoStep = from.rank + 2 * dir
                if (board[from.file, twoStep] == null) {
                    moves.add(Move(from, Square(from.file, twoStep)))
                }
            }
        }

        // Captures
        for (df in listOf(-1, 1)) {
            val tf = from.file + df
            val tr = from.rank + dir
            if (!Square.isValid(tf, tr)) continue
            val target = Square(tf, tr)
            val captured = board[tf, tr]

            if (captured != null && captured.color != color) {
                if (tr == promoRank) {
                    addPromotions(from, target, moves)
                } else {
                    moves.add(Move(from, target))
                }
            }

            // En passant
            if (board.enPassantTarget == target) {
                moves.add(Move(from, target, isEnPassant = true))
            }
        }
    }

    private fun addPromotions(from: Square, to: Square, moves: MutableList<Move>) {
        for (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            moves.add(Move(from, to, promotion = type))
        }
    }

    private fun generateKnightMoves(board: Board, from: Square, color: PieceColor, moves: MutableList<Move>) {
        val offsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((df, dr) in offsets) {
            val tf = from.file + df
            val tr = from.rank + dr
            if (!Square.isValid(tf, tr)) continue
            val target = board[tf, tr]
            if (target == null || target.color != color) {
                moves.add(Move(from, Square(tf, tr)))
            }
        }
    }

    private fun generateSlidingMoves(
        board: Board, from: Square, color: PieceColor,
        directions: List<Pair<Int, Int>>, moves: MutableList<Move>
    ) {
        for ((df, dr) in directions) {
            var f = from.file + df
            var r = from.rank + dr
            while (Square.isValid(f, r)) {
                val target = board[f, r]
                if (target == null) {
                    moves.add(Move(from, Square(f, r)))
                } else {
                    if (target.color != color) {
                        moves.add(Move(from, Square(f, r)))
                    }
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun generateKingMoves(board: Board, from: Square, color: PieceColor, moves: MutableList<Move>) {
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val tf = from.file + df
                val tr = from.rank + dr
                if (!Square.isValid(tf, tr)) continue
                val target = board[tf, tr]
                if (target == null || target.color != color) {
                    moves.add(Move(from, Square(tf, tr)))
                }
            }
        }

        // Castling
        if (!isInCheck(board, color)) {
            val rank = if (color == PieceColor.WHITE) 0 else 7

            // King-side
            val canKingSide = if (color == PieceColor.WHITE) board.castlingRights.whiteKingSide
            else board.castlingRights.blackKingSide
            if (canKingSide &&
                board[5, rank] == null && board[6, rank] == null &&
                !isSquareAttacked(board, Square(5, rank), color.opposite()) &&
                !isSquareAttacked(board, Square(6, rank), color.opposite())
            ) {
                moves.add(Move(from, Square(6, rank), isCastle = true))
            }

            // Queen-side
            val canQueenSide = if (color == PieceColor.WHITE) board.castlingRights.whiteQueenSide
            else board.castlingRights.blackQueenSide
            if (canQueenSide &&
                board[3, rank] == null && board[2, rank] == null && board[1, rank] == null &&
                !isSquareAttacked(board, Square(3, rank), color.opposite()) &&
                !isSquareAttacked(board, Square(2, rank), color.opposite())
            ) {
                moves.add(Move(from, Square(2, rank), isCastle = true))
            }
        }
    }
}
