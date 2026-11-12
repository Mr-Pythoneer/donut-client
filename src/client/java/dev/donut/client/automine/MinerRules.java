package dev.donut.client.automine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure Auto Miner decisions: exposure, hazards, target order, tool choice, turning and tunnel steps.
 * Everything reads blocks through a {@link View}, so the rules are testable without a level.
 */
public final class MinerRules {
    /** The tallest drop the miner will accept, in blocks. */
    public static final int MAX_FALL = 3;

    /** Block lookup. The controller answers "unknown" (unloaded) positions with a hazard-like state. */
    @FunctionalInterface
    public interface View { BlockState state(BlockPos pos); }

    /** One hotbar slot. {@code usesLeft} is -1 for items without durability (including an empty hand). */
    public record Tool(int slot, float speed, boolean harvests, int usesLeft, boolean silk) {
        public Tool(int slot, float speed, boolean harvests, int usesLeft) { this(slot, speed, harvests, usesLeft, false); }
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
        return null;
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
        List<Tool> usable = new java.util.ArrayList<>();
        for (Tool tool : hotbar) {
            if (protect && tool.usesLeft() >= 0 && tool.usesLeft() <= reserve) continue;
            if (needsHarvest && !tool.harvests()) continue;
            usable.add(tool);
        }
        if (silk != null && usable.stream().anyMatch(tool -> tool.silk() == silk)) usable.removeIf(tool -> tool.silk() != silk);
        Tool best = null;
        for (Tool tool : usable) {
            if (best == null || tool.speed() > best.speed()
                    || tool.speed() == best.speed() && tool.slot() == selected && best.slot() != selected) best = tool;
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

    /** Why a tunnel step is unsafe, or null. */
    public static String stepHazard(View view, Step step) {
        for (BlockPos pos : step.clear()) {
            BlockState state = view.state(pos);
            if (nearFluid(view, pos)) return "lava or water ahead";
            if (!state.isAir() && !breakable(state)) return "an unbreakable block ahead";
        }
        BlockPos top = step.clear().getFirst();
        if (view.state(top.above()).getBlock() instanceof Fallable) return "falling blocks overhead";
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
