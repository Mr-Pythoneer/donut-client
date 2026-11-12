package dev.donut.client.search;

import dev.donut.client.config.SearchSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Block-ID validation and suggestions against the block registry. */
public final class SearchBlocks {
    private SearchBlocks() { }

    /** The registered block for a typed ID, or an exception with a message the inspector can show. */
    public static Block validate(String raw) {
        String id = SearchSettings.normalize(raw);
        Identifier key = Identifier.tryParse(id);
        if (key == null) throw new IllegalArgumentException("Not a valid block ID: " + raw.strip());
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            List<String> close = suggestions(raw, 1);
            throw new IllegalArgumentException("Unknown block " + id + (close.isEmpty() ? "." : ". Did you mean " + close.getFirst() + "?"));
        }
        Block block = BuiltInRegistries.BLOCK.getValue(key);
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR)
            throw new IllegalArgumentException("Air can't be searched for.");
        return block;
    }

    /** The block for a stored ID, or null when it is not registered (for example a removed mod). */
    public static Block block(String id) {
        Identifier key = id == null ? null : Identifier.tryParse(id);
        return key != null && BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key) : null;
    }

    public static String id(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }

    /**
     * Registered block IDs matching typed text: exact path first, then IDs whose path starts with it, then any that
     * contain it; each group alphabetical. Air is never suggested.
     */
    public static List<String> suggestions(String raw, int limit) {
        String text = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || limit <= 0) return List.of();
        int colon = text.indexOf(':');
        String namespace = colon < 0 ? null : text.substring(0, colon), path = colon < 0 ? text : text.substring(colon + 1);
        String spaced = path.replace(' ', '_');
        List<Identifier> matches = new ArrayList<>();
        for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
            if (namespace != null && !namespace.isEmpty() && !key.getNamespace().startsWith(namespace)) continue;
            if (key.getPath().endsWith("air") && (key.getPath().equals("air") || key.getPath().equals("cave_air") || key.getPath().equals("void_air"))) continue;
            if (key.getPath().contains(spaced)) matches.add(key);
        }
        matches.sort(Comparator.<Identifier>comparingInt(key -> key.getPath().equals(spaced) ? 0 : key.getPath().startsWith(spaced) ? 1 : 2)
                .thenComparing(Identifier::getPath).thenComparing(Identifier::getNamespace));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, matches.size()); i++) out.add(matches.get(i).toString());
        return out;
    }

    /** "Diamond Ore" for minecraft:diamond_ore, from the block's translated name. */
    public static String displayName(String id) {
        Block block = block(id);
        return block == null ? id : block.getName().getString();
    }
}
