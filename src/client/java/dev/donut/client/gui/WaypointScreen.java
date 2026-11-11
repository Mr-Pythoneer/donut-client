package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.WaypointSettings;
import dev.donut.client.waypoints.Waypoint;
import dev.donut.client.waypoints.WaypointController;
import dev.donut.client.waypoints.Waypoints;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Lists this server's waypoints (all dimensions, current one first) and edits the selected one. Non-pausing. */
public final class WaypointScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;
    private final ClientConfig config;
    private final Runnable save;
    private final WaypointController controller;
    private EditBox nameField, renameField;
    private Button addHere, addStorage, addBase, rename, visibility, color, delete;
    private List<Waypoint> rows = List.of();
    private Waypoint selected, confirmDelete;
    private String newName = "", renameDraft = "", status = "";
    private int statusColor = 0xFFB5C7D8;
    private double scroll;
    private int left, w, listTop, listBottom;

    public WaypointScreen(Screen parent, ClientConfig config, Runnable save) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
        this.config = config;
        this.save = save == null ? () -> { } : save;
        WaypointController existing = WaypointController.get();
        controller = existing != null ? existing : new WaypointController(config, this.save);
    }

    @Override protected void init() {
        w = Math.min(440, width - 32);
        left = (width - w) / 2;
        nameField = addRenderableWidget(new EditBox(font, left, 36, w, 20, Component.literal("New waypoint name")));
        nameField.setMaxLength(Waypoint.MAX_NAME);
        nameField.setValue(newName);
        nameField.setHint(Component.literal("Name for new waypoint (optional)"));
        nameField.setResponder(value -> newName = value);
        int third = (w - 8) / 3;
        addHere = addRenderableWidget(Button.builder(Component.literal("Add here"), button -> added(controller.addHere(newName)))
                .bounds(left, 60, third, 20).build());
        addStorage = addRenderableWidget(Button.builder(Component.literal("Add at Storage ESP nearest"),
                button -> addAtStorage()).bounds(left + third + 4, 60, third, 20).build());
        addBase = addRenderableWidget(Button.builder(Component.literal("Add at Base Finder focus"), button -> addAtBaseFinder())
                .bounds(left + 2 * (third + 4), 60, w - 2 * (third + 4), 20).build());
        listTop = 86;
        listBottom = height - 60;

        renameField = addRenderableWidget(new EditBox(font, left, height - 54, w - 92, 20, Component.literal("Rename")));
        renameField.setMaxLength(Waypoint.MAX_NAME);
        renameField.setValue(renameDraft);
        renameField.setResponder(value -> renameDraft = value);
        rename = addRenderableWidget(Button.builder(Component.literal("Rename"), button -> {
            controller.rename(selected, renameDraft);
            if (selected != null) renameField.setValue(selected.name);
        }).bounds(left + w - 88, height - 54, 88, 20).build());
        int quarter = (w - 12) / 4;
        visibility = addRenderableWidget(Button.builder(Component.literal("Hide"), button -> controller.toggleVisible(selected))
                .bounds(left, height - 28, quarter, 20).build());
        color = addRenderableWidget(Button.builder(Component.literal("Color"), button -> controller.cycleColor(selected))
                .bounds(left + quarter + 4, height - 28, quarter, 20).build());
        delete = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete())
                .bounds(left + 2 * (quarter + 4), height - 28, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + 3 * (quarter + 4), height - 28, w - 3 * (quarter + 4), 20).build());
        setInitialFocus(nameField);
        refresh();
    }

    private void addAtStorage() {
        var target = controller.storageNearest();
        if (target == null) { setStatus("Storage ESP has nothing in range", 0xFFFF8590); return; }
        String name = Waypoint.cleanName(newName) == null ? (target.doubleChest() ? "Double " + target.kind().singular.toLowerCase(Locale.ROOT)
                : target.kind().singular) : newName;
        added(controller.add(name, target.pos()));
    }

    private void addAtBaseFinder() {
        var marker = controller.baseFinderFocus();
        if (marker == null) { setStatus("Look at a Base Finder marker first", 0xFFFF8590); return; }
        var center = marker.bounds().getCenter();
        String name = Waypoint.cleanName(newName) == null ? marker.label() : newName;
        added(controller.add(name, BlockPos.containing(center)));
    }

    private void added(Waypoint waypoint) {
        if (waypoint == null) {
            setStatus(minecraft.level == null ? "No world loaded"
                    : controller.settings().hasRoom() ? "Nothing to add" : "Limit of " + WaypointSettings.MAX_WAYPOINTS + " waypoints reached",
                    0xFFFF8590);
            return;
        }
        nameField.setValue("");
        select(waypoint);
        refresh();
        int index = rows.indexOf(waypoint);
        if (index >= 0) scroll = Math.clamp(index * ROW - (listBottom - listTop) / 2.0, 0, maxScroll());
        setStatus("Added \"" + waypoint.name + "\" at " + coords(waypoint), 0xFF9BE59B);
    }

    private void delete() {
        if (selected == null) return;
        if (confirmDelete != selected) { confirmDelete = selected; return; }
        String name = selected.name;
        controller.remove(selected);
        select(null);
        setStatus("Deleted \"" + name + "\"", 0xFFB5C7D8);
    }

    private void select(Waypoint waypoint) {
        selected = waypoint;
        confirmDelete = null;
        if (renameField != null) renameField.setValue(waypoint == null ? "" : waypoint.name);
    }

    private void setStatus(String text, int argb) { status = text; statusColor = argb; }

    private void refresh() {
        rows = controller.forCurrentServer();
        if (selected != null && !rows.contains(selected)) select(null);
        scroll = Math.clamp(scroll, 0, maxScroll());
        boolean world = minecraft != null && minecraft.level != null && minecraft.player != null;
        boolean room = controller.settings().hasRoom();
        addHere.active = world && room;
        addStorage.active = world && room && controller.storageNearest() != null;
        addBase.active = world && room && controller.baseFinderFocus() != null;
        boolean has = selected != null;
        renameField.active = renameField.visible = has;
        rename.active = visibility.active = color.active = delete.active = has;
        visibility.setMessage(Component.literal(has && !selected.visible ? "Show" : "Hide"));
        delete.setMessage(Component.literal(has && confirmDelete == selected ? "Confirm delete" : "Delete"));
    }

    private double maxScroll() { return Math.max(0, rows.size() * ROW - (listBottom - listTop)); }

    @Override public void tick() { refresh(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (mx < left || mx > left + w || my < listTop || my > listBottom) return false;
        int index = (int) Math.floor((my - listTop + scroll) / ROW);
        select(index >= 0 && index < rows.size() ? rows.get(index) : null);
        refresh();
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (my >= listTop && my <= listBottom) {
            scroll = Math.clamp(scroll - vertical * ROW, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xEE101722);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFF0F5FA);
        String dimension = controller.currentDimension();
        String header = font.plainSubstrByWidth(controller.currentServer() + "  |  "
                + (dimension == null ? "no world" : Waypoints.dimensionLabel(dimension)) + "  |  " + rows.size() + " here, "
                + controller.settings().waypoints.size() + "/" + WaypointSettings.MAX_WAYPOINTS + " total", width - 24);
        g.drawCenteredString(font, header, width / 2, 21, 0xFFB5C7D8);

        UiPaint.round(g, left - 2, listTop - 2, w + 4, listBottom - listTop + 4, 4, 0x80000000);
        g.enableScissor(left, listTop, left + w, listBottom);
        if (rows.isEmpty()) g.drawCenteredString(font, controller.settings().enabled ? "No waypoints on this server yet"
                : "No waypoints yet (the Waypoints module is off)", width / 2, listTop + 12, 0xFF8899AA);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop + i * ROW - (int) Math.round(scroll);
            if (y + ROW < listTop || y > listBottom) continue;
            drawRow(g, rows.get(i), y, mx, my);
        }
        g.disableScissor();
        if (rows.size() * ROW > listBottom - listTop) {
            double span = listBottom - listTop, total = rows.size() * ROW;
            int barH = (int) Math.max(12, span * span / total);
            int barY = listTop + (int) ((span - barH) * (scroll / Math.max(1, maxScroll())));
            g.fill(left + w - 2, barY, left + w, barY + barH, 0x80FFFFFF);
        }
        if (!status.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(status, width - 24), width / 2, height - 66, statusColor);
        super.render(g, mx, my, delta);
    }

    private void drawRow(GuiGraphics g, Waypoint waypoint, int y, int mx, int my) {
        boolean hover = mx >= left && mx <= left + w && my >= y && my < y + ROW && my >= listTop && my <= listBottom;
        if (waypoint == selected) g.fill(left, y, left + w, y + ROW, 0x40FFFFFF);
        else if (hover) g.fill(left, y, left + w, y + ROW, 0x20FFFFFF);
        int rgb = waypoint.color & 0xFFFFFF;
        UiPaint.round(g, left + 6, y + 7, 10, 10, 3, (waypoint.visible ? 0xFF000000 : 0x60000000) | rgb);
        String dimension = Waypoints.dimensionLabel(waypoint.dimension);
        double distance = controller.distanceTo(waypoint);
        String right = distance >= 0 ? String.format(Locale.ROOT, "%.0fm", distance) : dimension;
        int rightWidth = font.width(right);
        String name = waypoint.visible ? waypoint.name : waypoint.name + " (hidden)";
        g.drawString(font, font.plainSubstrByWidth(name, w - 40 - rightWidth), left + 22, y + 3,
                waypoint.visible ? 0xFFF0F5FA : 0xFF8899AA, false);
        g.drawString(font, right, left + w - 6 - rightWidth, y + 3, 0xFFB5C7D8, false);
        g.drawString(font, coords(waypoint) + "  " + dimension, left + 22, y + 13, 0xFF8899AA, false);
    }

    private static String coords(Waypoint waypoint) { return waypoint.x + ", " + waypoint.y + ", " + waypoint.z; }

    @Override public void onClose() {
        save.run();
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
