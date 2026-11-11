package dev.donut.client.gui;

import dev.donut.client.automine.AutoMinerController;
import dev.donut.client.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Two plain lines at the top centre: what the miner is doing, and how many ores it has mined. */
public final class AutoMinerHud {
    private AutoMinerHud() { }

    /** The lines that would be drawn; empty when hidden. */
    public static List<String> lines(Minecraft client, ClientConfig config, AutoMinerController miner) {
        var settings = config.autoMiner;
        var lines = new ArrayList<String>(2);
        if (miner == null || !config.showHud || !settings.showHud || client.options.hideGui || client.player == null
                || client.screen != null || !settings.enabled && !miner.showStopNotice()) return lines;
        lines.add(miner.statusLine(client));
        if (settings.enabled) lines.add("Mined " + miner.mined() + (miner.lastSkip() == null ? "" : " | skipped " + miner.lastSkip()));
        return lines;
    }

    public static void render(GuiGraphics graphics, ClientConfig config, AutoMinerController miner) {
        Minecraft client = Minecraft.getInstance();
        var lines = lines(client, config, miner);
        if (lines.isEmpty()) return;
        int width = Math.min(lines.stream().mapToInt(client.font::width).max().orElse(80) + 12, graphics.guiWidth() - 16);
        int height = lines.size() * 11 + 7, x = (graphics.guiWidth() - width) / 2, y = 4;
        UiPaint.round(graphics, x, y, width, height, 4, 0xB0141C27);
        for (int i = 0; i < lines.size(); i++)
            graphics.drawString(client.font, client.font.plainSubstrByWidth(lines.get(i), width - 12), x + 6, y + 5 + i * 11,
                    i == 0 ? 0xFFE5EFF9 : 0xFFB3C3D3, false);
    }
}
