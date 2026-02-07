package chess.speech

import chess.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Orchestrates speech commentary during gameplay.
 * Call the appropriate method when game events occur.
 * If a BanterGenerator is provided and ready, it generates AI-powered witty banter.
 * Otherwise falls back to template-based commentary.
 */
class GameCommentator(
    private val speechEngine: SpeechEngine,
    private val commentary: CommentaryGenerator = CommentaryGenerator(),
    private val playerColor: PieceColor = PieceColor.WHITE,
    private val banterGenerator: BanterGenerator? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var currentBoard: Board = Board.initial()
    private var moveNumber: Int = 0

    fun onGameStart(playingAsBlack: Boolean) {
        moveNumber = 0
        val event = if (playingAsBlack) GameEvent.GameStartedAsBlack else GameEvent.GameStarted
        speakEvent(event)
    }

    fun onPlayerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        moveNumber++
        currentBoard = boardAfter
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = true)
        speakEvents(events)
    }

    fun onComputerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        currentBoard = boardAfter
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = false)
        speakEvents(events)
    }

    fun onGhostPreviewStart() {
        speakEvent(GameEvent.GhostPreviewStarted)
    }

    fun onGhostAccepted() {
        speakEvent(GameEvent.GhostAccepted)
    }

    fun onGhostDismissed() {
        speakEvent(GameEvent.GhostDismissed)
    }

    fun onMoveUndone() {
        speakEvent(GameEvent.MoveUndone)
    }

    fun onBlunder(evalDrop: Double) {
        speakEvent(GameEvent.Blunder(evalDrop))
    }

    fun onGoodMove(evalGain: Double) {
        speakEvent(GameEvent.GoodMove(evalGain))
    }

    private fun speakEvent(event: GameEvent) {
        speakEvents(listOf(event))
    }

    private fun speakEvents(events: List<GameEvent>) {
        if (!speechEngine.enabled) return

        val banter = banterGenerator
        if (banter != null && banter.isReady && events.isNotEmpty()) {
            // Use AI banter for the highest-priority event
            val primaryEvent = events.sortedBy { priorityOf(it) }.first()
            scope.launch {
                val context = GameContext(
                    event = primaryEvent,
                    board = currentBoard,
                    playerColor = playerColor,
                    moveNumber = moveNumber
                )
                val text = banter.generateBanter(context)
                if (text != null) {
                    speechEngine.speak(text)
                } else {
                    // Fallback to template
                    val fallbackText = commentary.generateCommentary(events) ?: return@launch
                    speechEngine.speak(fallbackText)
                }
            }
        } else {
            // Template-based commentary
            val text = commentary.generateCommentary(events) ?: return
            speechEngine.speak(text)
        }
    }

    private fun priorityOf(event: GameEvent): Int = when (event) {
        is GameEvent.Checkmate -> 0
        is GameEvent.Stalemate -> 1
        is GameEvent.Check -> 2
        is GameEvent.PieceCaptured -> 3
        is GameEvent.Promotion -> 4
        is GameEvent.Castling -> 5
        is GameEvent.Blunder -> 6
        is GameEvent.GoodMove -> 7
        else -> 10
    }
}
