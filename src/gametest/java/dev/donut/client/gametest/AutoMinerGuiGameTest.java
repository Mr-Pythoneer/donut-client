package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.DonutClient;
import dev.donut.client.automine.OreKind;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/** Auto Miner in the real menu: category, search, favorites, ore and safety settings, shortcut and array-list row. */
public final class AutoMinerGuiGameTest implements FabricClientGameTest {
    private static final int MODULE = 20;

    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("autominer-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        String key = context.computeOnClient(mc -> ModuleKeybinds.mapping(MODULE).saveString());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            // Category placement: Blatant, not Visual.
            GuiTestInput.click(context, "tab0");
            require(!GuiTestInput.visible(context, "module" + MODULE), "Auto Miner is listed under Visual");
            GuiTestInput.click(context, "tab3");
            require(GuiTestInput.visible(context, "module" + MODULE), "Auto Miner is missing from Blatant");
            // Search by module words and by a setting name.
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "tunnel");
            require(GuiTestInput.visible(context, "module" + MODULE), "Auto Miner missing from the 'tunnel' search");
            GuiTestInput.click(context, "module" + MODULE);
            require(!config.autoMiner.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite" + MODULE);
            require(store.load().autoMiner.favorite, "Favorite did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".ore" + OreKind.COAL.id);
            GuiTestInput.click(context, "m" + MODULE + ".minerBuried");
            var saved = store.load().autoMiner;
            require(saved.oreEnabled(OreKind.COAL) && saved.oreEnabled(OreKind.DIAMOND), "Ore toggle did not persist");
            require(saved.includeBuried, "Buried-ore preference did not persist");
            require(saved.preferFortune, "Prefer Fortune should default on");
            GuiTestInput.click(context, "m" + MODULE + ".minerFortune");
            require(!store.load().autoMiner.preferFortune, "Prefer Fortune did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".minerRange"); // Midpoint of 8-64.
            context.waitTicks(2);
            context.runOnClient(mc -> require(config.autoMiner.range >= 34 && config.autoMiner.range <= 38, "Range slider: " + config.autoMiner.range));
            GuiTestInput.click(context, "enable" + MODULE);
            require(config.autoMiner.enabled && store.load().autoMiner.enabled, "The module switch did not enable Auto Miner");
            GuiTestInput.click(context, "enable" + MODULE);
            require(!config.autoMiner.enabled, "The module switch did not disable Auto Miner");
            context.takeScreenshot(TestScreenshotOptions.of("auto-miner-gui-01-settings").withSize(1280, 800));

            // Shortcut capture, then the real production toggle and array-list row.
            GuiTestInput.click(context, "keybind" + MODULE); context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            GuiTestInput.closeAnimated(context);
            var production = DonutClient.config();
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(production.autoMiner.enabled, "Production Auto Miner shortcut did not enable it");
            context.waitFor(mc -> rowNames().contains("Auto Miner"), 200);
            context.takeScreenshot(TestScreenshotOptions.of("auto-miner-gui-02-arraylist").withSize(1280, 800));
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(!production.autoMiner.enabled, "Production Auto Miner shortcut did not disable it");

            // GUI scale 2.
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeDisplay(); });
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "ancient");
            require(GuiTestInput.visible(context, "module" + MODULE), "Auto Miner missing from the 'ancient' search at scale 2");
            GuiTestInput.click(context, "module" + MODULE);
            GuiTestInput.click(context, "m" + MODULE + ".minerHud");
            require(!store.load().autoMiner.showHud, "HUD preference did not persist");
            context.takeScreenshot(TestScreenshotOptions.of("auto-miner-gui-03-settings-scale2").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
        } finally {
            context.runOnClient(mc -> {
                DonutClient.config().autoMiner.enabled = false;
                ModuleKeybinds.setKey(mc, MODULE, com.mojang.blaze3d.platform.InputConstants.getKey(key));
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
