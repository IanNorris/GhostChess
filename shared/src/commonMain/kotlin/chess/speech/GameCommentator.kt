package chess.speech

import chess.core.*
import chess.engine.SimpleEngine

class GameCommentator(
    private val speechEngine: SpeechEngine,
    private val commentary: CommentaryGenerator = CommentaryGenerator(),
    private val playerColor: PieceColor = PieceColor.WHITE
) {
    private val moveHistory = mutableListOf<Move>()
    private val evalHistory = mutableListOf<Double>()
    private var previousEval: Double = 0.0
    private var lastDetectedOpening: String? = null
    private var winAnnounced = false
    private val engine = SimpleEngine()
    private var engineInitialized = false

    private suspend fun ensureEngine() {
        if (!engineInitialized) {
            engine.initialize()
            engineInitialized = true
        }
    }

    fun onGameStart(playingAsBlack: Boolean) {
        moveHistory.clear()
        evalHistory.clear()
        previousEval = 0.0
        lastDetectedOpening = null
        winAnnounced = false
        val event = if (playingAsBlack) GameEvent.GameStartedAsBlack else GameEvent.GameStarted
        speak(listOf(event))
    }

    suspend fun onPlayerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        moveHistory.add(move)
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = true)
        val allEvents = events.toMutableList()

        // Opening detection
        val opening = OpeningBook.detectNewOpening(moveHistory)
        if (opening != null && opening.name != lastDetectedOpening) {
            lastDetectedOpening = opening.name
            allEvents.add(GameEvent.OpeningDetected(opening.name))
        }

        // Eval tracking for advantage shifts
        ensureEngine()
        val currentEval = engine.evaluate(boardAfter)
        evalHistory.add(currentEval)

        val evalDelta = currentEval - previousEval
        // Detect significant advantage shift (> 1.5 pawns change)
        if (kotlin.math.abs(evalDelta) > 1.5 && moveHistory.size > 2) {
            val playerLeading = if (playerColor == PieceColor.WHITE) currentEval > 1.0 else currentEval < -1.0
            val wasLeading = if (playerColor == PieceColor.WHITE) previousEval > 1.0 else previousEval < -1.0
            if (playerLeading != wasLeading) {
                allEvents.add(GameEvent.AdvantageShift(playerLeading, evalDelta))
            }
        }

        // Detect guaranteed win (overwhelming eval advantage, only announce once)
        if (!winAnnounced && moveHistory.size > 6) {
            val absEval = kotlin.math.abs(currentEval)
            if (absEval > 8.0) {
                val playerWinning = if (playerColor == PieceColor.WHITE) currentEval > 0 else currentEval < 0
                allEvents.add(GameEvent.WinGuaranteed(playerWinning, currentEval))
                winAnnounced = true
            }
        }

        previousEval = currentEval

        speak(allEvents)
    }

    suspend fun onComputerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        moveHistory.add(move)
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = false)
        val allEvents = events.toMutableList()

        // Opening detection
        val opening = OpeningBook.detectNewOpening(moveHistory)
        if (opening != null && opening.name != lastDetectedOpening) {
            lastDetectedOpening = opening.name
            allEvents.add(GameEvent.OpeningDetected(opening.name))
        }

        // Eval tracking
        ensureEngine()
        val currentEval = engine.evaluate(boardAfter)
        evalHistory.add(currentEval)

        val evalDelta = currentEval - previousEval
        if (kotlin.math.abs(evalDelta) > 1.5 && moveHistory.size > 2) {
            val playerLeading = if (playerColor == PieceColor.WHITE) currentEval > 1.0 else currentEval < -1.0
            val wasLeading = if (playerColor == PieceColor.WHITE) previousEval > 1.0 else previousEval < -1.0
            if (playerLeading != wasLeading) {
                allEvents.add(GameEvent.AdvantageShift(playerLeading, evalDelta))
            }
        }

        if (!winAnnounced && moveHistory.size > 6) {
            val absEval = kotlin.math.abs(currentEval)
            if (absEval > 8.0) {
                val playerWinning = if (playerColor == PieceColor.WHITE) currentEval > 0 else currentEval < 0
                allEvents.add(GameEvent.WinGuaranteed(playerWinning, currentEval))
                winAnnounced = true
            }
        }

        previousEval = currentEval

        speak(allEvents)
    }

    fun onIllegalMoveAttempt(inCheck: Boolean) {
        speak(listOf(GameEvent.IllegalMoveAttempt(inCheck)))
    }

    fun onGameEnd(playerWon: Boolean?, moveCount: Int) {
        if (!speechEngine.enabled) return
        val review = generateReview(playerWon, moveCount)
        if (review != null) {
            speechEngine.speak(review)
        }
    }

    private fun generateReview(playerWon: Boolean?, moveCount: Int): String? {
        if (evalHistory.size < 4) return null

        val openingEvals = evalHistory.take(minOf(10, evalHistory.size))
        val endgameEvals = if (evalHistory.size > 10) evalHistory.drop(evalHistory.size - minOf(10, evalHistory.size / 2)) else emptyList()

        val openingAvg = if (openingEvals.isNotEmpty()) openingEvals.average() else 0.0
        val endgameAvg = if (endgameEvals.isNotEmpty()) endgameEvals.average() else openingAvg

        val openingGood = if (playerColor == PieceColor.WHITE) openingAvg > 0.3 else openingAvg < -0.3
        val endgameGood = if (playerColor == PieceColor.WHITE) endgameAvg > 0.3 else endgameAvg < -0.3

        return when {
            openingGood && !endgameGood -> BanterLines.pick(BanterLines.reviewStrongStart)
            !openingGood && endgameGood -> BanterLines.pick(BanterLines.reviewStrongFinish)
            openingGood && endgameGood -> BanterLines.pick(BanterLines.reviewConsistent)
            else -> BanterLines.pick(BanterLines.reviewStruggled)
        }
    }

    fun onGhostPreviewStart() { }
    fun onGhostAccepted() { }
    fun onGhostDismissed() { }

    fun onMoveUndone() {
        if (moveHistory.isNotEmpty()) moveHistory.removeAt(moveHistory.size - 1)
        speak(listOf(GameEvent.MoveUndone))
    }

    fun onBlunder(evalDrop: Double) {
        speak(listOf(GameEvent.Blunder(evalDrop)))
    }

    fun onGoodMove(evalGain: Double) {
        speak(listOf(GameEvent.GoodMove(evalGain)))
    }

    private fun speak(events: List<GameEvent>) {
        if (!speechEngine.enabled) return
        val text = commentary.generateCommentary(events) ?: return
        speechEngine.speak(text)
    }
}
