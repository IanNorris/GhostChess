package chess.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chess.core.*
import chess.engine.SimpleEngine
import chess.game.GameConfig
import chess.game.GameMode
import chess.game.GameSession
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.ui.board.ChessBoard
import chess.ui.ghost.EngineThinkingPanel
import chess.ui.ghost.GhostPreviewControls
import chess.ui.theme.ChessColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
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
                    onStartGame = { config -> screen = Screen.Game(config) }
                )
                is Screen.Game -> GameScreen(
                    config = current.config,
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
fun MenuScreen(onStartGame: (GameConfig) -> Unit) {
    var selectedMode by remember { mutableStateOf(GameMode.HUMAN_VS_ENGINE) }
    var playerColor by remember { mutableStateOf(PieceColor.WHITE) }
    var showThinking by remember { mutableStateOf(false) }
    var ghostDepth by remember { mutableStateOf(5) }

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
                onClick = { selectedMode = GameMode.HUMAN_VS_ENGINE },
                label = { Text("vs Engine") },
                modifier = Modifier.testTag("mode-vs-engine")
            )
            FilterChip(
                selected = selectedMode == GameMode.HUMAN_VS_HUMAN,
                onClick = { selectedMode = GameMode.HUMAN_VS_HUMAN },
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
                    onClick = { playerColor = PieceColor.WHITE },
                    label = { Text("White ♔") },
                    modifier = Modifier.testTag("color-white")
                )
                FilterChip(
                    selected = playerColor == PieceColor.BLACK,
                    onClick = { playerColor = PieceColor.BLACK },
                    label = { Text("Black ♚") },
                    modifier = Modifier.testTag("color-black")
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Ghost depth
        Text("Preview depth: $ghostDepth moves", color = ChessColors.OnSurface, fontSize = 14.sp)
        Slider(
            value = ghostDepth.toFloat(),
            onValueChange = { ghostDepth = it.toInt() },
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
            Text("Show engine thinking", color = ChessColors.OnSurface, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = showThinking,
                onCheckedChange = { showThinking = it },
                modifier = Modifier.testTag("thinking-toggle")
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                onStartGame(
                    GameConfig(
                        mode = selectedMode,
                        playerColor = playerColor,
                        ghostDepth = ghostDepth,
                        showEngineThinking = showThinking
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
fun GameScreen(config: GameConfig, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { SimpleEngine() }
    val session = remember { GameSession(engine, config) }

    var gameState by remember { mutableStateOf(session.getGameState()) }
    var ghostState by remember { mutableStateOf(session.getGhostState()) }
    var selectedSquare by remember { mutableStateOf<Square?>(null) }
    var legalMovesForSelected by remember { mutableStateOf<List<Move>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }

    // Initialize engine
    LaunchedEffect(Unit) {
        session.initialize()
        initialized = true
        // If playing black vs engine, engine moves first
        if (config.mode == GameMode.HUMAN_VS_ENGINE &&
            config.playerColor == PieceColor.BLACK
        ) {
            session.makeEngineMove()
            gameState = session.getGameState()
            ghostState = session.getGhostState()
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

        val piece = gameState.board[square]

        if (selectedSquare != null) {
            // Try to make a move
            val move = legalMovesForSelected.find { it.to == square }
            if (move != null) {
                scope.launch {
                    gameState = session.makePlayerMove(move)
                    selectedSquare = null
                    legalMovesForSelected = emptyList()

                    // Engine responds in vs engine mode
                    if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                        gameState.status == GameStatus.IN_PROGRESS &&
                        !session.isPlayerTurn()
                    ) {
                        delay(500)
                        session.makeEngineMove()
                        gameState = session.getGameState()
                    }

                    // Request ghost preview after engine has responded
                    session.requestGhostPreview()
                    ghostState = session.getGhostState()
                }
                return
            }
        }

        // Select a piece
        if (piece != null && piece.color == gameState.board.activeColor) {
            selectedSquare = square
            legalMovesForSelected = session.legalMoves().filter { it.from == square }
        } else {
            selectedSquare = null
            legalMovesForSelected = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 36.dp)
            .testTag("game-screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("back-btn")
            ) {
                Text("← Back", color = ChessColors.OnSurface)
            }
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
                color = ChessColors.OnSurface,
                fontSize = 16.sp,
                modifier = Modifier.testTag("game-status")
            )
            TextButton(
                onClick = {
                    session.undoMove()
                    gameState = session.getGameState()
                    ghostState = session.getGhostState()
                    selectedSquare = null
                    legalMovesForSelected = emptyList()
                },
                modifier = Modifier.testTag("undo-btn"),
                enabled = gameState.moveHistory.isNotEmpty()
            ) {
                Text("Undo", color = ChessColors.OnSurface)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chess board
        ChessBoard(
            board = gameState.board,
            selectedSquare = selectedSquare,
            legalMoves = legalMovesForSelected,
            ghostState = ghostState,
            flipped = config.playerColor == PieceColor.BLACK,
            onSquareClick = ::onSquareClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Engine thinking panel
        EngineThinkingPanel(
            state = ghostState,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Ghost preview controls
        GhostPreviewControls(
            state = ghostState,
            onStepBack = {
                ghostState = session.ghostStepBack()
            },
            onStepForward = {
                ghostState = session.ghostStepForward()
            },
            onReset = {
                ghostState = session.ghostReset()
            },
            onPause = {
                ghostState = session.ghostPause()
            },
            onResume = {
                ghostState = session.ghostResume()
            },
            onAccept = {
                scope.launch {
                    gameState = session.acceptGhostLine()
                    ghostState = session.getGhostState()

                    // If engine's turn after accepting, let engine respond
                    if (config.mode == GameMode.HUMAN_VS_ENGINE &&
                        gameState.status == GameStatus.IN_PROGRESS &&
                        !session.isPlayerTurn()
                    ) {
                        delay(300)
                        session.makeEngineMove()
                        gameState = session.getGameState()
                    }
                }
            },
            onDismiss = {
                ghostState = session.dismissGhost()
            },
            onToggleMode = {
                val newMode = if (ghostState.mode == GhostPreviewMode.AUTO_PLAY)
                    GhostPreviewMode.STEP_THROUGH else GhostPreviewMode.AUTO_PLAY
                ghostState = session.ghostSetMode(newMode)
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
