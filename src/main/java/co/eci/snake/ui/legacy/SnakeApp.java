package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.PauseBarrier;
import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Main game window.
 *
 * PAUSE STATE
 * ───────────
 * Two mechanisms are paused/resumed together:
 *   1. GameClock    → controls repaints (60 ms / tick)
 *   2. PauseBarrier → controls SnakeRunner virtual threads
 *
 * The original code only paused the GameClock, leaving snakes running
 * while the screen stopped updating — an inconsistent state.
 *
 * On PAUSE, consistent statistics are shown:
 *   - Longest living snake (largest body size).
 *   - First snake to die (smallest deathTime).
 *
 * GAME OVER
 * ─────────
 * When all snakes are dead the clock tick detects it, stops the game,
 * and shows the last survivor.
 */
public final class SnakeApp extends JFrame {

    private final Board board;
    private final GamePanel gamePanel;
    private final JButton actionButton;
    private final GameClock clock;
    private final List<Snake> snakes;
    private final PauseBarrier barrier = new PauseBarrier();

    // Pause / game-over state — accessed on EDT only
    private boolean gamePaused = false;
    private boolean gameOver   = false;

    public SnakeApp() {
        super("The Snake Race");
        this.board = new Board(35, 28);

        int N = Integer.getInteger("snakes", 2);
        var mutableSnakes = new java.util.ArrayList<Snake>();
        for (int i = 0; i < N; i++) {
            int x = 2 + (i * 3) % board.width();
            int y = 2 + (i * 2) % board.height();
            var dir = Direction.values()[i % Direction.values().length];
            mutableSnakes.add(Snake.of(i, x, y, dir));
        }
        this.snakes = Collections.unmodifiableList(mutableSnakes);

        this.gamePanel    = new GamePanel(board, () -> snakes);
        this.actionButton = new JButton("Pausar");

        setLayout(new BorderLayout());
        add(gamePanel, BorderLayout.CENTER);
        add(actionButton, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        // Clock tick: repaint + game-over check
        this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(this::onTick));

        var exec = Executors.newVirtualThreadPerTaskExecutor();
        snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, barrier, snakes)));

        bindKeys();
        setVisible(true);
        clock.start();
    }

    // ── Clock tick (EDT) ─────────────────────────────────────────────────────

    private void onTick() {
        gamePanel.repaint();
        if (!gameOver && snakes.stream().allMatch(Snake::isDead)) {
            gameOver = true;
            gamePaused = false;
            clock.stop();
            barrier.resume();   // release any thread still in wait() — they will exit via isDead() check
            actionButton.setEnabled(false);
            showGameOver();
        }
    }

    // ── Pause / resume logic ─────────────────────────────────────────────────

    private void togglePause() {
        if (gameOver) return;

        if (!gamePaused) {
            // ── PAUSE ──
            gamePaused = true;
            clock.pause();
            barrier.pause();
            // Runners finish their current board.step() (atomic under Board lock)
            // then enter wait(). Stats may differ by at most 1 step per snake — acceptable.
            actionButton.setText("Reanudar");
            JOptionPane.showMessageDialog(
                    this,
                    buildPauseStats(),
                    "Game state — paused",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            // ── RESUME ──
            gamePaused = false;
            actionButton.setText("Pausar");
            barrier.resume();   // notifyAll() wakes all runners
            clock.resume();
        }
    }

    // ── Pause statistics ─────────────────────────────────────────────────────

    /**
     * Builds the statistics string in a consistent way.
     *
     * snapshot() on Snake is synchronized → atomic body reads.
     * No global lock is required because runners are in wait() (or finishing
     * their current step, which is atomic under the Board lock).
     */
    private String buildPauseStats() {
        var alive = snakes.stream().filter(s -> !s.isDead()).collect(Collectors.toList());
        var dead  = snakes.stream().filter(Snake::isDead).collect(Collectors.toList());

        var sb = new StringBuilder();
        sb.append("Alive: ").append(alive.size())
          .append("  /  Dead: ").append(dead.size()).append("\n\n");

        if (alive.isEmpty()) {
            sb.append("No snakes alive.\n");
        } else {
            Snake longest = alive.stream()
                    .max(Comparator.comparingInt(s -> s.snapshot().size()))
                    .orElseThrow();
            sb.append("Longest living snake : #")
              .append(longest.index())
              .append("  (length ").append(longest.snapshot().size()).append(")\n");
        }

        if (dead.isEmpty()) {
            sb.append("No snakes have died yet.");
        } else {
            Snake firstDead = dead.stream()
                    .min(Comparator.comparingLong(Snake::deathTime))
                    .orElseThrow();
            sb.append("First snake to die: #").append(firstDead.index());
        }

        return sb.toString();
    }

    // ── Game Over ─────────────────────────────────────────────────────────────

    private void showGameOver() {
        snakes.stream()
              .filter(s -> s.deathTime() != Long.MAX_VALUE)
              .max(Comparator.comparingLong(Snake::deathTime))
              .ifPresent(winner ->
                  JOptionPane.showMessageDialog(
                      this,
                      "Game over!\nLast snake standing: #" + winner.index(),
                      "Game Over",
                      JOptionPane.INFORMATION_MESSAGE));
    }

    // ── Key bindings ──────────────────────────────────────────────────────────

    private void bindKeys() {
        InputMap  im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = gamePanel.getActionMap();

        actionButton.addActionListener((ActionEvent e) -> togglePause());
        im.put(KeyStroke.getKeyStroke("SPACE"), "pause");
        am.put("pause", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { togglePause(); }
        });

        // Snake 0 → arrow keys
        Snake p1 = snakes.get(0);
        bindDir(im, am, "LEFT",  "p1-left",  () -> p1.turn(Direction.LEFT));
        bindDir(im, am, "RIGHT", "p1-right", () -> p1.turn(Direction.RIGHT));
        bindDir(im, am, "UP",    "p1-up",    () -> p1.turn(Direction.UP));
        bindDir(im, am, "DOWN",  "p1-down",  () -> p1.turn(Direction.DOWN));

        // Snake 1 → WASD
        if (snakes.size() > 1) {
            Snake p2 = snakes.get(1);
            bindChar(im, am, 'A', "p2-left",  () -> p2.turn(Direction.LEFT));
            bindChar(im, am, 'D', "p2-right", () -> p2.turn(Direction.RIGHT));
            bindChar(im, am, 'W', "p2-up",    () -> p2.turn(Direction.UP));
            bindChar(im, am, 'S', "p2-down",  () -> p2.turn(Direction.DOWN));
        }
    }

    private static void bindDir(InputMap im, ActionMap am,
                                 String key, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(key), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private static void bindChar(InputMap im, ActionMap am,
                                  char ch, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(ch), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    // ── Game panel ────────────────────────────────────────────────────────────

    public static final class GamePanel extends JPanel {

        private final Board board;
        private final SnakesSupplier snakesSupplier;
        private static final int CELL = 20;

        @FunctionalInterface
        public interface SnakesSupplier { List<Snake> get(); }

        public GamePanel(Board board, SnakesSupplier snakesSupplier) {
            this.board          = board;
            this.snakesSupplier = snakesSupplier;
            setPreferredSize(new Dimension(board.width() * CELL + 1, board.height() * CELL + 40));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawGrid(g2);
            drawObstacles(g2);
            drawMice(g2);
            drawTeleports(g2);
            drawTurbo(g2);
            drawSnakes(g2);

            g2.dispose();
        }

        private void drawGrid(Graphics2D g2) {
            g2.setColor(new Color(220, 220, 220));
            for (int x = 0; x <= board.width();  x++)
                g2.drawLine(x * CELL, 0, x * CELL, board.height() * CELL);
            for (int y = 0; y <= board.height(); y++)
                g2.drawLine(0, y * CELL, board.width() * CELL, y * CELL);
        }

        private void drawObstacles(Graphics2D g2) {
            g2.setColor(new Color(255, 102, 0));
            for (var p : board.obstacles()) {
                int x = p.x() * CELL, y = p.y() * CELL;
                g2.fillRect(x + 2, y + 2, CELL - 4, CELL - 4);
                g2.setColor(Color.RED);
                g2.drawLine(x + 4, y + 4,  x + CELL - 6, y + 4);
                g2.drawLine(x + 4, y + 8,  x + CELL - 6, y + 8);
                g2.drawLine(x + 4, y + 12, x + CELL - 6, y + 12);
                g2.setColor(new Color(255, 102, 0));
            }
        }

        private void drawMice(Graphics2D g2) {
            g2.setColor(Color.BLACK);
            for (var p : board.mice()) {
                int x = p.x() * CELL, y = p.y() * CELL;
                g2.fillOval(x + 4, y + 4, CELL - 8, CELL - 8);
                g2.setColor(Color.WHITE);
                g2.fillOval(x + 8, y + 8, CELL - 16, CELL - 16);
                g2.setColor(Color.BLACK);
            }
        }

        private void drawTeleports(Graphics2D g2) {
            Map<Position, Position> tp = board.teleports();
            g2.setColor(Color.RED);
            for (var entry : tp.entrySet()) {
                Position from = entry.getKey();
                int x = from.x() * CELL, y = from.y() * CELL;
                int[] xs = { x + 4, x + CELL - 4, x + CELL - 10, x + CELL - 10, x + 4 };
                int[] ys = { y + CELL / 2, y + CELL / 2, y + 4, y + CELL - 4, y + CELL / 2 };
                g2.fillPolygon(xs, ys, xs.length);
            }
        }

        private void drawTurbo(Graphics2D g2) {
            g2.setColor(Color.BLACK);
            for (var p : board.turbo()) {
                int x = p.x() * CELL, y = p.y() * CELL;
                int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
                int[] ys = { y + 2,  y + 2,  y + 8,  y + 8,  y + 16, y + 10 };
                g2.fillPolygon(xs, ys, xs.length);
            }
        }

        private void drawSnakes(Graphics2D g2) {
            // Color palette per snake index (cyclic for N > 4)
            Color[] PALETTE = {
                new Color(0, 170, 0),   // green   — snake 0
                new Color(0, 160, 180), // cyan    — snake 1
                new Color(180, 0, 180), // magenta — snake 2
                new Color(200, 130, 0), // orange  — snake 3
            };

            for (Snake s : snakesSupplier.get()) {
                var body = s.snapshot().toArray(new Position[0]);

                // Dead snake → flat gray; alive snake → color from palette
                Color base = s.isDead()
                        ? new Color(160, 160, 160)
                        : PALETTE[s.index() % PALETTE.length];

                for (int i = 0; i < body.length; i++) {
                    var p = body[i];
                    // Head-to-tail brightness gradient
                    int shade = s.isDead() ? 0 : Math.max(0, 40 - i * 4);
                    g2.setColor(new Color(
                            Math.min(255, base.getRed()   + shade),
                            Math.min(255, base.getGreen() + shade),
                            Math.min(255, base.getBlue()  + shade)));
                    g2.fillRect(p.x() * CELL + 2, p.y() * CELL + 2, CELL - 4, CELL - 4);

                    // Draw X on dead snake's head
                    if (i == 0 && s.isDead()) {
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawLine(p.x() * CELL + 4, p.y() * CELL + 4,
                                    p.x() * CELL + CELL - 6, p.y() * CELL + CELL - 6);
                        g2.drawLine(p.x() * CELL + CELL - 6, p.y() * CELL + 4,
                                    p.x() * CELL + 4, p.y() * CELL + CELL - 6);
                    }
                }
            }
        }
    }

    public static void launch() {
        SwingUtilities.invokeLater(SnakeApp::new);
    }
}
