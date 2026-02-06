package chess.web

import chess.core.*
import chess.engine.SimpleEngine
import chess.game.GameConfig
import chess.game.GameMode
import chess.game.GameSession
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.ghost.GhostPreviewStatus
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

fun main() {
    window.onload = { setupMenu(); null }
}

fun setupMenu() {
    val menuScreen = document.getElementById("menu-screen")!!
    val gameScreen = document.getElementById("game-screen")!!

    // Mode chips
    val modeEngine = document.getElementById("mode-vs-engine") as HTMLElement
    val modeHuman = document.getElementById("mode-vs-human") as HTMLElement
    val colorOptions = document.getElementById("color-options") as HTMLElement

    fun updateModeChips() {
        modeEngine.className = if (gameMode == GameMode.HUMAN_VS_ENGINE) "chip selected" else "chip"
        modeHuman.className = if (gameMode == GameMode.HUMAN_VS_HUMAN) "chip selected" else "chip"
        colorOptions.style.display = if (gameMode == GameMode.HUMAN_VS_ENGINE) "block" else "none"
    }

    modeEngine.onclick = { gameMode = GameMode.HUMAN_VS_ENGINE; updateModeChips(); null }
    modeHuman.onclick = { gameMode = GameMode.HUMAN_VS_HUMAN; updateModeChips(); null }

    // Color chips
    val colorWhite = document.getElementById("color-white") as HTMLElement
    val colorBlack = document.getElementById("color-black") as HTMLElement

    fun updateColorChips() {
        colorWhite.className = if (playerColor == PieceColor.WHITE) "chip selected" else "chip"
        colorBlack.className = if (playerColor == PieceColor.BLACK) "chip selected" else "chip"
    }

    colorWhite.onclick = { playerColor = PieceColor.WHITE; updateColorChips(); null }
    colorBlack.onclick = { playerColor = PieceColor.BLACK; updateColorChips(); null }

    // Depth slider
    val depthSlider = document.getElementById("depth-slider") as HTMLInputElement
    val depthLabel = document.getElementById("depth-label")!!
    depthSlider.oninput = {
        ghostDepth = depthSlider.value.toInt()
        depthLabel.textContent = "Preview depth: $ghostDepth moves"
        null
    }

    // Thinking toggle
    val thinkingToggle = document.getElementById("thinking-toggle") as HTMLInputElement
    thinkingToggle.onchange = { showThinking = thinkingToggle.checked; null }

    // Start game
    document.getElementById("start-game-btn")!!.addEventListener("click", {
        flipped = playerColor == PieceColor.BLACK
        val config = GameConfig(gameMode, playerColor, ghostDepth, showThinking)
        val engine = SimpleEngine()
        session = GameSession(engine, config)

        menuScreen.asDynamic().style.display = "none"
        gameScreen.asDynamic().style.display = "block"

        scope.launch {
            session!!.initialize()

            // If playing black, engine goes first
            if (gameMode == GameMode.HUMAN_VS_ENGINE && playerColor == PieceColor.BLACK) {
                session!!.makeEngineMove()
            }

            renderBoard()
            renderGhostControls()
        }
    })

    // Back button
    document.getElementById("back-btn")!!.addEventListener("click", {
        autoPlayJob?.cancel()
        session = null
        selectedSquare = null
        legalMovesForSelected = emptyList()
        gameScreen.asDynamic().style.display = "none"
        menuScreen.asDynamic().style.display = "block"
    })

    // Undo button
    document.getElementById("undo-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        try {
            autoPlayJob?.cancel()
            s.undoMove()
            selectedSquare = null
            legalMovesForSelected = emptyList()
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

            // Ghost highlight
            val ghostBoard = ghostState.boardAtStep
            val ghostPiece = if (ghostState.isActive && ghostBoard != null && ghostState.currentStepIndex >= 0) {
                ghostBoard[square]
            } else null
            val isGhostDiff = ghostPiece != null && ghostPiece != piece
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

            // Piece
            if (piece != null) {
                val pieceSpan = document.createElement("span")
                pieceSpan.textContent = pieceChar(piece.type, piece.color)
                pieceSpan.setAttribute("data-testid", "piece-${square.toAlgebraic()}")
                squareEl.appendChild(pieceSpan)
            }

            // Ghost piece overlay
            if (isGhostDiff && ghostPiece != null) {
                val ghostSpan = document.createElement("span") as HTMLElement
                ghostSpan.textContent = pieceChar(ghostPiece.type, ghostPiece.color)
                ghostSpan.className = "ghost-piece"
                ghostSpan.setAttribute("data-testid", "ghost-piece-${square.toAlgebraic()}")
                squareEl.appendChild(ghostSpan)
            }

            squareEl.addEventListener("click", { onSquareClick(square) })
            boardEl.appendChild(squareEl)
        }
    }

    // Status
    val status = s.getGameState().status
    val statusEl = document.getElementById("game-status")!!
    statusEl.textContent = when (status) {
        GameStatus.IN_PROGRESS -> {
            val turn = if (board.activeColor == PieceColor.WHITE) "White" else "Black"
            "$turn to move"
        }
        GameStatus.WHITE_WINS -> "White wins! ♔"
        GameStatus.BLACK_WINS -> "Black wins! ♚"
        GameStatus.DRAW -> "Draw"
    }

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
        s.makePlayerMove(move)
        selectedSquare = null
        legalMovesForSelected = emptyList()
        renderBoard()
        renderGhostControls()
        renderThinkingPanel()

        // Engine responds
        if (s.config.mode == GameMode.HUMAN_VS_ENGINE &&
            s.getGameState().status == GameStatus.IN_PROGRESS &&
            !s.isPlayerTurn()
        ) {
            delay(300)
            s.makeEngineMove()
            renderBoard()
            renderGhostControls()
        }
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
    (document.getElementById("ghost-accept-btn") as HTMLButtonElement).disabled = ghost.currentStepIndex < 0

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
            renderBoard()
            renderGhostControls()
            renderThinkingPanel()
        }
    })

    document.getElementById("ghost-dismiss-btn")!!.addEventListener("click", {
        val s = session ?: return@addEventListener
        autoPlayJob?.cancel()
        s.dismissGhost()
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
