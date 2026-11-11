package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.automine.OreKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoMinerSettingsTest {
    @TempDir Path directory;

    private static final Set<OreKind> DEFAULT_TARGETS = Set.of(OreKind.DIAMOND, OreKind.IRON, OreKind.GOLD, OreKind.EMERALD,
            OreKind.ANCIENT_DEBRIS);

    @Test void newAndOlderConfigurationsStartDisabledWithSafeDefaults() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"autoMiner\":null}", "{\"autoMiner\":{\"ores\":null}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().autoMiner;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertFalse(value.includeBuried, "Buried ores are opt-in");
            assertTrue(value.protectTools);
            assertTrue(value.showHud);
            assertEquals(24, value.range);
            assertEquals(6, value.minHealth);
            assertEquals(8, value.toolReserve);
            assertEquals(32, value.tunnelLimit);
            assertEquals(AutoMinerSettings.DEFAULT_TURN, value.turnSpeed);
            for (OreKind kind : OreKind.values())
                assertEquals(DEFAULT_TARGETS.contains(kind), value.oreEnabled(kind), kind.id);
        }
        assertFalse(new ClientConfig().autoMiner.enabled);
    }

    @Test void preferencesSurviveJsonAndAreClamped() throws Exception {
        var config = new ClientConfig();
        var value = config.autoMiner;
        value.favorite = value.includeBuried = true;
        value.protectTools = value.showHud = false;
        value.range = 500;
        value.minHealth = 0;
        value.toolReserve = 9999;
        value.tunnelLimit = 1;
        value.turnSpeed = 90;
        value.ores.put(OreKind.COAL.id, true);
        value.ores.put(OreKind.DIAMOND.id, false);
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var loaded = store.load().autoMiner;
        assertTrue(loaded.favorite && loaded.includeBuried);
        assertFalse(loaded.protectTools || loaded.showHud);
        assertEquals(AutoMinerSettings.MAX_RANGE, loaded.range);
        assertEquals(AutoMinerSettings.MIN_HEALTH, loaded.minHealth);
        assertEquals(AutoMinerSettings.MAX_TOOL_RESERVE, loaded.toolReserve);
        assertEquals(AutoMinerSettings.MIN_TUNNEL, loaded.tunnelLimit);
        assertEquals(AutoMinerSettings.MAX_TURN, loaded.turnSpeed);
        assertTrue(loaded.oreEnabled(OreKind.COAL));
        assertFalse(loaded.oreEnabled(OreKind.DIAMOND));
        assertTrue(loaded.oreEnabled(OreKind.IRON));
    }

    @Test void unknownMissingAndNullEntriesAreRepaired() {
        var value = new AutoMinerSettings();
        value.ores = new LinkedHashMap<>();
        value.ores.put("future-ore", true);
        value.ores.put(OreKind.EMERALD.id, null);
        value.ores.put(OreKind.COAL.id, null);
        value.range = -3;
        value.minHealth = 40;
        value.toolReserve = -1;
        value.tunnelLimit = Integer.MAX_VALUE;
        value.turnSpeed = Double.NaN;
        value.sanitize();
        assertFalse(value.ores.containsKey("future-ore"));
        assertEquals(OreKind.values().length, value.ores.size(), "Missing kinds are filled in");
        assertTrue(value.oreEnabled(OreKind.EMERALD), "A null toggle falls back to the kind's default");
        assertFalse(value.oreEnabled(OreKind.COAL));
        assertEquals(AutoMinerSettings.MIN_RANGE, value.range);
        assertEquals(AutoMinerSettings.MAX_HEALTH, value.minHealth);
        assertEquals(AutoMinerSettings.MIN_TOOL_RESERVE, value.toolReserve);
        assertEquals(AutoMinerSettings.MAX_TUNNEL, value.tunnelLimit);
        assertEquals(AutoMinerSettings.DEFAULT_TURN, value.turnSpeed);
        value.turnSpeed = Double.POSITIVE_INFINITY;
        value.ores = null;
        value.sanitize();
        assertEquals(AutoMinerSettings.DEFAULT_TURN, value.turnSpeed);
        assertNotNull(value.ores);
        assertTrue(value.oreEnabled(OreKind.DIAMOND));
        assertFalse(value.oreEnabled(null));
    }
}
