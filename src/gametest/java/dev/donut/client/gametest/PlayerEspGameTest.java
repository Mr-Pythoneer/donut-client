package dev.donut.client.gametest;

import com.mojang.authlib.GameProfile;
import dev.donut.client.DonutClient;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.playeresp.PlayerEspController;
import dev.donut.client.playeresp.PlayerEspTargets;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.CameraType;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Production Player ESP against a server fake player behind a wall, plus summoned mobs and an item: boxes reach the
 * renderer, nametags are drawn with health and distance, and the local player (and anything the camera is inside)
 * is never shown.
 */
public final class PlayerEspGameTest implements FabricClientGameTest {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("7d4c2b1a-3e5f-4a6b-9c8d-0e1f2a3b4c5d"), "EspTarget");
    private static final Vec3 FAKE = new Vec3(0.5, 65, 4.5);

    @Override
    public void runTest(ClientGameTestContext context) {
        PlayerEspController controller = PlayerEspController.get();
        require(controller != null, "The production Player ESP controller was not initialized");
        var config = DonutClient.config();
        var settings = controller.settings();
        String saved = new com.google.gson.Gson().toJson(settings);
        boolean nameHider = config.nameHider.enabled;
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            reset(settings);
            config.nameHider.enabled = false;
            client.options.guiScale().set(2);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
            try (var world = context.worldBuilder().create()) {
                TestServerContext server = world.getServer();
                fixtures(server);
                context.waitTicks(10);
                world.getClientWorld().waitForChunksRender();
                addFake(server);
                context.waitFor(client -> fake(client) != null, 200);
                context.waitTicks(5);
                context.runOnClient(client -> require(controller.targets().isEmpty(), "A disabled Player ESP selected targets"));

                // Enabled: only the fake player (players only by default), never the local player.
                context.runOnClient(client -> settings.enabled = true);
                context.waitFor(client -> controller.targets().size() == 1 && !controller.lastBoxes().isEmpty()
                        && !controller.lastTags().isEmpty(), 200);
                context.runOnClient(client -> {
                    Entity fake = fake(client);
                    var target = controller.targets().getFirst();
                    require(target.entity() == fake, "The target is not the fake player: " + target.entity());
                    require(target.kind() == PlayerEspTargets.Kind.PLAYER, "Wrong kind " + target.kind());
                    require(controller.lastBoxes().contains(fake.getId()), "The renderer did not get a box for the fake player");
                    require(!controller.lastBoxes().contains(client.player.getId()), "The local player got a box");
                    String tag = controller.lastTags().getFirst();
                    require(tag.startsWith("EspTarget") && tag.contains("7.5❤") && tag.endsWith("m"),
                            "Nametag text should show the name, synced health (15 = 7.5 hearts) and distance: " + tag);
                    var living = (Player) fake;
                    require(living.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && living.getMainHandItem().is(Items.DIAMOND_SWORD),
                            "The client did not receive the fake player's equipment for the icons");
                });

                // Boxes change the frame through the wall (compare with opacity 0, tags off), then tags and tracers.
                context.runOnClient(client -> settings.nametags = false);
                Path boxes = screenshot(context, "player-esp-01-boxes");
                context.runOnClient(client -> settings.opacity = 0);
                context.waitTicks(2);
                Path hidden = screenshot(context, "player-esp-00-hidden");
                int changed = changed(boxes, hidden);
                require(changed > 800, "Player boxes did not change the rendered frame through the wall: " + changed);
                context.runOnClient(client -> { settings.opacity = 220; settings.nametags = true; settings.tracers = true; });
                context.waitTicks(2);
                Path tags = screenshot(context, "player-esp-02-tags-tracers");
                require(changed(tags, boxes) > 500, "Nametags and tracers did not draw");
                context.runOnClient(client -> settings.tracers = false);

                // Name Hider privacy, and friends.
                context.runOnClient(client -> config.nameHider.enabled = true);
                context.waitFor(client -> !controller.lastTags().isEmpty() && controller.lastTags().getFirst().startsWith("Player "), 60);
                context.runOnClient(client -> require(controller.lastTags().stream().noneMatch(t -> t.contains("EspTarget")),
                        "Name Hider is on but a real name was drawn"));
                context.runOnClient(client -> settings.respectNameHider = false);
                context.waitFor(client -> !controller.lastTags().isEmpty() && controller.lastTags().getFirst().startsWith("EspTarget"), 60);
                context.runOnClient(client -> { config.nameHider.enabled = false; settings.respectNameHider = true; settings.addFriend("esptarget"); });
                context.waitFor(client -> !controller.targets().isEmpty() && controller.targets().getFirst().friend(), 60);
                context.runOnClient(client -> require(controller.color(controller.targets().getFirst(), client.gameRenderer.getMainCamera().position())
                        == settings.friendColor, "Friends are not drawn in the friend color"));
                screenshot(context, "player-esp-03-friend");
                context.runOnClient(client -> settings.removeFriend("EspTarget"));

                // Mobs, animals and items only when their toggles are on; armor stands never.
                context.runOnClient(client -> require(controller.targets().size() == 1, "Mobs/items were shown with only Players on"));
                context.runOnClient(client -> { settings.hostileMobs = true; settings.passiveMobs = true; settings.items = true; });
                context.waitFor(client -> controller.targets().size() == 4, 100);
                context.runOnClient(client -> {
                    var kinds = controller.targets().stream().map(PlayerEspController.Target::kind).toList();
                    require(kinds.containsAll(List.of(PlayerEspTargets.Kind.PLAYER, PlayerEspTargets.Kind.HOSTILE,
                            PlayerEspTargets.Kind.PASSIVE, PlayerEspTargets.Kind.ITEM)), "Missing a kind: " + kinds);
                    require(controller.targets().stream().noneMatch(t -> t.entity() instanceof net.minecraft.world.entity.decoration.ArmorStand),
                            "Armor stands must never be shown");
                    double last = -1;
                    for (var target : controller.targets()) { require(target.distanceSq() >= last, "Targets not nearest-first"); last = target.distanceSq(); }
                });
                context.waitFor(client -> controller.lastTags().stream().anyMatch(t -> t.startsWith("Diamond x3")), 60);
                screenshot(context, "player-esp-04-mobs-items");

                // Max shown keeps the nearest; range drops far targets.
                context.runOnClient(client -> settings.maxShown = 1);
                context.waitTicks(2);
                context.runOnClient(client -> {
                    require(controller.targets().size() == 1 && controller.total() == 4, "Max shown did not cap at 1");
                    var nearest = controller.targets().getFirst().entity();
                    double best = client.level.getEntity(nearest.getId()).getBoundingBox().getCenter().distanceToSqr(client.gameRenderer.getMainCamera().position());
                    for (Entity entity : client.level.entitiesForRendering()) {
                        if (entity == client.player || PlayerEspTargets.classify(entity instanceof Player, entity instanceof net.minecraft.world.entity.monster.Enemy,
                                entity instanceof net.minecraft.world.entity.Mob, entity instanceof net.minecraft.world.entity.item.ItemEntity) == null) continue;
                        require(entity.getBoundingBox().getCenter().distanceToSqr(client.gameRenderer.getMainCamera().position()) >= best - 1e-6,
                                "Max shown kept a farther target than " + entity);
                    }
                    settings.maxShown = 64;
                    settings.range = 8;
                });
                context.waitTicks(2);
                context.runOnClient(client -> {
                    require(controller.targets().stream().allMatch(t -> t.distance() <= 8.01), "Range kept a far target");
                    require(controller.targets().stream().noneMatch(t -> t.entity() == fake(client)), "The fake player (10 blocks) is beyond 8 blocks");
                    settings.range = 128;
                    settings.hostileMobs = settings.passiveMobs = settings.items = false;
                });
                context.waitFor(client -> controller.targets().size() == 1, 60);

                // Freecam: the local body becomes visible but is never shown; the camera inside the fake hides it.
                FreecamController freecam = FreecamController.getInstance();
                context.runOnClient(client -> freecam.setEnabled(true));
                context.waitFor(client -> freecam.isActive(), 100);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    require(controller.targets().stream().noneMatch(t -> t.entity() == client.player), "Freecam: the local player was selected");
                    require(!controller.lastBoxes().contains(client.player.getId()), "Freecam: the local player got a box");
                });
                context.runOnClient(client -> moveCamera(freecam, fake(client).getBoundingBox().getCenter()));
                context.waitFor(client -> controller.targets().isEmpty() && controller.lastBoxes().isEmpty() && controller.lastTags().isEmpty(), 60);
                context.runOnClient(client -> moveCamera(freecam, new Vec3(0.5, 67.6, 14.5)));
                context.waitFor(client -> controller.targets().size() == 1 && controller.lastBoxes().size() == 1, 60);
                context.runOnClient(client -> freecam.setEnabled(false));
                context.waitFor(client -> !freecam.isActive(), 60);

                // Disable clears everything.
                context.runOnClient(client -> settings.enabled = false);
                context.waitTicks(3);
                context.runOnClient(client -> require(controller.targets().isEmpty() && controller.lastTags().isEmpty(), "Disabling did not clear Player ESP"));
                context.runOnClient(client -> settings.enabled = true);
                context.waitFor(client -> controller.targets().size() == 1, 60);
                removeFake(server);
                context.waitFor(client -> controller.targets().isEmpty(), 100);
                // Leave the module enabled: the production disconnect hook must clear it.
            }
            context.runOnClient(client -> require(controller.targets().isEmpty() && controller.lastBoxes().isEmpty(),
                    "Disconnect did not clear Player ESP"));
        } finally {
            context.runOnClient(client -> {
                var restored = new com.google.gson.Gson().fromJson(saved, dev.donut.client.config.PlayerEspSettings.class);
                copy(restored, settings);
                config.nameHider.enabled = nameHider;
                controller.clear();
                if (FreecamController.getInstance().isActive()) FreecamController.getInstance().setEnabled(false);
            });
        }
    }

    private static void reset(dev.donut.client.config.PlayerEspSettings settings) {
        copy(new dev.donut.client.config.PlayerEspSettings(), settings);
        settings.enabled = false;
    }

    private static void copy(dev.donut.client.config.PlayerEspSettings from, dev.donut.client.config.PlayerEspSettings to) {
        for (var field : dev.donut.client.config.PlayerEspSettings.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(from);
                field.set(to, value instanceof List<?> list ? new java.util.ArrayList<>(list) : value);
            } catch (IllegalAccessException error) { throw new AssertionError(error); }
        }
    }

    private static void moveCamera(FreecamController freecam, Vec3 position) {
        for (String name : new String[] {"position", "previousPosition"}) {
            try {
                var field = FreecamController.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(freecam, position);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }

    private static Entity fake(net.minecraft.client.Minecraft client) {
        if (client.level == null) return null;
        for (var player : client.level.players()) if (player.getUUID().equals(PROFILE.id())) return player;
        return null;
    }

    private static void fixtures(TestServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runCommand("time set noon");
        server.runCommand("fill -16 64 -8 16 64 20 minecraft:smooth_stone");
        server.runCommand("fill -16 65 -8 16 72 20 minecraft:air");
        // A wall between the camera and every target, so everything is seen through it.
        server.runCommand("fill -8 65 9 8 69 9 minecraft:stone");
        server.runCommand("summon minecraft:husk 4.5 65 3.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
        server.runCommand("summon minecraft:pig -4.5 65 3.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
        server.runCommand("summon minecraft:item 2.5 65 6.5 {Item:{id:\"minecraft:diamond\",count:3},PickupDelay:32767,Age:-32768,NoGravity:1b}");
        server.runCommand("summon minecraft:armor_stand -2.5 65 6.5 {NoGravity:1b}");
        server.runCommand("tp @a 0.5 65 14.5 180 8");
    }

    private static void addFake(TestServerContext server) {
        server.runOnServer(mc -> {
            var level = mc.overworld();
            var fake = FakePlayer.get(level, PROFILE);
            fake.snapTo(FAKE.x, FAKE.y, FAKE.z, 0, 0);
            fake.setHealth(15);
            fake.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            fake.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            mc.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
            level.addNewPlayer(fake);
        });
    }

    private static void removeFake(TestServerContext server) {
        server.runOnServer(mc -> {
            var fake = FakePlayer.get(mc.overworld(), PROFILE);
            mc.overworld().removePlayerImmediately(fake, Entity.RemovalReason.DISCARDED);
            mc.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(PROFILE.id())));
        });
    }

    private static Path screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.getToastManager().clear(); client.gui.getChat().clearMessages(false); });
        context.waitTick();
        return context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static int changed(Path a, Path b) {
        try {
            BufferedImage first = ImageIO.read(a.toFile()), second = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
                if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
            return count;
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
