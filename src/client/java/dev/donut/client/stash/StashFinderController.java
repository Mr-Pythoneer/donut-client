package dev.donut.client.stash;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.StashFinderSettings;
import dev.donut.client.storageesp.StorageEspScanner;
import dev.donut.client.storageesp.StorageKind;
import dev.donut.client.waypoints.WaypointController;
import dev.donut.client.waypoints.Waypoints;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Counts storage blocks per received chunk with Storage ESP's classification ({@link StorageEspScanner#scanStorage}) and
 * logs chunks that reach the threshold. Runs its own bounded queue, so it works whether Storage ESP is on or off.
 * Reads only received chunks and block updates; nothing is requested from the server.
 */
public final class StashFinderController {
    public static final int MAX_CHUNKS_PER_TICK = 4;
    public static final long BUDGET_NANOS = 1_000_000L;
    /** Block changes wait this long so building or breaking a row of chests is counted once it settles. */
    public static final int SETTLE_TICKS = 10;
    private static final int SAVE_TICKS = 100;
    public static final String DIRECTORY = "donut-stashes";
    private static StashFinderController instance;

    private final ClientConfig config;
    private final Path directory;
    private final StashLog log = new StashLog();
    private final LongArrayFIFOQueue ready = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private final Long2LongOpenHashMap delayed = new Long2LongOpenHashMap();
    private ClientLevel level;
    private long ticks;
    private boolean loaded, dirty;
    private int saveAge;
    private long scannedChunks;
    private StashEntry lastFound;

    public StashFinderController(ClientConfig config) {
        this(config, FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY));
    }

    public StashFinderController(ClientConfig config, Path directory) {
        this.config = config;
        this.directory = directory;
        instance = this;
    }

    public static StashFinderController get() { return instance; }
    public StashFinderSettings settings() { return config.stashFinder; }
    public Path directory() { return directory; }
    public long scannedChunks() { return scannedChunks; }
    public int pendingChunks() { return ready.size() + delayed.size(); }
    public StashEntry lastFound() { return lastFound; }

    public StashLog log() {
        if (!loaded) load();
        return log;
    }

    public static void packetApplied(Packet<?> packet) {
        StashFinderController controller = instance;
        if (controller == null || !controller.settings().enabled || controller.level == null) return;
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) controller.enqueue(ChunkPos.asLong(chunk.getX(), chunk.getZ()));
        else if (packet instanceof ClientboundForgetLevelChunkPacket forget) controller.forget(forget.pos().toLong());
        else if (packet instanceof ClientboundBlockUpdatePacket block) controller.blockChanged(block.getPos().getX(), block.getPos().getZ(), block.getBlockState());
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket section)
            section.runUpdates((pos, state) -> controller.blockChanged(pos.getX(), pos.getZ(), state));
    }

    public void tick(Minecraft client) {
        ticks++;
        if (dirty && ++saveAge >= SAVE_TICKS) save();
        if (!settings().enabled || client.level == null || client.player == null) {
            if (level != null) resetQueues();
            return;
        }
        if (client.level != level) {
            resetQueues();
            level = client.level;
            log();
            queueLoaded(client);
        }
        for (var iterator = delayed.long2LongEntrySet().fastIterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (entry.getLongValue() <= ticks) {
                enqueue(entry.getLongKey());
                iterator.remove();
            }
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int processed = 0;
        while (!ready.isEmpty() && processed < MAX_CHUNKS_PER_TICK && System.nanoTime() < deadline) {
            long chunk = ready.dequeueLong();
            queued.remove(chunk);
            scan(client, chunk);
            processed++;
        }
    }

    private void queueLoaded(Minecraft client) {
        int radius = client.options.renderDistance().get() + 1;
        ChunkPos center = client.player.chunkPosition();
        for (int ring = 0; ring <= radius; ring++)
            for (int x = -ring; x <= ring; x++)
                for (int z = -ring; z <= ring; z++)
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring && chunk(level, center.x + x, center.z + z) != null)
                        enqueue(ChunkPos.asLong(center.x + x, center.z + z));
    }

    /** Scans every loaded chunk again (e.g. after changing the threshold or weights). */
    public void rescan() {
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.player == null) return;
        resetQueues();
        level = client.level;
        queueLoaded(client);
    }

    private static LevelChunk chunk(ClientLevel level, int x, int z) {
        return level == null ? null : level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
    }

    private void enqueue(long chunk) {
        if (queued.add(chunk)) ready.enqueue(chunk);
    }

    private void forget(long chunk) { delayed.remove(chunk); }

    private void blockChanged(int x, int z, net.minecraft.world.level.block.state.BlockState state) {
        if (StorageKind.of(state) == null) return; // Only placements can raise a count; removals never un-log a stash.
        long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        delayed.put(chunk, ticks + SETTLE_TICKS);
    }

    private void scan(Minecraft client, long key) {
        LevelChunk chunk = chunk(level, ChunkPos.getX(key), ChunkPos.getZ(key));
        if (chunk == null) return;
        scannedChunks++;
        List<StorageEspScanner.Found> found = StorageEspScanner.scanStorage(chunk);
        if (found.isEmpty()) return;
        List<StorageKind> kinds = new ArrayList<>(found.size());
        for (var entry : found) kinds.add(entry.kind());
        Map<StorageKind, Integer> counts = StashLog.count(kinds);
        var settings = settings();
        if (StashLog.score(counts, settings::weight) < settings.threshold) return;
        var center = center(found);
        var result = log().observe(server(), level.dimension().identifier().toString(), ChunkPos.getX(key), ChunkPos.getZ(key), counts,
                center.pos().getX(), center.pos().getY(), center.pos().getZ(), System.currentTimeMillis(), settings.threshold, settings::weight);
        if (result == StashLog.Result.IGNORED) return;
        markDirty();
        if (result == StashLog.Result.LOGGED) {
            lastFound = log.find(server(), level.dimension().identifier().toString(), ChunkPos.getX(key), ChunkPos.getZ(key));
            announce(client, lastFound);
        }
    }

    /** The counted storage block nearest the cluster's average position. */
    private static StorageEspScanner.Found center(List<StorageEspScanner.Found> found) {
        double x = 0, y = 0, z = 0;
        int weighted = 0;
        for (var entry : found) {
            if (entry.kind() == StorageKind.STORAGE_ENTITIES) continue;
            x += entry.pos().getX(); y += entry.pos().getY(); z += entry.pos().getZ();
            weighted++;
        }
        x /= Math.max(1, weighted); y /= Math.max(1, weighted); z /= Math.max(1, weighted);
        StorageEspScanner.Found best = found.getFirst();
        double bestDistance = Double.MAX_VALUE;
        for (var entry : found) {
            double d = entry.pos().distToCenterSqr(x + .5, y + .5, z + .5);
            if (d < bestDistance) { bestDistance = d; best = entry; }
        }
        return best;
    }

    private void announce(Minecraft client, StashEntry entry) {
        var settings = settings();
        if (entry == null || client.player == null) return;
        String where = entry.x + ", " + entry.y + ", " + entry.z;
        // Local notices only; nothing is sent to the server.
        if (settings.notify) client.player.displayClientMessage(Component.literal("Stash found: " + entry.score + " storage at " + where), true);
        if (settings.toast) SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("Stash found"), Component.literal(entry.summary() + " at " + where));
    }

    /** Server key shared with waypoints (address, or the singleplayer save folder). */
    public String server() {
        var waypoints = WaypointController.get();
        if (waypoints != null) return waypoints.currentServer();
        var server = Minecraft.getInstance().getCurrentServer();
        return Waypoints.serverKey(server == null ? null : server.ip, null);
    }

    /** Stashes for the current server, newest first. */
    public List<StashEntry> forCurrentServer() { return log().forServer(server()); }

    public boolean remove(StashEntry entry) {
        boolean removed = log().remove(entry);
        if (removed) { markDirty(); save(); }
        return removed;
    }

    private void markDirty() { dirty = true; }

    private void resetQueues() {
        ready.clear();
        queued.clear();
        delayed.clear();
        level = null;
    }

    public void disconnect() {
        resetQueues();
        save();
    }

    // ---- Files ----

    public Path logFile() { return directory.resolve("stashes.json"); }

    private void load() {
        loaded = true;
        Path file = logFile();
        try {
            if (Files.isRegularFile(file)) log.load(StashLog.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException failure) {
            log.clear();
        }
    }

    /** Writes the log atomically; called at most every few seconds, and on disconnect and shutdown. */
    public void save() {
        dirty = false;
        saveAge = 0;
        if (!loaded) return;
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve("stashes.json.tmp");
            Files.writeString(temp, StashLog.toJson(log.all()), StandardCharsets.UTF_8);
            try {
                Files.move(temp, logFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, logFile(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            dirty = true;
        }
    }

    /** Writes this server's stashes as CSV and JSON next to the log; returns the CSV path. */
    public Path export() throws IOException {
        List<StashEntry> entries = forCurrentServer();
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
        String base = "export-" + safe(server()) + "-" + stamp;
        Path csv = directory.resolve(base + ".csv");
        Files.writeString(csv, StashLog.toCsv(entries), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(base + ".json"), StashLog.toJson(entries), StandardCharsets.UTF_8);
        return csv;
    }

    static String safe(String server) {
        String clean = server.replaceAll("[^A-Za-z0-9._-]+", "_");
        return clean.length() > 48 ? clean.substring(0, 48) : clean.isEmpty() ? "unknown" : clean;
    }
}
