package chess.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chess.core.*
import chess.ghost.GhostPreviewState
import chess.ui.theme.ChessColors

@Composable
fun ChessBoard(
    board: Board,
    selectedSquare: Square? = null,
    legalMoves: List<Move> = emptyList(),
    ghostState: GhostPreviewState = GhostPreviewState(),
    flipped: Boolean = false,
    onSquareClick: (Square) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("chess-board")
    ) {
        for (displayRank in 0..7) {
            val rank = if (flipped) displayRank else 7 - displayRank
            Row {
                // Rank label
                Text(
                    text = "${rank + 1}",
                    modifier = Modifier
                        .width(20.dp)
                        .align(Alignment.CenterVertically)
                        .testTag("rank-label-${rank + 1}"),
                    color = ChessColors.OnSurface,
                    fontSize = 12.sp
                )

                for (displayFile in 0..7) {
                    val file = if (flipped) 7 - displayFile else displayFile
                    val square = Square(file, rank)
                    val piece = board[square]
                    val isLightSquare = (file + rank) % 2 == 1
                    val isSelected = square == selectedSquare
                    val isLegalTarget = legalMoves.any { it.to == square }
                    val isCheck = piece?.type == PieceType.KING &&
                        MoveGenerator.isInCheck(board, piece.color) &&
                        piece.color == board.activeColor

                    // Ghost state
                    val ghostBoard = ghostState.boardAtStep
                    val ghostActive = ghostState.isActive && ghostBoard != null &&
                        ghostState.currentStepIndex >= 0
                    val ghostPiece = if (ghostActive) ghostBoard!![square] else null
                    val isGhostDiff = ghostActive && ghostPiece != piece
                    val displayPiece = if (ghostActive) ghostPiece else piece

                    val bgColor = when {
                        isSelected -> ChessColors.SelectedSquare
                        isCheck -> ChessColors.CheckHighlight
                        isGhostDiff -> if (isLightSquare) ChessColors.GhostMoveTo else ChessColors.GhostMoveFrom
                        isLightSquare -> ChessColors.LightSquare
                        else -> ChessColors.DarkSquare
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(bgColor)
                            .then(
                                if (isLegalTarget) Modifier.border(2.dp, ChessColors.LegalMoveHighlight)
                                else Modifier
                            )
                            .clickable { onSquareClick(square) }
                            .testTag("square-${square.toAlgebraic()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Show piece: ghost board piece during ghost mode, real piece otherwise
                        if (displayPiece != null) {
                            Text(
                                text = PieceUnicode.get(displayPiece.type, displayPiece.color),
                                fontSize = 42.sp,
                                textAlign = TextAlign.Center,
                                color = if (ghostActive && isGhostDiff) ChessColors.GhostPiece else Color.Unspecified,
                                modifier = Modifier.testTag(
                                    if (ghostActive && isGhostDiff) "ghost-piece-${square.toAlgebraic()}"
                                    else "piece-${square.toAlgebraic()}"
                                )
                            )
                        }

                        // Legal move dot
                        if (isLegalTarget && piece == null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        ChessColors.LegalMoveHighlight,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .testTag("legal-move-${square.toAlgebraic()}")
                            )
                        }
                    }
                }
            }
        }

        // File labels
        Row {
            Spacer(modifier = Modifier.width(20.dp))
            for (displayFile in 0..7) {
                val file = if (flipped) 7 - displayFile else displayFile
                Text(
                    text = "${'a' + file}",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("file-label-${'a' + file}"),
                    color = ChessColors.OnSurface,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
