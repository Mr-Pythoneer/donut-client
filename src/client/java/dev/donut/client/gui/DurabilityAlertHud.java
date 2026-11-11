package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.durability.DurabilityAlertController;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Centered warning under the crosshair area listing items at low durability. */
public final class DurabilityAlertHud {
    private DurabilityAlertHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, DurabilityAlertController controller) {
        Minecraft client = Minecraft.getInstance();
        if (controller == null || !config.durabilityAlert.enabled || !config.showHud || client.options.hideGui
                || client.player == null || client.screen != null || controller.low().isEmpty()) return;
        var low = controller.low();
        var panel = HudPanel.of("Low durability", null).accent(HudPanel.WARNING);
        for (var entry : low) panel.item(entry.stack(), entry.slot() + "  " + entry.stack().getHoverName().getString(),
                String.format(Locale.ROOT, "%d%%  %d left", (int) Math.floor(entry.percent()), entry.left()),
                entry.percent() <= 5 ? HudPanel.DANGER : HudPanel.WARNING);
        panel.draw(graphics, config, HudPanel.Anchor.BELOW_CROSSHAIR);
    }
}
