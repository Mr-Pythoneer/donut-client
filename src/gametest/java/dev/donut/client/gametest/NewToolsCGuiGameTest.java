package dev.donut.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.gui.StashScreen;
import dev.donut.client.hudwidgets.HudWidget;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.storageesp.StorageKind;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/**
 * Menu tiles, gaps in the module IDs, search, favorites, settings persistence, shortcut capture and the array-list row
 * for HUD Widgets (24), NewChunks (25) and Stash Finder (26).
 */
public final class NewToolsCGuiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        config.sanitize();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("newtools-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        String[] keys = context.computeOnClient(mc -> new String[] {ModuleKeybinds.mapping(24).saveString(),
                ModuleKeybinds.mapping(25).saveString(), ModuleKeybinds.mapping(26).saveString()});
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));

            // IDs 21-23 and 27-28 belong to modules not in this build: no tile, no count, nothing matches them.
            context.runOnClient(mc -> {
                try {
                    Method matches = DonutScreen.class.getDeclaredMethod("matches", int.class);
                    matches.setAccessible(true);
                    for (int id = ModuleKeybinds.FIRST_MODULE; id < ModuleKeybinds.MODULE_LIMIT; id++) {
                        boolean expected = ModuleKeybinds.isModule(id);
                        require((boolean) matches.invoke(mc.screen, id) == expected, "Module " + id + " tile shown: " + !expected);
                    }
                    for (int id : new int[] {21, 22, 23, 27, 28}) require(!ModuleKeybinds.isModule(id), "Unexpected module " + id);
                    for (int id = 24; id <= 26; id++) require(ModuleKeybinds.isModule(id), "Missing module " + id);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            // All three live under Visual.
            GuiTestInput.click(context, "tab0");
            for (int id = 24; id <= 26; id++) GuiTestInput.reveal(context, "module" + id);
            GuiTestInput.click(context, "tab3");
            require(!GuiTestInput.visible(context, "module24") && !GuiTestInput.visible(context, "module26"), "Listed under Blatant");
            GuiTestInput.click(context, "tab5");

            // HUD Widgets: found by a widget name; favorite, widget toggle and corner persist.
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "keystrokes");
            require(GuiTestInput.visible(context, "module24"), "HUD Widgets missing from the 'keystrokes' search");
            GuiTestInput.click(context, "module24");
            require(!config.hudWidgets.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite24");
            require(store.load().hudWidgets.favorite, "Favorite did not persist");
            GuiTestInput.click(context, "m24.widget-" + HudWidget.CPS.id);
            require(store.load().hudWidgets.widgetEnabled(HudWidget.CPS), "CPS toggle did not persist");
            GuiTestInput.click(context, "m24.corner-" + HudWidget.FPS.id);
            require(store.load().hudWidgets.corner(HudWidget.FPS).equals("Bottom left"), "Corner: " + store.load().hudWidgets.corner(HudWidget.FPS));
            GuiTestInput.click(context, "m24.clock24h");
            require(!store.load().hudWidgets.clock24h, "Clock preference did not persist");
            GuiTestInput.click(context, "enable24");
            require(store.load().hudWidgets.enabled, "The tile switch did not enable HUD Widgets");
            GuiTestInput.click(context, "enable24");
            screenshot(context, "new-tools-gui-01-hud-widgets");

            // NewChunks.
            GuiTestInput.click(context, "search"); clearSearch(context); GuiTestInput.type(context, "new chunks");
            require(GuiTestInput.visible(context, "module25"), "NewChunks missing from the 'new chunks' search");
            GuiTestInput.click(context, "module25");
            GuiTestInput.click(context, "m25.showOld");
            GuiTestInput.click(context, "m25.throughWalls");
            GuiTestInput.click(context, "m25.range"); // midpoint of 2-64
            context.waitTicks(2);
            var newChunks = store.load().newChunks;
            require(newChunks.showOld && !newChunks.throughWalls, "NewChunks toggles did not persist");
            context.runOnClient(mc -> require(config.newChunks.range >= 30 && config.newChunks.range <= 36, "Range slider: " + config.newChunks.range));
            screenshot(context, "new-tools-gui-02-new-chunks");

            // Stash Finder: threshold slider, a weight, the list screen.
            GuiTestInput.click(context, "search"); clearSearch(context); GuiTestInput.type(context, "stash");
            require(GuiTestInput.visible(context, "module26"), "Stash Finder missing from the 'stash' search");
            GuiTestInput.click(context, "module26");
            GuiTestInput.click(context, "m26.threshold"); // midpoint of 2-128
            GuiTestInput.click(context, "m26.weight-" + StorageKind.HOPPERS.id); // midpoint of 0-5
            GuiTestInput.click(context, "m26.toast");
            context.waitTicks(2);
            var stash = store.load().stashFinder;
            require(stash.toast, "Toast toggle did not persist");
            context.runOnClient(mc -> {
                require(config.stashFinder.threshold >= 60 && config.stashFinder.threshold <= 70, "Threshold slider: " + config.stashFinder.threshold);
                require(config.stashFinder.weight(StorageKind.HOPPERS) >= 2 && config.stashFinder.weight(StorageKind.HOPPERS) <= 3,
                        "Weight slider: " + config.stashFinder.weight(StorageKind.HOPPERS));
            });
            screenshot(context, "new-tools-gui-03-stash-finder");
            GuiTestInput.clickOpeningScreen(context, "m26.list");
            context.waitForScreen(StashScreen.class);
            screenshot(context, "new-tools-gui-04-stash-list");
            context.runOnClient(mc -> mc.screen.onClose());
            context.waitForScreen(DonutScreen.class);
            GuiTestInput.settle(context);

            // Shortcut capture, then the production toggle and the array-list row, for each of the three.
            GuiTestInput.click(context, "search"); clearSearch(context);
            int[] testKeys = {GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_F10};
            for (int i = 0; i < 3; i++) {
                int id = 24 + i;
                GuiTestInput.click(context, "keybind" + id);
                context.getInput().pressKey(testKeys[i]);
                int key = testKeys[i];
                context.runOnClient(mc -> require(ModuleKeybinds.mapping(id).saveString().equals(InputConstants.Type.KEYSYM.getOrCreate(key).getName()),
                        "Key capture failed for module " + id));
            }
            GuiTestInput.closeAnimated(context);
            var production = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("donut-client.json"));
            String[] rows = {"HUD Widgets", "NewChunks", "Stash Finder"};
            for (int i = 0; i < 3; i++) {
                boolean before = enabled(production.load(), i);
                context.getInput().pressKey(testKeys[i]);
                context.waitTicks(3);
                require(enabled(production.load(), i) != before, "Production shortcut did not toggle " + rows[i]);
                String row = rows[i];
                if (!before) context.waitFor(mc -> rowNames().contains(row), 200);
                context.getInput().pressKey(testKeys[i]);
                context.waitTicks(3);
                require(enabled(production.load(), i) == before, "Production shortcut did not restore " + rows[i]);
            }
        } finally {
            context.runOnClient(mc -> {
                for (int i = 0; i < 3; i++) ModuleKeybinds.setKey(mc, 24 + i, InputConstants.getKey(keys[i]));
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
            });
            try { Files.deleteIfExists(fixture); } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static boolean enabled(ClientConfig config, int slot) {
        return slot == 0 ? config.hudWidgets.enabled : slot == 1 ? config.newChunks.enabled : config.stashFinder.enabled;
    }

    private static void clearSearch(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            for (int i = 0; i < 40; i++) mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        });
        GuiTestInput.settle(context);
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

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
