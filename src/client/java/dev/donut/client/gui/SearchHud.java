package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.search.SearchBlocks;
import dev.donut.client.search.SearchScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bottom-left count and nearest-block panel for Search; stacks with the other bottom-left panels. */
public final class SearchHud {
    private static final int MAX_ROWS = 8;

    private SearchHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, SearchScanner scanner) {
        Minecraft client = Minecraft.getInstance();
        var settings = config.search;
        if (scanner == null || !settings.enabled || !settings.showHud || !config.showHud || client.options.hideGui
                || client.player == null || client.screen != null) return;
        var snapshot = scanner.snapshot();
        String scope = snapshot.total() + " in " + settings.range + "m" + (snapshot.limited() ? " (nearest " + snapshot.shownBlocks() + ")" : "");
        var panel = HudPanel.of("Search", scope);
        if (settings.blocks.isEmpty()) {
            panel.text("No blocks chosen", HudPanel.MUTED);
            panel.draw(graphics, config, HudPanel.Anchor.BOTTOM_LEFT);
            return;
        }
        int rows = 0;
        for (var entry : settings.blocks) {
            int count = snapshot.count(entry.id);
            if (count == 0) continue;
            if (rows++ >= MAX_ROWS) continue;
            panel.swatch(settings.color(entry.id), SearchBlocks.displayName(entry.id), String.valueOf(count));
        }
        if (rows > MAX_ROWS) panel.text("+" + (rows - MAX_ROWS) + " more types", HudPanel.MUTED);
        if (scanner.pendingChunks() > 0) panel.footerText("Scanning " + scanner.pendingChunks() + " chunks", HudPanel.MUTED);
        var nearest = snapshot.nearest();
        if (nearest != null) {
            var pos = nearest.nearest();
            int distance = (int) Math.round(Math.sqrt(net.minecraft.world.phys.Vec3.atCenterOf(pos).distanceToSqr(client.player.getEyePosition())));
            panel.footer(settings.color(nearest.id()), "Nearest: " + SearchBlocks.displayName(nearest.id()),
                    distance + "m  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        } else if (scanner.pendingChunks() == 0) panel.footerText("None in range", HudPanel.MUTED);
        panel.draw(graphics, config, HudPanel.Anchor.BOTTOM_LEFT);
    }
}
