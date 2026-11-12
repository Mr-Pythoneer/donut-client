package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.HudWidgetsSettings;
import dev.donut.client.hudwidgets.ClickCounter;
import dev.donut.client.hudwidgets.HudWidget;
import dev.donut.client.hudwidgets.HudWidgetText;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the HUD Widgets module. Stats (FPS, ping, CPS, memory, clock, server, compass) assigned to the same corner share
 * one panel; potion effects, armour and key strokes are their own panels. Everything goes through {@link HudPanel}, so
 * panels in one corner stack instead of overlapping Coordinates, Storage ESP or the module list.
 */
public final class HudWidgetsHud {
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Panels drawn last frame, by kind and anchor, e.g. "stats@TOP_RIGHT"; read by tests. */
    private static volatile List<String> lastDrawn = List.of();
    /** {@link System#nanoTime()} of the last frame that drew any widget panel. */
    private static volatile long lastDrawNanos;

    private HudWidgetsHud() { }

    public static List<String> lastDrawn() { return lastDrawn; }
    public static long lastDrawNanos() { return lastDrawNanos; }

    public static HudPanel.Anchor anchor(String corner) {
        return switch (corner) {
            case "Top left" -> HudPanel.Anchor.TOP_LEFT;
            case "Bottom left" -> HudPanel.Anchor.BOTTOM_LEFT;
            case "Bottom right" -> HudPanel.Anchor.BOTTOM_RIGHT;
            default -> HudPanel.Anchor.TOP_RIGHT;
        };
    }

    public static void render(GuiGraphics g, ClientConfig config) {
        Minecraft client = Minecraft.getInstance();
        var settings = config.hudWidgets;
        if (!settings.enabled || !config.showHud || client.options.hideGui || client.player == null || client.level == null
                || client.screen != null || client.getDebugOverlay().showDebugScreen()) {
            lastDrawn = List.of();
            return;
        }
        List<String> drawn = new ArrayList<>();
        for (String corner : HudWidgetsSettings.CORNERS) {
            HudPanel.Anchor anchor = anchor(corner);
            HudPanel stats = stats(client, settings, corner);
            if (!stats.isEmpty() && stats.draw(g, config, anchor) > 0) drawn.add("stats@" + anchor);
            if (on(settings, HudWidget.POTIONS, corner)) {
                HudPanel potions = potions(client);
                if (!potions.isEmpty() && potions.draw(g, config, anchor) > 0) drawn.add("potions@" + anchor);
            }
            if (on(settings, HudWidget.ARMOUR, corner)) {
                HudPanel armour = armour(client);
                if (!armour.isEmpty() && armour.draw(g, config, anchor) > 0) drawn.add("armour@" + anchor);
            }
            if (on(settings, HudWidget.KEYSTROKES, corner) && keystrokes(client, config).draw(g, config, anchor) > 0)
                drawn.add("keystrokes@" + anchor);
        }
        lastDrawn = List.copyOf(drawn);
        if (!drawn.isEmpty()) lastDrawNanos = System.nanoTime();
    }

    private static boolean on(HudWidgetsSettings settings, HudWidget widget, String corner) {
        return settings.widgetEnabled(widget) && settings.corner(widget).equals(corner);
    }

    static HudPanel stats(Minecraft client, HudWidgetsSettings settings, String corner) {
        HudPanel panel = HudPanel.of(null, null);
        for (HudWidget widget : HudWidget.values()) {
            if (!widget.stat || !on(settings, widget, corner)) continue;
            switch (widget) {
                case FPS -> panel.row("FPS", HudWidgetText.fps(client.getFps()), HudPanel.TITLE);
                case PING -> {
                    boolean singleplayer = client.isLocalServer();
                    var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(client.player.getUUID());
                    Integer latency = info == null ? null : info.getLatency();
                    int color = singleplayer || latency == null ? HudPanel.MUTED
                            : switch (HudWidgetText.pingLevel(latency)) { case 0 -> HudPanel.GOOD; case 1 -> HudPanel.WARNING; default -> HudPanel.DANGER; };
                    panel.row("Ping", HudWidgetText.ping(latency, singleplayer), color);
                }
                case CPS -> panel.row("CPS", HudWidgetText.cps(ClickCounter.get().cps(ClickCounter.LEFT), ClickCounter.get().cps(ClickCounter.RIGHT)), HudPanel.TITLE);
                case MEMORY -> {
                    Runtime runtime = Runtime.getRuntime();
                    panel.row("Memory", HudWidgetText.memory(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(), settings.memoryPercent), HudPanel.TITLE);
                }
                case CLOCK -> {
                    panel.row("Time", HudWidgetText.clock(LocalTime.now(), settings.clock24h), HudPanel.TITLE);
                    if (settings.showGameTime) panel.row("Game", HudWidgetText.gameTime(client.level.getDayTime()), HudPanel.TITLE);
                }
                case SERVER -> {
                    var server = client.getCurrentServer();
                    panel.row("Server", server != null && !client.isLocalServer() ? server.ip : "Singleplayer", HudPanel.TITLE);
                }
                case COMPASS -> panel.row("Facing", HudWidgetText.compass(client.player.getYRot()), HudPanel.TITLE);
                default -> { }
            }
        }
        return panel;
    }

    static HudPanel potions(Minecraft client) {
        HudPanel panel = HudPanel.of(null, null);
        List<MobEffectInstance> effects = new ArrayList<>(client.player.getActiveEffects());
        // Beneficial first, then the one running out soonest.
        effects.sort(Comparator.comparing((MobEffectInstance e) -> !e.getEffect().value().isBeneficial())
                .thenComparing(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
        for (MobEffectInstance effect : effects) {
            String name = HudWidgetText.effectName(effect.getEffect().value().getDisplayName().getString(), effect.getAmplifier());
            boolean ending = !effect.isInfiniteDuration() && effect.getDuration() < 200;
            panel.sprite(net.minecraft.client.gui.Gui.getMobEffectSprite(effect.getEffect()), name,
                    HudWidgetText.effectTime(effect.getDuration(), effect.isInfiniteDuration()), ending ? HudPanel.WARNING : HudPanel.TITLE);
        }
        return panel;
    }

    static HudPanel armour(Minecraft client) {
        HudPanel panel = HudPanel.of(null, null);
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack stack = client.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (!stack.isDamageableItem()) { panel.item(stack, stack.getHoverName().getString(), null, HudPanel.TITLE); continue; }
            int max = stack.getMaxDamage(), left = max - stack.getDamageValue();
            int percent = (int) Math.floor(100.0 * left / Math.max(1, max));
            int color = percent <= 10 ? HudPanel.DANGER : percent <= 25 ? HudPanel.WARNING : HudPanel.TITLE;
            panel.item(stack, left + "/" + max, percent + "%", color);
        }
        return panel;
    }

    // Key caps in base units (scaled by the panel scale).
    private static final float CAP = 20, CAP_GAP = 3;

    static HudPanel keystrokes(Minecraft client, ClientConfig config) {
        float width = CAP * 3 + CAP_GAP * 2, height = CAP * 4 + CAP_GAP * 3;
        var options = client.options;
        return HudPanel.of(null, null).content(width, height, (g, x, y, s) -> {
            Font font = client.font;
            float cap = CAP * s, gap = CAP_GAP * s, row = cap + gap;
            cap(g, font, x + row, y, cap, cap, key(options.keyUp), options.keyUp.isDown(), config);
            cap(g, font, x, y + row, cap, cap, key(options.keyLeft), options.keyLeft.isDown(), config);
            cap(g, font, x + row, y + row, cap, cap, key(options.keyDown), options.keyDown.isDown(), config);
            cap(g, font, x + row * 2, y + row, cap, cap, key(options.keyRight), options.keyRight.isDown(), config);
            cap(g, font, x, y + row * 2, cap * 3 + gap * 2, cap, "Space", options.keyJump.isDown(), config);
            float half = (cap * 3 + gap) / 2;
            cap(g, font, x, y + row * 3, half, cap, "LMB", client.mouseHandler.isLeftPressed(), config);
            cap(g, font, x + half + gap, y + row * 3, half, cap, "RMB", client.mouseHandler.isRightPressed(), config);
        });
    }

    private static String key(KeyMapping mapping) { return HudWidgetText.keyCap(mapping.getTranslatedKeyMessage().getString()); }

    private static void cap(GuiGraphics g, Font font, float x, float y, float w, float h, String label, boolean down, ClientConfig config) {
        float s = h / CAP;
        int fill = down ? 0xE6000000 | config.accentColor & 0xFFFFFF : 0x30FFFFFF;
        UiPaint.round(g, x, y, w, h, 3 * s, fill);
        float textW = WorkspacePaint.smallWidth(font, label);
        WorkspacePaint.smallText(g, font, label, x + (w - textW) / 2, WorkspacePaint.centered(y, h, WorkspacePaint.SMALL_SIZE),
                down ? 0xFF0D1015 : HudPanel.TITLE);
    }
}
