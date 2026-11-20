/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.killswitch;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Build expiry. A build may carry a lifetime ({@code killSwitchHours} in donut-build.properties, set at build time with
 * {@code -PkillSwitchHours}, 0 = never). The clock starts the first time that build runs on a computer and keeps
 * running in real time whether or not the game is open. When it runs out, every module is switched off and cannot be
 * switched back on; a newer build starts its own clock.
 *
 * <p>The start time is kept per build in ~/.donut-client/activations.properties (outside .minecraft, so a fresh
 * instance or a deleted config does not reset it). The latest time seen is kept as well, so setting the system clock
 * back does not buy more time. This is a convenience limit for test builds, not copy protection.
 */
public final class KillSwitch {
    public static final long HOUR = 3_600_000L;
    /** How often the latest-seen time is written back while the game runs. */
    private static final long SAVE_EVERY = 10 * 60_000L;

    public record Build(String id, int hours) {
        public static final Build NONE = new Build("dev", 0);
    }

    private final Build build;
    private final Path file;
    private long first, latest, savedLatest;
    private boolean expired;

    public KillSwitch(Build build, Path file, long now) {
        this.build = build;
        this.file = file;
        reload(now);
    }

    /** Reads the stored clock again (used at start-up, and by runtime tests that rewrite the file). */
    public void reload(long now) {
        expired = false;
        if (build.hours() <= 0) return;
        Properties state = read(file);
        long[] stored = parse(state.getProperty(build.id()));
        if (stored == null) {
            first = latest = now;
        } else {
            first = stored[0];
            latest = Math.max(stored[1], now);
        }
        savedLatest = 0;
        persist();
        expired = expired(first, latest, now, build.hours());
    }

    /** The build's own settings, from the processed donut-build.properties resource. */
    public static Build currentBuild() {
        try (InputStream in = KillSwitch.class.getResourceAsStream("/donut-build.properties")) {
            if (in == null) return Build.NONE;
            Properties properties = new Properties();
            properties.load(in);
            String id = properties.getProperty("buildId", "").trim();
            int hours = Integer.parseInt(properties.getProperty("killSwitchHours", "0").trim());
            return id.isEmpty() || id.contains("$") ? Build.NONE : new Build(id, Math.max(0, hours));
        } catch (IOException | NumberFormatException error) {
            return Build.NONE;
        }
    }

    public static Path defaultFile() {
        String dir = System.getProperty("donut.killswitch.dir");
        Path base = dir != null && !dir.isBlank() ? Path.of(dir) : Path.of(System.getProperty("user.home"), ".donut-client");
        return base.resolve("activations.properties");
    }

    /** Elapsed time counts from the first run to the latest time ever seen, so a clock set backwards does not help. */
    public static boolean expired(long first, long latest, long now, int hours) {
        return hours > 0 && Math.max(latest, now) - first >= hours * HOUR;
    }

    public static long remaining(long first, long latest, long now, int hours) {
        return hours <= 0 ? Long.MAX_VALUE : Math.max(0, first + hours * HOUR - Math.max(latest, now));
    }

    /** "2d 5h", "5h 12m", "12m", for the click GUI footer. */
    public static String describe(long millis) {
        long minutes = Math.max(0, millis) / 60_000L, hours = minutes / 60, days = hours / 24;
        if (days > 0) return days + "d " + hours % 24 + "h";
        if (hours > 0) return hours + "h " + minutes % 60 + "m";
        return Math.max(1, minutes) + "m";
    }

    /** Checks the clock; once expired it stays expired for this session. */
    public boolean expired(long now) {
        if (build.hours() <= 0 || expired) return expired;
        latest = Math.max(latest, now);
        if (latest - savedLatest >= SAVE_EVERY) persist();
        expired = expired(first, latest, now, build.hours());
        if (expired) persist();
        return expired;
    }

    public boolean active() { return build.hours() > 0; }
    public Build build() { return build; }
    public long remaining(long now) { return remaining(first, latest, now, build.hours()); }

    private void persist() {
        try {
            Properties state = read(file);
            state.setProperty(build.id(), first + "," + latest);
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                state.store(out, "Donut Client test-build clocks: build id = first run, latest seen (ms)");
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            savedLatest = latest;
        } catch (IOException ignored) {
            // Unwritable home folder: the clock still runs for this session.
        }
    }

    private static Properties read(Path file) {
        Properties state = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { state.load(in); } catch (IOException | IllegalArgumentException ignored) { }
        }
        return state;
    }

    static long[] parse(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length != 2) return null;
        try {
            long first = Long.parseLong(parts[0].trim()), latest = Long.parseLong(parts[1].trim());
            return first > 0 && latest >= first ? new long[] {first, latest} : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
