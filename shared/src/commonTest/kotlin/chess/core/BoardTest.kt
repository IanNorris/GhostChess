package chess.core

import kotlin.test.*

class BoardTest {

    @Test
    fun initialBoardHasCorrectFen() {
        val board = Board.initial()
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            board.toFen()
        )
    }

    @Test
    fun initialBoardHasWhiteToMove() {
        val board = Board.initial()
        assertEquals(PieceColor.WHITE, board.activeColor)
    }

    @Test
    fun initialBoardHasAllCastlingRights() {
        val board = Board.initial()
        assertTrue(board.castlingRights.whiteKingSide)
        assertTrue(board.castlingRights.whiteQueenSide)
        assertTrue(board.castlingRights.blackKingSide)
        assertTrue(board.castlingRights.blackQueenSide)
    }

    @Test
    fun initialBoardHasCorrectPieces() {
        val board = Board.initial()
        // White pieces
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), board[Square(0, 0)])
        assertEquals(Piece(PieceType.KNIGHT, PieceColor.WHITE), board[Square(1, 0)])
        assertEquals(Piece(PieceType.BISHOP, PieceColor.WHITE), board[Square(2, 0)])
        assertEquals(Piece(PieceType.QUEEN, PieceColor.WHITE), board[Square(3, 0)])
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), board[Square(4, 0)])
        // White pawns
        for (file in 0..7) {
            assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), board[Square(file, 1)])
        }
        // Black pieces
        assertEquals(Piece(PieceType.ROOK, PieceColor.BLACK), board[Square(0, 7)])
        assertEquals(Piece(PieceType.KING, PieceColor.BLACK), board[Square(4, 7)])
        // Black pawns
        for (file in 0..7) {
            assertEquals(Piece(PieceType.PAWN, PieceColor.BLACK), board[Square(file, 6)])
        }
        // Empty squares in middle
        for (rank in 2..5) {
            for (file in 0..7) {
                assertNull(board[Square(file, rank)])
            }
        }
    }

    @Test
    fun fenRoundTrip() {
        val fens = listOf(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "r1bqkb1r/pppppppp/2n2n2/8/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
            "8/8/8/8/8/8/8/4K2k w - - 0 1",
            "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
        )
        for (fen in fens) {
            assertEquals(fen, Board.fromFen(fen).toFen(), "FEN round-trip failed for: $fen")
        }
    }

    @Test
    fun makeMoveChangesActiveColor() {
        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        val newBoard = board.makeMove(move)
        assertEquals(PieceColor.BLACK, newBoard.activeColor)
    }

    @Test
    fun makeMoveSetsPawnEnPassantTarget() {
        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        val newBoard = board.makeMove(move)
        assertEquals(Square(4, 2), newBoard.enPassantTarget)
    }

    @Test
    fun makeMoveKingRemovesCastlingRights() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
        val move = Move(Square(4, 0), Square(5, 0)) // Ke1-f1
        val newBoard = board.makeMove(move)
        assertFalse(newBoard.castlingRights.whiteKingSide)
        assertFalse(newBoard.castlingRights.whiteQueenSide)
        assertTrue(newBoard.castlingRights.blackKingSide)
        assertTrue(newBoard.castlingRights.blackQueenSide)
    }

    @Test
    fun makeMoveRookRemovesCastlingRightForThatSide() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
        val move = Move(Square(7, 0), Square(7, 1)) // Rh1-h2
        val newBoard = board.makeMove(move)
        assertFalse(newBoard.castlingRights.whiteKingSide)
        assertTrue(newBoard.castlingRights.whiteQueenSide)
    }

    @Test
    fun emptyBoardHasNoPieces() {
        val board = Board.empty()
        for (rank in 0..7) {
            for (file in 0..7) {
                assertNull(board[file, rank])
            }
        }
    }

    @Test
    fun findKingReturnsCorrectSquare() {
        val board = Board.initial()
        assertEquals(Square(4, 0), board.findKing(PieceColor.WHITE))
        assertEquals(Square(4, 7), board.findKing(PieceColor.BLACK))
    }

    @Test
    fun allPiecesReturnsCorrectCount() {
        val board = Board.initial()
        assertEquals(16, board.allPieces(PieceColor.WHITE).size)
        assertEquals(16, board.allPieces(PieceColor.BLACK).size)
    }

    @Test
    fun castlingMovesRookCorrectly() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
        // King-side castle
        val kingSide = board.makeMove(Move(Square(4, 0), Square(6, 0), isCastle = true))
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), kingSide[Square(6, 0)])
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), kingSide[Square(5, 0)])
        assertNull(kingSide[Square(4, 0)])
        assertNull(kingSide[Square(7, 0)])

        // Queen-side castle
        val queenSide = board.makeMove(Move(Square(4, 0), Square(2, 0), isCastle = true))
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), queenSide[Square(2, 0)])
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), queenSide[Square(3, 0)])
        assertNull(queenSide[Square(4, 0)])
        assertNull(queenSide[Square(0, 0)])
    }

    @Test
    fun enPassantCaptureRemovesPawn() {
        // White pawn on e5, black pawn just moved d7-d5
        val board = Board.fromFen("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 1")
        val move = Move(Square(4, 4), Square(3, 5), isEnPassant = true) // exd6 e.p.
        val newBoard = board.makeMove(move)
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), newBoard[Square(3, 5)])
        assertNull(newBoard[Square(3, 4)]) // captured pawn removed
    }

    @Test
    fun promotionReplacesPawn() {
        val board = Board.fromFen("8/P7/8/8/8/8/8/4K2k w - - 0 1")
        val move = Move(Square(0, 6), Square(0, 7), promotion = PieceType.QUEEN)
        val newBoard = board.makeMove(move)
        assertEquals(Piece(PieceType.QUEEN, PieceColor.WHITE), newBoard[Square(0, 7)])
    }

    @Test
    fun halfMoveClockResetsOnPawnMove() {
        val board = Board.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 5 1")
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        val newBoard = board.makeMove(move)
        assertEquals(0, newBoard.halfMoveClock)
    }

    @Test
    fun halfMoveClockIncrementsOnNonPawnNonCapture() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 5 1")
        val move = Move(Square(4, 0), Square(5, 0)) // Ke1-f1
        val newBoard = board.makeMove(move)
        assertEquals(6, newBoard.halfMoveClock)
    }

    @Test
    fun fullMoveNumberIncrementsAfterBlackMoves() {
        val board = Board.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
        val move = Move(Square(4, 6), Square(4, 4)) // e7-e5
        val newBoard = board.makeMove(move)
        assertEquals(2, newBoard.fullMoveNumber)
    }
}
