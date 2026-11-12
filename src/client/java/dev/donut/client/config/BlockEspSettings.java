package dev.donut.client.config;

import dev.donut.client.blockesp.OreMarker;
import dev.donut.client.blockesp.OreMarker.Kind;

/**
 * Vanilla 1.21.11 seed prediction preferences. Keep seeds as text to preserve all 64 bits in JSON.
 *
 * <p>{@code emerald}/{@code coal} and their colors are the original fields and stay authoritative, so older saved
 * settings load unchanged. The defaults (emerald + coal, deepslate variants only) reproduce the original two targets.
 */
public final class BlockEspSettings {
    public static final String DEFAULT_SEED = "6608149111735331168";
    public boolean enabled, favorite;
    public String seed = DEFAULT_SEED;
    public boolean emerald = true, coal = true, tracers, showHud = true;
    public int radius = 4, emeraldColor = 0x55EE88, coalColor = 0xA7B7CB, opacity = 210;
    // Added with the full ore set; all default off so existing users see the same markers.
    public boolean diamond, gold, lapis, redstone, iron, copper;
    public int diamondColor = Kind.DIAMOND.defaultColor, goldColor = Kind.GOLD.defaultColor, lapisColor = Kind.LAPIS.defaultColor,
            redstoneColor = Kind.REDSTONE.defaultColor, ironColor = Kind.IRON.defaultColor, copperColor = Kind.COPPER.defaultColor;
    public boolean stoneVariants = false, deepslateVariants = true;
    /** Markers below this confidence percentage are hidden. 0 shows every prediction that is not known to be gone. */
    public int minConfidence = 0;
    public boolean fadeByConfidence = true;

    public static long parseSeed(String text) {
        if (text == null || !text.trim().matches("-?[0-9]{1,19}"))
            throw new IllegalArgumentException("Enter a signed 64-bit numeric world seed.");
        try { return Long.parseLong(text.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Seed is outside the signed 64-bit range."); }
    }

    public boolean oreEnabled(Kind kind) {
        return switch (kind) {
            case EMERALD -> emerald; case DIAMOND -> diamond; case GOLD -> gold; case LAPIS -> lapis;
            case REDSTONE -> redstone; case IRON -> iron; case COPPER -> copper; case COAL -> coal;
        };
    }

    public void setOreEnabled(Kind kind, boolean value) {
        switch (kind) {
            case EMERALD -> emerald = value; case DIAMOND -> diamond = value; case GOLD -> gold = value; case LAPIS -> lapis = value;
            case REDSTONE -> redstone = value; case IRON -> iron = value; case COPPER -> copper = value; case COAL -> coal = value;
        }
    }

    public int oreColor(Kind kind) {
        return 0xFFFFFF & switch (kind) {
            case EMERALD -> emeraldColor; case DIAMOND -> diamondColor; case GOLD -> goldColor; case LAPIS -> lapisColor;
            case REDSTONE -> redstoneColor; case IRON -> ironColor; case COPPER -> copperColor; case COAL -> coalColor;
        };
    }

    public void setOreColor(Kind kind, int rgb) {
        int value = rgb & 0xFFFFFF;
        switch (kind) {
            case EMERALD -> emeraldColor = value; case DIAMOND -> diamondColor = value; case GOLD -> goldColor = value;
            case LAPIS -> lapisColor = value; case REDSTONE -> redstoneColor = value; case IRON -> ironColor = value;
            case COPPER -> copperColor = value; case COAL -> coalColor = value;
        }
    }

    public boolean anyOre() {
        for (Kind kind : Kind.values()) if (oreEnabled(kind)) return true;
        return false;
    }

    /** Exact target blocks to scan for: enabled ores times enabled variants (see {@link OreMarker#bit}). */
    public int targetMask() {
        int mask = 0;
        for (Kind kind : Kind.values()) {
            if (!oreEnabled(kind)) continue;
            if (stoneVariants) mask |= OreMarker.bit(kind, false);
            if (deepslateVariants) mask |= OreMarker.bit(kind, true);
        }
        return mask;
    }

    public void sanitize() {
        // Preserve invalid input so the module reports it instead of silently predicting a different world.
        if (seed == null) seed = "";
        seed = seed.trim();
        if (seed.length() > 64) seed = seed.substring(0, 64);
        radius = Math.clamp(radius, 1, 8);
        opacity = Math.clamp(opacity, 0, 255);
        minConfidence = Math.clamp(minConfidence, 0, 100);
        for (Kind kind : Kind.values()) setOreColor(kind, oreColor(kind));
    }
}
