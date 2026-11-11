package dev.donut.client.gui;

import dev.donut.client.blockesp.BlockEspController;
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
        var panel = HudPanel.of("BlockESP", "Predicted");
        panel.text(controller.status(), HudPanel.MUTED);
        for (var kind : OreMarker.Kind.values()) {
            if (kind == OreMarker.Kind.EMERALD ? !settings.emerald : !settings.coal) continue;
            var hits = controller.snapshot().stream().filter(marker -> marker.kind() == kind).toList();
            String value = String.valueOf(hits.size());
            if (!hits.isEmpty()) {
                var pos = hits.getFirst().pos();
                int distance = (int) Math.round(Math.sqrt(pos.distToCenterSqr(client.player.position())));
                value += "  nearest " + distance + "m  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            }
            panel.swatch(kind == OreMarker.Kind.EMERALD ? settings.emeraldColor : settings.coalColor,
                    kind == OreMarker.Kind.EMERALD ? "Emerald" : "Coal", value);
        }
        panel.draw(graphics, config, HudPanel.Anchor.BOTTOM_LEFT);
    }
}
