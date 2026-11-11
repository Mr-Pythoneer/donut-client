package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigProfilesTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:34:56.789Z");
    @TempDir Path directory;

    private static ClientConfig customized() {
        ClientConfig config = new ClientConfig();
        config.freecam.speed = 2.5;
        config.freecam.favorite = true;
        config.storageEsp.range = 200;
        config.storageEsp.tracers = true;
        config.blockEsp.seed = "12345";
        config.accentColor = 0x27ADC9;
        config.showHud = false;
        config.fullbright.favorite = true;
        return config;
    }

    private static Map<String, String> keys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("key.donut.toggle_freecam", "key.keyboard.f6");
        keys.put("key.donut.toggle_storage_esp", "key.keyboard.g");
        return keys;
    }

    @Test void settingsAndKeybindsSurviveARoundTrip() {
        String json = ConfigProfiles.toJson(customized(), keys(), "3.0", NOW);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, root.get("format").getAsInt());
        assertEquals("3.0", root.get("modVersion").getAsString());
        assertEquals("2026-11-10T12:34:56Z", root.get("exportedAt").getAsString());
        assertEquals("key.keyboard.f6", root.getAsJsonObject("keybinds").get("key.donut.toggle_freecam").getAsString());
        assertTrue(root.get("config").isJsonObject());

        ConfigProfiles.Profile profile = ConfigProfiles.parse(json);
        assertEquals(ConfigProfiles.FORMAT, profile.format());
        assertEquals("3.0", profile.modVersion());
        assertEquals("2026-11-10T12:34:56Z", profile.exportedAt());
        assertEquals(keys(), profile.keybinds());
        ClientConfig loaded = profile.config();
        assertEquals(2.5, loaded.freecam.speed);
        assertTrue(loaded.freecam.favorite);
        assertEquals(200, loaded.storageEsp.range);
        assertTrue(loaded.storageEsp.tracers);
        assertEquals("12345", loaded.blockEsp.seed);
        assertEquals(0x27ADC9, loaded.accentColor);
        assertFalse(loaded.showHud);
        assertTrue(loaded.fullbright.favorite);
    }

    @Test void exportDoesNotMutateTheLiveConfig() {
        ClientConfig live = new ClientConfig();
        live.storageEsp.range = 99_999;
        live.fullbright.enabled = true;
        String json = ConfigProfiles.toJson(live, Map.of(), "3.0", NOW);
        assertEquals(99_999, live.storageEsp.range);
        assertTrue(live.fullbright.enabled);
        JsonObject saved = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("config");
        assertEquals(StorageEspSettings.MAX_RANGE, saved.getAsJsonObject("storageEsp").get("range").getAsInt());
        // Exports contain whatever the config file would; only importing clears the flags.
        assertTrue(saved.getAsJsonObject("fullbright").get("enabled").getAsBoolean());
    }

    @Test void importingClearsEveryEnabledFlag() {
        ClientConfig source = customized();
        source.fullbright.enabled = true;
        source.storageEsp.enabled = true;
        source.blockEsp.enabled = true;
        source.soundDetector.enabled = true;
        source.chunkFinder.enabled = true;
        source.hotbar.enabled = true;
        String json = ConfigProfiles.toJson(source, Map.of(), "3.0", NOW);
        // Transient flags are never written by Gson; force one into the file by hand.
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        root.getAsJsonObject("config").getAsJsonObject("freecam").addProperty("enabled", true);
        root.getAsJsonObject("config").getAsJsonObject("fly").addProperty("enabled", true);
        ClientConfig loaded = ConfigProfiles.parse(root.toString()).config();
        assertFalse(loaded.fullbright.enabled);
        assertFalse(loaded.storageEsp.enabled);
        assertFalse(loaded.blockEsp.enabled);
        assertFalse(loaded.soundDetector.enabled);
        assertFalse(loaded.chunkFinder.enabled);
        assertFalse(loaded.hotbar.enabled);
        assertFalse(loaded.freecam.enabled);
        assertFalse(loaded.fly.enabled);
        assertEquals(2.5, loaded.freecam.speed);
    }

    @Test void disableModulesTurnsOffTransientFlagsToo() {
        ClientConfig config = new ClientConfig();
        config.freecam.enabled = true;
        config.fly.enabled = true;
        config.nameHider.enabled = true;
        config.lightDebug.enabled = true;
        config.staffDetector.enabled = true;
        config.amethyst.enabled = true;
        config.progressBar.enabled = true;
        ConfigProfiles.disableModules(config);
        assertFalse(config.freecam.enabled);
        assertFalse(config.fly.enabled);
        assertFalse(config.nameHider.enabled);
        assertFalse(config.lightDebug.enabled);
        assertFalse(config.staffDetector.enabled);
        assertFalse(config.amethyst.enabled);
        assertFalse(config.progressBar.enabled);
    }

    @Test void brokenProfilesAreRejectedWithAReason() {
        assertRejected("{broken json", "not valid JSON");
        assertRejected("", "empty");
        assertRejected("[1, 2]", "not a JSON object");
        assertRejected("{\"config\": {}}", "\"format\"");
        assertRejected("{\"format\": 2, \"config\": {}}", "Unsupported profile format 2");
        assertRejected("{\"format\": \"1\", \"config\": {}}", "Unsupported profile format");
        assertRejected("{\"format\": 1}", "no \"config\"");
        assertRejected("{\"format\": 1, \"config\": [1]}", "not an object");
        assertRejected("{\"format\": 1, \"config\": \"text\"}", "not an object");
        assertRejected("{\"format\": 1, \"config\": {\"freecam\": {\"speed\": \"fast\"}}}", "settings are invalid");
    }

    private static void assertRejected(String json, String expected) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> ConfigProfiles.parse(json));
        assertTrue(failure.getMessage().contains(expected), () -> "\"" + failure.getMessage() + "\" should mention " + expected);
    }

    @Test void foreignAndBlankKeybindsAreDropped() {
        String json = """
                {"format": 1, "config": {}, "keybinds": {
                  "key.donut.toggle_freecam": "key.keyboard.f6",
                  "key.donut.toggle_fly": "  ",
                  "key.donut.toggle_hotbar": 5,
                  "key.donut.": "key.keyboard.h",
                  "key.attack": "key.mouse.left",
                  "key.othermod.thing": "key.keyboard.k"
                }}""";
        ConfigProfiles.Profile profile = ConfigProfiles.parse(json);
        assertEquals(Map.of("key.donut.toggle_freecam", "key.keyboard.f6"), profile.keybinds());
        assertNull(profile.modVersion());
        assertFalse(ConfigProfiles.summarize(profile).isEmpty());
        assertTrue(ConfigProfiles.parse("{\"format\": 1, \"config\": {}, \"keybinds\": [\"x\"]}").keybinds().isEmpty());
    }

    @Test void missingSectionsAreRepairedLikeTheConfigFile() {
        ClientConfig config = ConfigProfiles.parse("{\"format\": 1, \"config\": {\"freecam\": null, \"storageEsp\": {\"range\": 1}}}").config();
        assertNotNull(config.freecam);
        assertNotNull(config.soundDetector);
        assertEquals(StorageEspSettings.MIN_RANGE, config.storageEsp.range);
    }

    @Test void copyIntoKeepsIdentityAndLiveEnabledFlags() {
        ClientConfig live = new ClientConfig();
        live.freecam.enabled = true;     // transient
        live.storageEsp.enabled = true;
        live.fullbright.enabled = false;
        ClientConfig imported = customized();
        imported.fullbright.enabled = true;   // must not switch on
        imported.storageEsp.enabled = false;  // must not switch off
        imported.freecam.enabled = false;
        ClientConfig before = live;
        ConfigProfiles.copyInto(live, imported);
        assertSame(before, live);
        assertTrue(live.freecam.enabled);
        assertTrue(live.storageEsp.enabled);
        assertFalse(live.fullbright.enabled);
        assertEquals(2.5, live.freecam.speed);
        assertEquals(200, live.storageEsp.range);
        assertEquals("12345", live.blockEsp.seed);
        assertEquals(0x27ADC9, live.accentColor);
        assertFalse(live.showHud);
        assertTrue(live.fullbright.favorite);
    }

    @Test void summaryMentionsFavoritesKeybindsAndNotableValues() {
        ConfigProfiles.Profile profile = ConfigProfiles.parse(ConfigProfiles.toJson(customized(), keys(), "3.0", NOW));
        String text = String.join("\n", ConfigProfiles.summarize(profile));
        assertTrue(text.contains("Donut Client 3.0"), text);
        assertTrue(text.contains("Favorites: Freecam, Fullbright"), text);
        assertTrue(text.contains("Shortcuts: 2 saved, 2 bound"), text);
        assertTrue(text.contains("Freecam: speed 2.5"), text);
        assertTrue(text.contains("Storage ESP: range 200"), text);
        assertFalse(ConfigProfiles.summarize(new ConfigProfiles.Profile(null, null, 1, null, null)).isEmpty());
        assertTrue(ConfigProfiles.summarize(null).isEmpty());
    }

    @Test void exportListAndBackupUseLocalFiles() throws IOException {
        Path dir = directory.resolve("donut-profiles");
        assertEquals(List.of(), ConfigProfiles.list(dir));
        Path first = ConfigProfiles.export(dir, customized(), keys(), "3.0", NOW);
        Path second = ConfigProfiles.export(dir, new ClientConfig(), Map.of(), "3.0", NOW);
        assertTrue(first.getFileName().toString().matches("donut-profile-\\d{8}-\\d{6}\\.json"), first.toString());
        assertNotEquals(first, second);
        Files.setLastModifiedTime(first, FileTime.from(NOW.minusSeconds(60)));
        Files.setLastModifiedTime(second, FileTime.from(NOW));
        Files.writeString(dir.resolve("notes.txt"), "ignored");
        assertEquals(List.of(second, first), ConfigProfiles.list(dir));
        assertEquals(2.5, ConfigProfiles.read(first).config().freecam.speed);
        assertFalse(Files.exists(first.resolveSibling(first.getFileName() + ".tmp")));

        Path configFile = directory.resolve("donut-client.json");
        assertNull(ConfigProfiles.backupCurrent(configFile));
        new ConfigStore(configFile).save(customized());
        String original = Files.readString(configFile);
        LocalDateTime at = LocalDateTime.of(2026, 11, 10, 9, 5, 7);
        Path backup = ConfigProfiles.backupCurrent(configFile, at);
        assertEquals("donut-client.json.before-import-20261110-090507", backup.getFileName().toString());
        assertEquals(original, Files.readString(backup));
        Path again = ConfigProfiles.backupCurrent(configFile, at);
        assertNotEquals(backup, again);
        assertEquals(original, Files.readString(again));
    }

    @Test void unreadableFilesAreRejectedWithoutThrowingIo() throws IOException {
        Path bad = directory.resolve("bad.json");
        Files.writeString(bad, "{\"format\": 9, \"config\": {}}");
        assertThrows(IllegalArgumentException.class, () -> ConfigProfiles.read(bad));
        assertThrows(IllegalArgumentException.class, () -> ConfigProfiles.read(directory.resolve("missing.json")));
    }
}
