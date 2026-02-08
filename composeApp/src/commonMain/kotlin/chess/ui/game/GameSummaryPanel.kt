package chess.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chess.game.GameSummary
import chess.ui.theme.ChessColors

@Composable
fun GameSummaryPanel(
    summary: GameSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChessColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("game-summary-panel")
    ) {
        // Header
        Text(
            text = "🎓 Coach",
            color = ChessColors.Accent,
            fontSize = 14.sp,
            modifier = Modifier.testTag("summary-header")
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${summary.evalDescription} · ${summary.phase} · Move ${summary.moveNumber}",
            color = ChessColors.OnSurface.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.testTag("summary-details")
        )

        // Lesson
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = summary.lesson,
            color = ChessColors.OnSurface,
            fontSize = 12.sp,
            modifier = Modifier.testTag("summary-lesson")
        )

        // Tips
        if (summary.tips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            for (tip in summary.tips) {
                Text(
                    text = "📌 $tip",
                    color = ChessColors.Error,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Suggestion
        if (summary.suggestion.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 ${summary.suggestion}",
                color = ChessColors.OnSurface,
                fontSize = 12.sp,
                modifier = Modifier.testTag("summary-suggestion")
            )
        }
    }
}

private fun phaseEmoji(phase: String): String = when (phase) {
    "Opening" -> "📖"
    "Endgame" -> "🏁"
    else -> "⚔️"
}
