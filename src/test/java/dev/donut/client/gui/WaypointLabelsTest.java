package dev.donut.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.waypoints.Waypoint;
import dev.donut.client.waypoints.Waypoints;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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

    @Test void labelsShowDistanceAndLinkedDimension() {
        var waypoint = new Waypoint("Base", 800, 64, 0, Waypoints.OVERWORLD, "mp:x");
        var linked = new Waypoints.Shown(waypoint, 100, 64, 0, true);
        assertEquals("Base · 100m (Overworld)", WaypointLabels.label(linked, new Vec3(0.5, 64.5, 0.5), true));
        assertEquals("Base (Overworld)", WaypointLabels.label(linked, new Vec3(0.5, 64.5, 0.5), false));
        var own = new Waypoints.Shown(waypoint, 800, 64, 0, false);
        assertEquals("Base", WaypointLabels.label(own, null, true));
    }
}
