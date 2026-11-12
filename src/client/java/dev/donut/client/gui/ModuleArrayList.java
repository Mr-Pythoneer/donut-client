// SPDX-License-Identifier: GPL-3.0-only
// Visuals adapted from Spectre 26.2 HudRenderer/ModuleListRows by 4ldenz.
package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.fly.FlyController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Spectre's connected, width-sorted module rows bound to Donut's active modules. */
public final class ModuleArrayList {
    private static final String[] NAMES = {"Freecam", "AutoBuilder", "Base Finder",
            "Fullbright", "Fly", "Progress Bar", "Name Hider", "Hotbar", "Amethyst Scanner", "Light Debug", "Sound Detector", "BlockESP", "Staff Detector", "Storage ESP", "Coordinates", "Waypoints", "Zoom", "Durability Alert", "Auto Miner",
            null, "Player ESP", "Search", null, null, null, null, null}; // IDs 21-28; null = no module at that ID.
    private static final float[] animation = new float[NAMES.length];
    private static long lastFrame;
    private static List<Row> rows = List.of();
    private ModuleArrayList() { }

    public static void reset() {
        lastFrame = 0;
        java.util.Arrays.fill(animation, 0);
        rows = List.of();
    }

    public static void render(GuiGraphics g, Font font, int ignored, ClientConfig config, boolean freecamActive) {
        if (!config.showHud) { reset(); return; }
        Minecraft client = Minecraft.getInstance();
        float guiScale = client.getWindow().getGuiScale();
        float s = HudPanel.scale(config);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1f / 60 : Math.clamp((now - lastFrame) / 1_000_000_000f, 0, .1f);
        lastFrame = now;
        boolean[] enabled = {freecamActive,
                AutoBuilderController.get().isRunning(), config.chunkFinder.enabled, config.fullbright.enabled,
                FlyController.getInstance() != null && FlyController.getInstance().isActive(),
                config.progressBar.enabled, config.nameHider.enabled, config.hotbar.enabled, config.amethyst.enabled, config.lightDebug.enabled, config.soundDetector.enabled, config.blockEsp.enabled, config.staffDetector.enabled, config.storageEsp.enabled, config.coordinates.enabled, config.waypoints.enabled,
                config.zoom.enabled, config.durabilityAlert.enabled, config.autoMiner.enabled,
                false, config.playerEsp.enabled, config.search.enabled, false, false, false, false, false};
        float k = config.clickGuiStyle.animationSpeed == 0 ? 1 : 1 - (float) Math.exp(-12 * dt * config.clickGuiStyle.animationSpeed);
        float previousFontScale = WorkspacePaint.pushScale(s);
        g.pose().pushMatrix();
        try {
            g.pose().scale(1 / guiScale, 1 / guiScale);
            List<Row> ordered = new ArrayList<>();
            for (int i = 0; i < Math.min(NAMES.length, enabled.length); i++) {
                if (NAMES[i] == null) continue;
                float target = enabled[i] ? 1 : 0;
                animation[i] += (target - animation[i]) * k;
                if (Math.abs(animation[i] - target) < .005f) animation[i] = target;
                if (animation[i] > 0) ordered.add(new Row(i, NAMES[i], WorkspacePaint.smallWidth(font, NAMES[i]), animation[i]));
            }
            ordered.sort(Comparator.comparingDouble((Row row) -> -row.nameWidth).thenComparing(Row::name));
            rows = List.copyOf(ordered);
            float right = client.getWindow().getWidth() - Math.round(HudPanel.MARGIN * s), y = Math.round(HudPanel.MARGIN * s);
            // Row height leaves room for the glyphs' full height so neighbouring rows never overlap.
            float fullHeight = Math.round(HudPanel.ROW * s + 2 * s), radius = HudPanel.RADIUS * s;
            List<Step> steps = new ArrayList<>();
            for (Row row : ordered) {
                float open = row.animation * row.animation * (3 - 2 * row.animation);
                float w = Math.round(HudPanel.PAD_X * 2 * s + row.nameWidth);
                float top = Math.round(y), bottom = Math.round(y + fullHeight * open);
                y += fullHeight * open;
                if (bottom > top) {
                    float slide = Math.round(w * (1 - open));
                    steps.add(new Step(row, right - w + slide, top, right + slide, bottom, Math.min(1, row.animation * 1.5f)));
                }
            }
            drawBackground(g, steps, radius, s);
            for (Step step : steps) {
                float x = Math.round(step.right - HudPanel.PAD_X * s - step.row.nameWidth);
                // Centre in the row's full height; rows that are still opening are clipped by the scissor below.
                float textY = WorkspacePaint.centered(step.bottom - fullHeight, fullHeight, WorkspacePaint.SMALL_SIZE);
                g.enableScissor((int) Math.floor(step.left), (int) step.top, (int) Math.ceil(step.right), (int) step.bottom);
                float alpha = step.row.animation * step.row.animation;
                WorkspacePaint.smallText(g, font, step.row.name, x, textY, scaleAlpha(HudPanel.TITLE, alpha));
                g.disableScissor();
            }
        } finally {
            g.pose().popMatrix();
            WorkspacePaint.popScale(previousFontScale);
        }
    }

    private static void drawBackground(GuiGraphics g, List<Step> steps, float radius, float s) {
        for (int i = 0; i < steps.size(); i++) {
            Step q = steps.get(i), up = i > 0 ? steps.get(i - 1) : null, down = i + 1 < steps.size() ? steps.get(i + 1) : null;
            int color = scaleAlpha(HudPanel.SURFACE, q.alpha);
            SmoothGeometry.corners(g, q.left, q.top, q.width(), q.height(), radius, corners(steps, i), color);
            if (up != null && up.left < q.left - .5f) fillet(g, q.left, q.top, -1, 1, Math.min(radius, Math.min(q.left - up.left, q.height())), color);
            if (up != null && up.right > q.right + .5f) fillet(g, q.right, q.top, 1, 1, Math.min(radius, Math.min(up.right - q.right, q.height())), color);
            if (down != null && down.left < q.left - .5f) fillet(g, q.left, q.bottom, -1, -1, Math.min(radius, Math.min(q.left - down.left, q.height())), color);
            if (down != null && down.right > q.right + .5f) fillet(g, q.right, q.bottom, 1, -1, Math.min(radius, Math.min(down.right - q.right, q.height())), color);
        }
    }

    private static int corners(List<Step> rows, int i) {
        Step q = rows.get(i), up = i > 0 ? rows.get(i - 1) : null, dn = i + 1 < rows.size() ? rows.get(i + 1) : null;
        int mask = 0;
        if (up == null || up.left > q.left + .5f) mask |= 16;
        if (up == null || up.right < q.right - .5f) mask |= 32;
        if (dn == null || dn.left > q.left + .5f) mask |= 64;
        if (dn == null || dn.right < q.right - .5f) mask |= 128;
        return mask;
    }

    private static void fillet(GuiGraphics g, float px, float py, int dx, int dy, float r, int color) {
        if (r < 1) return;
        float cx = px + dx * r, cy = py + dy * r;
        double a0 = dx < 0 ? 0 : Math.PI;
        double a1 = dy > 0 ? (dx < 0 ? -Math.PI / 2 : Math.PI * 1.5) : Math.PI / 2;
        float[] path = new float[20];
        path[0] = px; path[1] = py;
        for (int i = 0; i <= 8; i++) {
            double a = a0 + (a1 - a0) * i / 8;
            path[2 + i * 2] = cx + (float) Math.cos(a) * r;
            path[3 + i * 2] = cy + (float) Math.sin(a) * r;
        }
        SpectreMascotCanvas canvas = new SpectreMascotCanvas(g, 0, 0, 1, 1);
        canvas.polygon(path, color);
        canvas.submit(g);
    }

    private static int scaleAlpha(int color, float alpha) { return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF; }
    private record Row(int module, String name, float nameWidth, float animation) { }
    private record Step(Row row, float left, float top, float right, float bottom, float alpha) {
        float width() { return right - left; }
        float height() { return bottom - top; }
    }
}
