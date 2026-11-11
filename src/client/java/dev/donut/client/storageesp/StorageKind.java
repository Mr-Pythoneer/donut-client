package dev.donut.client.storageesp;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storage ESP target kinds. This refines Base Finder's coarse {@code SuspectObjects.Category} container,
 * ender-storage, hopper and machine groups into separate per-type toggles; Base Finder itself is unchanged.
 */
public enum StorageKind {
    CHESTS("chests", "Chests", "Chest", 0xE8A33D),
    TRAPPED_CHESTS("trapped-chests", "Trapped chests", "Trapped chest", 0xFF5A4E),
    BARRELS("barrels", "Barrels", "Barrel", 0xC08552),
    SHULKER_BOXES("shulker-boxes", "Shulker boxes", "Shulker box", 0xC77DFF),
    ENDER_CHESTS("ender-chests", "Ender chests", "Ender chest", 0x2EC4B6),
    HOPPERS("hoppers", "Hoppers", "Hopper", 0x9AA5B1),
    DROPPERS_DISPENSERS("droppers-dispensers", "Droppers / dispensers", "Dropper / dispenser", 0x7FA7FF),
    FURNACES("furnaces", "Furnaces / smokers / blast furnaces", "Furnace", 0xFF9F1C),
    BREWING_STANDS("brewing-stands", "Brewing stands", "Brewing stand", 0xF15BB5),
    OTHER("other", "Crafters / decorated pots / chiseled bookshelves", "Other storage", 0xC2D86B),
    STORAGE_ENTITIES("storage-entities", "Chest / hopper minecarts and chest boats", "Storage vehicle", 0x4CC9F0);

    public final String id;
    public final String label;
    public final String singular;
    public final int color;

    StorageKind(String id, String label, String singular, int color) {
        this.id = id;
        this.label = label;
        this.singular = singular;
        this.color = color;
    }

    /** Compact label for the HUD. */
    public String shortLabel() {
        return switch (this) {
            case CHESTS -> "Chests"; case TRAPPED_CHESTS -> "Trapped"; case BARRELS -> "Barrels";
            case SHULKER_BOXES -> "Shulkers"; case ENDER_CHESTS -> "Ender"; case HOPPERS -> "Hoppers";
            case DROPPERS_DISPENSERS -> "Droppers"; case FURNACES -> "Furnaces"; case BREWING_STANDS -> "Brewing";
            case OTHER -> "Other"; case STORAGE_ENTITIES -> "Carts/boats";
        };
    }

    /** Classifies a received block state; null for anything that is not storage. */
    public static StorageKind of(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        // Trapped and copper chests extend ChestBlock; check the more specific class first.
        if (block instanceof TrappedChestBlock) return TRAPPED_CHESTS;
        if (block instanceof ChestBlock) return CHESTS;
        if (block instanceof EnderChestBlock) return ENDER_CHESTS;
        if (block instanceof BarrelBlock) return BARRELS;
        if (block instanceof ShulkerBoxBlock) return SHULKER_BOXES;
        if (block instanceof HopperBlock) return HOPPERS;
        if (block instanceof DispenserBlock) return DROPPERS_DISPENSERS; // DropperBlock extends DispenserBlock.
        if (block instanceof AbstractFurnaceBlock) return FURNACES;
        if (block instanceof BrewingStandBlock) return BREWING_STANDS;
        if (block instanceof CrafterBlock || block instanceof DecoratedPotBlock || block instanceof ChiseledBookShelfBlock) return OTHER;
        return null;
    }

    /** Chest/hopper minecarts and chest boats/rafts the client has received. */
    public static StorageKind of(Entity entity) {
        return entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity && !entity.isRemoved() ? STORAGE_ENTITIES : null;
    }

    public static StorageKind byId(String id) {
        for (StorageKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }
}
