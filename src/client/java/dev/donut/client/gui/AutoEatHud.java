package dev.donut.client.gui;

import dev.donut.client.autoeat.AutoEatController;
import dev.donut.client.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A one-line panel above the hotbar while Auto Eat is eating or waiting for Auto Miner. */
public final class AutoEatHud {
    private AutoEatHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, AutoEatController controller) {
        Minecraft client = Minecraft.getInstance();
        if (controller == null || !config.autoEat.enabled || !config.autoEat.showHud || !config.showHud || client.options.hideGui
                || client.player == null || client.screen != null) return;
        String eating = controller.eatingName();
        String waiting = controller.waiting();
        if (eating == null && (waiting == null || !waiting.contains("Auto Miner"))) return;
        HudPanel.of("Auto Eat", null)
                .text(eating != null ? "Eating " + eating : waiting, eating != null ? HudPanel.GOOD : HudPanel.MUTED)
                .draw(graphics, config, HudPanel.Anchor.ABOVE_HOTBAR);
    }
}
