package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.durability.DurabilityAlertController;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/** Centered warning under the crosshair area listing items at low durability. */
public final class DurabilityAlertHud {
    private DurabilityAlertHud() { }

    public static void render(GuiGraphics graphics, ClientConfig config, DurabilityAlertController controller) {
        Minecraft client = Minecraft.getInstance();
        if (controller == null || !config.durabilityAlert.enabled || !config.showHud || client.options.hideGui
                || client.player == null || client.screen != null || controller.low().isEmpty()) return;
        var low = controller.low();
        int lineH = 18, w = 0;
        String[] text = new String[low.size()];
        for (int i = 0; i < low.size(); i++) {
            var entry = low.get(i);
            text[i] = String.format(Locale.ROOT, "%s: %s %d%% (%d left)", entry.slot(), entry.stack().getHoverName().getString(),
                    (int) Math.floor(entry.percent()), entry.left());
            w = Math.max(w, client.font.width(text[i]));
        }
        w += 34;
        int h = low.size() * lineH + 6;
        int x = (graphics.guiWidth() - w) / 2, y = graphics.guiHeight() / 2 + 18;
        // A gentle pulse keeps the warning noticeable without flashing.
        double pulse = .5 + .5 * Math.sin(Util.getMillis() / 250.0);
        int border = (int) (120 + 100 * pulse) << 24 | 0xFF5A4E;
        UiPaint.round(graphics, x - 1, y - 1, w + 2, h + 2, 6, border);
        UiPaint.round(graphics, x, y, w, h, 5, 0xE0141C27);
        for (int i = 0; i < text.length; i++) {
            int rowY = y + 3 + i * lineH;
            graphics.renderItem(low.get(i).stack(), x + 5, rowY + 1);
            graphics.drawString(client.font, text[i], x + 26, rowY + 5, low.get(i).percent() <= 5 ? 0xFFFF5A4E : 0xFFFFC66B, false);
        }
    }
}
