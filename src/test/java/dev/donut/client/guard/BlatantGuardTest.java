package dev.donut.client.guard;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.BlatantGuardSettings;
import dev.donut.client.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The multiplayer guard: single-player always, servers only when allowed, addresses matched loosely. */
class BlatantGuardTest {
    @TempDir Path directory;
    private static final BlatantGuard.Place SINGLE = new BlatantGuard.Place(true, true, null);

    private static BlatantGuard.Place server(String address) { return new BlatantGuard.Place(true, false, BlatantGuard.normalize(address)); }

    @Test void normalizesAddresses() {
        assertEquals("play.example.net", BlatantGuard.normalize("  Play.Example.NET:25565 "));
        assertEquals("play.example.net", BlatantGuard.normalize("play.example.net."));
        assertEquals("10.0.0.5:25570", BlatantGuard.normalize("10.0.0.5:25570"), "Other ports are kept: a different server");
        assertNull(BlatantGuard.normalize("   "));
        assertNull(BlatantGuard.normalize(null));
    }

    @Test void blocksUnlistedServersOnly() {
        var settings = new BlatantGuardSettings();
        assertTrue(settings.blockUnlisted, "On by default");
        assertTrue(BlatantGuard.allowed(settings, SINGLE), "Single-player always runs");
        assertTrue(BlatantGuard.allowed(settings, BlatantGuard.Place.NOWHERE), "Nothing to guard outside a world");
        assertFalse(BlatantGuard.allowed(settings, server("play.example.net")));
        assertFalse(BlatantGuard.allowed(settings, server(null)), "An unknown server can't be on the list");
        settings.allow("Play.Example.net:25565", true);
        assertTrue(BlatantGuard.allowed(settings, server("play.example.net")));
        assertFalse(BlatantGuard.allowed(settings, server("other.example.net")));
        settings.allow("play.example.net", false);
        assertFalse(BlatantGuard.allowed(settings, server("play.example.net")));
        settings.blockUnlisted = false;
        assertTrue(BlatantGuard.allowed(settings, server("other.example.net")), "Guard off: runs everywhere");
        assertTrue(BlatantGuard.allowed(null, server("x")));
    }

    @Test void onlyTheFourBlatantModulesAreGuarded() {
        for (int module = 2; module <= 28; module++)
            assertEquals(module == 3 || module == 6 || module == 20 || module == 28, BlatantGuard.isBlatant(module), "Module " + module);
    }

    @Test void allowlistPersistsAndIsCleaned() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"blatantGuard\":{\"blockUnlisted\":true,\"allowedServers\":[\"A.example:25565\",\"a.example\",\"  \",null,\"b.example\"]}}");
        var loaded = new ConfigStore(file).load().blatantGuard;
        assertEquals(List.of("a.example", "b.example"), loaded.allowedServers);
        Files.writeString(file, "{\"autoMiner\":{\"range\":30}}");
        var older = new ConfigStore(file).load().blatantGuard;
        assertTrue(older.blockUnlisted && older.allowedServers.isEmpty(), "Older files get the guard on with an empty list");
        var full = new BlatantGuardSettings();
        for (int i = 0; i < BlatantGuardSettings.MAX_SERVERS + 5; i++) full.allow("s" + i + ".example", true);
        assertEquals(BlatantGuardSettings.MAX_SERVERS, full.allowedServers.size());
    }

    @Test void riskRecordsMatchTheTest() {
        assertEquals(ModuleRisk.Level.BANS, ModuleRisk.of(6).level());
        for (int module : new int[] {3, 20, 28}) assertEquals(ModuleRisk.Level.FLAGGED, ModuleRisk.of(module).level(), "Module " + module);
        assertEquals(ModuleRisk.Level.LIMITED, ModuleRisk.of(23).level());
        assertEquals(ModuleRisk.Level.LIMITED, ModuleRisk.of(25).level());
        assertEquals(ModuleRisk.Level.NOT_FLAGGED, ModuleRisk.of(15).level());
        assertFalse(ModuleRisk.showOnTile(15), "Quiet modules carry no tile tag");
        assertTrue(ModuleRisk.showOnTile(6) && ModuleRisk.showOnTile(23));
    }
}
