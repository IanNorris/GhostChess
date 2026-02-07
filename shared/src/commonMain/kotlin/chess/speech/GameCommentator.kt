package chess.speech

import chess.core.*
import chess.engine.ChessEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Orchestrates speech commentary during gameplay.
 * Call the appropriate method when game events occur.
 * If a BanterGenerator is provided and ready, it generates AI-powered commentary.
 * Otherwise falls back to template-based commentary.
 */
class GameCommentator(
    private val speechEngine: SpeechEngine,
    private val commentary: CommentaryGenerator = CommentaryGenerator(),
    private val playerColor: PieceColor = PieceColor.WHITE,
    private val banterGenerator: BanterGenerator? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val engine: ChessEngine? = null
) {
    private var currentBoard: Board = Board.initial()
    private var previousBoard: Board? = null
    private var lastMove: Move? = null
    private var moveNumber: Int = 0

    fun onGameStart(playingAsBlack: Boolean) {
        moveNumber = 0
        previousBoard = null
        lastMove = null
        // Reset banter generator state between games
        banterGenerator?.let { banter ->
            scope.launch { banter.reset() }
        }
        val event = if (playingAsBlack) GameEvent.GameStartedAsBlack else GameEvent.GameStarted
        speakEvent(event)
    }

    fun onPlayerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        moveNumber++
        previousBoard = boardBefore
        lastMove = move
        currentBoard = boardAfter
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = true)
        speakEvents(events)
    }

    fun onComputerMove(move: Move, boardBefore: Board, boardAfter: Board) {
        previousBoard = boardBefore
        lastMove = move
        currentBoard = boardAfter
        val events = GameEventDetector.detectMoveEvents(move, boardBefore, boardAfter, playerColor, isPlayerMove = false)
        speakEvents(events)
    }

    fun onGhostPreviewStart() {
        // No speech — ghost preview shouldn't interrupt commentary
    }

    fun onGhostAccepted() {
        // No speech for ghost events
    }

    fun onGhostDismissed() {
        // No speech for ghost events
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
            // Use AI commentary for the highest-priority event
            val primaryEvent = events.sortedBy { priorityOf(it) }.first()
            // Snapshot state for the coroutine (avoid reading mutable fields later)
            val boardSnapshot = currentBoard
            val boardBeforeSnapshot = previousBoard
            val moveSnapshot = lastMove
            val moveNum = moveNumber

            scope.launch {
                // Get engine analysis for the current position
                val engineAnalysis = try {
                    engine?.getThinking(boardSnapshot.toFen(), 3)
                } catch (_: Exception) { null }

                val context = GameContext(
                    event = primaryEvent,
                    board = boardSnapshot,
                    boardBefore = boardBeforeSnapshot,
                    lastMove = moveSnapshot,
                    playerColor = playerColor,
                    evaluation = engineAnalysis?.evaluation,
                    engineThinking = engineAnalysis?.description,
                    engineCommentary = engineAnalysis?.let { thought ->
                        buildString {
                            append(thought.description)
                            if (thought.threats.isNotEmpty()) {
                                append(" Threats: ${thought.threats.joinToString(", ")}.")
                            }
                            if (thought.strategicNotes.isNotEmpty()) {
                                append(" ${thought.strategicNotes.joinToString(". ")}.")
                            }
                        }
                    },
                    moveNumber = moveNum
                )
                try {
                    val text = banter.generateBanter(context)
                    if (text != null) {
                        speechEngine.speak(text)
                    } else {
                        val fallbackText = commentary.generateCommentary(events) ?: return@launch
                        speechEngine.speak(fallbackText)
                    }
                } catch (_: Exception) {
                    // If banter generation crashes, fall back to template
                    val fallbackText = commentary.generateCommentary(events)
                    if (fallbackText != null) speechEngine.speak(fallbackText)
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
