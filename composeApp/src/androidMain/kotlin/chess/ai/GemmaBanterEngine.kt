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
 * Generates helpful chess commentary using Gemma 3 1B via MediaPipe LLM Inference.
 * Maintains conversation history so the model sees its own prior replies.
 * Falls back to template-based commentary if model is unavailable.
 */
class GemmaBanterEngine(
    private val modelManager: GemmaModelManager,
    private val fallback: BanterGenerator = TemplateBanterGenerator()
) : BanterGenerator {

    private var llmInference: LlmInference? = null
    private val inferenceMutex = Mutex()
    private var appContext: android.content.Context? = null

    // Conversation history: alternating user (move info) and assistant (model reply) messages
    private val conversationHistory = mutableListOf<ConversationTurn>()

    private data class ConversationTurn(val role: String, val content: String)

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
                    .setMaxTokens(256)
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
     * Destroys and recreates the inference instance, clears conversation history.
     */
    override suspend fun reset() {
        conversationHistory.clear()
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

        val userMessage = buildUserMessage(context)
        conversationHistory.add(ConversationTurn("user", userMessage))

        val prompt = buildConversationPrompt()
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(15_000L) {
                    try {
                        val response = llm.generateResponse(prompt)
                        val cleaned = cleanResponse(response)
                        if (cleaned.isNullOrBlank()) {
                            conversationHistory.removeLastOrNull()
                            fallback.generateBanter(context)
                        } else {
                            conversationHistory.add(ConversationTurn("assistant", cleaned))
                            trimHistory()
                            cleaned
                        }
                    } catch (e: Exception) {
                        conversationHistory.removeLastOrNull()
                        fallback.generateBanter(context)
                    }
                } ?: run {
                    conversationHistory.removeLastOrNull()
                    fallback.generateBanter(context)
                }
            }
        } finally {
            inferenceMutex.unlock()
        }
    }

    private fun cleanResponse(response: String?): String? {
        return response?.trim()
            ?.removePrefix("\"")?.removeSuffix("\"")
            ?.removePrefix("Assistant:")?.removePrefix("assistant:")
            ?.trim()
            ?.take(300)
    }

    /** Keep only the last N exchanges to avoid exceeding context window */
    private fun trimHistory() {
        val maxTurns = 20
        while (conversationHistory.size > maxTurns) {
            conversationHistory.removeAt(0)
        }
    }

    private fun buildConversationPrompt(): String {
        val sb = StringBuilder()
        sb.appendLine("You are a helpful chess coach providing brief spoken advice during a game. Give practical tips about the position, suggest ideas, or comment on what just happened. Keep each reply to 1-2 short sentences suitable for text-to-speech. Do not use chess notation — describe moves in plain English (e.g. \"knight to the center\" not \"Nf3\").")
        sb.appendLine()

        for (turn in conversationHistory) {
            when (turn.role) {
                "user" -> sb.appendLine("Move update: ${turn.content}")
                "assistant" -> sb.appendLine("Coach: ${turn.content}")
            }
            sb.appendLine()
        }

        sb.append("Coach:")
        return sb.toString()
    }

    private fun buildUserMessage(context: GameContext): String {
        val parts = mutableListOf<String>()

        // Move description
        parts.add(describeMoveDetail(context))

        // Event (captures, checks, etc.)
        val eventDesc = describeEvent(context.event)
        if (eventDesc.isNotBlank()) parts.add(eventDesc)

        // Board state
        parts.add("Position (FEN): ${context.board.toFen()}")

        // Material balance
        parts.add(describeMaterial(context.board, context.playerColor))

        // Engine analysis
        context.engineCommentary?.let { parts.add("Engine analysis: $it") }

        return parts.joinToString(". ")
    }

    private fun describeMoveDetail(context: GameContext): String {
        val move = context.lastMove ?: return "Move ${context.moveNumber}: Game event"
        val boardBefore = context.boardBefore ?: return "Move ${context.moveNumber}: ${move.from.toAlgebraic()} to ${move.to.toAlgebraic()}"
        val piece = boardBefore[move.from]
        val pieceName = piece?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Piece"
        val captured = boardBefore[move.to]
        val who = when (context.event) {
            is GameEvent.PlayerMoved -> "Player"
            is GameEvent.ComputerMoved -> "Computer"
            is GameEvent.PieceCaptured -> if ((context.event as GameEvent.PieceCaptured).isPlayerCapture) "Player" else "Computer"
            else -> if (piece?.color == context.playerColor) "Player" else "Computer"
        }

        val base = "Move ${context.moveNumber}: $who moved $pieceName from ${move.from.toAlgebraic()} to ${move.to.toAlgebraic()}"
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
        is GameEvent.Checkmate -> if (event.playerWon) "Player delivered checkmate!" else "Player got checkmated"
        is GameEvent.Stalemate -> "The game ended in stalemate"
        is GameEvent.Check -> "Check"
        is GameEvent.PieceCaptured -> {
            val piece = event.capturedType.name.lowercase()
            if (event.isPlayerCapture) "Player captured opponent's $piece" else "Computer captured player's $piece"
        }
        is GameEvent.Promotion -> "Pawn promoted to ${event.pieceType.name.lowercase()}"
        is GameEvent.Castling -> "Castled"
        is GameEvent.Blunder -> "A blunder was made"
        is GameEvent.GoodMove -> "A strong move was played"
        is GameEvent.PlayerMoved -> ""
        is GameEvent.ComputerMoved -> ""
        is GameEvent.GhostPreviewStarted -> ""
        is GameEvent.GhostAccepted -> ""
        is GameEvent.GhostDismissed -> ""
        is GameEvent.MoveUndone -> "Player took back their move"
        is GameEvent.GameStarted -> "A new game has begun"
        is GameEvent.GameStartedAsBlack -> "A new game has begun, playing as black"
    }
}
