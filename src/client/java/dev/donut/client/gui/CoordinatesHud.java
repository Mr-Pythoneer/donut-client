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

    /** Label/value rows, e.g. ("XYZ", "3.7, -60.0, -1.0"). */
    public record Line(String label, String value) { }

    public static List<Line> lines(Minecraft client, ClientConfig config) {
        var settings = config.coordinates;
        var player = client.player;
        var lines = new ArrayList<Line>();
        String dimension = client.level.dimension().identifier().toString();
        lines.add(new Line("XYZ", xyz(player.getX(), player.getY(), player.getZ())));
        if (settings.showFacing) lines.add(new Line("Facing", CoordinateText.facing(player.getYRot()) + "  " + CoordinateText.yaw(player.getYRot()) + "\u00B0"));
        if (settings.showChunk) lines.add(new Line("Chunk", (player.getBlockX() >> 4) + ", " + (player.getBlockZ() >> 4)
                + "  in " + (player.getBlockX() & 15) + ", " + (player.getBlockZ() & 15)));
        if (settings.showLinked) {
            String linked = CoordinateText.linked(dimension, player.getX(), player.getZ());
            if (linked != null) {
                int colon = linked.indexOf(':');
                lines.add(new Line(linked.substring(0, colon), linked.substring(colon + 1).trim()));
            } else lines.add(new Line("Dimension", CoordinateText.dimensionName(dimension)));
        }
        if (settings.showBiome) client.level.getBiome(player.blockPosition()).unwrapKey()
                .ifPresent(key -> lines.add(new Line("Biome", CoordinateText.dimensionName(key.identifier().toString()))));
        var freecam = FreecamController.getInstance();
        if (settings.showFreecam && freecam != null && freecam.isActive()) {
            var camera = freecam.position();
            lines.add(new Line("Camera", xyz(camera.x, camera.y, camera.z)));
            lines.add(new Line("Camera facing", CoordinateText.facing(freecam.yaw())));
        }
        return lines;
    }

    static String xyz(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    public static void render(GuiGraphics graphics, ClientConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (!config.coordinates.enabled || !config.showHud || client.options.hideGui || client.player == null
                || client.level == null || client.screen != null || client.getDebugOverlay().showDebugScreen()) return;
        var panel = HudPanel.of(null, null);
        for (Line line : lines(client, config)) panel.row(line.label(), line.value(), HudPanel.TITLE);
        panel.draw(graphics, config, "Bottom right".equals(config.coordinates.anchor) ? HudPanel.Anchor.BOTTOM_RIGHT : HudPanel.Anchor.TOP_LEFT);
    }
}
