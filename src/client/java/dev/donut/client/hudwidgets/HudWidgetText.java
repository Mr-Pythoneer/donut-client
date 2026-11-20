/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.hudwidgets;

import java.time.LocalTime;
import java.util.Locale;

/** Pure formatting for the HUD widgets, kept free of game state so it can be unit tested. */
public final class HudWidgetText {
    private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private HudWidgetText() { }

    public static String fps(int fps) { return Integer.toString(Math.max(0, fps)); }

    /**
     * Ping from the player's own tab-list entry. In singleplayer there is no network round trip, so say so; a missing
     * entry (not yet received) shows a dash.
     */
    public static String ping(Integer latencyMillis, boolean singleplayer) {
        if (singleplayer) return "singleplayer";
        if (latencyMillis == null || latencyMillis < 0) return "-";
        return latencyMillis + " ms";
    }

    /** 0 = good, 1 = warning, 2 = bad. */
    public static int pingLevel(int latencyMillis) { return latencyMillis < 100 ? 0 : latencyMillis < 250 ? 1 : 2; }

    public static String cps(int left, int right) { return left + " | " + right; }

    public static String memory(long usedBytes, long maxBytes, boolean percent) {
        long used = usedBytes / (1024 * 1024), max = Math.max(1, maxBytes / (1024 * 1024));
        String text = used + " / " + max + " MB";
        return percent ? text + "  " + Math.round(100.0 * used / max) + "%" : text;
    }

    /** Remaining effect time: {@code m:ss} (or {@code h:mm:ss}); infinite effects show the infinity sign. */
    public static String effectTime(int ticks, boolean infinite) {
        if (infinite) return "∞";
        int seconds = Math.max(0, ticks) / 20;
        int h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Effect name with its level; level I is left out, like vanilla. */
    public static String effectName(String name, int amplifier) {
        if (amplifier <= 0) return name;
        int level = amplifier + 1;
        return name + " " + (level <= ROMAN.length ? ROMAN[level - 1] : Integer.toString(level));
    }

    /** Compass bearing in whole degrees, 0 = north, 90 = east (Minecraft yaw 0 faces south, 90 west). */
    public static int bearing(float yaw) {
        int degrees = Math.round(yaw + 180) % 360;
        return degrees < 0 ? degrees + 360 : degrees;
    }

    public static String cardinal(int bearing) { return CARDINALS[Math.floorMod(Math.round(bearing / 45f), 8)]; }

    public static String compass(float yaw) {
        int bearing = bearing(yaw);
        return cardinal(bearing) + " " + bearing + "°";
    }

    public static String clock(LocalTime time, boolean twentyFourHour) {
        if (twentyFourHour) return String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
        int hour = time.getHour() % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", hour == 0 ? 12 : hour, time.getMinute(), time.getHour() < 12 ? "AM" : "PM");
    }

    /** In-game day and clock: day time 0 is 06:00 on day 1, and a day is 24000 ticks. */
    public static String gameTime(long dayTime) {
        long day = Math.floorDiv(dayTime, 24000L) + 1;
        long ticks = Math.floorMod(dayTime, 24000L);
        long minutes = (ticks * 1440 / 24000 + 360) % 1440;
        return String.format(Locale.ROOT, "Day %d  %02d:%02d", day, minutes / 60, minutes % 60);
    }

    /** Short key-cap label: the bound key's name, cut to fit a cap. */
    public static String keyCap(String keyName) {
        if (keyName == null || keyName.isBlank()) return "?";
        String name = keyName.strip();
        return name.length() <= 3 ? name.toUpperCase(Locale.ROOT) : name.substring(0, 3);
    }
}
