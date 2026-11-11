package dev.donut.client.config;

import dev.donut.client.storageesp.StorageKind;
import java.util.LinkedHashMap;
import java.util.Map;

/** Storage ESP preferences. Kinds and colors are keyed by {@link StorageKind#id}, like Base Finder's categories. */
public final class StorageEspSettings {
    public static final int MIN_RANGE = 16, MAX_RANGE = 512, MIN_TARGETS = 16, MAX_TARGETS = 2048;
    public boolean enabled = false;
    public boolean favorite = false;
    public Map<String, Boolean> kinds = new LinkedHashMap<>();
    public Map<String, Integer> colors = new LinkedHashMap<>();
    /** Requested range in blocks. The client can only highlight what the server has sent (its view distance). */
    public int range = 128;
    public int maxTargets = 256;
    public int opacity = 200;
    public boolean tracers = false;
    public boolean showHud = true;

    public boolean kindEnabled(StorageKind kind) {
        return kinds == null || !Boolean.FALSE.equals(kinds.get(kind.id));
    }

    public int kindColor(StorageKind kind) {
        Integer saved = colors == null ? null : colors.get(kind.id);
        return 0xFF000000 | (saved == null ? kind.color : saved) & 0xFFFFFF;
    }

    /** Changes to these values need a re-publish (not a rescan); colors/opacity/tracers are read live. */
    public int publishKey() {
        int key = 31 * range + maxTargets;
        for (StorageKind kind : StorageKind.values()) key = key * 2 + (kindEnabled(kind) ? 1 : 0);
        return key;
    }

    public void sanitize() {
        range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
        maxTargets = Math.clamp(maxTargets, MIN_TARGETS, MAX_TARGETS);
        opacity = Math.clamp(opacity, 0, 255);
        if (kinds == null) kinds = new LinkedHashMap<>();
        if (colors == null) colors = new LinkedHashMap<>();
        kinds.keySet().removeIf(id -> StorageKind.byId(id) == null);
        colors.keySet().removeIf(id -> StorageKind.byId(id) == null);
        kinds.replaceAll((id, value) -> value == null || value);
        for (StorageKind kind : StorageKind.values()) {
            kinds.putIfAbsent(kind.id, true);
            colors.compute(kind.id, (id, value) -> (value == null ? kind.color : value) & 0xFFFFFF);
        }
    }
}
