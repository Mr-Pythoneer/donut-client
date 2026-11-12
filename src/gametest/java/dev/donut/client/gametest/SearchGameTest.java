package dev.donut.client.gametest;

import dev.donut.client.config.SearchSettings;
import dev.donut.client.search.SearchScanner;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;

/** Production Search scanner, renderer and HUD against /setblock fixtures behind a wall in an integrated world. */
public final class SearchGameTest implements FabricClientGameTest {
    private static final String DIAMOND = "minecraft:diamond_ore", SPAWNER = "minecraft:spawner", GOLD = "minecraft:gold_block";

    @Override
    public void runTest(ClientGameTestContext context) {
        SearchScanner scanner = SearchScanner.get();
        require(scanner != null, "The production Search scanner was not initialized");
        var settings = scanner.settings();
        String saved = new com.google.gson.Gson().toJson(settings);
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            settings.enabled = false;
            settings.blocks = new ArrayList<>();
            settings.range = 96;
            settings.maxResults = 512;
            settings.opacity = 220;
            settings.mergeAdjacent = true;
            settings.tracers = false;
            settings.showHud = true;
            client.options.guiScale().set(2);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
            try (var world = context.worldBuilder().create()) {
                TestServerContext server = world.getServer();
                fixtures(server);
                context.waitTicks(10);
                world.getClientWorld().waitForChunksRender();

                // An empty list scans nothing; the HUD says so.
                context.runOnClient(client -> settings.enabled = true);
                context.waitTicks(5);
                context.runOnClient(client -> require(scanner.snapshot().total() == 0 && scanner.pendingChunks() == 0,
                        "An empty Search list found blocks"));
                screenshot(context, "search-00-empty-list");

                // Diamond ore (a row of three) and a spawner, both behind the wall.
                context.runOnClient(client -> { settings.add(DIAMOND, 0x56DCCB); settings.add(SPAWNER, 0xFF5A4E); });
                context.waitFor(client -> scanner.pendingChunks() == 0 && scanner.snapshot().total() == 4, 400);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    require(snapshot.count(DIAMOND) == 3 && snapshot.count(SPAWNER) == 1, "Wrong counts: " + snapshot.counts());
                    require(snapshot.targets().size() == 2, "Adjacent diamond ore was not merged into one box: " + snapshot.targets());
                    var merged = snapshot.targets().stream().filter(t -> t.id().equals(DIAMOND)).findFirst().orElseThrow();
                    require(merged.blocks() == 3 && Math.abs(merged.bounds().getXsize() - 3) < 1e-6 && Math.abs(merged.bounds().getZsize() - 1) < 1e-6,
                            "The merged box does not span the row: " + merged.bounds());
                });
                context.runOnClient(client -> settings.mergeAdjacent = false);
                context.waitFor(client -> scanner.snapshot().targets().size() == 4, 60);
                long scans = context.computeOnClient(client -> scanner.scannedChunks());
                context.runOnClient(client -> settings.mergeAdjacent = true);
                context.waitFor(client -> scanner.snapshot().targets().size() == 2, 60);
                context.runOnClient(client -> require(scanner.scannedChunks() == scans, "Toggling merge rescanned chunks instead of re-publishing"));

                // Through walls: the renderer changes the frame compared with opacity 0.
                Path visible = screenshot(context, "search-01-highlights");
                context.runOnClient(client -> settings.opacity = 0);
                context.waitTicks(2);
                Path hidden = screenshot(context, "search-00-hidden");
                int changed = changed(visible, hidden);
                require(changed > 800, "Search boxes did not change the rendered frame through the wall: " + changed);
                context.runOnClient(client -> { settings.opacity = 220; settings.tracers = true; });
                context.waitTicks(2);
                Path tracers = screenshot(context, "search-02-tracers");
                require(changed(tracers, visible) > 300, "Search tracers did not draw");
                context.runOnClient(client -> settings.tracers = false);

                // Live block updates: single block packets and multi-block section updates.
                server.runCommand("setblock 4 65 2 minecraft:air");
                context.waitFor(client -> scanner.snapshot().count(SPAWNER) == 0 && scanner.snapshot().total() == 3, 200);
                server.runCommand("setblock 6 65 2 minecraft:spawner");
                context.waitFor(client -> scanner.snapshot().count(SPAWNER) == 1 && scanner.snapshot().total() == 4, 200);
                context.runOnClient(client -> require(scanner.snapshot().targets().stream()
                        .anyMatch(t -> t.id().equals(SPAWNER) && t.nearest().equals(new BlockPos(6, 65, 2))), "The moved spawner was not found"));
                server.runCommand("fill -4 66 2 -2 66 2 minecraft:diamond_ore");
                context.waitFor(client -> scanner.snapshot().count(DIAMOND) == 6, 200);
                context.runOnClient(client -> {
                    var box = scanner.snapshot().targets().stream().filter(t -> t.id().equals(DIAMOND)).toList();
                    require(box.size() == 1 && box.getFirst().blocks() == 6, "The 3x2 diamond wall should merge into one box: " + box);
                });

                // Range and the nearest-N cap.
                context.runOnClient(client -> settings.add(GOLD, 0xF0B45A));
                context.waitFor(client -> scanner.pendingChunks() == 0 && scanner.snapshot().count(GOLD) == 25, 400);
                context.runOnClient(client -> settings.maxResults = SearchSettings.MIN_RESULTS);
                context.waitTicks(2);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    require(snapshot.limited() && snapshot.shownBlocks() == 16 && snapshot.total() == 32,
                            "Max results did not cap at 16 of 32: " + snapshot.shownBlocks() + "/" + snapshot.total());
                    require(snapshot.targets().stream().noneMatch(t -> t.bounds().minZ < -31.5), "The cap kept a far gold row instead of the nearest blocks");
                    double last = -1;
                    for (var target : snapshot.targets()) { require(target.distanceSq() >= last, "Targets not nearest-first"); last = target.distanceSq(); }
                    settings.maxResults = 512;
                    settings.range = 24;
                });
                context.waitTicks(2);
                context.runOnClient(client -> {
                    var snapshot = scanner.snapshot();
                    require(snapshot.count(GOLD) == 0 && snapshot.total() == 7, "Range 24 should drop the far gold: " + snapshot.counts());
                    settings.range = 96;
                });
                context.waitTicks(2);
                screenshot(context, "search-03-hud");

                // Changing the list rescans: removing an ID drops its blocks.
                context.runOnClient(client -> settings.remove(GOLD));
                context.waitFor(client -> scanner.pendingChunks() == 0 && scanner.snapshot().count(GOLD) == 0
                        && scanner.snapshot().total() == 7, 400);

                // Disable clears every cache; re-enabling rescans.
                context.runOnClient(client -> settings.enabled = false);
                context.waitTicks(2);
                context.runOnClient(client -> require(scanner.snapshot().total() == 0 && scanner.indexedChunks() == 0
                        && scanner.pendingChunks() == 0, "Disabling did not clear Search"));
                context.runOnClient(client -> settings.enabled = true);
                context.waitFor(client -> scanner.snapshot().total() == 7, 400);
                // Leave it enabled: the production disconnect hook must clear it.
            }
            context.runOnClient(client -> require(scanner.snapshot().total() == 0 && scanner.indexedChunks() == 0
                    && scanner.pendingChunks() == 0, "Disconnect did not clear Search"));
        } finally {
            context.runOnClient(client -> {
                var restored = new com.google.gson.Gson().fromJson(saved, SearchSettings.class);
                restored.sanitize();
                settings.enabled = restored.enabled;
                settings.blocks = restored.blocks;
                settings.range = restored.range;
                settings.maxResults = restored.maxResults;
                settings.opacity = restored.opacity;
                settings.mergeAdjacent = restored.mergeAdjacent;
                settings.tracers = restored.tracers;
                settings.showHud = restored.showHud;
                scanner.clear();
            });
        }
    }

    private static void fixtures(TestServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runCommand("time set noon");
        server.runCommand("fill -16 64 -40 16 64 20 minecraft:smooth_stone");
        server.runCommand("fill -16 65 -40 16 72 20 minecraft:air");
        server.runCommand("fill -8 65 9 8 70 9 minecraft:stone");
        for (String block : List.of("-4 65 2 diamond_ore", "-3 65 2 diamond_ore", "-2 65 2 diamond_ore", "4 65 2 spawner",
                // Non-matching neighbours.
                "0 65 2 deepslate_diamond_ore", "2 65 2 diamond_block"))
            server.runCommand("setblock " + block.replaceFirst(" (\\S+)$", " minecraft:$1"));
        // 25 gold blocks far away (about 45 blocks), behind the camera's range cut.
        server.runCommand("fill -2 65 -34 2 65 -30 minecraft:gold_block");
        server.runCommand("tp @a 0.5 65 14.5 180 12");
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.getToastManager().clear(); client.gui.getChat().clearMessages(false); });
        context.waitTick();
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static int changed(Path a, Path b) {
        try {
            BufferedImage first = ImageIO.read(a.toFile()), second = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
                if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
            return count;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
