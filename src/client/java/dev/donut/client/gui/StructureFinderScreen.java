package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.structurefinder.StructureFinderController;
import dev.donut.client.structurefinder.StructureSearch;
import dev.donut.client.waypoints.WaypointController;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Type a seed, find vanilla structures from it (the game's own locate logic), and save any as a waypoint. */
public final class StructureFinderScreen extends Screen {
    private static final int ROW = 22;
    private static final int[] RADII = {500, 1000, 1500, 2000, 3000};
    private final Screen parent;
    private final ClientConfig config;
    private final Runnable save;
    private final StructureFinderController controller;

    private EditBox seedBox;
    private Button findButton, stopButton, modeButton, radiusButton, addWaypoint, addAll, copyButton;
    private List<StructureSearch.Hit> rows = List.of();
    private StructureSearch.Hit selected;
    private BlockPos searchCenter = BlockPos.ZERO;
    private String status = "";
    private int statusColor = 0xFFB5C7D8;
    private double scroll;
    private int left, w, listTop, listBottom;

    public StructureFinderScreen(Screen parent, ClientConfig config, Runnable save) {
        super(Component.literal("Seed Structure Finder"));
        this.parent = parent;
        this.config = config;
        this.save = save == null ? () -> { } : save;
        StructureFinderController existing = StructureFinderController.get();
        controller = existing != null ? existing : new StructureFinderController(config);
    }

    @Override protected void init() {
        w = Math.min(520, width - 32);
        left = (width - w) / 2;
        listTop = 78;
        listBottom = height - 36;
        searchCenter = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;

        seedBox = new EditBox(font, left, 30, w - 96, 18, Component.literal("Seed"));
        seedBox.setMaxLength(64);
        seedBox.setHint(Component.literal("world seed (number or text)"));
        String initial = config.structureFinder.lastSeed;
        if (initial.isEmpty() && minecraft.getSingleplayerServer() != null)
            initial = Long.toString(minecraft.getSingleplayerServer().overworld().getSeed());
        seedBox.setValue(initial);
        addRenderableWidget(seedBox);

        findButton = addRenderableWidget(Button.builder(Component.literal("Find"), b -> find())
                .bounds(left + w - 92, 30, 44, 18).build());
        stopButton = addRenderableWidget(Button.builder(Component.literal("Stop"), b -> { controller.cancel(); refresh(); })
                .bounds(left + w - 44, 30, 44, 18).build());

        int third = (w - 8) / 3;
        modeButton = addRenderableWidget(Button.builder(Component.literal(""), b -> toggleMode())
                .bounds(left, 54, third, 18).build());
        radiusButton = addRenderableWidget(Button.builder(Component.literal(""), b -> cycleRadius())
                .bounds(left + third + 4, 54, third, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Types in module settings"), b -> { })
                .bounds(left + 2 * (third + 4), 54, w - 2 * (third + 4), 18).build()).active = false;

        int quarter = (w - 12) / 4;
        addWaypoint = addRenderableWidget(Button.builder(Component.literal("Add waypoint"), b -> addSelected())
                .bounds(left, height - 28, quarter, 20).build());
        addAll = addRenderableWidget(Button.builder(Component.literal("Add all"), b -> addAll())
                .bounds(left + quarter + 4, height - 28, quarter, 20).build());
        copyButton = addRenderableWidget(Button.builder(Component.literal("Copy coords"), b -> copy())
                .bounds(left + 2 * (quarter + 4), height - 28, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + 3 * (quarter + 4), height - 28, w - 3 * (quarter + 4), 20).build());
        refresh();
    }

    private void find() {
        long seed = parseSeed(seedBox.getValue());
        config.structureFinder.lastSeed = seedBox.getValue().trim();
        save.run();
        searchCenter = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        selected = null;
        controller.search(seed, searchCenter, config.structureFinder);
        setStatus("Searching...", 0xFFB5C7D8);
    }

    /** Minecraft's own rule: a number is the seed, anything else is hashed; empty uses the current world. */
    long parseSeed(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty())
            return minecraft.getSingleplayerServer() != null ? minecraft.getSingleplayerServer().overworld().getSeed() : 0L;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException notANumber) {
            return trimmed.hashCode();
        }
    }

    private void toggleMode() {
        config.structureFinder.nearestOnly = !config.structureFinder.nearestOnly;
        save.run();
        refresh();
    }

    private void cycleRadius() {
        int current = config.structureFinder.radius, next = RADII[0];
        for (int i = 0; i < RADII.length; i++) if (RADII[i] == current) next = RADII[(i + 1) % RADII.length];
        config.structureFinder.radius = next;
        save.run();
        refresh();
    }

    private void addSelected() {
        if (selected == null || WaypointController.get() == null) return;
        WaypointController.get().add(selected.groupLabel(), selected.pos());
        setStatus("Added waypoint: " + selected.groupLabel() + " " + coords(selected.pos())
                + (inDimension(selected) ? "" : " (in your current dimension, not " + dimensionName(selected.dimension()) + ")"), 0xFF9BE59B);
    }

    private void addAll() {
        var waypoints = WaypointController.get();
        if (waypoints == null || rows.isEmpty()) return;
        int added = 0;
        for (var hit : rows) {
            if (added >= 64) break;
            if (waypoints.add(hit.groupLabel(), hit.pos()) != null) added++;
        }
        setStatus("Added " + added + " waypoints (closest first)" + (rows.size() > 64 ? ", capped at 64" : ""), 0xFF9BE59B);
    }

    private void copy() {
        if (selected == null) return;
        minecraft.keyboardHandler.setClipboard(selected.pos().getX() + " " + selected.pos().getY() + " " + selected.pos().getZ());
        setStatus("Copied " + coords(selected.pos()), 0xFFB5C7D8);
    }

    private void refresh() {
        rows = controller.results();
        if (selected != null && !rows.contains(selected)) selected = null;
        scroll = Math.clamp(scroll, 0, maxScroll());
        if (!controller.status().isEmpty() && controller.phase() != StructureFinderController.Phase.IDLE) {
            status = controller.status();
            statusColor = controller.phase() == StructureFinderController.Phase.ERROR ? 0xFFFF8590 : 0xFFB5C7D8;
        }
        if (findButton == null) return;
        boolean busy = controller.running();
        findButton.active = !busy;
        stopButton.active = busy;
        addWaypoint.active = copyButton.active = selected != null;
        addAll.active = !rows.isEmpty();
        modeButton.setMessage(Component.literal(config.structureFinder.nearestOnly ? "Mode: Nearest of each" : "Mode: Area scan"));
        radiusButton.setMessage(Component.literal(config.structureFinder.nearestOnly ? "Radius: n/a" : "Radius: " + config.structureFinder.radius));
        radiusButton.active = !config.structureFinder.nearestOnly;
    }

    private double maxScroll() { return Math.max(0, rows.size() * ROW - (listBottom - listTop)); }

    @Override public void tick() { refresh(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (mx < left || mx > left + w || my < listTop || my > listBottom) return false;
        int index = (int) Math.floor((my - listTop + scroll) / ROW);
        selected = index >= 0 && index < rows.size() ? rows.get(index) : null;
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
        g.drawString(font, "Seed", left, 20, 0xFFB5C7D8, false);
        UiPaint.round(g, left - 2, listTop - 2, w + 4, listBottom - listTop + 4, 4, 0x80000000);
        g.enableScissor(left, listTop, left + w, listBottom);
        if (rows.isEmpty()) g.drawCenteredString(font, controller.running() ? "Searching the seed..."
                : "Type a seed and press Find", width / 2, listTop + 12, 0xFF8899AA);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop + i * ROW - (int) Math.round(scroll);
            if (y + ROW < listTop || y > listBottom) continue;
            drawRow(g, rows.get(i), y, mx, my);
        }
        g.disableScissor();
        if (!status.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(status, width - 24), width / 2, height - 42, statusColor);
        super.render(g, mx, my, delta);
    }

    private void drawRow(GuiGraphics g, StructureSearch.Hit hit, int y, int mx, int my) {
        boolean hover = mx >= left && mx <= left + w && my >= y && my < y + ROW && my >= listTop && my <= listBottom;
        if (hit == selected) g.fill(left, y, left + w, y + ROW, 0x40FFFFFF);
        else if (hover) g.fill(left, y, left + w, y + ROW, 0x20FFFFFF);
        g.drawString(font, hit.groupLabel(), left + 6, y + 7, 0xFFF0F5FA, false);
        String right = coords(hit.pos()) + "  ·  " + dimensionName(hit.dimension()) + "  ·  " + distance(hit) + "m";
        g.drawString(font, right, left + w - 6 - font.width(right), y + 7, 0xFFB5C7D8, false);
    }

    private int distance(StructureSearch.Hit hit) {
        long dx = hit.pos().getX() - searchCenter.getX(), dz = hit.pos().getZ() - searchCenter.getZ();
        return (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    private boolean inDimension(StructureSearch.Hit hit) {
        return minecraft.level != null && minecraft.level.dimension().equals(hit.dimension());
    }

    private static String dimensionName(net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim.equals(Level.NETHER)) return "Nether";
        if (dim.equals(Level.END)) return "The End";
        return "Overworld";
    }

    private static String coords(BlockPos pos) { return "(" + pos.getX() + ", " + pos.getZ() + ")"; }

    private void setStatus(String text, int argb) { status = text; statusColor = argb; }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
