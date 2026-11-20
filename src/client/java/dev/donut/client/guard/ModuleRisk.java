/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.guard;

/**
 * What the 2026-11-12 anti-cheat test found for each module (docs/anticheat-results.md): GrimAC, Intave, Spartan,
 * OmniAnticheat, BS-AntiCheat, DonutAntiCheat and a freecam detector, plus Paper's anti-xray, on Purpur 1.21.11.
 * A record of measurements, not a promise: other servers and anti-cheat versions can behave differently.
 */
public final class ModuleRisk {
    public enum Level {
        /** Ran during the test; no anti-cheat recorded anything for it. */
        NOT_FLAGGED("Not flagged", 0xFF7FCB8E),
        /** Not flagged, but it works badly on such servers. */
        LIMITED("Limited", 0xFFB9C1D0),
        /** Recorded by at least one anti-cheat; nothing punished it in a short test. */
        FLAGGED("Flagged", 0xFFF0B45A),
        /** Kicked or banned within seconds. */
        BANS("Bans fast", 0xFFF0705A);

        public final String label;
        public final int color;

        Level(String label, int color) { this.label = label; this.color = color; }
    }

    public record Risk(Level level, String tag, String detail) { }

    public static final String TESTED = "Tested 2026-11-12 against 7 anti-cheats and anti-xray.";
    private static final Risk QUIET = new Risk(Level.NOT_FLAGGED, "Not flagged",
            "No anti-cheat recorded anything while it ran. It only reads what the server already sends.");

    private ModuleRisk() { }

    public static Risk of(int module) {
        return switch (module) {
            case BlatantGuard.FLY -> new Risk(Level.BANS, "Bans fast",
                    "Omni kicked in about 2 s, Intave banned in 4 s, and Grim and Spartan flagged it. Don't use it on a protected server.");
            case BlatantGuard.AUTO_MINER -> new Risk(Level.FLAGGED, "Flagged: Spartan",
                    "Spartan flagged breaking blocks through other blocks and bot-like walking, and logged every ore. Grim, Intave and Omni stayed quiet.");
            case BlatantGuard.AUTO_BUILDER -> new Risk(Level.FLAGGED, "Flagged: Grim, Omni",
                    "Grim flagged its sharp head turns (AimModulo360) and Omni a burst of movement packets.");
            case BlatantGuard.AUTO_EAT -> new Risk(Level.FLAGGED, "Flagged: Grim",
                    "Grim flagged the packet order when it eats (Post), below Grim's punishment level.");
            case 23 -> new Risk(Level.LIMITED, "Fooled by anti-xray",
                    "Not flagged, but on an anti-xray server nearly every buried block it shows is fake: 0 of the 40 nearest \"diamonds\" were real.");
            case 25 -> new Risk(Level.LIMITED, "Unreliable on Paper",
                    "Not flagged, but it marked nothing as new on a Paper-based server.");
            case 13 -> new Risk(Level.NOT_FLAGGED, "Not flagged",
                    "No anti-cheat recorded anything, and anti-xray didn't fool it: 40 of 40 predicted diamonds were real. Mining straight to them can still show in ore-ratio logs.");
            case 2 -> new Risk(Level.NOT_FLAGGED, "Not flagged",
                    "No anti-cheat recorded anything; the freecam detector's score stayed 0. Your body doesn't move.");
            case 14 -> new Risk(Level.NOT_FLAGGED, "Not flagged",
                    "Its /msg tab-complete requests weren't flagged by any anti-cheat.");
            case 27 -> new Risk(Level.NOT_FLAGGED, "Not flagged",
                    "Not flagged, and it rejoined a real server by itself after a kick.");
            default -> QUIET;
        };
    }

    /** Tiles only carry a tag when there is something to warn about; the inspector always shows the result. */
    public static boolean showOnTile(int module) { return of(module).level() != Level.NOT_FLAGGED; }
}
