package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerEspSettingsTest {
    @TempDir Path directory;

    @Test void newAndOlderConfigurationsStartDisabledWithPlayersOnly() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"playerEsp\":null}", "{\"playerEsp\":{\"friends\":null}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().playerEsp;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertTrue(value.players);
            assertFalse(value.hostileMobs || value.passiveMobs || value.items, "Only players are shown by default");
            assertTrue(value.boxes && value.nametags && value.showHealth && value.showArmor && value.showHeldItem && value.showDistance);
            assertFalse(value.tracers);
            assertTrue(value.respectNameHider);
            assertTrue(value.colorByDistance);
            assertEquals(128, value.range);
            assertEquals(64, value.maxShown);
            assertEquals(220, value.opacity);
            assertNotNull(value.friends);
            assertTrue(value.friends.isEmpty());
        }
        assertFalse(new ClientConfig().playerEsp.enabled);
    }

    @Test void preferencesSurviveJsonAndAreClamped() {
        var config = new ClientConfig();
        var value = config.playerEsp;
        value.enabled = value.favorite = value.tracers = value.items = true;
        value.nametags = false;
        value.range = 99_999;
        value.maxShown = -3;
        value.opacity = 999;
        value.playerColor = 0xAB123456;
        value.addFriend("Notch");
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var loaded = store.load().playerEsp;
        assertTrue(loaded.enabled && loaded.favorite && loaded.tracers && loaded.items);
        assertFalse(loaded.nametags);
        assertEquals(PlayerEspSettings.MAX_RANGE, loaded.range);
        assertEquals(PlayerEspSettings.MIN_SHOWN, loaded.maxShown);
        assertEquals(255, loaded.opacity);
        assertEquals(0x123456, loaded.playerColor);
        assertEquals(java.util.List.of("Notch"), loaded.friends);
    }

    @Test void unknownKeysAreIgnored() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"playerEsp\":{\"futureOption\":true,\"range\":64,\"friends\":[\"Steve\"]}}");
        var value = new ConfigStore(file).load().playerEsp;
        assertEquals(64, value.range);
        assertEquals(java.util.List.of("Steve"), value.friends);
    }

    @Test void friendsAreValidatedAndMatchedWithoutCase() {
        var value = new PlayerEspSettings();
        assertTrue(value.addFriend("  Alex_99 "));
        assertFalse(value.addFriend("alex_99"), "Duplicates differ only by case");
        assertTrue(value.isFriend("ALEX_99"));
        assertFalse(value.isFriend("Alex"));
        assertFalse(value.isFriend(null));
        assertThrows(IllegalArgumentException.class, () -> value.addFriend(""));
        assertThrows(IllegalArgumentException.class, () -> value.addFriend("has space"));
        assertThrows(IllegalArgumentException.class, () -> value.addFriend("seventeen_chars_xy"));
        assertTrue(value.removeFriend("ALEX_99"));
        assertTrue(value.friends.isEmpty());
        for (int i = 0; i < PlayerEspSettings.MAX_FRIENDS; i++) value.addFriend("p" + i);
        assertThrows(IllegalArgumentException.class, () -> value.addFriend("onemore"));
    }

    @Test void sanitizeRepairsFriendsAndColors() {
        var value = new PlayerEspSettings();
        value.friends = new ArrayList<>(Arrays.asList("Steve", null, "steve", "bad name", " Alex ", ""));
        value.friendColor = 0xFF00FF00;
        value.range = 0;
        value.maxShown = 10_000;
        value.sanitize();
        assertEquals(java.util.List.of("Steve", "Alex"), value.friends);
        assertEquals(0x00FF00, value.friendColor);
        assertEquals(PlayerEspSettings.MIN_RANGE, value.range);
        assertEquals(PlayerEspSettings.MAX_SHOWN, value.maxShown);
        value.friends = null;
        value.sanitize();
        assertNotNull(value.friends);
    }
}
