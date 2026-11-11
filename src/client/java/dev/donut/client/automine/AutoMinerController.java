package dev.donut.client.automine;

import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.BuilderNavigator;
import dev.donut.client.autobuild.MaterialTracker;
import dev.donut.client.config.AutoMinerSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.freecam.FreecamMotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Finds enabled ores in already-loaded chunks, walks to them with {@link BuilderNavigator} and mines them with the
 * same game-mode destroy calls a held attack button makes. The player's real rotation turns at a capped rate,
 * reach is vanilla, nothing is teleported and no packet is sent except what vanilla sends for those actions.
 * All access is on the client thread.
 */
public final class AutoMinerController {
    public enum State { OFF, SEARCHING, WALKING, MINING, TUNNELING, PAUSED }

    /** How long a skipped target is ignored. */
    public static final int REJECT_TICKS = 1200;
    /** Manual override: control comes back after this many ticks without a movement key. */
    public static final int RESUME_IDLE_TICKS = 40;
    public static final int BREAK_TIMEOUT_TICKS = 600;
    public static final int STOP_NOTICE_TICKS = 100;
    private static final int RESCAN_DELAY_TICKS = 40;
    private static AutoMinerController instance;

    private final ClientConfig config;
    private final Runnable save;
    private final BuilderNavigator navigator = new BuilderNavigator();
    private final OreScanner scanner = new OreScanner();
    private final Map<BlockPos, Long> rejected = new HashMap<>();
    private final Map<BlockPos, String> rejectReasons = new HashMap<>();
    private State state = State.OFF;
    private String pauseReason = "";
    private BlockPos target;
    private OreKind targetKind;
    private ClientLevel session;
    private long tick;
    private int mined;
    private int originalSlot = -1;
    private long manualUntil = -1;
    private MinerRules.Step step;
    private int tunnelDug;
    private int stuck;
    private BlockPos breaking;
    private long breakStart;
    private int blockedTicks;
    /** Where mining last failed to land on the target; tunneling must move before trying from here again. */
    private BlockPos blockedAt;
    private int rescanDelay;
    private String lastSkip;
    private String lastStop;
    private long lastStopTick = Long.MIN_VALUE;

    private enum Break { DONE, WORKING, BLOCKED, NO_TOOL }

    public AutoMinerController(ClientConfig config, Runnable save) {
        this.config = config;
        this.save = save;
        // Never resume by itself after a restart.
        config.autoMiner.enabled = false;
        instance = this;
    }

    public static AutoMinerController get() { return instance; }

    /** While driving, vanilla attack/use/pick handling is suspended so it cannot abort or interfere with mining. */
    public static boolean controlsInput() {
        AutoMinerController miner = instance;
        return miner != null && miner.state != State.OFF && miner.state != State.PAUSED;
    }

    public AutoMinerSettings settings() { return config.autoMiner; }
    public State state() { return state; }
    public String pauseReason() { return state == State.PAUSED ? pauseReason : ""; }
    public BlockPos target() { return target; }
    public OreKind targetKind() { return targetKind; }
    public int mined() { return mined; }
    public String lastSkip() { return lastSkip; }
    public String lastStop() { return lastStop; }
    public boolean rejected(BlockPos pos) { return rejected.containsKey(pos); }
    public String rejectReason(BlockPos pos) { return rejectReasons.get(pos); }
    public boolean drivingInput(Minecraft client) { return navigator.holding(client); }
    public int pendingChunks() { return scanner.pendingChunks(); }

    /** True for a few seconds after the miner stopped itself, so the HUD can say why. */
    public boolean showStopNotice() { return state == State.OFF && lastStop != null && tick - lastStopTick < STOP_NOTICE_TICKS; }

    public void tick(Minecraft client) {
        tick++;
        AutoMinerSettings settings = config.autoMiner;
        if (!settings.enabled) {
            if (state != State.OFF) stop(client, null);
            return;
        }
        if (client.level == null || client.player == null || client.gameMode == null) {
            stop(client, "left the world");
            return;
        }
        LocalPlayer player = client.player;
        if (state == State.OFF) start(client);
        if (client.level != session) { stop(client, "dimension or world changed"); return; }
        if (!player.isAlive() || player.isDeadOrDying()) { stop(client, "you died"); return; }
        if (player.getInventory().getFreeSlot() < 0) { stop(client, "inventory full"); return; }
        navigator.setTurnLimit((float) settings.turnSpeed);

        String pause = pauseReason(client);
        if (pause != null) { pause(client, pause); return; }
        if (state == State.PAUSED) state = target != null ? State.WALKING : State.SEARCHING;

        switch (state) {
            case SEARCHING -> search(client);
            case WALKING -> walk(client);
            case MINING -> mine(client);
            case TUNNELING -> tunnel(client);
            default -> { }
        }
    }

    /** Disconnect: stop without touching a player that is going away. */
    public void disconnect() {
        if (state == State.OFF && !config.autoMiner.enabled) return;
        stop(Minecraft.getInstance(), "disconnected", false);
    }

    /** Stops, releases movement and any block being broken, restores the hotbar slot and disables the module. */
    public void stop(Minecraft client, String reason) { stop(client, reason, true); }

    private void stop(Minecraft client, String reason, boolean live) {
        navigator.reset();
        if (live && client.gameMode != null && client.gameMode.isDestroying()) client.gameMode.stopDestroyBlock();
        if (live && client.player != null && originalSlot >= 0) client.player.getInventory().setSelectedSlot(originalSlot);
        for (KeyMapping key : List.of(client.options.keyAttack, client.options.keyUse)) key.setDown(false);
        originalSlot = -1;
        state = State.OFF;
        clearTarget();
        scanner.clear();
        rejected.clear();
        rejectReasons.clear();
        session = null;
        manualUntil = -1;
        rescanDelay = 0;
        if (reason != null) {
            lastStop = reason;
            lastStopTick = tick;
            if (live && client.player != null)
                client.player.displayClientMessage(Component.literal("Auto Miner stopped: " + reason), true);
        }
        if (config.autoMiner.enabled) {
            config.autoMiner.enabled = false;
            save.run();
        }
    }

    private void start(Minecraft client) {
        session = client.level;
        originalSlot = client.player.getInventory().getSelectedSlot();
        mined = 0;
        lastSkip = null;
        lastStop = null;
        state = State.SEARCHING;
    }

    private String pauseReason(Minecraft client) {
        LocalPlayer player = client.player;
        if (client.screen != null || client.isPaused()) return "menu open";
        if (FreecamController.enabled()) return "Freecam is on";
        if (movementKeyDown(client)) manualUntil = tick + RESUME_IDLE_TICKS;
        if (manualUntil > tick) return "manual control";
        if (player.getHealth() <= config.autoMiner.minHealth) return "low health";
        if (player.isPassenger()) return "riding";
        if (player.isUsingItem()) return "using an item";
        if (client.gameMode.getPlayerMode().isBlockPlacingRestricted()) return "cannot mine in this game mode";
        if (AutoBuilderController.get().isRunning()) return "AutoBuilder is running";
        return null;
    }

    private static boolean movementKeyDown(Minecraft client) {
        var options = client.options;
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
                || options.keyJump.isDown() || options.keyShift.isDown();
    }

    private void pause(Minecraft client, String reason) {
        if (state != State.PAUSED || !reason.equals(pauseReason)) {
            // Hand the player back completely: real input, no block being broken.
            navigator.release();
            abortBreak(client);
            step = null;
        }
        state = State.PAUSED;
        pauseReason = reason;
    }

    private void search(Minecraft client) {
        navigator.hold(client, false);
        BlockPos next = selectTarget(client);
        if (next != null) {
            target = next;
            targetKind = OreKind.of(client.level.getBlockState(next));
            stuck = 0;
            tunnelDug = 0;
            step = null;
            state = aimPoint(client, next) != null && !MinerRules.standsOn(client.player.blockPosition(), next) ? State.MINING : State.WALKING;
            return;
        }
        if (!scanner.sweeping()) {
            if (rescanDelay > 0) { rescanDelay--; return; }
            scanner.begin(client.level, client.player.blockPosition(), config.autoMiner.range);
        }
        if (scanner.tick(client.level, config.autoMiner) && selectTarget(client) == null) rescanDelay = RESCAN_DELAY_TICKS;
    }

    private BlockPos selectTarget(Minecraft client) {
        rejected.values().removeIf(until -> until <= tick);
        rejectReasons.keySet().retainAll(rejected.keySet());
        Vec3 eye = client.player.getEyePosition();
        for (BlockPos pos : MinerRules.byDistance(scanner.results(), eye)) {
            if (rejected.containsKey(pos)) continue;
            String reason = targetProblem(client, pos, eye);
            if (reason == null) return pos;
            if (!reason.isEmpty()) reject(pos, reason);
        }
        return null;
    }

    /** Null when minable, "" when silently not a target, otherwise why it is skipped. */
    private String targetProblem(Minecraft client, BlockPos pos, Vec3 eye) {
        AutoMinerSettings settings = config.autoMiner;
        ClientLevel level = client.level;
        if (!MaterialTracker.isLoaded(level, pos)) return "";
        BlockState state = level.getBlockState(pos);
        OreKind kind = OreKind.of(state);
        if (kind == null || !settings.oreEnabled(kind)) return "";
        if (Vec3.atCenterOf(pos).distanceToSqr(eye) > (double) settings.range * settings.range) return "";
        MinerRules.View view = view(level);
        if (!settings.includeBuried && !MinerRules.exposed(view, pos)) return "";
        if (MinerRules.nearFluid(view, pos)) return "next to lava or water";
        if (toolSlot(client, state) < 0) return "no suitable tool";
        return null;
    }

    private void walk(Minecraft client) {
        if (!stillTarget(client)) return;
        if (aimPoint(client, target) != null && !MinerRules.standsOn(client.player.blockPosition(), target)) {
            navigator.hold(client, false);
            state = State.MINING;
            return;
        }
        MinerRules.View view = view(client.level);
        var result = navigator.moveTo(client, target, cell -> !MinerRules.standsOn(cell, target) && MinerRules.standHazard(view, cell) == null);
        switch (result) {
            case ARRIVED -> state = State.MINING;
            case NO_PATH -> {
                if (config.autoMiner.includeBuried) { state = State.TUNNELING; step = null; tunnelDug = 0; }
                else rejectTarget("no walkable path");
            }
            case STUCK -> { if (++stuck >= 3) rejectTarget("stuck on the way"); }
            default -> { }
        }
    }

    private void mine(Minecraft client) {
        navigator.hold(client, false);
        if (!stillTarget(client)) return;
        if (MinerRules.standsOn(client.player.blockPosition(), target)) { rejectTarget("it is under your feet"); return; }
        switch (breakBlock(client, target)) {
            case DONE -> { mined++; clearTarget(); state = State.SEARCHING; }
            case NO_TOOL -> rejectTarget("no suitable tool");
            case BLOCKED -> {
                if (config.autoMiner.includeBuried) {
                    abortBreak(client);
                    blockedAt = client.player.blockPosition();
                    state = State.TUNNELING;
                    step = null;
                } else rejectTarget("cannot reach it");
            }
            default -> { }
        }
    }

    private void tunnel(Minecraft client) {
        if (!stillTarget(client)) return;
        LocalPlayer player = client.player;
        if (tunnelDug >= config.autoMiner.tunnelLimit) { rejectTarget("tunnel limit reached"); return; }
        if (!player.blockPosition().equals(blockedAt) && aimPoint(client, target) != null
                && !MinerRules.standsOn(player.blockPosition(), target)) {
            navigator.hold(client, false);
            state = State.MINING;
            return;
        }
        MinerRules.View view = view(client.level);
        if (step == null) step = MinerRules.tunnelStep(player.blockPosition(), target);
        // Re-checked every tick: fluids can flow in while digging.
        String hazard = MinerRules.stepHazard(view, step);
        if (hazard != null) { rejectTarget(hazard); return; }
        for (BlockPos pos : step.clear()) {
            BlockState state = client.level.getBlockState(pos);
            if (state.getCollisionShape(client.level, pos).isEmpty()) continue;
            if (MinerRules.standsOn(player.blockPosition(), pos)) { rejectTarget("would dig straight down"); return; }
            navigator.hold(client, false);
            switch (breakBlock(client, pos)) {
                case DONE -> {
                    tunnelDug++;
                    if (pos.equals(target)) { mined++; clearTarget(); this.state = State.SEARCHING; }
                }
                case NO_TOOL -> rejectTarget("no tool for the tunnel");
                case BLOCKED -> rejectTarget("tunnel blocked");
                default -> { }
            }
            return;
        }
        switch (navigator.stepTo(client, step.feet())) {
            case ARRIVED -> { step = null; navigator.hold(client, false); }
            case NO_PATH, STUCK -> rejectTarget("cannot enter the tunnel");
            default -> { }
        }
    }

    /** False (and back to searching) when the target is gone or no longer valid. */
    private boolean stillTarget(Minecraft client) {
        if (target == null) { state = State.SEARCHING; return false; }
        OreKind kind = OreKind.of(client.level.getBlockState(target));
        if (kind == null || !config.autoMiner.oreEnabled(kind)) {
            abortBreak(client);
            clearTarget();
            state = State.SEARCHING;
            return false;
        }
        if (MinerRules.nearFluid(view(client.level), target)) { rejectTarget("next to lava or water"); return false; }
        return true;
    }

    /** One tick of vanilla mining: turn toward the block, then destroy it only once the crosshair is on it. */
    private Break breakBlock(Minecraft client, BlockPos pos) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Break.DONE;
        int slot = toolSlot(client, state);
        if (slot < 0) return Break.NO_TOOL;
        if (player.getInventory().getSelectedSlot() != slot) player.getInventory().setSelectedSlot(slot);
        Vec3 aim = aimPoint(client, pos);
        if (aim == null) return Break.BLOCKED;
        Vec2 wanted = FreecamMotion.lookAngles(player.getEyePosition(), aim);
        if (wanted != null) {
            var turned = MinerRules.turnToward(player.getYRot(), player.getXRot(), wanted.x, wanted.y, (float) config.autoMiner.turnSpeed);
            player.setYRot(turned.yaw());
            player.setXRot(turned.pitch());
        }
        if (!pos.equals(breaking)) { breaking = pos.immutable(); breakStart = tick; blockedTicks = 0; }
        if (tick - breakStart > BREAK_TIMEOUT_TICKS) { abortBreak(client); return Break.BLOCKED; }
        // The same pick vanilla uses for the crosshair, at vanilla reach.
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK || !blockHit.getBlockPos().equals(pos)) {
            if (wanted != null && MinerRules.aligned(player.getYRot(), player.getXRot(), wanted.x, wanted.y, 1) && ++blockedTicks > 10) {
                abortBreak(client);
                return Break.BLOCKED;
            }
            return Break.WORKING;
        }
        Direction face = blockHit.getDirection();
        // Exactly what Minecraft#continueAttack does while attack is held.
        if (client.gameMode.continueDestroyBlock(pos, face)) {
            level.addBreakingBlockEffect(pos, face);
            player.swing(InteractionHand.MAIN_HAND);
        }
        BlockState after = level.getBlockState(pos);
        // Vanilla predicts the break locally (air, or water for a waterlogged block).
        if (after.isAir() || after != state && after.getCollisionShape(level, pos).isEmpty()) {
            breaking = null;
            return Break.DONE;
        }
        return Break.WORKING;
    }

    /** A point on the block the eye can see within vanilla reach (center first, then face centers), or null. */
    private Vec3 aimPoint(Minecraft client, BlockPos pos) {
        LocalPlayer player = client.player;
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() - .05;
        Vec3 center = Vec3.atCenterOf(pos);
        List<Vec3> points = new ArrayList<>(7);
        points.add(center);
        for (Direction direction : Direction.values()) points.add(center.add(direction.getStepX() * .45, direction.getStepY() * .45, direction.getStepZ() * .45));
        for (Vec3 point : points) {
            if (eye.distanceToSqr(point) > reach * reach) continue;
            BlockHitResult hit = client.level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return point;
        }
        return null;
    }

    private int toolSlot(Minecraft client, BlockState state) {
        var inventory = client.player.getInventory();
        List<MinerRules.Tool> hotbar = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            int usesLeft = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : -1;
            boolean harvests = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
            hotbar.add(new MinerRules.Tool(slot, stack.getDestroySpeed(state), harvests, usesLeft));
        }
        var settings = config.autoMiner;
        return MinerRules.chooseTool(hotbar, inventory.getSelectedSlot(), state.requiresCorrectToolForDrops(),
                settings.protectTools, settings.toolReserve);
    }

    /** Unloaded positions read as lava, so anything near the edge of loaded data counts as a hazard. */
    private static MinerRules.View view(ClientLevel level) {
        return pos -> MaterialTracker.isLoaded(level, pos) ? level.getBlockState(pos) : Blocks.LAVA.defaultBlockState();
    }

    private void abortBreak(Minecraft client) {
        if (client.gameMode != null && client.gameMode.isDestroying()) client.gameMode.stopDestroyBlock();
        breaking = null;
    }

    private void reject(BlockPos pos, String reason) {
        rejected.put(pos.immutable(), tick + REJECT_TICKS);
        rejectReasons.put(pos.immutable(), reason);
        OreKind kind = OreKind.of(session == null ? null : session.getBlockState(pos));
        lastSkip = (kind == null ? "Target" : kind.label) + ": " + reason;
    }

    private void rejectTarget(String reason) {
        if (target != null) reject(target, reason);
        abortBreak(Minecraft.getInstance());
        navigator.release();
        clearTarget();
        state = State.SEARCHING;
    }

    private void clearTarget() {
        target = null;
        targetKind = null;
        step = null;
        breaking = null;
        stuck = 0;
        tunnelDug = 0;
        blockedAt = null;
    }

    /** One short status line for the HUD. */
    public String statusLine(Minecraft client) {
        if (state == State.OFF) return showStopNotice() ? "Auto Miner stopped: " + lastStop : "Auto Miner off";
        if (state == State.PAUSED) return "Auto Miner paused: " + pauseReason;
        if (state == State.SEARCHING) return scanner.sweeping() ? "Auto Miner: searching (" + scanner.pendingChunks() + " chunks)"
                : "Auto Miner: searching";
        String what = targetKind == null ? "ore" : targetKind.label;
        int distance = target == null || client.player == null ? 0
                : (int) Math.round(Math.sqrt(Vec3.atCenterOf(target).distanceToSqr(client.player.getEyePosition())));
        String verb = switch (state) { case WALKING -> "walking to"; case TUNNELING -> "tunneling to"; default -> "mining"; };
        return "Auto Miner: " + verb + " " + what + " " + distance + "m";
    }
}
