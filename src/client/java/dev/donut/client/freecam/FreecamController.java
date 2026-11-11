package dev.donut.client.freecam;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.config.FreecamSettings;
import java.lang.ref.WeakReference;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Camera-only flight. Never writes a player transform, abilities, gamemode or movement packet. */
public final class FreecamController {
    /** How long the speed readout stays fully visible after a change, then fades. */
    public static final long READOUT_MILLIS = 1500, READOUT_FADE_MILLIS = 400;
    private static FreecamController instance;
    private final ClientConfig config;
    private final Runnable save;
    private final ClientInput neutralInput = new ClientInput() {
        @Override public void tick() { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
        // Vanilla auto-jump can call this after input.tick(). It must stay neutral too.
        @Override public void makeJump() { }
    };
    private LocalPlayer player;
    private ClientLevel level;
    private ClientInput savedInput;
    private boolean active;
    private Vec3 position = Vec3.ZERO;
    private Vec3 previousPosition = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private float yaw;
    private float pitch;
    private float previousYaw;
    private float previousPitch;
    // Camera-only memory of the last pose for "Resume last position". Never applied to the player.
    private Vec3 lastPosition;
    private float lastYaw, lastPitch;
    private WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    private Player followTarget;
    private Vec3 followOffset = Vec3.ZERO;
    private boolean speedDirty;
    private long readoutShownAt = Long.MIN_VALUE / 2;

    public FreecamController(ClientConfig config) { this(config, () -> { }); }

    public FreecamController(ClientConfig config, Runnable save) {
        this.config = config;
        this.save = save == null ? () -> { } : save;
        instance = this;
    }

    public static FreecamController getInstance() { return instance; }
    public static boolean enabled() { return instance != null && instance.isActive(); }
    public FreecamSettings settings() { return config.freecam; }
    public Vec3 position() { return position; }
    public Vec3 velocity() { return velocity; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Player followTarget() { return followTarget; }

    public boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        return active && client.player == player && client.level == level
                && player != null && player.isAlive() && !player.isRemoved()
                && client.getCameraEntity() == player;
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) { disable(); return; }
        if (isActive()) { settings().enabled = true; return; }
        disable();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.player.isAlive()
                || client.getCameraEntity() != client.player) return;
        // Releasing a charged bow/trident would be a gameplay action. Let the user finish first.
        if (client.player.isUsingItem()) {
            client.player.displayClientMessage(Component.literal("Release the item before enabling Freecam."), true);
            return;
        }
        // Return builder steering before Freecam saves the player's normal input.
        if (AutoBuilderController.get().isRunning()) AutoBuilderController.get().pause();
        settings().sanitize();
        player = client.player;
        level = client.level;
        savedInput = player.input;
        neutralInput.tick();
        player.input = neutralInput;
        if (settings().resumeLastPosition && lastPosition != null && lastLevel.get() == client.level) {
            position = lastPosition;
            yaw = lastYaw;
            pitch = lastPitch;
        } else {
            position = player.getEyePosition();
            yaw = player.getYRot();
            pitch = player.getXRot();
        }
        previousPosition = position;
        previousYaw = yaw;
        previousPitch = pitch;
        velocity = Vec3.ZERO;
        followTarget = null;
        if (client.gameMode != null) client.gameMode.stopDestroyBlock();
        suppressActions(client);
        active = true;
        settings().enabled = true;
        showReadout();
    }

    public void disable() {
        if (player != null && player.input == neutralInput && savedInput != null) player.input = savedInput;
        if (active) {
            suppressActions(Minecraft.getInstance());
            lastPosition = position;
            lastYaw = yaw;
            lastPitch = pitch;
            lastLevel = new WeakReference<>(level);
        }
        active = false;
        settings().enabled = false;
        player = null;
        level = null;
        savedInput = null;
        velocity = Vec3.ZERO;
        followTarget = null;
        if (speedDirty) {
            speedDirty = false;
            save.run();
        }
    }

    /** Disconnect cleanup: also forgets the remembered pose, which belongs to the old world. */
    public void disconnect() {
        disable();
        lastPosition = null;
        lastLevel = new WeakReference<>(null);
    }

    public void tick(Minecraft client) {
        if (active && !isActive()) { disable(); return; }
        if (settings().enabled != active) setEnabled(settings().enabled);
        if (!isActive()) return;
        suppressActions(client);
        previousPosition = position;
        previousYaw = yaw;
        previousPitch = pitch;
        if (!settings().followNearestPlayer) followTarget = null;
        if (client.screen != null || client.isPaused()) { velocity = Vec3.ZERO; return; }
        var options = client.options;
        double forward = axis(options.keyUp, options.keyDown);
        double strafe = axis(options.keyRight, options.keyLeft);
        double vertical = axis(options.keyJump, options.keyShift);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        double radians = Math.toRadians(yaw);
        Vec3 motion = look.scale(forward).add(-Math.cos(radians) * strafe, vertical, -Math.sin(radians) * strafe);
        Vec3 target = Vec3.ZERO;
        if (motion.lengthSqr() > 1.0e-8) {
            double speed = settings().speed * (options.keySprint.isDown() ? settings().boostMultiplier : 1);
            target = motion.normalize().scale(speed);
        }
        velocity = FreecamMotion.smoothVelocity(velocity, target, settings().smoothing);
        Player followed = settings().followNearestPlayer ? updateFollowTarget(client) : null;
        // Keep all render matrices finite and within Minecraft's supported horizontal world bounds.
        if (followed != null) {
            followOffset = followOffset.add(velocity);
            position = FreecamMotion.clampToWorld(FreecamMotion.followPosition(followed.position(), followOffset));
        } else if (velocity.lengthSqr() > 1.0e-8) {
            position = FreecamMotion.clampToWorld(position.add(velocity));
        }
        if (settings().lookAtTarget) {
            Vec2 angles = FreecamMotion.lookAngles(position, lookTarget());
            if (angles != null) {
                if (!Float.isNaN(angles.x)) yaw = angles.x;
                pitch = angles.y;
            }
        }
    }

    /** Keeps the current target while it stays valid and in range, otherwise locks onto the nearest one. */
    private Player updateFollowTarget(Minecraft client) {
        double range = settings().followRange;
        if (followTarget != null && (followTarget.isRemoved() || !followTarget.isAlive() || followTarget.level() != client.level
                || followTarget.position().distanceToSqr(position) > (range + 16) * (range + 16))) followTarget = null;
        if (followTarget != null) return followTarget;
        Player nearest = null;
        double best = range * range;
        for (Player candidate : client.level.players()) {
            if (candidate == player || candidate.isRemoved() || !candidate.isAlive()) continue;
            double distance = candidate.position().distanceToSqr(position);
            if (distance <= best) { best = distance; nearest = candidate; }
        }
        if (nearest != null) {
            followTarget = nearest;
            followOffset = position.subtract(nearest.position());
            showReadout();
        }
        return followTarget;
    }

    public Vec3 lookTarget() {
        return new Vec3(settings().targetX + 0.5, settings().targetY + 0.5, settings().targetZ + 0.5);
    }

    public Vec3 interpolatedPosition(float partialTick) {
        return previousPosition.lerp(position, Math.clamp(partialTick, 0, 1));
    }

    /** Look-at rotation changes per tick, so it is interpolated; mouse rotation is already per frame. */
    public float renderYaw(float partialTick) {
        if (!settings().lookAtTarget) return yaw;
        return Mth.rotLerp(Math.clamp(partialTick, 0, 1), previousYaw, yaw);
    }

    public float renderPitch(float partialTick) {
        if (!settings().lookAtTarget) return pitch;
        return Mth.lerp(Math.clamp(partialTick, 0, 1), previousPitch, pitch);
    }

    /** Arguments have already been sensitivity-scaled by Minecraft's mouse handler. */
    public void turn(double dx, double dy) {
        if (!isActive() || Minecraft.getInstance().screen != null || settings().lookAtTarget) return;
        yaw = Mth.wrapDegrees(yaw + (float) (dx * 0.15));
        pitch = Math.clamp(pitch + (float) (dy * 0.15), -90, 90);
    }

    /** Adjusts camera speed from the scroll wheel. Saved when Freecam is disabled. */
    public void scroll(double notches) {
        if (!isActive() || Minecraft.getInstance().screen != null || !settings().scrollAdjustsSpeed) return;
        double next = FreecamMotion.scrollSpeed(settings().speed, notches);
        if (next != settings().speed) {
            settings().speed = next;
            speedDirty = true;
        }
        showReadout();
    }

    public void showReadout() { readoutShownAt = Util.getMillis(); }

    /** 1 while the readout is fresh, fading to 0. */
    public float readoutAlpha() {
        long age = Util.getMillis() - readoutShownAt;
        if (age < READOUT_MILLIS) return 1;
        return Math.clamp(1 - (age - READOUT_MILLIS) / (float) READOUT_FADE_MILLIS, 0, 1);
    }

    private static double axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
    }

    /** Drain action clicks as well as held states so they cannot replay after exiting Freecam. */
    public void suppressActions(Minecraft client) {
        var options = client.options;
        release(options.keyAttack);
        release(options.keyUse);
        release(options.keyPickItem);
        release(options.keyDrop);
        release(options.keySwapOffhand);
        for (KeyMapping slot : options.keyHotbarSlots) release(slot);
    }

    private static void release(KeyMapping key) {
        key.setDown(false);
        while (key.consumeClick()) { }
    }
}
