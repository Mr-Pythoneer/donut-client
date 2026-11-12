package dev.donut.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.DonutClient;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.config.FreelookSettings;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.input.ModuleKeybinds;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/** Freelook (21), Auto Reconnect (27) and Auto Eat (28) in the real menu, plus the unused IDs 22-26 staying hidden. */
public final class NewToolsGuiGameTest implements FabricClientGameTest {
    private static final int FREELOOK = 21, RECONNECT = 27, EAT = 28;
    private static final int[] MODULES = {FREELOOK, RECONNECT, EAT};
    private static final int[] KEYS = {GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_F10};

    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("newtools-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        boolean showHud = DonutClient.config().showHud;
        String[] keys = context.computeOnClient(mc -> new String[] {ModuleKeybinds.mapping(FREELOOK).saveString(),
                ModuleKeybinds.mapping(RECONNECT).saveString(), ModuleKeybinds.mapping(EAT).saveString()});
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));

            // Unused IDs never get a tile; every real module does, and the footer counts only real modules.
            context.runOnClient(mc -> {
                // Merge-safe: once other modules fill 22-26 they have mappings and are checked like any module.
                for (int id = ModuleKeybinds.FIRST_MODULE; id < ModuleKeybinds.MODULE_LIMIT; id++)
                    if (!ModuleKeybinds.exists(id)) require(!matches(mc.screen, id), "Unused ID " + id + " shows a tile");
                int shown = 0;
                for (int id = ModuleKeybinds.FIRST_MODULE; id < ModuleKeybinds.MODULE_LIMIT; id++) if (matches(mc.screen, id)) shown++;
                require(shown == ModuleKeybinds.registeredCount(), "All modules shows " + shown + " of " + ModuleKeybinds.registeredCount());
                for (int module : MODULES) require(matches(mc.screen, module), "Module " + module + " missing from All modules");
            });

            // Categories: Freelook and Auto Reconnect are Visual, Auto Eat is Blatant.
            GuiTestInput.click(context, "tab0");
            require(GuiTestInput.visible(context, "module" + FREELOOK) || reveal(context, FREELOOK), "Freelook missing from Visual");
            require(reveal(context, RECONNECT), "Auto Reconnect missing from Visual");
            context.runOnClient(mc -> require(!matches(mc.screen, EAT), "Auto Eat is listed under Visual"));
            GuiTestInput.click(context, "tab3");
            require(GuiTestInput.visible(context, "module" + EAT), "Auto Eat missing from Blatant");
            context.runOnClient(mc -> require(!matches(mc.screen, FREELOOK) && !matches(mc.screen, RECONNECT), "Visual tools in Blatant"));

            // Search by words and by a setting label.
            search(context, "orbit", FREELOOK);
            search(context, "rejoin", RECONNECT);
            search(context, "saturation", EAT);
            search(context, "rotten flesh", EAT);
            context.runOnClient(mc -> require(dev.donut.client.gui.ModuleSearchIndex.matchedSetting(EAT, "rotten flesh") != null, "Setting not indexed"));
            search(context, "", -1);
            GuiTestInput.click(context, "tab5");

            // Freelook settings persist.
            GuiTestInput.click(context, "module" + FREELOOK);
            require(!config.freelook.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite" + FREELOOK);
            GuiTestInput.click(context, "m" + FREELOOK + ".freelookToggle");
            GuiTestInput.click(context, "m" + FREELOOK + ".freelookInvert");
            GuiTestInput.click(context, "m" + FREELOOK + ".perspective");
            GuiTestInput.click(context, "m" + FREELOOK + ".freelookDistance");
            context.waitTicks(2);
            var freelook = store.load().freelook;
            require(freelook.favorite && freelook.toggleMode && freelook.invertPitch, "Freelook toggles did not persist");
            require(FreelookSettings.FRONT.equals(freelook.perspective), "Perspective did not cycle: " + freelook.perspective);
            context.runOnClient(mc -> require(config.freelook.distance >= 6 && config.freelook.distance <= 7, "Distance slider: " + config.freelook.distance));
            context.takeScreenshot(TestScreenshotOptions.of("new-tools-gui-01-freelook").withSize(1280, 800));

            // Auto Reconnect settings persist.
            GuiTestInput.click(context, "module" + RECONNECT);
            GuiTestInput.click(context, "m" + RECONNECT + ".reconnectBackoff");
            GuiTestInput.click(context, "m" + RECONNECT + ".reconnectSkipBans");
            GuiTestInput.click(context, "m" + RECONNECT + ".reconnectDelay");
            context.waitTicks(2);
            var reconnect = store.load().autoReconnect;
            require(!reconnect.backoff && !reconnect.skipBans, "Auto Reconnect toggles did not persist");
            context.runOnClient(mc -> require(config.autoReconnect.delaySeconds >= 28 && config.autoReconnect.delaySeconds <= 33,
                    "Delay slider: " + config.autoReconnect.delaySeconds));
            context.takeScreenshot(TestScreenshotOptions.of("new-tools-gui-02-auto-reconnect").withSize(1280, 800));

            // Auto Eat settings persist, including a blacklist toggle.
            GuiTestInput.click(context, "module" + EAT);
            GuiTestInput.click(context, "m" + EAT + ".eatBlock-rotten_flesh");
            GuiTestInput.click(context, "m" + EAT + ".eatBlock-chicken");
            GuiTestInput.click(context, "m" + EAT + ".eatHud");
            var eat = store.load().autoEat;
            require(!eat.isBlocked("rotten_flesh") && eat.isBlocked("chicken") && !eat.showHud, "Auto Eat toggles did not persist");
            GuiTestInput.click(context, "m" + EAT + ".eatHurt");
            require(!store.load().autoEat.eatWhenHurt, "Eat when hurt did not persist");
            require(!GuiTestInput.visible(context, "m" + EAT + ".eatHealth"), "The health slider shows while eat-when-hurt is off");
            context.takeScreenshot(TestScreenshotOptions.of("new-tools-gui-03-auto-eat").withSize(1280, 800));

            // Tile switches.
            for (int module : MODULES) {
                GuiTestInput.click(context, "enable" + module);
                require(enabled(store.load(), module), "Switch did not enable module " + module);
                GuiTestInput.click(context, "enable" + module);
                require(!enabled(store.load(), module), "Switch did not disable module " + module);
            }

            // Shortcut capture, then the production toggles and their array-list rows.
            for (int i = 0; i < MODULES.length; i++) {
                GuiTestInput.click(context, "keybind" + MODULES[i]);
                context.getInput().pressKey(KEYS[i]);
                int index = i;
                context.runOnClient(mc -> require(ModuleKeybinds.mapping(MODULES[index]).saveString()
                        .equals(InputConstants.Type.KEYSYM.getOrCreate(KEYS[index]).getName()), "Key capture failed for " + MODULES[index]));
            }
            require(!enabled(store.load(), FREELOOK), "Capturing a key toggled the module");
            GuiTestInput.closeAnimated(context);
            var production = DonutClient.config();
            boolean[] before = {production.freelook.enabled, production.autoReconnect.enabled, production.autoEat.enabled};
            context.runOnClient(mc -> production.showHud = true);
            for (int i = 0; i < MODULES.length; i++) {
                context.getInput().pressKey(KEYS[i]);
                context.waitTicks(3);
                require(enabled(production, MODULES[i]) != before[i], "Production shortcut did not toggle module " + MODULES[i]);
            }
            context.waitFor(mc -> rowNames().containsAll(java.util.List.of("Freelook", "Auto Reconnect", "Auto Eat")), 200);
            context.takeScreenshot(TestScreenshotOptions.of("new-tools-gui-04-arraylist").withSize(1280, 800));
            for (int i = 0; i < MODULES.length; i++) {
                context.getInput().pressKey(KEYS[i]);
                context.waitTicks(3);
                require(enabled(production, MODULES[i]) == before[i], "Production shortcut did not toggle module " + MODULES[i] + " back");
            }
        } finally {
            context.runOnClient(mc -> {
                DonutClient.config().showHud = showHud;
                for (int i = 0; i < MODULES.length; i++) ModuleKeybinds.setKey(mc, MODULES[i], InputConstants.getKey(keys[i]));
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            });
            try { Files.deleteIfExists(fixture); } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static void search(ClientGameTestContext context, String query, int module) {
        GuiTestInput.click(context, "search");
        context.runOnClient(mc -> {
            try {
                var field = DonutScreen.class.getDeclaredField("query");
                field.setAccessible(true);
                field.set(mc.screen, "");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        if (query.isEmpty()) { context.runOnClient(mc -> mc.screen.setFocused(null)); GuiTestInput.settle(context); return; }
        GuiTestInput.type(context, query);
        context.runOnClient(mc -> require(matches(mc.screen, module), "Module " + module + " missing from the '" + query + "' search"));
    }

    private static boolean reveal(ClientGameTestContext context, int module) {
        GuiTestInput.reveal(context, "module" + module);
        return GuiTestInput.visible(context, "module" + module);
    }

    private static boolean enabled(ClientConfig config, int module) {
        return module == FREELOOK ? config.freelook.enabled : module == RECONNECT ? config.autoReconnect.enabled : config.autoEat.enabled;
    }

    private static boolean matches(Object screen, int index) {
        try {
            Method method = DonutScreen.class.getDeclaredMethod("matches", int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(screen, index);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
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
