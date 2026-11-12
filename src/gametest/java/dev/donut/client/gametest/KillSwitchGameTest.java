package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.fly.FlyController;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.killswitch.KillSwitch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;

/**
 * The test-build kill switch in the real client: once the stored clock is past the build's lifetime, every running
 * module is switched off within a tick, shortcuts and the menu's switches cannot turn anything back on, the menu says
 * why, and a fresh clock (a newer build) brings everything back.
 */
public final class KillSwitchGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        KillSwitch killSwitch = DonutClient.killSwitch();
        require(killSwitch != null && killSwitch.active() && killSwitch.build().hours() == 72,
                "This build should carry a 72-hour kill switch: " + (killSwitch == null ? null : killSwitch.build()));
        Path file = KillSwitch.defaultFile();
        require(file.toString().contains("clientGameTest-killswitch"), "Runtime tests must not touch ~/.donut-client: " + file);
        var config = DonutClient.config();
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        long now = System.currentTimeMillis();
        require(!DonutClient.expired() && killSwitch.remaining(now) > 71 * KillSwitch.HOUR, "A fresh run did not start a full clock");

        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            // Several kinds of module running at once.
            context.runOnClient(mc -> {
                FreecamController.getInstance().setEnabled(true);
                config.storageEsp.enabled = config.fullbright.enabled = config.autoMiner.enabled = config.search.enabled = true;
            });
            context.waitTicks(5);
            require(context.computeOnClient(mc -> FreecamController.getInstance().isActive()), "Freecam fixture did not start");

            // The clock ran out (first run 73 hours ago): everything goes off on the next tick and says why.
            writeClock(file, killSwitch, now - 73 * KillSwitch.HOUR, now - 73 * KillSwitch.HOUR);
            context.runOnClient(mc -> killSwitch.reload(System.currentTimeMillis()));
            context.waitTicks(2);
            context.runOnClient(mc -> {
                require(DonutClient.expired(), "Not expired after 73 hours");
                require(!FreecamController.getInstance().isActive(), "Freecam stayed on");
                require(!config.storageEsp.enabled && !config.fullbright.enabled && !config.autoMiner.enabled && !config.search.enabled,
                        "A module stayed on after expiry");
            });

            // Shortcuts no longer switch anything on (Freecam F6, Fly by its module key).
            KeyMapping freecamKey = context.computeOnClient(mc -> java.util.Arrays.stream(mc.options.keyMappings)
                    .filter(key -> key.getName().equals("key.donut.toggle_freecam")).findFirst().orElseThrow());
            context.getInput().pressKey(freecamKey);
            context.waitTicks(3);
            require(context.computeOnClient(mc -> !FreecamController.getInstance().isActive()), "The Freecam shortcut worked after expiry");
            context.runOnClient(mc -> FlyController.getInstance().setEnabled(true));
            context.waitTicks(2);
            require(context.computeOnClient(mc -> !FlyController.getInstance().isActive()), "Fly was not switched back off");

            // The menu opens, explains, and its switches do nothing.
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));
            GuiTestInput.click(context, "tab0");
            GuiTestInput.reveal(context, "module15");
            GuiTestInput.click(context, "enable15");
            context.waitTicks(2);
            require(!config.storageEsp.enabled, "The menu switch enabled Storage ESP after expiry");
            context.takeScreenshot(TestScreenshotOptions.of("kill-switch-01-expired-menu").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
            context.takeScreenshot(TestScreenshotOptions.of("kill-switch-02-expired-chat").withSize(1280, 800));

            // A new build (fresh clock) works again.
            writeClock(file, killSwitch, now, now);
            context.runOnClient(mc -> killSwitch.reload(System.currentTimeMillis()));
            require(!DonutClient.expired(), "A fresh clock still reads as expired");
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));
            GuiTestInput.click(context, "tab0");
            GuiTestInput.reveal(context, "module15");
            GuiTestInput.click(context, "enable15");
            require(config.storageEsp.enabled, "The menu switch did not work with time left");
            context.takeScreenshot(TestScreenshotOptions.of("kill-switch-03-time-left").withSize(1280, 800));
            GuiTestInput.click(context, "enable15");
            GuiTestInput.closeAnimated(context);
        }
    }

    private static void writeClock(Path file, KillSwitch killSwitch, long first, long latest) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, killSwitch.build().id() + "=" + first + "," + latest + "\n");
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
