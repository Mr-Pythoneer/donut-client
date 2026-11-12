package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.autoreconnect.AutoReconnectController;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Auto Reconnect on vanilla's real disconnect screen. A multiplayer-style disconnect is simulated: the controller is
 * told a connection started to a closed local port (as the connect-screen mixin does), the vanilla DisconnectedScreen
 * opens, and each countdown really calls ConnectScreen.startConnecting, which fails with "Connection refused" and
 * returns to a new DisconnectedScreen. No dedicated server (and so no EULA) is needed.
 */
public final class AutoReconnectGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        var reconnect = AutoReconnectController.get();
        require(config != null && reconnect != null, "Auto Reconnect was not initialized");
        var original = config.autoReconnect;
        int port = closedPort();
        String address = "127.0.0.1:" + port;
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.autoReconnect = new dev.donut.client.config.AutoReconnectSettings();
            config.autoReconnect.enabled = true;
            config.autoReconnect.delaySeconds = 1;
            config.autoReconnect.maxAttempts = 2;
            config.autoReconnect.backoff = false;
        });
        try {
            // 1. A lost connection shows the countdown and a Stop button.
            remember(context, reconnect, address);
            disconnect(context, Component.literal("Timed out"));
            context.waitTicks(2);
            context.runOnClient(mc -> {
                require(reconnect.trackedScreen() == mc.screen && reconnect.counting(), "No countdown on the disconnect screen");
                require(button(mc.screen, "Reconnect in 1s") != null, "Countdown button missing: " + labels(mc.screen));
                require(button(mc.screen, "Stop") != null && button(mc.screen, "Stop").visible, "Stop button missing");
                require(reconnect.status().contains(address) && reconnect.status().contains("attempt 1/2"), reconnect.status());
            });
            context.takeScreenshot(TestScreenshotOptions.of("auto-reconnect-01-countdown").withSize(1280, 800));

            // 2. The countdown joins the same address through vanilla's connect screen; it fails and counts down again.
            context.waitFor(mc -> reconnect.attemptsMade() == 1, 60);
            context.waitFor(mc -> mc.screen instanceof DisconnectedScreen && reconnect.trackedScreen() == mc.screen, 400);
            context.runOnClient(mc -> {
                require(reconnect.counting() && reconnect.status().contains("attempt 2/2"), "Second attempt not scheduled: " + reconnect.status());
                require(address.equals(reconnect.targetAddress()), "The address changed: " + reconnect.targetAddress());
            });
            context.takeScreenshot(TestScreenshotOptions.of("auto-reconnect-02-retry").withSize(1280, 800));

            // 3. After the last allowed attempt it gives up; the button still reconnects by hand.
            context.waitFor(mc -> reconnect.attemptsMade() == 2, 60);
            context.waitFor(mc -> mc.screen instanceof DisconnectedScreen && reconnect.trackedScreen() == mc.screen, 400);
            context.waitTicks(30);
            context.runOnClient(mc -> {
                require(!reconnect.counting() && reconnect.attemptsMade() == 2, "Retried past the attempt limit");
                require(reconnect.status().contains("gave up after 2"), reconnect.status());
                require(button(mc.screen, "Reconnect") != null && !button(mc.screen, "Stop").visible, "Manual button missing: " + labels(mc.screen));
            });
            context.takeScreenshot(TestScreenshotOptions.of("auto-reconnect-03-gave-up").withSize(1280, 800));

            // 4. Stop cancels the countdown on this screen (clicked through the screen's own mouse handler).
            remember(context, reconnect, address);
            disconnect(context, Component.literal("Server closed"));
            context.waitTicks(2);
            context.runOnClient(mc -> {
                var stop = button(mc.screen, "Stop");
                require(stop != null && reconnect.counting(), "No countdown to stop");
                mc.screen.mouseClicked(new MouseButtonEvent(stop.getX() + stop.getWidth() / 2.0, stop.getY() + stop.getHeight() / 2.0,
                        new MouseButtonInfo(0, 0)), false);
            });
            context.waitTicks(40);
            context.runOnClient(mc -> {
                require(!reconnect.counting() && reconnect.attemptsMade() == 0 && mc.screen instanceof DisconnectedScreen, "Stop did not cancel");
                require(reconnect.status().equals("Auto Reconnect stopped"), reconnect.status());
            });

            // 5. Leaving the disconnect screen stops retrying.
            remember(context, reconnect, address);
            disconnect(context, Component.literal("Server closed"));
            context.waitTicks(2);
            context.setScreen(() -> new JoinMultiplayerScreen(new TitleScreen()));
            context.waitTicks(40);
            context.runOnClient(mc -> require(reconnect.trackedScreen() == null && reconnect.attemptsMade() == 0
                    && !(mc.screen instanceof ConnectScreen), "Kept retrying after leaving the screen"));

            // 6. Bans and whitelist kicks are not retried (vanilla translation key and plugin-style text).
            remember(context, reconnect, address);
            disconnect(context, Component.translatable("multiplayer.disconnect.banned.reason", "Testing"));
            context.waitTicks(30);
            context.runOnClient(mc -> require(!reconnect.counting() && "banned".equals(reconnect.finalReason())
                    && reconnect.attemptsMade() == 0, "Retried a ban: " + reconnect.status()));
            context.takeScreenshot(TestScreenshotOptions.of("auto-reconnect-04-banned").withSize(1280, 800));
            disconnect(context, Component.literal("You are not whitelisted on this server!"));
            context.waitTicks(2);
            context.runOnClient(mc -> require(!reconnect.counting() && "not whitelisted".equals(reconnect.finalReason()), reconnect.status()));

            // 7. Module off: the vanilla screen is left alone.
            context.runOnClient(mc -> config.autoReconnect.enabled = false);
            disconnect(context, Component.literal("Timed out"));
            context.waitTicks(2);
            context.runOnClient(mc -> require(reconnect.trackedScreen() == null && button(mc.screen, "Reconnect") == null,
                    "Buttons added while disabled: " + labels(mc.screen)));

            // 8. Singleplayer clears the remembered server, so a later disconnect screen is never retried.
            context.runOnClient(mc -> config.autoReconnect.enabled = true);
            context.setScreen(TitleScreen::new);
            try (var world = context.worldBuilder().create()) {
                context.waitFor(mc -> mc.player != null, 200);
                context.runOnClient(mc -> require(!reconnect.hasTarget(), "Singleplayer kept a reconnect target"));
            }
            disconnect(context, Component.literal("Timed out"));
            context.waitTicks(2);
            context.runOnClient(mc -> require(reconnect.trackedScreen() == null && button(mc.screen, "Reconnect") == null,
                    "Offered to reconnect after singleplayer"));
        } finally {
            context.runOnClient(mc -> config.autoReconnect = original);
            context.setScreen(TitleScreen::new);
        }
    }

    private static void remember(ClientGameTestContext context, AutoReconnectController reconnect, String address) {
        context.setScreen(TitleScreen::new);
        context.runOnClient(mc -> reconnect.connecting(ServerAddress.parseString(address), new ServerData("Donut test server", address, ServerData.Type.OTHER)));
    }

    private static void disconnect(ClientGameTestContext context, Component reason) {
        context.setScreen(() -> new DisconnectedScreen(new JoinMultiplayerScreen(new TitleScreen()), Component.literal("Connection Lost"), reason));
    }

    private static AbstractWidget button(Screen screen, String prefix) {
        if (screen == null) return null;
        for (AbstractWidget widget : Screens.getButtons(screen)) if (widget.getMessage().getString().startsWith(prefix)) return widget;
        return null;
    }

    private static String labels(Screen screen) {
        return screen == null ? "no screen" : Screens.getButtons(screen).stream().map(w -> w.getMessage().getString()).toList().toString();
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
