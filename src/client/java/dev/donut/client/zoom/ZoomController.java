package dev.donut.client.zoom;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ZoomSettings;
import dev.donut.client.freecam.FreecamController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Hold-to-zoom. Changes only the rendered field of view and local mouse sensitivity; sends nothing. */
public final class ZoomController {
    private static ZoomController instance;
    private final ClientConfig config;
    private final Runnable save;
    private KeyMapping key;
    private boolean holding;
    private double sessionFactor = Double.NaN;
    private double current = 1, previous = 1;
    private boolean scrollDirty;

    public ZoomController(ClientConfig config, Runnable save) {
        this.config = config;
        this.save = save == null ? () -> { } : save;
        instance = this;
    }

    public static ZoomController get() { return instance; }
    public ZoomSettings settings() { return config.zoom; }
    public void setKey(KeyMapping key) { this.key = key; }
    public boolean holding() { return holding; }
    /** The zoom factor being approached while held (scroll-adjusted). */
    public double targetFactor() { return Double.isNaN(sessionFactor) ? settings().factor : sessionFactor; }
    public double currentFactor() { return current; }

    public void tick(Minecraft client) {
        boolean wasHolding = holding;
        holding = settings().enabled && key != null && key.isDown() && client.screen == null && client.player != null
                && !FreecamController.enabled();
        if (!holding && wasHolding) {
            if (scrollDirty && settings().rememberScroll) { settings().factor = sessionFactor; save.run(); }
            sessionFactor = Double.NaN;
            scrollDirty = false;
        }
        previous = current;
        double target = holding ? targetFactor() : 1;
        current = ZoomMath.step(current, target, settings().smooth);
    }

    /** Multiplier applied to the world field of view for this frame (1 = no zoom). */
    public double fovMultiplier(float partialTick) {
        double factor = Mth.lerp(Math.clamp(partialTick, 0, 1), previous, current);
        return factor <= 1.0001 ? 1 : 1 / factor;
    }

    /** Mouse sensitivity scale while zoomed. */
    public double sensitivityScale() {
        return settings().reduceSensitivity && current > 1.0001 ? 1 / current : 1;
    }

    /** Scroll while holding the zoom key adjusts the zoom; returns true when the scroll was used. */
    public boolean scroll(double notches) {
        if (!holding || !settings().scrollAdjusts || notches == 0 || !Double.isFinite(notches)) return false;
        sessionFactor = ZoomMath.scroll(targetFactor(), notches);
        scrollDirty = true;
        return true;
    }

    public void reset() {
        holding = false;
        sessionFactor = Double.NaN;
        scrollDirty = false;
        current = previous = 1;
    }
}
