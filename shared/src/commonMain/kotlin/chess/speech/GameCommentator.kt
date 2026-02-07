package chess.speech

import chess.core.*

/**
 * Orchestrates speech commentary during gameplay.
 * Call the appropriate method when game events occur.
 */
class GameCommentator(
    private val speechEngine: SpeechEngine,
    private val commentary: CommentaryGenerator = CommentaryGenerator(),
    private val playerColor: PieceColor = PieceColor.WHITE
) {
    fun onGameStart(playingAsBlack: Boolean) {
        val event = if (playingAsBlack) GameEvent.GameStartedAsBlack else GameEvent.GameStarted
        speak(listOf(event))
    }

    fun onPlayerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = true)
        speak(events)
    }

    fun onComputerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = false)
        speak(events)
    }

    fun onGhostPreviewStart() {
        speak(listOf(GameEvent.GhostPreviewStarted))
    }

    fun onGhostAccepted() {
        speak(listOf(GameEvent.GhostAccepted))
    }

    fun onGhostDismissed() {
        speak(listOf(GameEvent.GhostDismissed))
    }

    fun onMoveUndone() {
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
