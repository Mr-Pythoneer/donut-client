package dev.donut.client.storageesp;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.StorageEspSettings;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Indexes storage blocks in chunks the client has already received. Chunks are rescanned incrementally from
 * applied chunk/block packets, with a bounded amount of work per tick; nothing is ever requested from the server.
 */
public final class StorageEspScanner {
    /** At most this many chunks and this much time per client tick. */
    public static final int MAX_CHUNKS_PER_TICK = 8;
    public static final long BUDGET_NANOS = 2_000_000L;
    private static final int PUBLISH_TICKS = 5;
    private static StorageEspScanner instance;

    private final ClientConfig config;
    private final Map<Long, List<Found>> index = new HashMap<>();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private ClientLevel lastLevel;
    private Snapshot snapshot = Snapshot.EMPTY;
    private boolean dirty;
    private int publishAge;
    private int publishKey;
    private long scannedChunks;

    public record Found(BlockPos pos, StorageKind kind) { }

    /** One highlighted target. {@code entity} is set for storage vehicles so rendering can follow them live. */
    public record Target(BlockPos pos, StorageKind kind, AABB bounds, boolean doubleChest, double distanceSq, Entity entity) {
        public AABB liveBounds() { return entity != null && !entity.isRemoved() ? entity.getBoundingBox() : bounds; }
    }

    public record Snapshot(List<Target> targets, Map<StorageKind, Integer> counts, int doubleChests, int total,
                           boolean limited, int pendingChunks, double range) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), Map.of(), 0, 0, false, 0, 0);
        public Target nearest() { return targets.isEmpty() ? null : targets.getFirst(); }
        public int count(StorageKind kind) { return counts.getOrDefault(kind, 0); }
    }

    public StorageEspScanner(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static StorageEspScanner get() { return instance; }
    public Snapshot snapshot() { return snapshot; }
    public int pendingChunks() { return queue.size(); }
    public int indexedChunks() { return index.size(); }
    public long scannedChunks() { return scannedChunks; }
    public StorageEspSettings settings() { return config.storageEsp; }

    /** Applied packets are only observed, after vanilla has updated the client world. */
    public static void packetApplied(Packet<?> packet) {
        StorageEspScanner scanner = instance;
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
        if (client.level != lastLevel) {
            clear();
            lastLevel = client.level;
            queueLoadedChunks(client);
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

    /** Clears every cache: used on disable, world change and disconnect. */
    public void clear() {
        index.clear();
        queue.clear();
        queued.clear();
        snapshot = Snapshot.EMPTY;
        lastLevel = null;
        dirty = false;
        publishAge = 0;
        publishKey = 0;
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
        if (client.level == null || client.player == null) return;
        int radius = client.options.renderDistance().get() + 1;
        ChunkPos center = client.player.chunkPosition();
        // Nearest rings first so nearby storage appears before distant chunks.
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
        if (queued.add(chunk)) queue.enqueue(chunk);
    }

    private void forget(long chunk) {
        if (index.remove(chunk) != null) dirty = true;
        // A queued entry may remain; scanChunk skips chunks that are no longer loaded.
    }

    private void blockChanged(BlockPos pos, BlockState state) {
        long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        // Only rescan when storage is involved: placed now, or previously indexed in this chunk.
        if (StorageKind.of(state) != null || indexedAt(chunk, pos)) enqueue(chunk);
        // Chest pairing reads live states when publishing, so a neighbour half's type change shows up then.
        if (StorageKind.of(state) == StorageKind.CHESTS || StorageKind.of(state) == StorageKind.TRAPPED_CHESTS) dirty = true;
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
        List<Found> found = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            // The palette check skips sections that cannot contain any storage block.
            if (section.hasOnlyAir() || !section.maybeHas(state -> StorageKind.of(state) != null)) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        StorageKind kind = StorageKind.of(section.getBlockState(x, y, z));
                        if (kind != null) found.add(new Found(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z), kind));
                    }
        }
        List<Found> previous = found.isEmpty() ? index.remove(key) : index.put(key, List.copyOf(found));
        if (previous != null || !found.isEmpty()) dirty = true;
    }

    private void publish(Minecraft client) {
        dirty = false;
        publishAge = 0;
        ClientLevel level = client.level;
        var settings = settings();
        Vec3 origin = client.gameRenderer.getMainCamera().isInitialized()
                ? client.gameRenderer.getMainCamera().position() : client.player.getEyePosition();
        double range = settings.range, rangeSq = range * range;
        List<Target> all = new ArrayList<>();
        int originChunkX = SectionPos.posToSectionCoord(origin.x), originChunkZ = SectionPos.posToSectionCoord(origin.z);
        int chunkRange = (int) Math.ceil(range / 16) + 1;
        for (var entry : index.entrySet()) {
            int cx = ChunkPos.getX(entry.getKey()), cz = ChunkPos.getZ(entry.getKey());
            if (Math.abs(cx - originChunkX) > chunkRange || Math.abs(cz - originChunkZ) > chunkRange) continue;
            for (Found found : entry.getValue()) {
                if (!settings.kindEnabled(found.kind())) continue;
                BlockPos pos = found.pos();
                double distanceSq = Vec3.atCenterOf(pos).distanceToSqr(origin);
                if (distanceSq > rangeSq) continue;
                BlockState state = level.getBlockState(pos);
                if (StorageKind.of(state) != found.kind()) continue; // Changed since scanning; a rescan is queued.
                AABB bounds = shape(level, pos, state);
                boolean doubleChest = false;
                BlockPos other = StoragePairs.partner(pos, state);
                if (other != null && level.hasChunkAt(other)) {
                    BlockState otherState = level.getBlockState(other);
                    if (StoragePairs.paired(pos, state, other, otherState)) {
                        if (!StoragePairs.reportsPair(pos, other)) continue;
                        bounds = bounds.minmax(shape(level, other, otherState));
                        doubleChest = true;
                        distanceSq = bounds.getCenter().distanceToSqr(origin);
                    }
                }
                all.add(new Target(pos, found.kind(), bounds, doubleChest, distanceSq, null));
            }
        }
        if (settings.kindEnabled(StorageKind.STORAGE_ENTITIES)) {
            for (Entity entity : level.entitiesForRendering()) {
                if (StorageKind.of(entity) == null) continue;
                double distanceSq = entity.getBoundingBox().getCenter().distanceToSqr(origin);
                if (distanceSq <= rangeSq) all.add(new Target(entity.blockPosition(), StorageKind.STORAGE_ENTITIES,
                        entity.getBoundingBox(), false, distanceSq, entity));
            }
        }
        Map<StorageKind, Integer> counts = new EnumMap<>(StorageKind.class);
        int doubles = 0;
        for (Target target : all) {
            counts.merge(target.kind(), 1, Integer::sum);
            if (target.doubleChest()) doubles++;
        }
        List<Target> nearest = nearest(all, settings.maxTargets);
        snapshot = new Snapshot(nearest, Collections.unmodifiableMap(counts), doubles, all.size(),
                nearest.size() < all.size(), queue.size(), range);
    }

    private static AABB shape(ClientLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    /** Sorts by distance and keeps the nearest {@code limit} targets. */
    public static List<Target> nearest(List<Target> targets, int limit) {
        List<Target> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingDouble(Target::distanceSq));
        return List.copyOf(sorted.subList(0, Math.min(Math.max(limit, 0), sorted.size())));
    }

    /** The farthest distance the client can currently have loaded: the effective render/view distance. */
    public static int loadedRadiusBlocks(Minecraft client) {
        return client.options.getEffectiveRenderDistance() * 16;
    }
}
