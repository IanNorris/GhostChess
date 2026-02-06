package chess.game

import chess.core.*
import chess.engine.SimpleEngine
import chess.ghost.GhostPreviewStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Regression tests for ghost preview accept/dismiss in vs-engine mode.
 * Bug: ghost preview was computed from post-player-move board, then engine
 * also moved, so accept tried to replay moves already on the board (e.g. e7e5
 * applied twice), causing IllegalArgumentException.
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

        // This should NOT throw IllegalArgumentException
        val stateAfter = session.acceptGhostLine()
        assertTrue(stateAfter.moveHistory.size > historyBefore)
    }

    @Test
    fun ghostAcceptAppliesCorrectNumberOfMoves() = runTest {
        val session = createSession()
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.makeEngineMove()
        session.requestGhostPreview()

        val stepsToTake = 3
        repeat(stepsToTake) { session.ghostStepForward() }

        val historyBefore = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        val historyAfter = session.getGameState().moveHistory.size
        assertEquals(historyBefore + stepsToTake, historyAfter)
    }

    @Test
    fun ghostAcceptAllMovesInVsEngine() = runTest {
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
        assertEquals(historyBefore + totalMoves, session.getGameState().moveHistory.size)
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

        // Game should still be in progress with legal moves
        assertEquals(GameStatus.IN_PROGRESS, session.getGameState().status)
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

        // Should be able to play again and get another ghost preview
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

            // Step through all and accept — should never crash
            repeat(ghost.predictedLine.size) { session.ghostStepForward() }
            val historyBefore = session.getGameState().moveHistory.size
            session.acceptGhostLine()
            assertTrue(
                session.getGameState().moveHistory.size > historyBefore,
                "Accept should apply moves after $moveStr"
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
        // The ghost move should have moved a piece — the ghost board
        // should show the piece at its new position, not at the old one
        val movedPiece = ghost.predictedLine[0]
        assertNull(ghostBoard[movedPiece.from], "Piece should be gone from origin on ghost board")
        assertNotNull(ghostBoard[movedPiece.to], "Piece should be at destination on ghost board")
    }

    @Test
    fun humanVsHumanGhostAcceptStillWorks() = runTest {
        val session = createSession(mode = GameMode.HUMAN_VS_HUMAN)
        session.makePlayerMove(Move(Square(4, 1), Square(4, 3))) // e2-e4
        session.requestGhostPreview()

        session.ghostStepForward()
        session.ghostStepForward()

        val historyBefore = session.getGameState().moveHistory.size
        session.acceptGhostLine()
        assertEquals(historyBefore + 2, session.getGameState().moveHistory.size)
    }
}
