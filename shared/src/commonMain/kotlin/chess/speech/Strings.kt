package chess.speech

/**
 * Internationalization system for game commentary.
 * Each MessageKey maps to a localised string template.
 * Templates can include placeholders: {piece}, {square}, {player}, etc.
 */
enum class MessageKey {
    // Game lifecycle
    GAME_START,
    GAME_START_AS_BLACK,
    YOUR_TURN,
    COMPUTER_THINKING,

    // Moves
    GOOD_MOVE,
    GREAT_MOVE,
    BLUNDER,
    ARE_YOU_SURE,

    // Captures
    CAPTURE_PAWN,
    CAPTURE_MINOR,
    CAPTURE_ROOK,
    CAPTURE_QUEEN,
    CAPTURE_TRADE,

    // Check & mate
    CHECK,
    CHECKMATE_WIN,
    CHECKMATE_LOSE,
    STALEMATE,

    // Ghost preview
    GHOST_PREVIEW_START,
    GHOST_PREVIEW_ACCEPTED,
    GHOST_PREVIEW_DISMISSED,

    // Undo
    MOVE_UNDONE,

    // Misc
    PROMOTION,
    CASTLING,
}

/**
 * Resolves a MessageKey to a localised string, substituting placeholders.
 */
class StringResolver(private val locale: String = "en") {

    private val strings: Map<MessageKey, String> get() = when (locale) {
        else -> englishStrings
    }

    fun resolve(key: MessageKey, params: Map<String, String> = emptyMap()): String {
        var text = strings[key] ?: return ""
        for ((k, v) in params) {
            text = text.replace("{$k}", v)
        }
        return text
    }

    companion object {
        val englishStrings = mapOf(
            // Game lifecycle
            MessageKey.GAME_START to "Let's play! Good luck.",
            MessageKey.GAME_START_AS_BLACK to "You're playing black. I'll go first.",
            MessageKey.YOUR_TURN to "Your move.",
            MessageKey.COMPUTER_THINKING to "Let me think about this.",

            // Moves
            MessageKey.GOOD_MOVE to "Nice move!",
            MessageKey.GREAT_MOVE to "Excellent! That's a strong move.",
            MessageKey.BLUNDER to "Oh no. Are you sure about that?",
            MessageKey.ARE_YOU_SURE to "Hmm, are you sure? That doesn't look great.",

            // Captures
            MessageKey.CAPTURE_PAWN to "Pawn taken.",
            MessageKey.CAPTURE_MINOR to "Got your {piece}!",
            MessageKey.CAPTURE_ROOK to "I'll take that rook, thank you very much!",
            MessageKey.CAPTURE_QUEEN to "The queen falls! That's a big deal.",
            MessageKey.CAPTURE_TRADE to "Fair trade.",

            // Check & mate
            MessageKey.CHECK to "Check!",
            MessageKey.CHECKMATE_WIN to "Checkmate! Well played.",
            MessageKey.CHECKMATE_LOSE to "Checkmate. Better luck next time!",
            MessageKey.STALEMATE to "Stalemate. It's a draw.",

            // Ghost preview
            MessageKey.GHOST_PREVIEW_START to "Here's what I think would happen.",
            MessageKey.GHOST_PREVIEW_ACCEPTED to "Got it, moving on.",
            MessageKey.GHOST_PREVIEW_DISMISSED to "Alright, try something different.",

            // Undo
            MessageKey.MOVE_UNDONE to "Move taken back.",

            // Misc
            MessageKey.PROMOTION to "Pawn promoted to {piece}!",
            MessageKey.CASTLING to "Castling. Nice and safe.",
        )
    }
}
