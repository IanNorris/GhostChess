package chess.ui.theme

import androidx.compose.ui.graphics.Color

object ChessColors {
    val LightSquare = Color(0xFFF0D9B5)
    val DarkSquare = Color(0xFFB58863)
    val SelectedSquare = Color(0xFF829769)
    val LegalMoveHighlight = Color(0x5500FF00)
    val CheckHighlight = Color(0x88FF0000)
    val PlayerThreat = Color(0x88FF8C00)       // Orange: player's pieces under threat
    val OpponentVulnerable = Color(0xAA4CAF50)  // Green border: capturable opponent pieces
    val OpponentAttackDot = Color(0x88E53935)   // Red dot: squares opponent can reach
    val GhostPiece = Color(0x66FFFFFF)
    val GhostMoveFrom = Color(0x4400AAFF)
    val GhostMoveTo = Color(0x6600AAFF)
    val Background = Color(0xFF2D2D2D)
    val Surface = Color(0xFF3D3D3D)
    val OnSurface = Color(0xFFE0E0E0)
    val Primary = Color(0xFF6BAF6B)
    val Accent = Color(0xFFFFB74D)
    val Error = Color(0xFFEF5350)
}
