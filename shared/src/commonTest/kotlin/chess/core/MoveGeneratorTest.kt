package chess.core

import kotlin.test.*

class MoveGeneratorTest {

    @Test
    fun initialPositionHas20LegalMoves() {
        val board = Board.initial()
        val moves = MoveGenerator.generateLegalMoves(board)
        assertEquals(20, moves.size, "Initial position should have 20 legal moves (16 pawn + 4 knight)")
    }

    @Test
    fun initialPositionIsNotInCheck() {
        val board = Board.initial()
        assertFalse(MoveGenerator.isInCheck(board, PieceColor.WHITE))
        assertFalse(MoveGenerator.isInCheck(board, PieceColor.BLACK))
    }

    @Test
    fun initialPositionIsNotCheckmate() {
        assertFalse(MoveGenerator.isCheckmate(Board.initial()))
    }

    @Test
    fun initialPositionIsNotStalemate() {
        assertFalse(MoveGenerator.isStalemate(Board.initial()))
    }

    // --- Pawn moves ---

    @Test
    fun pawnCanMoveSingleSquare() {
        val board = Board.initial()
        val moves = MoveGenerator.generateLegalMoves(board)
        assertTrue(moves.contains(Move(Square(4, 1), Square(4, 2))), "e2-e3 should be legal")
    }

    @Test
    fun pawnCanDoubleAdvanceFromStartRank() {
        val board = Board.initial()
        val moves = MoveGenerator.generateLegalMoves(board)
        assertTrue(moves.contains(Move(Square(4, 1), Square(4, 3))), "e2-e4 should be legal")
    }

    @Test
    fun pawnCannotMoveForwardIntoOccupiedSquare() {
        val board = Board.fromFen("rnbqkbnr/pppppppp/8/8/4p3/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        // e2 pawn can only go to e3, not e4 (blocked)
        assertTrue(moves.contains(Move(Square(4, 1), Square(4, 2))))
        assertFalse(moves.contains(Move(Square(4, 1), Square(4, 3))))
    }

    @Test
    fun pawnCanCaptureDiagonally() {
        val board = Board.fromFen("rnbqkbnr/pppp1ppp/8/4p3/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        assertTrue(moves.contains(Move(Square(3, 3), Square(4, 4))), "d4xe5 should be legal")
    }

    @Test
    fun pawnEnPassantCapture() {
        val board = Board.fromFen("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val epMove = moves.find { it.from == Square(4, 4) && it.to == Square(3, 5) && it.isEnPassant }
        assertNotNull(epMove, "En passant capture exd6 should be legal")
    }

    @Test
    fun pawnPromotionGeneratesAllFourPieces() {
        val board = Board.fromFen("8/P7/8/8/8/8/8/4K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val promoMoves = moves.filter { it.from == Square(0, 6) && it.to == Square(0, 7) }
        assertEquals(4, promoMoves.size, "Should generate 4 promotion options")
        assertTrue(promoMoves.any { it.promotion == PieceType.QUEEN })
        assertTrue(promoMoves.any { it.promotion == PieceType.ROOK })
        assertTrue(promoMoves.any { it.promotion == PieceType.BISHOP })
        assertTrue(promoMoves.any { it.promotion == PieceType.KNIGHT })
    }

    // --- Knight moves ---

    @Test
    fun knightMovesFromInitialPosition() {
        val board = Board.initial()
        val moves = MoveGenerator.generateLegalMoves(board)
        val knightMoves = moves.filter { board[it.from]?.type == PieceType.KNIGHT }
        assertEquals(4, knightMoves.size, "Two knights × 2 moves each = 4")
    }

    @Test
    fun knightMovesInCenter() {
        val board = Board.fromFen("8/8/8/8/4N3/8/8/4K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val knightMoves = moves.filter { it.from == Square(4, 3) }
        assertEquals(8, knightMoves.size, "Knight in center should have 8 moves")
    }

    @Test
    fun knightMovesInCorner() {
        val board = Board.fromFen("8/8/8/8/8/8/8/N3K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val knightMoves = moves.filter { it.from == Square(0, 0) }
        assertEquals(2, knightMoves.size, "Knight in a1 corner should have 2 moves")
    }

    // --- Bishop moves ---

    @Test
    fun bishopMovesOnOpenBoard() {
        val board = Board.fromFen("8/8/8/8/4B3/8/8/4K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val bishopMoves = moves.filter { it.from == Square(4, 3) }
        assertEquals(13, bishopMoves.size, "Bishop in center of open board should have 13 moves")
    }

    // --- Rook moves ---

    @Test
    fun rookMovesOnOpenBoard() {
        val board = Board.fromFen("8/8/8/8/4R3/8/8/4K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val rookMoves = moves.filter { it.from == Square(4, 3) }
        // 14 - 1 = 13 (e1 blocked by own king)
        assertEquals(13, rookMoves.size, "Rook on e4 with king on e1 should have 13 moves")
    }

    // --- Queen moves ---

    @Test
    fun queenMovesOnOpenBoard() {
        val board = Board.fromFen("8/8/8/8/4Q3/8/8/4K2k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val queenMoves = moves.filter { it.from == Square(4, 3) }
        // 27 - 1 = 26 (e1 blocked by own king)
        assertEquals(26, queenMoves.size, "Queen on e4 with king on e1 should have 26 moves")
    }

    // --- King moves ---

    @Test
    fun kingMovesInCenter() {
        val board = Board.fromFen("8/8/8/8/4K3/8/8/7k w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val kingMoves = moves.filter { it.from == Square(4, 3) }
        assertEquals(8, kingMoves.size, "King in center should have 8 moves")
    }

    // --- Castling ---

    @Test
    fun castlingKingSideWhite() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val castle = moves.find { it.from == Square(4, 0) && it.to == Square(6, 0) && it.isCastle }
        assertNotNull(castle, "King-side castling should be legal")
    }

    @Test
    fun castlingQueenSideWhite() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val castle = moves.find { it.from == Square(4, 0) && it.to == Square(2, 0) && it.isCastle }
        assertNotNull(castle, "Queen-side castling should be legal")
    }

    @Test
    fun cannotCastleThroughCheck() {
        // Black rook attacks f1
        val board = Board.fromFen("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val kingSideCastle = moves.find { it.from == Square(4, 0) && it.to == Square(6, 0) && it.isCastle }
        assertNull(kingSideCastle, "Cannot castle king-side through check on f1")
    }

    @Test
    fun cannotCastleWhileInCheck() {
        // Black rook gives check on e1
        val board = Board.fromFen("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val castleMoves = moves.filter { it.isCastle }
        assertTrue(castleMoves.isEmpty(), "Cannot castle while in check")
    }

    @Test
    fun cannotCastleAfterKingMoved() {
        val board = Board.fromFen("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val castleMoves = moves.filter { it.isCastle }
        assertTrue(castleMoves.isEmpty(), "Cannot castle with no castling rights")
    }

    // --- Check ---

    @Test
    fun detectsCheck() {
        val board = Board.fromFen("rnbqkbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1")
        assertTrue(MoveGenerator.isInCheck(board, PieceColor.WHITE), "White should be in check from Qh4")
    }

    @Test
    fun mustBlockOrEvadeCheck() {
        val board = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        // All moves must resolve the check
        for (move in moves) {
            val newBoard = board.makeMove(move)
            assertFalse(
                MoveGenerator.isInCheck(newBoard, PieceColor.WHITE),
                "Move ${move.toAlgebraic()} should resolve check"
            )
        }
    }

    @Test
    fun pinnedPieceCannotMoveExposingKing() {
        // White bishop on e2 is pinned by black rook on e8
        val board = Board.fromFen("4r3/8/8/8/8/8/4B3/4K3 w - - 0 1")
        val moves = MoveGenerator.generateLegalMoves(board)
        val bishopMoves = moves.filter { it.from == Square(4, 1) }
        // Bishop can only move along the pin line (e-file) — but bishops move diagonally, so 0 legal bishop moves
        assertTrue(bishopMoves.isEmpty(), "Pinned bishop should have no legal moves")
    }

    // --- Checkmate ---

    @Test
    fun foolsMateIsCheckmate() {
        val board = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1")
        assertTrue(MoveGenerator.isCheckmate(board), "Fool's mate position should be checkmate")
    }

    @Test
    fun scholarsMateIsCheckmate() {
        val board = Board.fromFen("r1bqk2r/pppp1Qpp/2n2n2/2b1p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4")
        assertTrue(MoveGenerator.isCheckmate(board), "Scholar's mate position should be checkmate")
    }

    @Test
    fun backRankMateIsCheckmate() {
        val board = Board.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")
        // After Ra8#
        val matedBoard = Board.fromFen("R5k1/5ppp/8/8/8/8/8/4K3 b - - 1 1")
        assertTrue(MoveGenerator.isCheckmate(matedBoard), "Back rank mate should be checkmate")
    }

    // --- Stalemate ---

    @Test
    fun basicStalemateDetected() {
        val board = Board.fromFen("k7/8/1K6/8/8/8/8/8 b - - 0 1")
        // Black king on a8, white king on b6 — not stalemate yet, let's use a real stalemate
        val stalemateBoard = Board.fromFen("k7/2Q5/1K6/8/8/8/8/8 b - - 0 1")
        assertFalse(MoveGenerator.isInCheck(stalemateBoard, PieceColor.BLACK))
        assertTrue(MoveGenerator.isStalemate(stalemateBoard), "Should be stalemate")
    }

    // --- Insufficient material ---

    @Test
    fun kingVsKingIsInsufficientMaterial() {
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(MoveGenerator.isInsufficientMaterial(board))
    }

    @Test
    fun kingBishopVsKingIsInsufficientMaterial() {
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1")
        assertTrue(MoveGenerator.isInsufficientMaterial(board))
    }

    @Test
    fun kingKnightVsKingIsInsufficientMaterial() {
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4KN2 w - - 0 1")
        assertTrue(MoveGenerator.isInsufficientMaterial(board))
    }

    @Test
    fun kingRookVsKingIsSufficientMaterial() {
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4KR2 w - - 0 1")
        assertFalse(MoveGenerator.isInsufficientMaterial(board))
    }

    // --- Square attacks ---

    @Test
    fun squareAttackedByPawn() {
        val board = Board.fromFen("8/8/8/8/4p3/8/8/4K2k w - - 0 1")
        assertTrue(MoveGenerator.isSquareAttacked(board, Square(3, 2), PieceColor.BLACK))
        assertTrue(MoveGenerator.isSquareAttacked(board, Square(5, 2), PieceColor.BLACK))
        assertFalse(MoveGenerator.isSquareAttacked(board, Square(4, 2), PieceColor.BLACK))
    }

    @Test
    fun squareAttackedByKnight() {
        val board = Board.fromFen("8/8/8/8/4n3/8/8/4K2k w - - 0 1")
        assertTrue(MoveGenerator.isSquareAttacked(board, Square(2, 2), PieceColor.BLACK))
        assertTrue(MoveGenerator.isSquareAttacked(board, Square(6, 4), PieceColor.BLACK))
    }

    // --- Perft test (move count validation at depth) ---

    @Test
    fun perftDepth1InitialPosition() {
        val board = Board.initial()
        assertEquals(20, MoveGenerator.generateLegalMoves(board).size)
    }

    @Test
    fun perftDepth2InitialPosition() {
        val board = Board.initial()
        var count = 0
        for (move in MoveGenerator.generateLegalMoves(board)) {
            val newBoard = board.makeMove(move)
            count += MoveGenerator.generateLegalMoves(newBoard).size
        }
        assertEquals(400, count, "Depth 2 perft should be 400")
    }
}
