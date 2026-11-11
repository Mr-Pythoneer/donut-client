package dev.donut.client.gui;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.config.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small in-world progress display; module names remain in the existing array list. */
public final class AutoBuilderHud {
    private AutoBuilderHud() { }

    public static void render(GuiGraphics g, Font font, int height, ClientConfig config) {
        AutoBuilderController builder = AutoBuilderController.get();
        if (builder.schematic() == null || (!builder.previewVisible()
                && builder.status() != AutoBuilderController.Status.RUNNING
                && builder.status() != AutoBuilderController.Status.PAUSED)) return;
        String reason = dev.donut.client.spectrebuilder.BuilderPlacement.placing() ? dev.donut.client.spectrebuilder.BuilderPlacement.hint() : builder.reason();
        while (UiPaint.width(font, reason) > 300 && reason.length() > 4)
            reason = reason.substring(0, reason.length() - 4) + "...";
        String state = builder.status().name().toLowerCase(java.util.Locale.ROOT);
        HudPanel.of("AutoBuilder", Character.toUpperCase(state.charAt(0)) + state.substring(1) + "  " + Math.round(builder.progress() * 100) + "%")
                .text(reason, HudPanel.MUTED)
                .progress(builder.progress(), config.accentColor)
                .draw(g, config, HudPanel.Anchor.TOP_LEFT);
    }
}
