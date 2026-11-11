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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Screen-space waypoint labels. The view-projection is rebuilt from the camera each frame (same FOV invoker as the
 * Base Finder card), so no render hook is needed. View bobbing is not applied, so labels may sway slightly while walking.
 */
public final class WaypointLabels {
    public static final int MAX_LABELS = 64;
    private static final float LABEL_LIFT = 0.75f;

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
        Matrix4f viewProjection = viewProjection(mc.gameRenderer.getProjectionMatrix(fov), camera.rotation());
        int width = g.guiWidth(), height = g.guiHeight();
        int alpha = Math.max(40, settings.opacity);
        int count = Math.min(shown.size(), MAX_LABELS);
        // Farthest first so nearer labels are painted on top.
        for (int i = count - 1; i >= 0; i--) {
            var point = shown.get(i);
            Vec3 center = point.center();
            if (center.distanceToSqr(eye) > range * range) continue;
            float[] anchor = project(viewProjection, center.add(0, LABEL_LIFT, 0).subtract(eye), width, height);
            if (anchor == null) continue;
            String text = label(point, mc.player.position(), settings.showDistance);
            draw(g, mc, text, anchor[0], anchor[1], point.waypoint().color, alpha);
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

    private static void draw(GuiGraphics g, Minecraft mc, String text, float x, float y, int rgb, int alpha) {
        var font = mc.font;
        String clipped = font.plainSubstrByWidth(text, Math.max(40, g.guiWidth() / 3));
        int w = font.width(clipped) + 8, h = 12;
        int left = Math.round(x - w / 2f), top = Math.round(y - h - 3);
        left = Math.clamp(left, 2, Math.max(2, g.guiWidth() - w - 2));
        top = Math.clamp(top, 2, Math.max(2, g.guiHeight() - h - 2));
        UiPaint.round(g, left, top, w, h, 4, Math.round(alpha * 0.75f) << 24 | 0x141C27);
        UiPaint.round(g, x - 1.5f, y - 1.5f, 3, 3, 1.5f, alpha << 24 | rgb & 0xFFFFFF);
        g.drawString(font, clipped, left + 4, top + 2, alpha << 24 | rgb & 0xFFFFFF, false);
    }

    /** Projection times the camera's view rotation, as vanilla builds it; positions must be camera-relative. */
    static Matrix4f viewProjection(Matrix4f projection, Quaternionf cameraRotation) {
        Matrix4f view = new Matrix4f().rotation(cameraRotation.conjugate(new Quaternionf()));
        return new Matrix4f(projection).mul(view);
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
