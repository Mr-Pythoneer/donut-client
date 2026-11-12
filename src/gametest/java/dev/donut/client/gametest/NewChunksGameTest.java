package dev.donut.client.gametest;

import com.google.gson.Gson;
import dev.donut.client.DonutClient;
import dev.donut.client.config.NewChunksSettings;
import dev.donut.client.newchunks.NewChunksController;
import dev.donut.client.newchunks.NewChunksTracker.Mark;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.level.ChunkPos;

/**
 * NewChunks through the real packet path.
 *
 * <p>Part 1 (flat test world, deterministic): water poured in a loaded chunk spreads as flowing-beside-source block
 * updates, which is the same signal a freshly generated chunk produces, so that chunk is marked new and the renderer
 * receives its box. After leaving, clearing the session and coming back, the chunk arrives with the settled water in its
 * data and is marked old.
 *
 * <p>Part 2 (real terrain, fixed seed): teleporting far generates chunks whose liquids flow for the first time; at
 * least one must be marked new. Real terrain never guarantees that any particular chunk has liquid, so this only
 * asserts "some".
 */
public final class NewChunksGameTest implements FabricClientGameTest {
    private static final long WATER_CHUNK = ChunkPos.asLong(1, 1);

    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        Gson gson = new Gson();
        String saved = gson.toJson(config.newChunks);
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.newChunks = new NewChunksSettings();
            config.newChunks.enabled = true;
            config.newChunks.showOld = true;
            config.newChunks.height = -60;
            config.newChunks.opacity = 110;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                server.runCommand("gamemode creative @a");
                server.runCommand("time set noon");
                // A pillar to stand on, looking south over chunk (1, 1).
                server.runCommand("fill 24 -60 6 24 -52 6 minecraft:stone");
                server.runCommand("tp @a 24.5 -51 6.5 0 40");
                context.waitTicks(20);
                world.getClientWorld().waitForChunksRender();
                var controller = NewChunksController.get();
                context.runOnClient(mc -> require(controller.tracker().size() == 0, "A flat world has no liquid, nothing is marked: "
                        + controller.tracker().size()));

                server.runCommand("setblock 24 -60 24 minecraft:water");
                context.waitFor(mc -> controller.tracker().mark(WATER_CHUNK) == Mark.NEW, 200);
                // The overlay reaches the renderer.
                context.waitFor(mc -> NewChunksController.lastRenderedBoxes > 0, 100);
                context.runOnClient(mc -> {
                    var overlay = controller.overlay(1, 1);
                    require(overlay.stream().anyMatch(o -> o.mark() == Mark.NEW && o.bounds().minX > 16 && o.bounds().maxX < 32
                            && o.bounds().minY == -60), "The new chunk's overlay box is wrong: " + overlay);
                });
                context.waitTicks(40);
                shot(context, "new-chunks-01-new");

                // Leave until the client forgets the chunk, start a fresh session, come back: settled water means old.
                server.runCommand("tp @a 3000.5 -50 3000.5");
                context.waitFor(mc -> !mc.level.getChunkSource().hasChunk(1, 1), 600);
                context.runOnClient(mc -> controller.tracker().clear());
                server.runCommand("tp @a 24.5 -51 6.5 0 40");
                context.waitFor(mc -> controller.tracker().mark(WATER_CHUNK) == Mark.OLD, 600);
                context.runOnClient(mc -> require(controller.tracker().newCount() == 0, "Revisiting marked something new"));
                world.getClientWorld().waitForChunksRender();
                context.waitTicks(20);
                shot(context, "new-chunks-02-old");

                // Disabling clears the session.
                context.runOnClient(mc -> config.newChunks.enabled = false);
                context.waitTicks(2);
                context.runOnClient(mc -> require(controller.tracker().size() == 0, "Disabling did not clear marks"));
                context.runOnClient(mc -> config.newChunks.enabled = true);
            }

            // Real terrain.
            try (var world = context.worldBuilder().setUseConsistentSettings(false)
                    .adjustSettings(settings -> settings.setSeed("donut-newchunks")).create()) {
                var server = world.getServer();
                // Spectators don't fall, so the camera stays above the new terrain.
                server.runCommand("gamemode spectator @a");
                server.runCommand("time set noon");
                server.runCommand("tp @a 6000.5 180 6000.5 0 55");
                var controller = NewChunksController.get();
                context.waitFor(mc -> controller.tracker().newCount() > 0, 1200);
                context.waitTicks(200); // Let more chunks settle for the screenshot.
                context.runOnClient(mc -> require(mc.player.getY() > 150, "Fell out of the sky: " + mc.player.getY()));
                int newCount = context.computeOnClient(mc -> controller.tracker().newCount());
                int oldCount = context.computeOnClient(mc -> controller.tracker().oldCount());
                System.out.println("[NewChunks] real terrain after generating: " + newCount + " new, " + oldCount + " old");
                context.runOnClient(mc -> { config.newChunks.height = 62; config.newChunks.range = 12; });
                world.getClientWorld().waitForChunksRender();
                context.waitTicks(20);
                shot(context, "new-chunks-03-real-terrain");
            }
        } finally {
            context.runOnClient(mc -> {
                config.newChunks = gson.fromJson(saved, NewChunksSettings.class);
                config.newChunks.sanitize();
            });
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> { mc.getToastManager().clear(); mc.gui.getChat().clearMessages(false); });
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
