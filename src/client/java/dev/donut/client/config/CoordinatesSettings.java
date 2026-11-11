package dev.donut.client.config;

import java.util.List;

/** Coordinates / direction HUD preferences. */
public final class CoordinatesSettings {
    public static final List<String> ANCHORS = List.of("Top left", "Bottom right");
    public boolean enabled = false;
    public boolean favorite = false;
    public boolean showFacing = true;
    public boolean showChunk = true;
    /** Overworld <-> Nether converted coordinates. */
    public boolean showLinked = true;
    /** While Freecam is on, also show the camera's own position. */
    public boolean showFreecam = true;
    public boolean showBiome = false;
    public String anchor = "Top left";

    public void sanitize() {
        if (anchor == null || !ANCHORS.contains(anchor)) anchor = "Top left";
    }
}
