package chess.engine

import chess.core.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SimpleEngineTest {

    private lateinit var engine: SimpleEngine

    @BeforeTest
    fun setup() = runTest {
        engine = SimpleEngine()
        engine.initialize()
    }

    @Test
    fun engineIsReadyAfterInit() {
        assertTrue(engine.isReady())
    }

    @Test
    fun engineNotReadyBeforeInit() {
        val uninit = SimpleEngine()
        assertFalse(uninit.isReady())
    }

    @Test
    fun engineShutdownMakesNotReady() {
        engine.shutdown()
        assertFalse(engine.isReady())
    }

    @Test
    fun getBestLineThrowsIfNotInitialized() = runTest {
        val uninit = SimpleEngine()
        assertFailsWith<IllegalArgumentException> {
            uninit.getBestLine("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        }
    }

    @Test
    fun getBestLineReturnsNonEmptyLine() = runTest {
        val result = engine.getBestLine(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            depth = 3
        )
        assertTrue(result.bestLine.isNotEmpty(), "Best line should not be empty")
        assertTrue(result.bestLine.size <= 3, "Should return at most 'depth' moves")
    }

    @Test
    fun getBestLineMovesAreLegal() = runTest {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val result = engine.getBestLine(fen, depth = 5)
        var board = Board.fromFen(fen)

        for (move in result.bestLine) {
            val legalMoves = MoveGenerator.generateLegalMoves(board)
            assertTrue(
                move in legalMoves,
                "Move ${move.toAlgebraic()} should be legal at position ${board.toFen()}"
            )
            board = board.makeMove(move)
        }
    }

    @Test
    fun getBestLineHasEvaluation() = runTest {
        val result = engine.getBestLine(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        // Initial position should be roughly equal
        assertTrue(result.evaluation > -2.0 && result.evaluation < 2.0,
            "Initial position eval should be near 0, got ${result.evaluation}")
    }

    @Test
    fun getBestLineHasCommentary() = runTest {
        val result = engine.getBestLine(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        assertTrue(result.commentary.isNotEmpty(), "Commentary should not be empty")
        assertTrue(result.commentary.contains("Best line"), "Commentary should describe best line")
    }

    @Test
    fun getBestLineDetectsWinningPosition() = runTest {
        // White is up a queen
        val result = engine.getBestLine(
            "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            depth = 2
        )
        assertTrue(result.evaluation > 0, "White should have positive eval when up material")
    }

    @Test
    fun getThinkingReturnsDescription() = runTest {
        val result = engine.getThinking(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        )
        assertTrue(result.description.isNotEmpty())
    }

    @Test
    fun getThinkingDetectsCheck() = runTest {
        // White king in check
        val result = engine.getThinking(
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1"
        )
        assertTrue(result.threats.any { "check" in it.lowercase() },
            "Should detect check as a threat")
    }

    @Test
    fun getThinkingSuggestsDevelopment() = runTest {
        // Opening position — minor pieces undeveloped
        val result = engine.getThinking(
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        )
        assertTrue(
            result.strategicNotes.any { "develop" in it.lowercase() },
            "Should suggest development in opening"
        )
    }

    @Test
    fun evaluateInitialPositionIsNearZero() {
        val board = Board.initial()
        val eval = engine.evaluate(board)
        assertTrue(eval > -1.0 && eval < 1.0,
            "Initial position eval should be near 0, got $eval")
    }

    @Test
    fun evaluatePositionWithExtraMaterial() {
        // White has extra queen
        val board = Board.fromFen("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val eval = engine.evaluate(board)
        assertTrue(eval > 5.0, "Should evaluate extra queen highly, got $eval")
    }

    @Test
    fun getBestLineInCheckmatePositionReturnsEmpty() = runTest {
        // White is checkmated
        val result = engine.getBestLine(
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1",
            depth = 3
        )
        assertTrue(result.bestLine.isEmpty(), "No moves from checkmate position")
    }

    @Test
    fun getBestLineDepthIsRespected() = runTest {
        for (depth in 1..5) {
            val result = engine.getBestLine(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                depth = depth
            )
            assertTrue(result.bestLine.size <= depth,
                "Depth $depth should produce at most $depth moves, got ${result.bestLine.size}")
        }
    }
}
