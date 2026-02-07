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
import kotlinx.coroutines.withContext

/**
 * Generates witty chess banter using Gemma 3 270M via MediaPipe LLM Inference.
 * Falls back to template-based commentary if model is unavailable.
 */
class GemmaBanterEngine(
    private val modelManager: GemmaModelManager,
    private val fallback: BanterGenerator = TemplateBanterGenerator()
) : BanterGenerator {

    private var llmInference: LlmInference? = null

    override val isReady: Boolean
        get() = modelManager.status.value == ModelStatus.READY

    /**
     * Initialize the LLM inference engine. Call after model is downloaded.
     */
    fun initialize(context: android.content.Context) {
        if (modelManager.status.value != ModelStatus.READY) return
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

    fun shutdown() {
        llmInference?.close()
        llmInference = null
    }

    override suspend fun generateBanter(context: GameContext): String? {
        val llm = llmInference ?: return fallback.generateBanter(context)

        val prompt = buildPrompt(context)
        return withContext(Dispatchers.IO) {
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
        }
    }

    private fun buildPrompt(context: GameContext): String {
        val eventDesc = describeEvent(context.event)
        val thinkingContext = context.engineThinking?.let { "\nComputer analysis: $it" } ?: ""
        val evalContext = context.evaluation?.let {
            val sign = if (it > 0) "+" else ""
            "\nPosition evaluation: $sign${formatEval(it)}"
        } ?: ""

        return """You are a witty, sarcastic chess commentator. Generate ONE short, funny remark (max 20 words) about this chess moment. Be playful and entertaining. Do not explain the move technically.

Game situation: $eventDesc$thinkingContext$evalContext
Move number: ${context.moveNumber}

Your witty remark:"""
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
