/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.automine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure Auto Miner decisions: exposure, hazards, target order, tool choice, turning and tunnel steps.
 * Everything reads blocks through a {@link View}, so the rules are testable without a level.
 */
public final class MinerRules {
    /** The tallest drop the miner will accept, in blocks. */
    public static final int MAX_FALL = 3;
    /** Most blocks that may fall into one buried target's tunnel (gravel, sand) before it gives up on that target. */
    public static final int MAX_FALLEN = 12;

    /** Block lookup. The controller answers "unknown" (unloaded) positions with a hazard-like state. */
    @FunctionalInterface
    public interface View { BlockState state(BlockPos pos); }

    /**
     * One hotbar slot. {@code speed} is the item's base destroy speed for the block; {@code efficiency} and
     * {@code fortune} are enchantment levels (0 when absent). {@code usesLeft} is -1 for items without durability
     * (including an empty hand).
     */
    public record Tool(int slot, float speed, boolean harvests, int usesLeft, boolean silk, int efficiency, int fortune) {
        public Tool(int slot, float speed, boolean harvests, int usesLeft) { this(slot, speed, harvests, usesLeft, false); }
        public Tool(int slot, float speed, boolean harvests, int usesLeft, boolean silk) { this(slot, speed, harvests, usesLeft, silk, 0, 0); }

        /** The speed the player actually mines at with this tool, Efficiency included (see {@link #effectiveSpeed(float, int)}). */
        public float effectiveSpeed() { return MinerRules.effectiveSpeed(speed, efficiency); }
    }

    /**
     * Vanilla's mining speed with Efficiency: the enchantment adds level squared + 1, but only when the tool's base
     * speed for the block is above 1 (so Efficiency does nothing on blocks the tool isn't made for).
     */
    public static float effectiveSpeed(float base, int efficiency) {
        return efficiency > 0 && base > 1 ? base + efficiency * efficiency + 1 : base;
    }

    /** The next 1x2 tunnel cell to stand in, and the blocks to clear first, in breaking order. */
    public record Step(BlockPos feet, List<BlockPos> clear) { }

    public record Angles(float yaw, float pitch) { }

    private MinerRules() { }

    /** Air or anything you can see through counts as an open face. */
    public static boolean open(BlockState state) {
        return state.isAir() || !state.isSolidRender();
    }

    /** True when at least one face touches air or a non-solid block: an ore a player could see. */
    public static boolean exposed(View view, BlockPos pos) {
        for (Direction direction : Direction.values()) if (open(view.state(pos.relative(direction)))) return true;
        return false;
    }

    /** The block itself or any face neighbour holds lava or water (including waterlogged blocks). */
    public static boolean nearFluid(View view, BlockPos pos) {
        if (!view.state(pos).getFluidState().isEmpty()) return true;
        for (Direction direction : Direction.values())
            if (!view.state(pos.relative(direction)).getFluidState().isEmpty()) return true;
        return false;
    }

    /** Something you can stand on. */
    public static boolean supports(BlockState state) {
        return state.blocksMotion() && state.getFluidState().isEmpty();
    }

    /** How many open cells are below {@code feet} before a supporting block, up to {@code limit + 1}. */
    public static int dropBelow(View view, BlockPos feet, int limit) {
        int drop = 0;
        for (BlockPos cursor = feet.below(); drop <= limit && !supports(view.state(cursor)); cursor = cursor.below()) drop++;
        return drop;
    }

    /** A cell is safe to stand in when the floor holds, nothing there is fluid, and the floor does not border fluid. */
    public static String standHazard(View view, BlockPos feet) {
        if (!view.state(feet).getFluidState().isEmpty() || !view.state(feet.above()).getFluidState().isEmpty()) return "water or lava";
        BlockPos floor = feet.below();
        if (!supports(view.state(floor))) {
            int drop = dropBelow(view, feet, MAX_FALL);
            return drop > MAX_FALL ? "a fall over " + MAX_FALL + " blocks" : "no floor";
        }
        if (nearFluid(view, floor)) return "lava or water by the floor";
        if (loose(view.state(feet.above(2)))) return "gravel or sand overhead";
        return null;
    }

    /**
     * Gravel, sand, red sand and concrete powder: blocks that fall when the block under them is gone, and that the
     * miner may break and clear. Anvils, dragon eggs, pointed dripstone and suspicious sand/gravel (which have
     * contents) are not loose: they stay hazards.
     */
    public static boolean loose(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FallingBlock && !(block instanceof AnvilBlock) && !(block instanceof DragonEggBlock)
                && !state.hasBlockEntity();
    }

    /** How many loose blocks are stacked directly on top of {@code pos}, counted up to {@code limit + 1}. */
    public static int looseAbove(View view, BlockPos pos, int limit) {
        int count = 0;
        for (BlockPos cursor = pos.above(); count <= limit && loose(view.state(cursor)); cursor = cursor.above()) count++;
        return count;
    }

    /**
     * True while a loose block sits right on top of an open cell of the step: it is about to fall into the tunnel
     * (or already floating, as worldgen gravel can). The miner waits instead of breaking or stepping in.
     */
    public static boolean unsettled(View view, Step step) {
        for (BlockPos pos : step.clear())
            if (FallingBlock.isFree(view.state(pos)) && loose(view.state(pos.above()))) return true;
        return false;
    }

    /** Whether a box (the player's) reaches into the vertical column of {@code cell}. */
    public static boolean inColumn(net.minecraft.world.phys.AABB box, BlockPos cell) {
        return box.maxX > cell.getX() && box.minX < cell.getX() + 1 && box.maxZ > cell.getZ() && box.minZ < cell.getZ() + 1;
    }

    /** Mining a block directly below your feet is digging straight down; the miner never does that. */
    public static boolean standsOn(BlockPos feet, BlockPos block) {
        return feet.getX() == block.getX() && feet.getZ() == block.getZ() && feet.getY() - 1 == block.getY();
    }

    /** Blocks the miner may break: solid, finite hardness, no block entity, nothing that falls. */
    public static boolean breakable(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.getBlock().defaultDestroyTime() >= 0
                && !state.hasBlockEntity() && !(state.getBlock() instanceof Fallable);
    }

    /** Nearest first; ties prefer higher, then lower x, then lower z, so the order is stable. */
    public static List<BlockPos> byDistance(Collection<BlockPos> positions, Vec3 origin) {
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.<BlockPos>comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(origin))
                .thenComparing(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed())
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return sorted;
    }

    /**
     * The fastest slot for a block, or -1 when nothing qualifies. Slots that cannot harvest a block that needs the
     * right tool are skipped, as are protected tools at or below {@code reserve} uses. Ties keep the held slot.
     */
    public static int chooseTool(List<Tool> hotbar, int selected, boolean needsHarvest, boolean protect, int reserve) {
        return chooseTool(hotbar, selected, needsHarvest, protect, reserve, null);
    }

    /**
     * As above, with a Silk Touch preference: {@code TRUE} uses a usable Silk Touch tool when there is one (falling
     * back to any other), {@code FALSE} avoids Silk Touch when another usable tool exists, {@code null} doesn't care.
     */
    public static int chooseTool(List<Tool> hotbar, int selected, boolean needsHarvest, boolean protect, int reserve, Boolean silk) {
        return chooseTool(hotbar, selected, needsHarvest, protect, reserve, silk, false);
    }

    /**
     * The full rule, in order: usable tools only (harvests if needed, not protected); then the Silk Touch preference
     * as above; then, with {@code fortune} and no Silk Touch tool left in the running, the highest Fortune level when
     * any tool has Fortune; then the fastest effective speed (Efficiency included); ties keep the held slot, then the
     * first slot.
     */
    public static int chooseTool(List<Tool> hotbar, int selected, boolean needsHarvest, boolean protect, int reserve,
                                 Boolean silk, boolean fortune) {
        List<Tool> usable = new java.util.ArrayList<>();
        for (Tool tool : hotbar) {
            if (protect && tool.usesLeft() >= 0 && tool.usesLeft() <= reserve) continue;
            if (needsHarvest && !tool.harvests()) continue;
            usable.add(tool);
        }
        if (silk != null && usable.stream().anyMatch(tool -> tool.silk() == silk)) usable.removeIf(tool -> tool.silk() != silk);
        if (fortune && usable.stream().noneMatch(Tool::silk)) {
            int most = usable.stream().mapToInt(Tool::fortune).max().orElse(0);
            if (most > 0) usable.removeIf(tool -> tool.fortune() < most);
        }
        Tool best = null;
        for (Tool tool : usable) {
            float speed = tool.effectiveSpeed(), bestSpeed = best == null ? 0 : best.effectiveSpeed();
            if (best == null || speed > bestSpeed || speed == bestSpeed && tool.slot() == selected && best.slot() != selected) best = tool;
        }
        return best == null ? -1 : best.slot();
    }

    /**
     * Whether a player standing in {@code feet} would pick up an item with this box. Vanilla collects items touching
     * the player's box grown by 1 sideways and 0.5 up and down; this assumes the player may be up to 0.3 off the cell
     * centre and keeps a small margin.
     */
    public static boolean reachesItem(BlockPos feet, net.minecraft.world.phys.AABB item) {
        double cx = feet.getX() + .5, cz = feet.getZ() + .5;
        return new net.minecraft.world.phys.AABB(cx - 1.0, feet.getY() - .45, cz - 1.0, cx + 1.0, feet.getY() + 2.2, cz + 1.0).intersects(item);
    }

    /** Moves yaw and pitch toward the target by at most {@code maxStep} degrees each. */
    public static Angles turnToward(float yaw, float pitch, float targetYaw, float targetPitch, float maxStep) {
        float step = Float.isFinite(maxStep) && maxStep > 0 ? maxStep : 0;
        float nextYaw = Float.isFinite(targetYaw) ? Mth.approachDegrees(yaw, targetYaw, step) : yaw;
        float nextPitch = Mth.approach(pitch, Math.clamp(targetPitch, -90, 90), step);
        return new Angles(nextYaw, nextPitch);
    }

    public static boolean aligned(float yaw, float pitch, float targetYaw, float targetPitch, float tolerance) {
        return Math.abs(Mth.wrapDegrees(targetYaw - yaw)) <= tolerance && Math.abs(targetPitch - pitch) <= tolerance;
    }

    /**
     * The next tunnel cell from {@code feet} toward {@code ore}: one horizontal step along the longer axis,
     * stepping up when the ore is above head height and down when it is below the feet. Clear order is top
     * first, so each block is visible once the one in front of it is gone. The block under {@code feet} is never
     * part of a step.
     */
    public static Step tunnelStep(BlockPos feet, BlockPos ore) {
        int dx = ore.getX() - feet.getX(), dz = ore.getZ() - feet.getZ(), dy = ore.getY() - feet.getY();
        Direction direction;
        if (dx == 0 && dz == 0) direction = Direction.EAST; // Directly above or below: approach from the side.
        else if (Math.abs(dx) >= Math.abs(dz)) direction = dx > 0 ? Direction.EAST : Direction.WEST;
        else direction = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        int rise = dy >= 2 ? 1 : dy < 0 ? -1 : 0;
        BlockPos next = feet.relative(direction).above(rise);
        List<BlockPos> clear = new ArrayList<>();
        if (rise > 0) clear.add(feet.above(2)); // Jump headroom over the departure cell.
        if (rise < 0) clear.add(next.above(2)); // Head clearance while stepping down.
        clear.add(next.above());
        clear.add(next);
        return new Step(next, List.copyOf(clear));
    }

    /** Why a tunnel step is unsafe, or null. Without the player's feet the "falls on you" rule is not checked. */
    public static String stepHazard(View view, Step step) { return stepHazard(view, step, null); }

    /**
     * Why a tunnel step is unsafe, or null. Loose blocks (gravel, sand) in the step or stacked on top of it are
     * allowed: the miner clears what falls in. They are unsafe when any of them borders lava or water, when more
     * than {@link #MAX_FALLEN} are stacked over the step, when another kind of falling block (an anvil, dripstone)
     * sits on the stack, or when they would fall into the column the player stands in ({@code feet}).
     */
    public static String stepHazard(View view, Step step, BlockPos feet) {
        for (BlockPos pos : step.clear()) {
            BlockState state = view.state(pos);
            if (nearFluid(view, pos)) return "lava or water ahead";
            if (!state.isAir() && !breakable(state) && !loose(state)) return "an unbreakable block ahead";
        }
        int stacked = 0;
        for (BlockPos pos : step.clear()) {
            if (step.clear().contains(pos.above())) continue; // The column goes on inside the step.
            int column = looseAbove(view, pos, MAX_FALLEN);
            if (column > MAX_FALLEN || (stacked += column) > MAX_FALLEN) return "too much gravel or sand overhead";
            if (view.state(pos.above(column + 1)).getBlock() instanceof Fallable) return "falling blocks overhead";
            for (int i = 1; i <= column; i++) if (nearFluid(view, pos.above(i))) return "lava or water by the gravel";
            if (feet != null && pos.getX() == feet.getX() && pos.getZ() == feet.getZ() && pos.getY() > feet.getY()
                    && (column > 0 || loose(view.state(pos)))) return "gravel or sand would fall on you";
        }
        BlockPos floor = step.feet().below();
        BlockState floorState = view.state(floor);
        if (!supports(floorState)) {
            int drop = dropBelow(view, step.feet(), MAX_FALL);
            return drop > MAX_FALL ? "a fall over " + MAX_FALL + " blocks" : "no floor ahead";
        }
        if (nearFluid(view, floor)) return "lava or water under the tunnel";
        return null;
    }
}
