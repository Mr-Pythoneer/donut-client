/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.playeresp;

import dev.donut.client.config.PlayerEspSettings;
import java.util.Locale;
import net.minecraft.util.Mth;

/** Pure Player ESP rules: target kinds, filters, colours and nametag text. Unit-tested without a world. */
public final class PlayerEspTargets {
    private PlayerEspTargets() { }

    public enum Kind { PLAYER, HOSTILE, PASSIVE, ITEM }

    /** What an entity is, from its type flags; null for anything Player ESP never shows. */
    public static Kind classify(boolean player, boolean hostile, boolean mob, boolean item) {
        if (player) return Kind.PLAYER;
        if (hostile) return Kind.HOSTILE;
        if (mob) return Kind.PASSIVE;
        if (item) return Kind.ITEM;
        return null;
    }

    public static boolean shown(Kind kind, PlayerEspSettings settings) {
        if (kind == null) return false;
        return switch (kind) {
            case PLAYER -> settings.players;
            case HOSTILE -> settings.hostileMobs;
            case PASSIVE -> settings.passiveMobs;
            case ITEM -> settings.items;
        };
    }

    /**
     * Whether an entity may be shown at all: never the local player, never the camera entity (spectating), and
     * never an entity the camera is inside (it would cover the whole view).
     */
    public static boolean allowed(boolean self, boolean cameraEntity, boolean cameraInside) {
        return !self && !cameraEntity && !cameraInside;
    }

    /** Red when close, through yellow, to green at the edge of the range. */
    public static int distanceColor(double distance, double range) {
        float t = (float) Math.clamp(range <= 0 ? 1 : distance / range, 0, 1);
        return Mth.hsvToRgb(t / 3f, .72f, 1f) & 0xFFFFFF;
    }

    /** Box/tracer/tag colour (RGB) for one target. {@code teamColor} is the entity's team colour or -1. */
    public static int color(Kind kind, boolean friend, int teamColor, double distance, PlayerEspSettings settings) {
        if (kind == Kind.PLAYER && friend) return settings.friendColor & 0xFFFFFF;
        if (settings.teamColors && teamColor >= 0 && (teamColor & 0xFFFFFF) != 0xFFFFFF) return teamColor & 0xFFFFFF;
        return switch (kind) {
            case PLAYER -> settings.colorByDistance ? distanceColor(distance, settings.range) : settings.playerColor & 0xFFFFFF;
            case HOSTILE -> settings.hostileColor & 0xFFFFFF;
            case PASSIVE -> settings.passiveColor & 0xFFFFFF;
            case ITEM -> settings.itemColor & 0xFFFFFF;
        };
    }

    /** The name a tag shows: real names unless Name Hider privacy applies to players. */
    public static String displayName(Kind kind, String name, boolean friend, boolean hideNames) {
        if (kind == Kind.PLAYER && hideNames) return friend ? "Friend" : "Player";
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    /** "7.5❤" in hearts or "15 HP", with absorption as "+2". Empty for non-living targets (health < 0). */
    public static String health(float health, float absorption, boolean hearts) {
        if (health < 0 || !Float.isFinite(health)) return "";
        float shown = hearts ? health / 2 : health, extra = hearts ? absorption / 2 : absorption;
        String text = number(shown) + (hearts ? "❤" : " HP");
        if (absorption > 0 && Float.isFinite(absorption)) text += " +" + number(extra);
        return text;
    }

    /** One decimal only when it isn't zero: 10, 7.5. */
    static String number(float value) {
        float rounded = Math.round(value * 10) / 10f;
        return rounded == Math.rint(rounded) ? Integer.toString(Math.round(rounded)) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Full text line: "Name  7.5❤  12m", omitting parts that are switched off or unknown. */
    public static String nametag(String name, String health, double distance, PlayerEspSettings settings) {
        StringBuilder text = new StringBuilder(name);
        if (settings.showHealth && health != null && !health.isEmpty()) text.append("  ").append(health);
        if (settings.showDistance && distance >= 0) text.append("  ").append(Math.round(distance)).append('m');
        return text.toString();
    }

    /** Health colour: green when full, amber at half, red when low. */
    public static int healthColor(float health, float max) {
        float t = max <= 0 ? 1 : Math.clamp(health / max, 0, 1);
        return t > .6f ? 0x7FCB8E : t > .3f ? 0xF0B45A : 0xEF6A5E;
    }
}
