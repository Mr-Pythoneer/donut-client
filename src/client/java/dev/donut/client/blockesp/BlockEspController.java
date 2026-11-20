/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.blockesp;

import dev.donut.client.config.BlockEspSettings;
import dev.donut.client.config.ClientConfig;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-thread results and settings; a single worker exclusively owns the generation world.
 * Cached predictions are re-checked against the received world in small batches each tick (see {@link OreConfidence}).
 */
public final class BlockEspController implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Donut/BlockESP");
    private static final int MAX_CACHED_CHUNKS = 512;
    /** Nearest markers drawn and listed. HUD counts still cover the whole scan area. */
    public static final int MAX_MARKERS = 2048;
    /** Tracers go to the nearest markers only. */
    public static final int MAX_TRACERS = 128;
    /** Predictions re-checked against received blocks per tick, so a radius-8 all-ore scan refreshes in about a second. */
    static final int EVALUATIONS_PER_TICK = 8192;
    private static final OreConfidence.State[] STATES = OreConfidence.State.values();
    private static final int SAMPLE = 8, INDEX_BITS = 22;
    private static BlockEspController instance;
    private final ClientConfig config;
    private final Path scratchParent;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("Donut BlockESP generation").daemon(true).factory());
    private final AtomicLong revision = new AtomicLong();
    private final LinkedHashMap<Long, Entry> cache = new LinkedHashMap<>();
    private final ArrayDeque<Long> evaluation = new ArrayDeque<>();
    private CompletableFuture<Result> pending;
    private long pendingRevision;
    private ClientLevel lastLevel;
    private long seed;
    private boolean active, failed, closed;
    private int centerX, centerZ, completed, total;
    private List<OrePrediction> predictions = List.of();
    private List<OreMarker> markers = List.of();
    private final int[] counts = new int[OreMarker.Kind.values().length];
    private final OrePrediction[] nearest = new OrePrediction[OreMarker.Kind.values().length];
    private int matches, samples, dropped;
    private boolean dirty;
    private long publishedKey, publishedBlock = Long.MIN_VALUE;
    private int sincePublish;
    private String status = "Disabled";
    // Accessed exclusively by worker tasks, including cleanup.
    private VanillaOreGenerator generator;
    private long generatorSeed;

    public BlockEspController(ClientConfig config) {
        this(config, FabricLoader.getInstance().getGameDir().resolve(".cache/donut-blockesp"));
        instance = this;
    }

    public BlockEspController(ClientConfig config, Path scratchParent) {
        this.config = config;
        this.scratchParent = scratchParent;
    }

    public static BlockEspController get() { return instance; }
    /** Shown markers without confidence, nearest first (at most {@link #MAX_MARKERS}). */
    public List<OreMarker> snapshot() { return markers; }
    /** Shown markers with confidence, nearest first (at most {@link #MAX_MARKERS}). */
    public List<OrePrediction> predictions() { return predictions; }
    /** Shown markers of this kind in the scan area, including ones beyond the display cap. */
    public int count(OreMarker.Kind kind) { return counts[kind.ordinal()]; }
    public OrePrediction nearest(OreMarker.Kind kind) { return nearest[kind.ordinal()]; }
    /** Exposed predictions that really are that ore, out of all exposed predictions checked. */
    public int seedMatches() { return matches; }
    public int seedSamples() { return samples; }
    public OreConfidence.Verdict seedVerdict() { return OreConfidence.verdict(matches, samples); }
    public String seedMatchText() { return "Seed match: " + OreConfidence.matchText(matches, samples); }
    /** Targets left out of chunk results by the per-chunk cap, over the scan area. */
    public int droppedByCap() { return dropped; }
    public String status() { return status; }
    public int completedChunks() { return completed; }
    public int totalChunks() { return total; }

    /** The shown prediction at this position, or null. */
    public OrePrediction find(BlockPos pos) {
        for (var prediction : predictions) if (prediction.marker().pos().equals(pos)) return prediction;
        return null;
    }

    /** State of any cached prediction at this position (including Gone and capped ones), or null if none was predicted. */
    public OreConfidence.State cachedState(BlockPos pos) {
        Entry entry = cache.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (entry == null) return null;
        long packed = pos.asLong();
        for (int i = 0; i < entry.scan.size(); i++) if (entry.scan.positions()[i] == packed) return STATES[entry.eval[i] & 7];
        return null;
    }

    public void tick(Minecraft client) {
        if (closed) return;
        collect();
        var settings = config.blockEsp;
        if (!settings.enabled || client.level == null || client.player == null) {
            deactivate(settings.enabled ? "Join a world to predict ores" : "Disabled");
            return;
        }
        if (!client.level.dimension().equals(Level.OVERWORLD)) {
            deactivate("Overworld only");
            return;
        }
        if (!settings.anyOre()) {
            deactivate("Select an ore in BlockESP settings");
            return;
        }
        if (!settings.stoneVariants && !settings.deepslateVariants) {
            deactivate("Select stone or deepslate variants");
            return;
        }
        int mask = settings.targetMask();
        long nextSeed;
        try { nextSeed = BlockEspSettings.parseSeed(settings.seed); }
        catch (IllegalArgumentException error) { deactivate(error.getMessage()); return; }
        if (!active || client.level != lastLevel || nextSeed != seed) {
            invalidate();
            lastLevel = client.level;
            seed = nextSeed;
            active = true;
        }
        var center = client.player.chunkPosition();
        centerX = center.x;
        centerZ = center.z;
        total = (settings.radius * 2 + 1) * (settings.radius * 2 + 1);
        evaluate(client.level, settings.radius);
        long key = publishKey(mask, settings.minConfidence, settings.radius, centerX, centerZ);
        long block = client.player.blockPosition().asLong();
        sincePublish++;
        // Distance order only needs refreshing a few times a second while walking.
        if (dirty || key != publishedKey || sincePublish >= 20 || (block != publishedBlock && sincePublish >= 4)) {
            publish(client, mask);
            publishedKey = key;
            publishedBlock = block;
            sincePublish = 0;
            dirty = false;
        }
        if (failed) return;
        if (pending == null) {
            Set<Long> covered = new HashSet<>();
            for (var entry : cache.entrySet()) if (entry.getValue().scan.covers(mask)) covered.add(entry.getKey());
            ChunkPos next = nextChunk(centerX, centerZ, settings.radius, covered);
            if (next != null) {
                long token = revision.get(), requestedSeed = seed;
                pendingRevision = token;
                pending = CompletableFuture.supplyAsync(() -> generate(token, requestedSeed, next, mask), worker);
            }
        }
        status = completed == total ? "Ready - original ore positions" : "Predicting " + completed + " / " + total + " chunks";
    }

    private Result generate(long token, long requestedSeed, ChunkPos chunk, int mask) {
        try {
            if (token != revision.get()) return null;
            if (generator != null && (generatorSeed != requestedSeed || generator.shouldRecycle())) releaseGenerator();
            if (generator == null) {
                generator = new VanillaOreGenerator(requestedSeed, scratchParent);
                generatorSeed = requestedSeed;
            }
            if (token != revision.get()) return null;
            return new Result(token, chunk.toLong(), generator.predict(chunk.x, chunk.z, mask));
        } catch (Exception error) {
            if (token != revision.get()) return null;
            throw new java.util.concurrent.CompletionException(error);
        } finally {
            if (token != revision.get()) releaseGenerator();
        }
    }

    private void collect() {
        if (pending == null || !pending.isDone()) return;
        try {
            Result result = pending.join();
            if (result != null && result.revision == revision.get()) {
                var entry = new Entry(result.scan);
                var level = Minecraft.getInstance().level;
                // Check new results at once so air in a received chunk never flashes an obsolete marker.
                if (level != null) evaluate(level, entry);
                // Re-insert so a rescan with more targets counts as the newest chunk.
                cache.remove(result.chunk);
                cache.put(result.chunk, entry);
                while (cache.size() > MAX_CACHED_CHUNKS) cache.remove(cache.keySet().iterator().next());
                dirty = true;
            }
        } catch (RuntimeException error) {
            if (pendingRevision == revision.get()) {
                failed = true;
                status = "Prediction failed - Rescan to retry (see latest.log)";
                LOGGER.error("BlockESP could not generate vanilla ore predictions", error);
                worker.execute(this::releaseGenerator);
            }
        } finally { pending = null; }
    }

    /** Re-checks cached chunks in the scan area against received blocks, nearest first, within a per-tick budget. */
    private void evaluate(ClientLevel level, int radius) {
        if (evaluation.isEmpty()) {
            var keys = new ArrayList<Long>();
            for (long chunk : cache.keySet()) if (inRadius(chunk, radius)) keys.add(chunk);
            keys.sort((a, b) -> Integer.compare(chunkDistance(a), chunkDistance(b)));
            evaluation.addAll(keys);
        }
        int budget = EVALUATIONS_PER_TICK;
        while (budget > 0 && !evaluation.isEmpty()) {
            Long chunk = evaluation.poll();
            Entry entry = cache.get(chunk);
            if (entry == null || !inRadius(chunk, radius)) continue;
            if (evaluate(level, entry)) dirty = true;
            budget -= Math.max(1, entry.scan.size());
        }
    }

    /** Returns true when any marker's state changed. */
    private static boolean evaluate(ClientLevel level, Entry entry) {
        boolean changed = false;
        var cursor = new BlockPos.MutableBlockPos();
        var neighbour = new BlockPos.MutableBlockPos();
        var scan = entry.scan;
        for (int i = 0; i < scan.size(); i++) {
            long packed = scan.positions()[i];
            int target = scan.targets()[i];
            cursor.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
            var observed = observe(level, cursor, OreMarker.kindOf(target).block(OreMarker.deepslateOf(target)));
            boolean exposed = (observed == OreConfidence.Observed.EXPECTED_ORE || observed == OreConfidence.Observed.OTHER_BLOCK)
                    && exposed(level, cursor, neighbour);
            byte next = (byte) (OreConfidence.classify(observed, exposed).ordinal() | (OreConfidence.isSample(observed, exposed) ? SAMPLE : 0));
            if (entry.eval[i] != next) { entry.eval[i] = next; changed = true; }
        }
        return changed;
    }

    static OreConfidence.Observed observe(ClientLevel level, BlockPos pos, Block expected) {
        if (!level.hasChunkAt(pos)) return OreConfidence.Observed.NOT_LOADED;
        BlockState state = level.getBlockState(pos);
        if (state.is(expected)) return OreConfidence.Observed.EXPECTED_ORE;
        // Air or a fluid at the position: the ore was mined (or never existed). Solid substitutions are kept,
        // because Anti-Xray may disguise an existing ore as stone or deepslate.
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return OreConfidence.Observed.EMPTY;
        return OreConfidence.Observed.OTHER_BLOCK;
    }

    /** A face touches received air. Unloaded neighbours never count, so this stays conservative. */
    private static boolean exposed(ClientLevel level, BlockPos pos, BlockPos.MutableBlockPos neighbour) {
        for (Direction direction : Direction.values()) {
            neighbour.setWithOffset(pos, direction);
            if (level.hasChunkAt(neighbour) && level.getBlockState(neighbour).isAir()) return true;
        }
        return false;
    }

    private void publish(Minecraft client, int mask) {
        var settings = config.blockEsp;
        completed = matches = samples = dropped = 0;
        Arrays.fill(counts, 0);
        Arrays.fill(nearest, null);
        // Pass 1: seed match evidence, and how many predictions are not known to be gone.
        var area = new ArrayList<Entry>();
        int candidates = 0;
        for (var entry : cache.entrySet()) {
            if (!inRadius(entry.getKey(), settings.radius)) continue;
            Entry value = entry.getValue();
            var scan = value.scan;
            if (scan.covers(mask)) completed++;
            dropped += scan.dropped();
            for (int i = 0; i < scan.size(); i++) {
                if ((mask & 1 << scan.targets()[i]) == 0) continue;
                int eval = value.eval[i];
                var state = STATES[eval & 7];
                if ((eval & SAMPLE) != 0) {
                    samples++;
                    if (state == OreConfidence.State.CONFIRMED) matches++;
                }
                if (state != OreConfidence.State.GONE) candidates++;
            }
            area.add(value);
        }
        double score = OreConfidence.matchScore(matches, samples);
        // Pass 2: confidence filter, counts, and nearest-first order packed as (squared distance << INDEX_BITS | index).
        long[] order = new long[candidates], positions = new long[candidates];
        byte[] targets = new byte[candidates], states = new byte[candidates];
        long[] best = new long[counts.length];
        Arrays.fill(best, Long.MAX_VALUE);
        int size = 0;
        var player = client.player.position();
        for (Entry value : area) {
            var scan = value.scan;
            for (int i = 0; i < scan.size(); i++) {
                int target = scan.targets()[i];
                if ((mask & 1 << target) == 0) continue;
                var state = STATES[value.eval[i] & 7];
                if (state == OreConfidence.State.GONE || OreConfidence.percent(state, score) < settings.minConfidence) continue;
                long packed = scan.positions()[i];
                double dx = BlockPos.getX(packed) + .5 - player.x, dy = BlockPos.getY(packed) + .5 - player.y,
                        dz = BlockPos.getZ(packed) + .5 - player.z;
                long key = (long) Math.min(dx * dx + dy * dy + dz * dz, 1L << 40) << INDEX_BITS | size;
                order[size] = key;
                positions[size] = packed;
                targets[size] = (byte) target;
                states[size] = (byte) state.ordinal();
                int kind = target >> 1;
                counts[kind]++;
                if (key < best[kind]) best[kind] = key;
                size++;
            }
        }
        Arrays.sort(order, 0, size);
        int limit = Math.min(size, MAX_MARKERS);
        var found = new ArrayList<OrePrediction>(limit);
        var plain = new ArrayList<OreMarker>(limit);
        for (int i = 0; i < limit; i++) {
            var prediction = prediction(index(order[i]), positions, targets, states, score);
            found.add(prediction);
            plain.add(prediction.marker());
        }
        for (int kind = 0; kind < best.length; kind++)
            if (best[kind] != Long.MAX_VALUE) nearest[kind] = prediction(index(best[kind]), positions, targets, states, score);
        predictions = List.copyOf(found);
        markers = List.copyOf(plain);
    }

    private static int index(long key) { return (int) (key & (1L << INDEX_BITS) - 1); }

    private static OrePrediction prediction(int index, long[] positions, byte[] targets, byte[] states, double score) {
        var state = STATES[states[index]];
        return new OrePrediction(new OreMarker(BlockPos.of(positions[index]), targets[index]), state, OreConfidence.percent(state, score));
    }

    private boolean inRadius(long chunk, int radius) {
        return Math.abs(ChunkPos.getX(chunk) - centerX) <= radius && Math.abs(ChunkPos.getZ(chunk) - centerZ) <= radius;
    }

    private int chunkDistance(long chunk) {
        int dx = ChunkPos.getX(chunk) - centerX, dz = ChunkPos.getZ(chunk) - centerZ;
        return dx * dx + dz * dz;
    }

    private static long publishKey(int mask, int minConfidence, int radius, int centerX, int centerZ) {
        return ((((long) mask * 101 + minConfidence) * 9 + radius) * 31 + centerX) * 31 + centerZ;
    }

    static ChunkPos nextChunk(int centerX, int centerZ, int radius, java.util.Set<Long> cached) {
        ChunkPos nearest = null;
        int distance = Integer.MAX_VALUE;
        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) {
            int cx = centerX + x, cz = centerZ + z;
            if (Math.abs((long) cx) > 1_874_999 || Math.abs((long) cz) > 1_874_999 || cached.contains(ChunkPos.asLong(cx, cz))) continue;
            int squared = x * x + z * z;
            if (squared < distance) { nearest = new ChunkPos(cx, cz); distance = squared; }
        }
        return nearest;
    }

    /** User-requested refresh; unchanged generation inputs produce the same original predictions. */
    public void rescan() { if (!closed) { invalidate(); active = false; status = "Waiting to predict"; } }

    public void disconnect() { if (!closed) deactivate("Join a world to predict ores"); }

    private void deactivate(String message) {
        if (active) invalidate();
        active = false;
        lastLevel = null;
        status = message;
    }

    private void invalidate() {
        revision.incrementAndGet();
        cache.clear();
        evaluation.clear();
        markers = List.of();
        predictions = List.of();
        Arrays.fill(counts, 0);
        Arrays.fill(nearest, null);
        completed = total = matches = samples = dropped = 0;
        failed = false;
        dirty = true;
        worker.execute(this::releaseGenerator);
    }

    private void releaseGenerator() {
        if (generator != null) {
            generator.close();
            generator = null;
        }
    }

    @Override public void close() {
        if (closed) return;
        invalidate();
        closed = true;
        active = false;
        lastLevel = null;
        worker.shutdown();
        if (instance == this) instance = null;
    }

    private record Result(long revision, long chunk, ChunkScan scan) { }

    /** A cached scan plus its latest evaluation: state ordinal in bits 0-2, {@link #SAMPLE} when it is a seed check. */
    private static final class Entry {
        final ChunkScan scan;
        final byte[] eval;

        Entry(ChunkScan scan) {
            this.scan = scan;
            eval = new byte[scan.size()];
            Arrays.fill(eval, (byte) OreConfidence.State.UNVERIFIED.ordinal());
        }
    }
}
