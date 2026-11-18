package dev.donut.client.config;

import java.util.ArrayList;
import java.util.List;

/** Seed structure finder options. No {@code enabled} flag: it's an action (opens a screen), not an on/off module. */
public final class StructureFinderSettings {
    public static final int MIN_RADIUS = 128, MAX_RADIUS = 5000, DEFAULT_RADIUS = 1000;
    public static final int MIN_RESULTS = 50, MAX_RESULTS = 2000, DEFAULT_RESULTS = 400;

    public boolean favorite = false;
    /** Area-scan radius in blocks (ignored in nearest-of-each mode). */
    public int radius = DEFAULT_RADIUS;
    /** true = nearest of each type from the center; false = every structure of each type within {@link #radius}. */
    public boolean nearestOnly = true;
    public int maxResults = DEFAULT_RESULTS;
    /** Group ids (see StructureCatalog) the user has hidden from results. */
    public List<String> hiddenGroups = new ArrayList<>();
    /** The last seed the user searched, as text (keeps all 64 bits). */
    public String lastSeed = "";

    public boolean groupHidden(String id) { return hiddenGroups != null && hiddenGroups.contains(id); }

    public void setGroupHidden(String id, boolean hidden) {
        if (hiddenGroups == null) hiddenGroups = new ArrayList<>();
        hiddenGroups.remove(id);
        if (hidden) hiddenGroups.add(id);
    }

    public void sanitize() {
        radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        maxResults = Math.clamp(maxResults, MIN_RESULTS, MAX_RESULTS);
        if (hiddenGroups == null) hiddenGroups = new ArrayList<>();
        if (lastSeed == null) lastSeed = "";
        if (lastSeed.length() > 64) lastSeed = lastSeed.substring(0, 64);
    }
}
