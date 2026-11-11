package dev.donut.client.config;

import dev.donut.client.waypoints.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Saved waypoints for every server plus how they are drawn. Waypoints are stored per server key. */
public final class WaypointSettings {
    public static final int MAX_WAYPOINTS = 500, MIN_LABEL_RANGE = 32, MAX_LABEL_RANGE = 4096;
    public boolean enabled, favorite;
    public List<Waypoint> waypoints = new ArrayList<>();
    public int opacity = 220;
    public boolean beams = true, labels = true, showDistance = true, showLinkedDimension = true;
    /** Labels farther than this many blocks from the camera are hidden. */
    public int labelRange = 512;

    public void sanitize() {
        List<Waypoint> kept = new ArrayList<>();
        if (waypoints != null) for (Waypoint waypoint : waypoints) {
            if (waypoint == null) continue;
            waypoint.sanitize();
            kept.add(waypoint);
            if (kept.size() >= MAX_WAYPOINTS) break;
        }
        waypoints = kept;
        opacity = Math.clamp(opacity, 0, 255);
        labelRange = Math.clamp(labelRange, MIN_LABEL_RANGE, MAX_LABEL_RANGE);
    }

    /** True when another waypoint can still be added. */
    public boolean hasRoom() {
        return waypoints == null || waypoints.stream().filter(Objects::nonNull).count() < MAX_WAYPOINTS;
    }
}
