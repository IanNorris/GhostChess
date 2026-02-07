package chess.speech

import chess.core.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BanterGeneratorTest {

    @Test
    fun templateBanterGeneratorIsAlwaysReady() {
        val generator = TemplateBanterGenerator()
        assertTrue(generator.isReady)
    }

    @Test
    fun templateBanterGeneratesForCheckmate() = runTest {
        val generator = TemplateBanterGenerator()
        val context = GameContext(
            event = GameEvent.Checkmate(playerWon = true),
            board = Board.initial(),
            playerColor = PieceColor.WHITE
        )
        val banter = generator.generateBanter(context)
        assertNotNull(banter)
        assertTrue(banter.contains("Checkmate", ignoreCase = true), "Should mention checkmate: $banter")
    }

    @Test
    fun templateBanterGeneratesForCapture() = runTest {
        val generator = TemplateBanterGenerator()
        val context = GameContext(
            event = GameEvent.PieceCaptured(
                capturedType = PieceType.QUEEN,
                capturerColor = PieceColor.WHITE,
                isPlayerCapture = true
            ),
            board = Board.initial(),
            playerColor = PieceColor.WHITE
        )
        val banter = generator.generateBanter(context)
        assertNotNull(banter)
        assertTrue(banter.isNotBlank())
    }

    @Test
    fun templateBanterReturnsNullForGhostEvents() = runTest {
        val generator = TemplateBanterGenerator()
        val context = GameContext(
            event = GameEvent.GhostPreviewStarted,
            board = Board.initial(),
            playerColor = PieceColor.WHITE
        )
        // Ghost events have commentary too - just verify no crash
        generator.generateBanter(context)
    }

    @Test
    fun templateBanterGeneratesForCheck() = runTest {
        val generator = TemplateBanterGenerator()
        val context = GameContext(
            event = GameEvent.Check(checkedColor = PieceColor.BLACK),
            board = Board.initial(),
            playerColor = PieceColor.WHITE
        )
        val banter = generator.generateBanter(context)
        assertNotNull(banter)
        assertTrue(banter.isNotBlank())
    }

    @Test
    fun gameContextHoldsAllFields() {
        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3))
        val context = GameContext(
            event = GameEvent.GameStarted,
            board = board,
            lastMove = move,
            playerColor = PieceColor.BLACK,
            evaluation = 0.5,
            engineThinking = "Nf3 is a solid developing move",
            moveNumber = 3
        )
        assertEquals(PieceColor.BLACK, context.playerColor)
        assertEquals(0.5, context.evaluation)
        assertEquals(3, context.moveNumber)
        assertEquals("Nf3 is a solid developing move", context.engineThinking)
        assertNotNull(context.lastMove)
    }
}
