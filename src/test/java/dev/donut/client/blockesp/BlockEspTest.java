package dev.donut.client.blockesp;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneOreBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockEspTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void legacyMaskMatchesOnlyTheExactDeepslateEmeraldAndCoal() {
        assertTrue(OreMarker.selected(OreMarker.LEGACY_MASK, Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState()));
        assertTrue(OreMarker.selected(OreMarker.LEGACY_MASK, Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()));
        for (Block block : new Block[] {Blocks.EMERALD_ORE, Blocks.COAL_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE,
                Blocks.STONE, Blocks.AIR, Blocks.COAL_BLOCK, Blocks.EMERALD_BLOCK})
            assertFalse(OreMarker.selected(OreMarker.LEGACY_MASK, block.defaultBlockState()), block.toString());
    }

    @Test void everyOverworldOreHasTwoDistinctExactTargets() {
        Set<Integer> seen = new HashSet<>();
        for (var kind : OreMarker.Kind.values()) for (boolean deepslate : new boolean[] {false, true}) {
            Block block = kind.block(deepslate);
            int target = OreMarker.target(block.defaultBlockState());
            assertEquals(OreMarker.target(kind, deepslate), target, block.toString());
            assertEquals(kind, OreMarker.kindOf(target));
            assertEquals(deepslate, OreMarker.deepslateOf(target));
            assertTrue(seen.add(target));
            assertTrue(OreMarker.selected(OreMarker.bit(kind, deepslate), block.defaultBlockState()));
            assertFalse(OreMarker.selected(~OreMarker.bit(kind, deepslate), block.defaultBlockState()));
        }
        assertEquals(OreMarker.TARGETS, seen.size());
        // Lit redstone (touched or walked on) is still the same exact target.
        assertEquals(OreMarker.target(OreMarker.Kind.REDSTONE, true),
                OreMarker.target(Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState().setValue(RedStoneOreBlock.LIT, true)));
        for (Block block : new Block[] {Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS,
                Blocks.RAW_IRON_BLOCK, Blocks.TUFF, Blocks.GILDED_BLACKSTONE})
            assertEquals(-1, OreMarker.target(block.defaultBlockState()), block.toString());
    }

    @Test void kindIdsRoundTripAndUnknownIdsAreRejected() {
        for (var kind : OreMarker.Kind.values()) assertEquals(kind, OreMarker.Kind.byId(kind.id));
        assertNull(OreMarker.Kind.byId("netherite"));
        assertNull(OreMarker.Kind.byId(null));
        assertEquals(OreMarker.Kind.EMERALD, OreMarker.Kind.values()[0], "Rarest kinds come first so per-chunk caps keep them");
        assertEquals(OreMarker.Kind.COAL, OreMarker.Kind.values()[OreMarker.Kind.values().length - 1]);
    }

    @Test void packedScanCoversOnlyItsMask() {
        var scan = new ChunkScan(OreMarker.LEGACY_MASK, new long[] {net.minecraft.core.BlockPos.asLong(-110, -17, -142)},
                new byte[] {(byte) OreMarker.target(OreMarker.Kind.EMERALD, true)}, 0);
        assertTrue(scan.covers(OreMarker.bit(OreMarker.Kind.EMERALD, true)));
        assertFalse(scan.covers(OreMarker.LEGACY_MASK | OreMarker.bit(OreMarker.Kind.DIAMOND, true)));
        assertEquals(new OreMarker(new net.minecraft.core.BlockPos(-110, -17, -142), OreMarker.Kind.EMERALD, true), scan.marker(0));
    }

    @Test void nearestFirstSchedulingHandlesNegativeCoordinatesWithoutDuplicates() {
        Set<Long> cached = new HashSet<>();
        assertEquals(new ChunkPos(-123, -456), BlockEspController.nextChunk(-123, -456, 2, cached));
        int lastDistance = -1;
        for (int i = 0; i < 25; i++) {
            var next = BlockEspController.nextChunk(-123, -456, 2, cached);
            assertNotNull(next);
            int dx = next.x + 123, dz = next.z + 456, distance = dx * dx + dz * dz;
            assertTrue(distance >= lastDistance);
            assertTrue(cached.add(next.toLong()));
            lastDistance = distance;
        }
        assertNull(BlockEspController.nextChunk(-123, -456, 2, cached));
    }
}
