package dev.donut.client.freecam;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.FreecamSettings;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FreecamMotionTest {
    private static final double EPSILON = 1.0e-6;

    @Test
    void zeroSmoothingKeepsInstantMovement() {
        Vec3 target = new Vec3(0.3, -0.4, 1.2);
        assertSame(target, FreecamMotion.smoothVelocity(new Vec3(5, 5, 5), target, 0));
        assertSame(target, FreecamMotion.smoothVelocity(Vec3.ZERO, target, Double.NaN), "Invalid smoothing must act like 0");
        assertSame(target, FreecamMotion.smoothVelocity(Vec3.ZERO, target, -1));
    }

    @Test
    void smoothingRampsUpAndConvergesWithoutOvershoot() {
        Vec3 target = new Vec3(1, 0, 0);
        Vec3 velocity = Vec3.ZERO;
        double previous = 0;
        for (int tick = 0; tick < 200; tick++) {
            velocity = FreecamMotion.smoothVelocity(velocity, target, 0.9);
            assertTrue(velocity.x >= previous - EPSILON && velocity.x <= 1 + EPSILON, "No overshoot at tick " + tick);
            if (tick == 0) assertEquals(0.1, velocity.x, EPSILON, "90% smoothing keeps 10% of the change per tick");
            previous = velocity.x;
        }
        assertEquals(1, velocity.x, 1.0e-6, "Velocity converges to the target");
        // Releasing all keys decelerates gradually to a full stop.
        Vec3 stopping = FreecamMotion.smoothVelocity(target, Vec3.ZERO, 0.5);
        assertEquals(0.5, stopping.x, EPSILON);
        for (int tick = 0; tick < 60; tick++) stopping = FreecamMotion.smoothVelocity(stopping, Vec3.ZERO, 0.5);
        assertEquals(Vec3.ZERO, stopping, "Tiny residual velocity snaps to zero");
    }

    @Test
    void smoothingIsClampedBelowOneSoTheCameraAlwaysMoves() {
        Vec3 next = FreecamMotion.smoothVelocity(Vec3.ZERO, new Vec3(1, 0, 0), 5);
        assertEquals(0.05, next.x, EPSILON, "Smoothing above 0.95 behaves as 0.95");
    }

    @Test
    void scrollStepsSpeedMultiplicativelyWithinBounds() {
        assertEquals(1.15, FreecamMotion.scrollSpeed(1, 1), EPSILON);
        assertEquals(1 / 1.15, FreecamMotion.scrollSpeed(1, -1), EPSILON);
        assertEquals(1.3225, FreecamMotion.scrollSpeed(1, 2), EPSILON);
        assertEquals(FreecamSettings.MAX_SPEED, FreecamMotion.scrollSpeed(9.5, 3));
        assertEquals(0.1, FreecamMotion.scrollSpeed(0.11, -5));
        assertEquals(2.5, FreecamMotion.scrollSpeed(2.5, 0), "No movement leaves speed unchanged");
        assertEquals(2.5, FreecamMotion.scrollSpeed(2.5, Double.NaN));
        assertEquals(1, FreecamMotion.scrollSpeed(Double.POSITIVE_INFINITY, 0), "Invalid speed falls back to the default");
        assertEquals(1.15 * 1.15, FreecamMotion.scrollSpeed(1, 2), EPSILON, "Two notches equal two separate notches");
    }

    @Test
    void tinyTrackpadDeltasAccumulateLikeWholeNotches() {
        double speed = 1;
        for (int i = 0; i < 100; i++) speed = FreecamMotion.scrollSpeed(speed, 0.01);
        assertEquals(1.15, speed, 1.0e-9, "100 deltas of 0.01 must equal one notch, not stall at 1.00");
    }

    @Test
    void lookAnglesMatchMinecraftRotationInEveryQuadrant() {
        Vec3 origin = new Vec3(10, 64, -10);
        assertAngles(origin, origin.add(0, 0, 5), 0, 0);       // South (+Z)
        assertAngles(origin, origin.add(-5, 0, 0), 90, 0);     // West (-X)
        assertAngles(origin, origin.add(0, 0, -5), 180, 0);    // North (-Z)
        assertAngles(origin, origin.add(5, 0, 0), -90, 0);     // East (+X)
        assertAngles(origin, origin.add(5, 5, 5), -45, -35.264389682754654);
        assertAngles(origin, origin.add(-3, -4, 0), 90, 53.13010235415598);
    }

    @Test
    void lookAnglesRoundTripThroughMinecraftsDirection() {
        Vec3 from = new Vec3(-123.25, 70.5, 456.75);
        Vec3 to = new Vec3(-140, 40, 470);
        var angles = FreecamMotion.lookAngles(from, to);
        Vec3 direction = Vec3.directionFromRotation(angles.y, angles.x);
        // Minecraft's direction uses a sine lookup table, accurate to roughly 1e-4.
        assertEquals(0, direction.subtract(to.subtract(from).normalize()).length(), 1.0e-3);
    }

    @Test
    void verticalAndCoincidentTargetsAreHandled() {
        var up = FreecamMotion.lookAngles(Vec3.ZERO, new Vec3(0, 10, 0));
        assertTrue(Float.isNaN(up.x), "Straight up keeps the current yaw");
        assertEquals(-90, up.y, EPSILON);
        var down = FreecamMotion.lookAngles(Vec3.ZERO, new Vec3(0, -10, 0));
        assertEquals(90, down.y, EPSILON);
        assertNull(FreecamMotion.lookAngles(Vec3.ZERO, Vec3.ZERO), "A target at the camera has no direction");
    }

    @Test
    void followKeepsTheOffsetAndClampsToTheWorld() {
        Vec3 offset = new Vec3(2, 3, -4);
        assertEquals(new Vec3(12, 67, -14), FreecamMotion.followPosition(new Vec3(10, 64, -10), offset));
        Vec3 clamped = FreecamMotion.clampToWorld(new Vec3(40_000_000, 9000, -40_000_000));
        assertEquals(new Vec3(FreecamSettings.MAX_HORIZONTAL, FreecamSettings.MAX_Y, -FreecamSettings.MAX_HORIZONTAL), clamped);
    }

    private static void assertAngles(Vec3 from, Vec3 to, double yaw, double pitch) {
        var angles = FreecamMotion.lookAngles(from, to);
        assertEquals(0, net.minecraft.util.Mth.wrapDegrees(angles.x - yaw), 1.0e-4, "yaw toward " + to);
        assertEquals(pitch, angles.y, 1.0e-4, "pitch toward " + to);
    }
}
