package chess.speech

import chess.core.PieceType

class CommentaryGenerator(
    private val strings: StringResolver = StringResolver("en")
) {
    fun generateCommentary(events: List<GameEvent>): String? {
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
        is GameEvent.IllegalMoveAttempt -> 3
        is GameEvent.OpeningDetected -> 4
        is GameEvent.PieceCaptured -> 5
        is GameEvent.Promotion -> 6
        is GameEvent.Castling -> 7
        is GameEvent.Blunder -> 8
        is GameEvent.GoodMove -> 9
        is GameEvent.AdvantageShift -> 10
        is GameEvent.HangingPiece -> 11
        is GameEvent.UnclaimedCapture -> 12
        is GameEvent.GhostPreviewStarted -> 13
        is GameEvent.GhostAccepted -> 14
        is GameEvent.GhostDismissed -> 15
        is GameEvent.MoveUndone -> 16
        is GameEvent.GameStarted -> 17
        is GameEvent.GameStartedAsBlack -> 17
        is GameEvent.PlayerMoved -> 99
        is GameEvent.ComputerMoved -> 99
    }

    private fun commentFor(event: GameEvent): String? = when (event) {
        is GameEvent.Checkmate ->
            if (event.playerWon) BanterLines.pick(BanterLines.checkmatePlayerWins)
            else BanterLines.pick(BanterLines.checkmateComputerWins)
        is GameEvent.Stalemate -> BanterLines.pick(BanterLines.stalemate)
        is GameEvent.Check ->
            if (event.isPlayerChecked) BanterLines.pick(BanterLines.check)
            else BanterLines.pick(BanterLines.checkByPlayer)
        is GameEvent.IllegalMoveAttempt ->
            if (event.inCheck) BanterLines.pick(BanterLines.illegalMoveInCheck)
            else BanterLines.pick(BanterLines.illegalMoveGeneral)
        is GameEvent.OpeningDetected -> BanterLines.pick(
            BanterLines.openingDetected, mapOf("opening" to event.openingName)
        )
        is GameEvent.PieceCaptured -> captureComment(event)
        is GameEvent.Promotion -> {
            val params = mapOf("piece" to BanterLines.pieceName(event.pieceType))
            if (event.isPlayerPromotion) BanterLines.pick(BanterLines.promotion, params)
            else BanterLines.pick(BanterLines.promotionByComputer, params)
        }
        is GameEvent.Castling ->
            if (event.isPlayerCastling) BanterLines.pick(BanterLines.castling)
            else BanterLines.pick(BanterLines.castlingByComputer)
        is GameEvent.Blunder ->
            if (event.isPlayerBlunder) BanterLines.pick(BanterLines.blunder)
            else BanterLines.pick(BanterLines.blunderByComputer)
        is GameEvent.GoodMove ->
            if (event.isPlayerMove) {
                if (event.evalGain > 2.0) BanterLines.pick(BanterLines.greatMove)
                else BanterLines.pick(BanterLines.goodMove)
            } else null // Don't praise computer's own moves
        is GameEvent.AdvantageShift ->
            if (event.playerLeading) BanterLines.pick(BanterLines.playerTakingLead)
            else BanterLines.pick(BanterLines.computerTakingLead)
        is GameEvent.HangingPiece -> {
            val lines = if (event.isPlayerPiece) BanterLines.hangingPieceWarning else BanterLines.hangingPieceComputer
            BanterLines.pick(
                lines,
                mapOf("piece" to BanterLines.pieceName(event.pieceType), "square" to event.square.toAlgebraic())
            )
        }
        is GameEvent.UnclaimedCapture -> BanterLines.pick(
            BanterLines.unclaimedCapture,
            mapOf("piece" to BanterLines.pieceName(event.pieceType))
        )
        is GameEvent.GhostPreviewStarted -> null  // Intentionally silent
        is GameEvent.GhostAccepted -> null
        is GameEvent.GhostDismissed -> null
        is GameEvent.MoveUndone -> BanterLines.pick(BanterLines.moveUndone)
        is GameEvent.GameStarted -> BanterLines.pick(BanterLines.gameStart)
        is GameEvent.GameStartedAsBlack -> BanterLines.pick(BanterLines.gameStartAsBlack)
        is GameEvent.PlayerMoved -> null
        is GameEvent.ComputerMoved -> null
    }

    private fun captureComment(event: GameEvent.PieceCaptured): String {
        val params = mapOf("piece" to BanterLines.pieceName(event.capturedType))
        return if (event.isPlayerCapture) {
            when (event.capturedType) {
                PieceType.QUEEN -> BanterLines.pick(BanterLines.captureQueenByPlayer)
                else -> BanterLines.pick(BanterLines.captureByPlayer, params)
            }
        } else {
            when (event.capturedType) {
                PieceType.QUEEN -> BanterLines.pick(BanterLines.captureQueenByComputer)
                else -> BanterLines.pick(BanterLines.captureByComputer, params)
            }
        }
    }
}
