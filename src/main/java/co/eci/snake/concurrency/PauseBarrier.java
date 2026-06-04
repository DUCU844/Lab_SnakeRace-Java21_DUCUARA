package co.eci.snake.concurrency;

/**
 * Shared monitor for pausing and resuming all SnakeRunner threads.
 *
 * Same pattern as PrimeFinder (Part I):
 *   - Single lock object (this).
 *   - paused is read/written only inside synchronized(this).
 *   - while(paused) instead of if(paused) → prevents spurious wakeups.
 *   - notifyAll() wakes every suspended thread on resume.
 */
public final class PauseBarrier {

    private boolean paused = false;

    /**
     * If the game is paused, suspends the calling thread until resume() wakes it.
     * No busy-wait: the thread stays in wait() consuming no CPU.
     */
    public synchronized void waitIfPaused() throws InterruptedException {
        while (paused) {
            wait();
        }
    }

    /** Signals pause. Threads will suspend on their next call to waitIfPaused(). */
    public synchronized void pause() {
        paused = true;
    }

    /** Wakes all threads suspended in waitIfPaused(). */
    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public synchronized boolean isPaused() {
        return paused;
    }
}
