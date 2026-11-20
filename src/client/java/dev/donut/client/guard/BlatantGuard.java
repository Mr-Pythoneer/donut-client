/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.guard;

import dev.donut.client.config.BlatantGuardSettings;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Decides whether the Blatant modules may run where the player is now. Single-player (including a world opened to
 * LAN by this client) is always allowed; a multiplayer server only when the user added it to the allowlist.
 * Nothing here hides anything from a server: it only stops these modules from starting where they were not allowed.
 */
public final class BlatantGuard {
    /** AutoBuilder, Fly, Auto Miner, Auto Eat. */
    public static final int AUTO_BUILDER = 3, FLY = 6, AUTO_MINER = 20, AUTO_EAT = 28;

    private BlatantGuard() { }

    /** Where the player is: single-player, a server address, or no world. */
    public record Place(boolean inWorld, boolean singleplayer, String address) {
        public static final Place NOWHERE = new Place(false, false, null);
        /** Short label for messages and the menu. */
        public String label() { return singleplayer ? "single-player" : address == null ? "this server" : address; }
    }

    public static boolean isBlatant(int module) {
        return module == AUTO_BUILDER || module == FLY || module == AUTO_MINER || module == AUTO_EAT;
    }

    /**
     * Lower-case host, without a trailing dot or the default port, so "Play.Example.net:25565" and "play.example.net"
     * match. Null for blank input.
     */
    public static String normalize(String address) {
        if (address == null) return null;
        String key = address.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(":25565")) key = key.substring(0, key.length() - 6);
        while (key.endsWith(".")) key = key.substring(0, key.length() - 1);
        return key.isEmpty() ? null : key;
    }

    /** True when Blatant modules may run at this place. */
    public static boolean allowed(BlatantGuardSettings settings, Place place) {
        if (settings == null || !settings.blockUnlisted || !place.inWorld() || place.singleplayer()) return true;
        return settings.allows(place.address());
    }

    public static Place current(Minecraft client) {
        if (client == null || client.level == null || client.player == null) return Place.NOWHERE;
        if (client.getSingleplayerServer() != null) return new Place(true, true, null);
        var server = client.getCurrentServer();
        String address = server == null ? null : normalize(server.ip);
        if (address == null && client.getConnection() != null && client.getConnection().getConnection().getRemoteAddress() != null)
            address = normalize(client.getConnection().getConnection().getRemoteAddress().toString().replaceFirst("^[^/]*/", ""));
        return new Place(true, false, address);
    }

    /** Shown when a Blatant module is stopped or refused on a server that is not allowed. */
    public static String blockedMessage(String module, Place place) {
        String toggle = place.address() == null ? "the allow switch" : "\"Allow on " + place.address() + "\"";
        return module + " is blocked on " + place.label() + ". If this server allows it, turn on " + toggle + " in its Donut settings.";
    }
}
