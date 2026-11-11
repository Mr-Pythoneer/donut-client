package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.freecam.FreecamController;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Small centred Freecam readout above the hotbar: speed after changes, plus follow/look-at status. */
public final class FreecamHud {
    private FreecamHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, FreecamController freecam) {
        Minecraft client = Minecraft.getInstance();
        var settings = config.freecam;
        if (freecam == null || !freecam.isActive() || !settings.speedReadout || !config.showHud
                || client.options.hideGui || client.player == null || client.screen != null) return;
        var lines = new ArrayList<String>();
        float speedAlpha = freecam.readoutAlpha();
        if (speedAlpha > 0.05f) lines.add(speedLine(settings.speed, client.options.keySprint.isDown() ? settings.boostMultiplier : 1));
        String status = status(freecam, settings.followNearestPlayer, settings.lookAtTarget, settings.followRange);
        if (status != null) lines.add(status);
        if (lines.isEmpty()) return;
        // Status lines stay visible; only a speed-only readout fades.
        float alpha = status != null ? 1 : speedAlpha;
        int w = lines.stream().mapToInt(client.font::width).max().orElse(60) + 16;
        int h = lines.size() * 11 + 8;
        int x = (graphics.guiWidth() - w) / 2, y = graphics.guiHeight() - 72 - h;
        UiPaint.round(graphics, x, y, w, h, 5, alphaColor(0xC0141C27, alpha));
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 && speedAlpha > 0.05f ? alphaColor(0xFFE5EFF9, speedAlpha) : 0xFFB3C3D3;
            graphics.drawCenteredString(client.font, lines.get(i), graphics.guiWidth() / 2, y + 5 + i * 11, color);
        }
    }

    static String speedLine(double speed, double boost) {
        String line = String.format(Locale.ROOT, "Freecam %.2f b/t", speed);
        return boost > 1 ? line + String.format(Locale.ROOT, " (x%.1f boost)", boost) : line;
    }

    private static String status(FreecamController freecam, boolean follow, boolean lookAt, int range) {
        var parts = new ArrayList<String>();
        if (follow) {
            var target = freecam.followTarget();
            parts.add(target == null ? "Follow: no player within " + range + "m"
                    : "Following " + target.getName().getString() + " (" + Math.round(target.position().distanceTo(freecam.position())) + "m)");
        }
        if (lookAt) {
            var t = freecam.settings();
            parts.add("Looking at " + t.targetX + ", " + t.targetY + ", " + t.targetZ);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private static int alphaColor(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0, 1));
        return a << 24 | (argb & 0xFFFFFF);
    }
}
