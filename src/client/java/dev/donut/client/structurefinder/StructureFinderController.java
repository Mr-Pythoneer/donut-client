/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.structurefinder;

import dev.donut.client.blockesp.VanillaOreGenerator;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.StructureFinderSettings;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a seed structure search off the client thread and holds its latest state for the screen to read. One worker
 * owns the generation world. Purely client-side: it never contacts the server (it works even on a multiplayer world,
 * since it only reads the client's registries and the seed you type).
 */
public final class StructureFinderController {
    private static final Logger LOG = LoggerFactory.getLogger("Donut/StructureFinder");
    private static StructureFinderController instance;

    public enum Phase { IDLE, RUNNING, DONE, CANCELLED, ERROR }

    private final Path scratch;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("Donut structure finder").daemon(true).factory());
    private final AtomicLong token = new AtomicLong();

    private volatile Phase phase = Phase.IDLE;
    private volatile String status = "";
    private volatile List<StructureSearch.Hit> results = List.of();
    private volatile boolean truncated;
    private volatile long searchedSeed;

    public StructureFinderController(ClientConfig config) {
        this(FabricLoader.getInstance().getGameDir().resolve(".cache/donut-structures"));
    }

    StructureFinderController(Path scratch) {
        this.scratch = scratch;
        instance = this;
    }

    public static StructureFinderController get() { return instance; }

    public Phase phase() { return phase; }
    public String status() { return status; }
    public boolean running() { return phase == Phase.RUNNING; }
    public List<StructureSearch.Hit> results() { return results; }
    public boolean truncated() { return truncated; }
    public long searchedSeed() { return searchedSeed; }

    /** Starts a search, cancelling any in progress. {@code center} is where "nearest" is measured and the area centres. */
    public void search(long seed, BlockPos center, StructureFinderSettings settings) {
        long id = token.incrementAndGet();
        phase = Phase.RUNNING;
        status = "Loading the seed...";
        results = List.of();
        truncated = false;
        searchedSeed = seed;
        Set<String> groups = activeGroups(settings);
        StructureSearch.Query query = new StructureSearch.Query(center, settings.radius, settings.nearestOnly, groups, settings.maxResults);
        worker.execute(() -> run(id, seed, query));
    }

    public void cancel() {
        token.incrementAndGet();
        if (phase == Phase.RUNNING) { phase = Phase.CANCELLED; status = "Cancelled"; }
    }

    private static Set<String> activeGroups(StructureFinderSettings settings) {
        Set<String> all = new HashSet<>(StructureCatalog.allIds());
        if (settings.hiddenGroups != null) all.removeAll(settings.hiddenGroups);
        return all;
    }

    private void run(long id, long seed, StructureSearch.Query query) {
        if (id != token.get()) return;
        StructureSearch.Progress progress = new StructureSearch.Progress() {
            public void status(String message) { if (id == token.get()) status = message; }
            public boolean cancelled() { return id != token.get(); }
        };
        try (VanillaOreGenerator generator = new VanillaOreGenerator(seed, scratch)) {
            StructureSearch.Results found = generator.findStructures(query, progress);
            if (id != token.get()) return;
            if (found.error() != null) { phase = Phase.ERROR; status = "Could not search: " + found.error(); return; }
            results = found.hits();
            truncated = found.truncated();
            phase = found.cancelled() ? Phase.CANCELLED : Phase.DONE;
            status = summary(found);
        } catch (Throwable error) {
            if (id != token.get()) return;
            LOG.warn("Structure search failed", error);
            phase = Phase.ERROR;
            status = "Could not search: " + error.getMessage();
        }
    }

    private String summary(StructureSearch.Results found) {
        if (found.hits().isEmpty()) return "No structures found" + (found.cancelled() ? " before cancel" : "");
        return "Found " + found.hits().size() + (found.truncated() ? "+ (limit reached)" : "") + " structures";
    }

    public void close() {
        token.incrementAndGet();
        worker.shutdownNow();
    }
}
