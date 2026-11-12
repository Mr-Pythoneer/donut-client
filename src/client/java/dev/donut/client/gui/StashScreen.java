package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.stash.StashEntry;
import dev.donut.client.stash.StashFinderController;
import dev.donut.client.waypoints.Waypoint;
import dev.donut.client.waypoints.WaypointController;
import dev.donut.client.waypoints.Waypoints;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Lists this server's logged stashes, newest first, with waypoint, copy, delete and export actions. Non-pausing. */
public final class StashScreen extends Screen {
    private static final int ROW = 24;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private final Screen parent;
    private final ClientConfig config;
    private final Runnable save;
    private final StashFinderController controller;
    private Button addWaypoint, copy, delete, export;
    private List<StashEntry> rows = List.of();
    private StashEntry selected, confirmDelete;
    private String status = "";
    private int statusColor = 0xFFB5C7D8;
    private double scroll;
    private int left, w, listTop, listBottom;

    public StashScreen(Screen parent, ClientConfig config, Runnable save) {
        super(Component.literal("Stash Finder"));
        this.parent = parent;
        this.config = config;
        this.save = save == null ? () -> { } : save;
        StashFinderController existing = StashFinderController.get();
        controller = existing != null ? existing : new StashFinderController(config);
    }

    @Override protected void init() {
        w = Math.min(460, width - 32);
        left = (width - w) / 2;
        listTop = 36;
        listBottom = height - 36;
        int fifth = (w - 16) / 5;
        addWaypoint = addRenderableWidget(Button.builder(Component.literal("Add waypoint"), button -> addWaypoint())
                .bounds(left, height - 28, fifth, 20).build());
        copy = addRenderableWidget(Button.builder(Component.literal("Copy coords"), button -> copy())
                .bounds(left + (fifth + 4), height - 28, fifth, 20).build());
        delete = addRenderableWidget(Button.builder(Component.literal("Delete"), button -> delete())
                .bounds(left + 2 * (fifth + 4), height - 28, fifth, 20).build());
        export = addRenderableWidget(Button.builder(Component.literal("Export"), button -> export())
                .bounds(left + 3 * (fifth + 4), height - 28, fifth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + 4 * (fifth + 4), height - 28, w - 4 * (fifth + 4), 20).build());
        refresh();
    }

    /** Selects the newest stash (used by tests and the "open after find" flow). */
    public void selectFirst() {
        refresh();
        if (!rows.isEmpty()) select(rows.getFirst());
        refresh();
    }

    public StashEntry selected() { return selected; }
    public String status() { return status; }

    public void addWaypoint() {
        var waypoints = WaypointController.get();
        if (selected == null || waypoints == null) return;
        if (!selected.dimension.equals(waypoints.currentDimension())) {
            setStatus("Go to " + Waypoints.dimensionLabel(selected.dimension) + " to add this waypoint", 0xFFFF8590);
            return;
        }
        Waypoint added = waypoints.add("Stash " + selected.score, new BlockPos(selected.x, selected.y, selected.z));
        if (added == null) setStatus(waypoints.settings().hasRoom() ? "No world loaded" : "Waypoint limit reached", 0xFFFF8590);
        else setStatus("Waypoint \"" + added.name + "\" added at " + coords(selected), 0xFF9BE59B);
    }

    public void copy() {
        if (selected == null) return;
        minecraft.keyboardHandler.setClipboard(selected.x + " " + selected.y + " " + selected.z);
        setStatus("Copied " + selected.x + " " + selected.y + " " + selected.z, 0xFF9BE59B);
    }

    public void delete() {
        if (selected == null) return;
        if (confirmDelete != selected) { confirmDelete = selected; refresh(); return; }
        controller.remove(selected);
        select(null);
        setStatus("Stash deleted", 0xFFB5C7D8);
        refresh();
    }

    public void export() {
        try {
            var file = controller.export();
            setStatus("Exported " + rows.size() + " to config/" + StashFinderController.DIRECTORY + "/" + file.getFileName(), 0xFF9BE59B);
        } catch (Exception failure) {
            setStatus("Export failed: " + failure.getMessage(), 0xFFFF8590);
        }
    }

    private void select(StashEntry entry) {
        selected = entry;
        confirmDelete = null;
    }

    private void setStatus(String text, int argb) { status = text; statusColor = argb; }

    private void refresh() {
        rows = controller.forCurrentServer();
        if (selected != null && !rows.contains(selected)) select(null);
        scroll = Math.clamp(scroll, 0, maxScroll());
        if (addWaypoint == null) return;
        boolean has = selected != null;
        addWaypoint.active = copy.active = delete.active = has;
        export.active = !rows.isEmpty();
        delete.setMessage(Component.literal(has && confirmDelete == selected ? "Confirm" : "Delete"));
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
        String header = font.plainSubstrByWidth(controller.server() + "  |  " + rows.size() + " logged  |  threshold "
                + config.stashFinder.threshold + (config.stashFinder.enabled ? "" : "  |  module off"), width - 24);
        g.drawCenteredString(font, header, width / 2, 21, 0xFFB5C7D8);
        UiPaint.round(g, left - 2, listTop - 2, w + 4, listBottom - listTop + 4, 4, 0x80000000);
        g.enableScissor(left, listTop, left + w, listBottom);
        if (rows.isEmpty()) g.drawCenteredString(font, config.stashFinder.enabled ? "No stashes found on this server yet"
                : "No stashes yet (Stash Finder is off)", width / 2, listTop + 12, 0xFF8899AA);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop + i * ROW - (int) Math.round(scroll);
            if (y + ROW < listTop || y > listBottom) continue;
            drawRow(g, rows.get(i), y, mx, my);
        }
        g.disableScissor();
        if (!status.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(status, width - 24), width / 2, height - 40, statusColor);
        super.render(g, mx, my, delta);
    }

    private void drawRow(GuiGraphics g, StashEntry entry, int y, int mx, int my) {
        boolean hover = mx >= left && mx <= left + w && my >= y && my < y + ROW && my >= listTop && my <= listBottom;
        if (entry == selected) g.fill(left, y, left + w, y + ROW, 0x40FFFFFF);
        else if (hover) g.fill(left, y, left + w, y + ROW, 0x20FFFFFF);
        String when = WHEN.format(Instant.ofEpochMilli(entry.foundAt).atZone(ZoneId.systemDefault()));
        int whenW = font.width(when);
        g.drawString(font, font.plainSubstrByWidth(entry.score + " storage  " + coords(entry), w - 20 - whenW), left + 8, y + 3, 0xFFF0F5FA, false);
        g.drawString(font, when, left + w - 6 - whenW, y + 3, 0xFFB5C7D8, false);
        g.drawString(font, font.plainSubstrByWidth(Waypoints.dimensionLabel(entry.dimension) + "  chunk " + entry.chunkX + ", " + entry.chunkZ
                + "  " + entry.summary(), w - 16), left + 8, y + 13, 0xFF8899AA, false);
    }

    private static String coords(StashEntry entry) { return entry.x + ", " + entry.y + ", " + entry.z; }

    @Override public void onClose() {
        save.run();
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
