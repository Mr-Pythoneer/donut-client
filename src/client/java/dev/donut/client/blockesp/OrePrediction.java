/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.blockesp;

/** A predicted target with its confidence, as evaluated against the blocks the client has received. */
public record OrePrediction(OreMarker marker, OreConfidence.State state, int confidence) {
    public String label() { return OreConfidence.label(state, confidence); }
}
