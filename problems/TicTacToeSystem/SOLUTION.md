# Tic Tac Toe - LLD Interview Solution ❌⭕
---

## 1) Requirements (~5 min)

**Prompt**: "Design Tic Tac Toe."

### Clarifying Questions

| Theme | Question | Answer |
|---|---|---|
| **Primary capabilities** | What operations? | Place mark, check winner, query state |
| **Primary capabilities** | Board size? | Fixed 3×3 grid |
| **Rules** | How do players alternate? | X always goes first, then O, alternating |
| **Rules** | Win condition? | Complete a row, column, or diagonal |
| **Rules** | Draw condition? | All 9 cells filled with no winner |
| **Error handling** | Invalid move? | Reject — occupied cell, wrong turn, game over |
| **Scope** | Concurrency? | No — single-threaded turn-based game |
| **Scope** | AI opponent? | Out of scope |

### Requirements

```
1. Two players alternate placing X and O on a 3×3 grid
2. A player wins by completing a row, column, or diagonal
3. The game ends in a draw if all 9 cells are filled with no winner
4. Invalid moves rejected (occupied cell, wrong player's turn, game already over)
5. System provides way to query current game state and reset the game

Out of Scope:
- UI/rendering layer
- AI opponent or move suggestions
- Networked multiplayer
- Variable board sizes (NxN)
- Undo/redo functionality
```

---

## 2) Entities & Relationships (~3 min)

```
Entities:
- Game    (orchestrator — manages turns, state, win/draw detection)
- Board   (3×3 grid — owns cell state, placement, win checking)
- Player  (name + mark — immutable)

NOT entities:
- Cell    (just a char/enum value in the grid — no behavior)
- Move    (just row + col params — no need for a class)

Relationships:
- Game → Board (has-a, 1:1)
- Game → Player (has-a, 2 players: X and O)
- Game tracks currentPlayer, state, winner
```

**Key decisions:**
- Board owns win-checking logic (it owns the grid data)
- Game owns turn management and state transitions (workflow rules)
- Player is immutable — just a name + mark (X or O)
- No concurrency needed — turn-based, single-threaded
- Win check is optimized: only check row/col/diagonals affected by the last move

---

## 3) Class Design (~10 min)

### Deriving State from Requirements

| Requirement | What must be tracked | Where |
|---|---|---|
| "3×3 grid" | grid: char[3][3] | Board |
| "Two players alternate" | playerX, playerO, currentPlayer | Game |
| "Wins by row/col/diagonal" | grid state (checked on each move) | Board |
| "Draw if all filled" | moveCount (9 = full) | Board |
| "Game ends" | state: IN_PROGRESS / WON / DRAW | Game |
| "Who won" | winner: Player (nullable) | Game |

### Deriving Behavior from Requirements

| Need | Method | On |
|---|---|---|
| Place a mark on the grid | placeMark(row, col, mark) → boolean | Board |
| Check if last move won | checkWin(row, col, mark) → boolean | Board |
| Check if board is full | isFull() → boolean | Board |
| Make a move (full workflow) | makeMove(player, row, col) → boolean | Game |
| Query game state | getState() → GameState | Game |
| Query winner | getWinner() → Player | Game |
| Reset game | reset() | Game |

### Class Outlines

```
enum Mark: X, O, EMPTY

enum GameState: IN_PROGRESS, WON, DRAW

class Player:
  - name: String
  - mark: Mark

class Board:
  - grid: Mark[3][3] (initialized to EMPTY)
  - moveCount: int

  + placeMark(row, col, mark) → boolean    // false if occupied or out of bounds
  + checkWin(row, col, mark) → boolean     // only checks row/col/diags of last move
  + isFull() → boolean                     // moveCount == 9
  + reset()
  + display() → String

class Game:                                 // Orchestrator
  - board: Board
  - playerX: Player
  - playerO: Player
  - currentPlayer: Player
  - state: GameState
  - winner: Player (nullable)

  + makeMove(player, row, col) → boolean
  + getCurrentPlayer() → Player
  + getState() → GameState
  + getWinner() → Player
  + reset()
```

### Key Principle

- **Game** owns workflow rules: is it this player's turn? Is the game still in progress?
- **Board** owns data rules: is this cell empty? Does this move complete a line?
- **Player** is just identity — no behavior beyond holding a name and mark

### Win Check Optimization

Instead of scanning the entire board after every move, only check the row, column, and diagonals that the last move touches. This is O(n) per move instead of O(n²).

---

## 4) Concurrency Control (~5 min)

**This problem does NOT require concurrency control.**

```
Why no concurrency?
- Turn-based game: only one player acts at a time
- Single-threaded: no parallel access to the board
- No shared mutable state across threads

If this were a networked multiplayer game:
- Synchronized makeMove to prevent race conditions
- But for a local game, it's unnecessary overhead
```

**Skip this section in the interview** — mention "single-threaded turn-based game, no concurrency needed" and move on.

---

## 5) Implementation (~10 min)

### Core Method: Game.makeMove — The Full Workflow

```java
public boolean makeMove(Player player, int row, int col) {
    // Edge case: game already over
    if (state != GameState.IN_PROGRESS) return false;

    // Edge case: wrong player's turn
    if (player != currentPlayer) return false;

    // Delegate placement to Board (checks bounds + occupied)
    if (!board.placeMark(row, col, player.getMark())) return false;

    // Check win (only row/col/diags affected by this move)
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
```

**What this demonstrates:**
- Guard clauses first: game over? wrong turn? invalid placement?
- Delegates grid logic to Board (encapsulation)
- Win check only after successful placement
- Turn switches only if game continues
- Returns boolean: true = move accepted, false = rejected

### Core Method: Board.placeMark

```java
public boolean placeMark(int row, int col, Mark mark) {
    if (row < 0 || row >= 3 || col < 0 || col >= 3) return false;
    if (grid[row][col] != Mark.EMPTY) return false;

    grid[row][col] = mark;
    moveCount++;
    return true;
}
```

### Core Method: Board.checkWin — Optimized for Last Move

```java
public boolean checkWin(int row, int col, Mark mark) {
    // Check row
    if (grid[row][0] == mark && grid[row][1] == mark && grid[row][2] == mark) return true;

    // Check column
    if (grid[0][col] == mark && grid[1][col] == mark && grid[2][col] == mark) return true;

    // Check main diagonal (only if move is on it)
    if (row == col) {
        if (grid[0][0] == mark && grid[1][1] == mark && grid[2][2] == mark) return true;
    }

    // Check anti-diagonal (only if move is on it)
    if (row + col == 2) {
        if (grid[0][2] == mark && grid[1][1] == mark && grid[2][0] == mark) return true;
    }

    return false;
}
```

**Why this is O(1) per check (for 3×3):**
- Only checks the row, column, and at most 2 diagonals that the last move touches
- Doesn't scan the entire board
- For NxN boards, this would be O(n) instead of O(n²)

### Verification: Walk Through a Scenario

```
Initial: board empty, currentPlayer = X, state = IN_PROGRESS

makeMove(X, 0, 0) → board[0][0]=X, no win, not full → switch to O. Returns true.
makeMove(O, 1, 1) → board[1][1]=O, no win, not full → switch to X. Returns true.
makeMove(X, 0, 1) → board[0][1]=X, no win, not full → switch to O. Returns true.
makeMove(O, 2, 2) → board[2][2]=O, no win, not full → switch to X. Returns true.
makeMove(X, 0, 2) → board[0][2]=X, checkWin(0,2,X):
  row 0: X,X,X → WIN! state=WON, winner=X. Returns true.

makeMove(O, 2, 0) → state != IN_PROGRESS → returns false (game over).

Board:
  X | X | X
  --|---|--
    | O |
  --|---|--
    |   | O

✓ Win detected on row completion. Game stops. Further moves rejected.
```

---

## 6) Testing Strategy (~3 min)

**Functional tests:**
- X wins by row (top row all X)
- O wins by column (middle column all O)
- Win by main diagonal
- Win by anti-diagonal
- Draw (all 9 cells filled, no winner)
- Game state transitions: IN_PROGRESS → WON, IN_PROGRESS → DRAW

**Invalid move tests:**
- Place on occupied cell → returns false
- Wrong player's turn → returns false
- Move after game is over → returns false
- Out-of-bounds coordinates → returns false

**Reset test:**
- Play a game to completion → reset → board empty, state IN_PROGRESS, X goes first

**Edge cases:**
- Win on the 5th move (minimum possible)
- Win on the 9th move (last cell completes a line)
- Attempt to place with wrong player object

---

## 7) Extensibility (~5 min)

**"How would you add undo functionality?"**
> "All state changes flow through makeMove. I'd add a move history stack (List of moves). Each successful move records (row, col, player). undo() pops the stack, clears that cell, reverts currentPlayer, and resets state if needed. Board and Game classes need minimal changes."

**"How would you support NxN boards?"**
> "Replace hardcoded 3 with a size parameter. Board becomes `Mark[n][n]`. Win check becomes a loop: check if all n cells in the row/col/diagonal match. checkWin goes from O(1) to O(n). Game and Player don't change."

**"How would you add an AI opponent?"**
> "Create an AIPlayer that implements a Player interface with a `chooseMove(board)` method. Strategies: random, minimax, alpha-beta pruning. Game calls `aiPlayer.chooseMove(board)` instead of waiting for input. Strategy pattern for different AI difficulties."

**"How would you add a move timer (time limit per turn)?"**
> "Add a Timer that starts when a turn begins. If it expires before makeMove is called, auto-forfeit that player. Game would need a `startTurn()` method that kicks off the timer."

**"How would you support more than 2 players?"**
> "Change playerX/playerO to a `List<Player>` with a circular index. Win condition stays the same (any player completing a line). currentPlayer cycles through the list."

---

### Complexity

| Operation | Time | Space |
|---|---|---|
| **makeMove** | O(1) | O(1) |
| **checkWin** | O(1) for 3×3, O(n) for NxN | O(1) |
| **isFull** | O(1) | O(1) |
| **Total space** | | O(n²) for the grid |

---

**Implementation**: See [TicTacToeSystemComplete.java](./TicTacToeSystemComplete.java)
