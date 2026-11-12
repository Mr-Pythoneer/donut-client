package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.input.ModuleKeybinds;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/** Player ESP in the real menu: search terms, target/nametag toggles, sliders, friends entry, favorite and shortcut. */
public final class PlayerEspGuiGameTest implements FabricClientGameTest {
    private static final int MODULE = 22;

    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        Path fixture = FabricLoader.getInstance().getConfigDir().resolve("playeresp-gametest.json");
        var store = new ConfigStore(fixture);
        int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
        String key = context.computeOnClient(mc -> ModuleKeybinds.mapping(MODULE).saveString());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });
        try (var world = context.worldBuilder().create()) {
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "nametag");
            require(GuiTestInput.visible(context, "module" + MODULE), "Player ESP missing from the 'nametag' search");
            require(!GuiTestInput.visible(context, "module23"), "Search should not match 'nametag'");
            GuiTestInput.click(context, "module" + MODULE);
            require(!config.playerEsp.enabled, "Selecting the module enabled it");
            GuiTestInput.click(context, "favorite" + MODULE);
            require(store.load().playerEsp.favorite, "Favorite did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".hostileMobs");
            GuiTestInput.click(context, "m" + MODULE + ".tracers");
            GuiTestInput.click(context, "m" + MODULE + ".showArmor");
            var saved = store.load().playerEsp;
            require(saved.hostileMobs && saved.tracers && !saved.showArmor && saved.players, "Target/display toggles did not persist");
            // Turning off distance colouring reveals the fixed player colour row.
            GuiTestInput.click(context, "m" + MODULE + ".colorByDistance");
            require(!store.load().playerEsp.colorByDistance, "Distance colouring toggle did not persist");
            require(GuiTestInput.visible(context, "m" + MODULE + ".colorPlayer color") || revealable(context, "m" + MODULE + ".colorPlayer color"),
                    "The fixed player colour row did not appear");
            GuiTestInput.click(context, "m" + MODULE + ".respectNameHider");
            require(!store.load().playerEsp.respectNameHider, "Respect Name Hider did not persist");
            GuiTestInput.click(context, "m" + MODULE + ".range"); // Midpoint of 8-512.
            context.waitTicks(2);
            GuiTestInput.click(context, "m" + MODULE + ".maxShown"); // Midpoint of 1-256.
            context.runOnClient(mc -> {
                require(config.playerEsp.range >= 248 && config.playerEsp.range <= 272, "Range slider: " + config.playerEsp.range);
                require(config.playerEsp.maxShown >= 118 && config.playerEsp.maxShown <= 138, "Max shown slider: " + config.playerEsp.maxShown);
            });

            // Friends: type a name + Enter, an invalid name is refused, then remove.
            GuiTestInput.click(context, "m" + MODULE + ".friendInput");
            GuiTestInput.type(context, "Alex_1");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            GuiTestInput.settle(context);
            require(store.load().playerEsp.friends.equals(java.util.List.of("Alex_1")), "Friend was not saved: " + store.load().playerEsp.friends);
            GuiTestInput.click(context, "m" + MODULE + ".friendInput");
            GuiTestInput.type(context, "bad name");
            GuiTestInput.click(context, "m" + MODULE + ".addFriend");
            context.runOnClient(mc -> {
                require(config.playerEsp.friends.size() == 1, "An invalid friend name was added");
                Object message = ((java.util.Map<?, ?>) SearchGuiGameTest.field(mc.screen, "entryMessages")).get("m22.friendInput");
                require(String.valueOf(message).contains("letters, digits or underscores"), "No validation message: " + message);
            });
            context.takeScreenshot(TestScreenshotOptions.of("player-esp-gui-01-settings").withSize(1280, 800));
            GuiTestInput.click(context, "m" + MODULE + ".removeFriend0");
            require(store.load().playerEsp.friends.isEmpty(), "Removing the friend did not persist");

            GuiTestInput.click(context, "enable" + MODULE);
            require(config.playerEsp.enabled && store.load().playerEsp.enabled, "The module switch did not enable Player ESP");
            GuiTestInput.click(context, "enable" + MODULE);
            require(!config.playerEsp.enabled, "The module switch did not disable Player ESP");

            // Shortcut capture, then the real production toggle and array-list row.
            GuiTestInput.click(context, "keybind" + MODULE); context.getInput().pressKey(GLFW.GLFW_KEY_F10);
            GuiTestInput.closeAnimated(context);
            var production = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("donut-client.json"));
            boolean before = production.load().playerEsp.enabled;
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(production.load().playerEsp.enabled != before, "Production Player ESP shortcut did not toggle");
            if (!before) {
                context.waitFor(mc -> SearchGuiGameTest.rowNames().contains("Player ESP"), 200);
                context.takeScreenshot(TestScreenshotOptions.of("player-esp-gui-02-arraylist").withSize(1280, 800));
            }
            context.getInput().pressKey(GLFW.GLFW_KEY_F10); context.waitTicks(3);
            require(production.load().playerEsp.enabled == before, "Production Player ESP shortcut did not restore state");

            // GUI scale 2 with another search term.
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeDisplay(); });
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> store.save(config)));
            GuiTestInput.click(context, "search"); GuiTestInput.type(context, "friends");
            require(GuiTestInput.visible(context, "module" + MODULE), "Player ESP missing from the 'friends' search at scale 2");
            GuiTestInput.click(context, "module" + MODULE);
            GuiTestInput.click(context, "m" + MODULE + ".nametags");
            require(!store.load().playerEsp.nametags, "Nametag preference did not persist");
            context.takeScreenshot(TestScreenshotOptions.of("player-esp-gui-03-settings-scale2").withSize(1280, 800));
            GuiTestInput.closeAnimated(context);
        } finally {
            context.runOnClient(mc -> {
                ModuleKeybinds.setKey(mc, MODULE, com.mojang.blaze3d.platform.InputConstants.getKey(key));
                mc.options.guiScale().set(scale); mc.resizeDisplay();
            });
            try { Files.deleteIfExists(fixture); } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static boolean revealable(ClientGameTestContext context, String id) {
        try { GuiTestInput.reveal(context, id); return true; } catch (AssertionError missing) { return false; }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
