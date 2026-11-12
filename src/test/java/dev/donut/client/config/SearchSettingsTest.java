package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchSettingsTest {
    @TempDir Path directory;

    @Test void newAndOlderConfigurationsStartDisabledWithAnEmptyList() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"search\":null}", "{\"search\":{\"blocks\":null}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().search;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertNotNull(value.blocks);
            assertTrue(value.blocks.isEmpty(), "The default list is empty");
            assertTrue(value.mergeAdjacent && value.showHud);
            assertFalse(value.tracers);
            assertEquals(96, value.range);
            assertEquals(512, value.maxResults);
            assertEquals(200, value.opacity);
        }
        assertFalse(new ClientConfig().search.enabled);
    }

    @Test void preferencesSurviveJsonAndAreClamped() {
        var config = new ClientConfig();
        var value = config.search;
        value.enabled = value.tracers = true;
        value.mergeAdjacent = false;
        value.range = 1;
        value.maxResults = 1 << 20;
        value.opacity = -4;
        value.add("diamond_ore", 0xFF112233);
        value.add("minecraft:spawner", 0x445566);
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var loaded = store.load().search;
        assertTrue(loaded.enabled && loaded.tracers);
        assertFalse(loaded.mergeAdjacent);
        assertEquals(SearchSettings.MIN_RANGE, loaded.range);
        assertEquals(SearchSettings.MAX_RESULTS, loaded.maxResults);
        assertEquals(0, loaded.opacity);
        assertEquals(2, loaded.blocks.size());
        assertEquals("minecraft:diamond_ore", loaded.blocks.get(0).id);
        assertEquals(0x112233, loaded.blocks.get(0).color);
        assertEquals(0xFF445566, loaded.color("minecraft:spawner"));
        assertEquals(0xFF000000 | SearchSettings.FALLBACK_COLOR, loaded.color("minecraft:unknown"));
    }

    @Test void unknownKeysAreIgnored() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"search\":{\"later\":1,\"blocks\":[{\"id\":\"spawner\",\"color\":255,\"extra\":true}]}}");
        var value = new ConfigStore(file).load().search;
        assertEquals(1, value.blocks.size());
        assertEquals("minecraft:spawner", value.blocks.getFirst().id);
        assertEquals(255, value.blocks.getFirst().color);
    }

    @Test void idsAreNormalizedOrRejectedWithAReason() {
        assertEquals("minecraft:diamond_ore", SearchSettings.normalize("  Diamond_Ore "));
        assertEquals("minecraft:diamond_ore", SearchSettings.normalize(":diamond_ore"));
        assertEquals("mymod:copper/block", SearchSettings.normalize("MyMod:copper/block"));
        for (String bad : new String[] {"", "   ", null, "a:b:c", "minecraft:", "diamond ore", "ore!", "my mod:x"})
            assertThrows(IllegalArgumentException.class, () -> SearchSettings.normalize(bad), String.valueOf(bad));
        var failure = assertThrows(IllegalArgumentException.class, () -> SearchSettings.normalize("diamond ore"));
        assertTrue(failure.getMessage().contains("' '"), failure.getMessage());
    }

    @Test void sanitizeDropsInvalidDuplicateAndAirEntries() {
        var value = new SearchSettings();
        value.blocks = new ArrayList<>(Arrays.asList(new SearchSettings.Entry("Spawner", 0xFF010203), null,
                new SearchSettings.Entry("minecraft:spawner", 5), new SearchSettings.Entry("bad id", 0),
                new SearchSettings.Entry(null, 0), new SearchSettings.Entry("air", 0), new SearchSettings.Entry("chest", 7)));
        value.sanitize();
        assertEquals(2, value.blocks.size());
        assertEquals("minecraft:spawner", value.blocks.get(0).id);
        assertEquals(0x010203, value.blocks.get(0).color);
        assertEquals("minecraft:chest", value.blocks.get(1).id);
        value.blocks = null;
        value.sanitize();
        assertNotNull(value.blocks);
        for (int i = 0; i < SearchSettings.MAX_BLOCKS + 5; i++) value.blocks.add(new SearchSettings.Entry("block" + i, 0));
        value.sanitize();
        assertEquals(SearchSettings.MAX_BLOCKS, value.blocks.size());
    }

    @Test void addRemoveAndKeys() {
        var value = new SearchSettings();
        int empty = value.listKey(), publish = value.publishKey();
        assertEquals(SearchSettings.PALETTE[0], value.nextColor());
        assertTrue(value.add("spawner", value.nextColor()));
        assertEquals(SearchSettings.PALETTE[1], value.nextColor(), "Each new entry gets the next palette colour");
        assertFalse(value.add("minecraft:spawner", 0), "Duplicates are refused");
        assertNotEquals(empty, value.listKey(), "Changing the list needs a rescan");
        value.find("minecraft:spawner").color = 0x123456;
        value.opacity = 1;
        value.tracers = true;
        assertEquals(publish, value.publishKey(), "Colours, opacity and tracers are read live");
        value.mergeAdjacent = false;
        assertNotEquals(publish, value.publishKey());
        value.mergeAdjacent = true;
        value.range = 200;
        assertNotEquals(publish, value.publishKey());
        assertTrue(value.remove("minecraft:spawner"));
        assertFalse(value.remove("minecraft:spawner"));
        assertEquals(empty, value.listKey());
        for (int i = 0; i < SearchSettings.MAX_BLOCKS; i++) value.add("b" + i, 0);
        assertThrows(IllegalArgumentException.class, () -> value.add("onemore", 0));
    }
}
