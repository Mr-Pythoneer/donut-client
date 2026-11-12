package dev.donut.client.automine;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.AutoMinerSettings;
import dev.donut.client.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Stone/deepslate filters, the Silk Touch preference and per-frame turning. */
class AutoMinerVariantsTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void deepslateFormIsDetected() {
        assertTrue(OreKind.deepslate(Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState()));
        assertTrue(OreKind.deepslate(Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()));
        assertFalse(OreKind.deepslate(Blocks.EMERALD_ORE.defaultBlockState()));
        assertFalse(OreKind.deepslate(Blocks.NETHER_GOLD_ORE.defaultBlockState()));
        assertFalse(OreKind.deepslate(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
        assertFalse(OreKind.deepslate(null));
    }

    @Test void variantFiltersSelectTheForm() {
        var settings = new AutoMinerSettings();
        var stone = Blocks.DIAMOND_ORE.defaultBlockState();
        var deep = Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        assertTrue(settings.wants(stone) && settings.wants(deep));
        settings.stoneVariants = false;
        assertFalse(settings.wants(stone));
        assertTrue(settings.wants(deep));
        settings.stoneVariants = true;
        settings.deepslateVariants = false;
        assertTrue(settings.wants(stone));
        assertFalse(settings.wants(deep));
        // The ore list still applies, and non-ores are never wanted.
        settings.deepslateVariants = true;
        settings.ores.put(OreKind.DIAMOND.id, false);
        assertFalse(settings.wants(stone) || settings.wants(deep));
        assertFalse(settings.wants(Blocks.STONE.defaultBlockState()));
        // Nether ores count as the normal form.
        settings.ores.put(OreKind.NETHER_GOLD.id, true);
        settings.stoneVariants = false;
        assertFalse(settings.wants(Blocks.NETHER_GOLD_ORE.defaultBlockState()));
    }

    @Test void silkTouchDefaultsAndPersistence() throws Exception {
        var settings = new AutoMinerSettings();
        assertTrue(settings.wantsSilk(Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState()));
        assertFalse(settings.wantsSilk(Blocks.EMERALD_ORE.defaultBlockState()));
        assertTrue(settings.collectDrops && settings.stoneVariants && settings.deepslateVariants);
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"autoMiner\":{\"silkDeepslate\":false,\"silkStone\":true,\"collectDrops\":false,\"stoneVariants\":false}}");
        var loaded = new ConfigStore(file).load().autoMiner;
        assertFalse(loaded.silkDeepslate || loaded.collectDrops || loaded.stoneVariants);
        assertTrue(loaded.silkStone && loaded.deepslateVariants);
        // Older files without the new fields get the defaults.
        Files.writeString(file, "{\"autoMiner\":{\"range\":30}}");
        loaded = new ConfigStore(file).load().autoMiner;
        assertTrue(loaded.silkDeepslate && loaded.collectDrops && loaded.stoneVariants && loaded.deepslateVariants);
        assertFalse(loaded.silkStone);
    }

    @Test void silkPreferencePicksOrAvoidsSilkTouch() {
        var plain = new MinerRules.Tool(0, 8, true, 1000, false);
        var silk = new MinerRules.Tool(1, 8, true, 1000, true);
        var fastPlain = new MinerRules.Tool(2, 9, true, 1000, false);
        var stick = new MinerRules.Tool(3, 1, false, -1, false);
        List<MinerRules.Tool> hotbar = List.of(plain, silk, fastPlain, stick);
        assertEquals(1, MinerRules.chooseTool(hotbar, 0, true, true, 8, true), "Prefers the Silk Touch pick even if slower");
        assertEquals(2, MinerRules.chooseTool(hotbar, 0, true, true, 8, false), "Avoids Silk Touch when not wanted");
        assertEquals(2, MinerRules.chooseTool(hotbar, 0, true, true, 8, null), "No preference: fastest");
        // Falls back when the preferred kind is missing or protected.
        assertEquals(2, MinerRules.chooseTool(List.of(plain, fastPlain), 0, true, true, 8, true));
        var wornSilk = new MinerRules.Tool(1, 8, true, 5, true);
        assertEquals(2, MinerRules.chooseTool(List.of(plain, wornSilk, fastPlain), 0, true, true, 8, true));
        assertEquals(1, MinerRules.chooseTool(List.of(silk, stick), 3, true, true, 8, false), "Silk is used when it is the only tool");
        assertEquals(-1, MinerRules.chooseTool(List.of(stick), 3, true, true, 8, true));
    }

    @Test void pickupReachMatchesVanilla() {
        // An item lying in a one-deep hole is out of reach from the rim, but reachable standing in the hole.
        var item = new net.minecraft.world.phys.AABB(8.0, 64.0, .75, 8.25, 64.25, 1.0);
        assertFalse(MinerRules.reachesItem(new net.minecraft.core.BlockPos(7, 65, 0), item));
        assertTrue(MinerRules.reachesItem(new net.minecraft.core.BlockPos(8, 64, 0), item));
        assertTrue(MinerRules.reachesItem(new net.minecraft.core.BlockPos(7, 64, 0), item));
        assertFalse(MinerRules.reachesItem(new net.minecraft.core.BlockPos(5, 64, 0), item));
        // Same level, the neighbouring cell: within the one-block reach.
        assertTrue(MinerRules.reachesItem(new net.minecraft.core.BlockPos(8, 64, 1), item));
    }

    @Test void smoothLookEasesAndCapsEachFrame() {
        // 60 fps, 360 deg/s cap: a big turn is capped, a small one eases.
        float[] big = SmoothLook.step(90, 0, 1 / 60.0, 360);
        assertEquals(6, big[0], 1e-3);
        float[] small = SmoothLook.step(4, 0, 1 / 60.0, 360);
        assertTrue(small[0] > 0 && small[0] < 4);
        // Diagonal turns keep their direction while capped.
        float[] diagonal = SmoothLook.step(60, 60, 1 / 60.0, 360);
        assertEquals(diagonal[0], diagonal[1], 1e-4);
        assertEquals(6, Math.hypot(diagonal[0], diagonal[1]), 1e-3);
        // Tiny errors snap so the aim actually arrives; converges over successive frames.
        assertArrayEquals(new float[] {.01f, -.02f}, SmoothLook.step(.01f, -.02f, 1 / 60.0, 360), 1e-6f);
        float error = 120;
        for (int frame = 0; frame < 120; frame++) error -= SmoothLook.step(error, 0, 1 / 60.0, 360)[0];
        assertEquals(0, error, .01);
    }
}
