package dev.donut.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search preferences: the block IDs to highlight (each with its own colour) plus range and display options.
 * IDs are stored normalised ("minecraft:diamond_ore"). Registry checks happen when an ID is added, not here,
 * so blocks from a mod that is temporarily missing are kept rather than silently dropped.
 */
public final class SearchSettings {
    public static final int MIN_RANGE = 16, MAX_RANGE = 512, MIN_RESULTS = 16, MAX_RESULTS = 4096, MAX_BLOCKS = 64;
    public static final int FALLBACK_COLOR = 0x56DCCB;
    /** Distinct colours handed to new entries in turn. */
    public static final int[] PALETTE = {0x56DCCB, 0xFF5A4E, 0xF0B45A, 0x7FA7FF, 0xC77DFF, 0x7FCB8E, 0xF15BB5, 0xFFE066};

    public static final class Entry {
        public String id;
        public int color = FALLBACK_COLOR;

        public Entry() { }
        public Entry(String id, int color) { this.id = id; this.color = color & 0xFFFFFF; }
    }

    public boolean enabled = false;
    public boolean favorite = false;
    public List<Entry> blocks = new ArrayList<>();
    public int range = 96;
    public int maxResults = 512;
    public int opacity = 200;
    public boolean mergeAdjacent = true;
    public boolean tracers = false;
    public boolean showHud = true;

    /**
     * Normalises a typed block ID: trims, lower-cases and adds the "minecraft:" namespace. Throws with a readable
     * message for text that cannot be a resource ID; it does not check that the block exists.
     */
    public static String normalize(String raw) {
        String text = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) throw new IllegalArgumentException("Type a block ID, for example minecraft:diamond_ore.");
        int colon = text.indexOf(':');
        if (colon != text.lastIndexOf(':')) throw new IllegalArgumentException("A block ID has at most one ':'.");
        String namespace = colon < 0 ? "minecraft" : text.substring(0, colon), path = colon < 0 ? text : text.substring(colon + 1);
        if (namespace.isEmpty()) namespace = "minecraft";
        if (path.isEmpty()) throw new IllegalArgumentException("Add the block name after ':'.");
        for (char c : namespace.toCharArray())
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.'))
                throw new IllegalArgumentException("'" + c + "' is not allowed in a namespace.");
        for (char c : path.toCharArray())
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '/'))
                throw new IllegalArgumentException("'" + c + "' is not allowed in a block ID.");
        return namespace + ":" + path;
    }

    /** The colour the next added entry gets. */
    public int nextColor() { return PALETTE[(blocks == null ? 0 : blocks.size()) % PALETTE.length]; }

    public Entry find(String id) {
        if (blocks == null || id == null) return null;
        for (Entry entry : blocks) if (id.equals(entry.id)) return entry;
        return null;
    }

    /** Adds an already validated ID; false when it is already in the list. Throws when the list is full. */
    public boolean add(String id, int color) {
        String normalized = normalize(id);
        if (blocks == null) blocks = new ArrayList<>();
        if (find(normalized) != null) return false;
        if (blocks.size() >= MAX_BLOCKS) throw new IllegalArgumentException("The list is full (" + MAX_BLOCKS + " blocks).");
        blocks.add(new Entry(normalized, color));
        return true;
    }

    public boolean remove(String id) {
        return blocks != null && blocks.removeIf(entry -> entry.id.equals(id));
    }

    public int color(String id) {
        Entry entry = find(id);
        return 0xFF000000 | (entry == null ? FALLBACK_COLOR : entry.color) & 0xFFFFFF;
    }

    /** Changes to the searched IDs need a full rescan. */
    public int listKey() {
        int key = 17;
        if (blocks != null) for (Entry entry : blocks) key = key * 31 + entry.id.hashCode();
        return key;
    }

    /** Changes to these need a re-publish only; colours, opacity and tracers are read live. */
    public int publishKey() { return (range * 31 + maxResults) * 2 + (mergeAdjacent ? 1 : 0); }

    public void sanitize() {
        range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
        maxResults = Math.clamp(maxResults, MIN_RESULTS, MAX_RESULTS);
        opacity = Math.clamp(opacity, 0, 255);
        List<Entry> clean = new ArrayList<>();
        if (blocks != null) for (Entry entry : blocks) {
            if (entry == null || entry.id == null || clean.size() >= MAX_BLOCKS) continue;
            String id;
            try { id = normalize(entry.id); } catch (IllegalArgumentException invalid) { continue; }
            if (id.equals("minecraft:air") || clean.stream().anyMatch(other -> other.id.equals(id))) continue;
            clean.add(new Entry(id, entry.color));
        }
        blocks = clean;
    }
}
