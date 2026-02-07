package chess.speech

import chess.core.PieceType

/**
 * Generates spoken commentary text from game events.
 * Prioritises the most important event when multiple occur
 * (e.g., checkmate > check > capture).
 */
class CommentaryGenerator(
    private val strings: StringResolver = StringResolver("en")
) {
    /**
     * Given a list of events from a single move, return the single
     * most important commentary line to speak (or null for silence).
     */
    fun generateCommentary(events: List<GameEvent>): String? {
        // Priority order: checkmate > stalemate > check > capture > promotion > castling > blunder > good move
        for (event in events.sortedBy { priority(it) }) {
            val text = commentFor(event)
            if (text != null) return text
        }
        return null
    }

    private fun priority(event: GameEvent): Int = when (event) {
        is GameEvent.Checkmate -> 0
        is GameEvent.Stalemate -> 1
        is GameEvent.Check -> 2
        is GameEvent.PieceCaptured -> 3
        is GameEvent.Promotion -> 4
        is GameEvent.Castling -> 5
        is GameEvent.Blunder -> 6
        is GameEvent.GoodMove -> 7
        is GameEvent.GhostPreviewStarted -> 8
        is GameEvent.GhostAccepted -> 9
        is GameEvent.GhostDismissed -> 10
        is GameEvent.MoveUndone -> 11
        is GameEvent.GameStarted -> 12
        is GameEvent.GameStartedAsBlack -> 12
        is GameEvent.PlayerMoved -> 99
        is GameEvent.ComputerMoved -> 99
    }

    private fun commentFor(event: GameEvent): String? = when (event) {
        is GameEvent.Checkmate ->
            if (event.playerWon) strings.resolve(MessageKey.CHECKMATE_WIN)
            else strings.resolve(MessageKey.CHECKMATE_LOSE)

        is GameEvent.Stalemate -> strings.resolve(MessageKey.STALEMATE)

        is GameEvent.Check -> strings.resolve(MessageKey.CHECK)

        is GameEvent.PieceCaptured -> captureComment(event)

        is GameEvent.Promotion -> strings.resolve(
            MessageKey.PROMOTION,
            mapOf("piece" to pieceName(event.pieceType))
        )

        is GameEvent.Castling -> strings.resolve(MessageKey.CASTLING)

        is GameEvent.Blunder -> strings.resolve(MessageKey.BLUNDER)

        is GameEvent.GoodMove -> if (event.evalGain > 2.0)
            strings.resolve(MessageKey.GREAT_MOVE)
        else
            strings.resolve(MessageKey.GOOD_MOVE)

        is GameEvent.GhostPreviewStarted -> strings.resolve(MessageKey.GHOST_PREVIEW_START)
        is GameEvent.GhostAccepted -> strings.resolve(MessageKey.GHOST_PREVIEW_ACCEPTED)
        is GameEvent.GhostDismissed -> strings.resolve(MessageKey.GHOST_PREVIEW_DISMISSED)
        is GameEvent.MoveUndone -> strings.resolve(MessageKey.MOVE_UNDONE)
        is GameEvent.GameStarted -> strings.resolve(MessageKey.GAME_START)
        is GameEvent.GameStartedAsBlack -> strings.resolve(MessageKey.GAME_START_AS_BLACK)
        is GameEvent.PlayerMoved -> null
        is GameEvent.ComputerMoved -> null
    }

    private fun captureComment(event: GameEvent.PieceCaptured): String {
        return when (event.capturedType) {
            PieceType.QUEEN -> strings.resolve(MessageKey.CAPTURE_QUEEN)
            PieceType.ROOK -> strings.resolve(MessageKey.CAPTURE_ROOK)
            PieceType.BISHOP, PieceType.KNIGHT -> strings.resolve(
                MessageKey.CAPTURE_MINOR,
                mapOf("piece" to pieceName(event.capturedType))
            )
            PieceType.PAWN -> strings.resolve(MessageKey.CAPTURE_PAWN)
            PieceType.KING -> strings.resolve(MessageKey.CHECK)
        }
    }

    private fun pieceName(type: PieceType): String = when (type) {
        PieceType.KING -> "king"
        PieceType.QUEEN -> "queen"
        PieceType.ROOK -> "rook"
        PieceType.BISHOP -> "bishop"
        PieceType.KNIGHT -> "knight"
        PieceType.PAWN -> "pawn"
    }
}
