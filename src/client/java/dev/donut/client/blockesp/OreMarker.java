/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.blockesp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Original generated block coordinates, never an assertion about the server's current block.
 * A target is one exact block: an ore {@link Kind} in its stone or deepslate form.
 */
public record OreMarker(BlockPos pos, Kind kind, boolean deepslate) {
    /** Rarest first: this is the HUD/settings order and the order kept when a chunk's results are capped. */
    public enum Kind {
        EMERALD("emerald", "Emerald", 0x55EE88),
        DIAMOND("diamond", "Diamond", 0x5EE6E0),
        GOLD("gold", "Gold", 0xF5D04A),
        LAPIS("lapis", "Lapis", 0x4A7BEA),
        REDSTONE("redstone", "Redstone", 0xF0524A),
        IRON("iron", "Iron", 0xD8B89A),
        COPPER("copper", "Copper", 0xE0895A),
        COAL("coal", "Coal", 0xA7B7CB);

        public final String id, label;
        public final int defaultColor;

        // No block references here: settings load this enum, and must not need a bootstrapped registry.
        Kind(String id, String label, int defaultColor) {
            this.id = id;
            this.label = label;
            this.defaultColor = defaultColor;
        }

        public Block block(boolean deepslateVariant) { return Table.BLOCKS[target(this, deepslateVariant)]; }

        public static Kind byId(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
    }

    /** Number of exact target blocks (8 ores x 2 variants); target bit = kind ordinal * 2 + (deepslate ? 1 : 0). */
    public static final int TARGETS = Kind.values().length * 2;
    /** Today's (pre-extension) targets: deepslate emerald and deepslate coal. */
    public static final int LEGACY_MASK = bit(Kind.EMERALD, true) | bit(Kind.COAL, true);

    public OreMarker(BlockPos pos, int target) { this(pos, kindOf(target), deepslateOf(target)); }

    public int target() { return target(kind, deepslate); }
    public Block block() { return kind.block(deepslate); }

    public static int target(Kind kind, boolean deepslate) { return kind.ordinal() * 2 + (deepslate ? 1 : 0); }
    public static int bit(Kind kind, boolean deepslate) { return 1 << target(kind, deepslate); }
    public static Kind kindOf(int target) { return Kind.values()[target >> 1]; }
    public static boolean deepslateOf(int target) { return (target & 1) != 0; }

    /** Exact target index of a block state (either variant, lit or unlit redstone), or -1 for anything else. */
    public static int target(BlockState state) {
        Block block = state.getBlock();
        for (int target = 0; target < TARGETS; target++) if (Table.BLOCKS[target] == block) return target;
        return -1;
    }

    /** True when the state is one of the exact targets selected in {@code mask}. */
    public static boolean selected(int mask, BlockState state) {
        int target = target(state);
        return target >= 0 && (mask & 1 << target) != 0;
    }

    /** Indexed by target: stone then deepslate form of each kind, in {@link Kind} order. */
    private static final class Table {
        static final Block[] BLOCKS = {
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE};
    }
}
