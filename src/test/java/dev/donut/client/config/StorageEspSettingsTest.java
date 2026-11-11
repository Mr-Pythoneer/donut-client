package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.storageesp.StorageKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageEspSettingsTest {
    @TempDir Path directory;

    @Test void newAndOlderConfigurationsStartDisabledWithEveryKindSelected() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"storageEsp\":null}", "{\"storageEsp\":{\"kinds\":null,\"colors\":null}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().storageEsp;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertFalse(value.tracers);
            assertTrue(value.showHud);
            assertEquals(128, value.range);
            assertEquals(256, value.maxTargets);
            assertEquals(200, value.opacity);
            for (StorageKind kind : StorageKind.values()) {
                assertTrue(value.kindEnabled(kind), kind.id);
                assertEquals(0xFF000000 | kind.color, value.kindColor(kind));
            }
        }
        assertFalse(new ClientConfig().storageEsp.enabled);
    }

    @Test void preferencesSurviveJsonAndAreClamped() throws Exception {
        var config = new ClientConfig();
        var value = config.storageEsp;
        value.enabled = value.favorite = value.tracers = true;
        value.showHud = false;
        value.range = 99_999;
        value.maxTargets = -5;
        value.opacity = 300;
        value.kinds.put(StorageKind.HOPPERS.id, false);
        value.colors.put(StorageKind.BARRELS.id, 0xAB123456);
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var loaded = store.load().storageEsp;
        assertTrue(loaded.enabled && loaded.favorite && loaded.tracers);
        assertFalse(loaded.showHud);
        assertEquals(StorageEspSettings.MAX_RANGE, loaded.range);
        assertEquals(StorageEspSettings.MIN_TARGETS, loaded.maxTargets);
        assertEquals(255, loaded.opacity);
        assertFalse(loaded.kindEnabled(StorageKind.HOPPERS));
        assertTrue(loaded.kindEnabled(StorageKind.CHESTS));
        assertEquals(0x123456, loaded.colors.get(StorageKind.BARRELS.id));
        assertEquals(0xFF123456, loaded.kindColor(StorageKind.BARRELS));
    }

    @Test void unknownAndNullEntriesAreRepaired() {
        var value = new StorageEspSettings();
        value.kinds = new LinkedHashMap<>();
        value.kinds.put("future-kind", false);
        value.kinds.put(StorageKind.BARRELS.id, null);
        value.colors = new LinkedHashMap<>();
        value.colors.put("future-kind", 0x123456);
        value.colors.put(StorageKind.CHESTS.id, null);
        value.range = -1;
        value.maxTargets = Integer.MAX_VALUE;
        value.opacity = -9;
        value.sanitize();
        assertFalse(value.kinds.containsKey("future-kind"));
        assertFalse(value.colors.containsKey("future-kind"));
        assertTrue(value.kindEnabled(StorageKind.BARRELS), "A null toggle falls back to enabled");
        assertEquals(StorageKind.CHESTS.color, value.colors.get(StorageKind.CHESTS.id));
        assertEquals(StorageKind.values().length, value.kinds.size());
        assertEquals(StorageEspSettings.MIN_RANGE, value.range);
        assertEquals(StorageEspSettings.MAX_TARGETS, value.maxTargets);
        assertEquals(0, value.opacity);
    }

    @Test void publishKeyTracksFilterRangeAndLimitButNotColors() {
        var value = new StorageEspSettings();
        value.sanitize();
        int key = value.publishKey();
        value.colors.put(StorageKind.CHESTS.id, 0x010203);
        value.opacity = 10;
        value.tracers = true;
        assertEquals(key, value.publishKey(), "Colors, opacity and tracers are read live and need no re-publish");
        value.kinds.put(StorageKind.CHESTS.id, false);
        assertNotEquals(key, value.publishKey());
        value.kinds.put(StorageKind.CHESTS.id, true);
        value.range = 64;
        assertNotEquals(key, value.publishKey());
        value.range = 128;
        value.maxTargets = 32;
        assertNotEquals(key, value.publishKey());
    }
}
