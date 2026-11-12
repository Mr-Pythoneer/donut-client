package dev.donut.client.autoeat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.automine.AutoMinerController;
import dev.donut.client.config.AutoEatSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.freecam.FreecamController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Eats hotbar food when hungry, with ordinary item use: select the slot, use the item like a right click in the air
 * ({@code MultiPlayerGameMode.useItem}), keep the use key held until vanilla finishes eating, then switch back.
 * It never moves items between inventory and hotbar.
 *
 * <p>Auto Miner: below {@link AutoEatSettings#CRITICAL_HUNGER} Auto Eat asks the miner to pause at once. Otherwise it
 * waits until the miner is between targets (searching or collecting drops) and asks then. The miner pauses itself
 * ("Auto Eat is eating") while {@link #holdsMiner()} is true and resumes when eating ends.
 */
public final class AutoEatController {
    /** Longest an eat may take before it is abandoned (vanilla food takes 32 ticks, dried kelp 16). */
    public static final int EAT_TIMEOUT_TICKS = 200;
    public static final int RETRY_TICKS = 40;
    private enum Phase { IDLE, WAITING_MINER, EATING }

    private static AutoEatController instance;
    private final ClientConfig config;
    private Phase phase = Phase.IDLE;
    private int previousSlot = -1, foodSlot = -1, eatTicks, cooldown;
    private String eating, waiting;
    private int eaten;

    public AutoEatController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static AutoEatController get() { return instance; }

    /** True while Auto Miner should stay paused for eating. */
    public static boolean holdsMiner() {
        AutoEatController eater = instance;
        return eater != null && eater.phase != Phase.IDLE;
    }

    public AutoEatSettings settings() { return config.autoEat; }
    public boolean isEating() { return phase == Phase.EATING; }
    public String eatingName() { return phase == Phase.EATING ? eating : null; }
    /** Why Auto Eat is holding back right now, or null. */
    public String waiting() { return waiting; }
    public int eaten() { return eaten; }

    public String statusLine() {
        if (phase == Phase.EATING) return "eating " + eating;
        if (waiting != null) return waiting;
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "idle" : "idle (hunger " + player.getFoodData().getFoodLevel() + "/20)";
    }

    public void tick(Minecraft client) {
        AutoEatSettings settings = settings();
        LocalPlayer player = client.player;
        if (!settings.enabled || player == null || client.gameMode == null || client.level == null) {
            if (phase == Phase.EATING) finish(client, true);
            phase = Phase.IDLE;
            waiting = null;
            return;
        }
        if (cooldown > 0) cooldown--;
        if (phase == Phase.EATING) { continueEating(client); return; }
        waiting = pauseReason(client);
        if (waiting != null || cooldown > 0) { phase = Phase.IDLE; return; }
        int food = player.getFoodData().getFoodLevel();
        if (!FoodChoice.shouldEat(settings, food, player.getHealth())) { phase = Phase.IDLE; return; }
        int slot = FoodChoice.best(candidates(player), settings, player.getInventory().getSelectedSlot());
        if (slot < 0) { phase = Phase.IDLE; waiting = "no allowed food in the hotbar"; return; }
        if (AutoMinerController.controlsInput()) {
            var state = AutoMinerController.get().state();
            boolean between = state == AutoMinerController.State.SEARCHING || state == AutoMinerController.State.COLLECTING;
            if (FoodChoice.critical(food) || between) {
                phase = Phase.WAITING_MINER;
                waiting = "pausing Auto Miner";
            } else {
                phase = Phase.IDLE;
                waiting = "waiting for Auto Miner to finish its target";
            }
            return;
        }
        start(client, slot);
    }

    private String pauseReason(Minecraft client) {
        LocalPlayer player = client.player;
        if (client.screen != null || client.isPaused()) return "menu open";
        if (player.isCreative() || player.isSpectator()) return "not in Survival";
        if (FreecamController.enabled()) return "Freecam is on";
        if (AutoBuilderController.get().isRunning()) return "AutoBuilder is running";
        if (player.isUsingItem()) return "you are using an item";
        if (!player.isAlive()) return "dead";
        return null;
    }

    private void start(Minecraft client, int slot) {
        LocalPlayer player = client.player;
        Inventory inventory = player.getInventory();
        if (previousSlot < 0) previousSlot = inventory.getSelectedSlot();
        if (inventory.getSelectedSlot() != slot) inventory.setSelectedSlot(slot);
        foodSlot = slot;
        ItemStack stack = inventory.getItem(slot);
        // Exactly what a right click in the air does; it sends the selected slot first, then the use.
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            phase = Phase.EATING;
            eatTicks = 0;
            eating = stack.getHoverName().getString();
            waiting = null;
            // Vanilla keeps eating only while the use key is held.
            client.options.keyUse.setDown(true);
        } else {
            phase = Phase.IDLE;
            waiting = "the server did not start eating";
            restoreSlot(client);
            cooldown = RETRY_TICKS;
        }
    }

    private void continueEating(Minecraft client) {
        LocalPlayer player = client.player;
        eatTicks++;
        boolean interrupted = client.screen != null || player.getInventory().getSelectedSlot() != foodSlot
                || player.isCreative() || player.isSpectator() || FreecamController.enabled() || eatTicks > EAT_TIMEOUT_TICKS;
        if (player.isUsingItem() && !interrupted) {
            client.options.keyUse.setDown(true);
            return;
        }
        if (!interrupted) eaten++;
        finish(client, interrupted);
        if (interrupted) return;
        // Still hungry: chain the next bite without switching back in between.
        int food = player.getFoodData().getFoodLevel();
        if (settings().enabled && pauseReason(client) == null && FoodChoice.shouldEat(settings(), food, player.getHealth())) {
            int slot = FoodChoice.best(candidates(player), settings(), player.getInventory().getSelectedSlot());
            if (slot >= 0) {
                int keep = previousSlot;
                start(client, slot);
                if (phase == Phase.EATING) { previousSlot = keep; return; }
            }
        }
        restoreSlot(client);
    }

    /** Stops holding use. An interrupted eat releases the item (nothing is eaten) and switches back. */
    private void finish(Minecraft client, boolean interrupted) {
        LocalPlayer player = client.player;
        if (interrupted && player != null && player.isUsingItem() && client.gameMode != null) client.gameMode.releaseUsingItem(player);
        releaseUseKey(client);
        phase = Phase.IDLE;
        eating = null;
        if (interrupted) {
            restoreSlot(client);
            cooldown = RETRY_TICKS;
        }
    }

    private void restoreSlot(Minecraft client) {
        LocalPlayer player = client.player;
        // Only switch back if the food slot is still selected; a manual slot change wins.
        if (player != null && previousSlot >= 0 && Inventory.isHotbarSlot(previousSlot) && player.getInventory().getSelectedSlot() == foodSlot)
            player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
        foodSlot = -1;
    }

    /** Puts the use key back to what the physical key or button says. */
    private static void releaseUseKey(Minecraft client) {
        KeyMapping use = client.options.keyUse;
        InputConstants.Key key = InputConstants.getKey(use.saveString());
        boolean held = false;
        if (client.screen == null && !key.equals(InputConstants.UNKNOWN)) {
            long window = client.getWindow().handle();
            held = key.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                    : key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(client.getWindow(), key.getValue());
        }
        use.setDown(held);
    }

    /** Hotbar food only (slots 0-8). */
    static List<FoodChoice.Candidate> candidates(LocalPlayer player) {
        List<FoodChoice.Candidate> list = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || !stack.has(DataComponents.CONSUMABLE) || player.getCooldowns().isOnCooldown(stack)) continue;
            if (!player.canEat(food.canAlwaysEat())) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String name = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
            list.add(new FoodChoice.Candidate(slot, name, food.nutrition(), food.saturation()));
        }
        return list;
    }

    public void disconnect() {
        phase = Phase.IDLE;
        eating = waiting = null;
        previousSlot = foodSlot = -1;
        cooldown = 0;
    }
}
