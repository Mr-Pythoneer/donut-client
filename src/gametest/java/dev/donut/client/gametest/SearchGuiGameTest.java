package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.input.ModuleKeybinds;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/**
 * Search in the real menu: tile and search terms, typed block IDs with validation and suggestions, "Add looked-at
 * block", remove, settings persistence, gaps in module IDs, shortcut capture and the array-list row.
 */
public final class SearchGuiGameTest implements FabricClientGameTest {
    private static final int MODULE = 23;

    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("search-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        String key = context.computeOnClient(mc -> ModuleKeybinds.mapping(MODULE).saveString());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -4 64 -4 4 64 4 minecraft:smooth_stone");
            world.getServer().runCommand("setblock 0 64 0 minecraft:emerald_block");
            world.getServer().runCommand("tp @a 0.5 65 0.5 0 90");
            context.waitTicks(5);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && mc.level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK), 100);

            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            // IDs without a module never show a tile; the new IDs do.
            context.runOnClient(mc -> {
                for (int id = ModuleKeybinds.FIRST_MODULE; id < ModuleKeybinds.MODULE_LIMIT; id++)
                    require(matches(mc.screen, id) == ModuleKeybinds.exists(id), "Tile visibility for ID " + id + " does not follow module existence");
                require(matches(mc.screen, 22) && matches(mc.screen, MODULE), "Player ESP / Search tiles are missing");
            });
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "xray");
            require(GuiTestInput.visible(context, "module" + MODULE), "Search missing from the 'xray' search");
            GuiTestInput.click(context, "module" + MODULE);
            require(!config.search.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite" + MODULE);
            require(store.load().search.favorite, "Favorite did not persist");

            // Typed ID with suggestions: pick the first suggestion for "diamond_or".
            GuiTestInput.click(context, "m" + MODULE + ".blockInput");
            GuiTestInput.type(context, "diamond_or");
            require(GuiTestInput.visible(context, "m" + MODULE + ".suggest0"), "No suggestions for 'diamond_or'");
            context.takeScreenshot(TestScreenshotOptions.of("search-gui-01-suggestions").withSize(1280, 800));
            GuiTestInput.click(context, "m" + MODULE + ".suggest0");
            require(store.load().search.find("minecraft:diamond_ore") != null, "The suggested block was not added and saved");
            // An unknown ID is refused with a reason and the list is unchanged.
            GuiTestInput.click(context, "m" + MODULE + ".blockInput");
            GuiTestInput.type(context, "notablock");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            GuiTestInput.settle(context);
            context.runOnClient(mc -> {
                require(config.search.blocks.size() == 1, "An unknown ID was added");
                String message = (String) ((java.util.Map<?, ?>) field(mc.screen, "entryMessages")).get("m23.blockInput");
                require(message != null && message.startsWith("Unknown block minecraft:notablock"), "No validation message: " + message);
            });
            // A valid typed ID with Enter (Backspace clears the rejected draft first).
            context.runOnClient(mc -> ((java.util.Map<String, String>) field(mc.screen, "entryDrafts")).put("m23.blockInput", ""));
            GuiTestInput.click(context, "m" + MODULE + ".blockInput");
            GuiTestInput.type(context, "Spawner");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            GuiTestInput.settle(context);
            require(store.load().search.find("minecraft:spawner") != null, "Typed ID + Enter did not add the spawner");
            // The block under the crosshair.
            GuiTestInput.click(context, "m" + MODULE + ".addLooked");
            require(store.load().search.find("minecraft:emerald_block") != null, "Add looked-at block did not add the emerald block");
            require(store.load().search.blocks.size() == 3, "Expected three blocks: " + store.load().search.blocks.size());
            context.takeScreenshot(TestScreenshotOptions.of("search-gui-02-list").withSize(1280, 800));
            // Remove the first entry.
            GuiTestInput.click(context, "m" + MODULE + ".remove0");
            require(store.load().search.find("minecraft:diamond_ore") == null && store.load().search.blocks.size() == 2, "Remove did not persist");

            GuiTestInput.click(context, "m" + MODULE + ".mergeAdjacent");
            GuiTestInput.click(context, "m" + MODULE + ".tracers");
            var saved = store.load().search;
            require(!saved.mergeAdjacent && saved.tracers, "Merge/tracer toggles did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".range"); // Midpoint of 16-512.
            context.waitTicks(2);
            context.runOnClient(mc -> require(config.search.range >= 256 && config.search.range <= 272, "Range slider: " + config.search.range));
            GuiTestInput.click(context, "enable" + MODULE);
            require(config.search.enabled && store.load().search.enabled, "The module switch did not enable Search");
            GuiTestInput.click(context, "enable" + MODULE);
            require(!config.search.enabled, "The module switch did not disable Search");
            context.takeScreenshot(TestScreenshotOptions.of("search-gui-03-settings").withSize(1280, 800));

            // Shortcut capture, then the real production toggle and array-list row.
            GuiTestInput.click(context, "keybind" + MODULE); context.getInput().pressKey(GLFW.GLFW_KEY_F9);
            GuiTestInput.closeAnimated(context);
            var production = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("donut-client.json"));
            boolean before = production.load().search.enabled;
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTicks(3);
            require(production.load().search.enabled != before, "Production Search shortcut did not toggle");
            if (!before) {
                context.waitFor(mc -> rowNames().contains("Search"), 200);
                context.takeScreenshot(TestScreenshotOptions.of("search-gui-04-arraylist").withSize(1280, 800));
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_F9); context.waitTicks(3);
            require(production.load().search.enabled == before, "Production Search shortcut did not restore state");

            // GUI scale 2 with another search term.
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeDisplay(); });
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "spawner");
            require(GuiTestInput.visible(context, "module" + MODULE), "Search missing from the 'spawner' search at scale 2");
            GuiTestInput.click(context, "module" + MODULE);
            GuiTestInput.click(context, "m" + MODULE + ".showHud");
            require(!store.load().search.showHud, "HUD preference did not persist");
            context.takeScreenshot(TestScreenshotOptions.of("search-gui-05-settings-scale2").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
        } finally {
            context.runOnClient(mc -> {
                ModuleKeybinds.setKey(mc, MODULE, com.mojang.blaze3d.platform.InputConstants.getKey(key));
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            });
            try { Files.deleteIfExists(fixture); } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    static boolean matches(Object screen, int id) {
        try {
            var method = DonutScreen.class.getDeclaredMethod("matches", int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(screen, id);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    static java.util.Set<String> rowNames() {
        var names = new java.util.HashSet<String>();
        for (Object row : (java.util.List<?>) field(ModuleArrayList.class, "rows")) names.add((String) field(row, "name"));
        return names;
    }

    static Object field(Object owner, String name) {
        try {
            var field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner instanceof Class<?> ? null : owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
