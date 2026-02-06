package chess.game

import chess.core.*
import chess.engine.SimpleEngine
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GameSessionTest {

    private suspend fun createSession(
        mode: GameMode = GameMode.HUMAN_VS_ENGINE,
        playerColor: PieceColor = PieceColor.WHITE,
        showThinking: Boolean = false
    ): GameSession {
        val engine = SimpleEngine()
        val config = GameConfig(
            mode = mode,
            playerColor = playerColor,
            ghostDepth = 3,
            showEngineThinking = showThinking
        )
        val session = GameSession(engine, config)
        session.initialize()
        return session
    }

    @Test
    fun newSessionStartsWithInitialPosition() = runTest {
        val session = createSession()
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            session.getGameState().toFen()
        )
    }

    @Test
    fun newSessionStartsInProgress() = runTest {
        val session = createSession()
        assertEquals(GameStatus.IN_PROGRESS, session.getGameState().status)
    }

    @Test
    fun playerCanMoveOnTheirTurn() = runTest {
        val session = createSession(playerColor = PieceColor.WHITE)
        assertTrue(session.isPlayerTurn())
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        session.makePlayerMove(move)
        assertEquals(PieceColor.BLACK, session.getGameState().board.activeColor)
    }

    @Test
    fun playerCannotMoveOnEngineTurn() = runTest {
        val session = createSession(playerColor = PieceColor.WHITE)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        assertFalse(session.isPlayerTurn())
        assertFailsWith<IllegalArgumentException> {
            session.makePlayerMove(Move(Square(4, 6), Square(4, 4)))
        }
    }

    @Test
    fun engineMakesLegalMove() = runTest {
        val session = createSession(playerColor = PieceColor.WHITE)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        val stateAfterEngine = session.makeEngineMove()
        assertEquals(PieceColor.WHITE, stateAfterEngine.board.activeColor)
        assertEquals(2, stateAfterEngine.moveHistory.size)
    }

    @Test
    fun humanVsHumanAlwaysPlayerTurn() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        assertTrue(session.isPlayerTurn())
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        assertTrue(session.isPlayerTurn()) // Still player turn (other human)
    }

    @Test
    fun ghostPreviewTriggeredAfterMove() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        val ghostState = session.getGhostState()
        assertTrue(ghostState.isActive, "Ghost should be active after move")
    }

    @Test
    fun ghostStepThroughWorks() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()

        val s1 = session.ghostStepForward()
        assertEquals(0, s1.currentStepIndex)

        val s2 = session.ghostStepBack()
        assertEquals(-1, s2.currentStepIndex)
    }

    @Test
    fun ghostResetWorks() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        session.ghostStepForward()
        session.ghostStepForward()
        val state = session.ghostReset()
        assertEquals(-1, state.currentStepIndex)
    }

    @Test
    fun ghostDismissReturnsToIdle() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        val state = session.dismissGhost()
        assertEquals(GhostPreviewStatus.IDLE, state.status)
    }

    @Test
    fun ghostAcceptAppliesMoves() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        session.ghostStepForward()
        session.ghostStepForward()

        val beforeAccept = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        val afterAccept = session.getGameState().moveHistory.size
        assertTrue(afterAccept > beforeAccept, "Accepting ghost should apply moves")
    }

    @Test
    fun undoMoveWorks() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        val originalFen = session.getGameState().toFen()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.undoMove()
        assertEquals(originalFen, session.getGameState().toFen())
    }

    @Test
    fun undoMoveDismissesGhost() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        assertTrue(session.getGhostState().isActive)
        session.undoMove()
        assertFalse(session.getGhostState().isActive)
    }

    @Test
    fun legalMovesReturnsCorrectMoves() = runTest {
        val session = createSession()
        assertEquals(20, session.legalMoves().size)
    }

    @Test
    fun ghostPauseAndResume() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        session.ghostPause()
        assertEquals(GhostPreviewStatus.PAUSED, session.getGhostState().status)
        session.ghostResume()
        assertEquals(GhostPreviewStatus.PLAYING, session.getGhostState().status)
    }

    @Test
    fun ghostModeSwitching() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        session.ghostSetMode(GhostPreviewMode.STEP_THROUGH)
        assertEquals(GhostPreviewMode.STEP_THROUGH, session.getGhostState().mode)
        assertEquals(GhostPreviewStatus.PAUSED, session.getGhostState().status)
    }

    @Test
    fun showEngineThinkingOption() = runTest {
        val session = createSession(showThinking = true)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.requestGhostPreview()
        val ghost = session.getGhostState()
        assertTrue(ghost.showThinking)
        assertNotNull(ghost.thinking)
    }

    @Test
    fun engineCannotMoveInHumanVsHuman() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        assertFailsWith<IllegalArgumentException> {
            session.makeEngineMove()
        }
    }
}
