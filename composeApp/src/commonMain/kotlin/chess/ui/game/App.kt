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
import chess.core.*
import chess.engine.SimpleEngine
import chess.game.Difficulty
import chess.game.GameConfig
import chess.game.GameMode
import chess.game.GameSession
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.speech.GameCommentator
import chess.speech.BanterGenerator
import chess.speech.NoOpSpeechEngine
import chess.speech.SpeechEngine
import chess.ui.board.ChessBoard
import chess.ui.ghost.EngineThinkingPanel
import chess.ui.ghost.GhostPreviewControls
import chess.ui.theme.ChessColors
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
fun App(
    speechEngine: SpeechEngine = NoOpSpeechEngine(),
    settingsStore: SettingsStore = NoOpSettingsStore(),
    banterGenerator: BanterGenerator? = null,
    banterSettingsContent: (@Composable () -> Unit)? = null
) {
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
                    settingsStore = settingsStore,
                    banterSettingsContent = banterSettingsContent
                )
                is Screen.Game -> GameScreen(
                    config = current.config,
                    speechEngine = speechEngine,
                    banterGenerator = banterGenerator,
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
fun MenuScreen(onStartGame: (GameConfig) -> Unit, speechEngine: SpeechEngine = NoOpSpeechEngine(), settingsStore: SettingsStore = NoOpSettingsStore(), banterSettingsContent: (@Composable () -> Unit)? = null) {
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
                try { Difficulty.valueOf(it) } catch (_: Exception) { Difficulty.MEDIUM }
            } ?: Difficulty.MEDIUM
        )
    }
    var speechEnabled by remember { mutableStateOf(settingsStore.getString("speech") == "true") }

    fun saveSettings() {
        settingsStore.putString("mode", selectedMode.name)
        settingsStore.putString("color", playerColor.name)
        settingsStore.putString("thinking", showThinking.toString())
        settingsStore.putString("depth", ghostDepth.toString())
        settingsStore.putString("difficulty", difficulty.name)
        settingsStore.putString("speech", speechEnabled.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("menu-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "♔ Chess Simulator",
            color = ChessColors.OnSurface,
            fontSize = 28.sp,
            modifier = Modifier.testTag("app-title")
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Game mode
        Text("Game Mode", color = ChessColors.OnSurface, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMode == GameMode.HUMAN_VS_ENGINE,
                onClick = { selectedMode = GameMode.HUMAN_VS_ENGINE; saveSettings() },
                label = { Text("vs Computer") },
                modifier = Modifier.testTag("mode-vs-engine")
            )
            FilterChip(
                selected = selectedMode == GameMode.HUMAN_VS_HUMAN,
                onClick = { selectedMode = GameMode.HUMAN_VS_HUMAN; saveSettings() },
                label = { Text("vs Human") },
                modifier = Modifier.testTag("mode-vs-human")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Player color (vs engine only)
        if (selectedMode == GameMode.HUMAN_VS_ENGINE) {
            Text("Play as", color = ChessColors.OnSurface, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = playerColor == PieceColor.WHITE,
                    onClick = { playerColor = PieceColor.WHITE; saveSettings() },
                    label = { Text("White ♔") },
                    modifier = Modifier.testTag("color-white")
                )
                FilterChip(
                    selected = playerColor == PieceColor.BLACK,
                    onClick = { playerColor = PieceColor.BLACK; saveSettings() },
                    label = { Text("Black ♚") },
                    modifier = Modifier.testTag("color-black")
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Difficulty
            Text("Difficulty", color = ChessColors.OnSurface, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { diff ->
                    FilterChip(
                        selected = difficulty == diff,
                        onClick = { difficulty = diff; saveSettings() },
                        label = { Text(diff.label()) },
                        modifier = Modifier.testTag("difficulty-${diff.name.lowercase()}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Ghost depth
        Text("Preview depth: $ghostDepth moves", color = ChessColors.OnSurface, fontSize = 14.sp)
        Slider(
            value = ghostDepth.toFloat(),
            onValueChange = { ghostDepth = it.toInt(); saveSettings() },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier
                .width(250.dp)
                .testTag("depth-slider")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show thinking toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("thinking-toggle-row")
        ) {
            Text("Show computer thinking", color = ChessColors.OnSurface, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = showThinking,
                onCheckedChange = { showThinking = it; saveSettings() },
                modifier = Modifier.testTag("thinking-toggle")
            )
        }

        // Speech toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("speech-toggle-row")
        ) {
            Text("Speech commentary", color = ChessColors.OnSurface, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = speechEnabled,
                onCheckedChange = { speechEnabled = it; saveSettings() },
                modifier = Modifier.testTag("speech-toggle")
            )
        }

        // AI Commentary (platform-specific content, if available)
        if (banterSettingsContent != null) {
            Spacer(modifier = Modifier.height(16.dp))
            banterSettingsContent()
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                speechEngine.enabled = speechEnabled
                onStartGame(
                    GameConfig(
                        mode = selectedMode,
                        playerColor = playerColor,
                        ghostDepth = ghostDepth,
                        showEngineThinking = showThinking,
                        difficulty = difficulty
                    )
                )
            },
            modifier = Modifier.testTag("start-game-btn"),
            colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary)
        ) {
            Text("Start Game", fontSize = 18.sp)
        }
    }
}

@Composable
fun GameScreen(config: GameConfig, speechEngine: SpeechEngine = NoOpSpeechEngine(), banterGenerator: BanterGenerator? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { SimpleEngine() }
    val session = remember { GameSession(engine, config) }
    val commentator = remember { GameCommentator(speechEngine, playerColor = config.playerColor, banterGenerator = banterGenerator, engine = engine) }

    var gameState by remember { mutableStateOf(session.getGameState()) }
    var ghostState by remember { mutableStateOf(session.getGhostState()) }
    var selectedSquare by remember { mutableStateOf<Square?>(null) }
    var legalMovesForSelected by remember { mutableStateOf<List<Move>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var gamePaused by remember { mutableStateOf(false) }
    var whiteElapsedSecs by remember { mutableIntStateOf(0) }
    var blackElapsedSecs by remember { mutableIntStateOf(0) }

    // Move timer — only counts during current player's turn (human only)
    LaunchedEffect(gamePaused, gameState.status, gameState.board.activeColor) {
        if (!gamePaused && gameState.status == GameStatus.IN_PROGRESS && initialized) {
            val activeColor = gameState.board.activeColor
            val isHumanTurn = when (config.mode) {
                GameMode.HUMAN_VS_HUMAN -> true
                GameMode.HUMAN_VS_ENGINE -> activeColor == config.playerColor
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
        if (config.mode == GameMode.HUMAN_VS_ENGINE &&
            config.playerColor == PieceColor.BLACK
        ) {
            val boardBefore = session.getGameState().board
            session.makeEngineMove()
            gameState = session.getGameState()
            ghostState = session.getGhostState()
            val engineMove = gameState.moveHistory.last()
            commentator.onComputerMove(engineMove, boardBefore, gameState.board)
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
        if (gamePaused) return

        val piece = gameState.board[square]

        if (selectedSquare != null) {
            val move = legalMovesForSelected.find { it.to == square }
            if (move != null) {
                scope.launch {
                    val boardBeforePlayer = session.getGameState().board
                    gameState = session.makePlayerMove(move)
                    commentator.onPlayerMove(move, boardBeforePlayer, gameState.board)
                    selectedSquare = null
                    legalMovesForSelected = emptyList()

                    if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                        gameState.status == GameStatus.IN_PROGRESS &&
                        !session.isPlayerTurn()
                    ) {
                        delay(500)
                        val boardBeforeEngine = session.getGameState().board
                        session.makeEngineMove()
                        gameState = session.getGameState()
                        val engineMove = gameState.moveHistory.last()
                        commentator.onComputerMove(engineMove, boardBeforeEngine, gameState.board)
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
            selectedSquare = null
            legalMovesForSelected = emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 52.dp)
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
                            onSquareClick = ::onSquareClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Right panel with controls
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(start = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top bar
                        TopBar(config, gameState, session, commentator, whiteElapsedSecs, blackElapsedSecs,
                            onPause = { gamePaused = true },
                            onUndo = {
                                session.undoMove()
                                if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                                    session.getGameState().moveHistory.isNotEmpty() &&
                                    !session.isPlayerTurn()
                                ) { session.undoMove() }
                                commentator.onMoveUndone()
                                gameState = session.getGameState()
                                ghostState = session.getGhostState()
                                selectedSquare = null
                                legalMovesForSelected = emptyList()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineThinkingPanel(state = ghostState)
                        Spacer(modifier = Modifier.height(8.dp))
                        GhostControls(session, scope, config, commentator, ghostState,
                            onStateChange = { gs, ghost ->
                                gameState = gs; ghostState = ghost
                                selectedSquare = null; legalMovesForSelected = emptyList()
                            }
                        )
                    }
                }
            } else {
                // Portrait: vertical layout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopBar(config, gameState, session, commentator, whiteElapsedSecs, blackElapsedSecs,
                        onPause = { gamePaused = true },
                        onUndo = {
                            session.undoMove()
                            if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                                session.getGameState().moveHistory.isNotEmpty() &&
                                !session.isPlayerTurn()
                            ) { session.undoMove() }
                            commentator.onMoveUndone()
                            gameState = session.getGameState()
                            ghostState = session.getGhostState()
                            selectedSquare = null
                            legalMovesForSelected = emptyList()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChessBoard(
                        board = gameState.board,
                        selectedSquare = selectedSquare,
                        legalMoves = legalMovesForSelected,
                        ghostState = ghostState,
                        flipped = config.playerColor == PieceColor.BLACK,
                        onSquareClick = ::onSquareClick,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    EngineThinkingPanel(state = ghostState, modifier = Modifier.padding(horizontal = 8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    GhostControls(session, scope, config, commentator, ghostState,
                        onStateChange = { gs, ghost ->
                            gameState = gs; ghostState = ghost
                            selectedSquare = null; legalMovesForSelected = emptyList()
                        }
                    )
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
                    "$turn to move"
                }
                GameStatus.WHITE_WINS -> "White wins! ♔"
                GameStatus.BLACK_WINS -> "Black wins! ♚"
                GameStatus.DRAW -> "Draw"
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
