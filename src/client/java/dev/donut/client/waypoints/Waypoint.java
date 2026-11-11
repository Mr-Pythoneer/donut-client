package dev.donut.client.waypoints;

import net.minecraft.core.BlockPos;

/** One saved block position. Mutable and Gson-friendly; {@link #sanitize()} repairs anything loaded from JSON. */
public final class Waypoint {
    public static final int MAX_NAME = 32, MAX_HORIZONTAL = 29_999_984, MIN_Y = -2048, MAX_Y = 4096;
    public static final String OVERWORLD = "minecraft:overworld", DEFAULT_NAME = "Waypoint";
    public String name = DEFAULT_NAME;
    public int x, y, z;
    public String dimension = OVERWORLD;
    /** Key from {@link Waypoints#serverKey}; empty for entries saved without one. */
    public String server = "";
    public int color = 0x5BC0EB;
    public boolean visible = true;
    public long created;

    public Waypoint() { }

    public Waypoint(String name, int x, int y, int z, String dimension, String server) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.server = server;
        sanitize();
    }

    public BlockPos pos() { return new BlockPos(x, y, z); }

    /** Trims a name to the stored form; blank or null becomes {@code null} so callers can pick a default. */
    public static String cleanName(String name) {
        if (name == null) return null;
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME) trimmed = trimmed.substring(0, MAX_NAME).strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void sanitize() {
        String clean = cleanName(name);
        name = clean == null ? DEFAULT_NAME : clean;
        x = Math.clamp(x, -MAX_HORIZONTAL, MAX_HORIZONTAL);
        y = Math.clamp(y, MIN_Y, MAX_Y);
        z = Math.clamp(z, -MAX_HORIZONTAL, MAX_HORIZONTAL);
        dimension = dimension == null || dimension.isBlank() ? OVERWORLD : dimension.strip();
        server = server == null ? "" : server.strip();
        color &= 0xFFFFFF;
        if (created < 0) created = 0;
    }
}
