/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.stash;

import dev.donut.client.storageesp.StorageKind;
import java.util.LinkedHashMap;
import java.util.Map;

/** One logged stash chunk. Mutable and Gson-friendly; {@link #sanitize()} repairs anything loaded from JSON. */
public final class StashEntry {
    /** Key from {@code Waypoints.serverKey}, so stashes follow the same per-server split as waypoints. */
    public String server = "";
    public String dimension = "minecraft:overworld";
    public int chunkX, chunkZ;
    /** A storage block near the middle of the cluster: where "Add waypoint" and "Copy coordinates" point. */
    public int x, y, z;
    /** Storage blocks by {@link StorageKind#id}; a double chest counts as two chest blocks. */
    public Map<String, Integer> counts = new LinkedHashMap<>();
    /** All storage blocks, whatever their weight. */
    public int total;
    /** Weighted count the threshold was compared with. */
    public int score;
    public long foundAt;
    public long updatedAt;

    public StashEntry() { }

    public int count(StorageKind kind) { return counts == null ? 0 : counts.getOrDefault(kind.id, 0); }

    public boolean sameChunk(String server, String dimension, int chunkX, int chunkZ) {
        return this.chunkX == chunkX && this.chunkZ == chunkZ && this.server.equals(server) && this.dimension.equals(dimension);
    }

    /** "12 chests, 3 barrels" style summary in enum order, skipping kinds that are absent. */
    public String summary() {
        StringBuilder out = new StringBuilder();
        for (StorageKind kind : StorageKind.values()) {
            int count = count(kind);
            if (count == 0) continue;
            if (!out.isEmpty()) out.append(", ");
            out.append(count).append(' ').append((count == 1 ? kind.singular : kind.shortLabel()).toLowerCase(java.util.Locale.ROOT));
        }
        return out.isEmpty() ? "no storage" : out.toString();
    }

    public void sanitize() {
        server = server == null ? "" : server.strip();
        dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.strip();
        if (counts == null) counts = new LinkedHashMap<>();
        counts.entrySet().removeIf(entry -> StorageKind.byId(entry.getKey()) == null || entry.getValue() == null || entry.getValue() <= 0);
        total = Math.max(0, total);
        score = Math.max(0, score);
        foundAt = Math.max(0, foundAt);
        updatedAt = Math.max(foundAt, updatedAt);
    }
}
