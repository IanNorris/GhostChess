package chess.game

import chess.core.*
import chess.engine.SimpleEngine
import chess.ghost.GhostPreviewStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Regression tests for ghost preview accept/dismiss in vs-engine mode.
 * Accept now simply dismisses the ghost preview — the game stays at the
 * real position (after player move + engine response). The ghost is
 * purely informational ("what would happen").
 */
class GhostAcceptRegressionTest {

    private suspend fun createSession(
        mode: GameMode = GameMode.HUMAN_VS_ENGINE,
        playerColor: PieceColor = PieceColor.WHITE
    ): GameSession {
        val engine = SimpleEngine()
        val config = GameConfig(mode = mode, playerColor = playerColor, ghostDepth = 5, showEngineThinking = false)
        val session = GameSession(engine, config)
        session.initialize()
        return session
    }

    @Test
    fun ghostPreviewStartsFromPostEnginePosition() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove() // engine responds
        session.requestGhostPreview()

        val ghost = session.getGhostState()
        assertTrue(ghost.isActive)
        // Ghost originates from position after BOTH moves
        assertEquals(session.getGameState().board, ghost.originalBoard)
    }

    @Test
    fun ghostAcceptInVsEngineDoesNotCrash() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        // Step through a couple of moves
        session.ghostStepForward()
        session.ghostStepForward()

        val historyBefore = session.getGameState().moveHistory.size

        // Accept should NOT throw — and should NOT change move history
        val stateAfter = session.acceptGhostLine()
        assertEquals(historyBefore, stateAfter.moveHistory.size)
    }

    @Test
    fun ghostAcceptDoesNotApplyMoves() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        val stepsToTake = 3
        repeat(stepsToTake) { session.ghostStepForward() }

        val historyBefore = session.getGameState().moveHistory.size
        val boardBefore = session.getGameState().board
        session.acceptGhostLine()
        // Accept is informational-only — no moves applied
        assertEquals(historyBefore, session.getGameState().moveHistory.size)
        assertEquals(boardBefore, session.getGameState().board)
    }

    @Test
    fun ghostAcceptAllMovesDoesNotAdvanceGame() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        // Step through ALL ghost moves
        val ghost = session.getGhostState()
        val totalMoves = ghost.predictedLine.size
        repeat(totalMoves) { session.ghostStepForward() }

        val historyBefore = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        assertEquals(historyBefore, session.getGameState().moveHistory.size)
    }

    @Test
    fun ghostDismissInVsEngineAllowsContinuedPlay() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        session.ghostStepForward()
        session.dismissGhost()

        assertFalse(session.getGhostState().isActive)
        // Should still be able to play — it's white's turn
        assertTrue(session.isPlayerTurn())
        val legalMoves = session.legalMoves()
        assertTrue(legalMoves.isNotEmpty())
    }

    @Test
    fun ghostAcceptThenContinuePlayInVsEngine() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        session.ghostStepForward()
        session.ghostStepForward()
        session.acceptGhostLine()

        // Ghost is dismissed, game is at real position, player can continue
        assertFalse(session.getGhostState().isActive)
        assertEquals(GameStatus.IN_PROGRESS, session.getGameState().status)
        assertTrue(session.isPlayerTurn())
        assertTrue(session.legalMoves().isNotEmpty())
    }

    @Test
    fun ghostPreviewAfterAcceptWorks() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        // First cycle: play, ghost, accept
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.requestGhostPreview()
        session.ghostStepForward()
        session.acceptGhostLine()

        // Should be able to play again (still black's turn since accept didn't apply moves)
        val legalMoves = session.legalMoves()
        assertTrue(legalMoves.isNotEmpty())
        session.makePlayerMove(legalMoves.first())
        session.requestGhostPreview()
        assertTrue(session.getGhostState().isActive)
    }

    @Test
    fun ghostAcceptWithZeroStepsAppliesNothing() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3)))
        session.makeEngineMove()
        session.requestGhostPreview()

        // Don't step forward — accept at step -1
        val historyBefore = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        assertEquals(historyBefore, session.getGameState().moveHistory.size)
    }

    @Test
    fun allOpeningMovesCanTriggerGhostWithoutCrash() = runTest {
        // All 20 legal opening moves for white
        val openingMoves = listOf(
            "a2a3", "a2a4", "b2b3", "b2b4", "c2c3", "c2c4",
            "d2d3", "d2d4", "e2e3", "e2e4", "f2f3", "f2f4",
            "g2g3", "g2g4", "h2h3", "h2h4", "b1a3", "b1c3",
            "g1f3", "g1h3"
        )

        for (moveStr in openingMoves) {
            val session = createSession()
            val move = Move.fromAlgebraic(moveStr)

            // Player moves
            session.makePlayerMove(move)
            // Engine responds
            session.makeEngineMove()
            // Ghost preview from post-engine position
            session.requestGhostPreview()

            val ghost = session.getGhostState()
            assertTrue(ghost.isActive, "Ghost should be active after $moveStr")
            assertTrue(ghost.predictedLine.isNotEmpty(), "Ghost should have moves after $moveStr")

            // Step through all and accept — should never crash, should not change game
            repeat(ghost.predictedLine.size) { session.ghostStepForward() }
            val historyBefore = session.getGameState().moveHistory.size
            session.acceptGhostLine()
            assertEquals(
                historyBefore,
                session.getGameState().moveHistory.size,
                "Accept should not apply moves after $moveStr"
            )
        }
    }

    @Test
    fun ghostBoardReplacesOriginalPiecesNotOverlays() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.requestGhostPreview()

        // Step forward — ghost line starts with engine's predicted black move
        session.ghostStepForward()
        val ghost = session.getGhostState()
        val ghostBoard = ghost.boardAtStep!!
        val realBoard = session.getGameState().board

        // Ghost board should differ from real board
        val movedPiece = ghost.predictedLine[0]
        assertNull(ghostBoard[movedPiece.from], "Piece should be gone from origin on ghost board")
        assertNotNull(ghostBoard[movedPiece.to], "Piece should be at destination on ghost board")
    }

    @Test
    fun humanVsHumanGhostAcceptDoesNotApplyMoves() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.requestGhostPreview()

        session.ghostStepForward()
        session.ghostStepForward()

        val historyBefore = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        // Accept dismisses preview without changing game state
        assertEquals(historyBefore, session.getGameState().moveHistory.size)
        assertFalse(session.getGhostState().isActive)
    }
}
