package dev.donut.client.config;

import dev.donut.client.waypoints.Waypoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Saved waypoints for every server plus how they are drawn. Waypoints are stored per server key. */
public final class WaypointSettings {
    public static final int MAX_WAYPOINTS = 500, MIN_LABEL_RANGE = 32, MAX_LABEL_RANGE = 4096, MAX_CLAIMED_WORLDS = 1000;
    public boolean enabled, favorite;
    public List<Waypoint> waypoints = new ArrayList<>();
    public int opacity = 220;
    public boolean beams = true, labels = true, showDistance = true, showLinkedDimension = true;
    /** Labels farther than this many blocks from the camera are hidden. */
    public int labelRange = 512;
    /**
     * Singleplayer world keys that already received the waypoints saved under their old name key, so the hand-over
     * happens once per world (see {@code Waypoints.claimLegacy}).
     */
    public List<String> claimedWorlds = new ArrayList<>();

    public void sanitize() {
        List<Waypoint> kept = new ArrayList<>();
        if (waypoints != null) for (Waypoint waypoint : waypoints) {
            if (waypoint == null) continue;
            waypoint.sanitize();
            kept.add(waypoint);
            if (kept.size() >= MAX_WAYPOINTS) break;
        }
        waypoints = kept;
        Set<String> claimed = new LinkedHashSet<>();
        if (claimedWorlds != null) for (String key : claimedWorlds)
            if (key != null && !key.isBlank()) claimed.add(key.strip());
        claimedWorlds = new ArrayList<>(claimed);
        // Keep the most recent hand-overs if the list ever grows past the cap.
        if (claimedWorlds.size() > MAX_CLAIMED_WORLDS)
            claimedWorlds = new ArrayList<>(claimedWorlds.subList(claimedWorlds.size() - MAX_CLAIMED_WORLDS, claimedWorlds.size()));
        opacity = Math.clamp(opacity, 0, 255);
        labelRange = Math.clamp(labelRange, MIN_LABEL_RANGE, MAX_LABEL_RANGE);
    }

    /** True when another waypoint can still be added. */
    public boolean hasRoom() {
        return waypoints == null || waypoints.stream().filter(Objects::nonNull).count() < MAX_WAYPOINTS;
    }
}
