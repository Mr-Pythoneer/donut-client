package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.gui.DonutScreen;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;

/**
 * Developer tool, not a regression test: renders the HUD panels and the click GUI at several window sizes and GUI
 * scales for visual review. Only runs with {@code -PuiPreviewOnly=true}.
 */
public final class UiPreviewGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.showHud = true;
            config.coordinates.enabled = config.storageEsp.enabled = config.durabilityAlert.enabled = true;
            config.waypoints.enabled = true;
            mc.options.guiScale().set(2);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 -60 0.5 -90 10");
            context.waitTicks(20);
            String[] blocks = {"3 -60 -2 chest", "3 -60 0 barrel", "3 -60 2 hopper", "5 -60 -2 furnace", "5 -60 0 purple_shulker_box",
                    "5 -60 2 ender_chest", "7 -60 -2 trapped_chest", "7 -60 0 dropper", "7 -60 2 brewing_stand"};
            for (String block : blocks) {
                String[] p = block.split(" ");
                server.runCommand("setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:" + p[3]);
            }
            server.runCommand("summon minecraft:chest_minecart 9.5 -60 0.5");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe[minecraft:damage=1530]");
            server.runCommand("item replace entity @a armor.head with minecraft:iron_helmet[minecraft:damage=158]");
            context.waitTicks(40);
            world.getClientWorld().waitForChunksRender();
            shoot(context, "ui-hud-gui2");
            context.runOnClient(mc -> { mc.options.guiScale().set(4); mc.resizeDisplay(); });
            context.waitTicks(5);
            shoot(context, "ui-hud-gui4");
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeDisplay(); });
            context.waitTicks(5);

            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));
            shoot(context, "ui-menu-empty");
            GuiTestInput.click(context, "module15");
            shoot(context, "ui-menu-selected");
            GuiTestInput.click(context, "search");
            GuiTestInput.type(context, "tracer");
            shoot(context, "ui-menu-search");
            GuiTestInput.click(context, "tab2");
            shoot(context, "ui-menu-appearance");
            GuiTestInput.click(context, "tab6");
            shoot(context, "ui-menu-hud");
            GuiTestInput.click(context, "workspaceModules");
            context.runOnClient(mc -> { mc.options.guiScale().set(4); mc.resizeDisplay(); });
            context.waitTicks(5);
            shoot(context, "ui-menu-gui4");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(mc -> mc.options.guiScale().set(2));
        }
    }

    private static Path shoot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> { mc.getToastManager().clear(); mc.gui.getChat().clearMessages(false); });
        context.waitTicks(3);
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }
}
