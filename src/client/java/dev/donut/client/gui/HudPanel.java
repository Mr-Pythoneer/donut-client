package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared look for Donut's in-game panels: one font, one row height, one surface, sized from the window rather than
 * Minecraft's GUI scale so large GUI scales don't inflate them. Panels in the same corner stack instead of overlapping.
 *
 * <p>Layout is in framebuffer pixels: {@link #draw} undoes the GUI scale and multiplies every measurement by
 * {@link #scale(ClientConfig)}.
 */
public final class HudPanel {
    /** {@code TOP_RIGHT} stacks below the module list and vanilla's effect icons, so neither is covered. */
    public enum Anchor { TOP_LEFT, BOTTOM_LEFT, BOTTOM_RIGHT, BELOW_CROSSHAIR, ABOVE_HOTBAR, TOP_RIGHT }

    public static final int TITLE = 0xFFF2F4F7, TEXT = 0xFFC7CCD6, MUTED = 0xFF8A919E;
    public static final int WARNING = 0xFFF0B45A, DANGER = 0xFFEF6A5E, GOOD = 0xFF7FCB8E;
    static final int SURFACE = 0xD20D1015, EDGE = 0x24FFFFFF, DIVIDER = 0x1CFFFFFF;
    // Base sizes at scale 1 (a 960-pixel-tall window); every value is multiplied by the panel scale.
    static final float PAD_X = 7, PAD_Y = 5, ROW = 14, TEXT_SIZE = 13, SWATCH = 6, ICON = 12, GAP = 12, COLUMN_GAP = 14,
            RADIUS = 4, MARGIN = 8, STACK_GAP = 5;

    private static float topLeft = -1, bottomLeft = -1, bottomRight = -1, topRight = -1;
    /** Bottom edge (framebuffer pixels) of what already occupies the top-right corner this frame. */
    private static float topRightFloor;

    private final String title, titleValue;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> footer = new ArrayList<>();
    private final List<Content> contents = new ArrayList<>();
    private int columns = 1;
    private int accent;
    private double progress = -1;
    private int progressColor;
    private float opacity = 1;

    private HudPanel(String title, String titleValue) {
        this.title = title;
        this.titleValue = titleValue;
    }

    /** A panel with a title row; {@code title} may be null for a panel of rows only. */
    public static HudPanel of(String title, String titleValue) { return new HudPanel(title, titleValue); }

    /** Reset corner stacking. Called once per frame before any Donut panel draws. */
    public static void newFrame() {
        topLeft = bottomLeft = bottomRight = topRight = -1;
        topRightFloor = vanillaEffectsBottom();
    }

    /** The module list reports its bottom edge here so {@link Anchor#TOP_RIGHT} panels start below it. */
    public static void reserveTopRight(float bottom) { topRightFloor = Math.max(topRightFloor, bottom); }

    /** Vanilla draws effect icons in the top-right corner (beneficial row, then harmful row, 24 GUI pixels each). */
    private static float vanillaEffectsBottom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options.hideGui) return 0;
        boolean beneficial = false, harmful = false;
        for (var effect : mc.player.getActiveEffects()) {
            if (!effect.showIcon()) continue;
            if (effect.getEffect().value().isBeneficial()) beneficial = true;
            else harmful = true;
        }
        int bottom = harmful ? 51 : beneficial ? 25 : 0;
        return bottom * (float) mc.getWindow().getGuiScale();
    }

    /** Panel scale: window height relative to 960 pixels times the user's HUD size, independent of GUI scale. */
    public static float scale(ClientConfig config) {
        var window = Minecraft.getInstance().getWindow();
        double size = config == null || config.clickGuiStyle == null ? 1 : config.clickGuiStyle.hudScale;
        return (float) (Math.round(Math.clamp(window.getHeight() / 960.0 * size, .5, 3.5) * 20) / 20.0);
    }

    public HudPanel row(String label, String value) { return add(new Row(label, value, 0, TEXT, TEXT, null, null)); }

    public HudPanel row(String label, String value, int valueColor) { return add(new Row(label, value, 0, TEXT, valueColor, null, null)); }

    /** A row with a small colour swatch before the label. */
    public HudPanel swatch(int rgb, String label, String value) { return add(new Row(label, value, 0xFF000000 | rgb, TEXT, TITLE, null, null)); }

    public HudPanel text(String value, int color) { return add(new Row(value, null, 0, color, color, null, null)); }

    public HudPanel item(ItemStack stack, String label, String value, int valueColor) { return add(new Row(label, value, 0, TEXT, valueColor, stack, null)); }

    /** A row led by a GUI sprite (e.g. a mob effect icon) drawn at item-icon size. */
    public HudPanel sprite(Identifier sprite, String label, String value, int valueColor) { return add(new Row(label, value, 0, TEXT, valueColor, null, sprite)); }

    /** Draws custom content (e.g. key caps) under the rows; it reserves {@code baseW} by {@code baseH} at scale 1. */
    public interface Painter { void paint(GuiGraphics g, float x, float y, float scale); }

    public HudPanel content(float baseW, float baseH, Painter painter) { contents.add(new Content(baseW, baseH, painter)); return this; }

    /** A full-width row under the column grid, separated by a divider. */
    public HudPanel footer(int rgb, String label, String value) { footer.add(new Row(label, value, rgb == 0 ? 0 : 0xFF000000 | rgb, TEXT, TITLE, null, null)); return this; }

    public HudPanel footerText(String value, int color) { footer.add(new Row(value, null, 0, color, color, null, null)); return this; }

    public HudPanel columns(int count) { columns = Math.max(1, count); return this; }

    /** A thin coloured stripe on the left edge, for warnings. */
    public HudPanel accent(int argb) { accent = argb; return this; }

    public HudPanel progress(double value, int rgb) { progress = Math.clamp(value, 0, 1); progressColor = 0xFF000000 | rgb; return this; }

    public HudPanel opacity(float value) { opacity = Math.clamp(value, 0, 1); return this; }

    public boolean isEmpty() { return title == null && rows.isEmpty() && footer.isEmpty() && contents.isEmpty(); }

    private HudPanel add(Row row) { rows.add(row); return this; }

    /** Draws the panel at the anchor and returns its height in framebuffer pixels (0 if nothing was drawn). */
    public float draw(GuiGraphics g, ClientConfig config, Anchor anchor) {
        if (isEmpty() || opacity <= .02f) return 0;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float s = scale(config), guiScale = (float) mc.getWindow().getGuiScale();
        float windowW = mc.getWindow().getWidth(), windowH = mc.getWindow().getHeight();
        float previousFont = WorkspacePaint.pushScale(s);
        double previousOpacity = UiPaint.pushOpacity(opacity);
        g.pose().pushMatrix();
        try {
            g.pose().scale(1 / guiScale, 1 / guiScale);
            // Measure: each column is [swatch/icon] label ... value.
            int perColumn = (rows.size() + columns - 1) / Math.max(1, columns);
            float[] labelW = new float[columns], valueW = new float[columns];
            boolean[] lead = new boolean[columns];
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int c = perColumn == 0 ? 0 : i / perColumn;
                labelW[c] = Math.max(labelW[c], WorkspacePaint.smallWidth(font, row.label));
                if (row.value != null) valueW[c] = Math.max(valueW[c], WorkspacePaint.smallWidth(font, row.value));
                if (row.swatch != 0 || row.icon != null || row.sprite != null) lead[c] = true;
            }
            float contentW = 0;
            float[] columnW = new float[columns];
            for (int c = 0; c < columns; c++) {
                float leadW = lead[c] ? (hasIcons() ? ICON + 4 : SWATCH + 5) * s : 0;
                columnW[c] = leadW + labelW[c] + (valueW[c] > 0 ? GAP * s + valueW[c] : 0);
                contentW += columnW[c] + (c > 0 ? COLUMN_GAP * s : 0);
            }
            float titleH = 0;
            if (title != null) {
                float titleW = WorkspacePaint.smallWidth(font, title) + (titleValue != null ? GAP * s + WorkspacePaint.smallWidth(font, titleValue) : 0);
                contentW = Math.max(contentW, titleW);
                titleH = ROW * s + (rows.isEmpty() ? 0 : 4 * s);
            }
            float footerH = footer.isEmpty() ? 0 : footer.size() * ROW * s + (rows.isEmpty() ? 0 : 4 * s);
            for (Row row : footer) contentW = Math.max(contentW, (row.swatch != 0 ? (SWATCH + 5) * s : 0) + WorkspacePaint.smallWidth(font, row.label)
                    + (row.value != null ? GAP * s + WorkspacePaint.smallWidth(font, row.value) : 0));
            float progressH = progress >= 0 ? 6 * s : 0;
            float contentH = 0;
            for (Content content : contents) {
                contentW = Math.max(contentW, content.w * s);
                contentH += content.h * s + 4 * s;
            }
            if (contentH > 0 && title == null && rows.isEmpty()) contentH -= 4 * s;
            float w = (float) Math.ceil(contentW + PAD_X * 2 * s + (accent != 0 ? 2 * s : 0));
            w = Math.min(w, windowW - MARGIN * 2 * s);
            float h = (float) Math.ceil(PAD_Y * 2 * s + titleH + perColumn * ROW * s + contentH + footerH + progressH);
            float[] at = place(anchor, w, h, s, guiScale, windowW, windowH);
            float x = at[0], y = at[1];

            UiPaint.round(g, x, y, w, h, RADIUS * s, SURFACE);
            outline(g, x, y, w, h, s);
            if (accent != 0) UiPaint.round(g, x, y, 2 * s, h, Math.min(RADIUS * s, s), accent);
            float left = x + PAD_X * s + (accent != 0 ? 2 * s : 0), cursor = y + PAD_Y * s;
            float textInset = WorkspacePaint.centered(0, ROW * s, WorkspacePaint.SMALL_SIZE);
            if (title != null) {
                WorkspacePaint.smallText(g, font, title, left, cursor + textInset, TITLE);
                if (titleValue != null) WorkspacePaint.smallText(g, font, titleValue,
                        x + w - PAD_X * s - WorkspacePaint.smallWidth(font, titleValue), cursor + textInset, MUTED);
                cursor += ROW * s;
                if (!rows.isEmpty()) {
                    g.fill(Math.round(left), Math.round(cursor + s), Math.round(x + w - PAD_X * s), Math.round(cursor + s) + Math.max(1, Math.round(s * .75f)), UiPaint.withOpacity(DIVIDER));
                    cursor += 4 * s;
                }
            }
            float columnX = left;
            for (int c = 0; c < columns; c++) {
                for (int r = 0; r < perColumn; r++) {
                    int i = c * perColumn + r;
                    if (i >= rows.size()) break;
                    Row row = rows.get(i);
                    float rowY = cursor + r * ROW * s, textX = columnX;
                    if (row.icon != null || row.sprite != null) {
                        g.pose().pushMatrix();
                        g.pose().translate(textX, rowY + (ROW - ICON) * s / 2);
                        if (row.icon != null) {
                            g.pose().scale(ICON * s / 16, ICON * s / 16);
                            g.renderItem(row.icon, 0, 0);
                        } else {
                            g.pose().scale(ICON * s / 18, ICON * s / 18);
                            g.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite, 0, 0, 18, 18, UiPaint.withOpacity(0xFFFFFFFF));
                        }
                        g.pose().popMatrix();
                        textX += (ICON + 4) * s;
                    } else if (row.swatch != 0) {
                        UiPaint.round(g, textX, rowY + (ROW - SWATCH) * s / 2, SWATCH * s, SWATCH * s, 1.5f * s, row.swatch);
                        textX += (SWATCH + 5) * s;
                    } else if (lead[c]) textX += (hasIcons() ? ICON + 4 : SWATCH + 5) * s;
                    WorkspacePaint.smallText(g, font, row.label, textX, rowY + textInset, row.labelColor);
                    if (row.value != null) WorkspacePaint.smallText(g, font, row.value,
                            columnX + columnW[c] - WorkspacePaint.smallWidth(font, row.value), rowY + textInset, row.valueColor);
                }
                columnX += columnW[c] + COLUMN_GAP * s;
            }
            float contentY = cursor + perColumn * ROW * s + (title != null || !rows.isEmpty() ? 4 * s : 0);
            for (Content content : contents) {
                content.painter.paint(g, left, contentY, s);
                contentY += content.h * s + 4 * s;
            }
            if (!footer.isEmpty()) {
                float fy = cursor + perColumn * ROW * s + contentH;
                if (!rows.isEmpty()) {
                    g.fill(Math.round(left), Math.round(fy + s), Math.round(x + w - PAD_X * s), Math.round(fy + s) + Math.max(1, Math.round(s * .75f)), UiPaint.withOpacity(DIVIDER));
                    fy += 4 * s;
                }
                for (Row row : footer) {
                    float textX = left;
                    if (row.swatch != 0) {
                        UiPaint.round(g, textX, fy + (ROW - SWATCH) * s / 2, SWATCH * s, SWATCH * s, 1.5f * s, row.swatch);
                        textX += (SWATCH + 5) * s;
                    }
                    WorkspacePaint.smallText(g, font, row.label, textX, fy + textInset, row.labelColor);
                    if (row.value != null) WorkspacePaint.smallText(g, font, row.value,
                            x + w - PAD_X * s - WorkspacePaint.smallWidth(font, row.value), fy + textInset, row.valueColor);
                    fy += ROW * s;
                }
            }
            if (progress >= 0) {
                float barY = y + h - PAD_Y * s - 2 * s, barW = w - (left - x) - PAD_X * s;
                UiPaint.round(g, left, barY, barW, 2 * s, s, 0x33FFFFFF);
                UiPaint.round(g, left, barY, (float) (barW * progress), 2 * s, s, progressColor);
            }
            return h;
        } finally {
            g.pose().popMatrix();
            UiPaint.popOpacity(previousOpacity);
            WorkspacePaint.popScale(previousFont);
        }
    }

    private static void outline(GuiGraphics g, float x, float y, float w, float h, float s) {
        int t = Math.max(1, Math.round(s * .75f)), edge = UiPaint.withOpacity(EDGE);
        int r = Math.round(RADIUS * s * .6f);
        int l = Math.round(x), top = Math.round(y), right = Math.round(x + w), bottom = Math.round(y + h);
        g.fill(l + r, top, right - r, top + t, edge);
        g.fill(l + r, bottom - t, right - r, bottom, edge);
        g.fill(l, top + r, l + t, bottom - r, edge);
        g.fill(right - t, top + r, right, bottom - r, edge);
    }

    /** Top-left position for the anchor, reserving space so the next panel in the same corner stacks clear of it. */
    static float[] place(Anchor anchor, float w, float h, float s, float guiScale, float windowW, float windowH) {
        float margin = MARGIN * s, gap = STACK_GAP * s;
        // Hotbar plus hearts/armour/XP rows, in framebuffer pixels.
        float hotbarLeft = (windowW - 182 * guiScale) / 2, hotbarTop = windowH - 40 * guiScale;
        return switch (anchor) {
            case TOP_LEFT -> {
                float y = topLeft < 0 ? margin : topLeft;
                topLeft = y + h + gap;
                yield new float[] {margin, y};
            }
            case BOTTOM_LEFT -> {
                float bottom = bottomLeft < 0 ? windowH - margin : bottomLeft;
                if (margin + w > hotbarLeft - gap) bottom = Math.min(bottom, hotbarTop - gap);
                bottomLeft = bottom - h - gap;
                yield new float[] {margin, bottom - h};
            }
            case BOTTOM_RIGHT -> {
                float bottom = bottomRight < 0 ? windowH - margin : bottomRight;
                if (windowW - margin - w < windowW - hotbarLeft + gap) bottom = Math.min(bottom, hotbarTop - gap);
                bottomRight = bottom - h - gap;
                yield new float[] {windowW - margin - w, bottom - h};
            }
            case BELOW_CROSSHAIR -> new float[] {(windowW - w) / 2, windowH / 2 + 14 * s};
            case ABOVE_HOTBAR -> new float[] {(windowW - w) / 2, Math.min(windowH - 72 * guiScale, hotbarTop - 24 * guiScale) - h};
            case TOP_RIGHT -> {
                float y = topRight < 0 ? Math.max(margin, topRightFloor + gap) : topRight;
                topRight = y + h + gap;
                yield new float[] {windowW - margin - w, y};
            }
        };
    }

    private boolean hasIcons() { return rows.stream().anyMatch(r -> r.icon != null || r.sprite != null); }

    private record Row(String label, String value, int swatch, int labelColor, int valueColor, ItemStack icon, Identifier sprite) { }
    private record Content(float w, float h, Painter painter) { }
}
