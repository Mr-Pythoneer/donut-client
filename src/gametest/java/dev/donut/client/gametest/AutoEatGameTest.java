package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.autoeat.AutoEatController;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.item.Items;

/** Auto Eat in survival through the production controller: real item use, slot restore, blacklist and hotbar-only. */
public final class AutoEatGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        var eater = AutoEatController.get();
        require(config != null && eater != null, "Auto Eat was not initialized");
        boolean enabled = config.autoEat.enabled;
        int hunger = config.autoEat.hungerThreshold;
        var blocked = new LinkedHashMap<>(config.autoEat.blocked);
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> {
            config.autoEat.enabled = false;
            config.autoEat.hungerThreshold = 14;
            config.autoEat.blocked.clear();
            config.autoEat.sanitize();
        });
        try (var world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("difficulty easy");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule natural_health_regeneration false");
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("fill -6 63 -6 6 63 6 minecraft:smooth_stone");
            server.runCommand("fill -6 64 -6 6 70 6 minecraft:air");
            server.runCommand("tp @a 0.5 64 0.5 0 -60");
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.waitFor(mc -> mc.player != null && mc.player.onGround(), 200);

            // Hotbar: a sword selected in slot 0, bread in slot 4, rotten flesh in slot 2.
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:stone_sword");
            server.runCommand("item replace entity @a hotbar.2 with minecraft:rotten_flesh 4");
            server.runCommand("item replace entity @a hotbar.4 with minecraft:bread 3");
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            setFood(context, server, 10);

            // Off: nothing happens.
            context.waitTicks(20);
            require(food(context) == 10 && count(context, 4) == 3, "Auto Eat ate while disabled");

            // On: picks the bread (not the blacklisted flesh), eats it with normal use, switches back to the sword.
            context.runOnClient(mc -> config.autoEat.enabled = true);
            context.waitFor(mc -> eater.isEating(), 40);
            context.runOnClient(mc -> {
                require(mc.player.getInventory().getSelectedSlot() == 4, "Auto Eat did not select the bread slot");
                require(mc.player.isUsingItem() && mc.player.getUseItem().is(Items.BREAD), "The player is not eating bread");
            });
            context.waitTicks(4);
            context.takeScreenshot(TestScreenshotOptions.of("auto-eat-01-eating").withSize(1280, 800));
            context.waitFor(mc -> !eater.isEating() && mc.player.getFoodData().getFoodLevel() > 14, 200);
            context.waitTicks(3);
            context.runOnClient(mc -> {
                require(mc.player.getInventory().getSelectedSlot() == 0, "Auto Eat did not switch back to slot 0");
                require(mc.player.getInventory().getItem(2).getCount() == 4, "Blacklisted rotten flesh was eaten");
                require(!mc.options.keyUse.isDown(), "The use key was left held");
            });
            int afterBread = count(context, 4);
            require(afterBread < 3, "No bread was consumed");
            require(food(context) >= 15, "Hunger did not rise above the threshold: " + food(context));

            // Blacklist: with only rotten flesh in the hotbar, nothing is eaten.
            server.runCommand("item replace entity @a hotbar.4 with minecraft:air");
            setFood(context, server, 8);
            context.waitTicks(60);
            context.runOnClient(mc -> {
                require(mc.player.getFoodData().getFoodLevel() == 8, "Hunger changed with only blacklisted food");
                require(mc.player.getInventory().getItem(2).getCount() == 4, "Rotten flesh was eaten");
                require(!eater.isEating() && "no allowed food in the hotbar".equals(eater.waiting()), "Unexpected state: " + eater.statusLine());
                require(mc.player.getInventory().getSelectedSlot() == 0, "The slot changed without eating");
            });

            // Hotbar only: bread in the main inventory is never moved or eaten.
            server.runCommand("item replace entity @a inventory.5 with minecraft:bread 5");
            context.waitTicks(60);
            context.runOnClient(mc -> {
                require(mc.player.getFoodData().getFoodLevel() == 8, "Ate food from the main inventory");
                var stack = mc.player.getInventory().getItem(14);
                require(stack.is(Items.BREAD) && stack.getCount() == 5, "Inventory bread was moved: " + stack);
            });
            context.takeScreenshot(TestScreenshotOptions.of("auto-eat-02-no-hotbar-food").withSize(1280, 800));

            // Auto Miner between targets (searching, no ores here): Auto Eat pauses it, eats, and the miner resumes.
            var miner = dev.donut.client.automine.AutoMinerController.get();
            context.runOnClient(mc -> config.autoMiner.enabled = true);
            context.waitFor(mc -> miner.state() == dev.donut.client.automine.AutoMinerController.State.SEARCHING, 60);
            server.runCommand("item replace entity @a hotbar.4 with minecraft:bread 3");
            context.waitFor(mc -> eater.isEating(), 60);
            context.runOnClient(mc -> require(miner.state() == dev.donut.client.automine.AutoMinerController.State.PAUSED
                    && miner.pauseReason().equals("Auto Eat is eating"), "Auto Miner did not pause for eating: " + miner.statusLine(mc)));
            context.waitFor(mc -> !eater.isEating(), 200);
            context.waitFor(mc -> miner.state() == dev.donut.client.automine.AutoMinerController.State.SEARCHING, 40);
            context.runOnClient(mc -> {
                require(mc.player.getFoodData().getFoodLevel() > 8, "Nothing was eaten during the miner pause");
                config.autoMiner.enabled = false;
            });
            context.waitTicks(2);
            server.runCommand("item replace entity @a hotbar.4 with minecraft:air");

            // Low health with hunger 19: eats when "eat when hurt" is on.
            server.runCommand("item replace entity @a hotbar.6 with minecraft:cooked_beef 2");
            setFood(context, server, 19);
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().setHealth(6));
            context.waitFor(mc -> eater.isEating(), 60);
            context.runOnClient(mc -> require(mc.player.getUseItem().is(Items.COOKED_BEEF), "Did not pick the hotbar beef"));
            context.waitFor(mc -> !eater.isEating(), 200);
            context.waitTicks(3);
            context.runOnClient(mc -> require(mc.player.getInventory().getSelectedSlot() == 0, "Slot not restored after the hurt-eat"));

            // Creative: never eats.
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().setHealth(20));
            server.runCommand("gamemode creative @a");
            setFood(context, server, 5);
            context.waitTicks(40);
            context.runOnClient(mc -> require(!eater.isEating() && mc.player.getFoodData().getFoodLevel() == 5, "Ate in creative"));
        } finally {
            context.runOnClient(mc -> {
                config.autoEat.enabled = enabled;
                config.autoEat.hungerThreshold = hunger;
                config.autoEat.blocked.clear();
                config.autoEat.blocked.putAll(blocked);
                mc.options.keyUse.setDown(false);
            });
        }
    }

    private static void setFood(ClientGameTestContext context, TestServerContext server, int level) {
        server.runOnServer(mc -> {
            var player = mc.getPlayerList().getPlayers().getFirst();
            player.getFoodData().setFoodLevel(level);
            player.getFoodData().setSaturation(0);
        });
        context.waitFor(mc -> mc.player.getFoodData().getFoodLevel() == level, 100);
    }

    private static int food(ClientGameTestContext context) { return context.computeOnClient(mc -> mc.player.getFoodData().getFoodLevel()); }

    private static int count(ClientGameTestContext context, int slot) {
        return context.computeOnClient(mc -> mc.player.getInventory().getItem(slot).getCount());
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
