package chess.core

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    fun toFen(): String {
        val sb = StringBuilder()
        if (whiteKingSide) sb.append('K')
        if (whiteQueenSide) sb.append('Q')
        if (blackKingSide) sb.append('k')
        if (blackQueenSide) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        fun fromFen(s: String): CastlingRights {
            if (s == "-") return CastlingRights(false, false, false, false)
            return CastlingRights(
                whiteKingSide = 'K' in s,
                whiteQueenSide = 'Q' in s,
                blackKingSide = 'k' in s,
                blackQueenSide = 'q' in s
            )
        }
    }
}

class Board private constructor(
    private val squares: Array<Piece?>,
    val activeColor: PieceColor,
    val castlingRights: CastlingRights,
    val enPassantTarget: Square?,
    val halfMoveClock: Int,
    val fullMoveNumber: Int
) {
    operator fun get(square: Square): Piece? = squares[square.rank * 8 + square.file]
    operator fun get(file: Int, rank: Int): Piece? = squares[rank * 8 + file]

    fun findKing(color: PieceColor): Square {
        for (rank in 0..7) {
            for (file in 0..7) {
                val piece = this[file, rank]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                    return Square(file, rank)
                }
            }
        }
        throw IllegalStateException("No king found for $color")
    }

    fun allPieces(color: PieceColor): List<Pair<Square, Piece>> {
        val result = mutableListOf<Pair<Square, Piece>>()
        for (rank in 0..7) {
            for (file in 0..7) {
                val piece = this[file, rank]
                if (piece != null && piece.color == color) {
                    result.add(Square(file, rank) to piece)
                }
            }
        }
        return result
    }

    fun makeMove(move: Move): Board {
        val newSquares = squares.copyOf()
        val piece = newSquares[move.from.rank * 8 + move.from.file]
            ?: throw IllegalArgumentException("No piece at ${move.from.toAlgebraic()}")

        // Clear source
        newSquares[move.from.rank * 8 + move.from.file] = null

        // Handle en passant capture
        if (move.isEnPassant) {
            val capturedRank = move.from.rank
            newSquares[capturedRank * 8 + move.to.file] = null
        }

        // Handle castling
        if (move.isCastle) {
            val rookFromFile = if (move.to.file > move.from.file) 7 else 0
            val rookToFile = if (move.to.file > move.from.file) 5 else 3
            val rook = newSquares[move.from.rank * 8 + rookFromFile]
            newSquares[move.from.rank * 8 + rookFromFile] = null
            newSquares[move.from.rank * 8 + rookToFile] = rook
        }

        // Place piece (with promotion if applicable)
        val placedPiece = if (move.promotion != null) {
            Piece(move.promotion, piece.color)
        } else {
            piece
        }
        newSquares[move.to.rank * 8 + move.to.file] = placedPiece

        // Update castling rights
        var newCastling = castlingRights
        if (piece.type == PieceType.KING) {
            newCastling = if (piece.color == PieceColor.WHITE) {
                newCastling.copy(whiteKingSide = false, whiteQueenSide = false)
            } else {
                newCastling.copy(blackKingSide = false, blackQueenSide = false)
            }
        }
        if (piece.type == PieceType.ROOK) {
            if (move.from == Square(0, 0)) newCastling = newCastling.copy(whiteQueenSide = false)
            if (move.from == Square(7, 0)) newCastling = newCastling.copy(whiteKingSide = false)
            if (move.from == Square(0, 7)) newCastling = newCastling.copy(blackQueenSide = false)
            if (move.from == Square(7, 7)) newCastling = newCastling.copy(blackKingSide = false)
        }
        // Also revoke if rook captured
        if (move.to == Square(0, 0)) newCastling = newCastling.copy(whiteQueenSide = false)
        if (move.to == Square(7, 0)) newCastling = newCastling.copy(whiteKingSide = false)
        if (move.to == Square(0, 7)) newCastling = newCastling.copy(blackQueenSide = false)
        if (move.to == Square(7, 7)) newCastling = newCastling.copy(blackKingSide = false)

        // En passant target
        val newEnPassant = if (piece.type == PieceType.PAWN &&
            kotlin.math.abs(move.to.rank - move.from.rank) == 2
        ) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        // Half-move clock
        val newHalfMove = if (piece.type == PieceType.PAWN ||
            squares[move.to.rank * 8 + move.to.file] != null || move.isEnPassant
        ) 0 else halfMoveClock + 1

        // Full move number
        val newFullMove = if (activeColor == PieceColor.BLACK) fullMoveNumber + 1 else fullMoveNumber

        return Board(
            newSquares,
            activeColor.opposite(),
            newCastling,
            newEnPassant,
            newHalfMove,
            newFullMove
        )
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var emptyCount = 0
            for (file in 0..7) {
                val piece = this[file, rank]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece.toString())
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (activeColor == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')
        sb.append(castlingRights.toFen())
        sb.append(' ')
        sb.append(enPassantTarget?.toAlgebraic() ?: "-")
        sb.append(' ')
        sb.append(halfMoveClock)
        sb.append(' ')
        sb.append(fullMoveNumber)
        return sb.toString()
    }

    companion object {
        fun initial(): Board = fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        fun empty(
            activeColor: PieceColor = PieceColor.WHITE,
            castlingRights: CastlingRights = CastlingRights(false, false, false, false)
        ): Board {
            return Board(
                arrayOfNulls(64),
                activeColor,
                castlingRights,
                null,
                0,
                1
            )
        }

        fun fromFen(fen: String): Board {
            val parts = fen.split(' ')
            require(parts.size == 6) { "Invalid FEN: $fen" }

            val squares = arrayOfNulls<Piece>(64)
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "Invalid FEN board: ${parts[0]}" }

            for ((fenRankIdx, rankStr) in ranks.withIndex()) {
                val rank = 7 - fenRankIdx
                var file = 0
                for (ch in rankStr) {
                    if (ch.isDigit()) {
                        file += ch.digitToInt()
                    } else {
                        val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                        val type = when (ch.uppercaseChar()) {
                            'P' -> PieceType.PAWN
                            'N' -> PieceType.KNIGHT
                            'B' -> PieceType.BISHOP
                            'R' -> PieceType.ROOK
                            'Q' -> PieceType.QUEEN
                            'K' -> PieceType.KING
                            else -> throw IllegalArgumentException("Invalid piece char: $ch")
                        }
                        squares[rank * 8 + file] = Piece(type, color)
                        file++
                    }
                }
            }

            val activeColor = if (parts[1] == "w") PieceColor.WHITE else PieceColor.BLACK
            val castlingRights = CastlingRights.fromFen(parts[2])
            val enPassant = if (parts[3] == "-") null else Square.fromAlgebraic(parts[3])
            val halfMove = parts[4].toInt()
            val fullMove = parts[5].toInt()

            return Board(squares, activeColor, castlingRights, enPassant, halfMove, fullMove)
        }
    }
}
