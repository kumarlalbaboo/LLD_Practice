/*
-- Requirements: -
    - Size of the board should be scalable (e.g., 3x3, 4x4, etc.)
    - There are standard rules & should be further extendible for custom rules.
    - Allow in App notification for TicTacToe moves, wins, draws etc.
    - Support for multiple players and customizable symbols.
*/


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

class Game {
    private final Board board;
    private final Deque<Player> players = new ArrayDeque<>();
    private final TicTacToeRules rules;
    private final List<IObserver> observers = new ArrayList<>();
    private boolean started;
    private boolean gameOver;
    private boolean notifying;
    private Player winner;

    public Game(int boardSize, TicTacToeRules rules) {
        this.board = new Board(boardSize);
        this.rules = Objects.requireNonNull(rules, "Rules are required.");
    }

    public void addPlayer(Player player) {
        Objects.requireNonNull(player, "Player is required.");
        if (started) {
            throw new IllegalStateException("Cannot add players after the game starts.");
        }
        if (player.getSymbol().equals(board.getEmptySymbol())) {
            throw new IllegalArgumentException("The empty-cell symbol is reserved.");
        }
        for (Player existing : players) {
            if (existing.getId() == player.getId()
                    || existing.getSymbol().equals(player.getSymbol())) {
                throw new IllegalArgumentException("Player IDs and symbols must be unique.");
            }
        }
        if (players.size() >= (long) board.getSize() * board.getSize()) {
            throw new IllegalStateException("There cannot be more players than cells.");
        }
        players.addLast(player);
    }

    public void addObserver(IObserver observer) {
        Objects.requireNonNull(observer, "Observer is required.");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(IObserver observer) {
        observers.remove(observer);
    }

    public void play(Player player, int row, int col) {
        if (notifying) {
            throw new IllegalStateException("Cannot play from an observer callback.");
        }
        if (gameOver) {
            throw new IllegalStateException("The game is already over.");
        }
        if (players.size() < 2) {
            throw new IllegalStateException("At least two players are required.");
        }
        if (player != players.peekFirst()) {
            throw new IllegalArgumentException("It is not this player's turn.");
        }
        if (!rules.isValidMove(board, row, col)) {
            throw new IllegalArgumentException("Invalid move: (" + row + ", " + col + ").");
        }

        board.placeSymbol(row, col, player.getSymbol());
        started = true;
        if (rules.checkWin(board, player.getSymbol())) {
            winner = player;
            winner.incrementScore();
            gameOver = true;
        } else if (rules.checkDraw(board)) {
            gameOver = true;
        } else {
            players.addLast(players.removeFirst());
        }

        notifying = true;
        try {
            notifyObservers(new GameEvent(GameEventType.MOVE, player, row, col,
                    player.getName() + " placed " + player.getSymbol().getMark()
                            + " at (" + row + ", " + col + ")."));
            if (winner != null) {
                notifyObservers(new GameEvent(GameEventType.WIN, winner, row, col,
                        winner.getName() + " wins!"));
            } else if (gameOver) {
                notifyObservers(new GameEvent(GameEventType.DRAW, player, row, col,
                        "The game is a draw."));
            }
        } finally {
            notifying = false;
        }
    }

    private void notifyObservers(GameEvent event) {
        for (IObserver observer : new ArrayList<>(observers)) {
            try {
                observer.update(event);
            } catch (RuntimeException exception) {
                System.err.println("Observer notification failed: " + exception.getMessage());
            }
        }
    }

    public Player getCurrentPlayer() {
        return gameOver ? null : players.peekFirst();
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public Player getWinner() {
        return winner;
    }

    public Symbol getCell(int row, int col) {
        return board.getCell(row, col);
    }

    public void displayBoard() {
        board.displayBoard();
    }
}

class GameFactory {
    public static Game createGame(int boardSize, TicTacToeRules rules) {
        return new Game(boardSize, rules);
    }

    public static Game createGame(int boardSize, GameType type) {
        return createGame(boardSize, type, null);
    }

    public static Game createGame(int boardSize, GameType type, TicTacToeRules customRules) {
        Objects.requireNonNull(type, "Game type is required.");
        switch (type) {
            case STANDARD:
                if (customRules != null) {
                    throw new IllegalArgumentException("Use CUSTOM for custom rules.");
                }
                return new Game(boardSize, new StandardTicTacToeRules());
            case CUSTOM:
                if (customRules == null) {
                    throw new IllegalArgumentException("Custom games require custom rules.");
                }
                return new Game(boardSize, customRules);
            default:
                throw new IllegalArgumentException("Unsupported game type: " + type);
        }
    }
}

enum GameType {
    STANDARD,
    CUSTOM
}

class Player {
    private final int id;
    private final String name;
    private final Symbol symbol;
    private int score;

    public Player(int id, String name, Symbol symbol) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be blank.");
        }
        this.id = id;
        this.name = name.trim();
        this.symbol = Objects.requireNonNull(symbol, "Symbol is required.");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public int getScore() {
        return score;
    }

    public void incrementScore() {
        score++;
    }
}

enum GameEventType {
    MOVE,
    WIN,
    DRAW
}

final class GameEvent {
    private final GameEventType type;
    private final Player player;
    private final int row;
    private final int col;
    private final String message;

    public GameEvent(GameEventType type, Player player, int row, int col, String message) {
        this.type = Objects.requireNonNull(type, "Event type is required.");
        this.player = Objects.requireNonNull(player, "Player is required.");
        this.row = row;
        this.col = col;
        this.message = Objects.requireNonNull(message, "Message is required.");
    }

    public GameEventType getType() {
        return type;
    }

    public Player getPlayer() {
        return player;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public String getMessage() {
        return message;
    }
}

interface IObserver {
    void update(GameEvent event);
}

class ConcreteObserver implements IObserver {
    @Override
    public void update(GameEvent event) {
        System.out.println("[" + event.getType() + "] " + event.getMessage());
    }
}

interface TicTacToeRules {
    boolean checkWin(Board board, Symbol symbol);

    boolean checkDraw(Board board);

    boolean isValidMove(Board board, int row, int col);
}

class StandardTicTacToeRules implements TicTacToeRules {
    @Override
    public boolean checkWin(Board board, Symbol symbol) {
        Objects.requireNonNull(symbol, "Symbol is required.");
        if (symbol.equals(board.getEmptySymbol())) {
            return false;
        }
        int size = board.getSize();
        boolean mainDiagonal = true;
        boolean antiDiagonal = true;
        for (int row = 0; row < size; row++) {
            boolean fullRow = true;
            boolean fullColumn = true;
            for (int col = 0; col < size; col++) {
                fullRow &= symbol.equals(board.getCell(row, col));
                fullColumn &= symbol.equals(board.getCell(col, row));
            }
            if (fullRow || fullColumn) {
                return true;
            }
            mainDiagonal &= symbol.equals(board.getCell(row, row));
            antiDiagonal &= symbol.equals(board.getCell(row, size - 1 - row));
        }
        return mainDiagonal || antiDiagonal;
    }

    @Override
    public boolean checkDraw(Board board) {
        if (!board.isFull()) {
            return false;
        }
        for (int row = 0; row < board.getSize(); row++) {
            for (int col = 0; col < board.getSize(); col++) {
                Symbol symbol = board.getCell(row, col);
                if (checkWin(board, symbol)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isValidMove(Board board, int row, int col) {
        return board.isInBounds(row, col) && board.isCellEmpty(row, col);
    }
}

class Board {
    private final List<List<Symbol>> grid = new ArrayList<>();
    private final int size;
    private final Symbol emptySymbol = new Symbol("-");
    private int occupiedCells;

    public Board(int size) {
        if (size < 2) {
            throw new IllegalArgumentException("Board size must be at least 2.");
        }
        this.size = size;
        for (int row = 0; row < size; row++) {
            List<Symbol> cells = new ArrayList<>();
            for (int col = 0; col < size; col++) {
                cells.add(emptySymbol);
            }
            grid.add(cells);
        }
    }

    public boolean isInBounds(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    public boolean isCellEmpty(int row, int col) {
        return getCell(row, col).equals(emptySymbol);
    }

    public void placeSymbol(int row, int col, Symbol symbol) {
        Objects.requireNonNull(symbol, "Symbol is required.");
        if (symbol.equals(emptySymbol)) {
            throw new IllegalArgumentException("Cannot place the empty-cell symbol.");
        }
        if (!isCellEmpty(row, col)) {
            throw new IllegalArgumentException("Cell is already occupied.");
        }
        grid.get(row).set(col, symbol);
        occupiedCells++;
    }

    public Symbol getCell(int row, int col) {
        if (!isInBounds(row, col)) {
            throw new IllegalArgumentException("Cell is outside the board.");
        }
        return grid.get(row).get(col);
    }

    public int getSize() {
        return size;
    }

    public Symbol getEmptySymbol() {
        return emptySymbol;
    }

    public boolean isFull() {
        return occupiedCells == (long) size * size;
    }

    public void displayBoard() {
        for (List<Symbol> row : grid) {
            List<String> marks = new ArrayList<>();
            for (Symbol symbol : row) {
                marks.add(symbol.getMark());
            }
            System.out.println(String.join(" | ", marks));
        }
    }
}

final class Symbol {
    private final String mark;

    public Symbol(String mark) {
        if (mark == null || mark.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol mark cannot be blank.");
        }
        this.mark = mark.trim();
    }

    public String getMark() {
        return mark;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Symbol && mark.equals(((Symbol) other).mark);
    }

    @Override
    public int hashCode() {
        return mark.hashCode();
    }

    @Override
    public String toString() {
        return mark;
    }
}

public class TicTacToe {
    public static void main(String[] args) {
        Game game = GameFactory.createGame(3, GameType.STANDARD);
        Player alice = new Player(1, "Alice", new Symbol("X"));
        Player bob = new Player(2, "Bob", new Symbol("O"));
        game.addPlayer(alice);
        game.addPlayer(bob);
        game.addObserver(new ConcreteObserver());

        game.displayBoard();
        try (Scanner scanner = new Scanner(System.in)) {
            while (!game.isGameOver()) {
                Player currentPlayer = game.getCurrentPlayer();
                System.out.println(currentPlayer.getName() + " ("
                        + currentPlayer.getSymbol().getMark()
                        + "), enter row and column (0-2), or 'quit':");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("quit")) {
                    break;
                }
                String[] coordinates = input.split("\\s+");
                if (coordinates.length != 2) {
                    System.out.println("Enter two integers separated by a space.");
                    continue;
                }
                try {
                    int row = Integer.parseInt(coordinates[0]);
                    int col = Integer.parseInt(coordinates[1]);
                    game.play(currentPlayer, row, col);
                    game.displayBoard();
                    System.out.println();
                } catch (NumberFormatException exception) {
                    System.out.println("Row and column must be integers.");
                } catch (IllegalArgumentException exception) {
                    System.out.println(exception.getMessage());
                }
            }
        }
        System.out.println(alice.getName() + " score: " + alice.getScore());
        System.out.println(bob.getName() + " score: " + bob.getScore());
    }
}