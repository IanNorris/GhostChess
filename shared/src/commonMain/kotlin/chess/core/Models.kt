package chess.core

enum class PieceColor { WHITE, BLACK;
    fun opposite() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

data class Piece(val type: PieceType, val color: PieceColor) {
    override fun toString(): String {
        val c = when (type) {
            PieceType.PAWN -> 'P'
            PieceType.KNIGHT -> 'N'
            PieceType.BISHOP -> 'B'
            PieceType.ROOK -> 'R'
            PieceType.QUEEN -> 'Q'
            PieceType.KING -> 'K'
        }
        return if (color == PieceColor.WHITE) c.toString() else c.lowercaseChar().toString()
    }
}

data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7 && rank in 0..7) { "Invalid square: file=$file, rank=$rank" }
    }

    fun toAlgebraic(): String = "${'a' + file}${rank + 1}"

    companion object {
        fun fromAlgebraic(s: String): Square {
            require(s.length == 2) { "Invalid algebraic notation: $s" }
            val file = s[0] - 'a'
            val rank = s[1] - '1'
            return Square(file, rank)
        }

        fun isValid(file: Int, rank: Int) = file in 0..7 && rank in 0..7
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastle: Boolean = false
) {
    fun toAlgebraic(): String {
        val base = "${from.toAlgebraic()}${to.toAlgebraic()}"
        return if (promotion != null) {
            base + when (promotion) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
        } else base
    }

    companion object {
        fun fromAlgebraic(s: String): Move {
            require(s.length in 4..5) { "Invalid move notation: $s" }
            val from = Square.fromAlgebraic(s.substring(0, 2))
            val to = Square.fromAlgebraic(s.substring(2, 4))
            val promotion = if (s.length == 5) {
                when (s[4].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            } else null
            return Move(from, to, promotion)
        }
    }
}
