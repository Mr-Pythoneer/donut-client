package dev.donut.client.automine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AutoMinerRulesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    /** A small block world: everything is {@code fill} unless set. */
    private static final class World implements MinerRules.View {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final BlockState fill;
        World(Block fill) { this.fill = fill.defaultBlockState(); }
        World set(BlockPos pos, Block block) { return set(pos, block.defaultBlockState()); }
        World set(BlockPos pos, BlockState state) { blocks.put(pos.immutable(), state); return this; }
        @Override public BlockState state(BlockPos pos) { return blocks.getOrDefault(pos, fill); }
    }

    private static final BlockPos ORE = new BlockPos(0, 10, 0);

    @Test void oresAndDeepslateVariantsShareAKind() {
        assertKind(OreKind.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        assertKind(OreKind.COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
        assertKind(OreKind.IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        assertKind(OreKind.GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
        assertKind(OreKind.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        assertKind(OreKind.LAPIS, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        assertKind(OreKind.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        assertKind(OreKind.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        assertKind(OreKind.NETHER_QUARTZ, Blocks.NETHER_QUARTZ_ORE);
        assertKind(OreKind.NETHER_GOLD, Blocks.NETHER_GOLD_ORE);
        assertKind(OreKind.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS);
        assertEquals(OreKind.REDSTONE, OreKind.of(Blocks.REDSTONE_ORE.defaultBlockState().setValue(BlockStateProperties.LIT, true)),
                "Lit redstone ore is still redstone ore");
    }

    private static void assertKind(OreKind kind, Block... blocks) {
        for (Block block : blocks) assertEquals(kind, OreKind.of(block.defaultBlockState()), block.toString());
    }

    @Test void nonOresAreIgnored() {
        for (Block block : new Block[] {Blocks.AIR, Blocks.STONE, Blocks.DEEPSLATE, Blocks.NETHERRACK, Blocks.DIAMOND_BLOCK,
                Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.GILDED_BLACKSTONE, Blocks.AMETHYST_CLUSTER, Blocks.LAVA})
            assertNull(OreKind.of(block.defaultBlockState()), block.toString());
        assertNull(OreKind.of(null));
        for (OreKind kind : OreKind.values()) assertSame(kind, OreKind.byId(kind.id));
        assertNull(OreKind.byId("future-ore"));
    }

    @Test void exposureNeedsAnOpenFace() {
        World world = new World(Blocks.STONE).set(ORE, Blocks.DIAMOND_ORE);
        assertFalse(MinerRules.exposed(world, ORE), "An ore surrounded by stone is buried");
        for (Direction direction : Direction.values()) {
            World open = new World(Blocks.STONE).set(ORE, Blocks.DIAMOND_ORE).set(ORE.relative(direction), Blocks.AIR);
            assertTrue(MinerRules.exposed(open, ORE), "Air on the " + direction + " face exposes it");
        }
        assertTrue(MinerRules.exposed(new World(Blocks.STONE).set(ORE.above(), Blocks.GLASS), ORE), "Visible through glass");
        assertTrue(MinerRules.exposed(new World(Blocks.STONE).set(ORE.east(), Blocks.CAVE_AIR), ORE));
        assertFalse(MinerRules.exposed(new World(Blocks.DEEPSLATE).set(ORE.north(), Blocks.IRON_ORE), ORE),
                "Another ore is not an open face");
    }

    @Test void fluidsNextToABlockAreHazards() {
        assertFalse(MinerRules.nearFluid(new World(Blocks.STONE), ORE));
        assertTrue(MinerRules.nearFluid(new World(Blocks.STONE).set(ORE.west(), Blocks.LAVA), ORE));
        assertTrue(MinerRules.nearFluid(new World(Blocks.STONE).set(ORE.below(), Blocks.WATER), ORE));
        assertTrue(MinerRules.nearFluid(new World(Blocks.STONE).set(ORE, Blocks.WATER), ORE));
        BlockState wetSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
        assertTrue(MinerRules.nearFluid(new World(Blocks.STONE).set(ORE.above(), wetSlab), ORE), "Waterlogged blocks hold water");
        assertFalse(MinerRules.nearFluid(new World(Blocks.STONE).set(ORE.above().east(), Blocks.LAVA), ORE),
                "Diagonal lava does not touch the block");
    }

    @Test void targetsAreOrderedNearestFirstWithStableTies() {
        Vec3 origin = new Vec3(0.5, 0.5, 0.5);
        BlockPos far = new BlockPos(10, 0, 0), near = new BlockPos(2, 0, 0), tieLow = new BlockPos(0, -3, 0),
                tieHigh = new BlockPos(0, 3, 0), tieWest = new BlockPos(-3, 0, 0), tieEast = new BlockPos(3, 0, 0);
        List<BlockPos> sorted = MinerRules.byDistance(List.of(far, tieLow, tieEast, near, tieWest, tieHigh), origin);
        assertEquals(List.of(near, tieHigh, tieWest, tieEast, tieLow, far), sorted);
        assertTrue(MinerRules.byDistance(List.of(), origin).isEmpty());
    }

    @Test void toolChoicePrefersFastestHarvestingUnprotectedTool() {
        var hand = new MinerRules.Tool(0, 1, false, -1);
        var stonePick = new MinerRules.Tool(1, 4, false, 100);
        var ironPick = new MinerRules.Tool(2, 6, true, 200);
        var wornDiamond = new MinerRules.Tool(3, 8, true, 5);
        var hotbar = List.of(hand, stonePick, ironPick, wornDiamond);
        assertEquals(2, MinerRules.chooseTool(hotbar, 0, true, true, 8), "The worn diamond pickaxe is protected");
        assertEquals(3, MinerRules.chooseTool(hotbar, 0, true, false, 8), "Without protection the fastest wins");
        assertEquals(3, MinerRules.chooseTool(hotbar, 0, true, true, 4), "Above the reserve it is usable");
        assertEquals(2, MinerRules.chooseTool(hotbar, 0, true, true, 5), "The reserve is inclusive");
        assertEquals(-1, MinerRules.chooseTool(List.of(hand, stonePick), 0, true, true, 8), "Nothing can harvest it");
        assertEquals(1, MinerRules.chooseTool(List.of(hand, stonePick), 0, false, true, 8), "No harvest tool needed");
        var unbreakable = new MinerRules.Tool(4, 6, true, -1);
        assertEquals(4, MinerRules.chooseTool(List.of(unbreakable), 0, true, true, 200), "Items without durability are never protected");
        assertEquals(4, MinerRules.chooseTool(List.of(ironPick, unbreakable), 4, true, true, 8), "Ties keep the held slot");
        assertEquals(2, MinerRules.chooseTool(List.of(ironPick, unbreakable), 0, true, true, 8), "Ties otherwise keep the first slot");
    }

    @Test void turningIsCappedAndTakesTheShortWay() {
        var step = MinerRules.turnToward(0, 0, 90, 60, 18);
        assertEquals(18, step.yaw(), 1e-4);
        assertEquals(18, step.pitch(), 1e-4);
        var wrap = MinerRules.turnToward(170, 0, -170, 0, 18);
        assertEquals(188, wrap.yaw(), 1e-4, "From 170 to -170 is 20 degrees through 180, not 340 back");
        var arrive = MinerRules.turnToward(10, 5, 15, 2, 18);
        assertEquals(15, arrive.yaw(), 1e-4);
        assertEquals(2, arrive.pitch(), 1e-4);
        var clamped = MinerRules.turnToward(0, 80, 0, 200, 45);
        assertEquals(90, clamped.pitch(), 1e-4, "Pitch never passes straight down");
        var noYaw = MinerRules.turnToward(33, 0, Float.NaN, 90, 18);
        assertEquals(33, noYaw.yaw(), 1e-4, "Looking straight up/down leaves yaw alone");
        assertTrue(MinerRules.aligned(359.5f, 0, -0.2f, 0.5f, 1));
        assertFalse(MinerRules.aligned(0, 0, 3, 0, 1));
    }

    @Test void tunnelStepsAreOneByTwoAndNeverDigUnderTheFeet() {
        BlockPos feet = new BlockPos(0, 10, 0);
        var level = MinerRules.tunnelStep(feet, new BlockPos(6, 11, 2));
        assertEquals(feet.east(), level.feet());
        assertEquals(List.of(feet.east().above(), feet.east()), level.clear(), "Head first, then feet");

        var up = MinerRules.tunnelStep(feet, new BlockPos(1, 16, -5));
        assertEquals(feet.north().above(), up.feet());
        assertEquals(List.of(feet.above(2), feet.north().above(2), feet.north().above()), up.clear(),
                "Jump headroom over the departure cell comes first");

        var down = MinerRules.tunnelStep(feet, new BlockPos(-4, 7, 0));
        assertEquals(feet.west().below(), down.feet());
        assertEquals(List.of(feet.west().above(), feet.west(), feet.west().below()), down.clear());

        for (BlockPos ore : new BlockPos[] {new BlockPos(0, 4, 0), new BlockPos(0, 20, 0), new BlockPos(3, 2, 0), new BlockPos(0, 9, 1)}) {
            var step = MinerRules.tunnelStep(feet, ore);
            assertFalse(step.clear().contains(feet.below()), "Never the block under the feet: " + ore);
            assertEquals(1, Math.abs(step.feet().getX() - feet.getX()) + Math.abs(step.feet().getZ() - feet.getZ()),
                    "Always one horizontal step: " + ore);
            for (BlockPos pos : step.clear()) assertFalse(MinerRules.standsOn(feet, pos), "Digs straight down at " + ore);
        }
        assertTrue(MinerRules.standsOn(feet, feet.below()));
        assertFalse(MinerRules.standsOn(feet, feet.below(2)));
        assertFalse(MinerRules.standsOn(feet, feet.below().east()));
    }

    @Test void tunnelHazardsSkipTheTarget() {
        BlockPos feet = new BlockPos(0, 10, 0);
        var step = MinerRules.tunnelStep(feet, new BlockPos(5, 10, 0));
        BlockPos next = step.feet();
        assertNull(MinerRules.stepHazard(new World(Blocks.STONE), step), "Solid stone is safe to tunnel");
        assertNotNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next.east(), Blocks.LAVA), step), "Lava behind the wall");
        assertNotNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next.above(2), Blocks.WATER), step), "Water overhead");
        assertNotNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next.below(2), Blocks.LAVA), step), "Lava under the floor");
        assertNotNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next, Blocks.BEDROCK), step), "Bedrock");
        assertNotNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next.above(), Blocks.CHEST), step), "Block entities stay");
        assertNull(MinerRules.stepHazard(new World(Blocks.STONE).set(next.above(2), Blocks.GRAVEL), step),
                "Gravel above the step is cleared as it falls in, not a reason to skip");
        World shallow = new World(Blocks.STONE).set(next.below(), Blocks.AIR).set(next.below(2), Blocks.AIR);
        assertEquals("no floor ahead", MinerRules.stepHazard(shallow, step));
        World deep = new World(Blocks.STONE);
        for (int i = 1; i <= 5; i++) deep.set(next.below(i), Blocks.AIR);
        assertEquals("a fall over 3 blocks", MinerRules.stepHazard(deep, step));
    }

    @Test void standingSpotsNeedAFloorAwayFromFluids() {
        BlockPos feet = new BlockPos(0, 10, 0);
        World room = new World(Blocks.STONE).set(feet, Blocks.AIR).set(feet.above(), Blocks.AIR);
        assertNull(MinerRules.standHazard(room, feet));
        assertNotNull(MinerRules.standHazard(new World(Blocks.STONE).set(feet, Blocks.WATER).set(feet.above(), Blocks.AIR), feet));
        assertNotNull(MinerRules.standHazard(new World(Blocks.STONE).set(feet, Blocks.AIR).set(feet.above(), Blocks.AIR)
                .set(feet.below(2), Blocks.LAVA), feet), "The floor borders lava");
        World ledge = new World(Blocks.AIR);
        ledge.set(feet.below(5), Blocks.STONE);
        assertEquals("a fall over 3 blocks", MinerRules.standHazard(ledge, feet));
        assertEquals(4, MinerRules.dropBelow(ledge, feet, 3), "Drops are counted to the limit plus one");
        World step = new World(Blocks.AIR).set(feet.below(3), Blocks.STONE);
        assertEquals(2, MinerRules.dropBelow(step, feet, 3));
    }

    @Test void onlyPlainSolidBlocksAreBroken() {
        assertTrue(MinerRules.breakable(Blocks.STONE.defaultBlockState()));
        assertTrue(MinerRules.breakable(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()));
        for (Block block : new Block[] {Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.BEDROCK, Blocks.CHEST, Blocks.SPAWNER,
                Blocks.GRAVEL, Blocks.SAND, Blocks.END_PORTAL_FRAME})
            assertFalse(MinerRules.breakable(block.defaultBlockState()), block.toString());
    }

    @Test void efficiencyAddsLevelSquaredPlusOneOnlyAboveBaseSpeedOne() {
        assertEquals(34, MinerRules.effectiveSpeed(8, 5), 1e-6, "Diamond pickaxe with Efficiency V");
        assertEquals(10, MinerRules.effectiveSpeed(8, 1), 1e-6);
        assertEquals(8, MinerRules.effectiveSpeed(8, 0), 1e-6);
        assertEquals(1, MinerRules.effectiveSpeed(1, 5), 1e-6, "Efficiency does nothing on blocks the tool isn't for");
        assertEquals(34, new MinerRules.Tool(0, 8, true, 100, false, 5, 0).effectiveSpeed(), 1e-6);
        // The old constructors mean "no enchantments".
        var plain = new MinerRules.Tool(2, 6, true, 50);
        assertEquals(0, plain.efficiency());
        assertEquals(0, plain.fortune());
        assertFalse(plain.silk());
    }

    @Test void toolChoiceCountsEfficiency() {
        var diamond = new MinerRules.Tool(0, 8, true, 1000);
        var fastIron = new MinerRules.Tool(1, 6, true, 200, false, 5, 0); // 6 + 26 = 32
        var stoneE1 = new MinerRules.Tool(2, 4, true, 100, false, 1, 0); // 4 + 2 = 6
        assertEquals(1, MinerRules.chooseTool(List.of(diamond, fastIron, stoneE1), 0, true, true, 8),
                "Efficiency V iron beats a plain diamond pickaxe");
        assertEquals(0, MinerRules.chooseTool(List.of(diamond, stoneE1), 2, true, true, 8));
        var tiedEfficiency = new MinerRules.Tool(3, 7, true, 1000, false, 1, 0); // 7 + 2 = 9
        var plainNine = new MinerRules.Tool(4, 9, true, 1000);
        assertEquals(4, MinerRules.chooseTool(List.of(tiedEfficiency, plainNine), 4, true, true, 8), "Equal effective speed keeps the held slot");
        assertEquals(3, MinerRules.chooseTool(List.of(tiedEfficiency, plainNine), 0, true, true, 8), "Otherwise the first slot");
        // Efficiency on a tool that can't harvest the block still doesn't qualify it.
        var shovel = new MinerRules.Tool(5, 1, false, 500, false, 5, 0);
        assertEquals(0, MinerRules.chooseTool(List.of(shovel, diamond), 5, true, true, 8));
    }

    @Test void fortuneComesAfterSilkTouchAndBeforeSpeed() {
        var plain = new MinerRules.Tool(0, 8, true, 1000);
        var silk = new MinerRules.Tool(1, 8, true, 1000, true, 0, 0);
        var fortune1 = new MinerRules.Tool(2, 8, true, 1000, false, 5, 1);
        var fortune3 = new MinerRules.Tool(3, 6, true, 1000, false, 0, 3);
        var fastest = new MinerRules.Tool(4, 9, true, 1000, false, 5, 0);
        List<MinerRules.Tool> hotbar = List.of(plain, silk, fortune1, fortune3, fastest);
        assertEquals(3, MinerRules.chooseTool(hotbar, 0, true, true, 8, false, true), "Most Fortune wins over speed");
        assertEquals(4, MinerRules.chooseTool(hotbar, 0, true, true, 8, false, false), "Prefer Fortune off: fastest");
        assertEquals(1, MinerRules.chooseTool(hotbar, 0, true, true, 8, true, true), "Silk Touch wanted and present comes first");
        assertEquals(3, MinerRules.chooseTool(List.of(plain, fortune1, fortune3, fastest), 0, true, true, 8, true, true),
                "Silk Touch wanted but missing: the fallback still prefers Fortune");
        assertEquals(4, MinerRules.chooseTool(hotbar, 0, true, true, 8, null, false), "No preference at all: fastest (ancient debris, tunnel blocks)");
        assertEquals(4, MinerRules.chooseTool(List.of(plain, fastest), 0, true, true, 8, false, true), "No Fortune tool: fastest");
        // Same Fortune level: the faster one, then the held slot.
        var fortune3Fast = new MinerRules.Tool(5, 8, true, 1000, false, 3, 3);
        assertEquals(5, MinerRules.chooseTool(List.of(fortune3, fortune3Fast), 3, true, true, 8, false, true));
        var fortune3Twin = new MinerRules.Tool(6, 6, true, 1000, false, 0, 3);
        assertEquals(6, MinerRules.chooseTool(List.of(fortune3, fortune3Twin), 6, true, true, 8, false, true));
        // A protected or non-harvesting Fortune tool doesn't count.
        var wornFortune = new MinerRules.Tool(7, 8, true, 3, false, 0, 3);
        var woodFortune = new MinerRules.Tool(8, 2, false, 50, false, 0, 3);
        assertEquals(4, MinerRules.chooseTool(List.of(wornFortune, woodFortune, fastest), 0, true, true, 8, false, true));
        assertEquals(7, MinerRules.chooseTool(List.of(wornFortune, woodFortune, fastest), 0, true, false, 8, false, true),
                "Protection off: the worn Fortune pickaxe is usable");
        // The six-argument overload is the same rule without Fortune.
        for (Boolean preference : new Boolean[] {true, false, null})
            assertEquals(MinerRules.chooseTool(hotbar, 0, true, true, 8, preference, false), MinerRules.chooseTool(hotbar, 0, true, true, 8, preference));
    }

    @Test void fortuneMattersForEveryOreButAncientDebris() {
        for (OreKind kind : OreKind.values()) assertEquals(kind != OreKind.ANCIENT_DEBRIS, kind.fortuneDrops, kind.id);
    }

    @Test void looseBlocksAreGravelSandAndConcretePowder() {
        for (Block block : new Block[] {Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND, Blocks.WHITE_CONCRETE_POWDER, Blocks.BLACK_CONCRETE_POWDER})
            assertTrue(MinerRules.loose(block.defaultBlockState()), block.toString());
        for (Block block : new Block[] {Blocks.STONE, Blocks.AIR, Blocks.ANVIL, Blocks.DRAGON_EGG, Blocks.POINTED_DRIPSTONE,
                Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.SANDSTONE, Blocks.SCAFFOLDING})
            assertFalse(MinerRules.loose(block.defaultBlockState()), block.toString());
        World world = new World(Blocks.STONE);
        for (int i = 1; i <= 4; i++) world.set(ORE.above(i), i % 2 == 0 ? Blocks.SAND : Blocks.GRAVEL);
        assertEquals(4, MinerRules.looseAbove(world, ORE, 12));
        assertEquals(3, MinerRules.looseAbove(world, ORE, 2), "Counted up to the limit plus one");
        assertEquals(0, MinerRules.looseAbove(world, ORE.above(4), 12));
        world.set(ORE.above(2), Blocks.STONE);
        assertEquals(1, MinerRules.looseAbove(world, ORE, 12), "Only the stack directly on top falls");
    }

    @Test void gravelOverATunnelStepIsClearedUnlessUnsafe() {
        BlockPos feet = new BlockPos(0, 10, 0);
        var step = MinerRules.tunnelStep(feet, new BlockPos(5, 10, 0));
        BlockPos head = step.feet().above();
        World column = new World(Blocks.STONE);
        for (int i = 1; i <= 3; i++) column.set(head.above(i), Blocks.GRAVEL);
        assertNull(MinerRules.stepHazard(column, step, feet), "A short gravel column is fine");
        assertNull(MinerRules.stepHazard(new World(Blocks.STONE).set(head, Blocks.GRAVEL).set(step.feet(), Blocks.SAND), step, feet),
                "Gravel and sand in the step itself are broken");
        // Fluid next to any falling block.
        World wet = new World(Blocks.STONE);
        for (int i = 1; i <= 3; i++) wet.set(head.above(i), Blocks.GRAVEL);
        wet.set(head.above(3).north(), Blocks.WATER);
        assertEquals("lava or water by the gravel", MinerRules.stepHazard(wet, step, feet));
        assertNull(MinerRules.stepHazard(wet.set(head.above(3), Blocks.STONE).set(head.above(3).north(), Blocks.STONE)
                .set(head.above(4).north(), Blocks.LAVA), step, feet), "Lava beside stone above the stack is not touching it");
        // Too tall a stack, exactly at the limit, and another kind of falling block on top.
        World tall = new World(Blocks.STONE);
        for (int i = 1; i <= MinerRules.MAX_FALLEN + 1; i++) tall.set(head.above(i), Blocks.SAND);
        assertEquals("too much gravel or sand overhead", MinerRules.stepHazard(tall, step, feet));
        tall.set(head.above(MinerRules.MAX_FALLEN + 1), Blocks.STONE);
        assertNull(MinerRules.stepHazard(tall, step, feet), "Exactly the limit is still cleared");
        World anvil = new World(Blocks.STONE).set(head.above(), Blocks.GRAVEL).set(head.above(2), Blocks.ANVIL);
        assertEquals("falling blocks overhead", MinerRules.stepHazard(anvil, step, feet));
        assertEquals("falling blocks overhead", MinerRules.stepHazard(new World(Blocks.STONE).set(head.above(), Blocks.POINTED_DRIPSTONE), step, feet));
        assertEquals("an unbreakable block ahead", MinerRules.stepHazard(new World(Blocks.STONE).set(head, Blocks.SUSPICIOUS_GRAVEL), step, feet));
    }

    @Test void gravelNeverFallsIntoThePlayersColumn() {
        BlockPos feet = new BlockPos(0, 10, 0);
        var up = MinerRules.tunnelStep(feet, new BlockPos(5, 15, 0)); // Clears the jump headroom over the player first.
        assertEquals(feet.above(2), up.clear().getFirst());
        World over = new World(Blocks.STONE).set(feet.above(3), Blocks.GRAVEL);
        assertEquals("gravel or sand would fall on you", MinerRules.stepHazard(over, up, feet));
        assertNull(MinerRules.stepHazard(over, up), "Without the player's position only the block rules apply");
        assertEquals("gravel or sand would fall on you", MinerRules.stepHazard(new World(Blocks.STONE).set(feet.above(2), Blocks.SAND), up, feet),
                "Sand in the headroom cell itself would drop onto the player");
        World side = new World(Blocks.STONE).set(up.feet().above(2), Blocks.GRAVEL);
        assertNull(MinerRules.stepHazard(side, up, feet), "Gravel over the next cell falls beside the player, not on them");
        // Both columns of a rising step count toward one limit.
        World both = new World(Blocks.STONE);
        for (int i = 1; i <= 6; i++) both.set(feet.above(2 + i), Blocks.GRAVEL);
        for (int i = 1; i <= 7; i++) both.set(up.feet().above(1 + i), Blocks.GRAVEL);
        assertEquals("too much gravel or sand overhead", MinerRules.stepHazard(both, up));
        // Player box against a column.
        var box = new net.minecraft.world.phys.AABB(0.7, 10, 0.2, 1.3, 11.8, 0.8);
        assertTrue(MinerRules.inColumn(box, new BlockPos(1, 12, 0)));
        assertTrue(MinerRules.inColumn(box, new BlockPos(0, 40, 0)));
        assertFalse(MinerRules.inColumn(box, new BlockPos(2, 11, 0)));
        assertFalse(MinerRules.inColumn(box, new BlockPos(1, 11, 1)));
    }

    @Test void looseBlocksOverOpenCellsAreUnsettled() {
        BlockPos feet = new BlockPos(0, 10, 0);
        var step = MinerRules.tunnelStep(feet, new BlockPos(5, 10, 0));
        BlockPos head = step.feet().above();
        assertFalse(MinerRules.unsettled(new World(Blocks.STONE).set(head.above(), Blocks.GRAVEL), step), "Still resting on the stone");
        World falling = new World(Blocks.STONE).set(head, Blocks.AIR).set(head.above(), Blocks.GRAVEL);
        assertTrue(MinerRules.unsettled(falling, step), "Gravel over a cleared cell is about to fall");
        assertTrue(MinerRules.unsettled(new World(Blocks.STONE).set(head, Blocks.SAND).set(step.feet(), Blocks.AIR), step),
                "Sand in the head cell over a cleared feet cell");
        World landed = new World(Blocks.STONE).set(head, Blocks.GRAVEL).set(step.feet(), Blocks.GRAVEL).set(head.above(), Blocks.AIR);
        assertFalse(MinerRules.unsettled(landed, step), "Landed blocks are settled; they are simply mined");
        assertFalse(MinerRules.unsettled(new World(Blocks.AIR).set(head.above(), Blocks.STONE), step));
    }

    @Test void standingSpotsAvoidLooseBlocksOverhead() {
        BlockPos feet = new BlockPos(0, 10, 0);
        World room = new World(Blocks.STONE).set(feet, Blocks.AIR).set(feet.above(), Blocks.AIR);
        assertNull(MinerRules.standHazard(room, feet));
        assertEquals("gravel or sand overhead", MinerRules.standHazard(room.set(feet.above(2), Blocks.GRAVEL), feet));
        assertNull(MinerRules.standHazard(room.set(feet.above(2), Blocks.STONE).set(feet.above(3), Blocks.SAND), feet),
                "Sand resting on the ceiling stays put");
    }
}
