package chess.game

import chess.core.*
import chess.engine.ChessEngine
import chess.ghost.GhostPreviewManager
import chess.ghost.GhostPreviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class GameMode {
    HUMAN_VS_ENGINE,
    HUMAN_VS_HUMAN,
    COMPUTER_VS_COMPUTER
}

enum class Difficulty(val level: Int) {
    LEVEL_1(1), LEVEL_2(2), LEVEL_3(3), LEVEL_4(4),
    LEVEL_5(5), LEVEL_6(6), LEVEL_7(7), LEVEL_8(8),
    LEVEL_9(9), LEVEL_10(10), LEVEL_11(11), LEVEL_12(12);

    fun label(): String = when (level) {
        1 -> "Clueless"
        2 -> "Beginner"
        3 -> "Novice"
        4 -> "Casual"
        5 -> "Intermediate"
        6 -> "Keen"
        7 -> "Skilled"
        8 -> "Advanced"
        9 -> "Expert"
        10 -> "Master"
        11 -> "Grandmaster"
        12 -> "Ruthless"
        else -> "Level $level"
    }

    val searchDepth: Int get() = when (level) {
        in 1..3 -> 1
        in 4..6 -> 2
        in 7..9 -> 3
        in 10..12 -> 4
        else -> 4
    }

    /** Chance (0.0–1.0) of picking a random legal move instead of the best one */
    val randomMoveProbability: Double get() = when (level) {
        1 -> 0.5
        2 -> 0.35
        3 -> 0.20
        4 -> 0.10
        5 -> 0.05
        else -> 0.0
    }

    fun description(): String {
        val depthDesc = "Search depth $searchDepth"
        val randomPct = (randomMoveProbability * 100).toInt()
        val randomDesc = if (randomPct > 0) "$randomPct% random moves" else "full strength"
        return "$depthDesc, $randomDesc"
    }

    companion object {
        fun fromLevel(n: Int): Difficulty = entries.first { it.level == n.coerceIn(1, 12) }

        /** Migrate old EASY/MEDIUM/HARD names from saved settings */
        fun fromName(name: String): Difficulty = when (name) {
            "EASY" -> LEVEL_3
            "MEDIUM" -> LEVEL_6
            "HARD" -> LEVEL_9
            else -> try { valueOf(name) } catch (_: Exception) { LEVEL_6 }
        }
    }
}

data class GameConfig(
    val mode: GameMode = GameMode.HUMAN_VS_ENGINE,
    val playerColor: PieceColor = PieceColor.WHITE,
    val ghostDepth: Int = 5,
    val showEngineThinking: Boolean = false,
    val difficulty: Difficulty = Difficulty.LEVEL_6,
    val showThreats: Boolean = false,
    val showOpportunities: Boolean = false,
    val dynamicDifficulty: Boolean = false,
    val whiteDifficulty: Difficulty = Difficulty.LEVEL_6,
    val blackDifficulty: Difficulty = Difficulty.LEVEL_6
)

class GameSession(
    private val engine: ChessEngine,
    val config: GameConfig = GameConfig()
) {
    private var gameState = GameState.new()
    private val ghostManager = GhostPreviewManager(engine, config.ghostDepth)

    /** Current difficulty level — may change during dynamic difficulty games */
    var currentDifficulty: Difficulty = config.difficulty
        private set

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
        require(config.mode == GameMode.HUMAN_VS_ENGINE || config.mode == GameMode.COMPUTER_VS_COMPUTER) {
            "Engine moves only in vs engine or computer vs computer mode"
        }
        if (config.mode == GameMode.HUMAN_VS_ENGINE) {
            require(!isPlayerTurn()) { "It's the player's turn" }
        }

        // Pick difficulty based on which side is moving
        val activeDifficulty = when (config.mode) {
            GameMode.COMPUTER_VS_COMPUTER -> {
                if (gameState.board.activeColor == PieceColor.WHITE) config.whiteDifficulty
                else config.blackDifficulty
            }
            else -> currentDifficulty
        }

        val searchDepth = activeDifficulty.searchDepth
        val randomChance = activeDifficulty.randomMoveProbability

        // At lower difficulties, occasionally make a random legal move
        if (randomChance > 0.0 && Random.nextDouble() < randomChance) {
            val legalMoves = MoveGenerator.generateLegalMoves(gameState.board)
            if (legalMoves.isNotEmpty()) {
                gameState = gameState.makeMove(legalMoves.random())
                return gameState
            }
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
            GameMode.HUMAN_VS_HUMAN -> true
            GameMode.HUMAN_VS_ENGINE -> gameState.board.activeColor == config.playerColor
            GameMode.COMPUTER_VS_COMPUTER -> false
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

    /** Update the difficulty mid-game (used by dynamic difficulty) */
    fun setDifficulty(newDifficulty: Difficulty) {
        currentDifficulty = newDifficulty
    }
}
