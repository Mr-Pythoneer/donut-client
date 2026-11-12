package dev.donut.client.search;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.SearchSettings;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Indexes the user's chosen blocks in chunks the client has already received, exactly like Storage ESP: a palette
 * check skips sections that cannot contain a target, a few chunks are scanned per tick, and applied chunk/block
 * packets queue rescans. Nothing is requested from the server; on anti-xray servers hidden blocks may be disguised.
 */
public final class SearchScanner {
    public static final int MAX_CHUNKS_PER_TICK = 8;
    public static final long BUDGET_NANOS = 2_000_000L;
    public static final int MAX_TRACERS = 256;
    private static final int PUBLISH_TICKS = 5;
    private static SearchScanner instance;

    private final ClientConfig config;
    private final Map<Long, List<Found>> index = new HashMap<>();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private Map<Block, String> targets = Map.of();
    private ClientLevel lastLevel;
    private Snapshot snapshot = Snapshot.EMPTY;
    private boolean dirty;
    private int publishAge, publishKey, listKey;
    private long scannedChunks;

    public record Found(BlockPos pos, Block block) { }

    /** One highlight: a single block, or a merged box of neighbouring blocks of the same type. */
    public record Target(AABB bounds, String id, int blocks, double distanceSq, BlockPos nearest) { }

    public record Snapshot(List<Target> targets, Map<String, Integer> counts, int total, int shownBlocks,
                           boolean limited, int pendingChunks, double range) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), 0, 0, false, 0, 0);
        public Target nearest() { return targets.isEmpty() ? null : targets.getFirst(); }
        public int count(String id) { return counts.getOrDefault(id, 0); }
    }

    public SearchScanner(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static SearchScanner get() { return instance; }
    public Snapshot snapshot() { return snapshot; }
    public int pendingChunks() { return queue.size(); }
    public int indexedChunks() { return index.size(); }
    public long scannedChunks() { return scannedChunks; }
    public SearchSettings settings() { return config.search; }

    /** Applied packets are only observed, after vanilla has updated the client world. */
    public static void packetApplied(Packet<?> packet) {
        SearchScanner scanner = instance;
        if (scanner == null || !scanner.settings().enabled || scanner.lastLevel == null) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) scanner.enqueue(ChunkPos.asLong(chunk.getX(), chunk.getZ()));
        else if (packet instanceof ClientboundForgetLevelChunkPacket forget) scanner.forget(forget.pos().toLong());
        else if (packet instanceof ClientboundBlockUpdatePacket block) scanner.blockChanged(block.getPos(), block.getBlockState());
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket section) section.runUpdates(scanner::blockChanged);
    }

    public void tick(Minecraft client) {
        if (!settings().enabled || client.level == null || client.player == null) {
            if (lastLevel != null || !index.isEmpty() || snapshot != Snapshot.EMPTY) clear();
            return;
        }
        int list = settings().listKey();
        if (client.level != lastLevel || list != listKey) {
            clear();
            lastLevel = client.level;
            listKey = list;
            targets = resolve(settings());
            queueLoadedChunks(client);
            dirty = true;
        }
        int key = settings().publishKey();
        if (key != publishKey) { publishKey = key; dirty = true; }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int processed = 0;
        while (!queue.isEmpty() && processed < MAX_CHUNKS_PER_TICK && System.nanoTime() < deadline) {
            long chunk = queue.dequeueLong();
            queued.remove(chunk);
            scanChunk(client.level, chunk);
            processed++;
        }
        if (dirty || ++publishAge >= PUBLISH_TICKS) publish(client);
    }

    private static Map<Block, String> resolve(SearchSettings settings) {
        Map<Block, String> resolved = new IdentityHashMap<>();
        if (settings.blocks != null) for (SearchSettings.Entry entry : settings.blocks) {
            Block block = SearchBlocks.block(entry.id);
            if (block != null) resolved.putIfAbsent(block, entry.id);
        }
        return resolved;
    }

    /** Clears every cache: used on disable, world change, list change and disconnect. */
    public void clear() {
        index.clear();
        queue.clear();
        queued.clear();
        snapshot = Snapshot.EMPTY;
        targets = Map.of();
        lastLevel = null;
        dirty = false;
        publishAge = 0;
        publishKey = 0;
        listKey = 0;
        scannedChunks = 0;
    }

    public void disconnect() { clear(); }

    /** Forces every loaded chunk to be scanned again. */
    public void rescan() {
        if (lastLevel == null) return;
        index.clear();
        queueLoadedChunks(Minecraft.getInstance());
        dirty = true;
    }

    private void queueLoadedChunks(Minecraft client) {
        if (client.level == null || client.player == null || targets.isEmpty()) return;
        int radius = client.options.renderDistance().get() + 1;
        ChunkPos center = client.player.chunkPosition();
        for (int ring = 0; ring <= radius; ring++)
            for (int x = -ring; x <= ring; x++)
                for (int z = -ring; z <= ring; z++)
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring) {
                        int cx = center.x + x, cz = center.z + z;
                        if (loadedChunk(client.level, cx, cz) != null) enqueue(ChunkPos.asLong(cx, cz));
                    }
    }

    private static LevelChunk loadedChunk(ClientLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
    }

    private void enqueue(long chunk) {
        if (!targets.isEmpty() && queued.add(chunk)) queue.enqueue(chunk);
    }

    private void forget(long chunk) {
        if (index.remove(chunk) != null) dirty = true;
    }

    private void blockChanged(BlockPos pos, BlockState state) {
        long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (targets.containsKey(state.getBlock()) || indexedAt(chunk, pos)) enqueue(chunk);
    }

    private boolean indexedAt(long chunk, BlockPos pos) {
        List<Found> found = index.get(chunk);
        if (found == null) return false;
        for (Found entry : found) if (entry.pos().equals(pos)) return true;
        return false;
    }

    private void scanChunk(ClientLevel level, long key) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        LevelChunk chunk = loadedChunk(level, cx, cz);
        scannedChunks++;
        if (chunk == null) { if (index.remove(key) != null) dirty = true; return; }
        Map<Block, String> wanted = targets;
        List<Found> found = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            // The palette check skips sections that cannot contain any searched block.
            if (section.hasOnlyAir() || !section.maybeHas(state -> wanted.containsKey(state.getBlock()))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (wanted.containsKey(block)) found.add(new Found(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z), block));
                    }
        }
        List<Found> previous = found.isEmpty() ? index.remove(key) : index.put(key, List.copyOf(found));
        if (previous != null || !found.isEmpty()) dirty = true;
    }

    private record Near(Found found, double distanceSq) { }

    private void publish(Minecraft client) {
        dirty = false;
        publishAge = 0;
        ClientLevel level = client.level;
        SearchSettings settings = settings();
        Vec3 origin = client.gameRenderer.getMainCamera().isInitialized()
                ? client.gameRenderer.getMainCamera().position() : client.player.getEyePosition();
        double rangeSq = (double) settings.range * settings.range;
        int originChunkX = SectionPos.posToSectionCoord(origin.x), originChunkZ = SectionPos.posToSectionCoord(origin.z);
        int chunkRange = (int) Math.ceil(settings.range / 16.0) + 1;
        List<Near> all = new ArrayList<>();
        for (var entry : index.entrySet()) {
            int cx = ChunkPos.getX(entry.getKey()), cz = ChunkPos.getZ(entry.getKey());
            if (Math.abs(cx - originChunkX) > chunkRange || Math.abs(cz - originChunkZ) > chunkRange) continue;
            for (Found found : entry.getValue()) {
                double distanceSq = Vec3.atCenterOf(found.pos()).distanceToSqr(origin);
                if (distanceSq > rangeSq) continue;
                if (level.getBlockState(found.pos()).getBlock() != found.block()) continue; // Changed; a rescan is queued.
                all.add(new Near(found, distanceSq));
            }
        }
        all.sort(Comparator.comparingDouble(Near::distanceSq));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Near near : all) counts.merge(targets.getOrDefault(near.found().block(), "?"), 1, Integer::sum);
        List<Near> kept = all.subList(0, Math.min(all.size(), settings.maxResults));
        snapshot = new Snapshot(targets(kept, settings.mergeAdjacent, origin), Collections.unmodifiableMap(counts),
                all.size(), kept.size(), kept.size() < all.size(), queue.size(), settings.range);
    }

    private List<Target> targets(List<Near> kept, boolean merge, Vec3 origin) {
        List<Target> out = new ArrayList<>();
        if (!merge) {
            for (Near near : kept) out.add(new Target(new AABB(near.found().pos()), targets.getOrDefault(near.found().block(), "?"),
                    1, near.distanceSq(), near.found().pos()));
            return out;
        }
        Map<Block, List<BlockPos>> byBlock = new IdentityHashMap<>();
        for (Near near : kept) byBlock.computeIfAbsent(near.found().block(), block -> new ArrayList<>()).add(near.found().pos());
        for (var entry : byBlock.entrySet()) {
            String id = targets.getOrDefault(entry.getKey(), "?");
            for (BoxMerger.Box box : BoxMerger.merge(entry.getValue())) {
                AABB bounds = box.bounds();
                BlockPos nearest = BlockPos.containing(Math.clamp(origin.x, box.minX(), box.maxX() - 1e-4),
                        Math.clamp(origin.y, box.minY(), box.maxY() - 1e-4), Math.clamp(origin.z, box.minZ(), box.maxZ() - 1e-4));
                out.add(new Target(bounds, id, box.volume(), Vec3.atCenterOf(nearest).distanceToSqr(origin), nearest));
            }
        }
        out.sort(Comparator.comparingDouble(Target::distanceSq));
        return out;
    }

    /** The farthest distance the client can currently have loaded: the effective render/view distance. */
    public static int loadedRadiusBlocks(Minecraft client) {
        return client.options.getEffectiveRenderDistance() * 16;
    }
}
