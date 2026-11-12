package dev.donut.client.config;

import dev.donut.client.storageesp.StorageKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stash Finder preferences. A chunk is logged when its weighted storage count reaches {@link #threshold}. Weights are
 * keyed by {@link StorageKind#id}; storage vehicles are not counted (they move).
 */
public final class StashFinderSettings {
    public static final int MIN_THRESHOLD = 2, MAX_THRESHOLD = 128, MAX_WEIGHT = 5;
    public boolean enabled = false;
    public boolean favorite = false;
    public int threshold = 8;
    public Map<String, Integer> weights = new LinkedHashMap<>();
    /** Action-bar note when a stash is first found. */
    public boolean notify = true;
    /** Also a toast (top right) when a stash is first found. */
    public boolean toast = false;

    /** Containers count 1 each by default; hoppers, droppers, furnaces, brewing stands and other blocks count 0. */
    public static int defaultWeight(StorageKind kind) {
        return switch (kind) {
            case CHESTS, TRAPPED_CHESTS, BARRELS, SHULKER_BOXES, ENDER_CHESTS -> 1;
            default -> 0;
        };
    }

    /** Storage vehicles are not counted: they move, so they don't mark a place. */
    public static boolean weighted(StorageKind kind) { return kind != StorageKind.STORAGE_ENTITIES; }

    public int weight(StorageKind kind) {
        if (!weighted(kind)) return 0;
        Integer saved = weights == null ? null : weights.get(kind.id);
        return saved == null ? defaultWeight(kind) : Math.clamp(saved, 0, MAX_WEIGHT);
    }

    public void sanitize() {
        threshold = Math.clamp(threshold, MIN_THRESHOLD, MAX_THRESHOLD);
        if (weights == null) weights = new LinkedHashMap<>();
        weights.keySet().removeIf(id -> StorageKind.byId(id) == null || !weighted(StorageKind.byId(id)));
        for (StorageKind kind : StorageKind.values()) {
            if (!weighted(kind)) continue;
            weights.compute(kind.id, (id, value) -> value == null ? defaultWeight(kind) : Math.clamp(value, 0, MAX_WEIGHT));
        }
    }
}
