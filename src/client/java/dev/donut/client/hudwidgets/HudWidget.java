/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.hudwidgets;

/**
 * The HUD Widgets module's individual widgets. Text widgets in the same corner share one compact "stats" panel;
 * armour, potion effects and key strokes draw their own panels.
 */
public enum HudWidget {
    FPS("fps", "FPS", true, true, "Top right"),
    PING("ping", "Ping", true, true, "Top right"),
    CPS("cps", "Clicks per second", true, false, "Top right"),
    MEMORY("memory", "Memory usage", true, false, "Top right"),
    CLOCK("clock", "Clock", true, false, "Top right"),
    SERVER("server", "Server address", true, false, "Top right"),
    COMPASS("compass", "Compass", true, false, "Top right"),
    POTIONS("potions", "Potion effects", false, true, "Top right"),
    ARMOUR("armour", "Armour status", false, true, "Bottom right"),
    KEYSTROKES("keystrokes", "Key strokes", false, false, "Bottom left");

    public final String id;
    public final String label;
    /** Text widgets are merged into one stats panel per corner. */
    public final boolean stat;
    public final boolean defaultOn;
    public final String defaultCorner;

    HudWidget(String id, String label, boolean stat, boolean defaultOn, String defaultCorner) {
        this.id = id;
        this.label = label;
        this.stat = stat;
        this.defaultOn = defaultOn;
        this.defaultCorner = defaultCorner;
    }

    public static HudWidget byId(String id) {
        for (HudWidget widget : values()) if (widget.id.equals(id)) return widget;
        return null;
    }
}
