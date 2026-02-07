package chess.speech

import chess.core.*
import kotlin.test.*

class CommentaryGeneratorTest {

    private val generator = CommentaryGenerator()

    @Test
    fun checkmateWinProducesCommentary() {
        val events = listOf(GameEvent.Checkmate(playerWon = true))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Checkmate"), "Should mention checkmate: $text")
    }

    @Test
    fun checkmateLoseProducesCommentary() {
        val events = listOf(GameEvent.Checkmate(playerWon = false))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Checkmate"), "Should mention checkmate: $text")
    }

    @Test
    fun stalemateProducesCommentary() {
        val events = listOf(GameEvent.Stalemate)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("draw", ignoreCase = true))
    }

    @Test
    fun checkProducesCommentary() {
        val events = listOf(GameEvent.Check(PieceColor.BLACK))
        val text = generator.generateCommentary(events)
        assertEquals("Check!", text)
    }

    @Test
    fun queenCaptureProducesCommentary() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.QUEEN, PieceColor.WHITE, isPlayerCapture = true)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("queen", ignoreCase = true))
    }

    @Test
    fun rookCaptureProducesCommentary() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.ROOK, PieceColor.WHITE, isPlayerCapture = true)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("rook", ignoreCase = true))
    }

    @Test
    fun minorPieceCaptureIncludesPieceName() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.BISHOP, PieceColor.WHITE, isPlayerCapture = true)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("bishop", ignoreCase = true))
    }

    @Test
    fun pawnCaptureProducesCommentary() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.PAWN, PieceColor.BLACK, isPlayerCapture = false)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Pawn", ignoreCase = true))
    }

    @Test
    fun promotionIncludesPieceName() {
        val events = listOf(GameEvent.Promotion(PieceType.QUEEN))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("queen", ignoreCase = true))
    }

    @Test
    fun castlingProducesCommentary() {
        val events = listOf(GameEvent.Castling)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Castling", ignoreCase = true))
    }

    @Test
    fun blunderProducesWarning() {
        val events = listOf(GameEvent.Blunder(3.0))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("sure", ignoreCase = true))
    }

    @Test
    fun goodMoveProducesEncouragement() {
        val events = listOf(GameEvent.GoodMove(1.5))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("move", ignoreCase = true))
    }

    @Test
    fun greatMoveProducesStrongerEncouragement() {
        val events = listOf(GameEvent.GoodMove(3.0))
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Excellent", ignoreCase = true))
    }

    @Test
    fun gameStartProducesGreeting() {
        val events = listOf(GameEvent.GameStarted)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("luck", ignoreCase = true))
    }

    @Test
    fun gameStartAsBlackMentionsGoingFirst() {
        val events = listOf(GameEvent.GameStartedAsBlack)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("first", ignoreCase = true))
    }

    @Test
    fun ghostPreviewStartProducesCommentary() {
        val events = listOf(GameEvent.GhostPreviewStarted)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
    }

    @Test
    fun ghostAcceptedProducesCommentary() {
        val events = listOf(GameEvent.GhostAccepted)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
    }

    @Test
    fun ghostDismissedProducesCommentary() {
        val events = listOf(GameEvent.GhostDismissed)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
    }

    @Test
    fun moveUndoneProducesCommentary() {
        val events = listOf(GameEvent.MoveUndone)
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("back", ignoreCase = true))
    }

    @Test
    fun checkmateTakesPriorityOverCapture() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.QUEEN, PieceColor.WHITE, true),
            GameEvent.Checkmate(playerWon = true)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertTrue(text.contains("Checkmate", ignoreCase = true), "Checkmate should take priority")
    }

    @Test
    fun checkTakesPriorityOverCapture() {
        val events = listOf(
            GameEvent.PieceCaptured(PieceType.PAWN, PieceColor.WHITE, true),
            GameEvent.Check(PieceColor.BLACK)
        )
        val text = generator.generateCommentary(events)
        assertNotNull(text)
        assertEquals("Check!", text, "Check should take priority over pawn capture")
    }

    @Test
    fun playerMoveAloneProducesNoCommentary() {
        val events = listOf(
            GameEvent.PlayerMoved(
                Move(Square(4, 1), Square(4, 3)),
                Board.initial()
            )
        )
        val text = generator.generateCommentary(events)
        assertNull(text, "Normal moves without special events should be silent")
    }

    @Test
    fun computerMoveAloneProducesNoCommentary() {
        val events = listOf(
            GameEvent.ComputerMoved(
                Move(Square(4, 6), Square(4, 4)),
                Board.initial()
            )
        )
        val text = generator.generateCommentary(events)
        assertNull(text, "Normal computer moves should be silent")
    }
}

class GameEventDetectorTest {

    @Test
    fun detectsCaptureEvent() {
        val board = Board.initial()
        // Set up a position where white pawn captures black pawn
        // e4 is played, then d5 by black, then exd5
        val afterE4 = board.makeMove(Move(Square(4, 1), Square(4, 3)))
        val afterD5 = afterE4.makeMove(Move(Square(3, 6), Square(3, 4)))
        val captureMove = Move(Square(4, 3), Square(3, 4))
        val afterCapture = afterD5.makeMove(captureMove)

        val events = GameEventDetector.detectMoveEvents(
            captureMove, afterD5, afterCapture, PieceColor.WHITE, isPlayerMove = true
        )

        assertTrue(events.any { it is GameEvent.PieceCaptured })
        val capture = events.filterIsInstance<GameEvent.PieceCaptured>().first()
        assertEquals(PieceType.PAWN, capture.capturedType)
        assertTrue(capture.isPlayerCapture)
    }

    @Test
    fun detectsCastling() {
        // Set up position for kingside castling
        val fen = "r1bqk2r/ppppbppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        val board = Board.fromFen(fen)
        val castleMove = Move(Square(4, 0), Square(6, 0))
        val afterCastle = board.makeMove(castleMove)

        val events = GameEventDetector.detectMoveEvents(
            castleMove, board, afterCastle, PieceColor.WHITE, isPlayerMove = true
        )

        assertTrue(events.any { it is GameEvent.Castling })
    }

    @Test
    fun detectsPromotion() {
        val fen = "8/P7/8/8/8/8/8/4K2k w - - 0 1"
        val board = Board.fromFen(fen)
        val promoMove = Move(Square(0, 6), Square(0, 7), promotion = PieceType.QUEEN)
        val afterPromo = board.makeMove(promoMove)

        val events = GameEventDetector.detectMoveEvents(
            promoMove, board, afterPromo, PieceColor.WHITE, isPlayerMove = true
        )

        assertTrue(events.any { it is GameEvent.Promotion })
        val promo = events.filterIsInstance<GameEvent.Promotion>().first()
        assertEquals(PieceType.QUEEN, promo.pieceType)
    }

    @Test
    fun detectsCheck() {
        // White queen on f7 checking black king on e8
        val fen = "rnbqkbnr/pppppQpp/8/8/4P3/8/PPPP1PPP/RNB1KBNR b KQkq - 0 1"
        val board = Board.fromFen(fen)
        assertTrue(MoveGenerator.isInCheck(board, PieceColor.BLACK))
    }

    @Test
    fun normalMoveProducesPlayerMovedEvent() {
        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3))
        val afterMove = board.makeMove(move)

        val events = GameEventDetector.detectMoveEvents(
            move, board, afterMove, PieceColor.WHITE, isPlayerMove = true
        )

        assertTrue(events.any { it is GameEvent.PlayerMoved })
    }

    @Test
    fun computerMoveProducesComputerMovedEvent() {
        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3))
        val afterMove = board.makeMove(move)

        val events = GameEventDetector.detectMoveEvents(
            move, board, afterMove, PieceColor.WHITE, isPlayerMove = false
        )

        assertTrue(events.any { it is GameEvent.ComputerMoved })
    }
}

class StringResolverTest {

    private val resolver = StringResolver("en")

    @Test
    fun resolvesSimpleKey() {
        val text = resolver.resolve(MessageKey.GAME_START)
        assertEquals("Let's play! Good luck.", text)
    }

    @Test
    fun resolvesWithParams() {
        val text = resolver.resolve(MessageKey.CAPTURE_MINOR, mapOf("piece" to "bishop"))
        assertEquals("Got your bishop!", text)
    }

    @Test
    fun unknownLocaleDefaultsToEnglish() {
        val frResolver = StringResolver("fr")
        val text = frResolver.resolve(MessageKey.CHECK)
        assertEquals("Check!", text, "Should fall back to English")
    }

    @Test
    fun allMessageKeysHaveTranslations() {
        for (key in MessageKey.entries) {
            val text = resolver.resolve(key)
            assertTrue(text.isNotEmpty(), "Key $key should have a translation")
        }
    }
}

class GameCommentatorTest {

    private class TestSpeechEngine : SpeechEngine {
        override var enabled: Boolean = true
        val spoken = mutableListOf<String>()
        override fun speak(text: String) { spoken.add(text) }
        override fun stop() {}
    }

    @Test
    fun gameStartSpeaks() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine)
        commentator.onGameStart(playingAsBlack = false)
        assertEquals(1, engine.spoken.size)
        assertTrue(engine.spoken[0].contains("luck", ignoreCase = true))
    }

    @Test
    fun disabledEngineDoesNotSpeak() {
        val engine = TestSpeechEngine()
        engine.enabled = false
        val commentator = GameCommentator(engine)
        commentator.onGameStart(playingAsBlack = false)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun captureMoveSpeaks() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine, playerColor = PieceColor.WHITE)

        // e2-e4, d7-d5, exd5 — capture
        val board = Board.initial()
        val afterE4 = board.makeMove(Move(Square(4, 1), Square(4, 3)))
        val afterD5 = afterE4.makeMove(Move(Square(3, 6), Square(3, 4)))
        val captureMove = Move(Square(4, 3), Square(3, 4))
        val afterCapture = afterD5.makeMove(captureMove)

        commentator.onPlayerMove(captureMove, afterD5, afterCapture)
        assertEquals(1, engine.spoken.size)
        assertTrue(engine.spoken[0].contains("Pawn", ignoreCase = true))
    }

    @Test
    fun normalMoveIsSilent() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine)

        val board = Board.initial()
        val move = Move(Square(4, 1), Square(4, 3))
        val afterMove = board.makeMove(move)

        commentator.onPlayerMove(move, board, afterMove)
        assertTrue(engine.spoken.isEmpty(), "Normal moves should be silent")
    }

    @Test
    fun ghostEventsDoNotSpeak() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine)

        commentator.onGhostPreviewStart()
        commentator.onGhostAccepted()
        commentator.onGhostDismissed()

        // Ghost events should not produce speech (they interrupt gameplay)
        assertEquals(0, engine.spoken.size)
    }

    @Test
    fun moveUndoneSpeaks() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine)
        commentator.onMoveUndone()
        assertEquals(1, engine.spoken.size)
    }

    @Test
    fun blunderSpeaks() {
        val engine = TestSpeechEngine()
        val commentator = GameCommentator(engine)
        commentator.onBlunder(3.0)
        assertEquals(1, engine.spoken.size)
        assertTrue(engine.spoken[0].contains("sure", ignoreCase = true))
    }
}
