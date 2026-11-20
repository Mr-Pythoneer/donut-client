/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.structurefinder;

import com.mojang.datafixers.util.Pair;
import dev.donut.client.structurefinder.StructureCatalog.Group;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Finds vanilla structures from a seed using the game's own placement and biome logic — the same code behind
 * {@code /locate} — run against a headless generation world (see VanillaOreGenerator). Results are biome-accurate,
 * not grid guesses. Read-only generation: it never touches the player's world or sends anything to a server.
 *
 * <p>Must run on the thread that owns {@code server} (the generation worker).
 */
public final class StructureSearch {
    /** The three vanilla dimensions, tried in turn; a structure only has placements in its own. */
    private static final List<ResourceKey<Level>> DIMENSIONS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
    /** Region rings scanned outward for the nearest-of-each search: generous, so even distant structures are found. */
    private static final int NEAREST_REGION_RADIUS = 120;

    public record Hit(String groupId, String groupLabel, String structureId, ResourceKey<Level> dimension, BlockPos pos) { }

    public record Query(BlockPos center, int radiusBlocks, boolean nearestOnly, Set<String> groups, int maxHits) {
        public boolean wants(String groupId) { return groups == null || groups.contains(groupId); }
    }

    public record Results(List<Hit> hits, boolean truncated, boolean cancelled, String error) {
        public static Results error(String message) { return new Results(List.of(), false, false, message); }
    }

    /** The controller implements this to show progress and to stop a long search. */
    public interface Progress {
        void status(String message);
        boolean cancelled();
        default void checkpoint() { }
    }

    private StructureSearch() { }

    public static Results run(MinecraftServer server, Query query, Progress progress) {
        try {
            var registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            // Group the version's structures into the friendly types the user asked for.
            Map<String, List<Holder<Structure>>> byGroup = new LinkedHashMap<>();
            registry.listElements().forEach(holder -> {
                Group group = StructureCatalog.groupFor(holder.key().identifier().getPath());
                if (group != null && query.wants(group.id())) byGroup.computeIfAbsent(group.id(), id -> new ArrayList<>()).add(holder);
            });

            List<Hit> hits = new ArrayList<>();
            boolean truncated = false;
            for (Group group : StructureCatalog.GROUPS) {
                if (progress.cancelled()) return new Results(sorted(hits, query.center()), truncated, true, null);
                List<Holder<Structure>> holders = byGroup.get(group.id());
                if (holders == null) continue;
                progress.status("Searching: " + group.label());
                for (ResourceKey<Level> dimensionKey : DIMENSIONS) {
                    ServerLevel level = server.getLevel(dimensionKey);
                    if (level == null) continue;
                    if (query.nearestOnly()) {
                        Hit nearest = findNearest(level, dimensionKey, group, holders, query.center());
                        if (nearest != null) hits.add(nearest);
                    } else {
                        truncated |= findInArea(level, dimensionKey, group, holders, query, progress, hits);
                    }
                    if (hits.size() >= query.maxHits()) { truncated = true; break; }
                }
                if (hits.size() >= query.maxHits()) { truncated = true; break; }
            }
            return new Results(sorted(hits, query.center()), truncated, progress.cancelled(), null);
        } catch (Exception error) {
            return Results.error(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static Hit findNearest(ServerLevel level, ResourceKey<Level> dim, Group group, List<Holder<Structure>> holders, BlockPos center) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        // A structure that doesn't belong to this dimension has no placements here, so the search returns null fast.
        if (holders.stream().allMatch(h -> level.getChunkSource().getGeneratorState().getPlacementsForStructure(h).isEmpty())) return null;
        Pair<BlockPos, Holder<Structure>> found = generator.findNearestMapStructure(level, HolderSet.direct(holders), center, NEAREST_REGION_RADIUS, false);
        if (found == null) return null;
        BlockPos pos = withHeight(level, found.getFirst());
        return new Hit(group.id(), group.label(), idOf(found.getSecond()), dim, pos);
    }

    /** Returns true if the scan was capped (radius or hit limit) rather than exhausted. */
    private static boolean findInArea(ServerLevel level, ResourceKey<Level> dim, Group group, List<Holder<Structure>> holders,
                                      Query query, Progress progress, List<Hit> hits) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        int centerChunkX = query.center().getX() >> 4, centerChunkZ = query.center().getZ() >> 4;
        int chunkRadius = Math.max(1, query.radiusBlocks() >> 4);
        long radiusSq = (long) query.radiusBlocks() * query.radiusBlocks();
        Set<Long> seen = new HashSet<>();
        int scanned = 0;
        for (Holder<Structure> holder : holders) {
            HolderSet<Structure> one = HolderSet.direct(holder);
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                // Strongholds and other ring-placed structures ignore the grid: take the nearest within the radius.
                if (placement instanceof net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement) {
                    var found = generator.findNearestMapStructure(level, one, query.center(), NEAREST_REGION_RADIUS, false);
                    if (found != null && distSq(found.getFirst(), query.center()) <= radiusSq && seen.add(chunkKey(found.getFirst())))
                        hits.add(new Hit(group.id(), group.label(), idOf(holder), dim, withHeight(level, found.getFirst())));
                    continue;
                }
                for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                    if ((++scanned & 0x1FFF) == 0) {
                        if (progress.cancelled()) return true;
                        progress.checkpoint();
                    }
                    for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
                        if (seen.contains(ChunkPos.asLong(cx, cz)) || !placement.isStructureChunk(state, cx, cz)) continue;
                        int x = (cx << 4) + 8, z = (cz << 4) + 8;
                        if ((long) (x - query.center().getX()) * (x - query.center().getX())
                                + (long) (z - query.center().getZ()) * (z - query.center().getZ()) > radiusSq) continue;
                        // Confirm with the game's own locate (biome check + exact position), scoped to this one region.
                        var found = generator.findNearestMapStructure(level, one, new BlockPos(x, 0, z), 0, false);
                        if (found == null || (found.getFirst().getX() >> 4) != cx || (found.getFirst().getZ() >> 4) != cz) continue;
                        if (!seen.add(ChunkPos.asLong(cx, cz))) continue;
                        hits.add(new Hit(group.id(), group.label(), idOf(holder), dim, withHeight(level, found.getFirst())));
                        if (hits.size() >= query.maxHits()) return true;
                    }
                }
            }
        }
        return false;
    }

    private static long chunkKey(BlockPos pos) { return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }

    private static BlockPos withHeight(ServerLevel level, BlockPos pos) {
        try {
            int y = level.getChunkSource().getGenerator().getFirstFreeHeight(pos.getX(), pos.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            return new BlockPos(pos.getX(), Math.max(level.getMinY() + 1, y), pos.getZ());
        } catch (Exception ignored) {
            return pos;
        }
    }

    private static String idOf(Holder<Structure> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("structure");
    }

    private static List<Hit> sorted(List<Hit> hits, BlockPos center) {
        hits.sort((a, b) -> {
            int group = Integer.compare(groupOrder(a.groupId()), groupOrder(b.groupId()));
            if (group != 0) return group;
            return Long.compare(distSq(a.pos(), center), distSq(b.pos(), center));
        });
        return hits;
    }

    private static int groupOrder(String id) {
        for (int i = 0; i < StructureCatalog.GROUPS.size(); i++) if (StructureCatalog.GROUPS.get(i).id().equals(id)) return i;
        return Integer.MAX_VALUE;
    }

    private static long distSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
