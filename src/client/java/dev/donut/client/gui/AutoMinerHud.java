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
        var panel = HudPanel.of("Auto Miner", miner.state() == AutoMinerController.State.PAUSED ? "Paused" : null);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // The panel title already names the module.
            if (line.startsWith("Auto Miner: ")) line = Character.toUpperCase(line.charAt(12)) + line.substring(13);
            panel.text(line, i == 0 ? HudPanel.TEXT : HudPanel.MUTED);
        }
        panel.draw(graphics, config, HudPanel.Anchor.TOP_LEFT);
    }
}
