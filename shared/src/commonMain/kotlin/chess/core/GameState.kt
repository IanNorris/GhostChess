package chess.core

data class GameState(
    val board: Board,
    val moveHistory: List<Move> = emptyList(),
    val boardHistory: List<Board> = listOf(),
    val status: GameStatus = GameStatus.IN_PROGRESS
) {
    val currentMoveIndex: Int get() = moveHistory.size

    fun makeMove(move: Move): GameState {
        require(status == GameStatus.IN_PROGRESS) { "Game is over: $status" }
        val legalMoves = MoveGenerator.generateLegalMoves(board)
        require(move in legalMoves) { "Illegal move: ${move.toAlgebraic()}" }

        val newBoard = board.makeMove(move)
        val newHistory = moveHistory + move
        val newBoardHistory = boardHistory + board

        val newStatus = when {
            MoveGenerator.isCheckmate(newBoard) -> {
                if (newBoard.activeColor == PieceColor.WHITE) GameStatus.BLACK_WINS
                else GameStatus.WHITE_WINS
            }
            MoveGenerator.isDraw(newBoard) -> GameStatus.DRAW
            else -> GameStatus.IN_PROGRESS
        }

        return GameState(newBoard, newHistory, newBoardHistory, newStatus)
    }

    fun undoMove(): GameState {
        require(moveHistory.isNotEmpty()) { "No moves to undo" }
        val previousBoard = boardHistory.last()
        return GameState(
            previousBoard,
            moveHistory.dropLast(1),
            boardHistory.dropLast(1),
            GameStatus.IN_PROGRESS
        )
    }

    fun legalMoves(): List<Move> = MoveGenerator.generateLegalMoves(board)

    fun toFen(): String = board.toFen()

    companion object {
        fun new(): GameState = GameState(
            board = Board.initial(),
            boardHistory = listOf()
        )

        fun fromFen(fen: String): GameState = GameState(
            board = Board.fromFen(fen),
            boardHistory = listOf()
        )
    }
}

enum class GameStatus {
    IN_PROGRESS,
    WHITE_WINS,
    BLACK_WINS,
    DRAW
}
