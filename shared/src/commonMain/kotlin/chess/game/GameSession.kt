package chess.game

import chess.core.*
import chess.engine.ChessEngine
import chess.ghost.GhostPreviewManager
import chess.ghost.GhostPreviewState

enum class GameMode {
    HUMAN_VS_ENGINE,
    HUMAN_VS_HUMAN
}

data class GameConfig(
    val mode: GameMode = GameMode.HUMAN_VS_ENGINE,
    val playerColor: PieceColor = PieceColor.WHITE,
    val ghostDepth: Int = 5,
    val showEngineThinking: Boolean = false
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

        if (gameState.status == GameStatus.IN_PROGRESS) {
            ghostManager.requestPreview(gameState.board, config.showEngineThinking)
        }

        return gameState
    }

    suspend fun makeEngineMove(): GameState {
        require(config.mode == GameMode.HUMAN_VS_ENGINE) { "Engine moves only in vs engine mode" }
        require(!isPlayerTurn()) { "It's the player's turn" }

        val analysis = engine.getBestLine(gameState.toFen(), 1)
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
        val (_, moves) = ghostManager.accept()
        for (move in moves) {
            gameState = gameState.makeMove(move)
        }
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
