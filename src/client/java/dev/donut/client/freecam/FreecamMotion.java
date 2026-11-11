package dev.donut.client.freecam;

import dev.donut.client.config.FreecamSettings;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Pure camera math. Nothing here reads or writes the player. */
public final class FreecamMotion {
    public static final double SCROLL_STEP = 1.15;

    private FreecamMotion() { }

    /** Moves the current velocity toward the target; smoothing 0 returns the target unchanged. */
    public static Vec3 smoothVelocity(Vec3 current, Vec3 target, double smoothing) {
        double s = Double.isFinite(smoothing) ? Math.clamp(smoothing, 0, 0.95) : 0;
        if (s <= 0) return target;
        Vec3 next = current.add(target.subtract(current).scale(1 - s));
        return next.lengthSqr() < 1.0e-8 && target.lengthSqr() < 1.0e-8 ? Vec3.ZERO : next;
    }

    /**
     * One scroll notch multiplies or divides speed by 15% within the supported range. Not rounded, so the
     * many tiny fractional deltas a trackpad sends still accumulate; displays round instead.
     */
    public static double scrollSpeed(double speed, double notches) {
        if (!Double.isFinite(speed)) speed = 1;
        if (!Double.isFinite(notches) || notches == 0) return Math.clamp(speed, 0.1, FreecamSettings.MAX_SPEED);
        double next = speed * Math.pow(SCROLL_STEP, Math.clamp(notches, -10, 10));
        return Math.clamp(next, 0.1, FreecamSettings.MAX_SPEED);
    }

    /** Minecraft yaw (x) and pitch (y), in degrees, for a camera at {@code from} facing {@code to}. */
    public static Vec2 lookAngles(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.hypot(delta.x, delta.z);
        if (horizontal < 1.0e-7 && Math.abs(delta.y) < 1.0e-7) return null;
        float yaw = horizontal < 1.0e-7 ? Float.NaN : (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-delta.y, horizontal));
        return new Vec2(yaw, Math.clamp(pitch, -90, 90));
    }

    /** Keeps the camera at a fixed offset from a moving target. */
    public static Vec3 followPosition(Vec3 target, Vec3 offset) { return target.add(offset); }

    /** Clamps a camera position to finite, supported world bounds. */
    public static Vec3 clampToWorld(Vec3 position) {
        return new Vec3(Math.clamp(position.x, -FreecamSettings.MAX_HORIZONTAL, FreecamSettings.MAX_HORIZONTAL),
                Math.clamp(position.y, FreecamSettings.MIN_Y, FreecamSettings.MAX_Y),
                Math.clamp(position.z, -FreecamSettings.MAX_HORIZONTAL, FreecamSettings.MAX_HORIZONTAL));
    }
}
