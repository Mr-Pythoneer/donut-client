package dev.donut.client.gametest;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * A free-standing 5x3 wall in Survival. Its top row can only be placed from above the middle row, so the builder
 * must put down a temporary step and stand on it. It used to stall ("No walking route") because the wall's own
 * blocks were mistaken for steps although more wall sat on top of them.
 */
public final class AutoBuilderTallWallGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 65, -3);

    @Override
    public void runTest(ClientGameTestContext context) {
        AutoBuilderController builder = AutoBuilderController.get();
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("time set noon");
            server.runCommand("fill -12 64 -12 12 64 12 minecraft:smooth_stone");
            server.runCommand("fill -12 65 -12 12 72 12 minecraft:air");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 64");
            server.runCommand("tp @a 2.5 65 1.5 180 10");
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(mc -> mc.player != null && mc.player.onGround());

            List<Schematic.Block> blocks = new ArrayList<>();
            for (int x = 0; x < 5; x++) for (int y = 0; y < 3; y++)
                blocks.add(new Schematic.Block(new BlockPos(x, y, 0), Blocks.COBBLESTONE.defaultBlockState(), null));
            context.runOnClient(mc -> {
                builder.setSchematic(new Schematic("tall wall", blocks, new BlockPos(5, 3, 1), 1, List.of()));
                builder.setTransform(new PlacementTransform(ORIGIN, 0, false));
                builder.start();
            });
            for (int tick = 0; tick < 1800 && context.computeOnClient(mc -> builder.isRunning()); tick++) context.waitTick();
            context.takeScreenshot(TestScreenshotOptions.of("autobuilder-tall-wall").withSize(1280, 800));
            String result = context.computeOnClient(mc -> builder.status() + " / " + builder.confirmedBlocks() + " / " + builder.reason());
            require(context.computeOnClient(mc -> builder.status() == AutoBuilderController.Status.COMPLETED), "The tall wall did not finish: " + result);
            require(context.computeOnClient(mc -> builder.confirmedBlocks() == blocks.size()), "Not every wall block was confirmed: " + result);
            require(context.computeOnClient(mc -> builder.supportsPlaced() >= 1), "Expected a temporary step for the top row: " + result);
            server.runOnServer(mc -> {
                for (var block : blocks) {
                    BlockPos pos = ORIGIN.offset(block.pos());
                    require(mc.overworld().getBlockState(pos).is(Blocks.COBBLESTONE), "Server has no wall block at " + pos);
                }
            });
            context.runOnClient(mc -> builder.cancel());
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
