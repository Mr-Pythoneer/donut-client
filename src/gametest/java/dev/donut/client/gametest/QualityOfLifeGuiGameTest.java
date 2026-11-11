package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigProfiles;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.ConfigProfileScreen;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.WaypointScreen;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.waypoints.WaypointController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/** Settings-aware search, the four new module tiles, the waypoint manager and profile export/import, via real input. */
public final class QualityOfLifeGuiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("qol-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        Set<Path> profilesBefore = new HashSet<>(profiles());
        Set<Path> backupsBefore = backups();
        var live = dev.donut.client.DonutClient.config();
        var savedWaypoints = new ArrayList<>(live.waypoints.waypoints);
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            // Search from Streaming finds Visual modules by a setting name, labels the match, and Enter opens it.
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.clickWithFade(context, "tab4", "contentProgress");
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "tracer");
            require(GuiTestInput.visible(context, "module13") && GuiTestInput.visible(context, "module15")
                    && GuiTestInput.visible(context, "module10"), "Setting search did not cross categories: 13="
                    + GuiTestInput.visible(context, "module13") + " 15=" + GuiTestInput.visible(context, "module15")
                    + " 10=" + GuiTestInput.visible(context, "module10") + " query=" + context.computeOnClient(mc -> field(mc.screen, "query"))
                    + " tab=" + context.computeOnClient(mc -> field(mc.screen, "tab")));
            require(!GuiTestInput.visible(context, "module8"), "An unrelated Streaming module matched 'tracer'");
            context.takeScreenshot(TestScreenshotOptions.of("qol-gui-01-setting-search").withSize(1280, 800));
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(3);
            int selected = context.computeOnClient(mc -> (Integer) field(mc.screen, "selectedModule"));
            require(selected == 10 || selected == 13 || selected == 15, "Enter did not open the top result: " + selected);

            // The four new modules: present, start disabled, toggle, favorite and capture a shortcut.
            for (int module : new int[] {16, 17, 18, 19}) {
                GuiTestInput.click(context, "search");
                context.runOnClient(mc -> setField(mc.screen, "query", ""));
                GuiTestInput.type(context, switch (module) { case 16 -> "coordinates"; case 17 -> "waypoints"; case 18 -> "zoom"; default -> "durability"; });
                require(GuiTestInput.visible(context, "module" + module), "Module " + module + " missing from search");
                GuiTestInput.click(context, "module" + module);
                require(!enabled(config, module), "Module " + module + " did not start disabled");
                GuiTestInput.click(context, "enable" + module);
                require(enabled(config, module) && enabled(store.load(), module), "Module " + module + " did not enable and save");
                GuiTestInput.click(context, "enable" + module);
                GuiTestInput.click(context, "favorite" + module);
            }
            open(context, "zoom", 18);
            GuiTestInput.click(context, "m18.zoomSmooth");
            require(!store.load().zoom.smooth, "Zoom setting did not persist");
            open(context, "coordinates", 16);
            GuiTestInput.click(context, "m16.bottomRight");
            require(store.load().coordinates.anchor.equals("Bottom right"), "Coordinates placement did not persist");
            context.takeScreenshot(TestScreenshotOptions.of("qol-gui-02-coordinates-settings").withSize(1280, 800));
            var loaded = store.load();
            require(loaded.coordinates.favorite && loaded.waypoints.favorite && loaded.zoom.favorite && loaded.durabilityAlert.favorite,
                    "Favorites for the new modules did not persist");
            open(context, "durability", 19);
            GuiTestInput.click(context, "keybind19"); context.getInput().pressKey(GLFW.GLFW_KEY_F8);
            context.runOnClient(mc -> require(ModuleKeybinds.mapping(19).saveString().equals("key.keyboard.f8"), "Shortcut capture failed"));
            context.runOnClient(mc -> ModuleKeybinds.setKey(mc, 19, com.mojang.blaze3d.platform.InputConstants.UNKNOWN));
            GuiTestInput.closeAnimated(context);

            // Waypoint manager: Add here creates a waypoint for this world, Delete removes it.
            context.setScreen(() -> new WaypointScreen(null, live, () -> { }));
            context.waitTicks(2);
            int beforeAdd = context.computeOnClient(mc -> WaypointController.get().forCurrentServer().size());
            press(context, "Add here");
            context.runOnClient(mc -> require(WaypointController.get().forCurrentServer().size() == beforeAdd + 1, "Add here did not add a waypoint"));
            context.takeScreenshot(TestScreenshotOptions.of("qol-gui-03-waypoint-manager").withSize(1280, 800));
            context.runOnClient(mc -> {
                for (var waypoint : new ArrayList<>(WaypointController.get().forCurrentServer()))
                    if (!savedWaypoints.contains(waypoint)) WaypointController.get().remove(waypoint);
                mc.setScreen(null);
            });

            // Profiles: export, change a setting, import (two-step confirm) restores it without enabling anything.
            config.freecam.speed = 2.5;
            config.storageEsp.range = 96;
            config.storageEsp.enabled = false;
            context.setScreen(() -> new ConfigProfileScreen(null, config, () -> store.save(config)));
            context.waitTicks(2);
            press(context, "Export current settings");
            context.waitTicks(2);
            Path exported = profiles().stream().filter(path -> !profilesBefore.contains(path)).findFirst()
                    .orElseThrow(() -> new AssertionError("Export did not write a profile"));
            String json = Files.readString(exported);
            require(json.contains("\"key.donut.toggle_freecam\"") && json.contains("\"range\": 96"), "Exported profile lacks settings or shortcuts");
            context.runOnClient(mc -> { config.freecam.speed = 7; config.storageEsp.range = 300; });
            pressStartingWith(context, exported.getFileName().toString());
            press(context, "Import selected");
            context.runOnClient(mc -> require(config.freecam.speed == 7, "Import applied before confirmation"));
            press(context, "Confirm import");
            context.runOnClient(mc -> {
                require(config.freecam.speed == 2.5 && config.storageEsp.range == 96, "Import did not restore the exported settings");
                require(!config.storageEsp.enabled && !config.zoom.enabled, "Import switched a module on");
            });
            require(backups().size() > backupsBefore.size(), "Import did not back up the current configuration");
            context.takeScreenshot(TestScreenshotOptions.of("qol-gui-04-profiles").withSize(1280, 800));
            context.runOnClient(mc -> mc.setScreen(null));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally {
            context.runOnClient(mc -> {
                live.waypoints.waypoints = savedWaypoints;
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            });
            try {
                Files.deleteIfExists(fixture);
                for (Path path : profiles()) if (!profilesBefore.contains(path)) Files.deleteIfExists(path);
                for (Path path : backups()) if (!backupsBefore.contains(path)) Files.deleteIfExists(path);
            } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    /** Search for a module and select it, so the test helpers can reach its inspector controls. */
    private static void open(ClientGameTestContext context, String term, int module) {
        GuiTestInput.click(context, "search");
        context.runOnClient(mc -> setField(mc.screen, "query", ""));
        GuiTestInput.type(context, term);
        GuiTestInput.click(context, "module" + module);
    }

    private static List<Path> profiles() {
        try { return ConfigProfiles.list(); } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static Set<Path> backups() {
        try (var files = Files.list(FabricLoader.getInstance().getConfigDir())) {
            return new HashSet<>(files.filter(p -> p.getFileName().toString().startsWith("donut-client.json.before-import-")).toList());
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static boolean enabled(ClientConfig config, int module) {
        return switch (module) { case 16 -> config.coordinates.enabled; case 17 -> config.waypoints.enabled;
            case 18 -> config.zoom.enabled; default -> config.durabilityAlert.enabled; };
    }

    private static void press(ClientGameTestContext context, String label) { click(context, label, false); }
    private static void pressStartingWith(ClientGameTestContext context, String prefix) { click(context, prefix, true); }

    private static void click(ClientGameTestContext context, String label, boolean prefix) {
        context.runOnClient(mc -> {
            List<Button> buttons = mc.screen.children().stream().filter(c -> c instanceof Button).map(c -> (Button) c).toList();
            Button button = buttons.stream().filter(b -> prefix ? b.getMessage().getString().contains(label.substring(0, Math.min(label.length(), 20)))
                            : b.getMessage().getString().equals(label)).findFirst()
                    .orElseThrow(() -> new AssertionError("No button " + label + " in " + buttons.stream().map(b -> b.getMessage().getString()).toList()));
            require(button.active, "Button is disabled: " + label);
            var event = new MouseButtonEvent(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, new MouseButtonInfo(0, 0));
            require(mc.screen.mouseClicked(event, false), "Screen ignored " + label);
            if (mc.screen != null) mc.screen.mouseReleased(event);
        });
        context.waitTicks(2);
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
