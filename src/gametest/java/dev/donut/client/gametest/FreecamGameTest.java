package dev.donut.client.gametest;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.gui.DonutScreen;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** Verifies the production singleton moves only its camera, retaining the actual client/server player. */
public final class FreecamGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        FreecamController controller = FreecamController.getInstance();
        require(controller != null, "The production Freecam controller was not initialized");
        double originalSpeed = controller.settings().speed;
        double originalBoost = controller.settings().boostMultiplier;
        var original = snapshot(controller.settings());
        context.runOnClient(client -> {
            controller.setEnabled(false);
            controller.settings().speed = 1;
            controller.settings().boostMultiplier = 3;
            resetHelpers(controller);
            client.options.guiScale().set(1);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        context.getInput().resizeWindow(1280, 800);

        try {
          try (var world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("fill -15 64 -15 15 64 15 minecraft:smooth_stone");
            server.runCommand("setblock -2 65 0 minecraft:chest");
            server.runCommand("setblock 0 65 0 minecraft:purple_shulker_box");
            server.runCommand("setblock 2 65 0 minecraft:spawner");
            server.runCommand("tp @a 0.5 65 8.5 180 10");
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.player.onGround()
                    && Math.abs(client.player.getY() - 65) < 0.001);
            context.waitTicks(3);

            Pose clientBody = context.computeOnClient(client -> Pose.of(client.player));
            Pose serverBody = server.computeOnServer(mcServer -> Pose.of(mcServer.getPlayerList().getPlayers().getFirst()));
            var originalInput = context.computeOnClient(client -> client.player.input);

            // Share just the actual controller settings with the screen, leaving finder preferences untouched.
            ClientConfig screenConfig = new ClientConfig();
            screenConfig.freecam = controller.settings();
            context.setScreen(() -> new DonutScreen(screenConfig, () -> { }));
            context.waitTicks(2);
            GuiTestInput.click(context, "enable2");
            context.waitFor(client -> controller.isActive());
            screenshot(context, "donut-freecam-01-modules");
            GuiTestInput.rightClick(context, "module2");
            GuiTestInput.click(context, "m2.freecamSpeed"); // Camera-speed slider midpoint.
            // Midpoint of 0.1-10 is 5.05; floating-point step rounding lands on 5.0 or 5.1.
            context.runOnClient(client -> require(Math.abs(controller.settings().speed - 5.05) < 0.051,
                    "The Freecam speed slider did not update the controller settings"));
            GuiTestInput.click(context, "m2.freecamBoost"); // Sprint-boost slider midpoint.
            context.runOnClient(client -> require(Math.abs(controller.settings().boostMultiplier - 5.5) < 0.001,
                    "The Freecam boost slider did not update the controller settings"));
            screenshot(context, "donut-freecam-02-settings");
            context.runOnClient(client -> {
                controller.settings().speed = 1;
                controller.settings().boostMultiplier = 3;
                client.screen.onClose();
            });
            context.waitForScreen(null);
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(client.player.input != originalInput, "Freecam did not install neutral player input");
                require(client.player.input.keyPresses.equals(Input.EMPTY), "Player movement input is not neutral");
                require(client.getCameraEntity() == client.player, "Freecam replaced the real camera entity");
                require(client.gameRenderer.getMainCamera().isDetached(), "Freecam camera was not detached");
                require(client.gameRenderer.getMainCamera().position().distanceTo(controller.position()) < 0.001,
                        "Rendered camera did not use the controller position");
            });

            double ordinary = move(context, controller, options -> options.keyUp, 6);
            require(ordinary > 3, "Forward input did not move the virtual camera");
            assertBodyUnchanged(context, server, clientBody, serverBody);

            context.getInput().holdKey(options -> options.keySprint);
            double boosted = move(context, controller, options -> options.keyUp, 6);
            context.getInput().releaseKey(options -> options.keySprint);
            context.waitTick();
            require(boosted > ordinary * 2.5 && boosted < ordinary * 3.5,
                    "Sprint boost did not apply the configured multiplier");
            assertBodyUnchanged(context, server, clientBody, serverBody);

            double beforeRise = context.computeOnClient(client -> controller.position().y);
            move(context, controller, options -> options.keyJump, 4);
            double afterRise = context.computeOnClient(client -> controller.position().y);
            require(afterRise > beforeRise + 2, "Jump input did not raise the virtual camera");
            move(context, controller, options -> options.keyShift, 4);
            double afterDescent = context.computeOnClient(client -> controller.position().y);
            require(afterDescent < afterRise - 2, "Sneak input did not lower the virtual camera");
            assertBodyUnchanged(context, server, clientBody, serverBody);

            // Also exercise MouseHandler's real input route, rather than testing only controller.turn().
            context.getInput().moveCursor(1, 0); // Clear vanilla's first-move guard after grabbing the mouse.
            context.waitTick();
            float beforeMouseYaw = context.computeOnClient(client -> controller.yaw());
            context.getInput().moveCursor(24, -12);
            context.waitTicks(2);
            context.runOnClient(client -> require(Math.abs(wrappedDegrees(controller.yaw() - beforeMouseYaw)) > 0.01,
                    "MouseHandler input did not reach the free camera"));
            assertBodyUnchanged(context, server, clientBody, serverBody);

            context.runOnClient(client -> {
                float yaw = controller.yaw();
                float pitch = controller.pitch();
                controller.turn(80, -30);
                require(Math.abs(wrappedDegrees(controller.yaw() - yaw) - 12) < 0.001,
                        "Mouse yaw did not rotate the virtual camera");
                require(Math.abs(controller.pitch() - pitch + 4.5) < 0.001,
                        "Mouse pitch did not rotate the virtual camera");
            });
            context.waitTicks(2);
            assertBodyUnchanged(context, server, clientBody, serverBody);
            // Move above the fixture and look back at the stationary body for a useful visual check.
            move(context, controller, options -> options.keyJump, 8);
            context.runOnClient(client -> {
                Vec3 toBody = client.player.getEyePosition().subtract(controller.position());
                double desiredYaw = Math.toDegrees(Math.atan2(-toBody.x, toBody.z));
                double desiredPitch = Math.toDegrees(Math.atan2(-toBody.y, Math.hypot(toBody.x, toBody.z)));
                controller.turn((desiredYaw - controller.yaw()) / 0.15, (desiredPitch - controller.pitch()) / 0.15);
            });
            context.waitTicks(2);
            assertBodyUnchanged(context, server, clientBody, serverBody);
            screenshot(context, "donut-freecam-03-detached-camera");

            // Queued world interactions must be cleared before they can run or replay after disabling.
            context.runOnClient(client -> {
                KeyMapping[] interactions = {client.options.keyAttack, client.options.keyUse,
                        client.options.keyDrop, client.options.keySwapOffhand};
                for (KeyMapping key : interactions) {
                    key.setDown(true);
                    KeyMapping.click(key.getDefaultKey());
                }
                controller.tick(client);
                for (KeyMapping key : interactions) {
                    require(!key.isDown(), "Freecam retained a held world interaction: " + key.getName());
                    require(!key.consumeClick(), "Freecam retained a queued world interaction: " + key.getName());
                }
            });
            context.waitTicks(2);
            assertBodyUnchanged(context, server, clientBody, serverBody);

            context.runOnClient(client -> controller.setEnabled(false));
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(!controller.isActive(), "Freecam did not disable");
                require(!controller.settings().enabled, "Freecam enabled flag was not reset");
                require(client.player.input == originalInput, "Disabling Freecam did not restore the original input");
                require(client.getCameraEntity() == client.player, "Disabling Freecam changed the camera entity");
                require(!client.gameRenderer.getMainCamera().isDetached(), "Camera remained detached after disabling");
                require(client.gameRenderer.getMainCamera().position().distanceTo(client.player.getEyePosition()) < 0.15,
                        "Camera did not return to the player");
            });
            assertBodyUnchanged(context, server, clientBody, serverBody);
            screenshot(context, "donut-freecam-04-returned-to-player");

            // A real server correction remains authoritative while the camera is elsewhere.
            context.runOnClient(client -> controller.setEnabled(true));
            context.waitFor(client -> controller.isActive());
            server.runCommand("tp @a 3.5 65 8.5 90 0");
            context.waitFor(client -> Math.abs(client.player.getX() - 3.5) < 0.001);
            context.waitTicks(3);
            Pose correctedClient = context.computeOnClient(client -> Pose.of(client.player));
            Pose correctedServer = server.computeOnServer(mcServer -> Pose.of(mcServer.getPlayerList().getPlayers().getFirst()));
            context.runOnClient(client -> controller.setEnabled(false));
            context.waitTicks(2);
            assertBodyUnchanged(context, server, correctedClient, correctedServer);
            require(correctedClient.position().distanceTo(clientBody.position()) > 2,
                    "Server correction fixture did not move the actual player");

            qualityOfLifeChecks(context, server, controller, correctedClient, correctedServer);
            lifecycleChecks(context, server, controller);

            // Leave Freecam enabled deliberately: the production disconnect hook must clean it up.
            context.runOnClient(client -> controller.setEnabled(true));
            context.waitFor(client -> controller.isActive());
          }
          context.runOnClient(client -> require(!controller.isActive() && !controller.settings().enabled,
                  "Disconnect did not leave Freecam disabled"));
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keySprint);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyShift);
            context.runOnClient(client -> {
                controller.setEnabled(false);
                controller.settings().speed = originalSpeed;
                controller.settings().boostMultiplier = originalBoost;
                restore(controller.settings(), original);
            });
        }
    }

    /** Scroll, smoothing, resume, look-at, follow and menus. Every step keeps the real player still. */
    private static void qualityOfLifeChecks(ClientGameTestContext context, TestServerContext server, FreecamController controller,
                                            Pose clientBody, Pose serverBody) {
        context.runOnClient(client -> controller.setEnabled(true));
        context.waitFor(client -> controller.isActive());
        context.getInput().moveCursor(1, 0);
        context.waitTick();

        // The scroll wheel changes camera speed, not the hotbar slot.
        int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        double before = context.computeOnClient(client -> controller.settings().speed);
        context.getInput().scroll(1);
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(Math.abs(controller.settings().speed - before * 1.15) < 1.0e-6, "Scrolling up did not raise speed by 15%");
            require(client.player.getInventory().getSelectedSlot() == slot, "Scrolling in Freecam changed the hotbar slot");
            require(controller.readoutAlpha() == 1, "Scrolling did not show the speed readout");
        });
        screenshot(context, "donut-freecam-05-speed-readout");
        context.getInput().scroll(-1);
        context.waitTicks(2);
        context.runOnClient(client -> require(Math.abs(controller.settings().speed - before) < 1.0e-6, "Scrolling down did not restore speed"));
        context.runOnClient(client -> controller.settings().scrollAdjustsSpeed = false);
        context.getInput().scroll(1);
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(Math.abs(controller.settings().speed - before) < 1.0e-6, "Disabled scroll option still changed speed");
            require(client.player.getInventory().getSelectedSlot() == slot, "Hotbar scroll was not suppressed with the option off");
            controller.settings().scrollAdjustsSpeed = true;
        });
        assertBodyUnchanged(context, server, clientBody, serverBody);

        // Smoothing ramps velocity up and down instead of jumping.
        context.runOnClient(client -> controller.settings().smoothing = 0.8);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTick();
        double first = context.computeOnClient(client -> controller.velocity().length());
        context.waitTicks(30);
        double settled = context.computeOnClient(client -> controller.velocity().length());
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTick();
        double coasting = context.computeOnClient(client -> controller.velocity().length());
        require(first > 0 && first < 0.5, "Smoothing did not ease the first tick of movement: " + first);
        require(Math.abs(settled - 1) < 0.01, "Smoothed velocity did not reach the configured speed: " + settled);
        require(coasting > 0.05 && coasting < settled, "Smoothing did not ease the stop: " + coasting);
        context.runOnClient(client -> controller.settings().smoothing = 0);
        context.waitFor(client -> controller.velocity().lengthSqr() == 0, 40);
        assertBodyUnchanged(context, server, clientBody, serverBody);

        // Resume keeps the last camera pose in the same world; off starts at the eyes again.
        move(context, controller, options -> options.keyJump, 3);
        Vec3 parked = context.computeOnClient(client -> controller.position());
        context.runOnClient(client -> {
            controller.settings().resumeLastPosition = true;
            controller.setEnabled(false);
            controller.setEnabled(true);
            require(controller.position().distanceTo(parked) < 1.0e-6, "Resume did not return to the last camera position");
            controller.settings().resumeLastPosition = false;
            controller.setEnabled(false);
            controller.setEnabled(true);
            require(controller.position().distanceTo(client.player.getEyePosition()) < 1.0e-6,
                    "With resume off the camera did not start at the player's eyes");
        });
        assertBodyUnchanged(context, server, clientBody, serverBody);

        // Look-at keeps facing a block and ignores mouse look.
        context.runOnClient(client -> {
            var settings = controller.settings();
            settings.targetX = -2;
            settings.targetY = 65;
            settings.targetZ = 0;
            settings.lookAtTarget = true;
        });
        move(context, controller, options -> options.keyRight, 4);
        context.waitTicks(2);
        context.runOnClient(client -> {
            Vec3 facing = Vec3.directionFromRotation(controller.pitch(), controller.yaw());
            Vec3 wanted = controller.lookTarget().subtract(controller.position()).normalize();
            require(facing.dot(wanted) > 0.999, "Look-at did not face the target block");
            float yaw = controller.yaw();
            controller.turn(80, -30);
            require(controller.yaw() == yaw, "Mouse look was not paused while looking at a target");
        });
        screenshot(context, "donut-freecam-06-look-at-chest");
        context.runOnClient(client -> controller.settings().lookAtTarget = false);
        assertBodyUnchanged(context, server, clientBody, serverBody);

        // Follow with nobody else in range keeps the camera where it is.
        Vec3 still = context.computeOnClient(client -> controller.position());
        context.runOnClient(client -> controller.settings().followNearestPlayer = true);
        context.waitTicks(5);
        context.runOnClient(client -> {
            require(controller.followTarget() == null, "Follow selected the local player or a phantom target");
            require(controller.position().distanceTo(still) < 1.0e-6, "Follow moved the camera with no target");
        });
        followFakePlayer(context, server, controller);
        context.runOnClient(client -> controller.settings().followNearestPlayer = false);
        assertBodyUnchanged(context, server, clientBody, serverBody);

        // Menus freeze camera movement and mouse look.
        Vec3 menuStart = context.computeOnClient(client -> controller.position());
        context.setScreen(() -> new dev.donut.client.gui.FreecamTargetScreen(null, new ClientConfig(), () -> { }));
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(5);
        context.getInput().releaseKey(options -> options.keyUp);
        context.runOnClient(client -> {
            require(controller.isActive(), "Opening a menu disabled Freecam");
            require(controller.position().distanceTo(menuStart) < 1.0e-6, "The camera moved while a menu was open");
            float yaw = controller.yaw();
            controller.turn(80, 0);
            require(controller.yaw() == yaw, "The camera turned while a menu was open");
            client.setScreen(null);
        });
        context.waitForScreen(null);
        assertBodyUnchanged(context, server, clientBody, serverBody);
        context.runOnClient(client -> controller.setEnabled(false));
        context.waitTicks(2);
    }

    /** Adds a server fake player beside the camera and checks the camera keeps its offset as that player moves. */
    private static void followFakePlayer(ClientGameTestContext context, TestServerContext server, FreecamController controller) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.fromString("2f4b1b6e-6f0e-4d9a-9a49-6d6f6e757431"), "FollowMe");
        server.runOnServer(mc -> {
            var level = mc.overworld();
            var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level, profile);
            fake.snapTo(6.5, 66, 6.5, 0, 0);
            mc.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                    .createPlayerInitializing(java.util.List.of(fake)));
            level.addNewPlayer(fake);
        });
        try {
            context.waitFor(client -> controller.followTarget() != null, 100);
        } catch (Throwable notDelivered) {
            System.out.println("[FreecamGameTest] SKIP follow-with-player: the client never received the fake player entity");
            removeFake(server, profile);
            return;
        }
        Vec3 offset = context.computeOnClient(client -> controller.position().subtract(controller.followTarget().position()));
        server.runOnServer(mc -> {
            var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(mc.overworld(), profile);
            fake.teleportTo(10.5, 66, 2.5);
        });
        context.waitFor(client -> controller.followTarget() != null
                && controller.followTarget().position().distanceTo(new Vec3(10.5, 66, 2.5)) < 0.01, 100);
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(controller.followTarget().getName().getString().equals("FollowMe"), "Follow picked the wrong player");
            Vec3 now = controller.position().subtract(controller.followTarget().position());
            require(now.distanceTo(offset) < 1.0e-3, "Follow did not keep the camera's offset from the moving player");
        });
        screenshot(context, "donut-freecam-07-follow-player");
        System.out.println("[FreecamGameTest] PASS follow-with-player using a server fake player");
        removeFake(server, profile);
        context.waitFor(client -> controller.followTarget() == null, 100);
    }

    private static void removeFake(TestServerContext server, com.mojang.authlib.GameProfile profile) {
        server.runOnServer(mc -> {
            var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(mc.overworld(), profile);
            mc.overworld().removePlayerImmediately(fake, Entity.RemovalReason.DISCARDED);
            mc.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(
                    java.util.List.of(profile.id())));
        });
    }

    /** Death and dimension changes must turn Freecam off without leaving neutral input installed. */
    private static void lifecycleChecks(ClientGameTestContext context, TestServerContext server, FreecamController controller) {
        context.runOnClient(client -> controller.setEnabled(true));
        context.waitFor(client -> controller.isActive());
        server.runCommand("kill @a");
        context.waitFor(client -> !client.player.isAlive());
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(!controller.isActive() && !controller.settings().enabled, "Freecam stayed on after death");
            require(client.getCameraEntity() == client.player, "Death left the camera detached from the player");
            client.player.respawn();
        });
        context.waitFor(client -> client.player != null && client.player.isAlive(), 200);
        context.runOnClient(client -> client.setScreen(null));
        context.waitTicks(5);
        context.runOnClient(client -> require(!controller.isActive(), "Freecam re-enabled itself after respawn"));

        server.runCommand("tp @a 0.5 65 8.5");
        context.waitTicks(5);
        context.runOnClient(client -> controller.setEnabled(true));
        context.waitFor(client -> controller.isActive());
        server.runCommand("execute in minecraft:the_nether run tp @a 0.5 80 0.5");
        context.waitFor(client -> client.level != null && client.level.dimension() == net.minecraft.world.level.Level.NETHER, 400);
        context.waitTicks(2);
        context.runOnClient(client -> {
            require(!controller.isActive() && !controller.settings().enabled, "Freecam stayed on after changing dimension");
            require(client.player.input.getClass().getEnclosingClass() != FreecamController.class,
                    "Freecam's neutral input survived the dimension change");
        });
        server.runCommand("execute in minecraft:overworld run tp @a 0.5 65 8.5");
        context.waitFor(client -> client.level != null && client.level.dimension() == net.minecraft.world.level.Level.OVERWORLD, 400);
        context.waitTicks(10);
    }

    private static double[] snapshot(dev.donut.client.config.FreecamSettings s) {
        return new double[] {s.smoothing, s.scrollAdjustsSpeed ? 1 : 0, s.speedReadout ? 1 : 0, s.resumeLastPosition ? 1 : 0,
                s.followNearestPlayer ? 1 : 0, s.followRange, s.lookAtTarget ? 1 : 0, s.targetX, s.targetY, s.targetZ};
    }

    private static void restore(dev.donut.client.config.FreecamSettings s, double[] v) {
        s.smoothing = v[0]; s.scrollAdjustsSpeed = v[1] != 0; s.speedReadout = v[2] != 0; s.resumeLastPosition = v[3] != 0;
        s.followNearestPlayer = v[4] != 0; s.followRange = (int) v[5]; s.lookAtTarget = v[6] != 0;
        s.targetX = (int) v[7]; s.targetY = (int) v[8]; s.targetZ = (int) v[9];
    }

    private static void resetHelpers(FreecamController controller) {
        var s = controller.settings();
        s.smoothing = 0; s.scrollAdjustsSpeed = true; s.speedReadout = true; s.resumeLastPosition = false;
        s.followNearestPlayer = false; s.followRange = 64; s.lookAtTarget = false;
    }

    private static double move(ClientGameTestContext context, FreecamController controller,
                               Function<Options, KeyMapping> key, int ticks) {
        Vec3 start = context.computeOnClient(client -> controller.position());
        context.getInput().holdKey(key);
        context.waitTicks(ticks);
        context.getInput().releaseKey(key);
        context.waitTick();
        return context.computeOnClient(client -> controller.position().distanceTo(start));
    }

    private static void assertBodyUnchanged(ClientGameTestContext context, TestServerContext server,
                                            Pose clientBody, Pose serverBody) {
        context.runOnClient(client -> {
            require(client.getCameraEntity() == client.player, "Virtual movement replaced the player's camera entity");
            clientBody.assertUnchanged(client.player, "Client player");
        });
        server.runOnServer(mcServer -> serverBody.assertUnchanged(mcServer.getPlayerList().getPlayers().getFirst(),
                "Server player"));
    }

    private static float wrappedDegrees(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Pose(Vec3 position, float yaw, float pitch) {
        static Pose of(Entity player) {
            return new Pose(player.position(), player.getYRot(), player.getXRot());
        }

        void assertUnchanged(Entity player, String label) {
            require(position.distanceTo(player.position()) < 0.00001, label + " moved during Freecam");
            require(Math.abs(wrappedDegrees(player.getYRot() - yaw)) < 0.001, label + " yaw changed during Freecam");
            require(Math.abs(player.getXRot() - pitch) < 0.001, label + " pitch changed during Freecam");
        }
    }
}
