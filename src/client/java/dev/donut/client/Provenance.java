/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * Those licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client;

/**
 * Authorship watermark for the Donut Client modules written by Mr_Pythoneer (BlockESP, Storage ESP, Auto Miner,
 * Structure Finder, Player ESP, Search, Auto Eat, Auto Reconnect, NewChunks, Stash Finder, HUD widgets, Freelook,
 * Waypoints, Zoom, Coordinates, Durability Alert, the multiplayer guard and the test-build kill switch, among others).
 *
 * <p>The {@link #SIGNATURE} is a stable, unique token baked into every build and into the resource
 * {@code donut-build.properties}. It does not change how the mod runs; it exists so that if this code or a renamed
 * copy of it turns up elsewhere, the token is evidence of where it came from. It is not a restriction on anyone's
 * rights under the project's licenses — only a record of authorship, which those licenses require be preserved.
 */
public final class Provenance {
    public static final String AUTHOR = "Mr_Pythoneer";
    public static final String PROJECT = "Donut Client";
    public static final String REPOSITORY = "github.com/Mr-Pythoneer/donut-client";
    /** Unique authorship token; its presence in another project is evidence that this code was copied. */
    public static final String SIGNATURE = "DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E";

    private Provenance() { }

    /** One line for the client log at start-up, so the authorship mark is present in the running build too. */
    public static String banner() {
        return PROJECT + " modules by " + AUTHOR + " · " + REPOSITORY + " · " + SIGNATURE;
    }
}
