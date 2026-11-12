package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.hudwidgets.HudWidget;
import dev.donut.client.storageesp.StorageKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Defaults, clamps and JSON persistence for HUD Widgets, NewChunks and Stash Finder. */
class NewToolsSettingsTest {
    @TempDir Path directory;

    @Test void newAndOlderConfigurationsStartDisabled() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"hudWidgets\":null,\"newChunks\":null,\"stashFinder\":null}",
                "{\"hudWidgets\":{\"widgets\":null,\"corners\":null},\"stashFinder\":{\"weights\":null}}"}) {
            Files.writeString(file, json);
            var config = new ConfigStore(file).load();
            assertFalse(config.hudWidgets.enabled || config.newChunks.enabled || config.stashFinder.enabled, "New modules start disabled: " + json);
            assertFalse(config.hudWidgets.favorite || config.newChunks.favorite || config.stashFinder.favorite);
            // Only FPS, ping, armour and potions are on by default.
            for (HudWidget widget : HudWidget.values()) {
                boolean expected = widget == HudWidget.FPS || widget == HudWidget.PING || widget == HudWidget.ARMOUR || widget == HudWidget.POTIONS;
                assertEquals(expected, config.hudWidgets.widgetEnabled(widget), widget.id);
            }
            assertEquals("Top right", config.hudWidgets.corner(HudWidget.FPS));
            assertEquals("Bottom right", config.hudWidgets.corner(HudWidget.ARMOUR));
            assertEquals("Bottom left", config.hudWidgets.corner(HudWidget.KEYSTROKES));
            assertTrue(config.hudWidgets.clock24h);
            assertEquals(16, config.newChunks.range);
            assertEquals(0, config.newChunks.height);
            assertTrue(config.newChunks.showNew && !config.newChunks.showOld && config.newChunks.throughWalls);
            assertEquals(8, config.stashFinder.threshold);
            assertTrue(config.stashFinder.notify);
            assertFalse(config.stashFinder.toast);
            assertEquals(1, config.stashFinder.weight(StorageKind.CHESTS));
            assertEquals(1, config.stashFinder.weight(StorageKind.SHULKER_BOXES));
            assertEquals(0, config.stashFinder.weight(StorageKind.HOPPERS));
            assertEquals(0, config.stashFinder.weight(StorageKind.STORAGE_ENTITIES));
        }
    }

    @Test void preferencesSurviveJsonAndAreClamped() throws Exception {
        var config = new ClientConfig();
        config.hudWidgets.enabled = true;
        config.hudWidgets.setWidgetEnabled(HudWidget.CPS, true);
        config.hudWidgets.setWidgetEnabled(HudWidget.FPS, false);
        config.hudWidgets.cycleCorner(HudWidget.PING); // Top right -> Bottom left
        config.hudWidgets.corners.put(HudWidget.CLOCK.id, "Middle");
        config.hudWidgets.widgets.put("not-a-widget", true);
        config.hudWidgets.clock24h = false;
        config.newChunks.enabled = true;
        config.newChunks.range = 999;
        config.newChunks.height = -500;
        config.newChunks.opacity = 300;
        config.newChunks.newColor = 0xAB123456;
        config.stashFinder.enabled = true;
        config.stashFinder.threshold = 1;
        config.stashFinder.weights.put(StorageKind.BARRELS.id, 99);
        config.stashFinder.weights.put(StorageKind.HOPPERS.id, -3);
        config.stashFinder.weights.put(StorageKind.STORAGE_ENTITIES.id, 4);
        config.stashFinder.weights.put("bogus", 2);
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var loaded = store.load();
        assertTrue(loaded.hudWidgets.enabled);
        assertTrue(loaded.hudWidgets.widgetEnabled(HudWidget.CPS));
        assertFalse(loaded.hudWidgets.widgetEnabled(HudWidget.FPS));
        assertEquals("Bottom left", loaded.hudWidgets.corner(HudWidget.PING));
        assertEquals("Top right", loaded.hudWidgets.corners.get(HudWidget.CLOCK.id), "An unknown corner is repaired");
        assertFalse(loaded.hudWidgets.widgets.containsKey("not-a-widget"));
        assertFalse(loaded.hudWidgets.clock24h);
        assertEquals(NewChunksSettings.MAX_RANGE, loaded.newChunks.range);
        assertEquals(NewChunksSettings.MIN_HEIGHT, loaded.newChunks.height);
        assertEquals(255, loaded.newChunks.opacity);
        assertEquals(0x123456, loaded.newChunks.newColor);
        assertEquals(StashFinderSettings.MIN_THRESHOLD, loaded.stashFinder.threshold);
        assertEquals(StashFinderSettings.MAX_WEIGHT, loaded.stashFinder.weight(StorageKind.BARRELS));
        assertEquals(0, loaded.stashFinder.weight(StorageKind.HOPPERS));
        assertEquals(0, loaded.stashFinder.weight(StorageKind.STORAGE_ENTITIES), "Vehicles never count");
        assertFalse(loaded.stashFinder.weights.containsKey("bogus"));
        assertFalse(loaded.stashFinder.weights.containsKey(StorageKind.STORAGE_ENTITIES.id));
    }

    @Test void cornersCycleThroughAllFour() {
        var settings = new HudWidgetsSettings();
        String start = settings.corner(HudWidget.FPS);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) { seen.add(settings.corner(HudWidget.FPS)); settings.cycleCorner(HudWidget.FPS); }
        assertEquals(java.util.Set.copyOf(HudWidgetsSettings.CORNERS), seen);
        assertEquals(start, settings.corner(HudWidget.FPS));
    }

    @Test void profileImportDisablesTheNewModules() {
        var config = new ClientConfig();
        config.hudWidgets.enabled = config.newChunks.enabled = config.stashFinder.enabled = true;
        ConfigProfiles.disableModules(config);
        assertFalse(config.hudWidgets.enabled || config.newChunks.enabled || config.stashFinder.enabled);
        assertEquals("HUD Widgets", ConfigProfiles.DISPLAY_NAMES.get("hudWidgets"));
        assertEquals("NewChunks", ConfigProfiles.DISPLAY_NAMES.get("newChunks"));
        assertEquals("Stash Finder", ConfigProfiles.DISPLAY_NAMES.get("stashFinder"));
    }
}
