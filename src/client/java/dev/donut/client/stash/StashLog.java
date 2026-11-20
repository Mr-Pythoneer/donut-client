/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.donut.client.storageesp.StorageKind;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Stash Finder's log: threshold check, first-sighting logging and export, with no game objects so it is unit tested.
 * A chunk is logged once per server and dimension; seeing it again with different storage refreshes its counts.
 */
public final class StashLog {
    public static final int MAX_ENTRIES = 5000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Result { IGNORED, LOGGED, UPDATED }

    private final List<StashEntry> entries = new ArrayList<>();

    public static int score(Map<StorageKind, Integer> counts, ToIntFunction<StorageKind> weight) {
        int score = 0;
        for (var entry : counts.entrySet()) score += entry.getValue() * weight.applyAsInt(entry.getKey());
        return score;
    }

    /**
     * Records what one chunk holds. Returns {@link Result#LOGGED} the first time the chunk reaches {@code threshold}
     * (callers notify only then), {@link Result#UPDATED} when a logged chunk is seen again at or above the threshold
     * with different counts, and {@link Result#IGNORED} otherwise. A logged stash is never dropped for falling below.
     */
    public Result observe(String server, String dimension, int chunkX, int chunkZ, Map<StorageKind, Integer> counts,
                          int x, int y, int z, long now, int threshold, ToIntFunction<StorageKind> weight) {
        int score = score(counts, weight), total = 0;
        for (int count : counts.values()) total += count;
        StashEntry existing = find(server, dimension, chunkX, chunkZ);
        if (score < threshold) return Result.IGNORED;
        if (existing != null) {
            if (existing.total == total && existing.score == score && existing.counts.equals(ids(counts))) return Result.IGNORED;
            fill(existing, counts, x, y, z, total, score);
            existing.updatedAt = now;
            return Result.UPDATED;
        }
        StashEntry entry = new StashEntry();
        entry.server = server;
        entry.dimension = dimension;
        entry.chunkX = chunkX;
        entry.chunkZ = chunkZ;
        entry.foundAt = entry.updatedAt = now;
        fill(entry, counts, x, y, z, total, score);
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(oldest());
        return Result.LOGGED;
    }

    private static void fill(StashEntry entry, Map<StorageKind, Integer> counts, int x, int y, int z, int total, int score) {
        entry.counts = ids(counts);
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.total = total;
        entry.score = score;
    }

    private static Map<String, Integer> ids(Map<StorageKind, Integer> counts) {
        Map<String, Integer> ids = new java.util.LinkedHashMap<>();
        for (StorageKind kind : StorageKind.values()) {
            Integer count = counts.get(kind);
            if (count != null && count > 0) ids.put(kind.id, count);
        }
        return ids;
    }

    private int oldest() {
        int index = 0;
        for (int i = 1; i < entries.size(); i++) if (entries.get(i).foundAt < entries.get(index).foundAt) index = i;
        return index;
    }

    public StashEntry find(String server, String dimension, int chunkX, int chunkZ) {
        for (StashEntry entry : entries) if (entry.sameChunk(server, dimension, chunkX, chunkZ)) return entry;
        return null;
    }

    /** This server's stashes, newest first. */
    public List<StashEntry> forServer(String server) {
        List<StashEntry> shown = new ArrayList<>();
        for (StashEntry entry : entries) if (entry.server.equals(server)) shown.add(entry);
        shown.sort(Comparator.comparingLong((StashEntry entry) -> entry.foundAt).reversed());
        return shown;
    }

    public boolean remove(StashEntry entry) { return entries.remove(entry); }
    public List<StashEntry> all() { return List.copyOf(entries); }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }

    /** Replaces the contents with entries read from disk (sanitized, duplicates and overflow dropped). */
    public void load(List<StashEntry> loaded) {
        entries.clear();
        if (loaded == null) return;
        for (StashEntry entry : loaded) {
            if (entry == null) continue;
            entry.sanitize();
            if (find(entry.server, entry.dimension, entry.chunkX, entry.chunkZ) == null) entries.add(entry);
        }
        while (entries.size() > MAX_ENTRIES) entries.remove(oldest());
    }

    // ---- JSON / CSV ----

    public static String toJson(List<StashEntry> entries) { return GSON.toJson(entries); }

    public static List<StashEntry> fromJson(String json) {
        try {
            List<StashEntry> parsed = GSON.fromJson(json, new TypeToken<List<StashEntry>>() { }.getType());
            if (parsed == null) return List.of();
            List<StashEntry> clean = new ArrayList<>();
            for (StashEntry entry : parsed) if (entry != null) { entry.sanitize(); clean.add(entry); }
            return clean;
        } catch (JsonParseException | IllegalStateException failure) {
            return List.of();
        }
    }

    private static final List<String> FIXED = List.of("server", "dimension", "chunk_x", "chunk_z", "x", "y", "z", "score", "total", "found_at");

    /** One row per stash; a column per storage kind (block kinds only), found_at as ISO-8601 UTC. */
    public static String toCsv(List<StashEntry> entries) {
        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>(FIXED);
        for (StorageKind kind : StorageKind.values()) header.add(kind.id);
        out.append(String.join(",", header)).append('\n');
        for (StashEntry entry : entries) {
            List<String> row = new ArrayList<>(List.of(csv(entry.server), csv(entry.dimension), "" + entry.chunkX, "" + entry.chunkZ,
                    "" + entry.x, "" + entry.y, "" + entry.z, "" + entry.score, "" + entry.total,
                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entry.foundAt).truncatedTo(ChronoUnit.MILLIS))));
            for (StorageKind kind : StorageKind.values()) row.add("" + entry.count(kind));
            out.append(String.join(",", row)).append('\n');
        }
        return out.toString();
    }

    /** Reads {@link #toCsv} output back (for tests and re-import tools). Unknown columns are ignored. */
    public static List<StashEntry> fromCsv(String text) {
        List<StashEntry> parsed = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        if (lines.length == 0) return parsed;
        List<String> header = split(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            List<String> cells = split(lines[i]);
            StashEntry entry = new StashEntry();
            for (int c = 0; c < Math.min(header.size(), cells.size()); c++) {
                String name = header.get(c), value = cells.get(c);
                switch (name) {
                    case "server" -> entry.server = value;
                    case "dimension" -> entry.dimension = value;
                    case "chunk_x" -> entry.chunkX = Integer.parseInt(value);
                    case "chunk_z" -> entry.chunkZ = Integer.parseInt(value);
                    case "x" -> entry.x = Integer.parseInt(value);
                    case "y" -> entry.y = Integer.parseInt(value);
                    case "z" -> entry.z = Integer.parseInt(value);
                    case "score" -> entry.score = Integer.parseInt(value);
                    case "total" -> entry.total = Integer.parseInt(value);
                    case "found_at" -> entry.foundAt = entry.updatedAt = Instant.parse(value).toEpochMilli();
                    default -> {
                        if (StorageKind.byId(name) != null && !value.equals("0")) entry.counts.put(name, Integer.parseInt(value));
                    }
                }
            }
            entry.sanitize();
            parsed.add(entry);
        }
        return parsed;
    }

    private static String csv(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static List<String> split(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { cells.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        cells.add(cell.toString());
        return cells;
    }

    /** Counts storage blocks by kind (block kinds only). */
    public static Map<StorageKind, Integer> count(Iterable<StorageKind> kinds) {
        Map<StorageKind, Integer> counts = new EnumMap<>(StorageKind.class);
        for (StorageKind kind : kinds) if (kind != StorageKind.STORAGE_ENTITIES) counts.merge(kind, 1, Integer::sum);
        return counts;
    }
}
