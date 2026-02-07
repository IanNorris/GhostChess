package chess.ai

import android.util.Log
import chess.core.Board
import chess.core.Move
import chess.core.PieceColor
import chess.speech.BanterGenerator
import chess.speech.GameContext
import chess.speech.GameEvent
import chess.speech.TemplateBanterGenerator
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Generates helpful chess commentary using Gemma 3 1B via MediaPipe LLM Inference.
 * 
 * IMPORTANT: MediaPipe's native LLM runtime crashes (SIGSEGV) if generateResponse
 * is called concurrently or interrupted mid-call. All inference is therefore fully
 * serialized through a single worker coroutine, and native calls are never cancelled.
 */
class GemmaBanterEngine(
    private val modelManager: GemmaModelManager,
    private val fallback: BanterGenerator = TemplateBanterGenerator()
) : BanterGenerator {

    companion object {
        private const val TAG = "GemmaBanter"
    }

    @Volatile
    private var llmInference: LlmInference? = null
    private val llmMutex = Mutex()
    private var appContext: android.content.Context? = null
    private var logFile: java.io.File? = null

    private val conversationHistory = java.util.concurrent.CopyOnWriteArrayList<ConversationTurn>()

    private data class ConversationTurn(val role: String, val content: String)

    // Serialized inference: requests go into a CONFLATED channel (only latest kept)
    // Results come back through the callback
    private data class InferenceRequest(
        val context: GameContext,
        val callback: (String?) -> Unit
    )
    private val requestChannel = Channel<InferenceRequest>(CONFLATED)
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Single worker coroutine processes LLM requests one at a time
        workerScope.launch {
            for (request in requestChannel) {
                val result = doGenerateBanter(request.context)
                try {
                    request.callback(result)
                } catch (e: Exception) {
                    log("Worker callback error: ${e.message}")
                }
            }
        }
    }

    override val isReady: Boolean
        get() = modelManager.status.value == ModelStatus.READY

    suspend fun initialize(context: android.content.Context) {
        if (modelManager.status.value != ModelStatus.READY) {
            Log.d(TAG, "initialize: model not ready, status=${modelManager.status.value}")
            return
        }
        appContext = context.applicationContext
        logFile = java.io.File(context.filesDir, "gemma_log.txt")
        log("initialize: creating LlmInference...")
        withContext(Dispatchers.IO) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelManager.modelPath)
                    .setMaxTokens(256)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                log("initialize: LlmInference created successfully")
            } catch (e: Exception) {
                log("initialize: FAILED - ${e.message}\n${e.stackTraceToString()}")
                llmInference = null
            }
        }
    }

    fun shutdown() {
        log("shutdown")
        try {
            llmInference?.close()
        } catch (e: Exception) {
            log("shutdown error: ${e.message}")
        }
        llmInference = null
    }

    override suspend fun reset() {
        log("reset: clearing ${conversationHistory.size} turns")
        conversationHistory.clear()
        val ctx = appContext ?: run {
            log("reset: no appContext")
            return
        }
        llmMutex.withLock {
            try {
                llmInference?.close()
                log("reset: closed old LlmInference")
            } catch (e: Exception) {
                log("reset error closing: ${e.message}")
            }
            llmInference = null
        }
        initialize(ctx)
    }

    suspend fun resetSession(context: android.content.Context) {
        appContext = context.applicationContext
        reset()
    }

    /**
     * Queue a banter request. If the LLM is busy, the CONFLATED channel drops
     * the previous pending request (only latest matters). The callback receives
     * the result on the worker thread.
     */
    override suspend fun generateBanter(context: GameContext): String? {
        if (llmInference == null) {
            return fallback.generateBanter(context)
        }

        // Use a CompletableDeferred to bridge the channel-based worker back to suspend
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        val request = InferenceRequest(context) { result -> deferred.complete(result) }

        // trySend on CONFLATED channel always succeeds (replaces old if pending)
        requestChannel.trySend(request)

        return try {
            // Wait for result, but if it takes too long, use fallback
            kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                deferred.await()
            } ?: run {
                log("generateBanter: deferred timed out, using fallback")
                fallback.generateBanter(context)
            }
        } catch (e: Exception) {
            log("generateBanter: error waiting for result: ${e.message}")
            fallback.generateBanter(context)
        }
    }

    /**
     * Actually run the LLM inference. Called only from the single worker coroutine.
     * NEVER cancelled — the native call must complete.
     */
    private fun doGenerateBanter(context: GameContext): String? {
        val llm = llmInference
        if (llm == null) {
            log("doGenerate: llm is null")
            return null
        }

        return try {
            val userMessage = buildUserMessage(context)
            conversationHistory.add(ConversationTurn("user", userMessage))
            log("doGenerate: move ${context.moveNumber}, history=${conversationHistory.size}")

            val prompt = buildConversationPrompt()
            log("doGenerate: prompt ${prompt.length} chars, calling generateResponse...")

            // This is a blocking native call — must never be interrupted
            val response = llm.generateResponse(prompt)
            log("doGenerate: response received, length=${response?.length ?: 0}")

            val cleaned = cleanResponse(response)
            if (cleaned.isNullOrBlank()) {
                log("doGenerate: blank response, removing from history")
                conversationHistory.removeLastOrNull()
                null
            } else {
                log("doGenerate: success: \"${cleaned.take(60)}...\"")
                conversationHistory.add(ConversationTurn("assistant", cleaned))
                trimHistory()
                cleaned
            }
        } catch (e: Exception) {
            log("doGenerate: EXCEPTION: ${e.message}\n${e.stackTraceToString()}")
            conversationHistory.removeLastOrNull()
            null
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try {
            logFile?.appendText("[${System.currentTimeMillis()}] $msg\n")
        } catch (_: Exception) {}
    }

    private fun cleanResponse(response: String?): String? {
        return response?.trim()
            ?.removePrefix("\"")?.removeSuffix("\"")
            ?.removePrefix("Coach:")?.removePrefix("coach:")
            ?.removePrefix("Assistant:")?.removePrefix("assistant:")
            ?.trim()
            ?.take(300)
    }

    private fun trimHistory() {
        while (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
        }
    }

    private fun buildConversationPrompt(): String {
        val sb = StringBuilder()
        sb.appendLine("You are a helpful chess coach providing brief spoken advice during a game. Give practical tips about the position, suggest ideas, or comment on what just happened. Keep each reply to 1-2 short sentences suitable for text-to-speech. Do not use chess notation — describe moves in plain English (e.g. \"knight to the center\" not \"Nf3\").")
        sb.appendLine()

        val history = conversationHistory.toList()
        for (turn in history) {
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

        parts.add(describeMoveDetail(context))

        val eventDesc = describeEvent(context.event)
        if (eventDesc.isNotBlank()) parts.add(eventDesc)

        parts.add("Position (FEN): ${context.board.toFen()}")
        parts.add(describeMaterial(context.board, context.playerColor))

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
