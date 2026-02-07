package chess.web

import chess.core.*
import chess.engine.SimpleEngine
import chess.game.Difficulty
import chess.game.GameConfig
import chess.game.GameMode
import chess.game.GameSession
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.ghost.GhostPreviewStatus
import chess.speech.GameCommentator
import chess.speech.SpeechEngine
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.w3c.dom.*

val pieceUnicode = mapOf(
    PieceType.KING to ("♔" to "♚"),
    PieceType.QUEEN to ("♕" to "♛"),
    PieceType.ROOK to ("♖" to "♜"),
    PieceType.BISHOP to ("♗" to "♝"),
    PieceType.KNIGHT to ("♘" to "♞"),
    PieceType.PAWN to ("♙" to "♟")
)

fun pieceChar(type: PieceType, color: PieceColor): String {
    val pair = pieceUnicode[type]!!
    return if (color == PieceColor.WHITE) pair.first else pair.second
}

val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

var session: GameSession? = null
var selectedSquare: Square? = null
var legalMovesForSelected: List<Move> = emptyList()
var flipped = false
var autoPlayJob: Job? = null
var pendingPromotionMove: ((PieceType) -> Move)? = null

// Config state
var gameMode = GameMode.HUMAN_VS_ENGINE
var playerColor = PieceColor.WHITE
var ghostDepth = 5
var showThinking = false
var difficulty = Difficulty.MEDIUM

// Move timer
var moveStartTime = 0.0
var moveTimerInterval: Int? = null
var gamePaused = false
var whiteTimeMs = 0.0
var blackTimeMs = 0.0
var lastTimerTick = 0.0

// Speech
class BrowserSpeechEngine : SpeechEngine {
    override var enabled: Boolean = false

    override fun speak(text: String) {
        if (!enabled) return
        stop()
        val utterance = js("new SpeechSynthesisUtterance(text)")
        utterance.rate = 1.0
        utterance.pitch = 1.0
        js("window.speechSynthesis.speak(utterance)")
    }

    override fun stop() {
        js("window.speechSynthesis.cancel()")
    }
}

val speechEngine = BrowserSpeechEngine()
var commentator: GameCommentator? = null

fun main() {
    window.onload = { loadSettings(); setupMenu(); null }
}

fun saveSettings() {
    window.localStorage.setItem("ghostchess_mode", gameMode.name)
    window.localStorage.setItem("ghostchess_color", playerColor.name)
    window.localStorage.setItem("ghostchess_depth", ghostDepth.toString())
    window.localStorage.setItem("ghostchess_thinking", showThinking.toString())
    window.localStorage.setItem("ghostchess_difficulty", difficulty.name)
    window.localStorage.setItem("ghostchess_speech", speechEngine.enabled.toString())
}

fun loadSettings() {
    window.localStorage.getItem("ghostchess_mode")?.let {
        gameMode = try { GameMode.valueOf(it) } catch (_: Exception) { GameMode.HUMAN_VS_ENGINE }
    }
    window.localStorage.getItem("ghostchess_color")?.let {
        playerColor = try { PieceColor.valueOf(it) } catch (_: Exception) { PieceColor.WHITE }
    }
    window.localStorage.getItem("ghostchess_depth")?.let {
        ghostDepth = it.toIntOrNull() ?: 5
    }
    window.localStorage.getItem("ghostchess_thinking")?.let {
        showThinking = it == "true"
    }
    window.localStorage.getItem("ghostchess_difficulty")?.let {
        difficulty = try { Difficulty.valueOf(it) } catch (_: Exception) { Difficulty.MEDIUM }
    }
    window.localStorage.getItem("ghostchess_speech")?.let {
        speechEngine.enabled = it == "true"
    }
}

fun setupMenu() {
    val menuScreen = document.getElementById("menu-screen")!!
    val gameScreen = document.getElementById("game-screen")!!

    // Mode chips
    val modeEngine = document.getElementById("mode-vs-engine") as HTMLElement
    val modeHuman = document.getElementById("mode-vs-human") as HTMLElement
    val colorOptions = document.getElementById("color-options") as HTMLElement
    val difficultyOptions = document.getElementById("difficulty-options") as HTMLElement

    fun updateModeChips() {
        modeEngine.className = if (gameMode == GameMode.HUMAN_VS_ENGINE) "chip selected" else "chip"
        modeHuman.className = if (gameMode == GameMode.HUMAN_VS_HUMAN) "chip selected" else "chip"
        colorOptions.style.display = if (gameMode == GameMode.HUMAN_VS_ENGINE) "block" else "none"
        difficultyOptions.style.display = if (gameMode == GameMode.HUMAN_VS_ENGINE) "block" else "none"
        updateWinLossDisplay()
    }

    modeEngine.onclick = { gameMode = GameMode.HUMAN_VS_ENGINE; updateModeChips(); saveSettings(); null }
    modeHuman.onclick = { gameMode = GameMode.HUMAN_VS_HUMAN; updateModeChips(); saveSettings(); null }

    // Color chips
    val colorWhite = document.getElementById("color-white") as HTMLElement
    val colorBlack = document.getElementById("color-black") as HTMLElement

    fun updateColorChips() {
        colorWhite.className = if (playerColor == PieceColor.WHITE) "chip selected" else "chip"
        colorBlack.className = if (playerColor == PieceColor.BLACK) "chip selected" else "chip"
    }

    colorWhite.onclick = { playerColor = PieceColor.WHITE; updateColorChips(); saveSettings(); null }
    colorBlack.onclick = { playerColor = PieceColor.BLACK; updateColorChips(); saveSettings(); null }

    // Difficulty chips
    val diffEasy = document.getElementById("difficulty-easy") as HTMLElement
    val diffMedium = document.getElementById("difficulty-medium") as HTMLElement
    val diffHard = document.getElementById("difficulty-hard") as HTMLElement

    fun updateDifficultyChips() {
        diffEasy.className = if (difficulty == Difficulty.EASY) "chip selected" else "chip"
        diffMedium.className = if (difficulty == Difficulty.MEDIUM) "chip selected" else "chip"
        diffHard.className = if (difficulty == Difficulty.HARD) "chip selected" else "chip"
        updateWinLossDisplay()
    }

    diffEasy.onclick = { difficulty = Difficulty.EASY; updateDifficultyChips(); saveSettings(); null }
    diffMedium.onclick = { difficulty = Difficulty.MEDIUM; updateDifficultyChips(); saveSettings(); null }
    diffHard.onclick = { difficulty = Difficulty.HARD; updateDifficultyChips(); saveSettings(); null }

    // Depth slider
    val depthSlider = document.getElementById("depth-slider") as HTMLInputElement
    val depthLabel = document.getElementById("depth-label")!!
    depthSlider.oninput = {
        ghostDepth = depthSlider.value.toInt()
        depthLabel.textContent = "Preview depth: $ghostDepth moves"
        saveSettings()
        null
    }

    // Thinking toggle
    val thinkingToggle = document.getElementById("thinking-toggle") as HTMLInputElement
    thinkingToggle.onchange = { showThinking = thinkingToggle.checked; saveSettings(); null }

    // Speech toggle
    val speechToggle = document.getElementById("speech-toggle") as HTMLInputElement
    speechToggle.onchange = { speechEngine.enabled = speechToggle.checked; saveSettings(); null }

    // Restore UI from loaded settings
    updateModeChips()
    updateColorChips()
    updateDifficultyChips()
    depthSlider.value = ghostDepth.toString()
    depthLabel.textContent = "Preview depth: $ghostDepth moves"
    thinkingToggle.checked = showThinking
    speechToggle.checked = speechEngine.enabled

    // Win/loss display
    updateWinLossDisplay()

    // Start game
    document.getElementById("start-game-btn")!!.addEventListener("click", {
        flipped = playerColor == PieceColor.BLACK
        val config = GameConfig(gameMode, playerColor, ghostDepth, showThinking, difficulty)
        val engine = SimpleEngine()
        session = GameSession(engine, config)
        commentator = GameCommentator(speechEngine, playerColor = playerColor)

        menuScreen.asDynamic().style.display = "none"
        gameScreen.asDynamic().style.display = "block"
        gamePaused = false
        resetAllTimers()
        startMoveTimer()

        scope.launch {
            session!!.initialize()
            commentator?.onGameStart(playerColor == PieceColor.BLACK)

            // If playing black, engine goes first
            if (gameMode == GameMode.HUMAN_VS_ENGINE && playerColor == PieceColor.BLACK) {
                val boardBefore = session!!.getGameState().board
                session!!.makeEngineMove()
                val boardAfter = session!!.getGameState().board
                val engineMove = session!!.getGameState().moveHistory.last()
                commentator?.onComputerMove(engineMove, boardBefore, boardAfter)
                resetMoveTimer()
            }

            renderBoard()
            renderGhostControls()
        }
    })

    fun returnToMenu() {
        autoPlayJob?.cancel()
        stopMoveTimer()
        session = null
        selectedSquare = null
        legalMovesForSelected = emptyList()
        gamePaused = false
        (document.getElementById("pause-modal") as HTMLElement).className = ""
        gameScreen.asDynamic().style.display = "none"
        menuScreen.asDynamic().style.display = "block"
        updateWinLossDisplay()
    }

    // Pause button
    document.getElementById("pause-btn")!!.addEventListener("click", {
        gamePaused = true
        stopMoveTimer()
        (document.getElementById("pause-modal") as HTMLElement).className = "active"
    })

    // Resume button
    document.getElementById("resume-btn")!!.addEventListener("click", {
        gamePaused = false
        (document.getElementById("pause-modal") as HTMLElement).className = ""
        val s = session
        if (s != null && s.getGameState().status == GameStatus.IN_PROGRESS) {
            startMoveTimer()
        }
    })

    // Quit button (return to menu)
    document.getElementById("quit-btn")!!.addEventListener("click", {
        returnToMenu()
    })

    // Undo button
    document.getElementById("undo-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        try {
            autoPlayJob?.cancel()
            s.undoMove()
            // In vs-computer mode, also undo the computer's move
            // so the player returns to their last decision point
            if (s.config.mode == GameMode.HUMAN_VS_ENGINE &&
                s.getGameState().moveHistory.isNotEmpty() &&
                !s.isPlayerTurn()
            ) {
                s.undoMove()
            }
            commentator?.onMoveUndone()
            selectedSquare = null
            legalMovesForSelected = emptyList()
            resetMoveTimer()
            renderBoard()
            renderGhostControls()
        } catch (_: Exception) {}
    })

    setupGhostButtons()
}

fun renderBoard() {
    val s = session ?: return
    val board = s.getGameState().board
    val ghostState = s.getGhostState()
    val boardEl = document.getElementById("chess-board")!!
    boardEl.innerHTML = ""

    // Rank labels
    val rankLabels = document.getElementById("rank-labels")!!
    rankLabels.innerHTML = ""
    for (displayRank in 0..7) {
        val rank = if (flipped) displayRank else 7 - displayRank
        val label = document.createElement("div")
        label.textContent = "${rank + 1}"
        label.setAttribute("data-testid", "rank-label-${rank + 1}")
        rankLabels.appendChild(label)
    }

    // File labels
    val fileLabels = document.getElementById("file-labels")!!
    fileLabels.innerHTML = ""
    for (displayFile in 0..7) {
        val file = if (flipped) 7 - displayFile else displayFile
        val label = document.createElement("div")
        label.textContent = "${'a' + file}"
        label.setAttribute("data-testid", "file-label-${'a' + file}")
        fileLabels.appendChild(label)
    }

    // Squares
    for (displayRank in 0..7) {
        for (displayFile in 0..7) {
            val rank = if (flipped) displayRank else 7 - displayRank
            val file = if (flipped) 7 - displayFile else displayFile
            val square = Square(file, rank)
            val piece = board[square]
            val isLight = (file + rank) % 2 == 1

            val squareEl = document.createElement("div") as HTMLElement
            val classes = mutableListOf("square", if (isLight) "light" else "dark")

            if (square == selectedSquare) classes.add("selected")

            // Check highlight
            if (piece?.type == PieceType.KING && piece.color == board.activeColor &&
                MoveGenerator.isInCheck(board, piece.color)
            ) {
                classes.add("check")
            }

            // Legal move highlights
            val isLegalTarget = legalMovesForSelected.any { it.to == square }
            if (isLegalTarget) {
                if (piece != null) classes.add("legal-capture") else classes.add("legal-target")
            }

            // Ghost state
            val ghostBoard = ghostState.boardAtStep
            val ghostActive = ghostState.isActive && ghostBoard != null && ghostState.currentStepIndex >= 0
            val ghostPiece = if (ghostActive) ghostBoard!![square] else null

            // Determine what to display: in ghost mode, show ghost board state
            val displayPiece = if (ghostActive) ghostPiece else piece
            val isGhostDiff = ghostActive && ghostPiece != piece
            if (isGhostDiff) classes.add("ghost-highlight")

            squareEl.className = classes.joinToString(" ")
            squareEl.setAttribute("data-testid", "square-${square.toAlgebraic()}")

            // Legal move dot indicator
            if (isLegalTarget && piece == null) {
                val dot = document.createElement("span") as HTMLElement
                dot.className = "legal-dot"
                dot.setAttribute("data-testid", "legal-move-${square.toAlgebraic()}")
                squareEl.appendChild(dot)
            }

            // Display piece (ghost or real)
            if (displayPiece != null) {
                val pieceSpan = document.createElement("span") as HTMLElement
                pieceSpan.textContent = pieceChar(displayPiece.type, displayPiece.color)
                if (ghostActive && isGhostDiff) {
                    pieceSpan.className = "ghost-piece"
                    pieceSpan.setAttribute("data-testid", "ghost-piece-${square.toAlgebraic()}")
                } else {
                    pieceSpan.setAttribute("data-testid", "piece-${square.toAlgebraic()}")
                }
                squareEl.appendChild(pieceSpan)
            }

            squareEl.addEventListener("click", { onSquareClick(square) })
            boardEl.appendChild(squareEl)
        }
    }

    // Status
    val status = s.getGameState().status
    val statusText = when (status) {
        GameStatus.IN_PROGRESS -> {
            val turn = if (board.activeColor == PieceColor.WHITE) "White" else "Black"
            "$turn to move"
        }
        GameStatus.WHITE_WINS -> "White wins! ♔"
        GameStatus.BLACK_WINS -> "Black wins! ♚"
        GameStatus.DRAW -> "Draw"
    }
    document.getElementById("game-status")!!.textContent = statusText

    // Move history
    renderMoveHistory()
}

fun renderMoveHistory() {
    val s = session ?: return
    val history = s.getGameState().moveHistory
    val el = document.getElementById("move-history") as HTMLElement
    el.innerHTML = ""
    if (history.isEmpty()) {
        el.className = ""
        return
    }
    el.className = "has-moves"
    for (i in history.indices step 2) {
        val moveNum = i / 2 + 1
        val white = history[i].toAlgebraic()
        val black = if (i + 1 < history.size) history[i + 1].toAlgebraic() else ""
        val span = document.createElement("span")
        span.textContent = "$moveNum. $white $black"
        el.appendChild(span)
    }
}

fun onSquareClick(square: Square) {
    val s = session ?: return
    if (gamePaused) return
    if (s.getGameState().status != GameStatus.IN_PROGRESS) return
    if (!s.isPlayerTurn()) return

    val board = s.getGameState().board
    val piece = board[square]

    if (selectedSquare != null) {
        // Check for promotion moves to this square
        val promoMoves = legalMovesForSelected.filter { it.to == square && it.promotion != null }
        if (promoMoves.isNotEmpty()) {
            // Show promotion picker
            val fromSq = selectedSquare!!
            pendingPromotionMove = { pieceType ->
                promoMoves.first { it.promotion == pieceType }
            }
            showPromotionModal(board.activeColor)
            return
        }

        // Try to make a normal move
        val move = legalMovesForSelected.find { it.to == square && it.promotion == null }
        if (move != null) {
            executeMove(s, move)
            return
        }
    }

    // Select piece
    if (piece != null && piece.color == board.activeColor) {
        selectedSquare = square
        legalMovesForSelected = s.legalMoves().filter { it.from == square }
    } else {
        selectedSquare = null
        legalMovesForSelected = emptyList()
    }
    renderBoard()
}

fun executeMove(s: GameSession, move: Move) {
    scope.launch {
        autoPlayJob?.cancel()
        val boardBeforePlayer = s.getGameState().board
        s.makePlayerMove(move)
        val boardAfterPlayer = s.getGameState().board
        commentator?.onPlayerMove(move, boardBeforePlayer, boardAfterPlayer)
        selectedSquare = null
        legalMovesForSelected = emptyList()
        resetMoveTimer()

        // In vs-computer mode, let computer respond first, then show ghost preview
        if (s.config.mode == GameMode.HUMAN_VS_ENGINE &&
            s.getGameState().status == GameStatus.IN_PROGRESS &&
            !s.isPlayerTurn()
        ) {
            renderBoard()
            delay(300)
            val boardBeforeEngine = s.getGameState().board
            s.makeEngineMove()
            val boardAfterEngine = s.getGameState().board
            val engineMove = s.getGameState().moveHistory.last()
            commentator?.onComputerMove(engineMove, boardBeforeEngine, boardAfterEngine)
            resetMoveTimer()
        }

        checkAndRecordGameEnd()

        // Request ghost preview from current position (after computer responded if applicable)
        if (s.getGameState().status == GameStatus.IN_PROGRESS) {
            s.requestGhostPreview()
            commentator?.onGhostPreviewStart()
        }
        renderBoard()
        renderGhostControls()
        renderThinkingPanel()
    }
}

fun showPromotionModal(color: PieceColor) {
    val modal = document.getElementById("promotion-modal") as HTMLElement
    modal.className = "active"
    // Set piece chars on buttons
    val pieces = listOf("queen" to PieceType.QUEEN, "rook" to PieceType.ROOK,
        "bishop" to PieceType.BISHOP, "knight" to PieceType.KNIGHT)
    for ((name, type) in pieces) {
        val btn = document.querySelector("[data-piece=\"$name\"]") as HTMLElement
        btn.textContent = pieceChar(type, color)
    }
}

fun hidePromotionModal() {
    val modal = document.getElementById("promotion-modal") as HTMLElement
    modal.className = ""
}

fun renderGhostControls() {
    val s = session ?: return
    val ghost = s.getGhostState()
    val controls = document.getElementById("ghost-controls") as HTMLElement

    if (ghost.isActive) {
        controls.className = "active"
        controls.style.display = "block"
    } else {
        controls.className = ""
        controls.style.display = "none"
        return
    }

    val statusText = document.getElementById("ghost-status-text")!!
    statusText.textContent = when (ghost.status) {
        GhostPreviewStatus.LOADING -> "Analyzing..."
        GhostPreviewStatus.PLAYING -> "Auto-playing best line"
        GhostPreviewStatus.PAUSED -> "Step through mode"
        GhostPreviewStatus.COMPLETE -> "Preview complete"
        GhostPreviewStatus.IDLE -> ""
    }

    val moveInfo = document.getElementById("ghost-move-info")!!
    moveInfo.textContent = if (ghost.currentStepIndex >= 0) {
        "Move ${ghost.currentStepIndex + 1}/${ghost.predictedLine.size}: ${ghost.currentMoveDescription}"
    } else ""

    val evalEl = document.getElementById("ghost-eval")!!
    ghost.analysis?.let { analysis ->
        val evalVal = analysis.evaluation
        val evalStr = evalVal.toString().let { str ->
            val dot = str.indexOf('.')
            if (dot < 0) "$str.00" else str.substring(0, minOf(str.length, dot + 3))
        }
        evalEl.textContent = "Eval: ${if (evalVal > 0) "+" else ""}$evalStr"
    } ?: run { evalEl.textContent = "" }

    // Button states
    (document.getElementById("ghost-reset-btn") as HTMLButtonElement).disabled = ghost.currentStepIndex <= -1
    (document.getElementById("ghost-step-back-btn") as HTMLButtonElement).disabled = !ghost.canStepBack
    (document.getElementById("ghost-step-forward-btn") as HTMLButtonElement).disabled = !ghost.canStepForward
    (document.getElementById("ghost-accept-btn") as HTMLButtonElement).disabled = !ghost.isActive

    val playPauseBtn = document.getElementById("ghost-play-pause-btn") as HTMLButtonElement
    playPauseBtn.textContent = if (ghost.status == GhostPreviewStatus.PLAYING) "Pause" else "Play"
    playPauseBtn.disabled = !ghost.canStepForward && ghost.status != GhostPreviewStatus.PLAYING

    val toggleModeBtn = document.getElementById("ghost-toggle-mode-btn")!!
    toggleModeBtn.textContent = if (ghost.mode == GhostPreviewMode.AUTO_PLAY) "Step" else "Auto"

    // Start auto-play if in PLAYING status
    if (ghost.status == GhostPreviewStatus.PLAYING && autoPlayJob?.isActive != true) {
        startAutoPlay()
    }
}

fun startAutoPlay() {
    autoPlayJob?.cancel()
    autoPlayJob = scope.launch {
        val s = session ?: return@launch
        while (s.getGhostState().canStepForward &&
            s.getGhostState().status == GhostPreviewStatus.PLAYING
        ) {
            delay(s.getGhostState().autoPlaySpeedMs)
            s.ghostStepForward()
            renderBoard()
            renderGhostControls()
        }
    }
}

fun renderThinkingPanel() {
    val s = session ?: return
    val ghost = s.getGhostState()
    val panel = document.getElementById("engine-thinking-panel") as HTMLElement

    if (ghost.showThinking && ghost.thinking != null) {
        panel.className = "active"
        panel.style.display = "block"
        document.getElementById("thinking-description")!!.textContent = ghost.thinking!!.description
        val threats = ghost.thinking!!.threats
        document.getElementById("thinking-threats")!!.textContent =
            if (threats.isNotEmpty()) "⚠️ Threats: ${threats.joinToString(", ")}" else ""
        val strategy = ghost.thinking!!.strategicNotes
        document.getElementById("thinking-strategy")!!.textContent =
            if (strategy.isNotEmpty()) "💡 ${strategy.joinToString(". ")}" else ""
        ghost.analysis?.let {
            document.getElementById("thinking-commentary")!!.textContent =
                if (it.commentary.isNotEmpty()) it.commentary else ""
        }
    } else {
        panel.className = ""
        panel.style.display = "none"
    }
}

fun setupGhostButtons() {
    document.getElementById("ghost-step-forward-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        try {
            s.ghostStepForward()
            renderBoard()
            renderGhostControls()
        } catch (_: Exception) {}
    })

    document.getElementById("ghost-step-back-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        try {
            s.ghostStepBack()
            renderBoard()
            renderGhostControls()
        } catch (_: Exception) {}
    })

    document.getElementById("ghost-reset-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        autoPlayJob?.cancel()
        s.ghostReset()
        renderBoard()
        renderGhostControls()
    })

    document.getElementById("ghost-play-pause-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        val ghost = s.getGhostState()
        if (ghost.status == GhostPreviewStatus.PLAYING) {
            autoPlayJob?.cancel()
            s.ghostPause()
        } else {
            s.ghostResume()
            startAutoPlay()
        }
        renderGhostControls()
    })

    document.getElementById("ghost-toggle-mode-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        val newMode = if (s.getGhostState().mode == GhostPreviewMode.AUTO_PLAY)
            GhostPreviewMode.STEP_THROUGH else GhostPreviewMode.AUTO_PLAY
        autoPlayJob?.cancel()
        s.ghostSetMode(newMode)
        if (newMode == GhostPreviewMode.AUTO_PLAY && s.getGhostState().canStepForward) {
            s.ghostResume()
            startAutoPlay()
        }
        renderGhostControls()
    })

    document.getElementById("ghost-accept-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        autoPlayJob?.cancel()
        scope.launch {
            s.acceptGhostLine()
            commentator?.onGhostAccepted()
            renderBoard()
            renderGhostControls()
            renderThinkingPanel()
        }
    })

    document.getElementById("ghost-dismiss-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        autoPlayJob?.cancel()
        s.dismissGhost()
        // Undo the player's move so they can try something different
        try {
            if (s.config.mode == GameMode.HUMAN_VS_ENGINE) {
                // Undo engine's response AND player's move
                s.undoMove() // undo engine response
                s.undoMove() // undo player move
            } else {
                // HvH: just undo the last move
                s.undoMove()
            }
        } catch (_: Exception) {}
        commentator?.onGhostDismissed()
        renderBoard()
        renderGhostControls()
        renderThinkingPanel()
    })

    // Promotion buttons
    for (pieceName in listOf("queen", "rook", "bishop", "knight")) {
        document.querySelector("[data-piece=\"$pieceName\"]")!!.addEventListener("click", {
            val s = session ?: return@addEventListener
            val pieceType = when (pieceName) {
                "queen" -> PieceType.QUEEN
                "rook" -> PieceType.ROOK
                "bishop" -> PieceType.BISHOP
                "knight" -> PieceType.KNIGHT
                else -> return@addEventListener
            }
            val move = pendingPromotionMove?.invoke(pieceType) ?: return@addEventListener
            pendingPromotionMove = null
            hidePromotionModal()
            executeMove(s, move)
        })
    }
}

// --- Move Timer (dual clocks) ---

fun startMoveTimer() {
    lastTimerTick = window.asDynamic().performance.now() as Double
    moveTimerInterval?.let { window.clearInterval(it) }
    moveTimerInterval = window.setInterval({
        if (!gamePaused) {
            val s = session ?: return@setInterval
            val now = window.asDynamic().performance.now() as Double
            val delta = now - lastTimerTick
            lastTimerTick = now

            val activeColor = s.getGameState().board.activeColor
            // Only count time for human players
            val shouldCount = when (s.config.mode) {
                GameMode.HUMAN_VS_HUMAN -> true
                GameMode.HUMAN_VS_ENGINE -> activeColor == s.config.playerColor
            }
            if (shouldCount) {
                if (activeColor == PieceColor.WHITE) whiteTimeMs += delta
                else blackTimeMs += delta
            }

            updateTimerDisplay()
        }
    }, 500)
}

fun stopMoveTimer() {
    moveTimerInterval?.let { window.clearInterval(it) }
    moveTimerInterval = null
}

fun resetMoveTimer() {
    lastTimerTick = window.asDynamic().performance.now() as Double
}

fun resetAllTimers() {
    whiteTimeMs = 0.0
    blackTimeMs = 0.0
    lastTimerTick = window.asDynamic().performance.now() as Double
    updateTimerDisplay()
}

fun formatTime(ms: Double): String {
    val totalSecs = (ms / 1000.0).toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

fun updateTimerDisplay() {
    val s = session ?: return
    val text = if (s.config.mode == GameMode.HUMAN_VS_HUMAN) {
        "⬜ ${formatTime(whiteTimeMs)}  ⬛ ${formatTime(blackTimeMs)}"
    } else {
        val playerTime = if (s.config.playerColor == PieceColor.WHITE) whiteTimeMs else blackTimeMs
        formatTime(playerTime)
    }
    document.getElementById("move-timer")?.textContent = text
}

// --- Win/Loss Counter (localStorage) ---

fun getWinLossKey(diff: Difficulty): String = "ghostchess_${diff.name.lowercase()}"

fun getWinLoss(diff: Difficulty): Pair<Int, Int> {
    val stored = window.localStorage.getItem(getWinLossKey(diff)) ?: "0:0"
    val parts = stored.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

fun recordResult(diff: Difficulty, won: Boolean) {
    val (w, l) = getWinLoss(diff)
    val newVal = if (won) "${w + 1}:$l" else "$w:${l + 1}"
    window.localStorage.setItem(getWinLossKey(diff), newVal)
}

fun updateWinLossDisplay() {
    val el = document.getElementById("win-loss-display") ?: return
    if (gameMode != GameMode.HUMAN_VS_ENGINE) {
        el.textContent = ""
        return
    }
    val (w, l) = getWinLoss(difficulty)
    el.textContent = "${difficulty.label()} — Wins: $w  Losses: $l"
}

fun checkAndRecordGameEnd() {
    val s = session ?: return
    val status = s.getGameState().status
    if (status == GameStatus.IN_PROGRESS) return
    if (s.config.mode != GameMode.HUMAN_VS_ENGINE) return

    stopMoveTimer()
    val playerWon = (status == GameStatus.WHITE_WINS && s.config.playerColor == PieceColor.WHITE) ||
            (status == GameStatus.BLACK_WINS && s.config.playerColor == PieceColor.BLACK)
    if (status != GameStatus.DRAW) {
        recordResult(s.config.difficulty, playerWon)
    }
}
