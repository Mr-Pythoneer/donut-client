package dev.donut.client.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Developer tool, not a test: builds a "Donut Testing Grounds" superflat world with an area for each module and
 * copies it to {@code run/saves/donut-testing-grounds} for {@code ./gradlew runClient}. Only runs with
 * {@code -PtestingGroundsOnly=true}; it is not part of the normal game-test registry or the release jar.
 */
public final class TestingGroundsBuilder implements FabricClientGameTest {
    public static final String FOLDER = "donut-testing-grounds";
    private static final int Y = -60; // First air layer above consistent-settings superflat ground.

    @Override public void runTest(ClientGameTestContext context) {
        Path target = Path.of(System.getProperty("donut.testingGrounds.target", "../../../run/saves/" + FOLDER)).toAbsolutePath().normalize();
        try (var world = context.worldBuilder().adjustSettings(creator -> {
            creator.setName("Donut Testing Grounds");
            creator.setAllowCommands(true);
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setDifficulty(Difficulty.PEACEFUL);
        }).create()) {
            TestServerContext server = world.getServer();
            run(server, "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                    "gamerule spawn_monsters false", "gamerule keep_inventory true", "gamerule spawn_phantoms false",
                    "time set noon", "weather clear", "setworldspawn 0 " + Y + " 0");
            // Keep every area loaded while building; fill/setblock fail in unloaded chunks.
            run(server, "forceload add -64 -176 80 64");
            context.waitTicks(40);
            hub(server);
            storage(server);
            freecam(server);
            durability(server);
            ores(server);
            portalAndZoom(server);
            run(server, "forceload remove all", "tp @a 0.5 " + Y + " 2.5 180 15", "gamemode creative @a");
            context.waitTicks(20);
            // The integrated server has no save-all command: flush every chunk and the player data directly.
            server.runOnServer(mc -> mc.saveEverything(false, true, true));
            context.waitTicks(10);
            Path source = server.computeOnServer(mc -> mc.getWorldPath(LevelResource.ROOT)).toAbsolutePath().normalize();
            copy(source, target);
            System.out.println("[TestingGrounds] Saved world to " + target);
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static void hub(TestServerContext s) {
        run(s, fill(-7, -1, -7, 7, -1, 7, "polished_andesite"), fill(-1, -1, -1, 1, -1, 1, "sea_lantern"));
        sign(s, 0, 4, "Donut Testing Grounds", "Right Shift: menu", "East: Storage ESP", "West: Freecam");
        sign(s, 2, 4, "North: Nether portal", "and Zoom tower", "South: ore field", "Chest: worn gear");
    }

    /** One of every storage kind, a double and trapped double chest, minecarts, a hut and a buried stash. */
    private static void storage(TestServerContext s) {
        run(s, fill(18, -1, -12, 44, -1, 12, "smooth_stone"));
        sign(s, 16, 0, "Storage ESP", "every kind, doubles,", "a hut and a buried", "stash under X=40");
        String[] blocks = {"20 0 -8 chest", "22 0 -8 chest[facing=south,type=left]", "21 0 -8 chest[facing=south,type=right]",
                "24 0 -8 trapped_chest", "26 0 -8 trapped_chest[facing=south,type=left]", "25 0 -8 trapped_chest[facing=south,type=right]",
                "28 0 -8 barrel", "30 0 -8 purple_shulker_box", "32 0 -8 ender_chest", "34 0 -8 hopper", "36 0 -8 dropper",
                "38 0 -8 dispenser", "20 0 -4 furnace", "22 0 -4 blast_furnace", "24 0 -4 smoker", "26 0 -4 brewing_stand",
                "28 0 -4 crafter", "30 0 -4 decorated_pot", "32 0 -4 chiseled_bookshelf", "34 0 -4 copper_chest",
                "36 0 -4 crafting_table", "38 0 -4 bookshelf"};
        for (String block : blocks) {
            String[] p = block.split(" ");
            run(s, "setblock " + p[0] + " " + (Y + Integer.parseInt(p[1])) + " " + p[2] + " minecraft:" + p[3]);
        }
        run(s, "summon minecraft:chest_minecart 30.5 " + Y + " 0.5", "summon minecraft:hopper_minecart 32.5 " + Y + " 0.5",
                "summon minecraft:oak_chest_boat 35.5 " + Y + " 0.5");
        // A closed stone hut with storage inside: only visible through walls.
        run(s, fill(24, 0, 4, 32, 4, 10, "stone_bricks"), fill(25, 0, 5, 31, 3, 9, "air"),
                "setblock 26 " + Y + " 6 minecraft:chest", "setblock 28 " + Y + " 6 minecraft:barrel", "setblock 30 " + Y + " 8 minecraft:shulker_box");
        // Buried stash in the dirt layer below the grass.
        run(s, "setblock 40 " + (Y - 3) + " 0 minecraft:chest[facing=north,type=left]", "setblock 41 " + (Y - 3) + " 0 minecraft:chest[facing=north,type=right]",
                "setblock 40 " + (Y - 3) + " 2 minecraft:barrel", "setblock 40 " + (Y - 1) + " 1 minecraft:red_wool");
    }

    /** A tall hollow tower and a sealed room to explore with Freecam. */
    private static void freecam(TestServerContext s) {
        run(s, fill(-44, -1, -12, -18, -1, 12, "smooth_stone"));
        sign(s, -16, 0, "Freecam (F6)", "fly into the sealed", "room and up the tower", "scroll = speed");
        run(s, fill(-40, 0, -6, -34, 40, 0, "quartz_block"), fill(-39, 0, -5, -35, 39, -1, "air"),
                "setblock -37 " + (Y + 40) + " -3 minecraft:gold_block");
        run(s, fill(-30, 0, 4, -24, 5, 10, "deepslate_bricks"), fill(-29, 0, 5, -25, 4, 9, "air"),
                "setblock -27 " + Y + " 7 minecraft:diamond_block", "setblock -27 " + (Y + 1) + " 7 minecraft:beacon");
    }

    /** Worn tools and armor: in your inventory and in a chest beside the hub. */
    private static void durability(TestServerContext s) {
        String pick = "minecraft:diamond_pickaxe[minecraft:damage=1530]";
        String sword = "minecraft:iron_sword[minecraft:damage=240]";
        run(s, "item replace entity @a hotbar.0 with " + pick, "item replace entity @a hotbar.1 with " + sword,
                "item replace entity @a hotbar.2 with minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]",
                "item replace entity @a hotbar.3 with minecraft:iron_pickaxe",
                "item replace entity @a armor.head with minecraft:iron_helmet[minecraft:damage=158]",
                "item replace entity @a armor.feet with minecraft:diamond_boots[minecraft:damage=420]",
                "item replace entity @a weapon.offhand with minecraft:shield[minecraft:damage=330]");
        run(s, "setblock 0 " + Y + " 8 minecraft:chest",
                "item replace block 0 " + Y + " 8 container.0 with " + pick,
                "item replace block 0 " + Y + " 8 container.1 with minecraft:netherite_chestplate[minecraft:damage=580]",
                "item replace block 0 " + Y + " 8 container.2 with minecraft:elytra[minecraft:damage=420]",
                "item replace block 0 " + Y + " 8 container.3 with minecraft:anvil",
                "item replace block 0 " + Y + " 8 container.4 with minecraft:experience_bottle 64");
    }

    /** Exposed and buried ores, plus lava and water hazards: for BlockESP-style checks now and Auto Miner later. */
    private static void ores(TestServerContext s) {
        run(s, fill(-12, -1, 18, 12, -1, 44, "smooth_stone"));
        sign(s, 0, 16, "Ore field", "exposed + buried ores,", "lava and water", "hazards");
        run(s, fill(-10, 0, 22, 10, 6, 40, "stone"));
        String[] ores = {"diamond_ore", "iron_ore", "gold_ore", "coal_ore", "redstone_ore", "lapis_ore", "emerald_ore",
                "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_coal_ore", "ancient_debris"};
        for (int i = 0; i < ores.length; i++) {
            int x = -9 + (i * 7) % 19, z = 23 + (i * 5) % 17, y = Y + 1 + (i % 4);
            run(s, "setblock " + x + " " + y + " " + z + " minecraft:" + ores[i]); // Buried.
            run(s, "setblock " + (x + 1) + " " + (Y + 7) + " " + z + " minecraft:" + ores[i]); // Exposed on top.
        }
        run(s, fill(-4, 3, 30, 0, 3, 33, "lava"), fill(3, 3, 30, 6, 3, 33, "water"));
    }

    /** A lit Nether portal (dimension changes, linked coordinates) and a distant tower for Zoom. */
    private static void portalAndZoom(TestServerContext s) {
        run(s, fill(-3, 0, -20, 2, 4, -20, "obsidian"), fill(-2, 1, -20, 1, 3, -20, "nether_portal[axis=x]"));
        sign(s, 4, -18, "Nether portal", "linked coordinates,", "Freecam turns off", "on dimension change");
        sign(s, -5, -18, "Zoom (hold Z)", "look north: tower", "and gold block", "150 blocks away");
        run(s, fill(-3, 0, -150, 3, 30, -144, "red_concrete"), "setblock 0 " + (Y + 31) + " -147 minecraft:gold_block",
                fill(-1, 31, -148, 1, 31, -146, "glowstone"));
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + (Y + y1) + " " + z1 + " " + x2 + " " + (Y + y2) + " " + z2 + " minecraft:" + block;
    }

    private static void sign(TestServerContext s, int x, int z, String... lines) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4; i++) text.append(i == 0 ? "" : ",").append('"').append(i < lines.length ? lines[i] : "").append('"');
        run(s, "setblock " + x + " " + Y + " " + z + " minecraft:oak_sign[rotation=8]{front_text:{messages:[" + text + "]}}");
    }

    /** Commands that fail only log; a missing decoration must not stop the build. */
    private static void run(TestServerContext server, String... commands) {
        for (String command : commands) {
            try { server.runCommand(command); }
            catch (Throwable error) { System.out.println("[TestingGrounds] Command failed: " + command + " (" + error.getMessage() + ")"); }
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        if (Files.exists(target)) try (Stream<Path> old = Files.walk(target)) {
            for (Path path : old.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        try (Stream<Path> files = Files.walk(source)) {
            for (Path path : files.toList()) {
                if (path.getFileName().toString().equals("session.lock")) continue;
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
