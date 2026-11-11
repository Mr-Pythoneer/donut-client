package dev.donut.client.zoom;

import dev.donut.client.config.ZoomSettings;

/** Pure zoom math. */
public final class ZoomMath {
    /** Each scroll notch changes the zoom factor by 25%. */
    public static final double SCROLL_STEP = 1.25;
    /** Smooth zoom covers half the remaining distance each tick. */
    public static final double SMOOTHING = 0.5;

    private ZoomMath() { }

    public static double scroll(double factor, double notches) {
        if (!Double.isFinite(factor)) factor = 4;
        if (!Double.isFinite(notches)) return Math.clamp(factor, ZoomSettings.MIN_FACTOR, ZoomSettings.MAX_FACTOR);
        double next = factor * Math.pow(SCROLL_STEP, Math.clamp(notches, -10, 10));
        return Math.clamp(next, ZoomSettings.MIN_FACTOR, ZoomSettings.MAX_FACTOR);
    }

    /** One tick toward the target factor; snaps when close or when smoothing is off. */
    public static double step(double current, double target, boolean smooth) {
        if (!smooth) return target;
        double next = current + (target - current) * SMOOTHING;
        return Math.abs(next - target) < 0.01 ? target : next;
    }
}
