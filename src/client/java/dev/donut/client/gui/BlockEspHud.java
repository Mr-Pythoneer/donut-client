package dev.donut.client.gui;

import dev.donut.client.blockesp.BlockEspController;
import dev.donut.client.blockesp.OreConfidence;
import dev.donut.client.blockesp.OreMarker;
import dev.donut.client.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class BlockEspHud {
    private BlockEspHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, BlockEspController controller) {
        Minecraft client = Minecraft.getInstance();
        var settings = config.blockEsp;
        if (!settings.enabled || !settings.showHud || !config.showHud || client.options.hideGui || client.player == null || client.screen != null) return;
        String status = controller.status();
        boolean predicting = status.startsWith("Predicting"), ready = status.startsWith("Ready");
        int done = controller.completedChunks(), total = controller.totalChunks();
        var panel = HudPanel.of("BlockESP", ready ? "Ready" : predicting ? done + " / " + total + " chunks" : "Predicted");
        if (predicting && total > 0) panel.progress(done / (double) total, 0x7FCB8E);
        if (!predicting && !ready) panel.text(status, status.startsWith("Prediction failed") ? HudPanel.DANGER : HudPanel.MUTED);
        int rows = 0;
        for (var kind : OreMarker.Kind.values()) if (settings.oreEnabled(kind)) rows++;
        // Coordinates per row fit a single column; with many ores they move to one "Nearest" footer line instead.
        boolean wide = rows <= 4;
        dev.donut.client.blockesp.OrePrediction closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (var kind : OreMarker.Kind.values()) {
            if (!settings.oreEnabled(kind)) continue;
            var nearest = controller.nearest(kind);
            String value = String.valueOf(controller.count(kind));
            if (nearest != null) {
                var pos = nearest.marker().pos();
                double distance = Math.sqrt(pos.distToCenterSqr(client.player.position()));
                value += "  " + Math.round(distance) + "m " + nearest.label();
                if (wide) value += "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
                if (distance < closestDistance) { closestDistance = distance; closest = nearest; }
            }
            panel.swatch(settings.oreColor(kind), kind.label, value);
        }
        if (rows > 4) panel.columns(2);
        if (!wide && closest != null) {
            var pos = closest.marker().pos();
            panel.footer(settings.oreColor(closest.marker().kind()), "Nearest " + closest.marker().kind().label,
                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        }
        if (predicting || ready) {
            var verdict = controller.seedVerdict();
            int color = switch (verdict) {
                case MATCH -> HudPanel.GOOD;
                case PARTIAL -> HudPanel.WARNING;
                case MISMATCH -> HudPanel.DANGER;
                case NOT_ENOUGH_DATA -> HudPanel.MUTED;
            };
            panel.footerText(controller.seedMatchText(), color);
            if (verdict != OreConfidence.Verdict.NOT_ENOUGH_DATA && verdict != OreConfidence.Verdict.MATCH)
                panel.footerText(OreConfidence.verdictText(verdict), color);
            if (verdict == OreConfidence.Verdict.MISMATCH) panel.accent(HudPanel.DANGER);
        }
        panel.draw(graphics, config, HudPanel.Anchor.BOTTOM_LEFT);
    }
}
