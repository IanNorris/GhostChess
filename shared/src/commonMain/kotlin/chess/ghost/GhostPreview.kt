package chess.ghost

import chess.core.*
import chess.engine.ChessEngine
import chess.engine.EngineAnalysis
import chess.engine.EngineThought
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GhostPreviewMode {
    AUTO_PLAY,
    STEP_THROUGH
}

enum class GhostPreviewStatus {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    COMPLETE
}

data class GhostPreviewState(
    val status: GhostPreviewStatus = GhostPreviewStatus.IDLE,
    val mode: GhostPreviewMode = GhostPreviewMode.AUTO_PLAY,
    val originalBoard: Board? = null,
    val predictedLine: List<Move> = emptyList(),
    val currentStepIndex: Int = -1,
    val boardAtStep: Board? = null,
    val boardBeforeStep: Board? = null,
    val animatingMove: Move? = null,
    val analysis: EngineAnalysis? = null,
    val thinking: EngineThought? = null,
    val showThinking: Boolean = false,
    val autoPlaySpeedMs: Long = 1500
) {
    val isActive: Boolean get() = status != GhostPreviewStatus.IDLE
    val canStepForward: Boolean get() = currentStepIndex < predictedLine.size - 1
    val canStepBack: Boolean get() = currentStepIndex > -1
    val currentMoveDescription: String
        get() = if (currentStepIndex >= 0 && currentStepIndex < predictedLine.size) {
            predictedLine[currentStepIndex].toAlgebraic()
        } else ""
}

class GhostPreviewManager(
    private val engine: ChessEngine,
    private val lineLength: Int = 5
) {
    private var state = GhostPreviewState()

    fun getState(): GhostPreviewState = state

    suspend fun requestPreview(board: Board, showThinking: Boolean = false): GhostPreviewState {
        if (lineLength <= 0) {
            state = GhostPreviewState()
            return state
        }
        state = state.copy(
            status = GhostPreviewStatus.LOADING,
            originalBoard = board,
            showThinking = showThinking
        )

        // Use shallow search depth (2) for responsiveness, but build a longer preview line
        val searchDepth = 2
        val analysis = withContext(Dispatchers.Default) {
            engine.getBestLine(board.toFen(), searchDepth, lineLength)
        }
        val thinking = if (showThinking) withContext(Dispatchers.Default) {
            engine.getThinking(board.toFen(), searchDepth)
        } else null

        state = state.copy(
            status = if (state.mode == GhostPreviewMode.AUTO_PLAY) GhostPreviewStatus.PLAYING
            else GhostPreviewStatus.PAUSED,
            predictedLine = analysis.bestLine,
            currentStepIndex = -1,
            boardAtStep = board,
            analysis = analysis,
            thinking = thinking
        )

        return state
    }

    fun stepForward(): GhostPreviewState {
        require(state.isActive) { "No active preview" }
        require(state.canStepForward) { "Already at end of line" }

        val nextIndex = state.currentStepIndex + 1
        val move = state.predictedLine[nextIndex]
        val boardBefore = state.boardAtStep!!
        val newBoard = boardBefore.makeMove(move)

        state = state.copy(
            currentStepIndex = nextIndex,
            boardBeforeStep = boardBefore,
            boardAtStep = newBoard,
            animatingMove = move,
            status = if (nextIndex >= state.predictedLine.size - 1) GhostPreviewStatus.COMPLETE
            else state.status
        )

        return state
    }

    fun stepBack(): GhostPreviewState {
        require(state.isActive) { "No active preview" }
        require(state.canStepBack) { "Already at beginning" }

        // Replay from original board up to the previous step
        var board = state.originalBoard!!
        val targetIndex = state.currentStepIndex - 1

        for (i in 0..targetIndex) {
            board = board.makeMove(state.predictedLine[i])
        }

        state = state.copy(
            currentStepIndex = targetIndex,
            boardAtStep = board,
            status = if (state.status == GhostPreviewStatus.COMPLETE) GhostPreviewStatus.PAUSED
            else state.status
        )

        return state
    }

    fun reset(): GhostPreviewState {
        state = state.copy(
            currentStepIndex = -1,
            boardAtStep = state.originalBoard,
            status = GhostPreviewStatus.PAUSED
        )
        return state
    }

    fun pause(): GhostPreviewState {
        if (state.status == GhostPreviewStatus.PLAYING) {
            state = state.copy(status = GhostPreviewStatus.PAUSED)
        }
        return state
    }

    fun resume(): GhostPreviewState {
        if (state.status == GhostPreviewStatus.PAUSED && state.canStepForward) {
            state = state.copy(status = GhostPreviewStatus.PLAYING)
        }
        return state
    }

    fun setMode(mode: GhostPreviewMode): GhostPreviewState {
        state = state.copy(mode = mode)
        if (mode == GhostPreviewMode.STEP_THROUGH && state.status == GhostPreviewStatus.PLAYING) {
            state = state.copy(status = GhostPreviewStatus.PAUSED)
        }
        return state
    }

    fun dismiss(): GhostPreviewState {
        state = GhostPreviewState()
        return state
    }

    fun accept(): Pair<GhostPreviewState, List<Move>> {
        val appliedMoves = if (state.currentStepIndex >= 0) {
            state.predictedLine.subList(0, state.currentStepIndex + 1)
        } else {
            emptyList()
        }
        state = GhostPreviewState()
        return state to appliedMoves
    }
}
