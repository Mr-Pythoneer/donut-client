package dev.donut.client.storageesp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StorageEspTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void everyStorageBlockHasItsOwnKind() {
        assertKind(StorageKind.CHESTS, Blocks.CHEST, Blocks.COPPER_CHEST, Blocks.EXPOSED_COPPER_CHEST, Blocks.WAXED_OXIDIZED_COPPER_CHEST);
        assertKind(StorageKind.TRAPPED_CHESTS, Blocks.TRAPPED_CHEST);
        assertKind(StorageKind.ENDER_CHESTS, Blocks.ENDER_CHEST);
        assertKind(StorageKind.BARRELS, Blocks.BARREL);
        assertKind(StorageKind.SHULKER_BOXES, Blocks.SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX);
        assertKind(StorageKind.HOPPERS, Blocks.HOPPER);
        assertKind(StorageKind.DROPPERS_DISPENSERS, Blocks.DROPPER, Blocks.DISPENSER);
        assertKind(StorageKind.FURNACES, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
        assertKind(StorageKind.BREWING_STANDS, Blocks.BREWING_STAND);
        assertKind(StorageKind.OTHER, Blocks.CRAFTER, Blocks.DECORATED_POT, Blocks.CHISELED_BOOKSHELF);
    }

    @Test void nonStorageBlocksAreIgnored() {
        for (Block block : new Block[] {Blocks.AIR, Blocks.STONE, Blocks.CRAFTING_TABLE, Blocks.BOOKSHELF, Blocks.SPAWNER,
                Blocks.PISTON, Blocks.OBSERVER, Blocks.LECTERN, Blocks.ENCHANTING_TABLE, Blocks.BEACON, Blocks.JUKEBOX})
            assertNull(StorageKind.of(block.defaultBlockState()), block.toString());
        assertNull(StorageKind.of((BlockState) null));
    }

    @Test void kindIdsAreUniqueAndRoundTrip() {
        for (StorageKind kind : StorageKind.values()) {
            assertSame(kind, StorageKind.byId(kind.id));
            assertEquals(kind.color, kind.color & 0xFFFFFF);
        }
        assertNull(StorageKind.byId("not-a-kind"));
    }

    @Test void doubleChestsPairInEveryFacingAndReportOnce() {
        BlockPos origin = new BlockPos(-17, -40, 31);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState left = chest(Blocks.CHEST, facing, ChestType.LEFT);
            BlockState right = chest(Blocks.CHEST, facing, ChestType.RIGHT);
            BlockPos rightPos = StoragePairs.partner(origin, left);
            assertNotNull(rightPos, "Left half must name its partner when facing " + facing);
            assertEquals(1, origin.distManhattan(rightPos));
            assertEquals(origin, StoragePairs.partner(rightPos, right), "Right half must point back when facing " + facing);
            assertTrue(StoragePairs.paired(origin, left, rightPos, right));
            assertTrue(StoragePairs.paired(rightPos, right, origin, left));
            assertNotEquals(StoragePairs.reportsPair(origin, rightPos), StoragePairs.reportsPair(rightPos, origin),
                    "Exactly one half reports the pair");
        }
    }

    @Test void mismatchedOrMissingPartnersStaySingle() {
        BlockPos pos = new BlockPos(0, 64, 0);
        BlockState left = chest(Blocks.CHEST, Direction.NORTH, ChestType.LEFT);
        BlockPos partner = StoragePairs.partner(pos, left);
        assertNull(StoragePairs.partner(pos, chest(Blocks.CHEST, Direction.NORTH, ChestType.SINGLE)));
        assertNull(StoragePairs.partner(pos, Blocks.BARREL.defaultBlockState()));
        assertNull(StoragePairs.partner(pos, Blocks.ENDER_CHEST.defaultBlockState()), "Ender chests never pair");
        assertFalse(StoragePairs.paired(pos, left, partner, Blocks.AIR.defaultBlockState()), "Broken partner");
        assertFalse(StoragePairs.paired(pos, left, partner, chest(Blocks.CHEST, Direction.NORTH, ChestType.SINGLE)));
        assertFalse(StoragePairs.paired(pos, left, partner, chest(Blocks.CHEST, Direction.NORTH, ChestType.LEFT)), "Same type");
        assertFalse(StoragePairs.paired(pos, left, partner, chest(Blocks.CHEST, Direction.SOUTH, ChestType.RIGHT)), "Wrong facing");
        assertFalse(StoragePairs.paired(pos, left, partner, chest(Blocks.TRAPPED_CHEST, Direction.NORTH, ChestType.RIGHT)),
                "A trapped chest is never half of a normal double chest");
        assertFalse(StoragePairs.paired(pos, left, pos.above(), chest(Blocks.CHEST, Direction.NORTH, ChestType.RIGHT)), "Wrong place");
        assertTrue(StoragePairs.paired(pos, chest(Blocks.TRAPPED_CHEST, Direction.NORTH, ChestType.LEFT),
                partner, chest(Blocks.TRAPPED_CHEST, Direction.NORTH, ChestType.RIGHT)), "Trapped double chests pair");
        assertTrue(StoragePairs.paired(pos, chest(Blocks.COPPER_CHEST, Direction.NORTH, ChestType.LEFT),
                partner, chest(Blocks.WEATHERED_COPPER_CHEST, Direction.NORTH, ChestType.RIGHT)), "Copper stages that connected stay paired");
    }

    @Test void nearestKeepsTheClosestTargetsInOrder() {
        List<StorageEspScanner.Target> targets = new ArrayList<>();
        double[] distances = {81, 4, 900, 0.25, 16, 36};
        for (int i = 0; i < distances.length; i++)
            targets.add(new StorageEspScanner.Target(new BlockPos(i, 0, 0), StorageKind.BARRELS, new AABB(new BlockPos(i, 0, 0)), false, distances[i], null));
        var nearest = StorageEspScanner.nearest(targets, 3);
        assertEquals(List.of(0.25, 4.0, 16.0), nearest.stream().map(StorageEspScanner.Target::distanceSq).toList());
        assertEquals(6, StorageEspScanner.nearest(targets, 100).size());
        assertTrue(StorageEspScanner.nearest(targets, 0).isEmpty());
        assertTrue(StorageEspScanner.nearest(List.of(), 5).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> nearest.add(targets.getFirst()), "Snapshots are immutable");
    }

    private static BlockState chest(Block block, Direction facing, ChestType type) {
        return block.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, type);
    }

    private static void assertKind(StorageKind expected, Block... blocks) {
        for (Block block : blocks) {
            assertEquals(expected, StorageKind.of(block.defaultBlockState()), block.toString());
            for (BlockState state : block.getStateDefinition().getPossibleStates())
                assertEquals(expected, StorageKind.of(state), "every state of " + block);
        }
    }
}
