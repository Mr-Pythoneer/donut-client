/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.blockesp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * One chunk's predicted targets, packed (8 bytes position + 1 byte target each) to keep 512 cached chunks small.
 * {@code mask} records which targets were scanned, so a wider selection knows the chunk must be scanned again.
 */
public record ChunkScan(int mask, long[] positions, byte[] targets, int dropped) {
    /** Per-chunk cap. A FULL chunk with every ore and both variants selected usually holds a few hundred. */
    public static final int MAX_PER_CHUNK = 1024;

    public int size() { return positions.length; }

    /** True when this scan looked for every target in {@code wanted}. */
    public boolean covers(int wanted) { return (mask & wanted) == wanted; }

    public OreMarker marker(int index) { return new OreMarker(BlockPos.of(positions[index]), targets[index]); }

    public List<OreMarker> markers() {
        var result = new ArrayList<OreMarker>(size());
        for (int i = 0; i < size(); i++) result.add(marker(i));
        return List.copyOf(result);
    }
}
