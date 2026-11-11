package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.coordinates.CoordinateText;
import dev.donut.client.freecam.FreecamController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Position, facing, chunk and linked-dimension coordinates. Reads only the local player and camera. */
public final class CoordinatesHud {
    private CoordinatesHud() { }

    public static List<String> lines(Minecraft client, ClientConfig config) {
        var settings = config.coordinates;
        var player = client.player;
        var lines = new ArrayList<String>();
        String dimension = client.level.dimension().identifier().toString();
        lines.add("XYZ " + CoordinateText.position(player.getX(), player.getY(), player.getZ()));
        if (settings.showFacing) lines.add("Facing " + CoordinateText.facing(player.getYRot()) + " | " + CoordinateText.yaw(player.getYRot()));
        if (settings.showChunk) lines.add(CoordinateText.chunk(player.getBlockX(), player.getBlockZ()));
        if (settings.showLinked) {
            String linked = CoordinateText.linked(dimension, player.getX(), player.getZ());
            lines.add(linked != null ? linked : CoordinateText.dimensionName(dimension));
        }
        if (settings.showBiome) client.level.getBiome(player.blockPosition()).unwrapKey()
                .ifPresent(key -> lines.add("Biome " + CoordinateText.dimensionName(key.identifier().toString())));
        var freecam = FreecamController.getInstance();
        if (settings.showFreecam && freecam != null && freecam.isActive()) {
            var camera = freecam.position();
            lines.add("Camera " + CoordinateText.position(camera.x, camera.y, camera.z));
            lines.add("Camera facing " + CoordinateText.facing(freecam.yaw()));
        }
        return lines;
    }

    public static void render(GuiGraphics graphics, ClientConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (!config.coordinates.enabled || !config.showHud || client.options.hideGui || client.player == null
                || client.level == null || client.screen != null || client.getDebugOverlay().showDebugScreen()) return;
        var text = lines(client, config);
        int w = text.stream().mapToInt(client.font::width).max().orElse(80) + 16;
        int h = text.size() * 11 + 10;
        boolean bottomRight = "Bottom right".equals(config.coordinates.anchor);
        int x = bottomRight ? graphics.guiWidth() - w - 8 : 8;
        int y = bottomRight ? graphics.guiHeight() - h - 8 : 8;
        UiPaint.round(graphics, x, y, w, h, 5, 0xC0141C27);
        for (int i = 0; i < text.size(); i++)
            graphics.drawString(client.font, text.get(i), x + 8, y + 6 + i * 11, i == 0 ? 0xFFE5EFF9 : 0xFFB3C3D3, false);
    }
}
