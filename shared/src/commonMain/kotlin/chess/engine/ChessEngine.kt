package chess.engine

import chess.core.Move

data class EngineAnalysis(
    val bestLine: List<Move>,
    val evaluation: Double, // centipawns / 100 (positive = white advantage)
    val depth: Int,
    val commentary: String = ""
)

data class EngineThought(
    val description: String,
    val evaluation: Double,
    val threats: List<String> = emptyList(),
    val strategicNotes: List<String> = emptyList()
)

interface ChessEngine {
    suspend fun getBestLine(fen: String, depth: Int = 5, lineLength: Int = depth): EngineAnalysis
    suspend fun getThinking(fen: String, depth: Int = 5): EngineThought
    fun isReady(): Boolean
    suspend fun initialize()
    fun shutdown()
}
