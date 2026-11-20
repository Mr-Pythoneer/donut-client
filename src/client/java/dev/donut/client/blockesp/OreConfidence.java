/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.blockesp;

import java.util.Locale;

/**
 * Pure BlockESP confidence rules. Inputs are only what the client already received: whether the chunk is loaded,
 * which block is at the predicted position, and whether a face of it touches air.
 *
 * <p>Model, in order:
 * <ul>
 *   <li><b>Confirmed</b> (100%): the received block is the predicted ore.</li>
 *   <li><b>Gone</b> (hidden): air or fluid was received there, so the ore was mined or never existed.</li>
 *   <li><b>Contradicted</b> (0%): the position is exposed to air but shows another block. Anti-Xray only disguises
 *       blocks that touch no air, so an exposed mismatch is real.</li>
 *   <li><b>Likely</b>: another solid block that touches no air. Anti-Xray may show stone, deepslate, planks or random
 *       ores there, so this neither confirms nor refutes the prediction. {@value #LIKELY_CAP}% of the seed match score.</li>
 *   <li><b>Unverified</b>: the chunk has not been received. {@value #UNVERIFIED_CAP}% of the seed match score,
 *       because it may also have been mined.</li>
 * </ul>
 * The seed match score is the Laplace-smoothed share of exposed predictions that really are that ore:
 * {@code (matches + 1) / (samples + 2)}, so it starts at 50% with no evidence.
 */
public final class OreConfidence {
    public enum State {
        CONFIRMED("Confirmed"), LIKELY("Likely"), UNVERIFIED("Unverified"), CONTRADICTED("Contradicted"), GONE("Gone");
        public final String label;
        State(String label) { this.label = label; }
    }

    /** What the client received at the predicted position (EMPTY = air, cave air or a fluid). */
    public enum Observed { NOT_LOADED, EXPECTED_ORE, EMPTY, OTHER_BLOCK }

    public enum Verdict { NOT_ENOUGH_DATA, MATCH, PARTIAL, MISMATCH }

    public static final int LIKELY_CAP = 95, UNVERIFIED_CAP = 80;
    /** Fewer exposed checks than this and the HUD says "not enough data". */
    public static final int MIN_SAMPLES = 16;
    /** Raw match share at or above which the seed is considered right, and below which it is considered wrong. */
    public static final double MATCH_AT = .85, MISMATCH_BELOW = .5;

    private OreConfidence() { }

    public static State classify(Observed observed, boolean exposed) {
        return switch (observed) {
            case NOT_LOADED -> State.UNVERIFIED;
            case EXPECTED_ORE -> State.CONFIRMED;
            case EMPTY -> State.GONE;
            case OTHER_BLOCK -> exposed ? State.CONTRADICTED : State.LIKELY;
        };
    }

    /** Whether this observation is an unbiased seed check: exposed (so Anti-Xray can't hide it) and not mined. */
    public static boolean isSample(Observed observed, boolean exposed) {
        return exposed && observed != Observed.NOT_LOADED && observed != Observed.EMPTY;
    }

    /** Laplace-smoothed match score in [0, 1]. */
    public static double matchScore(int matches, int samples) {
        int checked = Math.max(0, samples), hits = Math.clamp(matches, 0, checked);
        return (hits + 1.0) / (checked + 2.0);
    }

    /** Confidence percentage 0-100, or -1 for {@link State#GONE} (not shown). */
    public static int percent(State state, double matchScore) {
        double score = Double.isFinite(matchScore) ? Math.clamp(matchScore, 0, 1) : .5;
        return switch (state) {
            case CONFIRMED -> 100;
            case LIKELY -> (int) Math.round(LIKELY_CAP * score);
            case UNVERIFIED -> (int) Math.round(UNVERIFIED_CAP * score);
            case CONTRADICTED -> 0;
            case GONE -> -1;
        };
    }

    public static Verdict verdict(int matches, int samples) {
        if (samples < MIN_SAMPLES) return Verdict.NOT_ENOUGH_DATA;
        double raw = Math.clamp(matches, 0, samples) / (double) samples;
        return raw >= MATCH_AT ? Verdict.MATCH : raw < MISMATCH_BELOW ? Verdict.MISMATCH : Verdict.PARTIAL;
    }

    /** e.g. "97% (63 of 65 visible checked)" or "not enough data (3 of 16 visible checks)". */
    public static String matchText(int matches, int samples) {
        if (samples < MIN_SAMPLES) return "not enough data (" + Math.max(0, samples) + " of " + MIN_SAMPLES + " visible checks)";
        long percent = Math.round(100.0 * Math.clamp(matches, 0, samples) / samples);
        return percent + "% (" + Math.clamp(matches, 0, samples) + " of " + samples + " visible checked)";
    }

    public static String verdictText(Verdict verdict) {
        return switch (verdict) {
            case NOT_ENOUGH_DATA -> "Explore caves or enable common ores for more checks";
            case MATCH -> "Seed and version match this world";
            case PARTIAL -> "Partial match: older chunks, edits or another version";
            case MISMATCH -> "Seed or version probably doesn't match this world";
        };
    }

    /** Short HUD/label form: "Confirmed", "Likely 91%", "Unverified 76%", "Contradicted 0%". */
    public static String label(State state, int percent) {
        return state == State.CONFIRMED ? state.label : String.format(Locale.ROOT, "%s %d%%", state.label, Math.max(0, percent));
    }

    /** Marker opacity multiplier: confirmed solid, uncertain markers fainter but never invisible. */
    public static double fade(int percent) {
        return .25 + .75 * Math.clamp(percent, 0, 100) / 100.0;
    }
}
