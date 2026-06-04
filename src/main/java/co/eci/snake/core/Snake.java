package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents a snake on the board.
 *
 * CONCURRENCY
 * ───────────
 * body (ArrayDeque) is NOT thread-safe. It is accessed concurrently by:
 *   - The SnakeRunner thread   → advance()
 *   - The EDT (Swing paint)    → snapshot()
 *   - The SnakeRunner thread (inside Board.step()) → head()
 *
 * Fix: head(), snapshot(), and advance() are synchronized on 'this' (the Snake
 * instance). Only these three critical regions are protected; turn() and
 * direction() access 'direction' which is already volatile — no sync needed.
 *
 * Lock ordering in the system:
 *   Board.step()  →  synchronized(board)  →  then synchronized(snake)
 *   Paint thread  →  synchronized(board) [released]  →  then synchronized(snake) [snapshot]
 *   The order board→snake is never inverted, so no deadlock is possible.
 */
public final class Snake {

    private final int index;
    private final Deque<Position> body = new ArrayDeque<>();
    private volatile Direction direction;
    private int maxLength = 5;

    // Life state — volatile for cross-thread visibility without extra sync
    private volatile boolean dead = false;
    private volatile long deathTime = Long.MAX_VALUE;

    private Snake(int index, Position start, Direction dir) {
        this.index = index;
        body.addFirst(start);
        this.direction = dir;
    }

    public static Snake of(int index, int x, int y, Direction dir) {
        return new Snake(index, new Position(x, y), dir);
    }

    // ── Simple getters ───────────────────────────────────────────────────────

    public int index() { return index; }

    /** direction is volatile: written by turn(), visible to other threads without sync. */
    public Direction direction() { return direction; }

    // ── Critical regions on body ─────────────────────────────────────────────

    /** Critical region: reads peekFirst() concurrently with advance(). */
    public synchronized Position head() {
        return body.peekFirst();
    }

    /**
     * Critical region: creates a defensive copy for the paint thread.
     * Without sync, EDT could iterate body while SnakeRunner modifies it
     * → ConcurrentModificationException.
     */
    public synchronized Deque<Position> snapshot() {
        return new ArrayDeque<>(body);
    }

    /**
     * Critical region: modifies body (addFirst, removeLast).
     * Called from Board.step(), which already holds the Board lock.
     * Snake lock is acquired inside the Board lock (order: board→snake).
     */
    public synchronized void advance(Position newHead, boolean grow) {
        body.addFirst(newHead);
        if (grow) maxLength++;
        while (body.size() > maxLength) body.removeLast();
    }

    // ── Direction (no sync needed: volatile) ─────────────────────────────────

    public void turn(Direction dir) {
        if ((direction == Direction.UP    && dir == Direction.DOWN)  ||
            (direction == Direction.DOWN  && dir == Direction.UP)    ||
            (direction == Direction.LEFT  && dir == Direction.RIGHT) ||
            (direction == Direction.RIGHT && dir == Direction.LEFT)) {
            return;
        }
        this.direction = dir;
    }

    // ── Life state ────────────────────────────────────────────────────────────

    /**
     * Marks the snake as dead with a timestamp.
     * The !dead guard prevents overwriting the first deathTime if called twice.
     */
    public void markDead() {
        if (!dead) {
            dead = true;
            deathTime = System.currentTimeMillis();
        }
    }

    public boolean isDead()    { return dead; }
    public long    deathTime() { return deathTime; }
}
