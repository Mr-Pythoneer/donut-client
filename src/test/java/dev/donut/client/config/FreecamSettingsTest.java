package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreecamSettingsTest {
    @TempDir Path directory;

    @Test
    void freecamNeverReenablesFromSavedOrHandEditedConfiguration() throws IOException {
        Path path = directory.resolve("donut.json");
        ConfigStore store = new ConfigStore(path);
        ClientConfig config = new ClientConfig();
        config.freecam.enabled = true;
        config.freecam.favorite = true;
        config.freecam.speed = 2.25;
        config.freecam.boostMultiplier = 4.5;

        store.save(config);

        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertFalse(json.getAsJsonObject("freecam").has("enabled"));
        assertTrue(config.freecam.enabled, "Saving must not switch off the live camera");
        ClientConfig loaded = store.load();
        assertFalse(loaded.freecam.enabled);
        assertTrue(loaded.freecam.favorite);
        assertEquals(2.25, loaded.freecam.speed);
        assertEquals(4.5, loaded.freecam.boostMultiplier);

        Files.writeString(path, """
                {"freecam":{"enabled":true,"favorite":true,"speed":1.5,"boostMultiplier":2}}
                """);
        loaded = store.load();
        assertFalse(loaded.freecam.enabled, "A JSON enabled field must not activate a camera on join");
        assertTrue(loaded.freecam.favorite);
        assertEquals(1.5, loaded.freecam.speed);
        assertEquals(2, loaded.freecam.boostMultiplier);
    }

    @Test
    void olderAndNullConfigurationsRestoreDisabledDefaults() throws IOException {
        Path path = directory.resolve("donut.json");
        ConfigStore store = new ConfigStore(path);
        for (String json : new String[] {"{}", "{\"freecam\":null}"}) {
            Files.writeString(path, json);
            ClientConfig loaded = store.load();
            assertNotNull(loaded.freecam);
            assertFalse(loaded.freecam.enabled);
            assertFalse(loaded.freecam.favorite);
            assertEquals(1, loaded.freecam.speed);
            assertEquals(3, loaded.freecam.boostMultiplier);
        }
    }

    @Test
    void finiteSettingsClampToSupportedMovementBounds() {
        FreecamSettings low = new FreecamSettings();
        low.speed = -10;
        low.boostMultiplier = -10;
        FreecamSettings high = new FreecamSettings();
        high.speed = 100;
        high.boostMultiplier = 100;

        low.sanitize();
        high.sanitize();

        assertEquals(0.1, low.speed);
        assertEquals(1, low.boostMultiplier);
        assertEquals(10, high.speed);
        assertEquals(10, high.boostMultiplier);
    }

    @Test
    void nonFiniteValuesAreRepairedBeforeSerialization() throws IOException {
        Path path = directory.resolve("donut.json");
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            ClientConfig config = new ClientConfig();
            config.freecam.speed = invalid;
            config.freecam.boostMultiplier = invalid;
            config.freecam.favorite = true;

            assertDoesNotThrow(() -> new ConfigStore(path).save(config));
            ClientConfig loaded = new ConfigStore(path).load();

            assertEquals(1, loaded.freecam.speed, "speed: " + invalid);
            assertEquals(3, loaded.freecam.boostMultiplier, "boost multiplier: " + invalid);
            assertTrue(loaded.freecam.favorite);
            assertFalse(loaded.freecam.enabled);
            String json = Files.readString(path);
            assertFalse(json.contains("NaN"));
            assertFalse(json.contains("Infinity"));
        }
    }

    @Test
    void qualityOfLifeOptionsDefaultToTodaysBehaviourForOlderFiles() throws IOException {
        Path path = directory.resolve("donut.json");
        Files.writeString(path, "{\"freecam\":{\"favorite\":true,\"speed\":2,\"boostMultiplier\":3}}");
        FreecamSettings loaded = new ConfigStore(path).load().freecam;
        assertEquals(0, loaded.smoothing, "Smoothing must default to off so movement stays instant");
        assertTrue(loaded.scrollAdjustsSpeed);
        assertTrue(loaded.speedReadout);
        assertFalse(loaded.resumeLastPosition);
        assertFalse(loaded.followNearestPlayer);
        assertEquals(64, loaded.followRange);
        assertFalse(loaded.lookAtTarget);
        assertEquals(0, loaded.targetX);
        assertEquals(64, loaded.targetY);
        assertEquals(0, loaded.targetZ);
        assertEquals(2, loaded.speed);
    }

    @Test
    void qualityOfLifeOptionsPersistAndClamp() throws IOException {
        Path path = directory.resolve("donut.json");
        ClientConfig config = new ClientConfig();
        var value = config.freecam;
        value.smoothing = 0.4;
        value.scrollAdjustsSpeed = false;
        value.speedReadout = false;
        value.resumeLastPosition = true;
        value.followNearestPlayer = true;
        value.followRange = 32;
        value.lookAtTarget = true;
        value.targetX = -1200;
        value.targetY = -60;
        value.targetZ = 345;
        value.speed = 7.5;
        new ConfigStore(path).save(config);
        var loaded = new ConfigStore(path).load().freecam;
        assertEquals(0.4, loaded.smoothing);
        assertFalse(loaded.scrollAdjustsSpeed);
        assertFalse(loaded.speedReadout);
        assertTrue(loaded.resumeLastPosition);
        assertTrue(loaded.followNearestPlayer);
        assertEquals(32, loaded.followRange);
        assertTrue(loaded.lookAtTarget);
        assertEquals(-1200, loaded.targetX);
        assertEquals(-60, loaded.targetY);
        assertEquals(345, loaded.targetZ);
        assertEquals(7.5, loaded.speed, "Speeds above the old 5.0 cap are now kept");

        FreecamSettings wild = new FreecamSettings();
        wild.smoothing = Double.NaN;
        wild.followRange = 9999;
        wild.targetX = Integer.MAX_VALUE;
        wild.targetY = Integer.MIN_VALUE;
        wild.targetZ = Integer.MIN_VALUE;
        wild.sanitize();
        assertEquals(0, wild.smoothing);
        assertEquals(128, wild.followRange);
        assertEquals(FreecamSettings.MAX_HORIZONTAL, wild.targetX);
        assertEquals(FreecamSettings.MIN_Y, wild.targetY);
        assertEquals(-FreecamSettings.MAX_HORIZONTAL, wild.targetZ);
        wild.smoothing = 3;
        wild.followRange = 1;
        wild.sanitize();
        assertEquals(0.95, wild.smoothing);
        assertEquals(8, wild.followRange);
    }

    @Test
    void lookAtCoordinatesParseWholeBlocksWithinWorldBounds() {
        assertEquals(-345, FreecamSettings.parseCoordinate(" -345 ", false));
        assertEquals(120, FreecamSettings.parseCoordinate("+120", true));
        assertEquals(FreecamSettings.MAX_HORIZONTAL, FreecamSettings.parseCoordinate("29999984", false));
        for (String invalid : new String[] {"", "  ", null, "1.5", "12a", "1e3", "--4", "9999999999"})
            assertThrows(IllegalArgumentException.class, () -> FreecamSettings.parseCoordinate(invalid, false), "input: " + invalid);
        assertThrows(IllegalArgumentException.class, () -> FreecamSettings.parseCoordinate("29999985", false));
        assertThrows(IllegalArgumentException.class, () -> FreecamSettings.parseCoordinate("4097", true));
        assertThrows(IllegalArgumentException.class, () -> FreecamSettings.parseCoordinate("-2049", true));
    }
}
