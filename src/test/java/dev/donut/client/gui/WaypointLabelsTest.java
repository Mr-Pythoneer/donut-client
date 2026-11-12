package dev.donut.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.waypoints.Waypoint;
import dev.donut.client.waypoints.Waypoints;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WaypointLabelsTest {
    /** Camera rotation exactly as vanilla Camera#setRotation builds it from yaw and pitch. */
    private static Quaternionf camera(float yaw, float pitch) {
        return new Quaternionf().rotationYXZ((float) Math.PI - yaw * ((float) Math.PI / 180), -pitch * ((float) Math.PI / 180), 0);
    }

    private static Matrix4f matrix(float yaw, float pitch) {
        return WaypointLabels.viewProjection(new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 1000), camera(yaw, pitch));
    }

    @Test void pointsAheadProjectToTheMiddleAndBehindAreSkipped() {
        // Yaw 0 faces south (+Z).
        var center = WaypointLabels.project(matrix(0, 0), new Vec3(0, 0, 20), 320, 180);
        assertNotNull(center);
        assertEquals(160, center[0], 0.01);
        assertEquals(90, center[1], 0.01);
        assertNull(WaypointLabels.project(matrix(0, 0), new Vec3(0, 0, -20), 320, 180));
        // Facing east (+X, yaw -90), a point to the south is on the right; above is up.
        var right = WaypointLabels.project(matrix(-90, 0), new Vec3(20, 0, 3), 320, 180);
        assertNotNull(right);
        assertTrue(right[0] > 160);
        var up = WaypointLabels.project(matrix(-90, 0), new Vec3(20, 4, 0), 320, 180);
        assertTrue(up[1] < 90);
        assertNull(WaypointLabels.project(matrix(-90, 0), new Vec3(-20, 0, 0), 320, 180));
    }

    private static Matrix4f bobbed(float yaw, float pitch, Matrix4f bob) {
        return WaypointLabels.viewProjection(new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 1000), bob, camera(yaw, pitch));
    }

    private static Matrix4f walking(float walk, float bob) { return WaypointLabels.bobPose(-1, 0, 0, 1, -1, true, walk, bob); }

    private static void assertIdentity(Matrix4f pose) {
        assertTrue(pose.equals(new Matrix4f(), 1.0e-6f), pose::toString);
    }

    @Test void noBobWhenTheOptionIsOffOrStill() {
        // View Bobbing off: walking values are ignored entirely.
        assertIdentity(WaypointLabels.bobPose(-1, 0, 0, 1, -1, false, 0.37f, 0.09f));
        // On, but standing still (bob 0) or not hurt: nothing to apply.
        assertIdentity(walking(12.3f, 0));
        // An identity bob pose gives exactly the old unbobbed projection.
        var plain = WaypointLabels.viewProjection(new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, 0.05f, 1000), camera(30, 10));
        assertTrue(plain.equals(bobbed(30, 10, new Matrix4f()), 1.0e-6f));
    }

    @Test void viewBobMatchesVanillaFormula() {
        // walk 0.5: sin(PI/2) = 1, cos(PI/2) = 0, so the shift is purely sideways by bob/2 plus the two tilts.
        float bob = 0.1f;
        var pose = walking(0.5f, bob);
        var origin = pose.transformPosition(new Vector3f());
        assertEquals(0.05f, origin.x, 1.0e-4f);
        assertEquals(0, origin.y, 1.0e-4f);
        assertEquals(0, origin.z, 1.0e-6f);
        var expected = new Matrix4f().translate(0.05f, -Math.abs(Mth.cos(0.5f * (float) Math.PI) * bob), 0)
                .rotate(new Quaternionf().rotationZ(0.3f * Mth.DEG_TO_RAD))
                .rotate(new Quaternionf().rotationX(Math.abs(Mth.cos(0.5f * (float) Math.PI - 0.2f) * bob) * 5 * Mth.DEG_TO_RAD));
        assertTrue(expected.equals(pose, 1.0e-6f), pose::toString);
        // walk 0: no sideways shift, the view dips by the full bob.
        var dip = walking(0, bob).transformPosition(new Vector3f());
        assertEquals(0, dip.x, 1.0e-6f);
        assertEquals(-bob, dip.y, 1.0e-6f);
    }

    @Test void bobbingMovesLabelsLikeTheWorld() {
        // The same bob pose applied to the world and to the label: a beam's base, top and label all shift together
        // and the label stays on the line between the beam's ends.
        var bob = walking(0.5f, 0.12f);
        Vec3 base = new Vec3(-2, -3, 20), top = new Vec3(-2, 6, 20), label = new Vec3(-2, 1.5, 20);
        var still = WaypointLabels.project(matrix(0, 0), label, 1280, 800);
        var moving = WaypointLabels.project(bobbed(0, 0, bob), label, 1280, 800);
        assertNotNull(still);
        assertNotNull(moving);
        assertTrue(Math.hypot(moving[0] - still[0], moving[1] - still[1]) > 2, "Bobbing did not move the label");
        var a = WaypointLabels.project(bobbed(0, 0, bob), base, 1280, 800);
        var b = WaypointLabels.project(bobbed(0, 0, bob), top, 1280, 800);
        // Distance from the label to the projected beam line, in pixels.
        double cross = (b[0] - a[0]) * (moving[1] - a[1]) - (b[1] - a[1]) * (moving[0] - a[0]);
        assertEquals(0, cross / Math.hypot(b[0] - a[0], b[1] - a[1]), 0.05);
    }

    @Test void hurtTiltFollowsVanillaAndTheDamageTiltOption() {
        // hurt/duration = 0.5^(1/4) makes sin(t^4 * PI) = 1: a full 14 degree roll at Damage Tilt 1.
        int duration = 10;
        float hurt = (float) (Math.pow(0.5, 0.25) * duration);
        var tilted = WaypointLabels.bobPose(hurt, duration, 0, 1, -1, false, 0, 0);
        var expected = new Matrix4f().rotate(new Quaternionf().rotationZ(-14 * Mth.DEG_TO_RAD));
        assertTrue(expected.equals(tilted, 1.0e-4f), tilted::toString);
        var half = WaypointLabels.bobPose(hurt, duration, 0, 0.5, -1, false, 0, 0);
        assertTrue(new Matrix4f().rotate(new Quaternionf().rotationZ(-7 * Mth.DEG_TO_RAD)).equals(half, 1.0e-4f), half::toString);
        // Damage Tilt 0, not hurt, or a zero duration: no tilt.
        assertIdentity(WaypointLabels.bobPose(hurt, duration, 0, 0, -1, false, 0, 0));
        assertIdentity(WaypointLabels.bobPose(-0.5f, duration, 0, 1, -1, false, 0, 0));
        assertIdentity(WaypointLabels.bobPose(hurt, 0, 0, 1, -1, false, 0, 0));
        // The roll axis turns with the hurt direction: Y(-dir) * Z(roll) * Y(dir).
        var sideways = WaypointLabels.bobPose(hurt, duration, 90, 1, -1, false, 0, 0);
        var turned = new Matrix4f().rotate(new Quaternionf().rotationY(-90 * Mth.DEG_TO_RAD))
                .rotate(new Quaternionf().rotationZ(-14 * Mth.DEG_TO_RAD)).rotate(new Quaternionf().rotationY(90 * Mth.DEG_TO_RAD));
        assertTrue(turned.equals(sideways, 1.0e-4f), sideways::toString);
        // Dying rolls the view up to 40 - 8000/220 degrees.
        var dead = WaypointLabels.bobPose(-1, duration, 0, 1, 20, false, 0, 0);
        assertTrue(new Matrix4f().rotate(new Quaternionf().rotationZ((40 - 8000f / 220) * Mth.DEG_TO_RAD)).equals(dead, 1.0e-5f), dead::toString);
    }

    @Test void labelsShowDistanceAndLinkedDimension() {
        var waypoint = new Waypoint("Base", 800, 64, 0, Waypoints.OVERWORLD, "mp:x");
        var linked = new Waypoints.Shown(waypoint, 100, 64, 0, true);
        assertEquals("Base · 100m (Overworld)", WaypointLabels.label(linked, new Vec3(0.5, 64.5, 0.5), true));
        assertEquals("Base (Overworld)", WaypointLabels.label(linked, new Vec3(0.5, 64.5, 0.5), false));
        var own = new Waypoints.Shown(waypoint, 800, 64, 0, false);
        assertEquals("Base", WaypointLabels.label(own, null, true));
    }
}
