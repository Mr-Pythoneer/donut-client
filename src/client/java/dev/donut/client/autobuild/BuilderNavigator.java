package dev.donut.client.autobuild;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Walks using vanilla player input. Does not teleport, fly, mine, or modify the world. */
public final class BuilderNavigator {
    public enum Result { MOVING, ARRIVED, NO_PATH, STUCK }
    private LocalPlayer owner;
    private ClientInput saved;
    private final SteeringInput steering = new SteeringInput();
    private List<BuilderPathfinder.Cell> route = List.of();
    private int index, stalled;
    private Vec3 lastPosition;
    private BlockPos target;
    private final Set<BuilderPathfinder.Cell> rejectedGoals = new HashSet<>();
    /** Degrees per tick; infinite (the default, used by AutoBuilder) snaps straight to the walking direction. */
    private float turnLimit = Float.POSITIVE_INFINITY;

    private static final class SteeringInput extends ClientInput {
        boolean forward, jump, sneak;
        @Override public void tick() {
            keyPresses = new Input(forward, false, false, false, jump, sneak, false);
            moveVector = forward ? new Vec2(0, 1) : Vec2.ZERO;
        }
        @Override public void makeJump() { if (forward) super.makeJump(); }
    }

    public void hold(Minecraft client, boolean sneak) {
        if (client.player == null) return;
        if (owner != client.player) {
            release(); owner = client.player; saved = owner.input; owner.input = steering;
        }
        steering.forward = false; steering.jump = false; steering.sneak = sneak; steering.tick();
    }

    private BuilderPathfinder.Terrain terrain;
    private boolean walkingForward;

    /** True when the player can walk from {@code from} straight to the cell's centre on level, standable ground. */
    private boolean straight(Vec3 from, BuilderPathfinder.Cell to) {
        if (terrain == null) return false;
        double dx = to.x() + .5 - from.x, dz = to.z() + .5 - from.z, length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-3) return true;
        double nx = -dz / length, nz = dx / length;
        for (double t = 0; t <= length; t += .25) {
            for (double side : new double[] {-.3, 0, .3}) {
                double x = from.x + dx * t / length + nx * side, z = from.z + dz * t / length + nz * side;
                if (!terrain.standable(new BuilderPathfinder.Cell((int) Math.floor(x), to.y(), (int) Math.floor(z)))) return false;
            }
        }
        return true;
    }

    public void release() {
        if (owner != null && owner.input == steering && saved != null) owner.input = saved;
        owner = null; saved = null; steering.forward = false; steering.jump = false; steering.sneak = false; walkingForward = false;
        route = List.of(); index = 0; stalled = 0; lastPosition = null;
    }
    public void replan() { route = List.of(); index = 0; stalled = 0; lastPosition = null; }
    public void reset() { release(); target = null; rejectedGoals.clear(); }
    public void rejectCurrent(Minecraft client) {
        if (client.player != null) rejectedGoals.add(cell(client.player.blockPosition()));
        replan();
    }
    public int routeLength() { return Math.max(0, route.size() - index); }
    /** Turn gradually while walking, at most this many degrees per tick; 0 or less restores instant turns. */
    public void setTurnLimit(float degreesPerTick) {
        turnLimit = Float.isFinite(degreesPerTick) && degreesPerTick > 0 ? degreesPerTick : Float.POSITIVE_INFINITY;
    }
    /** True while this navigator has replaced the player's input with its own steering. */
    public boolean holding(Minecraft client) { return client.player != null && client.player.input == steering; }

    /** Like {@link #moveTo(Minecraft, BlockPos)}, but only stops in cells that {@code stand} also accepts. */
    public Result moveTo(Minecraft client, BlockPos destination, Predicate<BlockPos> stand) {
        if (client.player == null) return Result.NO_PATH;
        return walk(client, destination, at -> stand.test(pos(at)) && goal(client, at, destination));
    }

    /** Walks toward {@code destination} until the feet are in any cell {@code stand} accepts (no reach/placement rule). */
    public Result walkTo(Minecraft client, BlockPos destination, Predicate<BlockPos> stand) {
        if (client.player == null) return Result.NO_PATH;
        return walk(client, destination, at -> stand.test(pos(at)));
    }

    /** Walks until the feet are in exactly this cell. */
    public Result stepTo(Minecraft client, BlockPos feet) {
        BuilderPathfinder.Cell cell = cell(feet);
        return walk(client, feet, cell::equals);
    }

    public Result moveTo(Minecraft client, BlockPos destination) {
        if (client.player == null) return Result.NO_PATH;
        return walk(client, destination, at -> goal(client, at, destination));
    }

    private Result walk(Minecraft client, BlockPos destination, Predicate<BuilderPathfinder.Cell> accept) {
        if (client.level == null || client.player == null) return Result.NO_PATH;
        hold(client, false);
        if (!destination.equals(target)) { target = destination.immutable(); rejectedGoals.clear(); replan(); }
        BuilderPathfinder.Cell feet = cell(client.player.blockPosition());
        if (accept.test(feet)) return Result.ARRIVED;
        if (route.isEmpty() || index >= route.size()) {
            terrain = new BuilderPathfinder.Terrain() {
                @Override public boolean standable(BuilderPathfinder.Cell at) {
                    BlockPos pos = pos(at);
                    if (!MaterialTracker.isLoaded(client.level,pos) || !MaterialTracker.isLoaded(client.level,pos.above(2))) return false;
                    var ground = client.level.getBlockState(pos.below());
                    if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SOUL_CAMPFIRE)
                            || ground.is(Blocks.CACTUS) || ground.is(Blocks.POWDER_SNOW)
                            || !ground.isCollisionShapeFullBlock(client.level, pos.below())) return false;
                    return headroom(at);
                }
                @Override public boolean headroom(BuilderPathfinder.Cell at) {
                    BlockPos pos = pos(at);
                    if (!MaterialTracker.isLoaded(client.level,pos) || !client.level.getFluidState(pos).isEmpty()
                            || !client.level.getFluidState(pos.above()).isEmpty()) return false;
                    var state = client.level.getBlockState(pos);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                            || state.is(Blocks.POWDER_SNOW)) return false;
                    return client.level.noCollision(client.player, new AABB(at.x()+.2, at.y()+.01, at.z()+.2,
                            at.x()+.8, at.y()+1.8, at.z()+.8));
                }
            };
            route = BuilderPathfinder.find(feet, cell(destination), terrain, accept, 6000, 96);
            index = 0;
            if (route.isEmpty()) return Result.NO_PATH;
        }
        BuilderPathfinder.Cell next = route.get(index);
        Vec3 aim = new Vec3(next.x()+.5, next.y(), next.z()+.5);
        if (Float.isFinite(turnLimit)) {
            // Natural walking (Auto Miner): count cells as passed once the feet are in them, and steer toward the
            // farthest of the next few cells reachable in a straight line, so the path is followed without
            // zig-zagging through every cell centre or stopping at each one.
            BuilderPathfinder.Cell here = cell(client.player.blockPosition());
            for (int i = index; i < Math.min(route.size(), index + 5); i++) if (route.get(i).equals(here)) { index = i + 1; break; }
            if (index >= route.size()) { replan(); return Result.MOVING; }
            next = route.get(index);
            int ahead = index;
            while (ahead + 1 < route.size() && ahead - index < 4 && route.get(ahead + 1).y() == next.y()
                    && next.y() == here.y() && straight(client.player.position(), route.get(ahead + 1))) ahead++;
            BuilderPathfinder.Cell look = route.get(ahead);
            aim = new Vec3(look.x()+.5, look.y(), look.z()+.5);
        } else if (client.player.position().distanceToSqr(aim) < .16) {
            index++;
            if (index >= route.size()) { replan(); return Result.MOVING; }
            next = route.get(index); aim = new Vec3(next.x()+.5, next.y(), next.z()+.5);
        }
        Vec3 delta = aim.subtract(client.player.position());
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        if (Float.isFinite(turnLimit)) {
            // Turn like a person: eased every frame, capped in speed; walk only while roughly facing the way, with
            // hysteresis so small corrections never stop and restart the walk.
            float error = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - client.player.getYRot()));
            dev.donut.client.automine.SmoothLook.aim(client, yaw, 8, turnLimit);
            if (error <= 35) walkingForward = true;
            else if (error > 75) walkingForward = false;
            steering.forward = walkingForward;
        } else {
            client.player.setYRot(yaw);
            client.player.setXRot(10);
            steering.forward = true;
        }
        steering.jump = steering.forward && next.y() > client.player.getY()+.25 && client.player.onGround();
        steering.tick();
        // Turning in place is not being stuck.
        if (steering.forward && lastPosition != null && client.player.position().distanceToSqr(lastPosition) < .0004) stalled++;
        else if (steering.forward) stalled = 0;
        lastPosition = client.player.position();
        if (stalled > 30) { rejectCurrent(client); return Result.STUCK; }
        return Result.MOVING;
    }

    private boolean goal(Minecraft client, BuilderPathfinder.Cell at, BlockPos destination) {
        if (rejectedGoals.contains(at)) return false;
        Vec3 eye = new Vec3(at.x()+.5, at.y()+client.player.getEyeHeight(), at.z()+.5);
        // A block cannot be placed into the walking player's body.
        if (at.x()==destination.getX() && at.z()==destination.getZ()
                && at.y()<=destination.getY() && at.y()+1.8>destination.getY()) return false;
        // When the only usable neighbor is below, the top placement face must be viewed from above.
        // Center visibility alone would repeatedly choose ground cells below an elevated support column.
        if (client.level.getBlockState(destination).canBeReplaced() && eye.y<=destination.getY()+.01) {
            boolean alternativeFace=false;
            for (var direction:net.minecraft.core.Direction.values()) {
                if (direction==net.minecraft.core.Direction.DOWN) continue;
                BlockPos neighbor=destination.relative(direction);
                if (!MaterialTracker.isLoaded(client.level,neighbor)) continue;
                var state=client.level.getBlockState(neighbor);
                if (!state.canBeReplaced() && !state.getCollisionShape(client.level,neighbor).isEmpty()) { alternativeFace=true; break; }
            }
            if (!alternativeFace) return false;
        }
        double reach = Math.min(4.25, client.player.blockInteractionRange() - .25);
        if (eye.distanceToSqr(Vec3.atCenterOf(destination)) > reach * reach) return false;
        BlockHitResult hit = client.level.clip(new ClipContext(eye, Vec3.atCenterOf(destination),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(destination);
    }
    private static BuilderPathfinder.Cell cell(BlockPos p) { return new BuilderPathfinder.Cell(p.getX(),p.getY(),p.getZ()); }
    private static BlockPos pos(BuilderPathfinder.Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
}
