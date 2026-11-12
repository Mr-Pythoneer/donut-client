package dev.donut.client.blockesp;

/** A predicted target with its confidence, as evaluated against the blocks the client has received. */
public record OrePrediction(OreMarker marker, OreConfidence.State state, int confidence) {
    public String label() { return OreConfidence.label(state, confidence); }
}
