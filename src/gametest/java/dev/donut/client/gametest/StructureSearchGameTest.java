package dev.donut.client.gametest;

import dev.donut.client.blockesp.VanillaOreGenerator;
import dev.donut.client.structurefinder.StructureSearch;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proves the seed structure search against the real generator: nearest-of-each finds the structures every seed has
 * (village, stronghold, fortress, end city), and an area scan returns several hits near the origin. The generation
 * world runs on its own thread, like production, so this needs no loaded world of its own.
 */
public final class StructureSearchGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("Donut/StructureSearchTest");
    private static final long SEED = 6608149111735331168L;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path scratch = FabricLoader.getInstance().getGameDir().resolve(".cache/structure-search-test");
        AtomicReference<StructureSearch.Results> nearest = new AtomicReference<>();
        AtomicReference<StructureSearch.Results> area = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StructureSearch.Progress quiet = new StructureSearch.Progress() {
            public void status(String message) { }
            public boolean cancelled() { return false; }
        };
        Thread worker = new Thread(() -> {
            try (VanillaOreGenerator generator = new VanillaOreGenerator(SEED, scratch)) {
                nearest.set(generator.findStructures(new StructureSearch.Query(BlockPos.ZERO, 0, true, null, 500), quiet));
                area.set(generator.findStructures(new StructureSearch.Query(BlockPos.ZERO, 1200, false, null, 1000), quiet));
            } catch (Throwable error) { failure.set(error); }
        }, "structure-search-test");
        worker.setDaemon(true);
        // The generation world reuses the connected world's bound registry tags, so keep one loaded meanwhile.
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            worker.start();
            for (int i = 0; i < 1800 && worker.isAlive(); i++) context.waitTick();
        }
        try { worker.join(10_000); } catch (InterruptedException error) { throw new AssertionError(error); }
        require(failure.get() == null, "Search threw: " + failure.get());
        require(nearest.get() != null && area.get() != null, "Search did not finish in time");
        require(nearest.get().error() == null, "Nearest error: " + nearest.get().error());
        require(area.get().error() == null, "Area error: " + area.get().error());

        Set<String> near = new HashSet<>();
        for (var hit : nearest.get().hits()) near.add(hit.groupId());
        LOG.info("Nearest-of-each for seed {}: {}", SEED, nearest.get().hits());
        for (String required : new String[] {"village", "stronghold", "fortress", "end_city"})
            require(near.contains(required), "Nearest-of-each is missing " + required + "; found " + near);
        // Nearest mode returns at most one per group per dimension (ruined portals exist in two).
        Set<String> perGroupDim = new HashSet<>();
        for (var hit : nearest.get().hits())
            require(perGroupDim.add(hit.groupId() + "/" + hit.dimension().identifier()), "Duplicate nearest hit for " + hit.groupId());

        LOG.info("Area scan within 1200 of origin: {} hits, truncated={}", area.get().hits().size(), area.get().truncated());
        require(area.get().hits().size() >= 3, "Area scan found too few structures near origin: " + area.get().hits().size());
        // Every area hit is inside the radius.
        for (var hit : area.get().hits()) {
            long dx = hit.pos().getX(), dz = hit.pos().getZ();
            require(dx * dx + dz * dz <= 1200L * 1200L + 512 * 512, "Area hit outside the radius: " + hit);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
