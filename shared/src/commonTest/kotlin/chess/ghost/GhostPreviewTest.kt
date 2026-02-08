package chess.ghost

import chess.core.*
import chess.engine.SimpleEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GhostPreviewTest {

    private lateinit var manager: GhostPreviewManager

    @BeforeTest
    fun setup() = runTest {
        val engine = SimpleEngine()
        engine.initialize()
        manager = GhostPreviewManager(engine, lineLength = 3)
    }

    // --- Initial state ---

    @Test
    fun initialStateIsIdle() {
        val state = manager.getState()
        assertEquals(GhostPreviewStatus.IDLE, state.status)
        assertFalse(state.isActive)
    }

    // --- Request preview ---

    @Test
    fun requestPreviewSetsActiveState() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertTrue(state.isActive)
        assertNotEquals(GhostPreviewStatus.IDLE, state.status)
        assertNotEquals(GhostPreviewStatus.LOADING, state.status)
    }

    @Test
    fun requestPreviewPopulatesPredictedLine() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertTrue(state.predictedLine.isNotEmpty(), "Should have predicted moves")
        assertTrue(state.predictedLine.size <= 3, "Should respect depth")
    }

    @Test
    fun requestPreviewStoresOriginalBoard() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertEquals(board.toFen(), state.originalBoard?.toFen())
    }

    @Test
    fun requestPreviewStartsAtStepMinus1() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertEquals(-1, state.currentStepIndex)
    }

    @Test
    fun requestPreviewInAutoPlayMode() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertEquals(GhostPreviewStatus.PLAYING, state.status)
        assertEquals(GhostPreviewMode.AUTO_PLAY, state.mode)
    }

    @Test
    fun requestPreviewWithThinkingEnabled() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board, showThinking = true)
        assertTrue(state.showThinking)
        assertNotNull(state.thinking, "Thinking should be populated when enabled")
        assertTrue(state.thinking!!.description.isNotEmpty())
    }

    @Test
    fun requestPreviewWithoutThinking() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board, showThinking = false)
        assertFalse(state.showThinking)
        assertNull(state.thinking)
    }

    @Test
    fun requestPreviewHasAnalysis() = runTest {
        val board = Board.initial()
        val state = manager.requestPreview(board)
        assertNotNull(state.analysis)
        assertTrue(state.analysis!!.commentary.isNotEmpty())
    }

    // --- Step through ---

    @Test
    fun stepForwardAdvancesIndex() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val state = manager.stepForward()
        assertEquals(0, state.currentStepIndex)
    }

    @Test
    fun stepForwardUpdatesBoard() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val state = manager.stepForward()
        assertNotEquals(board.toFen(), state.boardAtStep?.toFen())
    }

    @Test
    fun stepForwardThenBackReturnsToOriginal() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        manager.stepForward()
        val state = manager.stepBack()
        assertEquals(-1, state.currentStepIndex)
        assertEquals(board.toFen(), state.boardAtStep?.toFen())
    }

    @Test
    fun cannotStepForwardPastEnd() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val line = manager.getState().predictedLine
        // Step to end
        repeat(line.size) { manager.stepForward() }
        assertFalse(manager.getState().canStepForward)
        assertFailsWith<IllegalArgumentException> { manager.stepForward() }
    }

    @Test
    fun cannotStepBackPastBeginning() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        assertFalse(manager.getState().canStepBack)
        assertFailsWith<IllegalArgumentException> { manager.stepBack() }
    }

    @Test
    fun steppingToEndSetsComplete() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val line = manager.getState().predictedLine
        repeat(line.size) { manager.stepForward() }
        assertEquals(GhostPreviewStatus.COMPLETE, manager.getState().status)
    }

    // --- Reset ---

    @Test
    fun resetReturnsToOriginalBoard() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        manager.stepForward()
        manager.stepForward()
        val state = manager.reset()
        assertEquals(-1, state.currentStepIndex)
        assertEquals(board.toFen(), state.boardAtStep?.toFen())
        assertEquals(GhostPreviewStatus.PAUSED, state.status)
    }

    // --- Mode switching ---

    @Test
    fun switchToStepThroughPausesAutoPlay() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        assertEquals(GhostPreviewStatus.PLAYING, manager.getState().status)

        val state = manager.setMode(GhostPreviewMode.STEP_THROUGH)
        assertEquals(GhostPreviewMode.STEP_THROUGH, state.mode)
        assertEquals(GhostPreviewStatus.PAUSED, state.status)
    }

    @Test
    fun requestPreviewInStepThroughMode() = runTest {
        val board = Board.initial()
        manager.setMode(GhostPreviewMode.STEP_THROUGH)
        val state = manager.requestPreview(board)
        assertEquals(GhostPreviewStatus.PAUSED, state.status)
    }

    // --- Pause / Resume ---

    @Test
    fun pauseAndResume() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        assertEquals(GhostPreviewStatus.PLAYING, manager.getState().status)

        manager.pause()
        assertEquals(GhostPreviewStatus.PAUSED, manager.getState().status)

        manager.resume()
        assertEquals(GhostPreviewStatus.PLAYING, manager.getState().status)
    }

    // --- Dismiss ---

    @Test
    fun dismissResetsToIdle() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val state = manager.dismiss()
        assertEquals(GhostPreviewStatus.IDLE, state.status)
        assertFalse(state.isActive)
    }

    // --- Accept ---

    @Test
    fun acceptReturnsAppliedMoves() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        manager.stepForward()
        manager.stepForward()

        val (state, moves) = manager.accept()
        assertEquals(GhostPreviewStatus.IDLE, state.status)
        assertEquals(2, moves.size)
    }

    @Test
    fun acceptWithNoStepsReturnsEmpty() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val (_, moves) = manager.accept()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun acceptedMovesAreLegal() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        val line = manager.getState().predictedLine
        repeat(line.size) { manager.stepForward() }

        val (_, moves) = manager.accept()
        var currentBoard = board
        for (move in moves) {
            val legal = MoveGenerator.generateLegalMoves(currentBoard)
            assertTrue(move in legal, "Accepted move ${move.toAlgebraic()} should be legal")
            currentBoard = currentBoard.makeMove(move)
        }
    }

    // --- Edge cases ---

    @Test
    fun previewFromCheckmatePositionIsEmpty() = runTest {
        val board = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1")
        val state = manager.requestPreview(board)
        assertTrue(state.predictedLine.isEmpty())
    }

    @Test
    fun currentMoveDescriptionWhenActive() = runTest {
        val board = Board.initial()
        manager.requestPreview(board)
        assertEquals("", manager.getState().currentMoveDescription)
        manager.stepForward()
        assertTrue(manager.getState().currentMoveDescription.isNotEmpty())
    }
}
