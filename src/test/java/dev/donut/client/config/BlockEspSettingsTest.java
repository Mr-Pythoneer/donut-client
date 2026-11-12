package dev.donut.client.config;

import dev.donut.client.blockesp.OreMarker;
import dev.donut.client.blockesp.OreMarker.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BlockEspSettingsTest {
    @TempDir Path directory;

    @Test void missingAndNullPreferencesUseTheRequestedSeedAndBothTargets() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"blockEsp\":null}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().blockEsp;
            assertEquals("6608149111735331168", value.seed);
            assertTrue(value.emerald && value.coal);
            assertFalse(value.enabled);
        }
    }

    @Test void full64BitSeedAndIndependentPreferencesSurviveJson() throws Exception {
        var config = new ClientConfig();
        config.blockEsp.seed = "6608149111735331168";
        config.blockEsp.enabled = config.blockEsp.favorite = config.blockEsp.tracers = true;
        config.blockEsp.coal = false;
        config.blockEsp.radius = 999;
        config.blockEsp.opacity = -1;
        config.blockEsp.emeraldColor = 0xAA123456;
        var store = new ConfigStore(directory.resolve("settings.json"));
        store.save(config);
        var value = store.load().blockEsp;
        assertEquals(6608149111735331168L, BlockEspSettings.parseSeed(value.seed));
        assertTrue(value.enabled && value.favorite && value.tracers && value.emerald);
        assertFalse(value.coal);
        assertEquals(8, value.radius);
        assertEquals(0, value.opacity);
        assertEquals(0x123456, value.emeraldColor);
        assertTrue(Files.readString(directory.resolve("settings.json")).contains("\"6608149111735331168\""));
    }

    @Test void freshDefaultsReproduceTheOriginalTwoDeepslateTargets() {
        var value = new BlockEspSettings();
        assertEquals(OreMarker.LEGACY_MASK, value.targetMask());
        assertFalse(value.stoneVariants);
        assertTrue(value.deepslateVariants && value.fadeByConfidence);
        assertEquals(0, value.minConfidence, "Every not-gone prediction stays visible by default");
        for (Kind kind : Kind.values())
            assertEquals(kind == Kind.EMERALD || kind == Kind.COAL, value.oreEnabled(kind), kind.id);
        assertEquals(0x55EE88, value.oreColor(Kind.EMERALD));
        assertEquals(0xA7B7CB, value.oreColor(Kind.COAL));
    }

    @Test void oldSavedSettingsWithOnlyEmeraldAndCoalLoadToIdenticalBehaviour() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"blockEsp\":{\"enabled\":true,\"seed\":\"-42\",\"emerald\":false,\"coal\":true,"
                + "\"tracers\":true,\"showHud\":false,\"radius\":6,\"emeraldColor\":1193046,\"coalColor\":65280,\"opacity\":99}}");
        var value = new ConfigStore(file).load().blockEsp;
        assertEquals(OreMarker.bit(Kind.COAL, true), value.targetMask(), "Only deepslate coal, as before");
        assertTrue(value.enabled && value.tracers && !value.showHud);
        assertEquals("-42", value.seed);
        assertEquals(6, value.radius);
        assertEquals(99, value.opacity);
        assertEquals(0x123456, value.oreColor(Kind.EMERALD));
        assertEquals(0x00FF00, value.oreColor(Kind.COAL));
        assertEquals(Kind.DIAMOND.defaultColor, value.oreColor(Kind.DIAMOND));
        assertEquals(0, value.minConfidence);
    }

    @Test void unknownKeysAreIgnoredAndNewOresVariantsAndClampsPersist() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"blockEsp\":{\"netherite\":true,\"netheriteColor\":5,\"ores\":{\"diamond\":true},"
                + "\"minConfidence\":250,\"diamondColor\":-1,\"stoneVariants\":true,\"deepslateVariants\":false}}");
        var store = new ConfigStore(file);
        var value = store.load().blockEsp;
        assertFalse(value.diamond, "An unknown map field must not switch an ore on");
        assertEquals(100, value.minConfidence);
        assertEquals(0xFFFFFF, value.oreColor(Kind.DIAMOND));
        assertEquals(OreMarker.bit(Kind.EMERALD, false) | OreMarker.bit(Kind.COAL, false), value.targetMask());
        value.setOreEnabled(Kind.DIAMOND, true);
        value.setOreColor(Kind.LAPIS, 0xAB123456);
        value.minConfidence = -7;
        value.deepslateVariants = true;
        var config = new ClientConfig();
        config.blockEsp = value;
        store.save(config);
        var loaded = store.load().blockEsp;
        assertTrue(loaded.oreEnabled(Kind.DIAMOND));
        assertEquals(0x123456, loaded.oreColor(Kind.LAPIS));
        assertEquals(0, loaded.minConfidence);
        assertEquals(6, Integer.bitCount(loaded.targetMask()), "Three ores in two variants");
        for (Kind kind : Kind.values()) { loaded.setOreEnabled(kind, false); }
        assertFalse(loaded.anyOre());
        assertEquals(0, loaded.targetMask());
    }

    @Test void numericValidationCoversSignedLimitsAndRejectsLossyOrMalformedSeeds() {
        assertEquals(Long.MIN_VALUE, BlockEspSettings.parseSeed("-9223372036854775808"));
        assertEquals(Long.MAX_VALUE, BlockEspSettings.parseSeed("9223372036854775807"));
        assertEquals(0, BlockEspSettings.parseSeed(" 0 "));
        for (String invalid : new String[] {"", "-", "9223372036854775808", "-9223372036854775809", "1.2", "6.6E18", "hello", "12 34"})
            assertThrows(IllegalArgumentException.class, () -> BlockEspSettings.parseSeed(invalid), invalid);
        var settings = new BlockEspSettings(); settings.seed = "invalid"; settings.sanitize();
        assertEquals("invalid", settings.seed, "Invalid persisted input must not become another valid seed");
    }
}
