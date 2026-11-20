/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.structurefinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups the game's individual structures (village_plains, village_desert, ...) into the friendly types a player
 * thinks in (Village). Pure string rules so it can be unit-tested without bootstrapping the game, and it reads
 * whatever structures the running version registers rather than hard-coding a version's list.
 */
public final class StructureCatalog {
    /** A friendly type. {@code dimensionHint} is for display only; the real dimension is where the structure generates. */
    public record Group(String id, String label, String dimensionHint, List<String> patterns) {
        boolean matches(String path) {
            for (String pattern : patterns) if (path.equals(pattern) || path.startsWith(pattern)) return true;
            return false;
        }
    }

    // Rarer / more sought first — this is the order results are grouped in.
    public static final List<Group> GROUPS = List.of(
            new Group("end_city", "End City / Ship", "The End", List.of("end_city")),
            new Group("ancient_city", "Ancient City", "Overworld (deep)", List.of("ancient_city")),
            new Group("trial_chambers", "Trial Chamber", "Overworld (underground)", List.of("trial_chambers")),
            new Group("mansion", "Woodland Mansion", "Overworld", List.of("mansion")),
            new Group("monument", "Ocean Monument", "Ocean", List.of("monument")),
            new Group("stronghold", "Stronghold", "Overworld (underground)", List.of("stronghold")),
            new Group("bastion", "Bastion Remnant", "Nether", List.of("bastion_remnant", "bastion")),
            new Group("fortress", "Nether Fortress", "Nether", List.of("fortress")),
            new Group("outpost", "Pillager Outpost", "Overworld", List.of("pillager_outpost")),
            new Group("village", "Village", "Overworld", List.of("village_", "village")),
            new Group("desert_pyramid", "Desert Pyramid", "Desert", List.of("desert_pyramid", "desert_temple")),
            new Group("jungle_temple", "Jungle Temple", "Jungle", List.of("jungle_temple", "jungle_pyramid")),
            new Group("swamp_hut", "Witch Hut", "Swamp", List.of("swamp_hut")),
            new Group("igloo", "Igloo", "Snowy", List.of("igloo")),
            new Group("ruined_portal", "Ruined Portal", "Any", List.of("ruined_portal")),
            new Group("shipwreck", "Shipwreck", "Ocean / beach", List.of("shipwreck")),
            new Group("ocean_ruin", "Ocean Ruin", "Ocean", List.of("ocean_ruin")),
            new Group("buried_treasure", "Buried Treasure", "Beach", List.of("buried_treasure")),
            new Group("trail_ruins", "Trail Ruins", "Overworld (buried)", List.of("trail_ruins")),
            new Group("nether_fossil", "Nether Fossil", "Nether", List.of("nether_fossil")));

    /** Too common to be worth listing in a finder: they are almost everywhere. */
    private static final List<String> EXCLUDED = List.of("mineshaft");

    private StructureCatalog() { }

    /** The friendly group for a structure's registry path, or null if it's excluded or unrecognised. */
    public static Group groupFor(String path) {
        if (path == null) return null;
        for (String excluded : EXCLUDED) if (path.equals(excluded) || path.startsWith(excluded)) return null;
        for (Group group : GROUPS) if (group.matches(path)) return group;
        return null;
    }

    public static Group byId(String id) {
        for (Group group : GROUPS) if (group.id.equals(id)) return group;
        return null;
    }

    /** Every group id, for a "search all" default. */
    public static List<String> allIds() {
        List<String> ids = new ArrayList<>();
        for (Group group : GROUPS) ids.add(group.id);
        return ids;
    }
}
