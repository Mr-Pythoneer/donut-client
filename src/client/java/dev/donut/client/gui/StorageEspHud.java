package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.storageesp.StorageEspScanner;
import dev.donut.client.storageesp.StorageKind;
import java.util.ArrayList;
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
        var text = new ArrayList<String>();
        var colors = new ArrayList<Integer>();
        String header = "Storage ESP | " + snapshot.total() + " within " + settings.range + "m";
        if (snapshot.limited()) header += " | nearest " + snapshot.targets().size();
        text.add(header); colors.add(0xFFE5EFF9);
        if (scanner.pendingChunks() > 0) { text.add("Scanning " + scanner.pendingChunks() + " chunks..."); colors.add(0xFFB3C3D3); }
        for (StorageKind kind : StorageKind.values()) {
            int count = snapshot.count(kind);
            if (count == 0 || !settings.kindEnabled(kind)) continue;
            String line = kind.label + ": " + count;
            if (kind == StorageKind.CHESTS || kind == StorageKind.TRAPPED_CHESTS) {
                long doubles = snapshot.targets().stream().filter(t -> t.kind() == kind && t.doubleChest()).count();
                if (doubles > 0) line += " (" + doubles + " double" + (snapshot.limited() ? " shown" : "") + ")";
            }
            text.add(line); colors.add(settings.kindColor(kind));
        }
        var nearest = snapshot.nearest();
        if (nearest != null) {
            var pos = nearest.pos();
            int distance = (int) Math.round(Math.sqrt(nearest.liveBounds().getCenter().distanceToSqr(client.player.getEyePosition())));
            String name = nearest.doubleChest() ? "Double " + nearest.kind().singular.toLowerCase(java.util.Locale.ROOT) : nearest.kind().singular;
            text.add("Nearest: " + name + " | " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " | " + distance + "m");
            colors.add(settings.kindColor(nearest.kind()));
        } else if (scanner.pendingChunks() == 0) {
            text.add("No storage in range"); colors.add(0xFFB3C3D3);
        }
        int maxWidth = Math.max(80, graphics.guiWidth() - 24);
        int w = Math.min(text.stream().mapToInt(client.font::width).max().orElse(100) + 16, maxWidth);
        int h = text.size() * 12 + 12, x = 8;
        int below = BlockEspHud.height(config);
        int y = graphics.guiHeight() - h - 8 - (below > 0 ? below + 6 : 0);
        UiPaint.round(graphics, x, y, w, h, 5, 0xC0141C27);
        for (int i = 0; i < text.size(); i++) graphics.drawString(client.font,
                client.font.plainSubstrByWidth(text.get(i), w - 16), x + 8, y + 7 + i * 12, colors.get(i), false);
    }
}
