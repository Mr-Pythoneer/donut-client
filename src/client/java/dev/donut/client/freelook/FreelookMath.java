/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.freelook;

/** Pure camera-angle math for Freelook. */
public final class FreelookMath {
    /** Vanilla's mouse-to-degrees factor in {@code Entity.turn}. */
    public static final double DEGREES_PER_UNIT = 0.15;
    public static final float MIN_PITCH = -90, MAX_PITCH = 90;

    private FreelookMath() { }

    /** Yaw after a mouse turn, wrapped to [-180, 180) so long orbits never lose float precision. */
    public static float turnYaw(float yaw, double dx) {
        if (!Float.isFinite(yaw)) yaw = 0;
        if (!Double.isFinite(dx)) return wrap(yaw);
        return wrap((float) (yaw + dx * DEGREES_PER_UNIT));
    }

    /** Pitch after a mouse turn, clamped like vanilla; {@code invert} flips the vertical direction. */
    public static float turnPitch(float pitch, double dy, boolean invert) {
        if (!Float.isFinite(pitch)) pitch = 0;
        if (!Double.isFinite(dy)) return Math.clamp(pitch, MIN_PITCH, MAX_PITCH);
        return Math.clamp((float) (pitch + (invert ? -dy : dy) * DEGREES_PER_UNIT), MIN_PITCH, MAX_PITCH);
    }

    public static float wrap(float degrees) {
        float value = degrees % 360;
        if (value >= 180) value -= 360;
        if (value < -180) value += 360;
        return value;
    }

    /**
     * Scales vanilla's computed third-person distance so the configured distance replaces vanilla's default 4 blocks
     * while keeping the player's scale and camera-distance attribute.
     */
    public static float scaleDistance(float vanillaDistance, double configured) {
        if (!Float.isFinite(vanillaDistance) || vanillaDistance <= 0) return vanillaDistance;
        if (!Double.isFinite(configured)) return vanillaDistance;
        return (float) (vanillaDistance * configured / 4.0);
    }
}
