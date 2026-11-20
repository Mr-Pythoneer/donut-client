/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.freelook;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.FreelookSettings;
import dev.donut.client.freecam.FreecamController;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Orbit the camera around your player without turning the player. Only the rendered camera angles change (see
 * {@code FreelookCameraMixin}); mouse turns are routed here instead of {@code LocalPlayer.turn}, so the player's
 * yaw, pitch, movement direction and every packet stay exactly as they were. Freecam wins: Freelook is inactive while
 * Freecam is on.
 */
public final class FreelookController {
    private static FreelookController instance;
    private final ClientConfig config;
    private KeyMapping key;
    private boolean active, toggled;
    private float yaw, pitch;
    /** The perspective before Freelook started, and the one Freelook switched to (null when it didn't switch). */
    private CameraType previous, applied;

    public FreelookController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static FreelookController get() { return instance; }
    public static boolean isActive() { return instance != null && instance.active; }

    /** True when the camera for this entity should use Freelook's angles. */
    public static boolean overridesCamera(Entity entity) {
        return isActive() && entity != null && entity == Minecraft.getInstance().player;
    }

    public FreelookSettings settings() { return config.freelook; }
    public void setKey(KeyMapping key) { this.key = key; }
    public KeyMapping key() { return key; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void tick(Minecraft client) {
        FreelookSettings settings = settings();
        boolean pressed = false;
        if (key != null) while (key.consumeClick()) pressed = true;
        boolean available = settings.enabled && key != null && client.player != null && client.level != null
                && client.player.isAlive() && client.getCameraEntity() == client.player && !FreecamController.enabled();
        if (!available) {
            toggled = false;
            if (active) stop(client);
            return;
        }
        boolean want;
        if (settings.toggleMode) {
            // Presses in menus are ignored; an active toggle simply stays where it was.
            if (pressed && client.screen == null) toggled = !toggled;
            want = toggled;
        } else {
            toggled = false;
            // Opening a screen releases every key, which pauses hold mode until the menu closes.
            want = key.isDown() && client.screen == null;
        }
        if (want && !active) start(client);
        else if (!want && active) stop(client);
    }

    private void start(Minecraft client) {
        yaw = FreelookMath.wrap(client.player.getYRot());
        pitch = Math.clamp(client.player.getXRot(), FreelookMath.MIN_PITCH, FreelookMath.MAX_PITCH);
        previous = client.options.getCameraType();
        CameraType target = switch (settings().perspective) {
            case FreelookSettings.FRONT -> CameraType.THIRD_PERSON_FRONT;
            case FreelookSettings.KEEP -> previous;
            default -> CameraType.THIRD_PERSON_BACK;
        };
        applied = target == previous ? null : target;
        if (applied != null) client.options.setCameraType(applied);
        active = true;
    }

    /** Ends Freelook and returns to the previous perspective, unless you picked another one meanwhile (F5). */
    public void stop(Minecraft client) {
        if (applied != null && client != null && client.options.getCameraType() == applied && previous != null)
            client.options.setCameraType(previous);
        applied = null;
        previous = null;
        active = false;
    }

    /** A mouse turn while Freelook is active: moves the camera only. */
    public void turn(double dx, double dy) {
        yaw = FreelookMath.turnYaw(yaw, dx);
        pitch = FreelookMath.turnPitch(pitch, dy, settings().invertPitch);
    }

    /** Third-person distance while active, replacing vanilla's 4 blocks; walls still pull the camera in. */
    public float distance(float vanilla) {
        return active ? FreelookMath.scaleDistance(vanilla, settings().distance) : vanilla;
    }

    public void disconnect() {
        toggled = false;
        if (active) stop(Minecraft.getInstance());
    }
}
