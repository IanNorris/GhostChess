package chess.ui.ghost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chess.ghost.GhostPreviewMode
import chess.ghost.GhostPreviewState
import chess.ghost.GhostPreviewStatus
import chess.ui.theme.ChessColors

@Composable
fun GhostPreviewControls(
    state: GhostPreviewState,
    onStepBack: () -> Unit = {},
    onStepForward: () -> Unit = {},
    onReset: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onToggleMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!state.isActive) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChessColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("ghost-controls"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status text
        Text(
            text = when (state.status) {
                GhostPreviewStatus.LOADING -> "Analyzing..."
                GhostPreviewStatus.PLAYING -> "Auto-playing best line"
                GhostPreviewStatus.PAUSED -> "Step through mode"
                GhostPreviewStatus.COMPLETE -> "Preview complete"
                GhostPreviewStatus.IDLE -> ""
            },
            color = ChessColors.OnSurface,
            fontSize = 14.sp,
            modifier = Modifier.testTag("ghost-status-text")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Move info
        if (state.currentStepIndex >= 0) {
            Text(
                text = "Move ${state.currentStepIndex + 1}/${state.predictedLine.size}: ${state.currentMoveDescription}",
                color = ChessColors.Accent,
                fontSize = 12.sp,
                modifier = Modifier.testTag("ghost-move-info")
            )
        }

        // Eval
        state.analysis?.let { analysis ->
            val evalVal = analysis.evaluation
            val evalStr = evalVal.toString().let { s ->
                val dot = s.indexOf('.')
                if (dot < 0) "$s.00" else s.substring(0, minOf(s.length, dot + 3))
            }
            Text(
                text = "Eval: ${if (evalVal > 0) "+" else ""}$evalStr",
                color = ChessColors.OnSurface,
                fontSize = 12.sp,
                modifier = Modifier.testTag("ghost-eval")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.testTag("ghost-reset-btn"),
                enabled = state.currentStepIndex > -1
            ) {
                Text("⏮", fontSize = 18.sp)
            }

            IconButton(
                onClick = onStepBack,
                modifier = Modifier.testTag("ghost-step-back-btn"),
                enabled = state.canStepBack
            ) {
                Text("⏪", fontSize = 18.sp)
            }

            IconButton(
                onClick = if (state.status == GhostPreviewStatus.PLAYING) onPause else onResume,
                modifier = Modifier.testTag("ghost-play-pause-btn"),
                enabled = state.canStepForward || state.status == GhostPreviewStatus.PLAYING
            ) {
                Text(
                    if (state.status == GhostPreviewStatus.PLAYING) "⏸" else "▶",
                    fontSize = 18.sp
                )
            }

            IconButton(
                onClick = onStepForward,
                modifier = Modifier.testTag("ghost-step-forward-btn"),
                enabled = state.canStepForward
            ) {
                Text("⏩", fontSize = 18.sp)
            }

            IconButton(
                onClick = onToggleMode,
                modifier = Modifier.testTag("ghost-toggle-mode-btn")
            ) {
                Text(
                    if (state.mode == GhostPreviewMode.AUTO_PLAY) "👣" else "🎬",
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Accept / Dismiss
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("ghost-accept-btn"),
                colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Primary),
                enabled = state.isActive
            ) {
                Text("Accept")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ghost-dismiss-btn")
            ) {
                Text("Try Something Else")
            }
        }
    }
}

@Composable
fun EngineThinkingPanel(
    state: GhostPreviewState,
    modifier: Modifier = Modifier
) {
    if (!state.showThinking || state.thinking == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChessColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("engine-thinking-panel")
    ) {
        Text(
            text = "🧠 Computer Thinking",
            color = ChessColors.Accent,
            fontSize = 14.sp,
            modifier = Modifier.testTag("thinking-title")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.thinking!!.description,
            color = ChessColors.OnSurface,
            fontSize = 12.sp,
            modifier = Modifier.testTag("thinking-description")
        )

        if (state.thinking!!.threats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⚠️ Threats: ${state.thinking!!.threats.joinToString(", ")}",
                color = ChessColors.Error,
                fontSize = 11.sp,
                modifier = Modifier.testTag("thinking-threats")
            )
        }

        if (state.thinking!!.strategicNotes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 ${state.thinking!!.strategicNotes.joinToString(". ")}",
                color = ChessColors.OnSurface,
                fontSize = 11.sp,
                modifier = Modifier.testTag("thinking-strategy")
            )
        }

        state.analysis?.let { analysis ->
            if (analysis.commentary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = analysis.commentary,
                    color = ChessColors.OnSurface,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag("thinking-commentary")
                )
            }
        }
    }
}
