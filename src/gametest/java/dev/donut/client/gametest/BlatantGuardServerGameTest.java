package dev.donut.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.DonutClient;
import dev.donut.client.fly.FlyController;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.input.ModuleKeybinds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Server half of the multiplayer guard, on a real server (tools/anticheat-server.sh, -PguardServerTestsOnly).
 * Not in the default suite: it needs that server running. Nothing it does switches on a module that anti-cheats
 * punish: every attempt is expected to be refused, and the one allowed module (Auto Eat) does nothing while full.
 */
public final class BlatantGuardServerGameTest implements FabricClientGameTest {
    private static final String ADDRESS = System.getenv().getOrDefault("DONUT_AC_ADDRESS", "127.0.0.1:25599");

    @Override
    public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        var guard = config.blatantGuard;
        require(guard.blockUnlisted && guard.allowedServers.isEmpty(), "Guard should start on with no servers");
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        connect(context);

        // 1. The Fly shortcut is refused on a server that isn't allowed.
        context.runOnClient(mc -> { ModuleKeybinds.mapping(6).setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8)); KeyMapping.resetMapping(); });
        context.getInput().pressKey(ModuleKeybinds.mapping(6));
        context.waitTicks(5);
        require(context.computeOnClient(mc -> !FlyController.enabled()), "The Fly shortcut worked on an unlisted server");
        context.takeScreenshot(TestScreenshotOptions.of("guard-server-01-fly-refused").withSize(1280, 800));

        // 2. A module switched on some other way (a saved setting, a profile) is stopped before it runs.
        context.runOnClient(mc -> config.autoMiner.enabled = true);
        context.waitTicks(2);
        require(context.computeOnClient(mc -> !config.autoMiner.enabled), "Auto Miner kept running on an unlisted server");

        // 3. The menu switch refuses too, and the footer says why.
        GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));
        GuiTestInput.click(context, "tab3");
        GuiTestInput.click(context, "enable28");
        require(!config.autoEat.enabled, "The menu switched Auto Eat on on an unlisted server");
        context.takeScreenshot(TestScreenshotOptions.of("guard-server-02-menu-refused").withSize(1280, 800));

        // 4. Allowing this server lets it run; taking that back stops it on the next tick.
        GuiTestInput.click(context, "module28");
        GuiTestInput.reveal(context, "m28.guardAllow");
        GuiTestInput.click(context, "m28.guardAllow");
        require(guard.allowedServers.contains(ADDRESS), "Allow did not add " + ADDRESS + ": " + guard.allowedServers);
        context.takeScreenshot(TestScreenshotOptions.of("guard-server-03-allowed").withSize(1280, 800));
        GuiTestInput.click(context, "enable28");
        require(config.autoEat.enabled, "Auto Eat stayed off on an allowed server");
        GuiTestInput.click(context, "m28.guardAllow");
        require(guard.allowedServers.isEmpty(), "Allow did not toggle back off");
        context.waitTicks(3);
        require(!config.autoEat.enabled, "Auto Eat kept running after the server was removed from the list");
        GuiTestInput.closeAnimated(context);

        context.runOnClient(mc -> {
            ModuleKeybinds.mapping(6).setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            if (mc.getConnection() != null) mc.getConnection().getConnection().disconnect(Component.literal("Guard test finished"));
        });
        context.waitTicks(20);
        context.setScreen(TitleScreen::new);
        context.waitTicks(5);
    }

    private static void connect(ClientGameTestContext context) {
        context.setScreen(() -> new JoinMultiplayerScreen(new TitleScreen()));
        context.runOnClient(mc -> ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(ADDRESS),
                new ServerData("Guard test", ADDRESS, ServerData.Type.OTHER), false, null));
        context.waitFor(mc -> mc.level != null && mc.player != null, 600);
        for (int i = 0; i < 50 && context.computeOnClient(mc -> mc.player.isDeadOrDying()); i++) {
            context.runOnClient(mc -> mc.player.respawn());
            context.waitTicks(10);
        }
        context.waitTicks(60);
        context.runOnClient(mc -> mc.setScreen(null));
        context.waitTicks(5);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
