package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.config.FreelookSettings;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.freelook.FreelookController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

/** Freelook through the production key, mouse route and camera: the camera orbits, the player never turns. */
public final class FreelookGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        var freelook = FreelookController.get();
        require(config != null && freelook != null && freelook.key() != null, "Freelook was not initialized");
        boolean enabled = config.freelook.enabled, toggleMode = config.freelook.toggleMode;
        String perspective = config.freelook.perspective;
        double distance = config.freelook.distance;
        CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.freelook.enabled = false;
            config.freelook.toggleMode = false;
            config.freelook.perspective = FreelookSettings.BACK;
            config.freelook.distance = 4;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("fill -16 63 -16 16 63 16 minecraft:smooth_stone");
            server.runCommand("fill -16 64 -16 16 75 16 minecraft:air");
            server.runCommand("setblock 0 64 -4 minecraft:gold_block");
            server.runCommand("tp @a 0.5 64 0.5 180 10");
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(mc -> mc.player != null && mc.player.onGround(), 200);

            // Disabled module: the key does nothing and the mouse turns the player as usual.
            context.getInput().holdKey(freelook.key());
            context.waitTicks(3);
            require(!FreelookController.isActive(), "Freelook started while the module is off");
            context.getInput().releaseKey(freelook.key());
            // The first cursor event after the world opens only primes vanilla's mouse handler.
            context.getInput().moveCursor(1, 0);
            context.waitTick();
            float yawBefore = context.computeOnClient(mc -> mc.player.getYRot());
            context.getInput().moveCursor(60, 0);
            context.waitTicks(2);
            float turned = context.computeOnClient(mc -> mc.player.getYRot());
            require(Math.abs(turned - yawBefore) > 1, "The mouse did not turn the player normally");
            server.runCommand("tp @a 0.5 64 0.5 180 10");
            context.waitFor(mc -> Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - 180)) < 1.0e-3, 100);
            context.waitTicks(4);
            context.takeScreenshot(TestScreenshotOptions.of("freelook-00-first-person").withSize(1280, 800));

            // Hold: third person behind the player, the camera turns, the player does not.
            context.runOnClient(mc -> config.freelook.enabled = true);
            float[] player0 = rotation(context);
            Vec3 position0 = context.computeOnClient(mc -> mc.player.position());
            context.getInput().holdKey(freelook.key());
            context.waitTicks(3);
            context.runOnClient(mc -> {
                require(FreelookController.isActive(), "Holding the Freelook key did not start it");
                require(mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "Freelook did not switch to third person");
            });
            context.getInput().moveCursor(600, 150);
            context.waitTicks(3);
            context.runOnClient(mc -> {
                float[] now = {mc.player.getYRot(), mc.player.getXRot()};
                require(now[0] == player0[0] && now[1] == player0[1], "Freelook turned the player: "
                        + now[0] + "/" + now[1] + " vs " + player0[0] + "/" + player0[1]);
                var cam = mc.gameRenderer.getMainCamera();
                require(Math.abs(cam.yRot() - player0[0]) > 30, "The camera did not orbit: camera " + cam.yRot() + ", player " + player0[0]);
                require(Math.abs(freelook.yaw() - cam.yRot()) < 1.0e-3, "The camera does not use Freelook's yaw");
                require(cam.isDetached(), "The camera is not in third person");
                require(cam.position().distanceTo(mc.player.getEyePosition()) > 2, "The camera is not behind the player");
            });
            context.takeScreenshot(TestScreenshotOptions.of("freelook-01-orbit").withSize(1280, 800));

            // Walking keeps the player's own heading: W moves toward its yaw (180 = north, -Z).
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(10);
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(mc -> {
                Vec3 moved = mc.player.position().subtract(position0);
                require(moved.z < -0.5 && Math.abs(moved.x) < 0.2, "Movement did not follow the player's heading: " + moved);
                require(mc.player.getYRot() == player0[0], "Walking during Freelook turned the player");
            });

            // A longer camera distance (client-side only) moves the camera farther out.
            double near = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().distanceTo(mc.player.getEyePosition()));
            context.runOnClient(mc -> config.freelook.distance = 8);
            context.waitTicks(2);
            double far = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().distanceTo(mc.player.getEyePosition()));
            require(far > near + 2, "Camera distance had no effect: " + near + " -> " + far);
            context.runOnClient(mc -> config.freelook.distance = 4);

            // Release: first person again, the player still faces its original way.
            context.getInput().releaseKey(freelook.key());
            context.waitTicks(3);
            context.runOnClient(mc -> {
                require(!FreelookController.isActive(), "Releasing the key did not stop Freelook");
                require(mc.options.getCameraType() == CameraType.FIRST_PERSON, "The previous perspective was not restored");
                require(mc.player.getYRot() == player0[0] && mc.player.getXRot() == player0[1], "Player rotation changed");
            });
            context.waitTicks(2);
            context.runOnClient(mc -> require(Math.abs(mc.gameRenderer.getMainCamera().yRot() - player0[0]) < 1.0e-3,
                    "The camera did not return to the player's view"));

            // Toggle mode with "Keep current": stays in first person, one press on, one press off.
            context.runOnClient(mc -> { config.freelook.toggleMode = true; config.freelook.perspective = FreelookSettings.KEEP; });
            context.getInput().pressKey(freelook.key());
            context.waitTicks(2);
            context.runOnClient(mc -> require(FreelookController.isActive() && mc.options.getCameraType() == CameraType.FIRST_PERSON,
                    "Toggle mode with Keep current did not start in first person"));
            context.getInput().moveCursor(-300, 0);
            context.waitTicks(2);
            context.runOnClient(mc -> require(mc.player.getYRot() == player0[0], "Toggle Freelook turned the player"));
            context.takeScreenshot(TestScreenshotOptions.of("freelook-02-keep-first-person").withSize(1280, 800));

            // Freecam wins: turning it on ends Freelook, and Freelook cannot start while Freecam is on.
            context.runOnClient(mc -> FreecamController.getInstance().setEnabled(true));
            context.waitTicks(2);
            context.runOnClient(mc -> require(FreecamController.enabled() && !FreelookController.isActive(), "Freelook stayed on with Freecam"));
            context.getInput().pressKey(freelook.key());
            context.waitTicks(2);
            context.runOnClient(mc -> require(!FreelookController.isActive(), "Freelook started during Freecam"));
            context.runOnClient(mc -> FreecamController.getInstance().setEnabled(false));
            context.waitTicks(2);

            // Front view, then a menu: hold mode pauses while a screen is open.
            context.runOnClient(mc -> { config.freelook.toggleMode = false; config.freelook.perspective = FreelookSettings.FRONT; });
            context.getInput().holdKey(freelook.key());
            context.waitTicks(3);
            context.runOnClient(mc -> require(mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT, "Front perspective not used"));
            context.takeScreenshot(TestScreenshotOptions.of("freelook-03-front").withSize(1280, 800));
            context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(3);
            context.runOnClient(mc -> require(!FreelookController.isActive() && mc.options.getCameraType() == CameraType.FIRST_PERSON,
                    "Freelook kept running behind a menu"));
            context.setScreen(() -> null);
            context.getInput().releaseKey(freelook.key());
            context.waitTicks(2);
        } finally {
            context.runOnClient(mc -> {
                freelook.stop(mc);
                config.freelook.enabled = enabled;
                config.freelook.toggleMode = toggleMode;
                config.freelook.perspective = perspective;
                config.freelook.distance = distance;
                mc.options.setCameraType(camera);
            });
        }
    }

    private static float[] rotation(ClientGameTestContext context) {
        return context.computeOnClient(mc -> new float[] {mc.player.getYRot(), mc.player.getXRot()});
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
