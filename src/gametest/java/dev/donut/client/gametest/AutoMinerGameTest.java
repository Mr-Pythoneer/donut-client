package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.automine.AutoMinerController;
import dev.donut.client.automine.AutoMinerController.State;
import dev.donut.client.gui.AutoMinerHud;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The production Auto Miner in a disposable survival world: exposed vs buried targets, a lava-side ore, a tunnel,
 * pausing for a menu and for manual movement, stopping on disable and on disconnect, and no teleports.
 */
public final class AutoMinerGameTest implements FabricClientGameTest {
    /** Exposed diamond, set into the floor some way from the start so the miner has to walk. */
    private static final BlockPos EXPOSED = new BlockPos(8, 64, 0);
    /** Exposed and nearer than {@link #EXPOSED}, but lava sits in a pit beside it. */
    private static final BlockPos LAVA_SIDE = new BlockPos(0, 64, -5), LAVA = new BlockPos(1, 64, -5);
    /** Fully enclosed in stone: ignored unless "Include buried ores" is on. */
    private static final BlockPos BURIED = new BlockPos(-6, 62, 0);
    /** Slow to mine, so the miner can be interrupted mid-break. */
    private static final BlockPos DEBRIS = new BlockPos(5, 64, 5);
    /** Enchantment scenario: a stone diamond ore (Fortune pickaxe) and ancient debris (fastest pickaxe). */
    private static final BlockPos FORTUNE_ORE = new BlockPos(-6, 64, 9), FAST_DEBRIS = new BlockPos(-8, 64, 9);
    /** Gravel scenario: an ore buried in a stone hill, with a gravel column over the tunnel path at x = 3. */
    private static final BlockPos GRAVEL_ORE = new BlockPos(6, 65, -10), GRAVEL_BASE = new BlockPos(3, 66, -10);

    private static boolean tracking;
    private static double maxStep;
    private static Vec3 last;
    /** Gravel scenario bookkeeping: falling blocks seen at all, and the first time one was over the player. */
    private static boolean sawFalling;
    private static String fallViolation;
    private static boolean listening;

    @Override
    public void runTest(ClientGameTestContext context) {
        AutoMinerController miner = AutoMinerController.get();
        require(miner != null, "The production Auto Miner was not initialized");
        var config = DonutClient.config();
        var settings = config.autoMiner;
        var saved = new Saved(settings.enabled, settings.includeBuried, settings.range, settings.minHealth, settings.protectTools,
                settings.tunnelLimit, settings.turnSpeed, settings.showHud, new LinkedHashMap<>(settings.ores), config.showHud);
        if (!listening) {
            listening = true;
            ClientTickEvents.END_CLIENT_TICK.register(AutoMinerGameTest::trackMovement);
        }
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> {
            require(!settings.enabled, "Auto Miner must start disabled");
            settings.includeBuried = false;
            settings.range = 24;
            settings.minHealth = 6;
            settings.protectTools = true;
            settings.tunnelLimit = 32;
            settings.turnSpeed = 18;
            settings.showHud = true;
            settings.ores = new LinkedHashMap<>(dev.donut.client.config.AutoMinerSettings.defaultOres());
            config.showHud = true;
            client.options.guiScale().set(2);
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try {
            try (var world = context.worldBuilder().create()) {
                TestServerContext server = world.getServer();
                fixtures(context, server);
                world.getClientWorld().waitForChunksRender();
                context.runOnClient(client -> {
                    require(client.player.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE), "The pickaxe is not in slot 0");
                    client.player.getInventory().setSelectedSlot(1);
                    require(client.level.getBlockState(BURIED).is(Blocks.DEEPSLATE_DIAMOND_ORE), "Buried fixture missing");
                    require(client.level.getBlockState(LAVA).is(Blocks.LAVA), "Lava fixture missing");
                });

                // 1. Exposed only: walks to and mines the exposed diamond, skips the lava-side one, ignores the buried one.
                startTracking(context);
                context.runOnClient(client -> settings.enabled = true);
                await(context, miner, client -> miner.state() == State.WALKING, 400);
                context.runOnClient(client -> {
                    require(EXPOSED.equals(miner.target()), "Wrong first target: " + miner.target() + " skip=" + miner.lastSkip());
                    require("next to lava or water".equals(miner.rejectReason(LAVA_SIDE)),
                            "The lava-side ore was not skipped: " + miner.rejectReason(LAVA_SIDE));
                    require(AutoMinerController.controlsInput() && miner.drivingInput(client), "Walking does not drive the player");
                });

                // A menu pauses everything and gives the player back.
                context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
                context.waitTicks(5);
                Vec3 paused = context.computeOnClient(client -> client.player.position());
                context.waitTicks(10);
                context.runOnClient(client -> {
                    require(miner.state() == State.PAUSED && "menu open".equals(miner.pauseReason()),
                            "An open menu did not pause: " + miner.state() + " " + miner.pauseReason());
                    require(miner.statusLine(client).equals("Auto Miner paused: menu open"), "Status: " + miner.statusLine(client));
                    require(!miner.drivingInput(client) && !AutoMinerController.controlsInput(), "A paused miner still drives input");
                    require(client.player.position().distanceToSqr(paused) < 1.0e-3, "The player moved while paused");
                    require(!client.gameMode.isDestroying(), "Breaking continued while paused");
                    client.setScreen(null);
                });
                await(context, miner, client -> miner.state() == State.MINING && client.gameMode.isDestroying(), 600);
                context.runOnClient(client -> {
                    require(client.player.getInventory().getSelectedSlot() == 0, "The pickaxe was not selected for mining");
                    require(!AutoMinerHud.lines(client, config, miner).isEmpty(), "The HUD is hidden while mining");
                });
                screenshot(context, "auto-miner-01-mining");
                await(context, miner, client -> miner.mined() == 1 && client.level.getBlockState(EXPOSED).isAir(), 600);
                // It walks over the drop and picks it up before looking for the next ore.
                await(context, miner, client -> miner.state() == State.SEARCHING
                        && client.player.getInventory().countItem(Items.DIAMOND) >= 1, 300);
                context.waitTicks(80); // Long enough for a fresh sweep; nothing else may be chosen.
                context.runOnClient(client -> {
                    require(miner.mined() == 1 && miner.target() == null, "Mined something else: " + miner.mined() + " " + miner.target());
                    require(client.level.getBlockState(BURIED).is(Blocks.DEEPSLATE_DIAMOND_ORE), "The buried ore was touched");
                    require(client.level.getBlockState(LAVA_SIDE).is(Blocks.DIAMOND_ORE), "The lava-side ore was mined");
                    require(client.player.position().distanceToSqr(new Vec3(0.5, 65, 0.5)) > 9, "The player never walked");
                    var lines = AutoMinerHud.lines(client, config, miner);
                    require(lines.size() == 2 && lines.get(1).startsWith("Mined 1"), "HUD lines: " + lines);
                });
                requireNoTeleport();

                // 2. Manual movement takes over mid-break; disabling stops and releases everything.
                server.runCommand("setblock " + DEBRIS.getX() + " " + DEBRIS.getY() + " " + DEBRIS.getZ() + " minecraft:ancient_debris");
                await(context, miner, client -> miner.state() == State.MINING && DEBRIS.equals(miner.target()) && client.gameMode.isDestroying(), 600);
                context.getInput().holdKey(options -> options.keyUp);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    require(miner.state() == State.PAUSED && "manual control".equals(miner.pauseReason()),
                            "A movement key did not take over: " + miner.state() + " " + miner.pauseReason());
                    require(!miner.drivingInput(client) && !client.gameMode.isDestroying(), "Manual control did not release the player");
                });
                context.getInput().releaseKey(options -> options.keyUp);
                context.waitTicks(AutoMinerController.RESUME_IDLE_TICKS - 10);
                context.runOnClient(client -> require(miner.state() == State.PAUSED, "Resumed before the idle delay"));
                await(context, miner, client -> miner.state() == State.MINING && client.gameMode.isDestroying(), 600);
                requireNoTeleport();
                context.runOnClient(client -> settings.enabled = false);
                context.waitTick();
                context.runOnClient(client -> {
                    require(miner.state() == State.OFF && miner.target() == null, "Disabling did not stop: " + miner.state());
                    require(!client.gameMode.isDestroying(), "Disabling left a block half broken");
                    require(!miner.drivingInput(client) && client.player.input instanceof KeyboardInput,
                            "Disabling did not hand the keyboard back: " + client.player.input);
                    require(!client.options.keyAttack.isDown() && !client.options.keyUse.isDown(), "Keys left held");
                    require(client.player.getInventory().getSelectedSlot() == 1, "The original hotbar slot was not restored");
                    require(client.level.getBlockState(DEBRIS).is(Blocks.ANCIENT_DEBRIS), "The debris broke after disabling");
                });
                stopTracking();

                // 4. Variant filter, Silk Touch and pickup: with stone variants off it ignores a plain emerald ore,
                // mines the deepslate one with the Silk Touch pick, and picks up the ore block itself.
                BlockPos deep = new BlockPos(-3, 64, 6), plain = new BlockPos(3, 64, 6);
                server.runCommand("setblock -3 64 6 minecraft:deepslate_emerald_ore");
                server.runCommand("setblock 3 64 6 minecraft:emerald_ore");
                server.runCommand("item replace entity @a hotbar.2 with minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]");
                context.waitFor(client -> client.player.getInventory().getItem(2).is(Items.DIAMOND_PICKAXE)
                        && client.level.getBlockState(deep).is(Blocks.DEEPSLATE_EMERALD_ORE), 100);
                startTracking(context);
                context.runOnClient(client -> { settings.stoneVariants = false; settings.enabled = true; });
                await(context, miner, client -> miner.state() == State.MINING && deep.equals(miner.target()) && client.gameMode.isDestroying(), 600);
                context.runOnClient(client -> require(client.player.getInventory().getSelectedSlot() == 2,
                        "The Silk Touch pickaxe was not chosen for the deepslate ore"));
                await(context, miner, client -> client.level.getBlockState(deep).isAir()
                        && client.player.getInventory().countItem(Items.DEEPSLATE_EMERALD_ORE) >= 1, 600);
                screenshot(context, "auto-miner-03-silk-pickup");
                await(context, miner, client -> miner.state() == State.SEARCHING, 200);
                context.waitTicks(60);
                context.runOnClient(client -> {
                    require(client.level.getBlockState(plain).is(Blocks.EMERALD_ORE), "Mined a stone-variant ore with stone variants off");
                    settings.enabled = false;
                    settings.stoneVariants = true;
                });
                requireNoTeleport();
                stopTracking();
                server.runCommand("setblock 3 64 6 minecraft:stone");
                context.waitFor(client -> client.level.getBlockState(plain).is(Blocks.STONE), 100);

                // 5. Enchantments: a stone diamond ore (Silk Touch not wanted) uses the Fortune III pickaxe over the faster
                // Efficiency V one; ancient debris has no Fortune or Silk Touch preference, so the fastest wins.
                server.runCommand("item replace entity @a hotbar.3 with minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:fortune\":3}]");
                server.runCommand("item replace entity @a hotbar.4 with minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
                server.runCommand("setblock " + FORTUNE_ORE.getX() + " " + FORTUNE_ORE.getY() + " " + FORTUNE_ORE.getZ() + " minecraft:diamond_ore");
                // The half-mined debris from scenario 2 would otherwise be the next target.
                server.runCommand("setblock " + DEBRIS.getX() + " " + DEBRIS.getY() + " " + DEBRIS.getZ() + " minecraft:stone");
                context.waitFor(client -> client.level.getBlockState(DEBRIS).is(Blocks.STONE)
                        && client.player.getInventory().getItem(3).is(Items.DIAMOND_PICKAXE)
                        && client.player.getInventory().getItem(4).is(Items.DIAMOND_PICKAXE)
                        && client.level.getBlockState(FORTUNE_ORE).is(Blocks.DIAMOND_ORE)
                        && miner.state() == State.OFF, 100);
                startTracking(context);
                context.runOnClient(client -> settings.enabled = true);
                await(context, miner, client -> miner.state() == State.MINING && FORTUNE_ORE.equals(miner.target())
                        && client.gameMode.isDestroying(), 600);
                context.runOnClient(client -> require(client.player.getInventory().getSelectedSlot() == 3,
                        "The Fortune pickaxe was not chosen for a stone diamond ore: slot " + client.player.getInventory().getSelectedSlot()));
                await(context, miner, client -> client.level.getBlockState(FORTUNE_ORE).isAir(), 600);
                await(context, miner, client -> miner.state() == State.SEARCHING, 300);
                server.runCommand("setblock " + FAST_DEBRIS.getX() + " " + FAST_DEBRIS.getY() + " " + FAST_DEBRIS.getZ() + " minecraft:ancient_debris");
                await(context, miner, client -> miner.state() == State.MINING && FAST_DEBRIS.equals(miner.target())
                        && client.gameMode.isDestroying(), 600);
                context.runOnClient(client -> require(client.player.getInventory().getSelectedSlot() == 4,
                        "The Efficiency pickaxe was not chosen for ancient debris: slot " + client.player.getInventory().getSelectedSlot()));
                await(context, miner, client -> client.level.getBlockState(FAST_DEBRIS).isAir() && miner.mined() == 2, 600);
                await(context, miner, client -> miner.state() == State.SEARCHING, 300);
                context.runOnClient(client -> settings.enabled = false);
                context.waitTick();
                requireNoTeleport();
                stopTracking();

                // 3. Buried on: no walkable spot can see it, so it tunnels 1x2 to it without digging straight down.
                server.runCommand("setblock " + DEBRIS.getX() + " " + DEBRIS.getY() + " " + DEBRIS.getZ() + " minecraft:stone");
                server.runCommand("tp @a -9.5 65 0.5 -90 0");
                context.waitFor(client -> client.player.position().distanceToSqr(new Vec3(-9.5, 65, 0.5)) < 1.0e-3 && client.player.onGround(), 200);
                startTracking(context);
                context.runOnClient(client -> { settings.includeBuried = true; settings.enabled = true; });
                await(context, miner, client -> miner.state() == State.TUNNELING && BURIED.equals(miner.target()), 600);
                await(context, miner, client -> miner.mined() == 1 && client.level.getBlockState(BURIED).isAir(), 1200);
                screenshot(context, "auto-miner-02-tunnel");
                context.runOnClient(client -> {
                    require(client.player.getY() < 65 && client.player.getY() >= 62, "The tunnel did not go down: " + client.player.position());
                    require(client.level.getBlockState(LAVA_SIDE).is(Blocks.DIAMOND_ORE), "The lava-side ore was mined");
                    // The first step is beside the start, never straight down under it.
                    require(!client.level.getBlockState(new BlockPos(-10, 64, 0)).isAir(), "Dug straight down under the start");
                });
                requireNoTeleport();
                stopTracking();

                // 6. Gravel: a buried ore in a stone hill with three gravel above the tunnel path. It breaks the block
                // under the gravel, waits for what falls to land, mines it again until the column is gone, and never
                // stands under a falling block.
                context.runOnClient(client -> settings.enabled = false);
                context.waitTick();
                server.runCommand("fill 2 65 -12 10 70 -8 minecraft:stone");
                server.runCommand("setblock " + GRAVEL_ORE.getX() + " " + GRAVEL_ORE.getY() + " " + GRAVEL_ORE.getZ() + " minecraft:diamond_ore");
                server.runCommand("fill 3 67 -10 3 69 -10 minecraft:gravel");
                server.runCommand("tp @a 0.5 65 -9.5 -90 0");
                context.waitFor(client -> client.player.position().distanceToSqr(new Vec3(0.5, 65, -9.5)) < 1.0e-3 && client.player.onGround()
                        && client.level.getBlockState(GRAVEL_BASE.above(3)).is(Blocks.GRAVEL)
                        && client.level.getBlockState(GRAVEL_ORE).is(Blocks.DIAMOND_ORE), 200);
                startTracking(context);
                context.runOnClient(client -> settings.enabled = true);
                await(context, miner, client -> miner.state() == State.TUNNELING && GRAVEL_ORE.equals(miner.target()), 600);
                await(context, miner, client -> miner.mined() == 1 && client.level.getBlockState(GRAVEL_ORE).isAir(), 1600);
                screenshot(context, "auto-miner-04-gravel");
                context.runOnClient(client -> {
                    for (int dy = 0; dy <= 3; dy++)
                        require(!client.level.getBlockState(GRAVEL_BASE.above(dy)).is(Blocks.GRAVEL), "Gravel left at " + GRAVEL_BASE.above(dy));
                    require(miner.fallenCleared() >= 1, "Nothing that fell in was cleared: " + miner.fallenCleared());
                    require(sawFalling, "No falling gravel was ever seen");
                    require(fallViolation == null, fallViolation);
                    require(client.player.getHealth() >= client.player.getMaxHealth() - 0.01f, "The player was hurt: " + client.player.getHealth());
                });
                requireNoTeleport();
                stopTracking();
                // Leave it enabled: the production disconnect hook must stop it.
            }
            context.runOnClient(client -> {
                require(miner.state() == State.OFF && miner.target() == null, "Disconnect left the miner running: " + miner.state());
                require(!settings.enabled, "Disconnect left the module enabled");
                require(!AutoMinerController.controlsInput(), "Disconnect left input suspended");
            });
        } finally {
            stopTracking();
            context.runOnClient(client -> {
                settings.enabled = false;
                miner.stop(client, null);
                settings.enabled = saved.enabled();
                settings.includeBuried = saved.includeBuried();
                settings.range = saved.range();
                settings.minHealth = saved.minHealth();
                settings.protectTools = saved.protectTools();
                settings.tunnelLimit = saved.tunnelLimit();
                settings.turnSpeed = saved.turnSpeed();
                settings.showHud = saved.showHud();
                settings.ores = saved.ores();
                settings.stoneVariants = settings.deepslateVariants = true;
                config.showHud = saved.showHud2();
            });
        }
    }

    private static void fixtures(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 80 0.5");
        context.waitFor(client -> client.player != null && Math.abs(client.player.getZ() - 0.5) < 1.0e-3, 200);
        context.waitTicks(10);
        server.runCommand("difficulty peaceful");
        server.runCommand("time set noon");
        server.runCommand("fill -14 56 -14 14 64 14 minecraft:stone");
        server.runCommand("fill -14 65 -14 14 76 14 minecraft:air");
        server.runCommand("setblock " + EXPOSED.getX() + " 64 " + EXPOSED.getZ() + " minecraft:diamond_ore");
        server.runCommand("setblock " + LAVA_SIDE.getX() + " 64 " + LAVA_SIDE.getZ() + " minecraft:diamond_ore");
        server.runCommand("setblock " + LAVA.getX() + " 64 " + LAVA.getZ() + " minecraft:lava");
        server.runCommand("setblock " + BURIED.getX() + " " + BURIED.getY() + " " + BURIED.getZ() + " minecraft:deepslate_diamond_ore");
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:diamond_pickaxe");
        server.runCommand("give @a minecraft:dirt 4");
        server.runCommand("tp @a 0.5 65 0.5 180 20");
        context.waitFor(client -> client.player.onGround() && Math.abs(client.player.getY() - 65) < 1.0e-3
                && client.player.getInventory().getItem(1).is(Items.DIRT), 200);
    }

    /** waitFor, but a timeout reports what the miner was doing. */
    private static void await(ClientGameTestContext context, AutoMinerController miner,
                              java.util.function.Predicate<Minecraft> condition, int timeout) {
        try {
            context.waitFor(condition, timeout);
        } catch (AssertionError timeout2) {
            String state = context.computeOnClient(client -> miner.state() + " target=" + miner.target() + " pause=" + miner.pauseReason()
                    + " skip=" + miner.lastSkip() + " stop=" + miner.lastStop() + " at=" + client.player.position()
                    + " destroying=" + client.gameMode.isDestroying() + " status=" + miner.statusLine(client) + " mined=" + miner.mined()
                    + " debris=" + client.level.getBlockState(DEBRIS) + " debrisRejected=" + miner.rejectReason(DEBRIS)
                    + " yaw=" + client.player.getYRot() + " pitch=" + client.player.getXRot());
            throw new AssertionError("Timed out: " + state, timeout2);
        }
    }

    private static void trackMovement(Minecraft client) {
        if (!tracking || client.player == null) return;
        Vec3 now = client.player.position();
        if (last != null) maxStep = Math.max(maxStep, now.distanceTo(last));
        last = now;
        var feet = client.player.blockPosition();
        for (var falling : client.level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                client.player.getBoundingBox().inflate(16, 32, 16), net.minecraft.world.entity.Entity::isAlive)) {
            sawFalling = true;
            var at = falling.blockPosition();
            if (fallViolation == null && at.getX() == feet.getX() && at.getZ() == feet.getZ() && at.getY() >= feet.getY())
                fallViolation = "A falling block was over the player at " + at + " while standing at " + feet;
        }
        if (fallViolation == null && client.player.isInWall()) fallViolation = "The player was inside a block at " + now;
    }

    private static void startTracking(ClientGameTestContext context) {
        context.runOnClient(client -> { last = null; maxStep = 0; sawFalling = false; fallViolation = null; tracking = true; });
    }

    private static void stopTracking() { tracking = false; last = null; }

    /** Walking, stepping down and short drops stay well under a block per tick; a teleport would not. */
    private static void requireNoTeleport() {
        require(maxStep < 1.0, "The player jumped " + maxStep + " blocks in one tick");
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.getToastManager().clear(); client.gui.getChat().clearMessages(false); });
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1280, 800));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private record Saved(boolean enabled, boolean includeBuried, int range, int minHealth, boolean protectTools, int tunnelLimit,
                         double turnSpeed, boolean showHud, Map<String, Boolean> ores, boolean showHud2) { }
}
