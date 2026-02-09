package chess.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chess.audio.AudioEngine
import chess.audio.GamePhase
import chess.audio.GamePhaseDetector
import chess.audio.NoOpAudioEngine
import chess.audio.SoundEffect
import chess.core.*
import chess.engine.SimpleEngine
import chess.game.Difficulty
import chess.game.DynamicDifficultyManager
import chess.game.EloEstimator
import chess.game.GameConfig
import chess.game.GameMode
import chess.game.GameSession
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.speech.CapturedPiecesTracker
import chess.speech.GameCommentator
import chess.speech.NoOpSpeechEngine
import chess.speech.SpeechEngine
import chess.ui.board.ChessBoard
import chess.ui.ghost.GhostPreviewControls
import chess.ui.theme.ChessColors
import chess.game.GameSummary
import chess.game.GameSummaryGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class NoOpSettingsStore : SettingsStore {
    override fun getString(key: String): String? = null
    override fun putString(key: String, value: String) {}
}

@Composable
fun App(speechEngine: SpeechEngine = NoOpSpeechEngine(), settingsStore: SettingsStore = NoOpSettingsStore(), audioEngine: AudioEngine = NoOpAudioEngine()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChessColors.Background)
                .testTag("app-root")
        ) {
            when (val current = screen) {
                is Screen.Menu -> MenuScreen(
                    onStartGame = { config -> screen = Screen.Game(config) },
                    speechEngine = speechEngine,
                    settingsStore = settingsStore
                )
                is Screen.Game -> GameScreen(
                    config = current.config,
                    speechEngine = speechEngine,
                    audioEngine = audioEngine,
                    settingsStore = settingsStore,
                    onBack = { screen = Screen.Menu }
                )
            }
        }
    }
}

sealed class Screen {
    data object Menu : Screen()
    data class Game(val config: GameConfig) : Screen()
}

@Composable
fun MenuScreen(onStartGame: (GameConfig) -> Unit, speechEngine: SpeechEngine = NoOpSpeechEngine(), settingsStore: SettingsStore = NoOpSettingsStore()) {
    var selectedMode by remember {
        mutableStateOf(
            settingsStore.getString("mode")?.let {
                try { GameMode.valueOf(it) } catch (_: Exception) { GameMode.HUMAN_VS_ENGINE }
            } ?: GameMode.HUMAN_VS_ENGINE
        )
    }
    var playerColor by remember {
        mutableStateOf(
            settingsStore.getString("color")?.let {
                try { PieceColor.valueOf(it) } catch (_: Exception) { PieceColor.WHITE }
            } ?: PieceColor.WHITE
        )
    }
    var showThinking by remember { mutableStateOf(settingsStore.getString("thinking") == "true") }
    var ghostDepth by remember { mutableStateOf(settingsStore.getString("depth")?.toIntOrNull() ?: 5) }
    var difficulty by remember {
        mutableStateOf(
            settingsStore.getString("difficulty")?.let {
                Difficulty.fromName(it)
            } ?: Difficulty.LEVEL_6
        )
    }
    var speechEnabled by remember { mutableStateOf(settingsStore.getString("speech") == "true") }
    var showThreats by remember { mutableStateOf(settingsStore.getString("threats") == "true") }
    var dynamicDifficulty by remember { mutableStateOf(settingsStore.getString("dynamic") == "true") }
    var cvcWhiteDifficulty by remember {
        mutableStateOf(settingsStore.getString("cvc_white")?.let { Difficulty.fromName(it) } ?: Difficulty.LEVEL_6)
    }
    var cvcBlackDifficulty by remember {
        mutableStateOf(settingsStore.getString("cvc_black")?.let { Difficulty.fromName(it) } ?: Difficulty.LEVEL_6)
    }

    val playerElo = remember {
        settingsStore.getString("elo")?.toIntOrNull() ?: EloEstimator.DEFAULT_ELO
    }

    fun saveSettings() {
        settingsStore.putString("mode", selectedMode.name)
        settingsStore.putString("color", playerColor.name)
        settingsStore.putString("thinking", showThinking.toString())
        settingsStore.putString("depth", ghostDepth.toString())
        settingsStore.putString("difficulty", difficulty.name)
        settingsStore.putString("speech", speechEnabled.toString())
        settingsStore.putString("threats", showThreats.toString())
        settingsStore.putString("dynamic", dynamicDifficulty.toString())
        settingsStore.putString("cvc_white", cvcWhiteDifficulty.name)
        settingsStore.putString("cvc_black", cvcBlackDifficulty.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("menu-screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "♔ Ghost Chess",
            color = ChessColors.OnSurface,
            fontSize = 28.sp,
            modifier = Modifier.testTag("app-title")
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Game mode selector — above the two columns
        Text("Game Mode", color = ChessColors.OnSurface, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        val chipColors = FilterChipDefaults.filterChipColors(
            labelColor = ChessColors.OnSurface,
            selectedLabelColor = ChessColors.OnSurface,
            selectedContainerColor = ChessColors.Primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMode == GameMode.HUMAN_VS_ENGINE,
                onClick = { selectedMode = GameMode.HUMAN_VS_ENGINE; saveSettings() },
                label = { Text("🤖 vs Computer") },
                colors = chipColors,
                modifier = Modifier.testTag("mode-vs-engine")
            )
            FilterChip(
                selected = selectedMode == GameMode.HUMAN_VS_HUMAN,
                onClick = { selectedMode = GameMode.HUMAN_VS_HUMAN; saveSettings() },
                label = { Text("👤 vs Human") },
                colors = chipColors,
                modifier = Modifier.testTag("mode-vs-human")
            )
            FilterChip(
                selected = selectedMode == GameMode.COMPUTER_VS_COMPUTER,
                onClick = { selectedMode = GameMode.COMPUTER_VS_COMPUTER; saveSettings() },
                label = { Text("🤖🤖") },
                colors = chipColors,
                modifier = Modifier.testTag("mode-cvc")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Two-column layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column: Game setup
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Player color (vs engine only)
                if (selectedMode == GameMode.HUMAN_VS_ENGINE) {
                    Text("Play as", color = ChessColors.OnSurface, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = playerColor == PieceColor.WHITE,
                            onClick = { playerColor = PieceColor.WHITE; saveSettings() },
                            label = { Text("White ♔") },
                            colors = chipColors,
                            modifier = Modifier.testTag("color-white")
                        )
                        FilterChip(
                            selected = playerColor == PieceColor.BLACK,
                            onClick = { playerColor = PieceColor.BLACK; saveSettings() },
                            label = { Text("Black ♚") },
                            colors = chipColors,
                            modifier = Modifier.testTag("color-black")
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Difficulty
                    Text("Difficulty: ${difficulty.label()}", color = ChessColors.OnSurface, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = difficulty.level.toFloat(),
                        onValueChange = { difficulty = Difficulty.fromLevel(it.toInt()); saveSettings() },
                        valueRange = 1f..12f,
                        steps = 10,
                        enabled = !dynamicDifficulty,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("difficulty-slider")
                    )

                    // Win/loss for this difficulty
                    val wl = settingsStore.getString("wl_${difficulty.name}")?.split(":") ?: listOf("0", "0")
                    val wins = wl.getOrNull(0)?.toIntOrNull() ?: 0
                    val losses = wl.getOrNull(1)?.toIntOrNull() ?: 0
                    Text(
                        text = "W: $wins  L: $losses",
                        color = ChessColors.OnSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )

                    // Dynamic difficulty toggle
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dynamic difficulty", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = dynamicDifficulty,
                            onCheckedChange = { dynamicDifficulty = it; saveSettings() },
                            modifier = Modifier.testTag("dynamic-toggle")
                        )
                    }

                    // ELO display
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your rating: $playerElo — ${EloEstimator.skillLabel(playerElo)}",
                        color = ChessColors.Accent,
                        fontSize = 14.sp
                    )
                }

                // CvC difficulty sliders
                if (selectedMode == GameMode.COMPUTER_VS_COMPUTER) {
                    Text("White: Level ${cvcWhiteDifficulty.level} — ${cvcWhiteDifficulty.label()}", color = ChessColors.OnSurface, fontSize = 14.sp)
                    Slider(
                        value = cvcWhiteDifficulty.level.toFloat(),
                        onValueChange = { cvcWhiteDifficulty = Difficulty.fromLevel(it.toInt()); saveSettings() },
                        valueRange = 1f..12f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Black: Level ${cvcBlackDifficulty.level} — ${cvcBlackDifficulty.label()}", color = ChessColors.OnSurface, fontSize = 14.sp)
                    Slider(
                        value = cvcBlackDifficulty.level.toFloat(),
                        onValueChange = { cvcBlackDifficulty = Difficulty.fromLevel(it.toInt()); saveSettings() },
                        valueRange = 1f..12f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ghost depth
                Text("Preview depth: $ghostDepth moves", color = ChessColors.OnSurface, fontSize = 14.sp)
                Slider(
                    value = ghostDepth.toFloat(),
                    onValueChange = { ghostDepth = it.toInt(); saveSettings() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("depth-slider")
                )
            }

            // Right column: Toggles
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Options", color = ChessColors.OnSurface, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Coach mode toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("thinking-toggle-row")
                ) {
                    Text("Enable coach", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = showThinking,
                        onCheckedChange = { showThinking = it; saveSettings() },
                        modifier = Modifier.testTag("thinking-toggle")
                    )
                }

                // Speech toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("speech-toggle-row")
                ) {
                    Text("Speech commentary", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = speechEnabled,
                        onCheckedChange = { speechEnabled = it; saveSettings() },
                        modifier = Modifier.testTag("speech-toggle")
                    )
                }

                // Show threats toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("threats-toggle-row")
                ) {
                    Text("Highlight threats", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = showThreats,
                        onCheckedChange = { showThreats = it; saveSettings() },
                        modifier = Modifier.testTag("threats-toggle")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                speechEngine.enabled = speechEnabled
                onStartGame(
                    GameConfig(
                        mode = selectedMode,
                        playerColor = playerColor,
                        ghostDepth = ghostDepth,
                        showEngineThinking = showThinking,
                        difficulty = difficulty,
                        showThreats = showThreats,
                        dynamicDifficulty = dynamicDifficulty,
                        whiteDifficulty = cvcWhiteDifficulty,
                        blackDifficulty = cvcBlackDifficulty
                    )
                )
            },
            modifier = Modifier.testTag("start-game-btn"),
            colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary)
        ) {
            Text("Start Game", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GameScreen(config: GameConfig, speechEngine: SpeechEngine = NoOpSpeechEngine(), audioEngine: AudioEngine = NoOpAudioEngine(), settingsStore: SettingsStore = NoOpSettingsStore(), onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { SimpleEngine() }
    val session = remember { GameSession(engine, config) }
    val commentator = remember { GameCommentator(speechEngine, playerColor = config.playerColor) }
    val capturedTracker = remember { CapturedPiecesTracker() }

    var gameState by remember { mutableStateOf(session.getGameState()) }
    var ghostState by remember { mutableStateOf(session.getGhostState()) }
    var selectedSquare by remember { mutableStateOf<Square?>(null) }
    var legalMovesForSelected by remember { mutableStateOf<List<Move>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var gamePaused by remember { mutableStateOf(false) }
    var engineThinking by remember { mutableStateOf(false) }
    var engineAnimMove by remember { mutableStateOf<Move?>(null) }
    var boardBeforeEngineMove by remember { mutableStateOf<Board?>(null) }
    var pendingMoveSound by remember { mutableStateOf<SoundEffect?>(null) }
    var whiteElapsedSecs by remember { mutableIntStateOf(0) }
    var blackElapsedSecs by remember { mutableIntStateOf(0) }
    var capturedState by remember { mutableStateOf(capturedTracker.getState()) }
    var eloMessage by remember { mutableStateOf<String?>(null) }
    val dynamicManager = remember {
        if (config.dynamicDifficulty && config.mode == GameMode.HUMAN_VS_ENGINE) {
            DynamicDifficultyManager(engine, config.difficulty)
        } else null
    }

    // Compute threatened squares when showThreats is enabled
    val threatSquares = remember(gameState, config.showThreats) {
        if (!config.showThreats) emptySet()
        else {
            val board = gameState.board
            val playerColor = config.playerColor
            val opponentColor = playerColor.opposite()
            val threatened = mutableSetOf<Square>()
            for ((sq, piece) in board.allPieces(playerColor)) {
                if (piece.type == PieceType.KING) continue
                if (MoveGenerator.isSquareAttacked(board, sq, opponentColor)) {
                    threatened.add(sq)
                }
            }
            threatened
        }
    }
    val vulnerableSquares = remember(gameState, config.showThreats) {
        if (!config.showThreats) emptySet()
        else {
            val board = gameState.board
            val playerColor = config.playerColor
            val opponentColor = playerColor.opposite()
            val vulnerable = mutableSetOf<Square>()
            for ((sq, piece) in board.allPieces(opponentColor)) {
                if (piece.type == PieceType.KING) continue
                val attacked = MoveGenerator.isSquareAttacked(board, sq, playerColor)
                val defended = MoveGenerator.isSquareAttacked(board, sq, opponentColor)
                if (attacked && !defended) {
                    vulnerable.add(sq)
                }
            }
            vulnerable
        }
    }

    // Compute game summary when enabled
    val gameSummary = remember(gameState, config.showEngineThinking) {
        if (!config.showEngineThinking) null
        else GameSummaryGenerator.generate(gameState.board, config.playerColor, engine)
    }

    // Move timer — only counts during current player's turn (human only)
    LaunchedEffect(gamePaused, gameState.status, gameState.board.activeColor) {
        if (!gamePaused && gameState.status == GameStatus.IN_PROGRESS && initialized) {
            val activeColor = gameState.board.activeColor
            val isHumanTurn = when (config.mode) {
                GameMode.HUMAN_VS_HUMAN -> true
                GameMode.HUMAN_VS_ENGINE -> activeColor == config.playerColor
                GameMode.COMPUTER_VS_COMPUTER -> false
            }
            if (isHumanTurn) {
                while (true) {
                    delay(1000)
                    if (!gamePaused) {
                        if (activeColor == PieceColor.WHITE) whiteElapsedSecs++
                        else blackElapsedSecs++
                    }
                }
            }
        }
    }

    // Initialize engine
    LaunchedEffect(Unit) {
        session.initialize()
        initialized = true
        commentator.onGameStart(config.playerColor == PieceColor.BLACK)
        audioEngine.playSound(SoundEffect.GAME_START)
        audioEngine.setMusicPhase(GamePhase.OPENING)
        if (config.mode == GameMode.HUMAN_VS_ENGINE &&
            config.playerColor == PieceColor.BLACK
        ) {
            engineThinking = true
            val boardBefore = session.getGameState().board
            session.makeEngineMove()
            engineThinking = false
            val engineMove = session.getGameState().moveHistory.last()
            // Trigger animation before updating gameState
            boardBeforeEngineMove = boardBefore
            engineAnimMove = engineMove
            gameState = session.getGameState()
            ghostState = session.getGhostState()
            // Track engine capture
            val engineCapturedPiece = boardBefore[engineMove.to]
            if (engineCapturedPiece != null) {
                capturedTracker.onCapture(engineCapturedPiece.type, boardBefore.activeColor)
                capturedState = capturedTracker.getState()
            } else if (engineMove.to == boardBefore.enPassantTarget && boardBefore[engineMove.from]?.type == PieceType.PAWN) {
                capturedTracker.onCapture(PieceType.PAWN, boardBefore.activeColor)
                capturedState = capturedTracker.getState()
            }
            commentator.onComputerMove(engineMove, boardBefore, gameState.board)
            pendingMoveSound = SoundEffect.MOVE
        }

        // Computer vs Computer: auto-play loop
        if (config.mode == GameMode.COMPUTER_VS_COMPUTER) {
            while (session.getGameState().status == GameStatus.IN_PROGRESS) {
                delay(800)
                val boardBefore = session.getGameState().board
                session.makeEngineMove()
                val lastMove = session.getGameState().moveHistory.last()

                val cap = boardBefore[lastMove.to]
                if (cap != null) {
                    capturedTracker.onCapture(cap.type, boardBefore.activeColor)
                    capturedState = capturedTracker.getState()
                } else if (lastMove.to == boardBefore.enPassantTarget && boardBefore[lastMove.from]?.type == PieceType.PAWN) {
                    capturedTracker.onCapture(PieceType.PAWN, boardBefore.activeColor)
                    capturedState = capturedTracker.getState()
                }

                boardBeforeEngineMove = boardBefore
                engineAnimMove = lastMove
                pendingMoveSound = detectMoveSound(lastMove, boardBefore, session.getGameState().board)
                gameState = session.getGameState()

                val moveCount = session.getGameState().moveHistory.size
                audioEngine.setMusicPhase(GamePhaseDetector.detect(gameState.board, moveCount))
            }
            val cvcStatus = gameState.status
            if (cvcStatus == GameStatus.DRAW) audioEngine.playSound(SoundEffect.DRAW)
            else audioEngine.playSound(SoundEffect.CHECKMATE)
            audioEngine.stopAll()
        }
    }

    // Auto-play ghost animation
    LaunchedEffect(ghostState.status) {
        if (ghostState.status == chess.ghost.GhostPreviewStatus.PLAYING) {
            while (ghostState.canStepForward &&
                ghostState.status == chess.ghost.GhostPreviewStatus.PLAYING
            ) {
                delay(ghostState.autoPlaySpeedMs)
                ghostState = session.ghostStepForward()
            }
        }
    }

    fun onSquareClick(square: Square) {
        if (!initialized || !session.isPlayerTurn()) return
        if (gameState.status != GameStatus.IN_PROGRESS) return
        if (gamePaused || engineThinking) return

        val piece = gameState.board[square]

        if (selectedSquare != null) {
            val move = legalMovesForSelected.find { it.to == square }
            if (move != null) {
                scope.launch {
                    val boardBeforePlayer = session.getGameState().board
                    gameState = session.makePlayerMove(move)

                    // Track player capture
                    val playerCapturedPiece = boardBeforePlayer[move.to]
                    if (playerCapturedPiece != null) {
                        capturedTracker.onCapture(playerCapturedPiece.type, boardBeforePlayer.activeColor)
                    } else if (move.to == boardBeforePlayer.enPassantTarget && boardBeforePlayer[move.from]?.type == PieceType.PAWN) {
                        capturedTracker.onCapture(PieceType.PAWN, boardBeforePlayer.activeColor)
                    }
                    capturedState = capturedTracker.getState()

                    commentator.onPlayerMove(move, boardBeforePlayer, gameState.board)
                    audioEngine.playSound(detectMoveSound(move, boardBeforePlayer, gameState.board))

                    val moveCount = session.getGameState().moveHistory.size
                    audioEngine.setMusicPhase(GamePhaseDetector.detect(gameState.board, moveCount))
                    selectedSquare = null
                    legalMovesForSelected = emptyList()

                    if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                        gameState.status == GameStatus.IN_PROGRESS &&
                        !session.isPlayerTurn()
                    ) {
                        engineThinking = true
                        delay(500)

                        // Dynamic difficulty: evaluate player's move quality while spinner is shown
                        if (dynamicManager != null) {
                            dynamicManager.recordPlayerMove(boardBeforePlayer, session.getGameState().toFen())
                            session.setDifficulty(dynamicManager.currentLevel)
                        }

                        val boardBeforeEngine = session.getGameState().board
                        session.makeEngineMove()
                        engineThinking = false
                        val engineMove = session.getGameState().moveHistory.last()
                        // Trigger animation before updating gameState
                        boardBeforeEngineMove = boardBeforeEngine
                        engineAnimMove = engineMove
                        gameState = session.getGameState()
                        // Track engine capture
                        val engineCapturedPiece = boardBeforeEngine[engineMove.to]
                        if (engineCapturedPiece != null) {
                            capturedTracker.onCapture(engineCapturedPiece.type, boardBeforeEngine.activeColor)
                        } else if (engineMove.to == boardBeforeEngine.enPassantTarget && boardBeforeEngine[engineMove.from]?.type == PieceType.PAWN) {
                            capturedTracker.onCapture(PieceType.PAWN, boardBeforeEngine.activeColor)
                        }
                        capturedState = capturedTracker.getState()
                        commentator.onComputerMove(engineMove, boardBeforeEngine, gameState.board)
                        pendingMoveSound = detectMoveSound(engineMove, boardBeforeEngine, gameState.board)
                        val engineMoveCount = session.getGameState().moveHistory.size
                        audioEngine.setMusicPhase(GamePhaseDetector.detect(gameState.board, engineMoveCount))
                    }

                    // Check for game end
                    val status = gameState.status
                    if (status != GameStatus.IN_PROGRESS && config.mode == GameMode.HUMAN_VS_ENGINE) {
                        val playerWon = when {
                            status == GameStatus.DRAW -> null
                            status == GameStatus.WHITE_WINS && config.playerColor == PieceColor.WHITE -> true
                            status == GameStatus.BLACK_WINS && config.playerColor == PieceColor.BLACK -> true
                            status == GameStatus.WHITE_WINS || status == GameStatus.BLACK_WINS -> false
                            else -> null
                        }

                        // Record win/loss
                        if (playerWon != null) {
                            val wlKey = "wl_${config.difficulty.name}"
                            val wl = settingsStore.getString(wlKey)?.split(":") ?: listOf("0", "0")
                            val w = (wl.getOrNull(0)?.toIntOrNull() ?: 0) + if (playerWon) 1 else 0
                            val l = (wl.getOrNull(1)?.toIntOrNull() ?: 0) + if (!playerWon) 1 else 0
                            settingsStore.putString(wlKey, "$w:$l")
                        }

                        // Update ELO
                        val eloResult = when {
                            status == GameStatus.DRAW -> 0.5
                            playerWon == true -> 1.0
                            else -> 0.0
                        }
                        val currentElo = settingsStore.getString("elo")?.toIntOrNull() ?: EloEstimator.DEFAULT_ELO
                        val opponentElo = EloEstimator.engineElo(config.difficulty)
                        val newElo = EloEstimator.calculateNewElo(currentElo, opponentElo, eloResult)
                        settingsStore.putString("elo", newElo.toString())
                        val eloChange = newElo - currentElo
                        val changeStr = if (eloChange >= 0) "+$eloChange" else "$eloChange"
                        eloMessage = "Rating: $newElo ($changeStr) — ${EloEstimator.skillLabel(newElo)}"

                        commentator.onGameEnd(playerWon, gameState.moveHistory.size)
                        if (gameState.status == GameStatus.DRAW) {
                            audioEngine.playSound(SoundEffect.DRAW)
                        } else {
                            audioEngine.playSound(SoundEffect.CHECKMATE)
                        }
                        audioEngine.stopAll()
                    }

                    session.requestGhostPreview()
                    ghostState = session.getGhostState()
                    commentator.onGhostPreviewStart()
                }
                return
            }
        }

        if (piece != null && piece.color == gameState.board.activeColor) {
            selectedSquare = square
            legalMovesForSelected = session.legalMoves().filter { it.from == square }
        } else {
            if (selectedSquare != null && piece?.color != gameState.board.activeColor) {
                val inCheck = MoveGenerator.isInCheck(gameState.board, gameState.board.activeColor)
                commentator.onIllegalMoveAttempt(inCheck)
                audioEngine.playSound(SoundEffect.ILLEGAL)
            }
            selectedSquare = null
            legalMovesForSelected = emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 8.dp)
                .testTag("game-screen")
        ) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                // Landscape: board on left, controls on right
                Row(modifier = Modifier.fillMaxSize()) {
                    // Board constrained to available height
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .padding(end = 8.dp)
                    ) {
                        ChessBoard(
                            board = gameState.board,
                            selectedSquare = selectedSquare,
                            legalMoves = legalMovesForSelected,
                            ghostState = ghostState,
                            flipped = config.playerColor == PieceColor.BLACK,
                            threatSquares = threatSquares,
                            vulnerableSquares = vulnerableSquares,
                            engineAnimMove = engineAnimMove,
                            boardBeforeEngineMove = boardBeforeEngineMove,
                            onEngineAnimDone = { engineAnimMove = null; boardBeforeEngineMove = null },
                            onAnimLand = { pendingMoveSound?.let { audioEngine.playSound(it) }; pendingMoveSound = null },
                            onSquareClick = ::onSquareClick,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (engineThinking) {
                            EngineThinkingOverlay()
                        }
                    }

                    // Right panel with controls
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(start = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top bar (pause/undo)
                        TopBar(config, gameState, session, commentator, whiteElapsedSecs, blackElapsedSecs,
                            dynamicLevel = dynamicManager?.currentLevel,
                            eloMessage = eloMessage,
                            onPause = { gamePaused = true },
                            onUndo = {
                                fun undoOneMove() {
                                    val gs = session.getGameState()
                                    if (gs.moveHistory.isEmpty()) return
                                    val lastMove = gs.moveHistory.last()
                                    val board = gs.board
                                    val moverColor = board.activeColor.opposite()
                                    if (lastMove.isEnPassant) {
                                        capturedTracker.undoCapture(PieceType.PAWN, moverColor)
                                    }
                                    session.undoMove()
                                    val boardAfterUndo = session.getGameState().board
                                    val restoredPiece = boardAfterUndo[lastMove.to]
                                    if (restoredPiece != null && restoredPiece.color != moverColor) {
                                        capturedTracker.undoCapture(restoredPiece.type, moverColor)
                                    }
                                }
                                undoOneMove()
                                if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                                    session.getGameState().moveHistory.isNotEmpty() &&
                                    !session.isPlayerTurn()
                                ) { undoOneMove() }
                                commentator.onMoveUndone()
                                audioEngine.playSound(SoundEffect.UNDO)
                                dynamicManager?.markUndone()
                                gameState = session.getGameState()
                                ghostState = session.getGhostState()
                                capturedState = capturedTracker.getState()
                                selectedSquare = null
                                legalMovesForSelected = emptyList()
                            }
                        )
                        // Material balance
                        CapturedPiecesDisplay(capturedState)
                        // Scrollable area for ghost controls + coaching
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GhostControls(session, scope, config, commentator, ghostState,
                                onStateChange = { gs, ghost ->
                                    gameState = gs; ghostState = ghost
                                    selectedSquare = null; legalMovesForSelected = emptyList()
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GameSummaryPanel(summary = gameSummary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                // Portrait: vertical layout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pause/Undo buttons at the top
                    TopBar(config, gameState, session, commentator, whiteElapsedSecs, blackElapsedSecs,
                        dynamicLevel = dynamicManager?.currentLevel,
                        eloMessage = eloMessage,
                        onPause = { gamePaused = true },
                        onUndo = {
                            fun undoOneMove() {
                                val gs = session.getGameState()
                                if (gs.moveHistory.isEmpty()) return
                                val lastMove = gs.moveHistory.last()
                                val board = gs.board
                                val moverColor = board.activeColor.opposite()
                                if (lastMove.isEnPassant) {
                                    capturedTracker.undoCapture(PieceType.PAWN, moverColor)
                                }
                                session.undoMove()
                                val boardAfterUndo = session.getGameState().board
                                val restoredPiece = boardAfterUndo[lastMove.to]
                                if (restoredPiece != null && restoredPiece.color != moverColor) {
                                    capturedTracker.undoCapture(restoredPiece.type, moverColor)
                                }
                            }
                            undoOneMove()
                            if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                                session.getGameState().moveHistory.isNotEmpty() &&
                                !session.isPlayerTurn()
                            ) { undoOneMove() }
                            commentator.onMoveUndone()
                            audioEngine.playSound(SoundEffect.UNDO)
                            dynamicManager?.markUndone()
                            gameState = session.getGameState()
                            ghostState = session.getGhostState()
                            capturedState = capturedTracker.getState()
                            selectedSquare = null
                            legalMovesForSelected = emptyList()
                        }
                    )
                    // Material balance below buttons
                    CapturedPiecesDisplay(capturedState, modifier = Modifier.padding(horizontal = 8.dp))
                    // Chess board
                    Box {
                        ChessBoard(
                            board = gameState.board,
                            selectedSquare = selectedSquare,
                            legalMoves = legalMovesForSelected,
                            ghostState = ghostState,
                            flipped = config.playerColor == PieceColor.BLACK,
                            threatSquares = threatSquares,
                            vulnerableSquares = vulnerableSquares,
                            engineAnimMove = engineAnimMove,
                            boardBeforeEngineMove = boardBeforeEngineMove,
                            onEngineAnimDone = { engineAnimMove = null; boardBeforeEngineMove = null },
                            onAnimLand = { pendingMoveSound?.let { audioEngine.playSound(it) }; pendingMoveSound = null },
                            onSquareClick = ::onSquareClick,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                        if (engineThinking) {
                            EngineThinkingOverlay()
                        }
                    }
                    // Scrollable area for coaching + ghost controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GhostControls(session, scope, config, commentator, ghostState,
                            onStateChange = { gs, ghost ->
                                gameState = gs; ghostState = ghost
                                selectedSquare = null; legalMovesForSelected = emptyList()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GameSummaryPanel(summary = gameSummary, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Pause modal overlay
        if (gamePaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChessColors.Background.copy(alpha = 0.8f))
                    .testTag("pause-modal"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(ChessColors.Surface, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Game Paused", color = ChessColors.OnSurface, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Sound effects toggle
                    var sfxOn by remember { mutableStateOf(audioEngine.sfxEnabled) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(200.dp).testTag("sfx-toggle-row")
                    ) {
                        Text("Sound effects", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = sfxOn,
                            onCheckedChange = { sfxOn = it; audioEngine.sfxEnabled = it },
                            modifier = Modifier.testTag("sfx-toggle")
                        )
                    }

                    // Background music toggle
                    var musicOn by remember { mutableStateOf(audioEngine.musicEnabled) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(200.dp).testTag("music-toggle-row")
                    ) {
                        Text("Background music", color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = musicOn,
                            onCheckedChange = { musicOn = it; audioEngine.musicEnabled = it },
                            modifier = Modifier.testTag("music-toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { gamePaused = false },
                        modifier = Modifier.width(200.dp).testTag("resume-btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary)
                    ) {
                        Text("Resume", fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            gamePaused = false
                            audioEngine.stopAll()
                            onBack()
                        },
                        modifier = Modifier.width(200.dp).testTag("quit-btn")
                    ) {
                        Text("Return to Menu", fontSize = 16.sp, color = ChessColors.OnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    config: GameConfig,
    gameState: chess.core.GameState,
    session: GameSession,
    commentator: GameCommentator,
    whiteElapsedSecs: Int,
    blackElapsedSecs: Int,
    dynamicLevel: Difficulty? = null,
    eloMessage: String? = null,
    onPause: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPause,
            modifier = Modifier.testTag("pause-btn").height(44.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("⏸ Pause", color = ChessColors.OnSurface, fontSize = 15.sp)
        }

        val timerText = if (config.mode == GameMode.HUMAN_VS_HUMAN) {
            val wm = whiteElapsedSecs / 60; val ws = whiteElapsedSecs % 60
            val bm = blackElapsedSecs / 60; val bs = blackElapsedSecs % 60
            "⬜ $wm:${ws.toString().padStart(2, '0')}  ⬛ $bm:${bs.toString().padStart(2, '0')}"
        } else {
            val playerSecs = if (config.playerColor == PieceColor.WHITE) whiteElapsedSecs else blackElapsedSecs
            val mins = playerSecs / 60; val secs = playerSecs % 60
            "$mins:${secs.toString().padStart(2, '0')}"
        }
        Text(text = timerText, color = ChessColors.Accent, fontSize = 14.sp, modifier = Modifier.testTag("move-timer"))

        Text(
            text = when (gameState.status) {
                GameStatus.IN_PROGRESS -> {
                    val turn = if (gameState.board.activeColor == PieceColor.WHITE) "White" else "Black"
                    val dynSuffix = if (dynamicLevel != null) " (Lvl ${dynamicLevel.level})" else ""
                    "$turn to move$dynSuffix"
                }
                GameStatus.WHITE_WINS -> eloMessage ?: "White wins! ♔"
                GameStatus.BLACK_WINS -> eloMessage ?: "Black wins! ♚"
                GameStatus.DRAW -> eloMessage ?: "Draw"
            },
            color = ChessColors.OnSurface, fontSize = 14.sp, modifier = Modifier.testTag("game-status")
        )

        OutlinedButton(
            onClick = onUndo,
            modifier = Modifier.testTag("undo-btn").height(44.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = gameState.moveHistory.isNotEmpty()
        ) {
            Text("Undo", color = ChessColors.OnSurface, fontSize = 15.sp)
        }
    }
}

@Composable
private fun GhostControls(
    session: GameSession,
    scope: kotlinx.coroutines.CoroutineScope,
    config: GameConfig,
    commentator: GameCommentator,
    ghostState: GhostPreviewState,
    onStateChange: (chess.core.GameState, GhostPreviewState) -> Unit
) {
    GhostPreviewControls(
        state = ghostState,
        onStepBack = { onStateChange(session.getGameState(), session.ghostStepBack()) },
        onStepForward = { onStateChange(session.getGameState(), session.ghostStepForward()) },
        onReset = { onStateChange(session.getGameState(), session.ghostReset()) },
        onPause = { onStateChange(session.getGameState(), session.ghostPause()) },
        onResume = { onStateChange(session.getGameState(), session.ghostResume()) },
        onAccept = {
            scope.launch {
                val gs = session.acceptGhostLine()
                onStateChange(gs, session.getGhostState())
                commentator.onGhostAccepted()
            }
        },
        onDismiss = {
            session.dismissGhost()
            try {
                if (config.mode == GameMode.HUMAN_VS_ENGINE) {
                    session.undoMove()
                    session.undoMove()
                } else {
                    session.undoMove()
                }
            } catch (_: Exception) {}
            onStateChange(session.getGameState(), session.getGhostState())
            commentator.onGhostDismissed()
        },
        onToggleMode = {
            val newMode = if (ghostState.mode == GhostPreviewMode.AUTO_PLAY)
                GhostPreviewMode.STEP_THROUGH else GhostPreviewMode.AUTO_PLAY
            onStateChange(session.getGameState(), session.ghostSetMode(newMode))
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
fun EngineThinkingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .testTag("engine-thinking-overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = ChessColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Thinking…",
                color = ChessColors.OnSurface,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun CapturedPiecesDisplay(state: CapturedPiecesTracker.MaterialState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("captured-pieces"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Black's captures (white pieces taken by black)
        Row(
            modifier = Modifier.testTag("black-captures"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "♚",
                fontSize = 18.sp,
                color = ChessColors.OnSurface.copy(alpha = 0.5f)
            )
            for (piece in state.blackCaptured) {
                Text(
                    text = CapturedPiecesTracker.pieceUnicode(piece, PieceColor.WHITE),
                    fontSize = 28.sp,
                    color = ChessColors.OnSurface.copy(alpha = 0.9f)
                )
            }
        }

        // Material balance
        val adv = state.advantage
        Text(
            text = when {
                adv > 0 -> "⚖️ +$adv"
                adv < 0 -> "⚖️ $adv"
                else -> "⚖️ ="
            },
            fontSize = 18.sp,
            color = when {
                adv > 0 -> ChessColors.Primary
                adv < 0 -> androidx.compose.ui.graphics.Color(0xFFEF5350)
                else -> ChessColors.OnSurface.copy(alpha = 0.5f)
            },
            modifier = Modifier.testTag("material-balance")
        )

        // White's captures (black pieces taken by white)
        Row(
            modifier = Modifier.testTag("white-captures"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (piece in state.whiteCaptured) {
                Text(
                    text = CapturedPiecesTracker.pieceUnicode(piece, PieceColor.BLACK),
                    fontSize = 28.sp,
                    color = ChessColors.OnSurface.copy(alpha = 0.9f)
                )
            }
            Text(
                text = "♔",
                fontSize = 18.sp,
                color = ChessColors.OnSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/** Determine the right sound effect for a move. */
private fun detectMoveSound(move: Move, boardBefore: Board, boardAfter: Board): SoundEffect {
    if (MoveGenerator.isCheckmate(boardAfter)) return SoundEffect.CHECKMATE
    if (MoveGenerator.isInCheck(boardAfter, boardAfter.activeColor)) return SoundEffect.CHECK
    if (MoveGenerator.isDraw(boardAfter)) return SoundEffect.DRAW
    val movedPiece = boardBefore[move.from]
    if (movedPiece?.type == PieceType.KING && kotlin.math.abs(move.from.file - move.to.file) == 2) {
        return SoundEffect.CASTLE
    }
    val captured = boardBefore[move.to]
    if (captured != null) return SoundEffect.CAPTURE
    if (movedPiece?.type == PieceType.PAWN && move.from.file != move.to.file) {
        return SoundEffect.CAPTURE // en passant
    }
    return SoundEffect.MOVE
}
