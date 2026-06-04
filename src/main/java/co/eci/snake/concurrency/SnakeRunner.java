package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Autonomous movement thread for a single snake.
 *
 * CONCURRENCY
 * ───────────
 * Each snake runs in its own virtual thread (see SnakeApp).
 * All threads share the same Board (Board lock in step()) and each has its
 * own Snake (Snake lock in head/advance/snapshot).
 *
 * PAUSE
 * ─────
 * At the start of every iteration, barrier.waitIfPaused() is called.
 * If the game is paused the thread enters wait() (no CPU) until the UI
 * calls barrier.resume() → notifyAll().
 * This fixes the original bug where clock.pause() only stopped repaints
 * while snakes kept moving.
 *
 * DEATH
 * ─────
 * A snake dies when its head enters the body of another snake.
 * Obstacles still cause a bounce (original game rule), not death.
 */
public final class SnakeRunner implements Runnable {

    private final Snake snake;
    private final Board board;
    private final PauseBarrier barrier;
    private final List<Snake> allSnakes;   // read-only reference to all snakes

    private static final int BASE_SLEEP_MS  = 80;
    private static final int TURBO_SLEEP_MS = 40;
    private int turboTicks = 0;

    public SnakeRunner(Snake snake, Board board, PauseBarrier barrier, List<Snake> allSnakes) {
        this.snake     = snake;
        this.board     = board;
        this.barrier   = barrier;
        this.allSnakes = allSnakes;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !snake.isDead()) {

                // ── Cooperative pause point ───────────────────────────────────
                // No busy-wait: if paused, wait() releases CPU until notifyAll().
                barrier.waitIfPaused();

                // Re-check after waking: snake could have died while in wait()
                if (snake.isDead()) break;

                maybeTurn();
                var res = board.step(snake);

                switch (res) {
                    case HIT_OBSTACLE -> randomTurn();   // bounce: pick a random direction
                    case ATE_TURBO    -> turboTicks = 100;
                    default -> {
                        // Snake moved; check body-on-body collision with others
                        if (hitsOtherSnake(snake.head())) {
                            snake.markDead();
                            return;
                        }
                    }
                }

                int sleep = (turboTicks > 0) ? TURBO_SLEEP_MS : BASE_SLEEP_MS;
                if (turboTicks > 0) turboTicks--;
                Thread.sleep(sleep);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns true if head overlaps the body of any other living snake.
     *
     * Called AFTER board.step(), so the head position is already advanced.
     * Each snapshot() acquires the respective Snake lock; no other lock is
     * held at this point → no deadlock risk.
     */
    private boolean hitsOtherSnake(Position head) {
        for (Snake other : allSnakes) {
            if (other == snake || other.isDead()) continue;
            // snapshot() is synchronized on Snake → consistent body copy
            if (other.snapshot().contains(head)) return true;
        }
        return false;
    }

    private void maybeTurn() {
        double p = (turboTicks > 0) ? 0.05 : 0.10;
        if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
    }

    private void randomTurn() {
        var dirs = Direction.values();
        snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
    }
}
