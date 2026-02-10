package chess.ui.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import chess.core.*
import chess.ghost.GhostPreviewState
import chess.ui.theme.ChessColors

private const val PIECE_FONT_SIZE = 55
private const val ANIM_DURATION_MS = 600

/**
 * Compute waypoints for a ghost move animation.
 * Knights move in an L: longest leg first, then the short leg.
 * All other pieces move in a straight line from → to.
 * Returns list of (file, rank) waypoints including start and end.
 */
private fun animationWaypoints(move: Move, pieceType: PieceType?): List<Pair<Int, Int>> {
    val from = move.from
    val to = move.to
    if (pieceType == PieceType.KNIGHT) {
        val df = to.file - from.file
        val dr = to.rank - from.rank
        // Longest leg first
        val mid = if (kotlin.math.abs(df) > kotlin.math.abs(dr)) {
            Pair(to.file, from.rank)
        } else {
            Pair(from.file, to.rank)
        }
        return listOf(Pair(from.file, from.rank), mid, Pair(to.file, to.rank))
    }
    return listOf(Pair(from.file, from.rank), Pair(to.file, to.rank))
}

@Composable
fun ChessBoard(
    board: Board,
    selectedSquare: Square? = null,
    legalMoves: List<Move> = emptyList(),
    ghostState: GhostPreviewState = GhostPreviewState(),
    flipped: Boolean = false,
    threatSquares: Set<Square> = emptySet(),
    vulnerableSquares: Set<Square> = emptySet(),
    opponentAttackSquares: Set<Square> = emptySet(),
    engineAnimMove: Move? = null,
    boardBeforeEngineMove: Board? = null,
    onEngineAnimDone: () -> Unit = {},
    onAnimLand: () -> Unit = {},
    onSquareClick: (Square) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ghostBoard = ghostState.boardAtStep
    val ghostActive = ghostState.isActive && ghostBoard != null &&
        ghostState.currentStepIndex >= 0
    val animMove = ghostState.animatingMove

    // Track the move we're animating so we can detect new steps
    var animKey by remember { mutableStateOf(0) }
    var animating by remember { mutableStateOf(false) }
    var fadeOutActive by remember { mutableStateOf(false) }
    var prevAnimMove by remember { mutableStateOf<Move?>(null) }

    // Engine move animation state
    var engineAnimKey by remember { mutableStateOf(0) }
    var engineAnimating by remember { mutableStateOf(false) }
    var engineFadeOutActive by remember { mutableStateOf(false) }
    var prevEngineAnimMove by remember { mutableStateOf<Move?>(null) }

    // Detect new ghost step
    LaunchedEffect(ghostState.currentStepIndex, animMove) {
        if (animMove != null && animMove != prevAnimMove) {
            prevAnimMove = animMove
            animKey++
            animating = true
            fadeOutActive = false
        }
    }

    // Detect new engine move to animate
    LaunchedEffect(engineAnimMove) {
        if (engineAnimMove != null && engineAnimMove != prevEngineAnimMove) {
            prevEngineAnimMove = engineAnimMove
            engineAnimKey++
            engineAnimating = true
            engineFadeOutActive = false
        }
    }

    // Active animation: engine anim takes priority when ghost isn't active
    val isEngineAnim = engineAnimating || engineFadeOutActive
    val activeAnimMove = when {
        animating || fadeOutActive -> animMove
        isEngineAnim -> engineAnimMove
        else -> null
    }
    val activeAnimBoard = when {
        animating || fadeOutActive -> ghostState.boardBeforeStep
        isEngineAnim -> boardBeforeEngineMove
        else -> null
    }
    val isGhostAnim = animating || fadeOutActive

    // Determine the board to render for pieces during animation:
    // Hide the piece at the destination (it will be shown by the overlay)
    // and show the piece at the origin from the board-before-step
    val animFromSquare = if ((animating || engineAnimating) && activeAnimMove != null) activeAnimMove.from else null
    val animToSquare = if ((animating || engineAnimating) && activeAnimMove != null) activeAnimMove.to else null
    val boardBeforeStep = ghostState.boardBeforeStep

    BoxWithConstraints(modifier = modifier.testTag("chess-board")) {
        val totalWidth = maxWidth
        val labelWidth = 20.dp
        val boardWidth = totalWidth - labelWidth
        val squareSize = boardWidth / 8

        Column {
            for (displayRank in 0..7) {
                val rank = if (flipped) displayRank else 7 - displayRank
                Row {
                    Text(
                        text = "${rank + 1}",
                        modifier = Modifier
                            .width(labelWidth)
                            .height(squareSize)
                            .wrapContentHeight(Alignment.CenterVertically)
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

                        val ghostPiece = if (ghostActive) ghostBoard!![square] else null
                        val isGhostDiff = ghostActive && ghostPiece != piece

                        // During animation: show the pre-move board so captured pieces
                        // remain visible until the moving piece lands on them.
                        // The moving piece itself is hidden here (drawn by the overlay).
                        // During fade-out: hide on-board piece at destination (overlay handles it).
                        val isGhostAnimOrFade = animating || fadeOutActive
                        val isEngineAnimOrFade = engineAnimating || engineFadeOutActive
                        val displayPiece = when {
                            // Ghost animation
                            isGhostAnimOrFade && ghostActive && square == animFromSquare ->
                                null
                            isGhostAnimOrFade && ghostActive && square == animToSquare ->
                                if (animating) boardBeforeStep?.get(square)
                                else ghostPiece
                            // Engine move animation
                            isEngineAnimOrFade && !ghostActive && square == animFromSquare ->
                                null
                            isEngineAnimOrFade && !ghostActive && square == animToSquare ->
                                if (engineAnimating) boardBeforeEngineMove?.get(square)
                                else board[square]
                            ghostActive -> ghostPiece
                            else -> piece
                        }

                        val bgColor = when {
                            isSelected -> ChessColors.SelectedSquare
                            isCheck -> ChessColors.CheckHighlight
                            isGhostDiff -> if (isLightSquare) ChessColors.GhostMoveTo else ChessColors.GhostMoveFrom
                            square in threatSquares -> ChessColors.PlayerThreat
                            isLightSquare -> ChessColors.LightSquare
                            else -> ChessColors.DarkSquare
                        }

                        val isVulnerable = square in vulnerableSquares

                        Box(
                            modifier = Modifier
                                .size(squareSize)
                                .background(bgColor)
                                .then(
                                    if (isVulnerable) Modifier.border(3.dp, ChessColors.OpponentVulnerable)
                                    else if (isLegalTarget) Modifier.border(2.dp, ChessColors.LegalMoveHighlight)
                                    else Modifier
                                )
                                .clickable { onSquareClick(square) }
                                .testTag("square-${square.toAlgebraic()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayPiece != null) {
                                Text(
                                    text = PieceUnicode.get(displayPiece.type, displayPiece.color),
                                    fontSize = PIECE_FONT_SIZE.sp,
                                    textAlign = TextAlign.Center,
                                    color = if (ghostActive && isGhostDiff) ChessColors.GhostPiece else Color.Unspecified,
                                    modifier = Modifier.testTag(
                                        if (ghostActive && isGhostDiff) "ghost-piece-${square.toAlgebraic()}"
                                        else "piece-${square.toAlgebraic()}"
                                    )
                                )
                            }

                            // Red dot on squares the opponent can attack
                            if (square in opponentAttackSquares && displayPiece == null) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            ChessColors.OpponentAttackDot,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .testTag("attack-dot-${square.toAlgebraic()}")
                                )
                            }

                            if (isLegalTarget && piece == null && square !in opponentAttackSquares) {
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
                Spacer(modifier = Modifier.width(labelWidth))
                for (displayFile in 0..7) {
                    val file = if (flipped) 7 - displayFile else displayFile
                    Text(
                        text = "${'a' + file}",
                        modifier = Modifier
                            .width(squareSize)
                            .testTag("file-label-${'a' + file}"),
                        color = ChessColors.OnSurface,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Floating animated piece overlay + captured piece fade-out
        if ((animating || fadeOutActive) && animMove != null && boardBeforeStep != null) {
            val movingPiece = boardBeforeStep[animMove.from]
            val capturedPiece = boardBeforeStep[animMove.to]

            if (movingPiece != null) {
                val waypoints = animationWaypoints(animMove, movingPiece.type)

                // Animate progress from 0f to 1f
                val progress = remember(animKey) { Animatable(0f) }
                val captureAlpha = remember(animKey) { Animatable(1f) }

                LaunchedEffect(animKey) {
                    progress.snapTo(0f)
                    captureAlpha.snapTo(1f)
                    progress.animateTo(
                        1f,
                        animationSpec = tween(
                            durationMillis = ANIM_DURATION_MS * (waypoints.size - 1),
                            easing = LinearEasing
                        )
                    )
                    animating = false
                    onAnimLand()
                    // After landing, fade out captured piece
                    if (capturedPiece != null) {
                        fadeOutActive = true
                        captureAlpha.animateTo(
                            0f,
                            animationSpec = tween(durationMillis = 300, easing = LinearEasing)
                        )
                        fadeOutActive = false
                    }
                }

                if (animating) {
                    // Interpolate position along waypoints
                    val totalSegments = waypoints.size - 1
                    val rawSegment = progress.value * totalSegments
                    val segIndex = rawSegment.toInt().coerceIn(0, totalSegments - 1)
                    val segProgress = (rawSegment - segIndex).coerceIn(0f, 1f)

                    val startFile = waypoints[segIndex].first.toFloat()
                    val startRank = waypoints[segIndex].second.toFloat()
                    val endFile = waypoints[segIndex + 1].first.toFloat()
                    val endRank = waypoints[segIndex + 1].second.toFloat()

                    val currentFile = startFile + (endFile - startFile) * segProgress
                    val currentRank = startRank + (endRank - startRank) * segProgress

                    val displayFilePos = if (flipped) 7f - currentFile else currentFile
                    val displayRankPos = if (flipped) currentRank else 7f - currentRank

                    val squareSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { squareSize.toPx() }
                    val labelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { labelWidth.toPx() }

                    val offsetX = labelWidthPx + displayFilePos * squareSizePx
                    val offsetY = displayRankPos * squareSizePx

                    // Scale: lift up (1.0→1.35) at start, set down (1.35→1.0) at end
                    val liftScale = when {
                        progress.value < 0.15f -> 1f + 0.35f * (progress.value / 0.15f)
                        progress.value > 0.85f -> 1f + 0.35f * ((1f - progress.value) / 0.15f)
                        else -> 1.35f
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .size(squareSize)
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PieceUnicode.get(movingPiece.type, movingPiece.color),
                            fontSize = PIECE_FONT_SIZE.sp,
                            textAlign = TextAlign.Center,
                            color = ChessColors.GhostPiece,
                            modifier = Modifier.scale(liftScale)
                        )
                    }
                }

                // Fade-out overlay for captured piece after landing
                if (fadeOutActive && capturedPiece != null) {
                    val toFile = animMove.to.file
                    val toRank = animMove.to.rank
                    val dispFile = if (flipped) 7 - toFile else toFile
                    val dispRank = if (flipped) toRank else 7 - toRank

                    val squareSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { squareSize.toPx() }
                    val labelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { labelWidth.toPx() }

                    val ox = labelWidthPx + dispFile.toFloat() * squareSizePx
                    val oy = dispRank.toFloat() * squareSizePx

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(ox.toInt(), oy.toInt()) }
                            .size(squareSize)
                            .zIndex(5f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PieceUnicode.get(capturedPiece.type, capturedPiece.color),
                            fontSize = PIECE_FONT_SIZE.sp,
                            textAlign = TextAlign.Center,
                            color = ChessColors.GhostPiece.copy(alpha = captureAlpha.value),
                            modifier = Modifier.scale(1f)
                        )
                    }
                }
            }
        }

        // Engine move animation overlay (same waypoint system, normal piece colors)
        if ((engineAnimating || engineFadeOutActive) && engineAnimMove != null && boardBeforeEngineMove != null) {
            val movingPiece = boardBeforeEngineMove[engineAnimMove.from]
            val capturedPiece = boardBeforeEngineMove[engineAnimMove.to]

            if (movingPiece != null) {
                val waypoints = animationWaypoints(engineAnimMove, movingPiece.type)

                val progress = remember(engineAnimKey) { Animatable(0f) }
                val captureAlpha = remember(engineAnimKey) { Animatable(1f) }

                LaunchedEffect(engineAnimKey) {
                    progress.snapTo(0f)
                    captureAlpha.snapTo(1f)
                    progress.animateTo(
                        1f,
                        animationSpec = tween(
                            durationMillis = ANIM_DURATION_MS * (waypoints.size - 1),
                            easing = LinearEasing
                        )
                    )
                    engineAnimating = false
                    onAnimLand()
                    if (capturedPiece != null) {
                        engineFadeOutActive = true
                        captureAlpha.animateTo(
                            0f,
                            animationSpec = tween(durationMillis = 300, easing = LinearEasing)
                        )
                        engineFadeOutActive = false
                    }
                    onEngineAnimDone()
                }

                if (engineAnimating) {
                    val totalSegments = waypoints.size - 1
                    val rawSegment = progress.value * totalSegments
                    val segIndex = rawSegment.toInt().coerceIn(0, totalSegments - 1)
                    val segProgress = (rawSegment - segIndex).coerceIn(0f, 1f)

                    val startFile = waypoints[segIndex].first.toFloat()
                    val startRank = waypoints[segIndex].second.toFloat()
                    val endFile = waypoints[segIndex + 1].first.toFloat()
                    val endRank = waypoints[segIndex + 1].second.toFloat()

                    val currentFile = startFile + (endFile - startFile) * segProgress
                    val currentRank = startRank + (endRank - startRank) * segProgress

                    val displayFilePos = if (flipped) 7f - currentFile else currentFile
                    val displayRankPos = if (flipped) currentRank else 7f - currentRank

                    val squareSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { squareSize.toPx() }
                    val labelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { labelWidth.toPx() }

                    val offsetX = labelWidthPx + displayFilePos * squareSizePx
                    val offsetY = displayRankPos * squareSizePx

                    val liftScale = when {
                        progress.value < 0.15f -> 1f + 0.35f * (progress.value / 0.15f)
                        progress.value > 0.85f -> 1f + 0.35f * ((1f - progress.value) / 0.15f)
                        else -> 1.35f
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .size(squareSize)
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PieceUnicode.get(movingPiece.type, movingPiece.color),
                            fontSize = PIECE_FONT_SIZE.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.scale(liftScale)
                        )
                    }
                }

                if (engineFadeOutActive && capturedPiece != null) {
                    val toFile = engineAnimMove.to.file
                    val toRank = engineAnimMove.to.rank
                    val dispFile = if (flipped) 7 - toFile else toFile
                    val dispRank = if (flipped) toRank else 7 - toRank

                    val squareSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { squareSize.toPx() }
                    val labelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { labelWidth.toPx() }

                    val ox = labelWidthPx + dispFile.toFloat() * squareSizePx
                    val oy = dispRank.toFloat() * squareSizePx

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(ox.toInt(), oy.toInt()) }
                            .size(squareSize)
                            .zIndex(5f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PieceUnicode.get(capturedPiece.type, capturedPiece.color),
                            fontSize = PIECE_FONT_SIZE.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Unspecified.copy(alpha = captureAlpha.value),
                            modifier = Modifier.scale(1f)
                        )
                    }
                }
            }
        }
    }
}
