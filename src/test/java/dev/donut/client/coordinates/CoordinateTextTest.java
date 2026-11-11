package dev.donut.client.coordinates;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CoordinateTextTest {
    @Test void facingFollowsMinecraftYawIncludingWrapping() {
        assertEquals("South (+Z)", CoordinateText.facing(0));
        assertEquals("West (-X)", CoordinateText.facing(90));
        assertEquals("North (-Z)", CoordinateText.facing(180));
        assertEquals("North (-Z)", CoordinateText.facing(-180));
        assertEquals("East (+X)", CoordinateText.facing(-90));
        assertEquals("East (+X)", CoordinateText.facing(270));
        assertEquals("South (+Z)", CoordinateText.facing(720 + 10));
        assertEquals("West (-X)", CoordinateText.facing(-270));
        assertEquals("South (+Z)", CoordinateText.facing(44.9f));
        assertEquals("West (-X)", CoordinateText.facing(45));
        assertEquals("-170.0", CoordinateText.yaw(190));
    }

    @Test void chunkCoordinatesAreCorrectForNegativeBlocks() {
        assertEquals("Chunk 0, 0 | in chunk 0, 0", CoordinateText.chunk(0, 0));
        assertEquals("Chunk -1, -1 | in chunk 15, 15", CoordinateText.chunk(-1, -1));
        assertEquals("Chunk -2, 3 | in chunk 0, 2", CoordinateText.chunk(-32, 50));
    }

    @Test void linkedDimensionConversionUsesFloorAndEightToOne() {
        assertEquals("Nether: 12, -13", CoordinateText.linked(CoordinateText.OVERWORLD, 100, -100));
        assertEquals("Nether: -1, 0", CoordinateText.linked(CoordinateText.OVERWORLD, -0.5, 7.9));
        assertEquals("Overworld: 803, -808", CoordinateText.linked(CoordinateText.NETHER, 100.4, -100.9));
        assertNull(CoordinateText.linked("minecraft:the_end", 10, 10));
    }

    @Test void positionAndDimensionNamesAreReadable() {
        assertEquals("1.5 / -60.0 / -3.3", CoordinateText.position(1.5, -60, -3.25));
        assertEquals("The Nether", CoordinateText.dimensionName("minecraft:the_nether"));
        assertEquals("Overworld", CoordinateText.dimensionName("minecraft:overworld"));
        assertEquals("Cherry Grove", CoordinateText.dimensionName("minecraft:cherry_grove"));
        assertEquals("Unknown", CoordinateText.dimensionName(null));
        assertEquals("Custom Place", CoordinateText.dimensionName("mymod:custom_place"));
    }
}
