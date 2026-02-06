package chess.core

import kotlin.test.*

class GameStateTest {

    @Test
    fun newGameStartsWithInitialBoard() {
        val game = GameState.new()
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            game.toFen()
        )
    }

    @Test
    fun newGameIsInProgress() {
        val game = GameState.new()
        assertEquals(GameStatus.IN_PROGRESS, game.status)
    }

    @Test
    fun newGameHasEmptyMoveHistory() {
        val game = GameState.new()
        assertTrue(game.moveHistory.isEmpty())
        assertEquals(0, game.currentMoveIndex)
    }

    @Test
    fun makeLegalMoveUpdatesBoard() {
        val game = GameState.new()
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        val newGame = game.makeMove(move)
        assertEquals(PieceColor.BLACK, newGame.board.activeColor)
        assertEquals(1, newGame.moveHistory.size)
        assertEquals(move, newGame.moveHistory[0])
    }

    @Test
    fun makeIllegalMoveThrows() {
        val game = GameState.new()
        val illegalMove = Move(Square(4, 1), Square(4, 5)) // e2-e6 (not a legal pawn move)
        assertFailsWith<IllegalArgumentException> {
            game.makeMove(illegalMove)
        }
    }

    @Test
    fun undoMoveRestoresPreviousState() {
        val game = GameState.new()
        val move = Move(Square(4, 1), Square(4, 3)) // e2-e4
        val afterMove = game.makeMove(move)
        val afterUndo = afterMove.undoMove()
        assertEquals(game.toFen(), afterUndo.toFen())
        assertTrue(afterUndo.moveHistory.isEmpty())
    }

    @Test
    fun undoOnEmptyHistoryThrows() {
        val game = GameState.new()
        assertFailsWith<IllegalArgumentException> {
            game.undoMove()
        }
    }

    @Test
    fun multipleMovesAndUndos() {
        var game = GameState.new()
        val moves = listOf(
            Move(Square(4, 1), Square(4, 3)), // e2-e4
            Move(Square(4, 6), Square(4, 4)), // e7-e5
            Move(Square(6, 0), Square(5, 2)), // Ng1-f3
        )
        for (move in moves) {
            game = game.makeMove(move)
        }
        assertEquals(3, game.currentMoveIndex)

        game = game.undoMove()
        assertEquals(2, game.currentMoveIndex)
        assertEquals(PieceColor.WHITE, game.board.activeColor)
    }

    @Test
    fun legalMovesReturnsCorrectMoves() {
        val game = GameState.new()
        assertEquals(20, game.legalMoves().size)
    }

    @Test
    fun fromFenCreatesCorrectState() {
        val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val game = GameState.fromFen(fen)
        assertEquals(fen, game.toFen())
    }

    @Test
    fun detectsCheckmateStatus() {
        // Fool's mate position - white is checkmated
        val game = GameState.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 0 1")
        // The status is evaluated when making the move that leads to this position
        // So we create a game that's already in this state and check
        val freshGame = GameState(
            board = game.board,
            status = if (MoveGenerator.isCheckmate(game.board)) {
                if (game.board.activeColor == PieceColor.WHITE) GameStatus.BLACK_WINS else GameStatus.WHITE_WINS
            } else GameStatus.IN_PROGRESS
        )
        assertEquals(GameStatus.BLACK_WINS, freshGame.status)
    }

    @Test
    fun playFoolsMateSequence() {
        var game = GameState.new()
        game = game.makeMove(Move(Square(5, 1), Square(5, 2))) // f2-f3
        game = game.makeMove(Move(Square(4, 6), Square(4, 4))) // e7-e5
        game = game.makeMove(Move(Square(6, 1), Square(6, 3))) // g2-g4
        game = game.makeMove(Move(Square(3, 7), Square(7, 3))) // Qd8-h4#
        assertEquals(GameStatus.BLACK_WINS, game.status)
    }

    @Test
    fun cannotMoveAfterGameOver() {
        var game = GameState.new()
        game = game.makeMove(Move(Square(5, 1), Square(5, 2))) // f3
        game = game.makeMove(Move(Square(4, 6), Square(4, 4))) // e5
        game = game.makeMove(Move(Square(6, 1), Square(6, 3))) // g4
        game = game.makeMove(Move(Square(3, 7), Square(7, 3))) // Qh4#
        assertFailsWith<IllegalArgumentException> {
            game.makeMove(Move(Square(0, 1), Square(0, 2))) // any move
        }
    }
}
