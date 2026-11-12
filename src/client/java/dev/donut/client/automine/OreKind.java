package dev.donut.client.automine;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Auto Miner target kinds. Deepslate variants map to the same kind as their stone counterpart. */
public enum OreKind {
    COAL("coal", "Coal ore", false),
    COPPER("copper", "Copper ore", false),
    IRON("iron", "Iron ore", true),
    GOLD("gold", "Gold ore", true),
    REDSTONE("redstone", "Redstone ore", false),
    LAPIS("lapis", "Lapis ore", false),
    DIAMOND("diamond", "Diamond ore", true),
    EMERALD("emerald", "Emerald ore", true),
    NETHER_QUARTZ("nether-quartz", "Nether quartz ore", false),
    NETHER_GOLD("nether-gold", "Nether gold ore", false),
    ANCIENT_DEBRIS("ancient-debris", "Ancient debris", true, false);

    public final String id;
    public final String label;
    /** Whether this kind is a target in fresh settings. */
    public final boolean defaultEnabled;
    /**
     * Fortune multiplies this ore's drop (and Silk Touch turns it into the ore block). False for ancient debris,
     * which always drops itself, so the miner has no Fortune or Silk Touch preference for it.
     */
    public final boolean fortuneDrops;

    OreKind(String id, String label, boolean defaultEnabled) { this(id, label, defaultEnabled, true); }

    OreKind(String id, String label, boolean defaultEnabled, boolean fortuneDrops) {
        this.id = id;
        this.label = label;
        this.defaultEnabled = defaultEnabled;
        this.fortuneDrops = fortuneDrops;
    }

    /** Classifies a received block state; null for anything that is not a supported ore. */
    public static OreKind of(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return COAL;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return COPPER;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return GOLD;
        // Lit and unlit redstone ore are one block with a LIT property.
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return REDSTONE;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return LAPIS;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return DIAMOND;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return EMERALD;
        if (block == Blocks.NETHER_QUARTZ_ORE) return NETHER_QUARTZ;
        if (block == Blocks.NETHER_GOLD_ORE) return NETHER_GOLD;
        if (block == Blocks.ANCIENT_DEBRIS) return ANCIENT_DEBRIS;
        return null;
    }

    /** True for the deepslate form of an ore (deepslate_*_ore); stone and Nether ores are the normal form. */
    public static boolean deepslate(BlockState state) {
        return state != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("deepslate_");
    }

    public static OreKind byId(String id) {
        for (OreKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }
}
