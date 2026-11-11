package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.durability.DurabilityAlertController;
import dev.donut.client.gui.CoordinatesHud;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.waypoints.WaypointController;
import dev.donut.client.waypoints.Waypoints;
import dev.donut.client.zoom.ZoomController;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Coordinates HUD, hold-to-zoom, durability warnings and waypoints, all through the production instances. */
public final class QualityOfLifeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        require(config != null, "Donut Client was not initialized");
        boolean coordinates = config.coordinates.enabled, zoom = config.zoom.enabled, durability = config.durabilityAlert.enabled,
                waypoints = config.waypoints.enabled, showHud = config.showHud;
        var savedWaypoints = new ArrayList<>(config.waypoints.waypoints);
        double zoomFactor = config.zoom.factor;
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.showHud = true;
            config.coordinates.enabled = config.zoom.enabled = config.durabilityAlert.enabled = config.waypoints.enabled = false;
            config.zoom.factor = 4;
            config.zoom.smooth = true;
            mc.options.guiScale().set(2);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try (var world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            // Load the fixture's chunks before building: fill fails in unloaded chunks.
            server.runCommand("tp @a -33.5 90 100.5 90 0");
            context.waitFor(mc -> mc.player != null && Math.abs(mc.player.getZ() - 100.5) < 1.0e-3);
            context.waitTicks(20);
            server.runCommand("fill -40 63 90 -20 63 110 minecraft:smooth_stone");
            server.runCommand("fill -40 64 90 -20 74 110 minecraft:air");
            server.runCommand("fill -36 64 96 -36 68 104 minecraft:bricks");
            server.runCommand("tp @a -33.5 64 100.5 90 0");
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(mc -> mc.player != null && mc.player.onGround() && Math.abs(mc.player.getY() - 64) < 1.0e-3, 200);

            // Coordinates: exact text from the real player, and drawn only when enabled.
            Path before = screenshot(context, "qol-00-baseline");
            context.runOnClient(mc -> {
                config.coordinates.enabled = true;
                var lines = CoordinatesHud.lines(mc, config);
                require(lines.getFirst().equals(new CoordinatesHud.Line("XYZ", "-33.5, 64.0, 100.5")), "Position line: " + lines.getFirst());
                require(lines.stream().anyMatch(l -> l.label().equals("Facing") && l.value().startsWith("West (-X)")), "Facing line missing: " + lines);
                require(lines.contains(new CoordinatesHud.Line("Chunk", "-3, 6  in 14, 4")), "Chunk line missing: " + lines);
                require(lines.contains(new CoordinatesHud.Line("Nether", "-5, 12")), "Nether line missing: " + lines);
            });
            context.waitTick();
            Path hud = screenshot(context, "qol-01-coordinates");
            require(changed(before, hud, 0, 0, 400, 160) > 300, "The coordinates HUD did not draw in the top-left corner");
            context.runOnClient(mc -> config.coordinates.enabled = false);

            // Zoom: holding the key narrows the view, scroll adjusts it, sensitivity drops, and release restores.
            var controller = ZoomController.get();
            context.runOnClient(mc -> config.zoom.enabled = true);
            context.getInput().moveCursor(1, 0);
            context.waitTick();
            float yaw0 = context.computeOnClient(mc -> mc.player.getYRot());
            context.getInput().moveCursor(40, 0);
            context.waitTicks(2);
            float normalTurn = Math.abs(context.computeOnClient(mc -> mc.player.getYRot()) - yaw0);
            Path unzoomed = screenshot(context, "qol-02-unzoomed");
            context.getInput().holdKey(ModuleKeybinds.zoomKey());
            context.waitTicks(12);
            context.runOnClient(mc -> require(Math.abs(controller.currentFactor() - 4) < 1.0e-6 && controller.holding(),
                    "Zoom did not reach 4x while held: " + controller.currentFactor()));
            Path zoomed = screenshot(context, "qol-03-zoomed");
            require(changed(unzoomed, zoomed, 0, 0, 1280, 800) > 100_000, "Zooming did not change the rendered view");
            int slot = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            context.getInput().scroll(1);
            context.waitTicks(10);
            context.runOnClient(mc -> {
                require(Math.abs(controller.targetFactor() - 5) < 1.0e-6, "Scroll did not raise the zoom by 25%");
                require(mc.player.getInventory().getSelectedSlot() == slot, "Zoom scrolling changed the hotbar slot");
            });
            float yaw1 = context.computeOnClient(mc -> mc.player.getYRot());
            context.getInput().moveCursor(40, 0);
            context.waitTicks(2);
            float zoomedTurn = Math.abs(context.computeOnClient(mc -> mc.player.getYRot()) - yaw1);
            require(normalTurn > 0 && zoomedTurn < normalTurn * 0.5, "Sensitivity was not reduced while zoomed: "
                    + zoomedTurn + " vs " + normalTurn);
            context.getInput().releaseKey(ModuleKeybinds.zoomKey());
            context.waitTicks(12);
            context.runOnClient(mc -> {
                require(controller.currentFactor() == 1 && !controller.holding(), "Releasing did not restore the normal view");
                require(config.zoom.factor == 4, "A scroll was saved although Remember scrolled level is off");
                config.zoom.enabled = false;
            });

            // Durability: a nearly broken pickaxe in hand warns; a fresh one clears the warning.
            context.runOnClient(mc -> config.durabilityAlert.enabled = true);
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe[minecraft:damage=1500]");
            context.waitFor(mc -> !DurabilityAlertController.get().low().isEmpty(), 100);
            context.runOnClient(mc -> {
                var low = DurabilityAlertController.get().low().getFirst();
                require(low.slot().equals("Main hand") && low.left() == 61, "Unexpected warning: " + low);
            });
            screenshot(context, "qol-04-durability");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
            context.waitFor(mc -> DurabilityAlertController.get().low().isEmpty(), 100);
            context.runOnClient(mc -> config.durabilityAlert.enabled = false);

            // Waypoints: markers render, labels show, Nether shows converted Overworld waypoints, removal clears.
            var points = WaypointController.get();
            // Same view for both frames: empty hand (no item-name popup), fixed facing toward the brick wall.
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            server.runCommand("tp @a -33.5 64 100.5 90 0");
            context.waitTicks(60);
            Path plain = screenshot(context, "qol-05-before-waypoint");
            context.runOnClient(mc -> {
                config.waypoints.enabled = true;
                var added = points.add("Test Base", new BlockPos(-44, 64, 100));
                require(added != null && added.server.startsWith("sp:"), "Singleplayer waypoint key missing: " + (added == null ? null : added.server));
                require(points.add("Test Base", new BlockPos(-44, 66, 103)).name.equals("Test Base 2"), "Duplicate names were not numbered");
                require(points.shown().size() == 2, "Waypoints are not shown in their own dimension");
            });
            context.waitTicks(2);
            Path marked = screenshot(context, "qol-06-waypoints");
            // The markers sit behind the wall, ahead of the player: the middle of the frame must change.
            require(changed(plain, marked, 320, 150, 640, 500) > 1500, "Waypoint markers, beams and labels did not render through the wall");
            server.runCommand("execute in minecraft:the_nether run tp @a -4.5 80 12.5");
            context.waitFor(mc -> mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.NETHER, 400);
            context.runOnClient(mc -> {
                var shown = points.shown();
                require(shown.size() == 2 && shown.stream().allMatch(Waypoints.Shown::linked), "Linked Overworld waypoints missing in the Nether");
                require(Math.abs(shown.getFirst().x() - Math.floor(-44 / 8.0)) <= 1, "Linked waypoint was not converted 8:1: " + shown.getFirst());
                config.waypoints.showLinkedDimension = false;
                require(points.shown().isEmpty(), "Linked waypoints showed with the option off");
                config.waypoints.showLinkedDimension = true;
            });
            server.runCommand("execute in minecraft:overworld run tp @a -33.5 64 100.5 90 0");
            context.waitFor(mc -> mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD, 400);
            context.runOnClient(mc -> {
                for (var waypoint : new ArrayList<>(points.forCurrentServer())) points.remove(waypoint);
                require(points.shown().isEmpty(), "Removing waypoints did not clear them");
            });
            requireNoMovement(context, server);
        } finally {
            context.getInput().releaseKey(ModuleKeybinds.zoomKey());
            context.runOnClient(mc -> {
                config.coordinates.enabled = coordinates;
                config.zoom.enabled = zoom;
                config.zoom.factor = zoomFactor;
                config.durabilityAlert.enabled = durability;
                config.waypoints.enabled = waypoints;
                config.waypoints.showLinkedDimension = true;
                config.waypoints.waypoints = savedWaypoints;
                config.showHud = showHud;
                ZoomController.get().reset();
            });
        }
    }

    /** None of these features move the player: compare client and server positions at the end. */
    private static void requireNoMovement(ClientGameTestContext context, TestServerContext server) {
        Vec3 client = context.computeOnClient(mc -> mc.player.position());
        Vec3 host = server.computeOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().position());
        require(client.distanceTo(host) < 1.0e-3, "Client and server player positions diverged");
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> { mc.getToastManager().clear(); mc.gui.getChat().clearMessages(false); });
        context.waitTick();
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static int changed(Path a, Path b, int x, int y, int w, int h) {
        try {
            BufferedImage first = ImageIO.read(a.toFile()), second = ImageIO.read(b.toFile());
            int count = 0;
            for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++)
                if (first.getRGB(px, py) != second.getRGB(px, py)) count++;
            return count;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

}
