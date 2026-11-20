/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.automine;

import dev.donut.client.config.AutoMinerSettings;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Sweeps the chunks around the player for enabled ores, a few chunks per tick, using only chunk data the client
 * already holds. Nothing is requested from the server; unloaded chunks are skipped.
 */
public final class OreScanner {
    public static final int MAX_CHUNKS_PER_TICK = 4;
    public static final long BUDGET_NANOS = 1_500_000L;
    /** Results are trimmed to the nearest this many positions. */
    public static final int MAX_RESULTS = 2048;

    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final List<BlockPos> found = new ArrayList<>();
    private List<BlockPos> results = List.of();
    private BlockPos center = BlockPos.ZERO;
    private int range;
    private boolean sweeping;
    private long scannedChunks;

    public boolean sweeping() { return sweeping; }
    public int pendingChunks() { return queue.size(); }
    public long scannedChunks() { return scannedChunks; }
    /** The last finished sweep, nearest first from where it started. */
    public List<BlockPos> results() { return results; }

    public void clear() {
        queue.clear();
        found.clear();
        results = List.of();
        sweeping = false;
        scannedChunks = 0;
    }

    /** Starts a new sweep around {@code center}; the previous results stay readable until it finishes. */
    public void begin(ClientLevel level, BlockPos center, int range) {
        queue.clear();
        found.clear();
        this.center = center.immutable();
        this.range = range;
        sweeping = true;
        int radius = (range >> 4) + 1;
        int cx = SectionPos.blockToSectionCoord(center.getX()), cz = SectionPos.blockToSectionCoord(center.getZ());
        // Nearest rings first, like Storage ESP.
        for (int ring = 0; ring <= radius; ring++)
            for (int x = -ring; x <= ring; x++)
                for (int z = -ring; z <= ring; z++)
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring && loaded(level, cx + x, cz + z) != null)
                        queue.enqueue(ChunkPos.asLong(cx + x, cz + z));
    }

    /** Scans a bounded number of chunks. Returns true on the tick a sweep finishes. */
    public boolean tick(ClientLevel level, AutoMinerSettings settings) {
        if (!sweeping) return false;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int processed = 0;
        while (!queue.isEmpty() && processed < MAX_CHUNKS_PER_TICK && System.nanoTime() < deadline) {
            scanChunk(level, queue.dequeueLong(), settings);
            processed++;
        }
        if (!queue.isEmpty()) return false;
        List<BlockPos> sorted = MinerRules.byDistance(found, Vec3.atCenterOf(center));
        results = List.copyOf(sorted.subList(0, Math.min(MAX_RESULTS, sorted.size())));
        found.clear();
        sweeping = false;
        return true;
    }

    private void scanChunk(ClientLevel level, long key, AutoMinerSettings settings) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        LevelChunk chunk = loaded(level, cx, cz);
        scannedChunks++;
        if (chunk == null) return;
        long rangeSq = (long) range * range;
        int minY = center.getY() - range, maxY = center.getY() + range;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            if (baseY + 15 < minY || baseY > maxY) continue;
            // The palette check skips sections that cannot hold any ore.
            if (section.hasOnlyAir() || !section.maybeHas(state -> OreKind.of(state) != null)) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        OreKind kind = OreKind.of(section.getBlockState(x, y, z));
                        if (kind == null || !settings.wants(section.getBlockState(x, y, z))) continue;
                        BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                        if (pos.distSqr(center) <= rangeSq) found.add(pos);
                    }
        }
    }

    private static LevelChunk loaded(ClientLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
    }
}
