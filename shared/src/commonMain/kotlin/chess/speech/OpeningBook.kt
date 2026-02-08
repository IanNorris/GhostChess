package chess.speech

import chess.core.Move

/**
 * Detects named chess openings from move history.
 * Returns the most specific (longest matching) opening name.
 */
object OpeningBook {

    data class Opening(val name: String, val moves: List<String>, val isGood: Boolean = true)

    private val openings = listOf(
        // King's Pawn Openings
        Opening("Italian Game", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")),
        Opening("Ruy Lopez", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5")),
        Opening("Scotch Game", listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4")),
        Opening("King's Gambit", listOf("e2e4", "e7e5", "f2f4")),
        Opening("Petrov's Defence", listOf("e2e4", "e7e5", "g1f3", "g8f6")),
        Opening("Philidor Defence", listOf("e2e4", "e7e5", "g1f3", "d7d6")),
        Opening("Vienna Game", listOf("e2e4", "e7e5", "b1c3")),
        Opening("Four Knights Game", listOf("e2e4", "e7e5", "g1f3", "b8c6", "b1c3", "g8f6")),
        Opening("Giuoco Piano", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5")),
        Opening("Evans Gambit", listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "b2b4")),

        // Queen's Pawn Openings
        Opening("Queen's Gambit", listOf("d2d4", "d7d5", "c2c4")),
        Opening("Queen's Gambit Declined", listOf("d2d4", "d7d5", "c2c4", "e7e6")),
        Opening("Queen's Gambit Accepted", listOf("d2d4", "d7d5", "c2c4", "d5c4")),
        Opening("Slav Defence", listOf("d2d4", "d7d5", "c2c4", "c7c6")),
        Opening("King's Indian Defence", listOf("d2d4", "g8f6", "c2c4", "g7g6")),
        Opening("Nimzo-Indian Defence", listOf("d2d4", "g8f6", "c2c4", "e7e6", "b1c3", "f8b4")),
        Opening("Queen's Indian Defence", listOf("d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "b7b6")),
        Opening("Grünfeld Defence", listOf("d2d4", "g8f6", "c2c4", "g7g6", "b1c3", "d7d5")),
        Opening("London System", listOf("d2d4", "d7d5", "c1f4")),
        Opening("Colle System", listOf("d2d4", "d7d5", "g1f3", "g8f6", "e2e3")),

        // Sicilian Variations
        Opening("Sicilian Defence", listOf("e2e4", "c7c5")),
        Opening("Sicilian Dragon", listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "g7g6")),
        Opening("Sicilian Najdorf", listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6")),

        // Other Openings
        Opening("French Defence", listOf("e2e4", "e7e6")),
        Opening("Caro-Kann Defence", listOf("e2e4", "c7c6")),
        Opening("Pirc Defence", listOf("e2e4", "d7d6", "d2d4", "g8f6")),
        Opening("Scandinavian Defence", listOf("e2e4", "d7d5")),
        Opening("Alekhine's Defence", listOf("e2e4", "g8f6")),
        Opening("English Opening", listOf("c2c4")),
        Opening("Réti Opening", listOf("g1f3", "d7d5", "c2c4")),
        Opening("Bird's Opening", listOf("f2f4")),
        Opening("Dutch Defence", listOf("d2d4", "f7f5")),
        Opening("Benoni Defence", listOf("d2d4", "g8f6", "c2c4", "c7c5")),
        Opening("Catalan Opening", listOf("d2d4", "g8f6", "c2c4", "e7e6", "g2g3")),
    )

    // Sort by move count descending so we match the most specific opening first
    private val sortedOpenings = openings.sortedByDescending { it.moves.size }

    /**
     * Detect the most specific named opening from the move history.
     * Returns null if no known opening matches.
     */
    fun detectOpening(moveHistory: List<Move>): Opening? {
        if (moveHistory.size < 2) return null
        val moveStrings = moveHistory.map { it.toAlgebraic() }

        for (opening in sortedOpenings) {
            if (opening.moves.size <= moveStrings.size) {
                val prefix = moveStrings.subList(0, opening.moves.size)
                if (prefix == opening.moves) {
                    return opening
                }
            }
        }
        return null
    }

    /**
     * Check if the latest move completed a new opening detection.
     * Only returns an opening if this exact move count matches (to avoid repeating).
     */
    fun detectNewOpening(moveHistory: List<Move>): Opening? {
        val opening = detectOpening(moveHistory) ?: return null
        // Only report if the current move count exactly matches the opening length
        if (moveHistory.size == opening.moves.size) return opening
        return null
    }
}
