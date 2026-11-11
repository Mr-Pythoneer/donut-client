package dev.donut.client.gui;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.FreecamSettings;
import dev.donut.client.freecam.FreecamController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit X/Y/Z editor for Freecam's look-at helper; invalid or cancelled input keeps the saved target. */
public final class FreecamTargetScreen extends Screen {
    private final Screen parent;
    private final ClientConfig config;
    private final Runnable save;
    private EditBox xField, yField, zField;
    private String x, y, z, error = "";

    public FreecamTargetScreen(Screen parent, ClientConfig config, Runnable save) {
        super(Component.literal("Freecam look-at target"));
        this.parent = parent;
        this.config = config;
        this.save = save;
        x = Integer.toString(config.freecam.targetX);
        y = Integer.toString(config.freecam.targetY);
        z = Integer.toString(config.freecam.targetZ);
    }

    @Override protected void init() {
        int w = Math.min(330, width - 32), left = (width - w) / 2, top = height / 2 - 25;
        int field = (w - 16) / 3;
        xField = coordinate(left, top, field, x, "X", value -> x = value);
        yField = coordinate(left + field + 8, top, field, y, "Y", value -> y = value);
        zField = coordinate(left + 2 * (field + 8), top, field, z, "Z", value -> z = value);
        setInitialFocus(xField);
        addRenderableWidget(Button.builder(Component.literal("Use camera position"), button -> useCamera())
                .bounds(left, top + 30, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply target"), button -> apply())
                .bounds(left, top + 55, (w - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + (w + 8) / 2, top + 55, (w - 8) / 2, 20).build());
    }

    private EditBox coordinate(int left, int top, int w, String value, String label, java.util.function.Consumer<String> responder) {
        EditBox box = addRenderableWidget(new EditBox(font, left, top, w, 22, Component.literal(label)));
        box.setMaxLength(10);
        box.setValue(value);
        box.setHint(Component.literal(label));
        box.setResponder(responder);
        return box;
    }

    /** Fills the fields from where the camera (or the player, if Freecam is off) currently is. */
    private void useCamera() {
        var freecam = FreecamController.getInstance();
        var pos = freecam != null && freecam.isActive() ? freecam.position()
                : minecraft.player != null ? minecraft.player.getEyePosition() : null;
        if (pos == null) return;
        xField.setValue(Integer.toString((int) Math.floor(pos.x)));
        yField.setValue(Integer.toString((int) Math.floor(pos.y)));
        zField.setValue(Integer.toString((int) Math.floor(pos.z)));
    }

    private void apply() {
        try {
            int nx = FreecamSettings.parseCoordinate(x, false);
            int ny = FreecamSettings.parseCoordinate(y, true);
            int nz = FreecamSettings.parseCoordinate(z, false);
            config.freecam.targetX = nx;
            config.freecam.targetY = ny;
            config.freecam.targetZ = nz;
            save.run();
            onClose();
        } catch (IllegalArgumentException failure) { error = failure.getMessage(); }
    }

    @Override public void render(GuiGraphics graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0xEE101722);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFF0F5FA);
        graphics.drawCenteredString(font, "The camera turns to face this block while Look at target is on", width / 2, height / 2 - 54, 0xFFB5C7D8);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(error, width - 24), width / 2, height / 2 + 82, 0xFFFF8590);
        super.render(graphics, mx, my, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
