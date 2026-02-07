package chess.ai

import chess.core.Board
import chess.core.Move
import chess.core.PieceColor
import chess.speech.BanterGenerator
import chess.speech.GameContext
import chess.speech.GameEvent
import chess.speech.TemplateBanterGenerator
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Generates witty chess banter using Gemma 3 270M via MediaPipe LLM Inference.
 * Falls back to template-based commentary if model is unavailable.
 */
class GemmaBanterEngine(
    private val modelManager: GemmaModelManager,
    private val fallback: BanterGenerator = TemplateBanterGenerator()
) : BanterGenerator {

    private var llmInference: LlmInference? = null
    private val inferenceMutex = Mutex()
    private var appContext: android.content.Context? = null

    override val isReady: Boolean
        get() = modelManager.status.value == ModelStatus.READY

    /**
     * Initialize the LLM inference engine. Call after model is downloaded.
     * Safe to call from any thread.
     */
    suspend fun initialize(context: android.content.Context) {
        if (modelManager.status.value != ModelStatus.READY) return
        appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelManager.modelPath)
                    .setMaxTokens(128)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            } catch (e: Exception) {
                llmInference = null
            }
        }
    }

    fun shutdown() {
        llmInference?.close()
        llmInference = null
    }

    /**
     * Reset the LLM session to prevent context leaks between games.
     * Destroys and recreates the inference instance.
     */
    override suspend fun reset() {
        val ctx = appContext ?: return
        inferenceMutex.withLock {
            llmInference?.close()
            llmInference = null
        }
        initialize(ctx)
    }

    suspend fun resetSession(context: android.content.Context) {
        appContext = context.applicationContext
        reset()
    }

    override suspend fun generateBanter(context: GameContext): String? {
        val llm = llmInference ?: return fallback.generateBanter(context)

        // Prevent concurrent LLM calls — skip if already generating
        if (!inferenceMutex.tryLock()) return fallback.generateBanter(context)

        val prompt = buildPrompt(context)
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(10_000L) {
                    try {
                        val response = llm.generateResponse(prompt)
                        val cleaned = response?.trim()
                            ?.removePrefix("\"")?.removeSuffix("\"")
                            ?.take(200)
                        if (cleaned.isNullOrBlank()) fallback.generateBanter(context)
                        else cleaned
                    } catch (e: Exception) {
                        fallback.generateBanter(context)
                    }
                } ?: fallback.generateBanter(context)
            }
        } finally {
            inferenceMutex.unlock()
        }
    }

    private fun buildPrompt(context: GameContext): String {
        val eventDesc = describeEvent(context.event)
        val moveDesc = describeMoveDetail(context)
        val boardState = context.board.toFen().split(" ").first() // piece placement only
        val materialInfo = describeMaterial(context.board, context.playerColor)
        val moveNum = context.moveNumber

        return """You are a witty chess commentator providing SHORT spoken remarks during a game. Keep it fun and relevant to what just happened on the board.

Rules: ONE sentence only, max 15 words. Be funny, dramatic, or sarcastic about THIS specific move. No existential commentary. Reference the actual pieces and squares when possible.

Move $moveNum: $eventDesc
$moveDesc
Board (FEN): $boardState
$materialInfo

Your remark:"""
    }

    private fun describeMoveDetail(context: GameContext): String {
        val move = context.lastMove ?: return ""
        val boardBefore = context.boardBefore ?: return "Move: ${move.from.toAlgebraic()} → ${move.to.toAlgebraic()}"
        val piece = boardBefore[move.from]
        val pieceName = piece?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Piece"
        val captured = boardBefore[move.to]
        val who = when (context.event) {
            is GameEvent.PlayerMoved -> "Player"
            is GameEvent.ComputerMoved -> "Computer"
            is GameEvent.PieceCaptured -> if ((context.event as GameEvent.PieceCaptured).isPlayerCapture) "Player" else "Computer"
            else -> if (piece?.color == context.playerColor) "Player" else "Computer"
        }

        val base = "$who moved $pieceName from ${move.from.toAlgebraic()} to ${move.to.toAlgebraic()}"
        val captureInfo = if (captured != null) ", capturing ${captured.type.name.lowercase()}" else ""
        val promoInfo = if (move.promotion != null) ", promoting to ${move.promotion!!.name.lowercase()}" else ""
        return base + captureInfo + promoInfo
    }

    private fun describeMaterial(board: Board, playerColor: PieceColor): String {
        val playerPieces = board.allPieces(playerColor)
        val opponentPieces = board.allPieces(playerColor.opposite())
        val playerMat = materialValue(playerPieces)
        val opponentMat = materialValue(opponentPieces)
        val diff = playerMat - opponentMat
        val status = when {
            diff > 5 -> "Player is winning decisively"
            diff > 2 -> "Player has a solid advantage"
            diff > 0 -> "Player is slightly ahead"
            diff == 0 -> "Material is even"
            diff > -2 -> "Computer is slightly ahead"
            diff > -5 -> "Computer has a solid advantage"
            else -> "Computer is winning decisively"
        }
        return "Material: Player ${playerMat}pts vs Computer ${opponentMat}pts — $status"
    }

    private fun materialValue(pieces: List<Pair<chess.core.Square, chess.core.Piece>>): Int {
        var total = 0
        for ((_, piece) in pieces) {
            total += when (piece.type) {
                chess.core.PieceType.PAWN -> 1
                chess.core.PieceType.KNIGHT -> 3
                chess.core.PieceType.BISHOP -> 3
                chess.core.PieceType.ROOK -> 5
                chess.core.PieceType.QUEEN -> 9
                chess.core.PieceType.KING -> 0
            }
        }
        return total
    }

    private fun describeEvent(event: GameEvent): String = when (event) {
        is GameEvent.Checkmate -> if (event.playerWon) "Player just delivered checkmate!" else "Player just got checkmated!"
        is GameEvent.Stalemate -> "The game ended in stalemate"
        is GameEvent.Check -> "Check!"
        is GameEvent.PieceCaptured -> {
            val piece = event.capturedType.name.lowercase()
            if (event.isPlayerCapture) "Player captured opponent's $piece" else "Computer captured player's $piece"
        }
        is GameEvent.Promotion -> "A pawn was promoted to ${event.pieceType.name.lowercase()}"
        is GameEvent.Castling -> "A player castled"
        is GameEvent.Blunder -> "A terrible blunder was made (eval dropped ${formatEval(event.evalDrop)})"
        is GameEvent.GoodMove -> "An excellent move was played (eval gained ${formatEval(event.evalGain)})"
        is GameEvent.PlayerMoved -> "Player made a move"
        is GameEvent.ComputerMoved -> "Computer made a move"
        is GameEvent.GhostPreviewStarted -> "Showing the ghost preview of future moves"
        is GameEvent.GhostAccepted -> "Player accepted the ghost preview"
        is GameEvent.GhostDismissed -> "Player dismissed the ghost preview to try something else"
        is GameEvent.MoveUndone -> "Player took back their move"
        is GameEvent.GameStarted -> "A new game has begun"
        is GameEvent.GameStartedAsBlack -> "A new game has begun, playing as black"
    }

    private fun formatEval(value: Double): String {
        val s = value.toString()
        val dot = s.indexOf('.')
        return if (dot < 0) s else s.substring(0, minOf(s.length, dot + 3))
    }
}
