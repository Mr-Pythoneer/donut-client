package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.storageesp.StorageEspScanner;
import dev.donut.client.storageesp.StorageKind;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/** Real menu search/category, favorites, per-kind settings, sliders, shortcut capture and the array-list entry. */
public final class StorageEspGuiGameTest implements FabricClientGameTest {
    private static final int MODULE = 15;

    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("storageesp-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        String key = context.computeOnClient(mc -> ModuleKeybinds.mapping(MODULE).saveString());
        boolean showHud = context.computeOnClient(mc -> StorageEspScanner.get().settings().showHud);
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "chest");
            require(GuiTestInput.visible(context, "module" + MODULE), "Storage ESP missing from the 'chest' search");
            GuiTestInput.click(context, "module" + MODULE);
            require(!config.storageEsp.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite" + MODULE);
            require(store.load().storageEsp.favorite, "Favorite did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".kind-" + StorageKind.HOPPERS.id);
            GuiTestInput.click(context, "m" + MODULE + ".tracers");
            var saved = store.load().storageEsp;
            require(!saved.kindEnabled(StorageKind.HOPPERS) && saved.kindEnabled(StorageKind.CHESTS), "Kind toggle did not persist");
            require(saved.tracers, "Tracer preference did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".range"); // Range slider midpoint (16-512).
            context.waitTicks(2);
            GuiTestInput.click(context, "m" + MODULE + ".maxTargets");
            context.runOnClient(mc -> {
                require(config.storageEsp.range >= 256 && config.storageEsp.range <= 272, "Range slider: " + config.storageEsp.range);
                require(config.storageEsp.maxTargets > 512 && config.storageEsp.maxTargets < 1300, "Target slider: " + config.storageEsp.maxTargets);
            });
            GuiTestInput.click(context, "enable" + MODULE);
            require(config.storageEsp.enabled && store.load().storageEsp.enabled, "The module switch did not enable Storage ESP");
            GuiTestInput.click(context, "enable" + MODULE);
            require(!config.storageEsp.enabled, "The module switch did not disable Storage ESP");
            context.takeScreenshot(TestScreenshotOptions.of("storage-esp-gui-01-settings").withSize(1280, 800));

            // Shortcut capture, then the real production toggle and array-list row.
            GuiTestInput.click(context, "keybind" + MODULE); context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            GuiTestInput.closeAnimated(context);
            var production = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("donut-client.json"));
            boolean before = production.load().storageEsp.enabled;
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(production.load().storageEsp.enabled != before, "Production Storage ESP shortcut did not toggle");
            if (!before) {
                context.waitFor(mc -> rowNames().contains("Storage ESP"), 200);
                context.takeScreenshot(TestScreenshotOptions.of("storage-esp-gui-02-arraylist").withSize(1280, 800));
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(production.load().storageEsp.enabled == before, "Production Storage ESP shortcut did not restore state");

            // GUI scale 2 with another search term.
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeDisplay(); });
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "shulker");
            require(GuiTestInput.visible(context, "module" + MODULE), "Storage ESP missing from the 'shulker' search at scale 2");
            GuiTestInput.click(context, "module" + MODULE);
            GuiTestInput.click(context, "m" + MODULE + ".showHud");
            require(!store.load().storageEsp.showHud, "HUD preference did not persist");
            context.takeScreenshot(TestScreenshotOptions.of("storage-esp-gui-03-settings-scale2").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
        } finally {
            context.runOnClient(mc -> {
                ModuleKeybinds.setKey(mc, MODULE, com.mojang.blaze3d.platform.InputConstants.getKey(key));
                StorageEspScanner.get().settings().showHud = showHud;
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            });
            try { Files.deleteIfExists(fixture); } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static java.util.Set<String> rowNames() {
        var names = new java.util.HashSet<String>();
        for (Object row : (java.util.List<?>) field(ModuleArrayList.class, "rows")) names.add((String) field(row, "name"));
        return names;
    }

    private static Object field(Object owner, String name) {
        try {
            var field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner instanceof Class<?> ? null : owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
