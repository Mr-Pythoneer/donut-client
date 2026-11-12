package dev.donut.client.automine;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Turns the player's real view toward an aim point a little on every rendered frame, the way a hand on a mouse does.
 * Rotating only on game ticks (20 per second) looks like stepping at high frame rates, because the local player's view
 * isn't interpolated between ticks. The turn eases out as it closes in and never exceeds the configured speed.
 *
 * <p>Only the player's own yaw/pitch change, exactly as mouse look would change them; vanilla sends them as usual.
 */
public final class SmoothLook {
    /** Fraction of the remaining angle closed per second (exponential ease-out). */
    private static final double EASE_PER_SECOND = 9;
    private static boolean active;
    private static float targetYaw, targetPitch, degreesPerSecond;
    private static long lastFrame;

    private SmoothLook() { }

    /** Aim at these angles at most {@code degreesPerTick} per game tick. A non-finite yaw keeps the current yaw. */
    public static void aim(Minecraft client, float yaw, float pitch, float degreesPerTick) {
        if (client.player == null) return;
        targetYaw = Float.isFinite(yaw) ? yaw : client.player.getYRot();
        targetPitch = Float.isFinite(pitch) ? Math.clamp(pitch, -90, 90) : client.player.getXRot();
        degreesPerSecond = Math.max(1, degreesPerTick) * 20;
        active = true;
    }

    public static void release() { active = false; }

    public static boolean active() { return active; }

    /** Called once per rendered frame. */
    public static void frame(Minecraft client) {
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 0 : Math.min(.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        var player = client.player;
        if (!active || player == null || seconds <= 0) return;
        float yawError = Mth.wrapDegrees(targetYaw - player.getYRot()), pitchError = targetPitch - player.getXRot();
        float[] step = step(yawError, pitchError, seconds, degreesPerSecond);
        player.setYRot(player.getYRot() + step[0]);
        player.setXRot(Math.clamp(player.getXRot() + step[1], -90, 90));
    }

    /** Yaw and pitch change for one frame: an eased share of the error, capped by the speed, snapping when tiny. */
    static float[] step(float yawError, float pitchError, double seconds, float degreesPerSecond) {
        float share = (float) (1 - Math.exp(-EASE_PER_SECOND * seconds)), max = (float) (degreesPerSecond * seconds);
        // Scale both axes together so diagonal turns follow a straight line instead of finishing one axis first.
        float yaw = yawError * share, pitch = pitchError * share, length = (float) Math.hypot(yaw, pitch);
        if (length > max && length > 0) { yaw *= max / length; pitch *= max / length; }
        if (Math.abs(yawError) < .05f) yaw = yawError;
        if (Math.abs(pitchError) < .05f) pitch = pitchError;
        return new float[] {yaw, pitch};
    }
}
