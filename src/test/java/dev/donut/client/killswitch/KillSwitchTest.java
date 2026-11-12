package dev.donut.client.killswitch;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test-build expiry: real-time clock from the first run, per build, surviving restarts and a clock set back. */
class KillSwitchTest {
    private static final long H = KillSwitch.HOUR, START = 1_800_000_000_000L;
    @TempDir Path directory;

    @Test void expiresAfterTheConfiguredHours() {
        assertFalse(KillSwitch.expired(START, START, START + 72 * H - 1, 72));
        assertTrue(KillSwitch.expired(START, START, START + 72 * H, 72));
        assertFalse(KillSwitch.expired(START, START, START + 10_000 * H, 0), "0 hours never expires");
        assertEquals(3 * H, KillSwitch.remaining(START, START, START + 69 * H, 72));
        assertEquals(0, KillSwitch.remaining(START, START, START + 80 * H, 72));
        assertEquals(Long.MAX_VALUE, KillSwitch.remaining(START, START, START, 0));
    }

    @Test void settingTheClockBackDoesNotAddTime() {
        // Latest seen was 71 h in; the clock now reads the start time again: still 1 h left, not 72.
        assertEquals(H, KillSwitch.remaining(START, START + 71 * H, START, 72));
        assertTrue(KillSwitch.expired(START, START + 72 * H, START - 5 * H, 72));
    }

    @Test void clockSurvivesRestartsAndCountsWhileClosed() {
        Path file = directory.resolve("activations.properties");
        var build = new KillSwitch.Build("3.0-20261112T120000", 72);
        var first = new KillSwitch(build, file, START);
        assertFalse(first.expired(START + H));
        assertEquals(71 * H, first.remaining(START + H));
        // Game closed for two days, then opened again: the clock kept running.
        var later = new KillSwitch(build, file, START + 48 * H);
        assertEquals(24 * H, later.remaining(START + 48 * H));
        assertFalse(later.expired(START + 71 * H));
        assertTrue(later.expired(START + 72 * H));
        // Once expired, it stays expired in that session even if the clock goes back.
        assertTrue(later.expired(START));
        // And a restart with the clock set back to the start is still expired.
        assertTrue(new KillSwitch(build, file, START).expired(START));
    }

    @Test void eachBuildHasItsOwnClock() {
        Path file = directory.resolve("activations.properties");
        var old = new KillSwitch(new KillSwitch.Build("3.0-a", 72), file, START);
        assertTrue(old.expired(START + 100 * H));
        var fresh = new KillSwitch(new KillSwitch.Build("3.0-b", 72), file, START + 100 * H);
        assertFalse(fresh.expired(START + 100 * H));
        assertEquals(72 * H, fresh.remaining(START + 100 * H));
        assertTrue(new KillSwitch(new KillSwitch.Build("3.0-a", 72), file, START + 100 * H).expired(START + 100 * H));
    }

    @Test void neverExpiringBuildsWriteNothing() throws Exception {
        Path file = directory.resolve("activations.properties");
        var none = new KillSwitch(KillSwitch.Build.NONE, file, START);
        assertFalse(none.active());
        assertFalse(none.expired(START + 100_000 * H));
        assertFalse(Files.exists(file));
    }

    @Test void damagedStateStartsAFreshClock() throws Exception {
        Path file = directory.resolve("activations.properties");
        Files.writeString(file, "3.0-a=garbage\n3.0-b=5,1\n\\u12");
        var build = new KillSwitch(new KillSwitch.Build("3.0-a", 72), file, START);
        assertEquals(72 * H, build.remaining(START));
        assertNull(KillSwitch.parse("10,5"));
        assertNull(KillSwitch.parse("x,5"));
        assertArrayEquals(new long[] {5, 10}, KillSwitch.parse("5,10"));
    }

    @Test void describesTimeLeft() {
        assertEquals("2d 5h", KillSwitch.describe(53 * H + 59 * 60_000L));
        assertEquals("5h 12m", KillSwitch.describe(5 * H + 12 * 60_000L));
        assertEquals("12m", KillSwitch.describe(12 * 60_000L + 5_000));
        assertEquals("1m", KillSwitch.describe(10_000));
    }

    @Test void releaseBuildsCarryTheirLifetime() {
        var build = KillSwitch.currentBuild();
        assertNotEquals("dev", build.id(), "processResources did not expand donut-build.properties");
        assertFalse(build.id().contains("$"));
        assertEquals(Integer.parseInt(System.getProperty("donut.expectedKillSwitchHours", "72")), build.hours());
    }
}
