package dev.donut.client.playeresp;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.PlayerEspSettings;
import dev.donut.client.playeresp.PlayerEspTargets.Kind;
import org.junit.jupiter.api.Test;

class PlayerEspTargetsTest {
    @Test void entitiesAreClassifiedAndFilteredToPlayersByDefault() {
        assertEquals(Kind.PLAYER, PlayerEspTargets.classify(true, false, false, false));
        assertEquals(Kind.HOSTILE, PlayerEspTargets.classify(false, true, true, false));
        assertEquals(Kind.PASSIVE, PlayerEspTargets.classify(false, false, true, false));
        assertEquals(Kind.ITEM, PlayerEspTargets.classify(false, false, false, true));
        assertNull(PlayerEspTargets.classify(false, false, false, false), "Armor stands, minecarts etc. are never shown");
        var settings = new PlayerEspSettings();
        assertTrue(PlayerEspTargets.shown(Kind.PLAYER, settings));
        for (Kind kind : new Kind[] {Kind.HOSTILE, Kind.PASSIVE, Kind.ITEM}) assertFalse(PlayerEspTargets.shown(kind, settings), kind.name());
        assertFalse(PlayerEspTargets.shown(null, settings));
        settings.players = false;
        settings.hostileMobs = settings.passiveMobs = settings.items = true;
        assertFalse(PlayerEspTargets.shown(Kind.PLAYER, settings));
        for (Kind kind : new Kind[] {Kind.HOSTILE, Kind.PASSIVE, Kind.ITEM}) assertTrue(PlayerEspTargets.shown(kind, settings), kind.name());
    }

    @Test void neverYourselfTheCameraEntityOrAnEntityTheCameraIsIn() {
        assertTrue(PlayerEspTargets.allowed(false, false, false));
        assertFalse(PlayerEspTargets.allowed(true, false, false));
        assertFalse(PlayerEspTargets.allowed(false, true, false));
        assertFalse(PlayerEspTargets.allowed(false, false, true));
    }

    @Test void colorsFollowFriendTeamDistanceAndFixedRules() {
        var settings = new PlayerEspSettings();
        assertEquals(settings.friendColor, PlayerEspTargets.color(Kind.PLAYER, true, 0x5555FF, 3, settings), "Friends win over everything");
        int near = PlayerEspTargets.color(Kind.PLAYER, false, -1, 0, settings);
        int far = PlayerEspTargets.color(Kind.PLAYER, false, -1, settings.range, settings);
        assertTrue((near >> 16 & 255) > 200 && (near >> 8 & 255) < 100, "Close players are red: " + Integer.toHexString(near));
        assertTrue((far >> 8 & 255) > 200 && (far >> 16 & 255) < 100, "Players at the range are green: " + Integer.toHexString(far));
        assertEquals(far, PlayerEspTargets.distanceColor(10_000, settings.range), "Beyond the range clamps");
        settings.colorByDistance = false;
        assertEquals(settings.playerColor, PlayerEspTargets.color(Kind.PLAYER, false, -1, 5, settings));
        assertEquals(settings.playerColor, PlayerEspTargets.color(Kind.PLAYER, false, 0x5555FF, 5, settings), "Team colours are off by default");
        settings.teamColors = true;
        assertEquals(0x5555FF, PlayerEspTargets.color(Kind.PLAYER, false, 0x5555FF, 5, settings));
        assertEquals(settings.playerColor, PlayerEspTargets.color(Kind.PLAYER, false, 0xFFFFFF, 5, settings), "White means no team colour");
        settings.teamColors = false;
        assertEquals(settings.hostileColor, PlayerEspTargets.color(Kind.HOSTILE, false, -1, 5, settings));
        assertEquals(settings.passiveColor, PlayerEspTargets.color(Kind.PASSIVE, false, -1, 5, settings));
        assertEquals(settings.itemColor, PlayerEspTargets.color(Kind.ITEM, false, -1, 5, settings));
    }

    @Test void nameHiderReplacesOnlyPlayerNames() {
        assertEquals("Steve", PlayerEspTargets.displayName(Kind.PLAYER, "Steve", false, false));
        assertEquals("Player", PlayerEspTargets.displayName(Kind.PLAYER, "Steve", false, true));
        assertEquals("Friend", PlayerEspTargets.displayName(Kind.PLAYER, "Steve", true, true));
        assertEquals("Zombie", PlayerEspTargets.displayName(Kind.HOSTILE, "Zombie", false, true));
        assertEquals("Unknown", PlayerEspTargets.displayName(Kind.PLAYER, " ", false, false));
    }

    @Test void healthIsFormattedInHeartsOrPoints() {
        assertEquals("10❤", PlayerEspTargets.health(20, 0, true));
        assertEquals("7.5❤", PlayerEspTargets.health(15, 0, true));
        assertEquals("7.5❤ +2", PlayerEspTargets.health(15, 4, true));
        assertEquals("15 HP", PlayerEspTargets.health(15, 0, false));
        assertEquals("3.3 HP +1", PlayerEspTargets.health(3.33f, 1, false));
        assertEquals("0❤", PlayerEspTargets.health(0, 0, true));
        assertEquals("", PlayerEspTargets.health(-1, 0, true), "Non-living targets have no health");
        assertEquals("", PlayerEspTargets.health(Float.NaN, 0, true));
    }

    @Test void nametagTextHonoursToggles() {
        var settings = new PlayerEspSettings();
        assertEquals("Steve  7.5❤  12m", PlayerEspTargets.nametag("Steve", "7.5❤", 12.4, settings));
        settings.showHealth = false;
        assertEquals("Steve  12m", PlayerEspTargets.nametag("Steve", "7.5❤", 12.4, settings));
        settings.showDistance = false;
        assertEquals("Steve", PlayerEspTargets.nametag("Steve", "7.5❤", 12.4, settings));
        settings.showHealth = true;
        assertEquals("Diamond x3", PlayerEspTargets.nametag("Diamond x3", "", 4, settings), "Items have no health part");
    }

    @Test void healthColorGoesGreenAmberRed() {
        assertEquals(0x7FCB8E, PlayerEspTargets.healthColor(20, 20));
        assertEquals(0xF0B45A, PlayerEspTargets.healthColor(10, 20));
        assertEquals(0xEF6A5E, PlayerEspTargets.healthColor(2, 20));
        assertEquals(0x7FCB8E, PlayerEspTargets.healthColor(5, 0));
    }
}
