package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.staff.StaffDetectorController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Observations and uncertainty stay explicit, even when no staff packets are visible. */
public final class StaffDetectorHud {
    private StaffDetectorHud() { }
    public static void render(GuiGraphics g, ClientConfig config, StaffDetectorController controller) {
        var mc = Minecraft.getInstance(); var settings = config.staffDetector;
        if (!settings.enabled || !settings.showHud || !config.showHud || mc.player == null || mc.screen != null || mc.options.hideGui) return;
        var panel = HudPanel.of("Staff Detector", "Observed");
        var views = controller.views().stream().filter(view -> view.online() || view.nearby() || view.possibleHidden() || view.rosterLost()).toList();
        for (var view : views.stream().limit(settings.maxHudRows).toList())
            panel.row(config.nameHider.enabled ? "Staff member" : view.name(), view.description(),
                    view.invisible() || view.spectator() ? HudPanel.WARNING : view.possibleHidden() ? 0xFFFFE4A1 : HudPanel.TEXT);
        if (views.isEmpty()) panel.text("No staff presence observed", HudPanel.TEXT);
        if (views.size() > settings.maxHudRows) panel.text("+" + (views.size() - settings.maxHudRows) + " more in staff list", HudPanel.MUTED);
        panel.text("Hidden presence is unknown", HudPanel.MUTED);
        panel.draw(g, config, HudPanel.Anchor.TOP_LEFT);
    }
}
