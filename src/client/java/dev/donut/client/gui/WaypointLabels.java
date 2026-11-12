package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.mixin.BaseFinderProjectionAccessor;
import dev.donut.client.waypoints.WaypointController;
import dev.donut.client.waypoints.Waypoints;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Screen-space waypoint labels. The view-projection is rebuilt from the camera each frame (same FOV invoker as the
 * Base Finder card), so no render hook is needed. It includes vanilla's hurt tilt and view bobbing (the
 * {@code GameRenderer#bobHurt}/{@code #bobView} transforms), so labels stay on their beams while walking.
 */
public final class WaypointLabels {
    public static final int MAX_LABELS = 64;
    private static final float LABEL_LIFT = 0.75f, PI = (float) Math.PI;

    private WaypointLabels() { }

    public static void render(GuiGraphics g, ClientConfig config) {
        Minecraft mc = Minecraft.getInstance();
        var settings = config.waypoints;
        if (settings == null || !settings.enabled || !settings.labels || !config.showHud || mc.options.hideGui
                || mc.screen != null || mc.player == null || mc.level == null) return;
        WaypointController controller = WaypointController.get();
        if (controller == null) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;
        Vec3 eye = camera.position();
        double range = settings.labelRange;
        List<Waypoints.Shown> shown = Waypoints.nearestFirst(controller.shown(), eye);
        if (shown.isEmpty()) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fov = ((BaseFinderProjectionAccessor) mc.gameRenderer).donut$worldFov(camera, partial, true);
        Matrix4f viewProjection = viewProjection(mc.gameRenderer.getProjectionMatrix(fov), cameraBob(mc, camera.getPartialTickTime()),
                camera.rotation());
        int width = g.guiWidth(), height = g.guiHeight();
        int alpha = Math.max(40, settings.opacity);
        int count = Math.min(shown.size(), MAX_LABELS);
        float guiScale = (float) mc.getWindow().getGuiScale(), s = HudPanel.scale(config);
        float previousFont = WorkspacePaint.pushScale(s);
        g.pose().pushMatrix();
        try {
            // Labels share the HUD panels' window-relative size instead of growing with the GUI scale.
            g.pose().scale(1 / guiScale, 1 / guiScale);
            // Farthest first so nearer labels are painted on top.
            for (int i = count - 1; i >= 0; i--) {
                var point = shown.get(i);
                Vec3 center = point.center();
                if (center.distanceToSqr(eye) > range * range) continue;
                float[] anchor = project(viewProjection, center.add(0, LABEL_LIFT, 0).subtract(eye), width, height);
                if (anchor == null) continue;
                String text = label(point, mc.player.position(), settings.showDistance);
                draw(g, mc, text, anchor[0] * guiScale, anchor[1] * guiScale, s, point.waypoint().color, alpha);
            }
        } finally {
            g.pose().popMatrix();
            WorkspacePaint.popScale(previousFont);
        }
    }

    /** "name · 123m", with " (Overworld)" style suffix for waypoints shown from the linked dimension. */
    static String label(Waypoints.Shown point, Vec3 player, boolean showDistance) {
        StringBuilder text = new StringBuilder(point.waypoint().name);
        if (showDistance && player != null)
            text.append(" · ").append(String.format(Locale.ROOT, "%.0fm", Math.sqrt(point.distanceSq(player))));
        if (point.linked()) text.append(" (").append(Waypoints.dimensionLabel(point.waypoint().dimension)).append(')');
        return text.toString();
    }

    private static void draw(GuiGraphics g, Minecraft mc, String text, float x, float y, float s, int rgb, int alpha) {
        var font = mc.font;
        float windowW = mc.getWindow().getWidth(), windowH = mc.getWindow().getHeight();
        String clipped = text;
        while (WorkspacePaint.smallWidth(font, clipped) > windowW / 3 && clipped.length() > 4) clipped = clipped.substring(0, clipped.length() - 2);
        float pad = 5 * s, h = 14 * s, swatch = 5 * s;
        float w = pad * 2 + swatch + 4 * s + WorkspacePaint.smallWidth(font, clipped);
        float left = Math.clamp(x - w / 2, 2, Math.max(2, windowW - w - 2)), top = Math.clamp(y - h - 4 * s, 2, Math.max(2, windowH - h - 2));
        UiPaint.round(g, left, top, w, h, 3 * s, Math.round(alpha * 0.8f) << 24 | 0x0D1015);
        UiPaint.round(g, left + pad, top + (h - swatch) / 2, swatch, swatch, 1.5f * s, alpha << 24 | rgb & 0xFFFFFF);
        UiPaint.round(g, x - 1.5f * s, y - 1.5f * s, 3 * s, 3 * s, 1.5f * s, alpha << 24 | rgb & 0xFFFFFF);
        WorkspacePaint.smallText(g, font, clipped, left + pad + swatch + 4 * s, WorkspacePaint.centered(top, h, WorkspacePaint.SMALL_SIZE), Math.max(alpha, 160) << 24 | 0xF2F4F7);
    }

    /** Projection times the camera's view rotation, without bobbing; positions must be camera-relative. */
    static Matrix4f viewProjection(Matrix4f projection, Quaternionf cameraRotation) {
        return viewProjection(projection, new Matrix4f(), cameraRotation);
    }

    /**
     * Projection, then the hurt/bob pose, then the camera's view rotation: the same order vanilla's
     * {@code GameRenderer#renderLevel} uses for the world. Positions must be camera-relative.
     */
    static Matrix4f viewProjection(Matrix4f projection, Matrix4f bobPose, Quaternionf cameraRotation) {
        Matrix4f view = new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf()));
        return new Matrix4f(projection).mul(bobPose).mul(view);
    }

    /**
     * The hurt tilt and view bob vanilla applies to the world this frame, read from the camera entity and the
     * "View Bobbing" and "Damage Tilt" options. Identity when neither applies.
     */
    public static Matrix4f cameraBob(Minecraft mc, float partialTick) {
        Entity entity = mc.getCameraEntity();
        float hurt = -1, hurtDir = 0, death = -1;
        int hurtDuration = 0;
        if (entity instanceof LivingEntity living) {
            hurt = living.hurtTime - partialTick;
            hurtDuration = living.hurtDuration;
            hurtDir = living.getHurtDir();
            if (living.isDeadOrDying()) death = Math.min(living.deathTime + partialTick, 20);
        }
        boolean bobbing = mc.options.bobView().get() && entity instanceof AbstractClientPlayer;
        float walk = 0, bob = 0;
        if (bobbing) {
            var state = ((AbstractClientPlayer) entity).avatarState();
            walk = state.getBackwardsInterpolatedWalkDistance(partialTick);
            bob = state.getInterpolatedBob(partialTick);
        }
        return bobPose(hurt, hurtDuration, hurtDir, mc.options.damageTiltStrength().get(), death, bobbing, walk, bob);
    }

    /**
     * Pure version of vanilla's {@code bobHurt} followed by {@code bobView}.
     *
     * @param hurt         {@code hurtTime - partialTick}; negative when not hurt
     * @param hurtDuration the entity's {@code hurtDuration}
     * @param hurtDir      the entity's hurt direction in degrees
     * @param damageTilt   the "Damage Tilt" option, 0..1
     * @param death        {@code min(deathTime + partialTick, 20)} while dying, otherwise negative
     * @param bobbing      the "View Bobbing" option is on and the camera entity is a player
     * @param walk         backwards-interpolated walk distance
     * @param bob          interpolated bob amount
     */
    static Matrix4f bobPose(float hurt, int hurtDuration, float hurtDir, double damageTilt, float death,
            boolean bobbing, float walk, float bob) {
        Matrix4f pose = new Matrix4f();
        if (death >= 0) pose.rotate(new Quaternionf().rotationZ((40 - 8000 / (death + 200)) * Mth.DEG_TO_RAD));
        if (hurt >= 0 && hurtDuration > 0) {
            float t = hurt / hurtDuration;
            t = Mth.sin(t * t * t * t * PI);
            pose.rotate(new Quaternionf().rotationY(-hurtDir * Mth.DEG_TO_RAD));
            pose.rotate(new Quaternionf().rotationZ((float) (-t * 14.0 * damageTilt) * Mth.DEG_TO_RAD));
            pose.rotate(new Quaternionf().rotationY(hurtDir * Mth.DEG_TO_RAD));
        }
        if (bobbing) {
            float swing = Mth.sin(walk * PI);
            pose.translate(swing * bob * 0.5f, -Math.abs(Mth.cos(walk * PI) * bob), 0);
            pose.rotate(new Quaternionf().rotationZ(swing * bob * 3 * Mth.DEG_TO_RAD));
            pose.rotate(new Quaternionf().rotationX(Math.abs(Mth.cos(walk * PI - 0.2f) * bob) * 5 * Mth.DEG_TO_RAD));
        }
        return pose;
    }

    /** GUI coordinates of a camera-relative point, or {@code null} when it is behind the camera or far off-screen. */
    static float[] project(Matrix4f viewProjection, Vec3 relative, float width, float height) {
        var clip = viewProjection.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1));
        if (!Float.isFinite(clip.w) || clip.w <= 0.05f) return null;
        float x = (clip.x / clip.w + 1) * .5f * width;
        float y = (1 - clip.y / clip.w) * .5f * height;
        return Float.isFinite(x) && Float.isFinite(y) && x >= -50 && x <= width + 50 && y >= -50 && y <= height + 50
                ? new float[] {x, y} : null;
    }
}
