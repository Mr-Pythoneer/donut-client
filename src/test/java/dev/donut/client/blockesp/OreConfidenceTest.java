package dev.donut.client.blockesp;

import dev.donut.client.blockesp.OreConfidence.Observed;
import dev.donut.client.blockesp.OreConfidence.State;
import dev.donut.client.blockesp.OreConfidence.Verdict;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OreConfidenceTest {
    @Test void observationsClassifyIntoTheDocumentedStates() {
        for (boolean exposed : new boolean[] {false, true}) {
            assertEquals(State.UNVERIFIED, OreConfidence.classify(Observed.NOT_LOADED, exposed));
            assertEquals(State.CONFIRMED, OreConfidence.classify(Observed.EXPECTED_ORE, exposed));
            assertEquals(State.GONE, OreConfidence.classify(Observed.EMPTY, exposed));
        }
        // Anti-Xray can only disguise blocks that touch no air.
        assertEquals(State.LIKELY, OreConfidence.classify(Observed.OTHER_BLOCK, false));
        assertEquals(State.CONTRADICTED, OreConfidence.classify(Observed.OTHER_BLOCK, true));
    }

    @Test void onlyExposedReceivedSolidBlocksAreSeedChecks() {
        assertTrue(OreConfidence.isSample(Observed.EXPECTED_ORE, true));
        assertTrue(OreConfidence.isSample(Observed.OTHER_BLOCK, true));
        assertFalse(OreConfidence.isSample(Observed.EXPECTED_ORE, false), "Hidden confirmations are biased by Anti-Xray");
        assertFalse(OreConfidence.isSample(Observed.OTHER_BLOCK, false));
        assertFalse(OreConfidence.isSample(Observed.EMPTY, true), "Mined ores say nothing about the seed");
        assertFalse(OreConfidence.isSample(Observed.NOT_LOADED, true));
    }

    @Test void matchScoreIsLaplaceSmoothedAndClamped() {
        assertEquals(.5, OreConfidence.matchScore(0, 0), 1e-9);
        assertEquals(64 / 67.0, OreConfidence.matchScore(63, 65), 1e-9);
        assertEquals(1 / 67.0, OreConfidence.matchScore(0, 65), 1e-9);
        assertEquals(.5, OreConfidence.matchScore(-4, -9), 1e-9);
        assertEquals(OreConfidence.matchScore(10, 10), OreConfidence.matchScore(99, 10), 1e-9, "Matches can't exceed samples");
    }

    @Test void percentagesScaleWithTheMatchScoreUnderTheirCaps() {
        assertEquals(100, OreConfidence.percent(State.CONFIRMED, 0));
        assertEquals(0, OreConfidence.percent(State.CONTRADICTED, 1));
        assertEquals(-1, OreConfidence.percent(State.GONE, 1));
        assertEquals(95, OreConfidence.percent(State.LIKELY, 1));
        assertEquals(80, OreConfidence.percent(State.UNVERIFIED, 1));
        assertEquals(48, OreConfidence.percent(State.LIKELY, .5));
        assertEquals(40, OreConfidence.percent(State.UNVERIFIED, .5));
        assertEquals(0, OreConfidence.percent(State.LIKELY, -3));
        assertEquals(95, OreConfidence.percent(State.LIKELY, 7));
        assertEquals(48, OreConfidence.percent(State.LIKELY, Double.NaN), "NaN falls back to the neutral prior");
        double good = OreConfidence.matchScore(63, 65);
        assertTrue(OreConfidence.percent(State.LIKELY, good) > OreConfidence.percent(State.UNVERIFIED, good));
        assertTrue(OreConfidence.percent(State.UNVERIFIED, good) > OreConfidence.percent(State.UNVERIFIED, OreConfidence.matchScore(5, 65)));
    }

    @Test void verdictNeedsEnoughSamplesThenUsesTheRawShare() {
        assertEquals(Verdict.NOT_ENOUGH_DATA, OreConfidence.verdict(15, OreConfidence.MIN_SAMPLES - 1));
        assertEquals(Verdict.NOT_ENOUGH_DATA, OreConfidence.verdict(0, 0));
        assertEquals(Verdict.MATCH, OreConfidence.verdict(63, 65));
        assertEquals(Verdict.MATCH, OreConfidence.verdict(85, 100));
        assertEquals(Verdict.PARTIAL, OreConfidence.verdict(84, 100));
        assertEquals(Verdict.PARTIAL, OreConfidence.verdict(50, 100));
        assertEquals(Verdict.MISMATCH, OreConfidence.verdict(49, 100));
        assertEquals(Verdict.MISMATCH, OreConfidence.verdict(0, 40));
    }

    @Test void textsMatchTheHudWording() {
        assertEquals("97% (63 of 65 visible checked)", OreConfidence.matchText(63, 65));
        assertEquals("not enough data (3 of 16 visible checks)", OreConfidence.matchText(3, 3));
        assertEquals("Confirmed", OreConfidence.label(State.CONFIRMED, 100));
        assertEquals("Likely 91%", OreConfidence.label(State.LIKELY, 91));
        assertEquals("Unverified 0%", OreConfidence.label(State.UNVERIFIED, -5));
        assertTrue(OreConfidence.verdictText(Verdict.MISMATCH).contains("doesn't match"));
    }

    @Test void fadeKeepsConfirmedSolidAndUncertainVisible() {
        assertEquals(1, OreConfidence.fade(100), 1e-9);
        assertEquals(.25, OreConfidence.fade(0), 1e-9);
        assertEquals(.25, OreConfidence.fade(-50), 1e-9);
        assertEquals(1, OreConfidence.fade(400), 1e-9);
        assertTrue(OreConfidence.fade(80) > OreConfidence.fade(40));
    }
}
