package dev.donut.client.autoreconnect;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.AutoReconnectSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconnectPolicyTest {
    @TempDir Path directory;

    @Test void fixedDelayWithoutBackoff() {
        for (int attempt = 1; attempt <= 10; attempt++) assertEquals(5, ReconnectPolicy.delaySeconds(5, attempt, false));
        assertEquals(1, ReconnectPolicy.delaySeconds(0, 1, false), "A delay is always at least a second");
    }

    @Test void backoffDoublesUpToAMinute() {
        assertEquals(5, ReconnectPolicy.delaySeconds(5, 1, true), "The first attempt waits the configured delay");
        assertEquals(10, ReconnectPolicy.delaySeconds(5, 2, true));
        assertEquals(20, ReconnectPolicy.delaySeconds(5, 3, true));
        assertEquals(40, ReconnectPolicy.delaySeconds(5, 4, true));
        assertEquals(60, ReconnectPolicy.delaySeconds(5, 5, true));
        assertEquals(60, ReconnectPolicy.delaySeconds(5, 500, true), "Huge attempt numbers don't overflow");
        assertEquals(60, ReconnectPolicy.delaySeconds(60, 3, true));
        assertEquals(1, ReconnectPolicy.delaySeconds(1, 0, true));
    }

    @Test void attemptLimits() {
        assertTrue(ReconnectPolicy.attemptsLeft(0, 5));
        assertTrue(ReconnectPolicy.attemptsLeft(4, 5));
        assertFalse(ReconnectPolicy.attemptsLeft(5, 5));
        assertTrue(ReconnectPolicy.attemptsLeft(10_000, 0), "0 means unlimited");
        assertEquals("attempt 2/5", ReconnectPolicy.attemptLabel(2, 5));
        assertEquals("attempt 7", ReconnectPolicy.attemptLabel(7, 0));
    }

    @Test void bansWhitelistsAndVersionMismatchesAreFinal() {
        assertEquals("banned", ReconnectPolicy.finalReason("multiplayer.disconnect.banned.reason", "You are banned from this server."));
        assertEquals("banned", ReconnectPolicy.finalReason("multiplayer.disconnect.banned_ip.reason", null));
        assertEquals("not whitelisted", ReconnectPolicy.finalReason("multiplayer.disconnect.not_whitelisted", null));
        assertEquals("logged in elsewhere", ReconnectPolicy.finalReason("multiplayer.disconnect.duplicate_login", null));
        assertEquals("version mismatch", ReconnectPolicy.finalReason("multiplayer.disconnect.outdated_client", null));
        // Plugin kicks are plain text.
        assertEquals("banned", ReconnectPolicy.finalReason(null, "You have been TEMPORARILY BANNED for 3 days"));
        assertEquals("not whitelisted", ReconnectPolicy.finalReason(null, "You are not white-listed on this server!"));
        assertEquals("blacklisted", ReconnectPolicy.finalReason(null, "Your IP is blacklisted"));
    }

    @Test void ordinaryDisconnectsAreRetried() {
        assertNull(ReconnectPolicy.finalReason("disconnect.timeout", "Timed out"));
        assertNull(ReconnectPolicy.finalReason("multiplayer.disconnect.server_shutdown", "Server closed"));
        assertNull(ReconnectPolicy.finalReason(null, "Connection refused"));
        assertNull(ReconnectPolicy.finalReason("multiplayer.disconnect.kicked", "Kicked by an operator"));
        assertNull(ReconnectPolicy.finalReason(null, "The server is restarting, please rejoin shortly"));
        assertNull(ReconnectPolicy.finalReason(null, null));
    }

    @Test void settingsStartDisabledSurviveJsonAndAreClamped() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"autoReconnect\":null}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().autoReconnect;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertEquals(5, value.delaySeconds);
            assertEquals(5, value.maxAttempts);
            assertTrue(value.backoff && value.skipBans);
        }
        var config = new ClientConfig();
        config.autoReconnect.delaySeconds = 0;
        config.autoReconnect.maxAttempts = 999;
        config.autoReconnect.backoff = config.autoReconnect.skipBans = false;
        var store = new ConfigStore(file);
        store.save(config);
        var loaded = store.load().autoReconnect;
        assertEquals(AutoReconnectSettings.MIN_DELAY, loaded.delaySeconds);
        assertEquals(AutoReconnectSettings.MAX_ATTEMPTS, loaded.maxAttempts);
        assertFalse(loaded.backoff || loaded.skipBans);
        loaded.delaySeconds = 600;
        loaded.maxAttempts = -3;
        loaded.sanitize();
        assertEquals(AutoReconnectSettings.MAX_DELAY, loaded.delaySeconds);
        assertEquals(0, loaded.maxAttempts);
    }
}
