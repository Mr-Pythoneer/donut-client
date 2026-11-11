package dev.donut.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigProfiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Export the current settings and shortcuts to a local profile file, or import one.
 * Imports are validated first, back up donut-client.json, and never switch a module on or off.
 */
public final class ConfigProfileScreen extends Screen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final int ROW = 22;
    private final Screen parent;
    private final ClientConfig config;
    private final Runnable save;
    private List<Path> profiles = List.of();
    private Path selected;
    private ConfigProfiles.Profile preview;
    private String previewError = "", message = "";
    private boolean messageIsError, confirming;
    private int page;

    public ConfigProfileScreen(Screen parent, ClientConfig config, Runnable save) {
        super(Component.literal("Config profiles"));
        this.parent = parent;
        this.config = config;
        this.save = save;
    }

    @Override protected void init() {
        refresh();
        int w = Math.min(520, width - 32), left = (width - w) / 2, listW = (w - 8) / 2, top = 46;
        int bottom = height - 28, rows = Math.max(1, (bottom - 24 - ROW - top) / ROW);
        int pages = Math.max(1, (profiles.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * rows; i < Math.min(profiles.size(), (page + 1) * rows); i++) {
            Path path = profiles.get(i);
            String label = (path.equals(selected) ? "> " : "") + path.getFileName() + "  " + date(path);
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, listW - 10)), button -> select(path))
                    .bounds(left, top + (i - page * rows) * ROW, listW, 20).build());
        }
        if (pages > 1) {
            int y = top + rows * ROW, half = (listW - 4) / 2;
            Button previous = addRenderableWidget(Button.builder(Component.literal("< Newer"), button -> turn(-1)).bounds(left, y, half, 20).build());
            Button next = addRenderableWidget(Button.builder(Component.literal("Older > (" + (page + 1) + "/" + pages + ")"), button -> turn(1))
                    .bounds(left + half + 4, y, listW - half - 4, 20).build());
            previous.active = page > 0;
            next.active = page < pages - 1;
        }
        int b = (w - 24) / 4;
        addRenderableWidget(Button.builder(Component.literal("Export current settings"), button -> export()).bounds(left, bottom, b, 20).build());
        Button importButton = addRenderableWidget(Button.builder(Component.literal(confirming ? "Confirm import" : "Import selected"),
                button -> importSelected()).bounds(left + b + 8, bottom, b, 20).build());
        importButton.active = preview != null;
        addRenderableWidget(Button.builder(Component.literal("Open folder"), button -> openFolder()).bounds(left + 2 * (b + 8), bottom, b, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(left + 3 * (b + 8), bottom, w - 3 * (b + 8), 20).build());
    }

    private void refresh() {
        try {
            profiles = ConfigProfiles.list();
        } catch (IOException failure) {
            profiles = List.of();
            show("Could not list profiles: " + failure.getMessage(), true);
        }
        if (selected != null && !profiles.contains(selected)) {
            selected = null;
            preview = null;
            confirming = false;
        }
    }

    private void turn(int delta) { page += delta; rebuildWidgets(); }

    private void select(Path path) {
        selected = path;
        confirming = false;
        preview = null;
        previewError = "";
        if (path != null) {
            try {
                preview = ConfigProfiles.read(path);
            } catch (IllegalArgumentException failure) {
                previewError = failure.getMessage();
            }
        }
        if (minecraft != null) rebuildWidgets();
    }

    private void export() {
        try {
            Path file = ConfigProfiles.export(config, currentKeybinds(), modVersion());
            show("Exported " + file.getFileName(), false);
            page = 0;
            refresh();
            select(file);
        } catch (IOException | RuntimeException failure) {
            show("Export failed: " + failure.getMessage(), true);
        }
    }

    private void importSelected() {
        if (selected == null || preview == null) return;
        if (!confirming) {
            confirming = true;
            show("Click Confirm import to replace your settings. Your current file is backed up first.", false);
            rebuildWidgets();
            return;
        }
        confirming = false;
        ConfigProfiles.Profile profile;
        Path backup;
        try {
            profile = ConfigProfiles.read(selected); // Re-validate: the file may have changed since it was selected.
            save.run(); // Flush the live settings so the backup matches what is in use right now.
            backup = ConfigProfiles.backupCurrent(ConfigProfiles.configFile());
        } catch (IllegalArgumentException failure) {
            show("Nothing changed. " + failure.getMessage(), true);
            rebuildWidgets();
            return;
        } catch (IOException failure) {
            show("Nothing changed: could not back up your current settings (" + failure.getMessage() + ")", true);
            rebuildWidgets();
            return;
        }
        ConfigProfiles.copyInto(config, profile.config());
        int applied = applyKeybinds(profile.keybinds());
        save.run();
        show("Imported " + selected.getFileName() + ": " + applied + " shortcut(s) applied, modules left as they were"
                + (backup == null ? "." : ". Backup: " + backup.getFileName()), false);
        rebuildWidgets();
    }

    /** Applies saved shortcuts to the matching registered mappings; unknown names or key codes are skipped. */
    private int applyKeybinds(Map<String, String> keybinds) {
        Minecraft client = Minecraft.getInstance();
        int applied = 0;
        for (KeyMapping mapping : client.options.keyMappings) {
            String value = keybinds.get(mapping.getName());
            if (value == null) continue;
            try {
                mapping.setKey(InputConstants.getKey(value));
                applied++;
            } catch (IllegalArgumentException ignored) { }
        }
        if (applied > 0) {
            KeyMapping.resetMapping();
            client.options.save();
        }
        return applied;
    }

    private static Map<String, String> currentKeybinds() {
        Map<String, String> keys = new LinkedHashMap<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
            if (mapping.getName().startsWith(ConfigProfiles.KEY_PREFIX)) keys.put(mapping.getName(), mapping.saveString());
        return keys;
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("donut")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    private void openFolder() {
        try {
            Path dir = ConfigProfiles.profilesDir();
            Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
        } catch (IOException | RuntimeException failure) {
            show("Could not open the profiles folder: " + failure.getMessage(), true);
        }
    }

    private void show(String text, boolean error) {
        message = text == null ? "" : text;
        messageIsError = error;
    }

    private static String date(Path path) {
        return DATE.format(ConfigProfiles.modified(path).toInstant().atZone(ZoneId.systemDefault()));
    }

    @Override public void render(GuiGraphics graphics, int mx, int my, float delta) {
        graphics.fill(0, 0, width, height, 0xEE101722);
        int w = Math.min(520, width - 32), left = (width - w) / 2, listW = (w - 8) / 2, top = 46;
        int textX = left + listW + 8, textW = w - listW - 8;
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFF0F5FA);
        graphics.drawCenteredString(font, font.plainSubstrByWidth("Local files in config/donut-profiles - importing never turns a module on", width - 24),
                width / 2, 26, 0xFFB5C7D8);
        if (profiles.isEmpty())
            graphics.drawString(font, font.plainSubstrByWidth("No profiles yet - export one first", listW), left, top + 6, 0xFF8A9AAD);
        int y = top + 2;
        if (selected == null) {
            if (!profiles.isEmpty()) graphics.drawString(font, "Select a profile to preview it", textX, y, 0xFF8A9AAD);
        } else {
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (preview != null) for (String line : ConfigProfiles.summarize(preview)) lines.addAll(font.split(Component.literal(line), textW));
            else lines.addAll(font.split(Component.literal("Cannot import this file: " + previewError), textW));
            int color = preview != null ? 0xFFE2E9F0 : 0xFFFF8590, limit = height - 60;
            for (FormattedCharSequence line : lines) {
                if (y + 10 > limit) break;
                graphics.drawString(font, line, textX, y, color);
                y += 11;
            }
        }
        if (!message.isEmpty())
            graphics.drawCenteredString(font, font.plainSubstrByWidth(message, width - 24), width / 2, height - 42,
                    messageIsError ? 0xFFFF8590 : 0xFF8FE3B0);
        super.render(graphics, mx, my, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
