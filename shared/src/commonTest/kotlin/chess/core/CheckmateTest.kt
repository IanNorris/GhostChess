package chess.core

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for checkmate detection and game-ending scenarios.
 */
class CheckmateTest {

    @Test
    fun foolsMateBlackWins() {
        // Fool's mate: 1. f3 e5 2. g4 Qh4#
        var state = GameState.new()
        state = state.makeMove(Move.fromAlgebraic("f2f3"))
        state = state.makeMove(Move.fromAlgebraic("e7e5"))
        state = state.makeMove(Move.fromAlgebraic("g2g4"))
        state = state.makeMove(Move(Square(3, 7), Square(7, 3))) // Qd8-h4
        assertEquals(GameStatus.BLACK_WINS, state.status)
    }

    @Test
    fun scholarsMateWhiteWins() {
        // Scholar's mate: 1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#
        var state = GameState.new()
        state = state.makeMove(Move.fromAlgebraic("e2e4"))
        state = state.makeMove(Move.fromAlgebraic("e7e5"))
        state = state.makeMove(Move.fromAlgebraic("f1c4")) // Bc4
        state = state.makeMove(Move.fromAlgebraic("b8c6")) // Nc6
        state = state.makeMove(Move.fromAlgebraic("d1h5")) // Qh5
        state = state.makeMove(Move.fromAlgebraic("g8f6")) // Nf6
        state = state.makeMove(Move.fromAlgebraic("h5f7")) // Qxf7#
        assertEquals(GameStatus.WHITE_WINS, state.status)
    }

    @Test
    fun checkmateEndsGame() {
        // After checkmate, no more moves should be possible
        var state = GameState.new()
        state = state.makeMove(Move.fromAlgebraic("f2f3"))
        state = state.makeMove(Move.fromAlgebraic("e7e5"))
        state = state.makeMove(Move.fromAlgebraic("g2g4"))
        state = state.makeMove(Move(Square(3, 7), Square(7, 3))) // Qh4#

        assertEquals(GameStatus.BLACK_WINS, state.status)
        assertFailsWith<IllegalArgumentException> {
            state.makeMove(Move.fromAlgebraic("e2e4"))
        }
    }

    @Test
    fun checkmatedPositionHasNoLegalMoves() {
        var state = GameState.new()
        state = state.makeMove(Move.fromAlgebraic("f2f3"))
        state = state.makeMove(Move.fromAlgebraic("e7e5"))
        state = state.makeMove(Move.fromAlgebraic("g2g4"))
        state = state.makeMove(Move(Square(3, 7), Square(7, 3))) // Qh4#

        val legalMoves = MoveGenerator.generateLegalMoves(state.board)
        assertTrue(legalMoves.isEmpty(), "Checkmated side should have no legal moves")
    }

    @Test
    fun checkmateFromFenPosition() {
        // Position just before back-rank mate
        // White Rook on a1, White King on g1, Black King on g8
        // White plays Ra8#
        val fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"
        var state = GameState.fromFen(fen)
        state = state.makeMove(Move.fromAlgebraic("a1a8"))
        assertEquals(GameStatus.WHITE_WINS, state.status)
    }

    @Test
    fun gameSessionDetectsCheckmate() = runTest {
        val engine = chess.engine.SimpleEngine()
        val config = chess.game.GameConfig(
            mode = chess.game.GameMode.HUMAN_VS_HUMAN,
            ghostDepth = 1
        )
        val session = chess.game.GameSession(engine, config)
        session.initialize()

        // Play fool's mate
        session.makePlayerMove(Move.fromAlgebraic("f2f3"))
        session.makePlayerMove(Move.fromAlgebraic("e7e5"))
        session.makePlayerMove(Move.fromAlgebraic("g2g4"))
        session.makePlayerMove(Move(Square(3, 7), Square(7, 3))) // Qh4#

        assertEquals(GameStatus.BLACK_WINS, session.getGameState().status)
    }

    @Test
    fun stalemateIsDraw() {
        // Stalemate position: Black king on a8, White queen on b6, White king on c8
        // It's black's turn and they have no legal moves but aren't in check
        val fen = "k7/8/1Q6/8/8/8/8/2K5 b - - 0 1"
        val board = Board.fromFen(fen)
        assertTrue(MoveGenerator.isDraw(board), "Stalemate should be a draw")
        assertTrue(MoveGenerator.isStalemate(board), "Should detect stalemate")
        val legalMoves = MoveGenerator.generateLegalMoves(board)
        assertTrue(legalMoves.isEmpty(), "Stalemated side should have no legal moves")
    }
}
