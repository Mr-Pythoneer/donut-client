package dev.donut.client.newchunks;

import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.NewChunksSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Feeds {@link NewChunksTracker} from packets the client already received (after vanilla applied them) and builds the
 * flat overlay. Nothing is requested from the server. Cleared on disable, dimension/world change and disconnect.
 */
public final class NewChunksController {
    private static final Direction[] SOURCE_SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
    private static NewChunksController instance;
    /** Overlay boxes the renderer queued last frame; read by tests. */
    public static volatile int lastRenderedBoxes;

    private final ClientConfig config;
    private final NewChunksTracker tracker = new NewChunksTracker();
    private ClientLevel level;

    public record Overlay(AABB bounds, NewChunksTracker.Mark mark) { }

    public NewChunksController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static NewChunksController get() { return instance; }
    public NewChunksTracker tracker() { return tracker; }
    public NewChunksSettings settings() { return config.newChunks; }

    public static void packetApplied(Packet<?> packet) {
        NewChunksController controller = instance;
        if (controller == null || !controller.settings().enabled) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        controller.follow(level);
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) controller.chunkReceived(level, chunk.getX(), chunk.getZ());
        else if (packet instanceof ClientboundBlockUpdatePacket block) controller.blockChanged(level, block.getPos(), block.getBlockState());
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket section)
            section.runUpdates((pos, state) -> controller.blockChanged(level, pos, state));
    }

    public void tick(Minecraft client) {
        if (!settings().enabled || client.level == null) {
            if (level != null || tracker.size() > 0) clear();
            return;
        }
        follow(client.level);
    }

    /** A new ClientLevel means another dimension or world: start over. */
    private void follow(ClientLevel current) {
        if (current != level) {
            tracker.clear();
            level = current;
        }
    }

    public void clear() {
        tracker.clear();
        level = null;
        lastRenderedBoxes = 0;
    }

    public void disconnect() { clear(); }

    void chunkReceived(ClientLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        if (chunk != null) tracker.chunkReceived(ChunkPos.asLong(x, z), hasFlowingFluid(chunk));
    }

    /** The section palette check skips sections that cannot hold flowing liquid, so most chunks cost almost nothing. */
    static boolean hasFlowingFluid(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(state -> flowing(state.getFluidState()))) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (flowing(section.getFluidState(x, y, z))) return true;
        }
        return false;
    }

    void blockChanged(ClientLevel level, BlockPos pos, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (!flowing(fluid)) return;
        boolean besideSource = false;
        for (Direction side : SOURCE_SIDES) {
            BlockPos next = pos.relative(side);
            if (level.hasChunkAt(next) && level.getFluidState(next).isSource()) { besideSource = true; break; }
        }
        tracker.fluidUpdate(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())),
                true, besideSource);
    }

    static boolean flowing(FluidState fluid) { return !fluid.isEmpty() && !fluid.isSource(); }

    /** Flat boxes at the chosen height for marked chunks within range of {@code centerX/centerZ} (chunk coordinates). */
    public List<Overlay> overlay(int centerX, int centerZ) {
        var settings = settings();
        List<Overlay> boxes = new ArrayList<>();
        if (!settings.enabled || (!settings.showNew && !settings.showOld)) return boxes;
        int range = settings.range;
        double y = settings.height;
        for (int dx = -range; dx <= range; dx++)
            for (int dz = -range; dz <= range; dz++) {
                int cx = centerX + dx, cz = centerZ + dz;
                NewChunksTracker.Mark mark = tracker.mark(ChunkPos.asLong(cx, cz));
                if (mark == null || mark == NewChunksTracker.Mark.NEW && !settings.showNew || mark == NewChunksTracker.Mark.OLD && !settings.showOld) continue;
                // Inset a little so neighbouring chunks keep a visible seam.
                boxes.add(new Overlay(new AABB(cx * 16 + .15, y, cz * 16 + .15, cx * 16 + 15.85, y + .02, cz * 16 + 15.85), mark));
            }
        return boxes;
    }
}
