package dev.donut.client.zoom;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.config.ZoomSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZoomMathTest {
    @TempDir Path directory;

    @Test void scrollStepsAndClamps() {
        assertEquals(5, ZoomMath.scroll(4, 1), 1.0e-9);
        assertEquals(3.2, ZoomMath.scroll(4, -1), 1.0e-9);
        assertEquals(ZoomSettings.MAX_FACTOR, ZoomMath.scroll(45, 3));
        assertEquals(ZoomSettings.MIN_FACTOR, ZoomMath.scroll(1.6, -4));
        assertEquals(4, ZoomMath.scroll(4, Double.NaN));
        assertEquals(4, ZoomMath.scroll(Double.NaN, 0), 1.0e-9);
    }

    @Test void smoothStepConvergesAndSnaps() {
        assertEquals(4, ZoomMath.step(1, 4, false), "Without smoothing the target applies at once");
        double value = 1;
        value = ZoomMath.step(value, 4, true);
        assertEquals(2.5, value, 1.0e-9);
        for (int i = 0; i < 20; i++) value = ZoomMath.step(value, 4, true);
        assertEquals(4, value, "Smoothing snaps exactly onto the target");
        for (int i = 0; i < 20; i++) value = ZoomMath.step(value, 1, true);
        assertEquals(1, value, "Releasing returns exactly to normal");
    }

    @Test void settingsStartDisabledPersistAndClamp() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{}");
        var loaded = new ConfigStore(file).load();
        assertFalse(loaded.zoom.enabled);
        assertEquals(4, loaded.zoom.factor);
        assertTrue(loaded.zoom.smooth && loaded.zoom.scrollAdjusts && loaded.zoom.reduceSensitivity);
        assertFalse(loaded.zoom.rememberScroll);
        var config = new ClientConfig();
        config.zoom.factor = 999;
        config.zoom.smooth = false;
        new ConfigStore(file).save(config);
        loaded = new ConfigStore(file).load();
        assertEquals(ZoomSettings.MAX_FACTOR, loaded.zoom.factor);
        assertFalse(loaded.zoom.smooth);
        var invalid = new ZoomSettings();
        invalid.factor = Double.NaN;
        invalid.sanitize();
        assertEquals(4, invalid.factor);
    }
}
