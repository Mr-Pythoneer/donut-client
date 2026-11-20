/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.playeresp;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.PlayerEspSettings;
import dev.donut.client.playeresp.PlayerEspTargets.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chooses which loaded entities Player ESP shows each tick. It only reads entities the client already has
 * (players the server is tracking for you); it never sends anything.
 */
public final class PlayerEspController {
    private static PlayerEspController instance;
    private final ClientConfig config;
    private List<Target> targets = List.of();
    private int total;
    // What the last frame actually drew, for runtime tests and diagnostics.
    private volatile List<Integer> lastBoxes = List.of();
    private volatile List<String> lastTags = List.of();

    public record Target(Entity entity, Kind kind, boolean friend, double distanceSq) {
        public double distance() { return Math.sqrt(distanceSq); }
    }

    public PlayerEspController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static PlayerEspController get() { return instance; }
    public PlayerEspSettings settings() { return config.playerEsp; }
    public List<Target> targets() { return targets; }
    /** Matching entities in range before the "max shown" cap. */
    public int total() { return total; }
    public List<Integer> lastBoxes() { return lastBoxes; }
    public List<String> lastTags() { return lastTags; }
    public void recordBoxes(List<Integer> ids) { lastBoxes = List.copyOf(ids); }
    public void recordTags(List<String> tags) { lastTags = List.copyOf(tags); }

    public void tick(Minecraft client) {
        var settings = settings();
        if (!settings.enabled || client.level == null || client.player == null) {
            if (!targets.isEmpty() || !lastBoxes.isEmpty() || !lastTags.isEmpty()) clear();
            return;
        }
        Vec3 camera = cameraPosition(client);
        double rangeSq = (double) settings.range * settings.range;
        Entity cameraEntity = client.getCameraEntity();
        List<Target> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || entity.isRemoved()) continue;
            Kind kind = kind(entity);
            if (!PlayerEspTargets.shown(kind, settings)) continue;
            if (!PlayerEspTargets.allowed(entity == client.player, entity == cameraEntity, entity.getBoundingBox().contains(camera))) continue;
            double distanceSq = entity.getBoundingBox().getCenter().distanceToSqr(camera);
            if (distanceSq > rangeSq) continue;
            boolean friend = kind == Kind.PLAYER && settings.isFriend(((Player) entity).getGameProfile().name());
            found.add(new Target(entity, kind, friend, distanceSq));
        }
        found.sort(Comparator.comparingDouble(Target::distanceSq));
        total = found.size();
        targets = List.copyOf(found.subList(0, Math.min(found.size(), settings.maxShown)));
    }

    public static Kind kind(Entity entity) {
        return PlayerEspTargets.classify(entity instanceof Player, entity instanceof Enemy && entity instanceof LivingEntity,
                entity instanceof Mob, entity instanceof ItemEntity);
    }

    public static Vec3 cameraPosition(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera();
        return camera.isInitialized() ? camera.position() : client.player.getEyePosition();
    }

    /** The entity's bounding box at its rendered (interpolated) position this frame. */
    public static AABB renderBounds(Entity entity, float partialTick) {
        Vec3 lerped = entity.getPosition(partialTick);
        return entity.getBoundingBox().move(lerped.subtract(entity.position()));
    }

    /** RGB colour for a target, using its live distance from {@code camera}. */
    public int color(Target target, Vec3 camera) {
        Entity entity = target.entity();
        int team = entity.getTeam() == null ? -1 : entity.getTeamColor();
        return PlayerEspTargets.color(target.kind(), target.friend(), team,
                Math.sqrt(entity.getBoundingBox().getCenter().distanceToSqr(camera)), settings());
    }

    /** Name for the tag, honouring Name Hider when "Respect Name Hider" is on. */
    public String name(Target target) {
        Entity entity = target.entity();
        String name = entity instanceof Player player ? player.getGameProfile().name()
                : entity instanceof ItemEntity item ? item.getItem().getHoverName().getString()
                + (item.getItem().getCount() > 1 ? " x" + item.getItem().getCount() : "")
                : entity.getDisplayName().getString();
        boolean hide = settings().respectNameHider && config.nameHider.enabled;
        return PlayerEspTargets.displayName(target.kind(), name, target.friend(), hide);
    }

    public void clear() {
        targets = List.of();
        total = 0;
        lastBoxes = List.of();
        lastTags = List.of();
    }

    public void disconnect() { clear(); }
}
