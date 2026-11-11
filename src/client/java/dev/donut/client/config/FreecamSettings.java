package dev.donut.client.config;

public final class FreecamSettings {
    public static final double MAX_SPEED = 10.0;
    public static final int MAX_HORIZONTAL = 29_999_984, MIN_Y = -2048, MAX_Y = 4096;
    /** Freecam always starts off when joining a new session. */
    public transient boolean enabled = false;
    public boolean favorite = false;
    public double speed = 1.0;
    public double boostMultiplier = 3.0;
    /** 0 keeps instant movement; higher values ease acceleration and deceleration. */
    public double smoothing = 0.0;
    public boolean scrollAdjustsSpeed = true;
    public boolean speedReadout = true;
    /** Start from the last camera pose in the same world instead of the player's eyes. */
    public boolean resumeLastPosition = false;
    public boolean followNearestPlayer = false;
    public int followRange = 64;
    public boolean lookAtTarget = false;
    public int targetX = 0, targetY = 64, targetZ = 0;

    public void sanitize() {
        speed = Double.isFinite(speed) ? Math.clamp(speed, 0.1, MAX_SPEED) : 1.0;
        boostMultiplier = Double.isFinite(boostMultiplier) ? Math.clamp(boostMultiplier, 1.0, 10.0) : 3.0;
        smoothing = Double.isFinite(smoothing) ? Math.clamp(smoothing, 0.0, 0.95) : 0.0;
        followRange = Math.clamp(followRange, 8, 128);
        targetX = Math.clamp(targetX, -MAX_HORIZONTAL, MAX_HORIZONTAL);
        targetY = Math.clamp(targetY, MIN_Y, MAX_Y);
        targetZ = Math.clamp(targetZ, -MAX_HORIZONTAL, MAX_HORIZONTAL);
    }

    /** Parses one whole-block look-at coordinate; throws with a readable reason when invalid. */
    public static int parseCoordinate(String text, boolean vertical) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a whole number for every coordinate.");
        if (!value.matches("[+-]?\\d{1,9}")) throw new IllegalArgumentException("\"" + value + "\" is not a whole number.");
        int parsed = Integer.parseInt(value);
        int min = vertical ? MIN_Y : -MAX_HORIZONTAL, max = vertical ? MAX_Y : MAX_HORIZONTAL;
        if (parsed < min || parsed > max)
            throw new IllegalArgumentException((vertical ? "Y" : "X/Z") + " must be between " + min + " and " + max + ".");
        return parsed;
    }
}
