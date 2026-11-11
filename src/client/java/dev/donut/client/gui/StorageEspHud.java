package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.storageesp.StorageEspScanner;
import dev.donut.client.storageesp.StorageKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bottom-left count and nearest-distance panel; stacks above the BlockESP HUD when both are shown. */
public final class StorageEspHud {
    private StorageEspHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, StorageEspScanner scanner) {
        Minecraft client = Minecraft.getInstance();
        var settings = config.storageEsp;
        if (scanner == null || !settings.enabled || !settings.showHud || !config.showHud || client.options.hideGui
                || client.player == null || client.screen != null) return;
        var snapshot = scanner.snapshot();
        String scope = snapshot.total() + " in " + settings.range + "m" + (snapshot.limited() ? " (nearest " + snapshot.targets().size() + ")" : "");
        var panel = HudPanel.of("Storage ESP", scope);
        int kinds = 0;
        for (StorageKind kind : StorageKind.values()) {
            int count = snapshot.count(kind);
            if (count == 0 || !settings.kindEnabled(kind)) continue;
            String value = String.valueOf(count);
            if (kind == StorageKind.CHESTS || kind == StorageKind.TRAPPED_CHESTS) {
                long doubles = snapshot.targets().stream().filter(t -> t.kind() == kind && t.doubleChest()).count();
                if (doubles > 0) value += " (" + doubles + "\u00D72)";
            }
            panel.swatch(settings.kindColor(kind), kind.shortLabel(), value);
            kinds++;
        }
        panel.columns(kinds > 4 ? 2 : 1);
        if (scanner.pendingChunks() > 0) panel.footerText("Scanning " + scanner.pendingChunks() + " chunks", HudPanel.MUTED);
        var nearest = snapshot.nearest();
        if (nearest != null) {
            var pos = nearest.pos();
            int distance = (int) Math.round(Math.sqrt(nearest.liveBounds().getCenter().distanceToSqr(client.player.getEyePosition())));
            String name = nearest.doubleChest() ? "Double " + nearest.kind().singular.toLowerCase(java.util.Locale.ROOT) : nearest.kind().singular;
            panel.footer(settings.kindColor(nearest.kind()), "Nearest: " + name.toLowerCase(java.util.Locale.ROOT),
                    distance + "m  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        } else if (scanner.pendingChunks() == 0) panel.footerText("No storage in range", HudPanel.MUTED);
        panel.draw(graphics, config, HudPanel.Anchor.BOTTOM_LEFT);
    }
}
