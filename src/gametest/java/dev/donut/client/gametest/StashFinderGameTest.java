package dev.donut.client.gametest;

import com.google.gson.Gson;
import dev.donut.client.DonutClient;
import dev.donut.client.config.StashFinderSettings;
import dev.donut.client.gui.StashScreen;
import dev.donut.client.stash.StashEntry;
import dev.donut.client.stash.StashFinderController;
import dev.donut.client.stash.StashLog;
import dev.donut.client.storageesp.StorageKind;
import dev.donut.client.waypoints.WaypointController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** Ten chests placed with /setblock become one stash entry; waypoint, copy, export, persistence and delete work. */
public final class StashFinderGameTest implements FabricClientGameTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        Gson gson = new Gson();
        String saved = gson.toJson(config.stashFinder);
        boolean storage = config.storageEsp.enabled, waypointsOn = config.waypoints.enabled;
        var controller = StashFinderController.get();
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.stashFinder = new StashFinderSettings();
            config.stashFinder.sanitize();
            config.stashFinder.enabled = true;
            config.storageEsp.enabled = false; // Works without Storage ESP.
            config.waypoints.enabled = true;
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 40.5 -60 20.5 0 30");
            context.waitTicks(20);
            world.getClientWorld().waitForChunksRender();
            String key = context.computeOnClient(mc -> controller.server());
            int before = context.computeOnClient(mc -> controller.forCurrentServer().size());

            // Chunk (2, 2): 10 single chests, spaced so none pair. Chunk (3, 2): only 5, below the threshold of 8.
            int placed = 0;
            for (int z = 34; z <= 38 && placed < 10; z += 2)
                for (int x = 33; x <= 45 && placed < 10; x += 2, placed++) server.runCommand("setblock " + x + " -60 " + z + " minecraft:chest");
            server.runCommand("setblock 34 -60 44 minecraft:hopper"); // weight 0: counted in the summary only
            for (int x = 49; x <= 57; x += 2) server.runCommand("setblock " + x + " -60 34 minecraft:barrel");
            context.waitFor(mc -> {
                StashEntry entry = controller.log().find(key, OVERWORLD, 2, 2);
                return entry != null && entry.count(StorageKind.CHESTS) == 10;
            }, 200);
            context.waitTicks(30);
            context.runOnClient(mc -> {
                StashEntry entry = controller.log().find(key, OVERWORLD, 2, 2);
                require(entry.score == 10 && entry.total == 11 && entry.count(StorageKind.HOPPERS) == 1, "Counts: " + entry.summary()
                        + " score " + entry.score + " total " + entry.total);
                require(entry.x >= 32 && entry.x < 48 && entry.z >= 32 && entry.z < 48 && entry.y == -60, "Position outside the chunk");
                require(controller.log().find(key, OVERWORLD, 3, 2) == null, "Five barrels were logged as a stash");
                require(controller.forCurrentServer().size() == before + 1, "Expected exactly one new stash");
                require(!config.storageEsp.enabled, "Storage ESP was needed");
            });

            // The list screen: add a waypoint, copy, export, persist, delete.
            context.setScreen(() -> new StashScreen(null, config, () -> { }));
            context.waitTicks(2);
            context.runOnClient(mc -> {
                var screen = (StashScreen) mc.screen;
                screen.selectFirst();
                StashEntry entry = screen.selected();
                require(entry != null && entry.chunkX == 2 && entry.chunkZ == 2, "The newest stash is not selected first");
                int waypoints = WaypointController.get().forCurrentServer().size();
                screen.addWaypoint();
                var added = WaypointController.get().forCurrentServer().stream()
                        .filter(w -> w.name.equals("Stash 10") && w.x == entry.x && w.y == entry.y && w.z == entry.z).findFirst();
                require(added.isPresent() && WaypointController.get().forCurrentServer().size() == waypoints + 1,
                        "Add waypoint did not create the waypoint: " + screen.status());
                screen.copy();
                require(mc.keyboardHandler.getClipboard().equals(entry.x + " " + entry.y + " " + entry.z), "Clipboard: " + mc.keyboardHandler.getClipboard());
                screen.export();
                require(screen.status().startsWith("Exported"), "Export: " + screen.status());
            });
            shot(context, "stash-finder-01-list");
            context.runOnClient(mc -> {
                try {
                    List<Path> exports;
                    try (Stream<Path> files = Files.list(controller.directory())) {
                        exports = files.filter(p -> p.getFileName().toString().startsWith("export-")).toList();
                    }
                    Path csv = exports.stream().filter(p -> p.toString().endsWith(".csv")).findFirst().orElseThrow();
                    Path json = exports.stream().filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
                    var rows = StashLog.fromCsv(Files.readString(csv));
                    require(rows.stream().anyMatch(e -> e.chunkX == 2 && e.chunkZ == 2 && e.count(StorageKind.CHESTS) == 10), "CSV rows: " + rows.size());
                    require(StashLog.fromJson(Files.readString(json)).size() == rows.size(), "JSON and CSV disagree");
                    controller.save();
                    require(Files.readString(controller.logFile()).contains("\"chunkX\": 2"), "The log was not persisted");
                    for (Path file : exports) Files.deleteIfExists(file);
                } catch (java.io.IOException error) { throw new AssertionError(error); }
                var screen = (StashScreen) mc.screen;
                var waypoint = WaypointController.get().forCurrentServer().stream().filter(w -> w.name.equals("Stash 10")).findFirst().orElseThrow();
                WaypointController.get().remove(waypoint);
                screen.delete();
                screen.delete(); // confirm
                require(controller.log().find(key, OVERWORLD, 2, 2) == null, "Delete did not remove the stash");
            });
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(mc -> {
                config.stashFinder = gson.fromJson(saved, StashFinderSettings.class);
                config.stashFinder.sanitize();
                config.storageEsp.enabled = storage;
                config.waypoints.enabled = waypointsOn;
                controller.save();
            });
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> mc.getToastManager().clear());
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
