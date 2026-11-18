package dev.donut.client.structurefinder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The friendly-type grouping: real structure paths land in the right group, noise is excluded. */
class StructureCatalogTest {
    private static String group(String path) {
        var g = StructureCatalog.groupFor(path);
        return g == null ? null : g.id();
    }

    @Test void realStructurePathsGroupCorrectly() {
        assertEquals("village", group("village_plains"));
        assertEquals("village", group("village_snowy"));
        assertEquals("end_city", group("end_city"));
        assertEquals("fortress", group("fortress"));
        assertEquals("bastion", group("bastion_remnant"));
        assertEquals("outpost", group("pillager_outpost"));
        assertEquals("monument", group("monument"));
        assertEquals("ruined_portal", group("ruined_portal"));
        assertEquals("ruined_portal", group("ruined_portal_desert"));
        assertEquals("ruined_portal", group("ruined_portal_nether"));
        assertEquals("ocean_ruin", group("ocean_ruin_warm"));
        assertEquals("shipwreck", group("shipwreck_beached"));
        assertEquals("trial_chambers", group("trial_chambers"));
        assertEquals("swamp_hut", group("swamp_hut"));
        assertEquals("desert_pyramid", group("desert_pyramid"));
    }

    @Test void mineshaftsAndUnknownsAreExcluded() {
        assertNull(group("mineshaft"));
        assertNull(group("mineshaft_mesa"));
        assertNull(group("some_datapack_thing"));
        assertNull(group(null));
    }

    @Test void everyGroupHasAUniqueIdAndIsListed() {
        Set<String> ids = new HashSet<>();
        for (var g : StructureCatalog.GROUPS) {
            assertTrue(ids.add(g.id()), "Duplicate group id " + g.id());
            assertNotNull(StructureCatalog.byId(g.id()));
            assertFalse(g.label().isBlank());
            assertFalse(g.patterns().isEmpty());
        }
        assertEquals(ids.size(), StructureCatalog.allIds().size());
        assertTrue(ids.contains("end_city") && ids.contains("stronghold"));
    }

}
