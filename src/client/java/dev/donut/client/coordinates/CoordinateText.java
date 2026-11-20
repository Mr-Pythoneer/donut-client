/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.coordinates;

import java.util.Locale;

/** Pure formatting for the coordinates HUD: facing, chunk position and Overworld/Nether conversion. */
public final class CoordinateText {
    public static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";

    private CoordinateText() { }

    /** Cardinal facing from Minecraft yaw (0 = south, 90 = west, 180 = north, -90 = east). */
    public static String facing(float yaw) {
        float wrapped = ((yaw % 360) + 540) % 360 - 180; // -180..180
        if (wrapped >= -45 && wrapped < 45) return "South (+Z)";
        if (wrapped >= 45 && wrapped < 135) return "West (-X)";
        if (wrapped >= -135 && wrapped < -45) return "East (+X)";
        return "North (-Z)";
    }

    /** Wrapped yaw in degrees, shown next to the facing name. */
    public static String yaw(float yaw) {
        float wrapped = ((yaw % 360) + 540) % 360 - 180;
        return String.format(Locale.ROOT, "%.1f", wrapped);
    }

    public static String position(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f / %.1f / %.1f", x, y, z);
    }

    /** "Chunk cx, cz | in chunk lx, lz", correct for negative coordinates. */
    public static String chunk(int blockX, int blockZ) {
        return "Chunk " + (blockX >> 4) + ", " + (blockZ >> 4) + " | in chunk " + (blockX & 15) + ", " + (blockZ & 15);
    }

    /** The matching position in the linked dimension, or null outside the Overworld and Nether. */
    public static String linked(String dimension, double x, double z) {
        if (OVERWORLD.equals(dimension)) return String.format(Locale.ROOT, "Nether: %d, %d", (int) Math.floor(x / 8), (int) Math.floor(z / 8));
        if (NETHER.equals(dimension)) return String.format(Locale.ROOT, "Overworld: %d, %d", (int) Math.floor(x * 8), (int) Math.floor(z * 8));
        return null;
    }

    /** "minecraft:the_nether" becomes "The Nether"; other namespaces keep their path in title case. */
    public static String dimensionName(String dimension) {
        if (dimension == null || dimension.isBlank()) return "Unknown";
        String path = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? "Unknown" : out.toString();
    }
}
