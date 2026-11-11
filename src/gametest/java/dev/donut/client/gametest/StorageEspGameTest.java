package dev.donut.client.gametest;

import dev.donut.client.storageesp.StorageEspScanner;
import dev.donut.client.storageesp.StorageKind;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Production scanner, renderer and HUD against received blocks/entities in a disposable integrated world. */
public final class StorageEspGameTest implements FabricClientGameTest {
    private static final BlockPos DOUBLE_LEFT = new BlockPos(-5, 65, 0), DOUBLE_RIGHT = new BlockPos(-6, 65, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        StorageEspScanner scanner = StorageEspScanner.get();
        require(scanner != null, "The production Storage ESP scanner was not initialized");
        var settings = scanner.settings();
        var saved = new Saved(settings.enabled, settings.range, settings.maxTargets, settings.opacity, settings.tracers,
                settings.showHud, new LinkedHashMap<>(settings.kinds));
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            settings.enabled = false;
            settings.range = 128;
            settings.maxTargets = 256;
            settings.opacity = 255;
            settings.tracers = false;
            settings.showHud = true;
            settings.sanitize();
            for (var kind : StorageKind.values()) settings.kinds.put(kind.id, true);
            client.options.guiScale().set(2);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
            try (var world = context.worldBuilder().create()) {
                TestServerContext server = world.getServer();
                fixtures(server);
                context.waitTicks(10);
                world.getClientWorld().waitForChunksRender();
                context.runOnClient(client -> {
                    var left = client.level.getBlockState(DOUBLE_LEFT);
                    var right = client.level.getBlockState(DOUBLE_RIGHT);
                    require(left.getValue(ChestBlock.TYPE) == ChestType.LEFT && right.getValue(ChestBlock.TYPE) == ChestType.RIGHT,
                            "The double-chest fixture did not connect: " + left + " / " + right);
                    require(scanner.snapshot().total() == 0, "A disabled Storage ESP published targets");
                });

                // Every kind is detected, and the double chest is one merged target.
                context.runOnClient(client -> settings.enabled = true);
                context.waitFor(client -> scanner.pendingChunks() == 0 && scanner.snapshot().total() == 17, 400);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    Map<StorageKind, Integer> expected = Map.ofEntries(
                            Map.entry(StorageKind.CHESTS, 2), Map.entry(StorageKind.TRAPPED_CHESTS, 1),
                            Map.entry(StorageKind.BARRELS, 1), Map.entry(StorageKind.SHULKER_BOXES, 1),
                            Map.entry(StorageKind.ENDER_CHESTS, 1), Map.entry(StorageKind.HOPPERS, 1),
                            Map.entry(StorageKind.DROPPERS_DISPENSERS, 2), Map.entry(StorageKind.FURNACES, 3),
                            Map.entry(StorageKind.BREWING_STANDS, 1), Map.entry(StorageKind.OTHER, 3),
                            Map.entry(StorageKind.STORAGE_ENTITIES, 1));
                    for (var entry : expected.entrySet())
                        require(snapshot.count(entry.getKey()) == entry.getValue(), "Wrong count for " + entry.getKey()
                                + ": " + snapshot.count(entry.getKey()) + " (all: " + snapshot.counts() + ")");
                    require(snapshot.doubleChests() == 1, "The double chest was not recognised once: " + snapshot.doubleChests());
                    var merged = snapshot.targets().stream().filter(StorageEspScanner.Target::doubleChest).findFirst().orElseThrow();
                    require(merged.bounds().getXsize() > 1.8 && merged.bounds().getXsize() < 2.01 && merged.bounds().getZsize() < 1,
                            "The double chest box does not span both halves: " + merged.bounds());
                    require(merged.pos().equals(DOUBLE_RIGHT) || merged.pos().equals(DOUBLE_LEFT), "Merged chest has the wrong position");
                    double last = -1;
                    for (var target : snapshot.targets()) {
                        require(target.distanceSq() >= last, "Targets are not sorted nearest first");
                        last = target.distanceSq();
                    }
                    require(!snapshot.limited(), "17 targets should fit under the default limit");
                });

                // The renderer draws them: compare against an identical frame with opacity 0 (renderer skip).
                Path visible = screenshot(context, "storage-esp-01-highlights");
                context.runOnClient(client -> settings.opacity = 0);
                context.waitTicks(2);
                Path hidden = screenshot(context, "storage-esp-00-hidden");
                int highlighted = changed(visible, hidden, 0, 0, 1280, 800);
                require(highlighted > 2000, "Storage boxes did not change the rendered frame: " + highlighted);
                context.runOnClient(client -> { settings.opacity = 255; settings.tracers = true; });
                context.waitTicks(2);
                Path tracers = screenshot(context, "storage-esp-02-tracers");
                require(changed(tracers, visible, 0, 0, 1280, 800) > 500, "Tracers did not draw");
                context.runOnClient(client -> settings.tracers = false);

                // Breaking one half leaves the other as a single chest; live block updates are picked up.
                server.runCommand("setblock " + DOUBLE_RIGHT.getX() + " 65 0 minecraft:air");
                context.waitFor(client -> scanner.snapshot().doubleChests() == 0 && scanner.snapshot().count(StorageKind.CHESTS) == 2
                        && scanner.snapshot().total() == 17, 200);
                context.runOnClient(client -> require(scanner.snapshot().targets().stream()
                        .noneMatch(t -> t.pos().equals(DOUBLE_RIGHT)), "The broken half is still highlighted"));
                server.runCommand("setblock 9 65 6 minecraft:barrel");
                context.waitFor(client -> scanner.snapshot().count(StorageKind.BARRELS) == 2, 200);
                server.runCommand("setblock 9 65 6 minecraft:air");
                context.waitFor(client -> scanner.snapshot().count(StorageKind.BARRELS) == 1 && scanner.snapshot().total() == 17, 200);

                // Per-kind toggles, nearest-N and range apply by re-publishing, without rescanning chunks.
                long scans = context.computeOnClient(client -> scanner.scannedChunks());
                context.runOnClient(client -> settings.kinds.put(StorageKind.HOPPERS.id, false));
                context.waitFor(client -> scanner.snapshot().count(StorageKind.HOPPERS) == 0 && scanner.snapshot().total() == 16, 100);
                context.runOnClient(client -> settings.kinds.put(StorageKind.HOPPERS.id, true));
                context.waitFor(client -> scanner.snapshot().total() == 17, 100);
                context.runOnClient(client -> settings.maxTargets = 16);
                context.waitFor(client -> scanner.snapshot().limited(), 100);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    require(snapshot.targets().size() == 16 && snapshot.total() == 17, "Nearest-N did not cap the targets at 16");
                    // The dropped target must be the farthest one.
                    require(snapshot.targets().stream().noneMatch(t -> t.pos().equals(new BlockPos(-12, 65, 0))),
                            "Nearest-N kept the farthest target (the chest at -12 65 0) instead of a nearer one");
                    settings.maxTargets = 256;
                    settings.range = 16;
                });
                context.waitTicks(2);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    require(snapshot.total() > 0 && snapshot.total() < 17, "Range did not filter targets: " + snapshot.total());
                    require(snapshot.targets().stream().allMatch(t -> t.distanceSq() <= 16 * 16 + 1), "A target beyond the range was kept");
                    require(scanner.scannedChunks() - scans < 4, "Settings changes rescanned chunks instead of re-publishing");
                    settings.range = 128;
                });
                context.waitTicks(2);
                screenshot(context, "storage-esp-03-hud");

                // Disable clears every cache; re-enabling rescans.
                context.runOnClient(client -> settings.enabled = false);
                context.waitTicks(2);
                context.runOnClient(client -> require(scanner.snapshot().total() == 0 && scanner.indexedChunks() == 0
                        && scanner.pendingChunks() == 0, "Disabling did not clear Storage ESP"));
                context.runOnClient(client -> settings.enabled = true);
                context.waitFor(client -> scanner.snapshot().total() == 17, 400);

                // Leaving the area unloads chunks, and their storage disappears.
                server.runCommand("tp @a 1000.5 120 1000.5");
                context.waitFor(client -> scanner.snapshot().total() == 0 && scanner.snapshot().count(StorageKind.CHESTS) == 0, 600);
                context.runOnClient(client -> require(scanner.indexedChunks() == 0 || scanner.snapshot().total() == 0,
                        "Storage from unloaded chunks remained"));
                server.runCommand("tp @a 0.5 66 14.5 180 25");
                context.waitFor(client -> scanner.snapshot().total() == 17, 600);
                // Leave the module enabled deliberately: the production disconnect hook must clear it.
            }
            context.runOnClient(client -> require(scanner.snapshot().total() == 0 && scanner.indexedChunks() == 0
                    && scanner.pendingChunks() == 0, "Disconnect did not clear Storage ESP"));
        } finally {
            context.runOnClient(client -> {
                settings.enabled = saved.enabled();
                settings.range = saved.range();
                settings.maxTargets = saved.maxTargets();
                settings.opacity = saved.opacity();
                settings.tracers = saved.tracers();
                settings.showHud = saved.showHud();
                settings.kinds = saved.kinds();
                scanner.clear();
            });
        }
    }

    private static void fixtures(TestServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runCommand("time set noon");
        server.runCommand("fill -16 64 -6 16 64 16 minecraft:smooth_stone");
        server.runCommand("fill -16 65 -6 16 70 16 minecraft:air");
        String[] blocks = {
                "-12 65 0 chest", // Uniquely the farthest target from the camera.
                DOUBLE_LEFT.getX() + " 65 0 chest[facing=south,type=left]",
                DOUBLE_RIGHT.getX() + " 65 0 chest[facing=south,type=right]",
                "-2 65 0 trapped_chest", "0 65 0 barrel", "2 65 0 purple_shulker_box", "4 65 0 ender_chest",
                "6 65 0 hopper", "8 65 0 dropper", "10 65 0 dispenser",
                "-8 65 3 furnace", "-6 65 3 blast_furnace", "-4 65 3 smoker", "-2 65 3 brewing_stand",
                "0 65 3 crafter", "2 65 3 decorated_pot", "4 65 3 chiseled_bookshelf",
                // Non-storage neighbours must not be counted.
                "6 65 3 crafting_table", "8 65 3 bookshelf", "10 65 3 spawner"};
        for (String block : blocks) server.runCommand("setblock " + block.replaceFirst(" (\\S+)$", " minecraft:$1"));
        server.runCommand("summon minecraft:chest_minecart 12.5 65 3.5 {NoGravity:1b}");
        server.runCommand("tp @a 0.5 66 14.5 180 25");
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.getToastManager().clear(); client.gui.getChat().clearMessages(false); });
        context.waitTick();
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static int changed(Path a, Path b, int x, int y, int w, int h) {
        try {
            BufferedImage first = ImageIO.read(a.toFile()), second = ImageIO.read(b.toFile());
            int count = 0;
            for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++)
                if (first.getRGB(px, py) != second.getRGB(px, py)) count++;
            return count;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private record Saved(boolean enabled, int range, int maxTargets, int opacity, boolean tracers, boolean showHud,
                         Map<String, Boolean> kinds) { }
}
