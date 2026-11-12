package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.mixin.BaseFinderProjectionAccessor;
import dev.donut.client.playeresp.PlayerEspController;
import dev.donut.client.playeresp.PlayerEspTargets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Screen-space Player ESP nametags: name, health, distance and equipment icons above each shown entity. Uses the
 * same projection as {@link WaypointLabels} (including hurt tilt and view bobbing) so tags stay on the entity.
 */
public final class PlayerEspLabels {
    /** Above vanilla's own name tag (which sits about 0.5 above the head), so both stay readable. */
    private static final float LIFT = 0.8f;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private PlayerEspLabels() { }

    public static void render(GuiGraphics g, ClientConfig config) {
        Minecraft mc = Minecraft.getInstance();
        PlayerEspController controller = PlayerEspController.get();
        var settings = config.playerEsp;
        if (controller == null || settings == null || !settings.enabled || !settings.nametags || !config.showHud
                || mc.options.hideGui || mc.screen != null || mc.player == null || mc.level == null) {
            if (controller != null && !controller.lastTags().isEmpty()) controller.recordTags(List.of());
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;
        List<PlayerEspController.Target> targets = controller.targets();
        List<String> drawn = new ArrayList<>();
        if (targets.isEmpty()) { controller.recordTags(drawn); return; }
        Vec3 eye = camera.position();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fov = ((BaseFinderProjectionAccessor) mc.gameRenderer).donut$worldFov(camera, partial, true);
        Matrix4f viewProjection = WaypointLabels.viewProjection(mc.gameRenderer.getProjectionMatrix(fov),
                WaypointLabels.cameraBob(mc, camera.getPartialTickTime()), camera.rotation());
        int width = g.guiWidth(), height = g.guiHeight();
        int alpha = Math.max(60, settings.opacity);
        float guiScale = (float) mc.getWindow().getGuiScale(), s = HudPanel.scale(config);
        float previousFont = WorkspacePaint.pushScale(s);
        g.pose().pushMatrix();
        try {
            g.pose().scale(1 / guiScale, 1 / guiScale);
            // Farthest first so nearer tags paint on top.
            for (int i = targets.size() - 1; i >= 0; i--) {
                var target = targets.get(i);
                var entity = target.entity();
                if (entity.isRemoved()) continue;
                var bounds = PlayerEspController.renderBounds(entity, partial);
                if (bounds.contains(eye)) continue;
                Vec3 top = new Vec3((bounds.minX + bounds.maxX) / 2, bounds.maxY + LIFT, (bounds.minZ + bounds.maxZ) / 2);
                float[] anchor = WaypointLabels.project(viewProjection, top.subtract(eye), width, height);
                if (anchor == null) continue;
                String health = "";
                int healthColor = 0;
                List<ItemStack> icons = new ArrayList<>();
                if (entity instanceof LivingEntity living) {
                    health = PlayerEspTargets.health(living.getHealth(), living.getAbsorptionAmount(), settings.healthAsHearts);
                    healthColor = PlayerEspTargets.healthColor(living.getHealth(), living.getMaxHealth());
                    if (settings.showHeldItem && !living.getMainHandItem().isEmpty()) icons.add(living.getMainHandItem());
                    if (settings.showArmor) for (EquipmentSlot slot : ARMOR) {
                        ItemStack stack = living.getItemBySlot(slot);
                        if (!stack.isEmpty()) icons.add(stack);
                    }
                }
                String name = controller.name(target);
                double distance = Math.sqrt(entity.getBoundingBox().getCenter().distanceToSqr(eye));
                int rgb = controller.color(target, eye);
                draw(g, mc, name, settings.showHealth ? health : "", healthColor, settings.showDistance ? Math.round(distance) + "m" : "",
                        icons, anchor[0] * guiScale, anchor[1] * guiScale, s, rgb, alpha);
                drawn.add(PlayerEspTargets.nametag(name, health, distance, settings));
            }
        } finally {
            g.pose().popMatrix();
            WorkspacePaint.popScale(previousFont);
        }
        controller.recordTags(drawn);
    }

    private static void draw(GuiGraphics g, Minecraft mc, String name, String health, int healthColor, String distance,
                             List<ItemStack> icons, float x, float y, float s, int rgb, int alpha) {
        var font = mc.font;
        float windowW = mc.getWindow().getWidth(), windowH = mc.getWindow().getHeight();
        float pad = 5 * s, h = 14 * s, gap = 6 * s, icon = 12 * s;
        float nameW = WorkspacePaint.smallWidth(font, name), healthW = health.isEmpty() ? 0 : WorkspacePaint.smallWidth(font, health),
                distanceW = distance.isEmpty() ? 0 : WorkspacePaint.smallWidth(font, distance);
        float w = pad * 2 + nameW + (healthW > 0 ? gap + healthW : 0) + (distanceW > 0 ? gap + distanceW : 0);
        float left = Math.clamp(x - w / 2, 2, Math.max(2, windowW - w - 2)), top = Math.clamp(y - h, 2, Math.max(2, windowH - h - 2));
        int background = Math.round(alpha * .8f) << 24 | 0x0D1015, accent = alpha << 24 | rgb & 0xFFFFFF;
        UiPaint.round(g, left, top, w, h, 3 * s, background);
        UiPaint.round(g, left, top + h - 1.5f * s, w, 1.5f * s, .75f * s, accent);
        float textY = WorkspacePaint.centered(top, h - s, WorkspacePaint.SMALL_SIZE), cursor = left + pad;
        WorkspacePaint.smallText(g, font, name, cursor, textY, Math.max(alpha, 170) << 24 | rgb & 0xFFFFFF);
        cursor += nameW + gap;
        if (healthW > 0) {
            WorkspacePaint.smallText(g, font, health, cursor, textY, Math.max(alpha, 170) << 24 | healthColor & 0xFFFFFF);
            cursor += healthW + gap;
        }
        if (distanceW > 0) WorkspacePaint.smallText(g, font, distance, cursor, textY, Math.max(alpha, 170) << 24 | 0xC7CCD6);
        if (!icons.isEmpty()) {
            float rowW = icons.size() * (icon + 2 * s) - 2 * s, iconX = left + (w - rowW) / 2, iconY = top - icon - 2 * s;
            for (ItemStack stack : icons) {
                g.pose().pushMatrix();
                g.pose().translate(iconX, iconY);
                g.pose().scale(icon / 16, icon / 16);
                g.renderItem(stack, 0, 0);
                g.pose().popMatrix();
                iconX += icon + 2 * s;
            }
        }
    }
}
