package dev.donut.client.search;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SearchTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void validIdsResolveToRegisteredBlocks() {
        assertSame(Blocks.DIAMOND_ORE, SearchBlocks.validate("diamond_ore"));
        assertSame(Blocks.DIAMOND_ORE, SearchBlocks.validate(" Minecraft:Diamond_Ore "));
        assertSame(Blocks.SPAWNER, SearchBlocks.validate("spawner"));
        assertEquals("minecraft:ancient_debris", SearchBlocks.id(SearchBlocks.validate("ancient_debris")));
        assertSame(Blocks.CHEST, SearchBlocks.block("minecraft:chest"));
        assertNull(SearchBlocks.block("minecraft:not_a_block"));
        assertNull(SearchBlocks.block(null));
    }

    @Test void invalidIdsExplainWhy() {
        var unknown = assertThrows(IllegalArgumentException.class, () -> SearchBlocks.validate("diamond_or"));
        assertTrue(unknown.getMessage().contains("Unknown block minecraft:diamond_or"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("Did you mean minecraft:"), "Unknown IDs suggest a close match: " + unknown.getMessage());
        for (String air : new String[] {"air", "cave_air", "minecraft:void_air"})
            assertThrows(IllegalArgumentException.class, () -> SearchBlocks.validate(air), air);
        assertThrows(IllegalArgumentException.class, () -> SearchBlocks.validate("diamond ore!"));
        assertThrows(IllegalArgumentException.class, () -> SearchBlocks.validate("othermod:diamond_ore"));
    }

    @Test void suggestionsPreferExactThenPrefixMatches() {
        List<String> stone = SearchBlocks.suggestions("stone", 6);
        assertEquals("minecraft:stone", stone.getFirst(), "The exact path comes first: " + stone);
        assertTrue(stone.get(1).startsWith("minecraft:stone"), "Prefix matches come next: " + stone);
        assertEquals(6, stone.size());
        List<String> diamond = SearchBlocks.suggestions("DIAMOND", 20);
        assertTrue(diamond.contains("minecraft:diamond_ore") && diamond.contains("minecraft:diamond_block")
                && diamond.contains("minecraft:deepslate_diamond_ore"), diamond.toString());
        assertTrue(diamond.indexOf("minecraft:diamond_block") < diamond.indexOf("minecraft:deepslate_diamond_ore"));
        assertTrue(SearchBlocks.suggestions("minecraft:ancient", 3).contains("minecraft:ancient_debris"));
        assertTrue(SearchBlocks.suggestions("air", 50).stream().noneMatch(id -> id.equals("minecraft:air") || id.endsWith(":cave_air")),
                "Air is never suggested");
        assertEquals(List.of(), SearchBlocks.suggestions("", 5));
        assertEquals(List.of(), SearchBlocks.suggestions("zzzz_nothing", 5));
        assertEquals(List.of(), SearchBlocks.suggestions("stone", 0));
    }

    @Test void mergingJoinsNeighboursIntoExactBoxes() {
        assertEquals(List.of(new BoxMerger.Box(1, 2, 3, 2, 3, 4)), BoxMerger.merge(List.of(new BlockPos(1, 2, 3))));
        // A row of three is one box.
        var row = BoxMerger.merge(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)));
        assertEquals(List.of(new BoxMerger.Box(0, 0, 0, 3, 1, 1)), row);
        // A 2x2x2 cube is one box.
        List<BlockPos> cube = new ArrayList<>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) cube.add(new BlockPos(x + 10, y - 5, z));
        assertEquals(List.of(new BoxMerger.Box(10, -5, 0, 12, -3, 2)), BoxMerger.merge(cube));
        // Separate blocks stay separate, and an L-shape never covers the empty corner.
        assertEquals(2, BoxMerger.merge(List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0))).size());
        var corner = BoxMerger.merge(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1)));
        assertEquals(2, corner.size());
        assertTrue(corner.stream().noneMatch(box -> box.contains(new BlockPos(1, 0, 1))), "The merged boxes cover a block that is not there");
        assertTrue(BoxMerger.merge(List.of()).isEmpty());
    }

    @Test void mergedBoxesPartitionRandomShapesExactly() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            Set<BlockPos> input = new HashSet<>();
            int count = 1 + random.nextInt(60);
            for (int i = 0; i < count; i++) input.add(new BlockPos(random.nextInt(5), random.nextInt(4) - 2, random.nextInt(5)));
            var boxes = BoxMerger.merge(input);
            int volume = 0;
            Set<BlockPos> covered = new HashSet<>();
            for (var box : boxes) {
                volume += box.volume();
                for (int x = box.minX(); x < box.maxX(); x++) for (int y = box.minY(); y < box.maxY(); y++) for (int z = box.minZ(); z < box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    assertTrue(input.contains(pos), "Box covers a missing block " + pos);
                    assertTrue(covered.add(pos), "Block in two boxes " + pos);
                }
            }
            assertEquals(input.size(), volume);
            assertEquals(input, covered);
            assertTrue(boxes.size() <= input.size());
        }
    }
}
