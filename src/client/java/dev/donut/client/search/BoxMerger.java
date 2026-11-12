package dev.donut.client.search;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Greedy box merging for one block type: neighbouring blocks are joined into as few axis-aligned boxes as the
 * greedy pass finds (runs along X, then rows along Z, then layers along Y). The boxes cover exactly the given
 * blocks: they never include a block that was not in the input, and every input block is in exactly one box.
 */
public final class BoxMerger {
    private BoxMerger() { }

    /** Block-grid box; max coordinates are exclusive. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int volume() { return (maxX - minX) * (maxY - minY) * (maxZ - minZ); }
        public AABB bounds() { return new AABB(minX, minY, minZ, maxX, maxY, maxZ); }
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() < maxX && pos.getY() >= minY && pos.getY() < maxY
                    && pos.getZ() >= minZ && pos.getZ() < maxZ;
        }
    }

    public static List<Box> merge(Collection<BlockPos> positions) {
        LongOpenHashSet remaining = new LongOpenHashSet(positions.size());
        for (BlockPos pos : positions) remaining.add(pos.asLong());
        List<BlockPos> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ()).thenComparingInt(pos -> pos.getX()));
        List<Box> boxes = new ArrayList<>();
        for (BlockPos start : ordered) {
            if (!remaining.contains(start.asLong())) continue;
            int x0 = start.getX(), y0 = start.getY(), z0 = start.getZ(), x1 = x0, y1 = y0, z1 = z0;
            while (remaining.contains(BlockPos.asLong(x1 + 1, y0, z0))) x1++;
            while (filled(remaining, x0, x1, y0, y0, z1 + 1, z1 + 1)) z1++;
            while (filled(remaining, x0, x1, y1 + 1, y1 + 1, z0, z1)) y1++;
            for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++)
                remaining.remove(BlockPos.asLong(x, y, z));
            boxes.add(new Box(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1));
        }
        return boxes;
    }

    private static boolean filled(LongOpenHashSet set, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++)
            if (!set.contains(BlockPos.asLong(x, y, z))) return false;
        return true;
    }
}
