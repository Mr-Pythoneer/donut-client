package dev.donut.client.freelook;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.config.FreelookSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreelookTest {
    @TempDir Path directory;

    @Test void turnsLikeVanillaMouseLook() {
        assertEquals(15, FreelookMath.turnYaw(0, 100), 1.0e-4, "0.15 degrees per mouse unit, as in Entity.turn");
        assertEquals(10, FreelookMath.turnPitch(4, 40, false), 1.0e-4);
        assertEquals(-2, FreelookMath.turnPitch(4, 40, true), 1.0e-4, "Invert pitch flips the vertical direction");
        assertEquals(90, FreelookMath.turnPitch(80, 1000, false), "Pitch is clamped like vanilla");
        assertEquals(-90, FreelookMath.turnPitch(-80, 1000, true));
    }

    @Test void yawWrapsAndBadInputIsIgnored() {
        assertEquals(-170, FreelookMath.turnYaw(170, 20 / FreelookMath.DEGREES_PER_UNIT), 1.0e-3);
        assertEquals(170, FreelookMath.turnYaw(-170, -20 / FreelookMath.DEGREES_PER_UNIT), 1.0e-3);
        assertEquals(-180, FreelookMath.wrap(180));
        assertEquals(0, FreelookMath.wrap(720));
        assertEquals(10, FreelookMath.turnYaw(10, Double.NaN));
        assertEquals(0, FreelookMath.turnYaw(Float.NaN, 0));
        assertEquals(30, FreelookMath.turnPitch(30, Double.POSITIVE_INFINITY, false));
        assertEquals(0, FreelookMath.turnPitch(Float.NaN, 0, false));
        // A long orbit stays bounded.
        float yaw = 0;
        for (int i = 0; i < 10_000; i++) yaw = FreelookMath.turnYaw(yaw, 977);
        assertTrue(yaw >= -180 && yaw < 180, "Yaw drifted out of range: " + yaw);
    }

    @Test void distanceReplacesVanillaFourBlocks() {
        assertEquals(4, FreelookMath.scaleDistance(4, 4), 1.0e-6);
        assertEquals(12, FreelookMath.scaleDistance(4, 12), 1.0e-6);
        assertEquals(1, FreelookMath.scaleDistance(4, 1), 1.0e-6);
        assertEquals(6, FreelookMath.scaleDistance(2, 12), 1.0e-6, "A half-size player keeps its scale");
        assertEquals(4, FreelookMath.scaleDistance(4, Double.NaN), 1.0e-6);
        assertEquals(0, FreelookMath.scaleDistance(0, 8), 1.0e-6);
    }

    @Test void settingsStartDisabledSurviveJsonAndAreClamped() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"freelook\":null}", "{\"freelook\":{\"perspective\":\"Sideways\"}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().freelook;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite || value.toggleMode || value.invertPitch);
            assertEquals(FreelookSettings.BACK, value.perspective);
            assertEquals(FreelookSettings.DEFAULT_DISTANCE, value.distance);
        }
        var config = new ClientConfig();
        config.freelook.toggleMode = config.freelook.invertPitch = true;
        config.freelook.perspective = FreelookSettings.KEEP;
        config.freelook.distance = 99;
        var store = new ConfigStore(file);
        store.save(config);
        var loaded = store.load().freelook;
        assertTrue(loaded.toggleMode && loaded.invertPitch);
        assertEquals(FreelookSettings.KEEP, loaded.perspective);
        assertEquals(FreelookSettings.MAX_DISTANCE, loaded.distance);
        loaded.distance = 0;
        loaded.sanitize();
        assertEquals(FreelookSettings.MIN_DISTANCE, loaded.distance);
        loaded.distance = Double.NaN;
        loaded.perspective = null;
        loaded.sanitize();
        assertEquals(FreelookSettings.DEFAULT_DISTANCE, loaded.distance);
        assertEquals(FreelookSettings.BACK, loaded.perspective);
    }

    @Test void perspectiveButtonCycles() {
        assertEquals(FreelookSettings.FRONT, FreelookSettings.next(FreelookSettings.BACK));
        assertEquals(FreelookSettings.KEEP, FreelookSettings.next(FreelookSettings.FRONT));
        assertEquals(FreelookSettings.BACK, FreelookSettings.next(FreelookSettings.KEEP));
        assertEquals(FreelookSettings.BACK, FreelookSettings.next("unknown"));
    }
}
