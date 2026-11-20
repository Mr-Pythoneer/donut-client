/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.autoreconnect;

import dev.donut.client.config.AutoReconnectSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.mixin.DisconnectedScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Adds a countdown and a Stop button to vanilla's disconnect screen and, when the countdown ends, joins the same
 * server through {@link ConnectScreen#startConnecting} exactly like the server list does. It remembers the last
 * address you connected to (singleplayer clears it), counts attempts until a join succeeds, and does nothing once you
 * leave the disconnect screen.
 */
public final class AutoReconnectController {
    private static AutoReconnectController instance;
    private final ClientConfig config;
    private ServerAddress address;
    private ServerData data;
    private boolean ownConnect;
    private int attemptsMade;
    // State for the disconnect screen currently shown.
    private DisconnectedScreen screen;
    private int ticksLeft = -1;
    private boolean stopped;
    private String finalReason;
    private Button main, stop;

    public AutoReconnectController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static AutoReconnectController get() { return instance; }
    public AutoReconnectSettings settings() { return config.autoReconnect; }
    public boolean hasTarget() { return address != null && data != null; }
    public String targetAddress() { return data == null ? null : data.ip; }
    public int attemptsMade() { return attemptsMade; }
    public boolean counting() { return screen != null && ticksLeft > 0; }
    public int secondsLeft() { return ticksLeft <= 0 ? 0 : (ticksLeft + 19) / 20; }
    public String finalReason() { return finalReason; }
    public Screen trackedScreen() { return screen; }
    public String buttonLabel() { return main == null ? null : main.getMessage().getString(); }

    /** Called (from a mixin) whenever any connection starts. Your own connects reset the attempt count. */
    public void connecting(ServerAddress address, ServerData data) {
        if (ownConnect) return;
        this.address = address;
        this.data = data;
        attemptsMade = 0;
    }

    public void joined(Minecraft client) {
        // Singleplayer and LAN-hosted worlds are never retried.
        if (client.isLocalServer() || client.getCurrentServer() == null) { address = null; data = null; }
        attemptsMade = 0;
    }

    /** ScreenEvents.AFTER_INIT: decorates disconnect screens (also after a resize re-init). */
    public void screenInit(Minecraft client, Screen opened, int width, int height) {
        if (!(opened instanceof DisconnectedScreen disconnected)) return;
        if (!settings().enabled || !hasTarget() || data.isRealm()) return;
        if (disconnected != screen) {
            screen = disconnected;
            stopped = false;
            DisconnectionDetails details = ((DisconnectedScreenAccessor) disconnected).donut$details();
            Component reason = details == null ? null : details.reason();
            String key = reason != null && reason.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
            finalReason = settings().skipBans ? ReconnectPolicy.finalReason(key, reason == null ? null : reason.getString()) : null;
            ticksLeft = finalReason == null && ReconnectPolicy.attemptsLeft(attemptsMade, settings().maxAttempts)
                    ? ReconnectPolicy.delaySeconds(settings().delaySeconds, attemptsMade + 1, settings().backoff) * 20 : -1;
        }
        main = Button.builder(Component.literal(label()), button -> reconnectNow(client)).bounds(0, 0, 150, 20).build();
        stop = Button.builder(Component.literal("Stop"), button -> stopCountdown()).bounds(0, 0, 70, 20).build();
        Screens.getButtons(opened).add(main);
        Screens.getButtons(opened).add(stop);
        layout(opened);
        ScreenEvents.afterTick(opened).register(this::tick);
        ScreenEvents.remove(opened).register(removed -> { if (removed == screen) clearScreen(); });
        ScreenEvents.afterRender(opened).register((shown, graphics, mouseX, mouseY, delta) -> {
            if (shown != screen) return;
            graphics.drawCenteredString(client.font, status(), shown.width / 2, shown.height - 42, 0xFFA8AEBA);
        });
    }

    private void tick(Screen ticking) {
        if (ticking != screen) return;
        layout(ticking);
        if (ticksLeft > 0 && --ticksLeft == 0) {
            reconnectNow(Minecraft.getInstance());
            return;
        }
        if (main != null) main.setMessage(Component.literal(label()));
        if (stop != null) stop.visible = counting();
    }

    private void layout(Screen shown) {
        if (main == null || stop == null) return;
        int y = shown.height - 30, total = 150 + 4 + 70, x = (shown.width - total) / 2;
        boolean withStop = counting();
        main.setPosition(withStop ? x : (shown.width - 150) / 2, y);
        stop.setPosition(x + 154, y);
        stop.visible = withStop;
    }

    private String label() {
        return counting() ? "Reconnect in " + secondsLeft() + "s" : "Reconnect";
    }

    /** The line above the buttons. */
    public String status() {
        int max = settings().maxAttempts;
        if (finalReason != null) return "Auto Reconnect: not retrying (" + finalReason + ")";
        if (counting()) return "Auto Reconnect to " + data.ip + ", " + ReconnectPolicy.attemptLabel(attemptsMade + 1, max);
        if (stopped) return "Auto Reconnect stopped";
        if (!ReconnectPolicy.attemptsLeft(attemptsMade, max)) return "Auto Reconnect gave up after " + attemptsMade + " attempts";
        return "Auto Reconnect";
    }

    /** The Stop button: no more automatic attempts from this screen. "Reconnect" still works by hand. */
    public void stopCountdown() {
        stopped = true;
        ticksLeft = -1;
        if (main != null) main.setMessage(Component.literal(label()));
        if (screen != null) layout(screen);
    }

    /** Joins the remembered server now through vanilla's connect screen. */
    public void reconnectNow(Minecraft client) {
        if (!hasTarget()) return;
        Screen parent = screen == null ? null : ((DisconnectedScreenAccessor) screen).donut$parent();
        if (parent == null) parent = new JoinMultiplayerScreen(new TitleScreen());
        attemptsMade++;
        clearScreen();
        ownConnect = true;
        try {
            ConnectScreen.startConnecting(parent, client, address, data, false, null);
        } finally {
            ownConnect = false;
        }
    }

    private void clearScreen() {
        screen = null;
        ticksLeft = -1;
        main = stop = null;
        finalReason = null;
    }
}
