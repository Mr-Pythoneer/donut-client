/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.hudwidgets;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/** Counts your own left/right mouse presses over the last second. The clock is injectable for tests. */
public final class ClickCounter {
    public static final int LEFT = 0, RIGHT = 1;
    public static final long WINDOW_MILLIS = 1000;
    /** Bounded so a stuck or macro'd button can't grow memory. */
    private static final int MAX_TRACKED = 200;
    private static final ClickCounter INSTANCE = new ClickCounter(System::currentTimeMillis);

    private final LongSupplier clock;
    private final ArrayDeque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    private long totalLeft, totalRight;

    public ClickCounter(LongSupplier clock) { this.clock = clock; }

    public static ClickCounter get() { return INSTANCE; }

    public synchronized void click(int button) {
        ArrayDeque<Long> times = times(button);
        if (times == null) return;
        long now = clock.getAsLong();
        times.addLast(now);
        while (times.size() > MAX_TRACKED) times.removeFirst();
        if (button == LEFT) totalLeft++; else totalRight++;
        prune(times, now);
    }

    public synchronized int cps(int button) {
        ArrayDeque<Long> times = times(button);
        if (times == null) return 0;
        prune(times, clock.getAsLong());
        return times.size();
    }

    /** Presses counted since start (or the last reset); used by tests to check real clicks arrived. */
    public synchronized long total(int button) { return button == LEFT ? totalLeft : button == RIGHT ? totalRight : 0; }

    public synchronized void reset() {
        left.clear();
        right.clear();
        totalLeft = totalRight = 0;
    }

    private ArrayDeque<Long> times(int button) { return button == LEFT ? left : button == RIGHT ? right : null; }

    private static void prune(ArrayDeque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MILLIS) times.removeFirst();
    }
}
