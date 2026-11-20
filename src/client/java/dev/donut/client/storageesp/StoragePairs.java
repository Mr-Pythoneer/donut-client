/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.storageesp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Pure double-chest pairing from block states. A pair is reported once, by its lower-keyed half. */
public final class StoragePairs {
    private StoragePairs() { }

    /** Where this chest half says its other half is, or null for single chests and non-chests. */
    public static BlockPos partner(BlockPos pos, BlockState state) {
        if (state == null || !(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    /**
     * True only when both halves point at each other with opposite types and the same storage kind
     * (a trapped chest never pairs with a normal chest; copper chest oxidation stages still pair).
     */
    public static boolean paired(BlockPos pos, BlockState state, BlockPos otherPos, BlockState other) {
        BlockPos expected = partner(pos, state);
        if (expected == null || !expected.equals(otherPos)) return false;
        BlockPos back = partner(otherPos, other);
        return back != null && back.equals(pos)
                && other.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                && StorageKind.of(state) == StorageKind.of(other);
    }

    /** The half that reports a confirmed pair, so a double chest is counted and drawn exactly once. */
    public static boolean reportsPair(BlockPos pos, BlockPos otherPos) { return pos.asLong() < otherPos.asLong(); }
}
