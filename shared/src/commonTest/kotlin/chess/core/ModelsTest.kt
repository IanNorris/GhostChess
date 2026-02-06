package chess.core

import kotlin.test.*

class ModelsTest {

    @Test
    fun squareAlgebraicRoundTrip() {
        for (file in 0..7) {
            for (rank in 0..7) {
                val square = Square(file, rank)
                val algebraic = square.toAlgebraic()
                assertEquals(square, Square.fromAlgebraic(algebraic))
            }
        }
    }

    @Test
    fun squareFromAlgebraicCornerCases() {
        assertEquals(Square(0, 0), Square.fromAlgebraic("a1"))
        assertEquals(Square(7, 7), Square.fromAlgebraic("h8"))
        assertEquals(Square(4, 3), Square.fromAlgebraic("e4"))
    }

    @Test
    fun squareValidation() {
        assertFailsWith<IllegalArgumentException> { Square(-1, 0) }
        assertFailsWith<IllegalArgumentException> { Square(0, 8) }
        assertFailsWith<IllegalArgumentException> { Square(8, 0) }
    }

    @Test
    fun squareIsValid() {
        assertTrue(Square.isValid(0, 0))
        assertTrue(Square.isValid(7, 7))
        assertFalse(Square.isValid(-1, 0))
        assertFalse(Square.isValid(8, 0))
    }

    @Test
    fun moveAlgebraicRoundTrip() {
        val move = Move(Square(4, 1), Square(4, 3))
        assertEquals("e2e4", move.toAlgebraic())
        assertEquals(move, Move.fromAlgebraic("e2e4"))
    }

    @Test
    fun moveWithPromotion() {
        val move = Move(Square(0, 6), Square(0, 7), promotion = PieceType.QUEEN)
        assertEquals("a7a8q", move.toAlgebraic())
        val parsed = Move.fromAlgebraic("a7a8q")
        assertEquals(PieceType.QUEEN, parsed.promotion)
    }

    @Test
    fun movePromotionTypes() {
        assertEquals("a7a8r", Move(Square(0, 6), Square(0, 7), promotion = PieceType.ROOK).toAlgebraic())
        assertEquals("a7a8b", Move(Square(0, 6), Square(0, 7), promotion = PieceType.BISHOP).toAlgebraic())
        assertEquals("a7a8n", Move(Square(0, 6), Square(0, 7), promotion = PieceType.KNIGHT).toAlgebraic())
    }

    @Test
    fun pieceToString() {
        assertEquals("K", Piece(PieceType.KING, PieceColor.WHITE).toString())
        assertEquals("k", Piece(PieceType.KING, PieceColor.BLACK).toString())
        assertEquals("P", Piece(PieceType.PAWN, PieceColor.WHITE).toString())
        assertEquals("n", Piece(PieceType.KNIGHT, PieceColor.BLACK).toString())
    }

    @Test
    fun colorOpposite() {
        assertEquals(PieceColor.BLACK, PieceColor.WHITE.opposite())
        assertEquals(PieceColor.WHITE, PieceColor.BLACK.opposite())
    }

    @Test
    fun castlingRightsFenRoundTrip() {
        assertEquals("KQkq", CastlingRights().toFen())
        assertEquals("-", CastlingRights(false, false, false, false).toFen())
        assertEquals("Kq", CastlingRights(true, false, false, true).toFen())

        assertEquals(CastlingRights(), CastlingRights.fromFen("KQkq"))
        assertEquals(CastlingRights(false, false, false, false), CastlingRights.fromFen("-"))
    }
}
