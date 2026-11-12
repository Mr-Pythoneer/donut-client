package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.gui.DonutScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * Single-player half of the multiplayer guard: Blatant modules run freely here, the menu shows each module's
 * anti-cheat result (tile tags only where there's a warning), and the guard switch persists.
 * The server half is BlatantGuardServerGameTest (-PguardServerTestsOnly, needs tools/anticheat-server.sh).
 */
public final class BlatantGuardGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        var guard = config.blatantGuard;
        require(guard.blockUnlisted && guard.allowedServers.isEmpty(), "Guard should start on with no servers");
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));

            // Single-player: the guard never blocks.
            GuiTestInput.click(context, "tab3");
            GuiTestInput.click(context, "enable20");
            context.waitTicks(3);
            require(config.autoMiner.enabled, "Auto Miner was blocked in single-player");
            GuiTestInput.click(context, "enable20");
            require(!config.autoMiner.enabled, "Auto Miner did not switch back off");
            context.takeScreenshot(TestScreenshotOptions.of("guard-01-blatant-tags").withSize(1280, 800));

            // Fly's inspector: its anti-cheat result, and where Blatant modules may run.
            GuiTestInput.click(context, "module6");
            GuiTestInput.reveal(context, "m6.guardOn");
            context.takeScreenshot(TestScreenshotOptions.of("guard-02-fly-inspector").withSize(1280, 800));
            GuiTestInput.click(context, "m6.guardOn");
            require(!guard.blockUnlisted, "The guard switch did not turn the guard off");
            GuiTestInput.click(context, "m6.guardOn");
            require(guard.blockUnlisted, "The guard switch did not turn the guard back on");
            require(!GuiTestInput.visible(context, "m6.guardAllow"), "Single-player offered a server allow switch");

            // A quiet module: no tile tag, but the inspector still shows the result.
            GuiTestInput.click(context, "tab1");
            GuiTestInput.click(context, "search");
            GuiTestInput.type(context, "storage esp");
            GuiTestInput.click(context, "module15");
            require(!GuiTestInput.visible(context, "m15.guardOn"), "A Visual module showed the Blatant guard");
            context.takeScreenshot(TestScreenshotOptions.of("guard-03-quiet-inspector").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
