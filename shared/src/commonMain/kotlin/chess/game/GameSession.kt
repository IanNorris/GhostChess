package chess.game

import chess.core.*
import chess.engine.ChessEngine
import chess.ghost.GhostPreviewManager
import chess.ghost.GhostPreviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GameMode {
    HUMAN_VS_ENGINE,
    HUMAN_VS_HUMAN
}

enum class Difficulty {
    EASY, MEDIUM, HARD;
    fun label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class GameConfig(
    val mode: GameMode = GameMode.HUMAN_VS_ENGINE,
    val playerColor: PieceColor = PieceColor.WHITE,
    val ghostDepth: Int = 5,
    val showEngineThinking: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM
)

class GameSession(
    private val engine: ChessEngine,
    val config: GameConfig = GameConfig()
) {
    private var gameState = GameState.new()
    private val ghostManager = GhostPreviewManager(engine, config.ghostDepth)

    fun getGameState(): GameState = gameState
    fun getGhostState(): GhostPreviewState = ghostManager.getState()

    suspend fun initialize() {
        if (!engine.isReady()) {
            engine.initialize()
        }
    }

    suspend fun makePlayerMove(move: Move): GameState {
        require(isPlayerTurn()) { "Not player's turn" }
        gameState = gameState.makeMove(move)
        return gameState
    }

    suspend fun requestGhostPreview() {
        if (gameState.status == GameStatus.IN_PROGRESS) {
            ghostManager.requestPreview(gameState.board, config.showEngineThinking)
        }
    }

    suspend fun makeEngineMove(): GameState {
        require(config.mode == GameMode.HUMAN_VS_ENGINE) { "Engine moves only in vs engine mode" }
        require(!isPlayerTurn()) { "It's the player's turn" }

        val searchDepth = when (config.difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 1
            Difficulty.HARD -> 2
        }
        val analysis = withContext(Dispatchers.Default) {
            engine.getBestLine(gameState.toFen(), searchDepth)
        }
        if (analysis.bestLine.isNotEmpty()) {
            gameState = gameState.makeMove(analysis.bestLine.first())
        }

        return gameState
    }

    fun isPlayerTurn(): Boolean {
        return when (config.mode) {
            GameMode.HUMAN_VS_HUMAN -> true // Always a player's turn
            GameMode.HUMAN_VS_ENGINE -> gameState.board.activeColor == config.playerColor
        }
    }

    fun undoMove(): GameState {
        gameState = gameState.undoMove()
        ghostManager.dismiss()
        return gameState
    }

    suspend fun acceptGhostLine(): GameState {
        // Accept just dismisses the ghost preview — the game continues
        // from the real position (after the player's move + engine response).
        // The ghost was purely informational ("what would happen").
        ghostManager.dismiss()
        return gameState
    }

    fun dismissGhost(): GhostPreviewState {
        return ghostManager.dismiss()
    }

    fun ghostStepForward() = ghostManager.stepForward()
    fun ghostStepBack() = ghostManager.stepBack()
    fun ghostReset() = ghostManager.reset()
    fun ghostPause() = ghostManager.pause()
    fun ghostResume() = ghostManager.resume()
    fun ghostSetMode(mode: chess.ghost.GhostPreviewMode) = ghostManager.setMode(mode)

    fun legalMoves(): List<Move> = gameState.legalMoves()
}
