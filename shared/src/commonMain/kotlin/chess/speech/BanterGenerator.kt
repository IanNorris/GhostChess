package chess.speech

import chess.core.Board
import chess.core.Move
import chess.core.PieceColor

/**
 * Context about the current game state for banter generation.
 */
data class GameContext(
    val event: GameEvent,
    val board: Board,
    val boardBefore: Board? = null,
    val lastMove: Move? = null,
    val playerColor: PieceColor = PieceColor.WHITE,
    val evaluation: Double? = null,
    val engineThinking: String? = null,
    val moveNumber: Int = 0
)

/**
 * Interface for generating witty commentary from game context.
 * Implementations may use AI models, templates, or other sources.
 */
interface BanterGenerator {
    /** Whether this generator is ready to produce banter */
    val isReady: Boolean

    /**
     * Generate a witty remark for the given game context.
     * Returns null if no banter is appropriate or generator is unavailable.
     */
    suspend fun generateBanter(context: GameContext): String?

    /** Reset internal state between games to prevent context leaks. */
    suspend fun reset() {}
}

/**
 * Fallback banter generator using the existing template-based commentary system.
 */
class TemplateBanterGenerator(
    private val commentaryGenerator: CommentaryGenerator = CommentaryGenerator()
) : BanterGenerator {

    override val isReady: Boolean = true

    override suspend fun generateBanter(context: GameContext): String? {
        return commentaryGenerator.generateCommentary(listOf(context.event))
    }
}
