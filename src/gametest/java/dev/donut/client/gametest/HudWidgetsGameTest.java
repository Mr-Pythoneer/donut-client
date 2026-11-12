package dev.donut.client.gametest;

import com.google.gson.Gson;
import dev.donut.client.DonutClient;
import dev.donut.client.config.HudWidgetsSettings;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.HudPanel;
import dev.donut.client.gui.HudWidgetsHud;
import dev.donut.client.hudwidgets.ClickCounter;
import dev.donut.client.hudwidgets.HudWidget;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

/** HUD Widgets through the production renderer: default set, every widget, stacking, CPS from real clicks, potions, F1. */
public final class HudWidgetsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        require(config != null, "Donut Client was not initialized");
        Gson gson = new Gson();
        String savedWidgets = gson.toJson(config.hudWidgets);
        boolean coordinates = config.coordinates.enabled, storage = config.storageEsp.enabled, showHud = config.showHud;
        double hudScale = config.clickGuiStyle.hudScale;
        int guiScale = context.computeOnClient(mc -> mc.options.guiScale().get());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.showHud = true;
            config.hudWidgets = new HudWidgetsSettings();
            config.hudWidgets.sanitize();
            config.hudWidgets.enabled = true;
            config.coordinates.enabled = true;   // top left
            config.storageEsp.enabled = true;    // bottom left
            config.clickGuiStyle.hudScale = 1;
            mc.options.guiScale().set(2);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.resizeDisplay();
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("setblock 3 -60 3 minecraft:chest");
            // Look straight up so clicks hit nothing.
            server.runCommand("tp @a 0.5 -60 0.5 0 -90");
            server.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[minecraft:damage=300]");
            server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
            server.runCommand("item replace entity @a armor.feet with minecraft:golden_boots[minecraft:damage=85]");
            context.waitTicks(20);
            world.getClientWorld().waitForChunksRender();

            // Defaults: FPS + ping (one stats panel, top right) and armour; no potion yet, no key strokes.
            context.waitFor(mc -> HudWidgetsHud.lastDrawn().containsAll(List.of("stats@TOP_RIGHT", "armour@BOTTOM_RIGHT")), 100);
            context.runOnClient(mc -> {
                require(!HudWidgetsHud.lastDrawn().contains("keystrokes@BOTTOM_LEFT"), "Key strokes are off by default");
                require(!HudWidgetsHud.lastDrawn().contains("potions@TOP_RIGHT"), "No effects, no potion panel");
            });
            shot(context, "hud-widgets-01-defaults");

            // Potions from /effect give, shown with name, level and time.
            server.runCommand("effect give @a minecraft:speed 95 1");
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            context.waitFor(mc -> HudWidgetsHud.lastDrawn().contains("potions@TOP_RIGHT")
                    && mc.player.hasEffect(MobEffects.SPEED) && mc.player.getEffect(MobEffects.SPEED).getAmplifier() == 1, 100);

            // Every widget on.
            context.runOnClient(mc -> { for (HudWidget widget : HudWidget.values()) config.hudWidgets.setWidgetEnabled(widget, true); });
            context.waitFor(mc -> HudWidgetsHud.lastDrawn().containsAll(List.of("stats@TOP_RIGHT", "potions@TOP_RIGHT",
                    "armour@BOTTOM_RIGHT", "keystrokes@BOTTOM_LEFT")), 100);
            context.waitTicks(20); // Module list rows finish animating in.
            assertLayout(context);
            shot(context, "hud-widgets-02-all");

            // CPS counts real clicks through the mouse handler (own input only).
            context.runOnClient(mc -> ClickCounter.get().reset());
            for (int i = 0; i < 3; i++) context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            for (int i = 0; i < 2; i++) context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(mc -> {
                var clicks = ClickCounter.get();
                require(clicks.total(ClickCounter.LEFT) == 3 && clicks.total(ClickCounter.RIGHT) == 2,
                        "Clicks counted: " + clicks.total(ClickCounter.LEFT) + " left, " + clicks.total(ClickCounter.RIGHT) + " right");
                require(clicks.cps(ClickCounter.LEFT) >= 1, "Recent clicks fell out of the one-second window already");
            });
            // Clicks inside a menu don't count.
            context.setScreen(() -> new DonutScreen(config, () -> { }));
            context.waitTicks(2);
            context.runOnClient(mc -> require(HudWidgetsHud.lastDrawn().isEmpty(), "Widgets drew over a menu"));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.runOnClient(mc -> require(ClickCounter.get().total(ClickCounter.LEFT) == 3, "A menu click was counted"));
            context.setScreen(() -> null);
            context.waitTicks(2);

            // Holding W lights its key cap.
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(2);
            shot(context, "hud-widgets-03-keystroke-w");
            context.getInput().releaseKey(options -> options.keyUp);

            // F1 hides everything.
            context.getInput().pressKey(GLFW.GLFW_KEY_F1);
            context.waitTicks(2);
            long hidden = context.computeOnClient(mc -> System.nanoTime());
            context.waitTicks(4);
            context.runOnClient(mc -> require(mc.options.hideGui && HudWidgetsHud.lastDrawNanos() < hidden, "Widgets drew with F1"));
            context.getInput().pressKey(GLFW.GLFW_KEY_F1);

            // Larger panel size and GUI scale still stack without overlapping.
            context.runOnClient(mc -> config.clickGuiStyle.hudScale = 1.4);
            context.waitTicks(3);
            assertLayout(context);
            shot(context, "hud-widgets-04-panel-size");
            context.runOnClient(mc -> { config.clickGuiStyle.hudScale = 1; mc.options.guiScale().set(4); mc.resizeDisplay(); });
            context.waitTicks(5);
            assertLayout(context);
            shot(context, "hud-widgets-05-gui4");

            // Moving widgets to other corners.
            context.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                config.hudWidgets.corners.put(HudWidget.FPS.id, "Top left");
                config.hudWidgets.corners.put(HudWidget.POTIONS.id, "Bottom left");
            });
            context.waitFor(mc -> HudWidgetsHud.lastDrawn().containsAll(List.of("stats@TOP_LEFT", "potions@BOTTOM_LEFT")), 100);
            assertLayout(context);
            shot(context, "hud-widgets-06-corners");

            // Module off: nothing drawn.
            context.runOnClient(mc -> config.hudWidgets.enabled = false);
            context.waitTicks(2);
            context.runOnClient(mc -> require(HudWidgetsHud.lastDrawn().isEmpty(), "Drew while disabled"));
        } finally {
            context.runOnClient(mc -> {
                config.hudWidgets = gson.fromJson(savedWidgets, HudWidgetsSettings.class);
                config.hudWidgets.sanitize();
                config.coordinates.enabled = coordinates;
                config.storageEsp.enabled = storage;
                config.showHud = showHud;
                config.clickGuiStyle.hudScale = hudScale;
                mc.options.guiScale().set(guiScale);
                mc.resizeDisplay();
            });
        }
    }

    /** No two Donut panels overlap, and top-right panels start below the module list. */
    private static void assertLayout(ClientGameTestContext context) {
        context.waitTicks(2);
        context.runOnClient(mc -> {
            List<HudPanel.Placed> placed = HudPanel.lastFrame();
            float floor = HudPanel.lastTopRightFloor();
            require(floor > 0, "The module list did not report its height");
            require(placed.stream().anyMatch(p -> p.anchor() == HudPanel.Anchor.TOP_LEFT), "Coordinates panel missing: " + placed);
            for (int i = 0; i < placed.size(); i++) {
                var a = placed.get(i);
                if (a.anchor() == HudPanel.Anchor.TOP_RIGHT) require(a.y() >= floor, "A top-right panel covers the module list: " + a + " floor " + floor);
                require(a.x() >= 0 && a.y() >= 0 && a.x() + a.w() <= mc.getWindow().getWidth() + 1 && a.y() + a.h() <= mc.getWindow().getHeight() + 1,
                        "Panel off screen: " + a);
                for (int j = i + 1; j < placed.size(); j++) require(!a.overlaps(placed.get(j)), "Panels overlap: " + a + " and " + placed.get(j));
            }
        });
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> { mc.getToastManager().clear(); mc.gui.getChat().clearMessages(false); });
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
