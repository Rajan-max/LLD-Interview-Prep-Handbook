package problems.TicTacToeSystem;

import java.util.*;

/**
 * TIC TAC TOE - Complete Implementation
 *
 * Key Features:
 * - Clean separation: Game (workflow) vs Board (grid logic)
 * - Optimized win check: only checks row/col/diags of last move
 * - Validated state transitions (can't move after game over)
 * - Turn enforcement (wrong player rejected)
 * - No concurrency needed (turn-based, single-threaded)
 */

// ============================================================================
// ENUMS
// ============================================================================

enum Mark { X, O, EMPTY }

enum GameState { IN_PROGRESS, WON, DRAW }

// ============================================================================
// PLAYER — Immutable identity
// ============================================================================

class Player {
    private final String name;
    private final Mark mark;

    public Player(String name, Mark mark) {
        this.name = name;
        this.mark = mark;
    }

    public String getName() { return name; }
    public Mark getMark() { return mark; }

    @Override
    public String toString() { return name + "(" + mark + ")"; }
}

// ============================================================================
// BOARD — Grid state + placement + win detection
// ============================================================================

class Board {
    private static final int SIZE = 3;
    private final Mark[][] grid;
    private int moveCount;

    public Board() {
        grid = new Mark[SIZE][SIZE];
        reset();
    }

    /** Place mark at (row, col). Returns false if invalid. */
    public boolean placeMark(int row, int col, Mark mark) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        if (grid[row][col] != Mark.EMPTY) return false;
        grid[row][col] = mark;
        moveCount++;
        return true;
    }

    /** Check if the last move at (row, col) resulted in a win. O(1) for 3×3. */
    public boolean checkWin(int row, int col, Mark mark) {
        // Check row
        if (grid[row][0] == mark && grid[row][1] == mark && grid[row][2] == mark) return true;
        // Check column
        if (grid[0][col] == mark && grid[1][col] == mark && grid[2][col] == mark) return true;
        // Check main diagonal (row == col)
        if (row == col && grid[0][0] == mark && grid[1][1] == mark && grid[2][2] == mark) return true;
        // Check anti-diagonal (row + col == 2)
        if (row + col == 2 && grid[0][2] == mark && grid[1][1] == mark && grid[2][0] == mark) return true;
        return false;
    }

    public boolean isFull() { return moveCount == SIZE * SIZE; }

    public Mark getCell(int row, int col) { return grid[row][col]; }

    public void reset() {
        for (Mark[] row : grid) Arrays.fill(row, Mark.EMPTY);
        moveCount = 0;
    }

    public String display() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char ch = switch (grid[r][c]) {
                    case X -> 'X';
                    case O -> 'O';
                    case EMPTY -> '.';
                };
                sb.append(ch);
                if (c < SIZE - 1) sb.append(" | ");
            }
            sb.append("\n");
            if (r < SIZE - 1) sb.append("--|---|--\n");
        }
        return sb.toString();
    }
}

// ============================================================================
// GAME — Orchestrator (turns, state, win/draw detection)
// ============================================================================

class Game {
    private final Board board;
    private final Player playerX;
    private final Player playerO;
    private Player currentPlayer;
    private GameState state;
    private Player winner;

    public Game(String playerXName, String playerOName) {
        this.board = new Board();
        this.playerX = new Player(playerXName, Mark.X);
        this.playerO = new Player(playerOName, Mark.O);
        this.currentPlayer = playerX;
        this.state = GameState.IN_PROGRESS;
        this.winner = null;
    }

    /**
     * Core method: validate → place → check win/draw → switch turn.
     * Returns true if move accepted, false if rejected.
     */
    public boolean makeMove(Player player, int row, int col) {
        if (state != GameState.IN_PROGRESS) return false;
        if (player != currentPlayer) return false;
        if (!board.placeMark(row, col, player.getMark())) return false;

        if (board.checkWin(row, col, player.getMark())) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerX) ? playerO : playerX;
        }
        return true;
    }

    public void reset() {
        board.reset();
        currentPlayer = playerX;
        state = GameState.IN_PROGRESS;
        winner = null;
    }

    public Player getCurrentPlayer() { return currentPlayer; }
    public GameState getState() { return state; }
    public Player getWinner() { return winner; }
    public Player getPlayerX() { return playerX; }
    public Player getPlayerO() { return playerO; }
    public Board getBoard() { return board; }
}

// ============================================================================
// DEMO AND TESTS
// ============================================================================

public class TicTacToeSystemComplete {

    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   TIC TAC TOE — LLD Implementation Demo                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        testXWinsByRow();
        testOWinsByColumn();
        testWinByMainDiagonal();
        testWinByAntiDiagonal();
        testDraw();
        testInvalidMoveOccupied();
        testInvalidMoveWrongTurn();
        testMoveAfterGameOver();
        testOutOfBounds();
        testReset();
        testMinimumWin();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // --- Test 1: X wins by completing top row ---
    static void testXWinsByRow() {
        System.out.println("Test 1: X Wins by Row");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        game.makeMove(x, 0, 0); // X
        game.makeMove(o, 1, 0); // O
        game.makeMove(x, 0, 1); // X
        game.makeMove(o, 1, 1); // O
        game.makeMove(x, 0, 2); // X wins — top row

        check("State is WON", game.getState() == GameState.WON);
        check("Winner is X (Alice)", game.getWinner() == x);
        System.out.println("  Board:\n" + indent(game.getBoard().display()));
    }

    // --- Test 2: O wins by column ---
    static void testOWinsByColumn() {
        System.out.println("Test 2: O Wins by Column");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        game.makeMove(x, 0, 0); // X
        game.makeMove(o, 0, 1); // O
        game.makeMove(x, 1, 0); // X
        game.makeMove(o, 1, 1); // O
        game.makeMove(x, 2, 2); // X (not winning)
        game.makeMove(o, 2, 1); // O wins — middle column

        check("State is WON", game.getState() == GameState.WON);
        check("Winner is O (Bob)", game.getWinner() == o);
        System.out.println();
    }

    // --- Test 3: Win by main diagonal ---
    static void testWinByMainDiagonal() {
        System.out.println("Test 3: X Wins by Main Diagonal");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        game.makeMove(x, 0, 0); // X
        game.makeMove(o, 0, 1); // O
        game.makeMove(x, 1, 1); // X
        game.makeMove(o, 0, 2); // O
        game.makeMove(x, 2, 2); // X wins — main diagonal

        check("State is WON", game.getState() == GameState.WON);
        check("Winner is X", game.getWinner() == x);
        System.out.println();
    }

    // --- Test 4: Win by anti-diagonal ---
    static void testWinByAntiDiagonal() {
        System.out.println("Test 4: O Wins by Anti-Diagonal");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        game.makeMove(x, 0, 0); // X
        game.makeMove(o, 0, 2); // O
        game.makeMove(x, 2, 2); // X
        game.makeMove(o, 1, 1); // O
        game.makeMove(x, 2, 0); // X
        game.makeMove(o, 2, 0); // O — invalid (occupied), returns false
        // Let's redo: X played 2,0 so O needs another move
        // Actually X at (2,0) blocks anti-diag. Let me redo this test.

        Game g2 = new Game("Alice", "Bob");
        Player x2 = g2.getPlayerX(), o2 = g2.getPlayerO();
        g2.makeMove(x2, 1, 0); // X
        g2.makeMove(o2, 0, 2); // O
        g2.makeMove(x2, 2, 2); // X
        g2.makeMove(o2, 1, 1); // O
        g2.makeMove(x2, 0, 0); // X
        g2.makeMove(o2, 2, 0); // O wins — anti-diagonal (0,2), (1,1), (2,0)

        check("State is WON", g2.getState() == GameState.WON);
        check("Winner is O", g2.getWinner() == o2);
        System.out.println();
    }

    // --- Test 5: Draw ---
    static void testDraw() {
        System.out.println("Test 5: Draw (All Cells Filled, No Winner)");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        // Classic draw pattern:
        // X | O | X
        // X | X | O
        // O | X | O
        game.makeMove(x, 0, 0); game.makeMove(o, 0, 1);
        game.makeMove(x, 0, 2); game.makeMove(o, 1, 2);
        game.makeMove(x, 1, 0); game.makeMove(o, 2, 0);
        game.makeMove(x, 1, 1); game.makeMove(o, 2, 2);
        game.makeMove(x, 2, 1); // last cell

        check("State is DRAW", game.getState() == GameState.DRAW);
        check("No winner", game.getWinner() == null);
        System.out.println();
    }

    // --- Test 6: Invalid move — occupied cell ---
    static void testInvalidMoveOccupied() {
        System.out.println("Test 6: Invalid Move — Occupied Cell");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        game.makeMove(x, 0, 0);
        boolean result = game.makeMove(o, 0, 0); // occupied!

        check("Move on occupied cell rejected", !result);
        check("Still O's turn (move didn't count)", game.getCurrentPlayer() == o);
        System.out.println();
    }

    // --- Test 7: Invalid move — wrong turn ---
    static void testInvalidMoveWrongTurn() {
        System.out.println("Test 7: Invalid Move — Wrong Player's Turn");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        boolean result = game.makeMove(o, 0, 0); // O tries to go first!

        check("Wrong turn rejected", !result);
        check("Still X's turn", game.getCurrentPlayer() == x);
        System.out.println();
    }

    // --- Test 8: Move after game over ---
    static void testMoveAfterGameOver() {
        System.out.println("Test 8: Move After Game Over");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        // X wins quickly
        game.makeMove(x, 0, 0); game.makeMove(o, 1, 0);
        game.makeMove(x, 0, 1); game.makeMove(o, 1, 1);
        game.makeMove(x, 0, 2); // X wins

        boolean result = game.makeMove(o, 2, 2); // game is over!
        check("Move after game over rejected", !result);
        check("State still WON", game.getState() == GameState.WON);
        System.out.println();
    }

    // --- Test 9: Out of bounds ---
    static void testOutOfBounds() {
        System.out.println("Test 9: Out of Bounds Move");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX();

        check("Row -1 rejected", !game.makeMove(x, -1, 0));
        check("Row 3 rejected", !game.makeMove(x, 3, 0));
        check("Col -1 rejected", !game.makeMove(x, 0, -1));
        check("Col 3 rejected", !game.makeMove(x, 0, 3));
        check("Still X's turn (no valid move made)", game.getCurrentPlayer() == x);
        System.out.println();
    }

    // --- Test 10: Reset ---
    static void testReset() {
        System.out.println("Test 10: Reset Game");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        // Play to completion
        game.makeMove(x, 0, 0); game.makeMove(o, 1, 0);
        game.makeMove(x, 0, 1); game.makeMove(o, 1, 1);
        game.makeMove(x, 0, 2); // X wins
        check("Game over before reset", game.getState() == GameState.WON);

        game.reset();
        check("State is IN_PROGRESS after reset", game.getState() == GameState.IN_PROGRESS);
        check("No winner after reset", game.getWinner() == null);
        check("X goes first after reset", game.getCurrentPlayer() == x);
        check("Board cell (0,0) is empty after reset", game.getBoard().getCell(0, 0) == Mark.EMPTY);

        // Can play again
        check("Can make move after reset", game.makeMove(x, 1, 1));
        System.out.println();
    }

    // --- Test 11: Minimum win (5 moves) ---
    static void testMinimumWin() {
        System.out.println("Test 11: Minimum Win (5 Total Moves)");
        System.out.println("-".repeat(56));
        Game game = new Game("Alice", "Bob");
        Player x = game.getPlayerX(), o = game.getPlayerO();

        // X: (0,0), (1,1), (2,2) — diagonal win in 5 moves total
        game.makeMove(x, 0, 0); // move 1
        game.makeMove(o, 0, 1); // move 2
        game.makeMove(x, 1, 1); // move 3
        game.makeMove(o, 0, 2); // move 4
        game.makeMove(x, 2, 2); // move 5 — X wins

        check("Win possible in 5 moves", game.getState() == GameState.WON);
        check("X wins", game.getWinner() == x);
        System.out.println();
    }

    // --- Helpers ---
    static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ FAIL: " + name);
            failed++;
        }
    }

    static String indent(String s) {
        return Arrays.stream(s.split("\n")).map(line -> "    " + line)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
