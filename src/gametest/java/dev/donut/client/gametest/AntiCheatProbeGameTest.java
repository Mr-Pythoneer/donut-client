package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.autobuild.PlacementTransform;
import dev.donut.client.autobuild.Schematic;
import dev.donut.client.autoeat.AutoEatController;
import dev.donut.client.automine.AutoMinerController;
import dev.donut.client.autoreconnect.AutoReconnectController;
import dev.donut.client.blockesp.BlockEspController;
import dev.donut.client.blockesp.OreMarker;
import dev.donut.client.config.AutoMinerSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.SearchSettings;
import dev.donut.client.fly.FlyController;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.mixin.DisconnectedScreenAccessor;
import dev.donut.client.newchunks.NewChunksController;
import dev.donut.client.search.SearchScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developer tool, not a test: joins a local Paper server running anti-cheat plugins (tools/anticheat-server.sh) and
 * runs every module that talks to the server, one scenario at a time. Server commands go through the server
 * console (the player is never opped, so no anti-cheat bypass permission applies). Each scenario is bracketed by
 * "[probe] begin/end" lines in the server log so tools/anticheat-report.py can attribute alerts to it; the client's
 * own view (setbacks, kicks, what the module achieved) is written to probe-client.tsv next to the console file.
 */
public final class AntiCheatProbeGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("Donut/AntiCheatProbe");
    private static final Path DIR = Path.of(System.getenv().getOrDefault("DONUT_AC_DIR", "/Volumes/ VideoGames/DevTools/ac-testserver"));
    private static final Path CONSOLE = DIR.resolve("console.in"), REPORT = DIR.resolve("probe-client.tsv");
    private static final String ADDRESS = System.getenv().getOrDefault("DONUT_AC_ADDRESS", "127.0.0.1:25599");
    /** The arena: a cleared stone floor at y 119, standing height 120, far from spawn. */
    private static final int AX = 1000, AY = 120, AZ = 1000;
    /** Natural deep terrain for the ore-visibility scenarios (spectator, so movement checks do not apply). */
    private static final int UX = 300, UY = -30, UZ = -300;

    private static volatile int setbacks;
    private String name;

    public static void setback() { setbacks++; }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        try { Files.writeString(REPORT, "time\tscenario\tsetbacks\tkicked\tdetail\n"); } catch (IOException error) { throw new RuntimeException(error); }
        // The Blatant scenarios are the point of the probe: allow them on this (local, offline) test server only.
        context.runOnClient(client -> DonutClient.config().blatantGuard.allow(ADDRESS, true));
        connect(context);
        name = context.computeOnClient(client -> client.getUser().getName());
        LOGGER.info("Probe player is {}", name);
        // Peaceful except during Auto Eat (peaceful refills hunger). 1.21.11 renamed the gamerules, so send both spellings.
        console("difficulty peaceful", "time set noon", "worldborder set 60000000",
                "gamerule doDaylightCycle false", "gamerule advance_time false", "gamerule doWeatherCycle false", "gamerule advance_weather false",
                "gamerule doMobSpawning false", "gamerule spawn_mobs false", "gamerule keepInventory true", "gamerule keep_inventory true");
        buildArena(context);

        scenario(context, "baseline-walk-and-mine", this::baseline);
        scenario(context, "visual-modules", this::visuals);
        scenario(context, "freecam", this::freecam);
        scenario(context, "staff-detector", this::staffDetector);
        scenario(context, "auto-eat", this::autoEat);
        scenario(context, "auto-miner", this::autoMiner);
        scenario(context, "auto-builder", this::autoBuilder);
        scenario(context, "search-under-anti-xray", this::searchXray);
        scenario(context, "blockesp-seed-under-anti-xray", this::blockEspXray);
        scenario(context, "new-chunks", this::newChunks);
        scenario(context, "fly", this::fly);
        scenario(context, "auto-reconnect", this::autoReconnect);

        console("say [probe] done");
        context.runOnClient(client -> DonutClient.config().blatantGuard.allow(ADDRESS, false));
        context.runOnClient(client -> { if (client.getConnection() != null) client.getConnection().getConnection().disconnect(Component.literal("Probe finished")); });
        context.waitTicks(20);
        context.setScreen(TitleScreen::new);
        context.waitTicks(5);
    }

    // ---- scenarios -------------------------------------------------------------------------------------------

    private String baseline(ClientGameTestContext context) {
        arena(context);
        console("item replace entity " + name + " hotbar.0 with minecraft:diamond_pickaxe");
        context.waitTicks(10);
        var options = context.computeOnClient(client -> client.options);
        walk(context, options.keyUp, 40);
        walk(context, options.keyLeft, 20);
        walk(context, options.keyDown, 30);
        for (int i = 0; i < 3; i++) { context.getInput().pressKey(options.keyJump); context.waitTicks(14); }
        // Mine three blocks the normal way: aim, hold attack.
        int broken = 0;
        for (int i = 0; i < 3; i++) {
            BlockPos block = context.computeOnClient(client -> client.player.blockPosition().relative(client.player.getDirection(), 2));
            console(String.format("setblock %d %d %d minecraft:stone", block.getX(), block.getY(), block.getZ()));
            context.waitTicks(6);
            aim(context, Vec3.atCenterOf(block));
            context.getInput().holdKey(options.keyAttack);
            boolean done = until(context, client -> client.level.getBlockState(block).isAir(), 80);
            context.getInput().releaseKey(options.keyAttack);
            if (done) broken++;
            context.waitTicks(10);
        }
        return "walked, jumped, broke " + broken + "/3 stone by hand";
    }

    private String visuals(ClientGameTestContext context) {
        arena(context);
        ClientConfig config = DonutClient.config();
        context.runOnClient(client -> {
            config.fullbright.enabled = config.blockEsp.enabled = config.storageEsp.enabled = config.search.enabled = true;
            config.playerEsp.enabled = config.newChunks.enabled = config.stashFinder.enabled = config.hudWidgets.enabled = true;
            config.coordinates.enabled = config.waypoints.enabled = config.lightDebug.enabled = config.soundDetector.enabled = true;
            config.amethyst.enabled = config.chunkFinder.enabled = config.progressBar.enabled = config.nameHider.enabled = true;
            config.durabilityAlert.enabled = config.zoom.enabled = config.freelook.enabled = config.hotbar.enabled = true;
        });
        var options = context.computeOnClient(client -> client.options);
        walk(context, options.keyUp, 60);
        KeyMapping freelook = key(context, "key.donut.freelook");
        if (freelook != null) {
            context.getInput().holdKey(freelook);
            context.getInput().moveCursor(400, 0);
            walk(context, options.keyUp, 40);
            context.getInput().releaseKey(freelook);
        }
        walk(context, options.keyDown, 60);
        return "19 visual/HUD modules on while walking" + (freelook == null ? " (no freelook key)" : ", freelook held");
    }

    private String freecam(ClientGameTestContext context) {
        arena(context);
        FreecamController freecam = FreecamController.getInstance();
        Vec3 before = context.computeOnClient(client -> client.player.position());
        context.runOnClient(client -> freecam.setEnabled(true));
        var options = context.computeOnClient(client -> client.options);
        walk(context, options.keyUp, 60);
        context.getInput().holdKey(options.keyJump);
        context.waitTicks(30);
        context.getInput().releaseKey(options.keyJump);
        context.getInput().moveCursor(300, 100);
        context.waitTicks(40);
        Vec3 after = context.computeOnClient(client -> client.player.position());
        context.runOnClient(client -> freecam.setEnabled(false));
        return String.format("camera flew ~5 s; body moved %.2f blocks", before.distanceTo(after));
    }

    private String staffDetector(ClientGameTestContext context) {
        arena(context);
        context.runOnClient(client -> DonutClient.config().staffDetector.enabled = true);
        context.waitTicks(400);
        return "Staff Detector on for 20 s (sends /msg tab-complete requests)";
    }

    private String autoEat(ClientGameTestContext context) {
        arena(context);
        console("difficulty easy", "kill @e[type=!minecraft:player]");
        console("item replace entity " + name + " hotbar.3 with minecraft:cooked_beef 8",
                "effect give " + name + " minecraft:hunger 20 120 true");
        until(context, client -> client.player.getFoodData().getFoodLevel() <= 12, 600);
        console("effect clear " + name + " minecraft:hunger");
        AutoEatController eat = AutoEatController.get();
        int before = context.computeOnClient(client -> eat.eaten());
        context.runOnClient(client -> DonutClient.config().autoEat.enabled = true);
        boolean ate = until(context, client -> eat.eaten() > before && eat.eatingName() == null, 400);
        int food = context.computeOnClient(client -> client.player.getFoodData().getFoodLevel());
        console("difficulty peaceful");
        return (ate ? "ate " + (context.computeOnClient(client -> eat.eaten()) - before) : "did not eat") + "; food now " + food;
    }

    private String autoMiner(ClientGameTestContext context) {
        arena(context);
        String[] ores = {"diamond_ore", "iron_ore", "gold_ore", "deepslate_diamond_ore", "emerald_ore", "redstone_ore", "lapis_ore", "deepslate_iron_ore"};
        int[][] spots = {{6, 2}, {-5, 7}, {9, -6}, {-9, -4}, {2, 12}, {-12, 10}, {13, 9}, {-3, -13}};
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < ores.length; i++) {
            BlockPos pos = new BlockPos(AX + spots[i][0], AY - 1, AZ + spots[i][1]);
            placed.add(pos);
            console(String.format("setblock %d %d %d minecraft:%s", pos.getX(), pos.getY(), pos.getZ(), ores[i]));
        }
        console("item replace entity " + name + " hotbar.0 with minecraft:diamond_pickaxe",
                "item replace entity " + name + " hotbar.1 with minecraft:iron_pickaxe");
        context.waitTicks(20);
        AutoMinerController miner = AutoMinerController.get();
        var settings = DonutClient.config().autoMiner;
        context.runOnClient(client -> {
            settings.ores = new LinkedHashMap<>(AutoMinerSettings.defaultOres());
            settings.includeBuried = false;
            settings.range = 24;
            settings.enabled = true;
        });
        int wanted = context.computeOnClient(client -> (int) placed.stream().filter(pos -> settings.wants(client.level.getBlockState(pos))).count());
        until(context, client -> miner.mined() >= wanted || !settings.enabled, 2400);
        int mined = context.computeOnClient(client -> miner.mined());
        String state = context.computeOnClient(client -> miner.state() + (miner.pauseReason().isEmpty() ? "" : " (" + miner.pauseReason() + ")"));
        context.runOnClient(client -> settings.enabled = false);
        int left = 0;
        for (BlockPos pos : placed) if (!context.computeOnClient(client -> client.level.getBlockState(pos).isAir())) left++;
        return "mined " + mined + " of the " + wanted + " ores it targets (" + (ores.length - wanted) + " off by default), " + left + " still standing, ended " + state;
    }

    private String autoBuilder(ClientGameTestContext context) {
        arena(context);
        console("item replace entity " + name + " hotbar.2 with minecraft:cobblestone 64");
        context.waitTicks(10);
        AutoBuilderController builder = AutoBuilderController.get();
        List<Schematic.Block> blocks = new ArrayList<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 3; y++) blocks.add(new Schematic.Block(new BlockPos(x, y, 0), Blocks.COBBLESTONE.defaultBlockState(), null));
        context.runOnClient(client -> {
            builder.setSchematic(new Schematic("probe wall", blocks, new BlockPos(5, 3, 1), 1, List.of()));
            builder.setTransform(new PlacementTransform(new BlockPos(AX - 2, AY, AZ - 4), 0, false));
            builder.start();
        });
        until(context, client -> !builder.isRunning(), 1200);
        String result = context.computeOnClient(client -> builder.status() + ", " + builder.confirmedBlocks() + "/" + blocks.size() + " placed" + (builder.reason() == null ? "" : " (" + builder.reason() + ")"));
        context.runOnClient(client -> builder.cancel());
        return result;
    }

    private String searchXray(ClientGameTestContext context) {
        underground(context);
        var settings = DonutClient.config().search;
        context.runOnClient(client -> {
            settings.blocks = new ArrayList<>(List.of(new SearchSettings.Entry("minecraft:diamond_ore", 0x5EE6E0),
                    new SearchSettings.Entry("minecraft:deepslate_diamond_ore", 0x5EE6E0)));
            settings.mergeAdjacent = false;
            settings.range = 64;
            settings.maxResults = 4096;
            settings.enabled = true;
        });
        SearchScanner scanner = SearchScanner.get();
        context.waitTicks(40);
        until(context, client -> scanner.pendingChunks() == 0 && scanner.snapshot().total() > 0, 400);
        context.waitTicks(20);
        List<BlockPos> found = context.computeOnClient(client -> scanner.snapshot().targets().stream().map(SearchScanner.Target::nearest).toList());
        int total = context.computeOnClient(client -> scanner.snapshot().total());
        List<BlockPos> sample = found.subList(0, Math.min(40, found.size()));
        int real = verify(context, "search", sample, pos -> List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
        return "client saw " + total + " diamond ores within 64; server check of " + sample.size() + " nearest: " + real + " real";
    }

    private String blockEspXray(ClientGameTestContext context) {
        underground(context);
        var settings = DonutClient.config().blockEsp;
        BlockEspController esp = BlockEspController.get();
        context.runOnClient(client -> {
            settings.seed = System.getenv().getOrDefault("DONUT_AC_SEED", dev.donut.client.config.BlockEspSettings.DEFAULT_SEED);
            settings.diamond = true;
            settings.stoneVariants = settings.deepslateVariants = true;
            settings.minConfidence = 0;
            settings.enabled = true;
            esp.rescan();
        });
        until(context, client -> esp.count(OreMarker.Kind.DIAMOND) > 0, 600);
        context.waitTicks(60);
        Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
        List<OreMarker> markers = context.computeOnClient(client -> esp.snapshot().stream()
                .filter(marker -> marker.kind() == OreMarker.Kind.DIAMOND)
                .sorted((a, b) -> Double.compare(a.pos().distToCenterSqr(eye), b.pos().distToCenterSqr(eye))).limit(40).toList());
        Map<String, Integer> states = new LinkedHashMap<>();
        for (OreMarker marker : markers) context.runOnClient(client -> states.merge(String.valueOf(esp.cachedState(marker.pos())), 1, Integer::sum));
        int real = verify(context, "blockesp", markers.stream().map(OreMarker::pos).toList(),
                pos -> List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
        return "seed prediction: " + markers.size() + " nearest diamonds checked, " + real + " real on the server; client confidence " + states;
    }

    private String newChunks(ClientGameTestContext context) {
        console("gamemode spectator " + name);
        NewChunksController chunks = NewChunksController.get();
        context.runOnClient(client -> { DonutClient.config().newChunks.enabled = true; chunks.clear(); });
        console(String.format("tp %s %d 120 %d", name, 0, 0));
        context.waitTicks(200);
        String old = context.computeOnClient(client -> "spawn (generated at startup): new=" + chunks.tracker().newCount() + " old=" + chunks.tracker().oldCount());
        context.runOnClient(client -> chunks.clear());
        console(String.format("tp %s %d 120 %d", name, 40000, 40000));
        context.waitTicks(300);
        String fresh = context.computeOnClient(client -> "never-visited x40000: new=" + chunks.tracker().newCount() + " old=" + chunks.tracker().oldCount());
        return old + "; " + fresh;
    }

    private String fly(ClientGameTestContext context) {
        arena(context);
        FlyController fly = FlyController.getInstance();
        context.runOnClient(client -> fly.setEnabled(true));
        var options = context.computeOnClient(client -> client.options);
        context.getInput().pressKey(options.keyJump);
        context.waitTicks(4);
        context.getInput().pressKey(options.keyJump);
        context.getInput().holdKey(options.keyJump);
        context.waitTicks(20);
        context.getInput().releaseKey(options.keyJump);
        double peak = context.computeOnClient(client -> client.player.getY());
        walk(context, options.keyUp, 80);
        context.waitTicks(40);
        double end = context.computeOnClient(client -> client.player.getY());
        boolean flying = context.computeOnClient(client -> client.player.getAbilities().flying);
        context.runOnClient(client -> fly.setEnabled(false));
        context.waitTicks(80);
        return String.format("rose to y %.1f, y %.1f after flying forward (flying=%s)", peak, end, flying);
    }

    private String autoReconnect(ClientGameTestContext context) {
        arena(context);
        AutoReconnectController reconnect = AutoReconnectController.get();
        context.runOnClient(client -> DonutClient.config().autoReconnect.enabled = true);
        console("kick " + name + " Probe kick");
        boolean screen = until(context, client -> client.screen instanceof DisconnectedScreen, 200);
        String status = context.computeOnClient(client -> reconnect.status());
        boolean back = until(context, client -> client.level != null && client.player != null && client.screen == null, 1200);
        context.runOnClient(client -> DonutClient.config().autoReconnect.enabled = false);
        return "kicked=" + screen + " (" + status + "), rejoined by itself=" + back;
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    private void buildArena(ClientGameTestContext context) {
        console(String.format("forceload add %d %d %d %d", AX - 32, AZ - 32, AX + 32, AZ + 32));
        context.waitTicks(40);
        for (int y = AY; y <= AY + 40; y += 8)
            console(String.format("fill %d %d %d %d %d %d minecraft:air", AX - 30, y, AZ - 30, AX + 30, Math.min(y + 7, AY + 40), AZ + 30));
        console(String.format("fill %d %d %d %d %d %d minecraft:stone", AX - 30, AY - 3, AZ - 30, AX + 30, AY - 1, AZ + 30));
        context.waitTicks(40);
    }

    private void arena(ClientGameTestContext context) {
        console("gamemode survival " + name, "clear " + name, "effect clear " + name, "effect give " + name + " minecraft:instant_health 1 10 true",
                "effect give " + name + " minecraft:saturation 1 20 true",
                String.format("fill %d %d %d %d %d %d minecraft:stone", AX - 30, AY - 1, AZ - 30, AX + 30, AY - 1, AZ + 30),
                String.format("fill %d %d %d %d %d %d minecraft:air", AX - 30, AY, AZ - 30, AX + 30, AY + 7, AZ + 30),
                String.format("tp %s %d.5 %d %d.5 0 0", name, AX, AY, AZ));
        context.waitTicks(10);
        until(context, client -> client.player != null && client.player.onGround() && Math.abs(client.player.getX() - AX - .5) < 1, 200);
        context.waitTicks(30);
        setbacks = 0; // the fixture's own teleport is not an anti-cheat setback
    }

    private void underground(ClientGameTestContext context) {
        console("gamemode spectator " + name, String.format("tp %s %d %d %d 0 30", name, UX, UY, UZ));
        context.waitTicks(80);
        until(context, client -> client.player != null && Math.abs(client.player.getY() - UY) < 1, 200);
        context.waitTicks(60);
        setbacks = 0;
    }

    /** Asks the server, through the console, which of these positions really hold one of the given blocks. */
    private int verify(ClientGameTestContext context, String tag, List<BlockPos> positions, java.util.function.Function<BlockPos, List<String>> blocks) {
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            console(String.format("say [probe] check %s %d %d %d %d", tag, i, pos.getX(), pos.getY(), pos.getZ()));
            for (String id : blocks.apply(pos))
                console(String.format("execute if block %d %d %d %s run say [probe] real %s %d", pos.getX(), pos.getY(), pos.getZ(), id, tag, i));
        }
        console("say [probe] checks-done " + tag);
        // The console reply is only in the server log, so wait for the log to show it and count there.
        Path log = DIR.resolve("logs/latest.log");
        int real = -1;
        for (int tries = 0; tries < 200 && real < 0; tries++) {
            context.waitTicks(5);
            try {
                String text = Files.readString(log, StandardCharsets.UTF_8);
                int start = text.lastIndexOf("[probe] check " + tag + " 0 ");
                int end = text.lastIndexOf("[probe] checks-done " + tag);
                if (start >= 0 && end > start) real = (int) text.substring(start, end).lines().filter(line -> line.contains("[probe] real " + tag + " ")).count();
            } catch (IOException ignored) { }
        }
        return real;
    }

    // ---- plumbing ---------------------------------------------------------------------------------------------

    private void scenario(ClientGameTestContext context, String id, java.util.function.Function<ClientGameTestContext, String> body) {
        ensureConnected(context);
        // A previous scenario may have left the player dead or a menu open; neither belongs to the next one.
        context.runOnClient(client -> {
            if (client.player != null && client.player.isDeadOrDying()) client.player.respawn();
            if (client.screen != null) client.setScreen(null);
        });
        console("kill @e[type=!minecraft:player]");
        context.waitTicks(20);
        resetModules(context);
        console("say [probe] begin " + id);
        context.waitTicks(10);
        setbacks = 0;
        String detail;
        try {
            detail = body.apply(context);
        } catch (Throwable error) {
            LOGGER.warn("Scenario {} failed", id, error);
            detail = "ERROR " + error;
        }
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keyAttack);
        context.getInput().releaseKey(options -> options.keyJump);
        String kicked = context.computeOnClient(AntiCheatProbeGameTest::kickReason);
        int corrections = setbacks;
        console("say [probe] end " + id);
        resetModules(context);
        String line = String.join("\t", LocalTime.now().toString(), id, Integer.toString(corrections), kicked == null ? "" : kicked, detail);
        LOGGER.info("[probe] {}", line);
        try { Files.writeString(REPORT, line.replace('\n', ' ') + "\n", StandardOpenOption.APPEND); } catch (IOException error) { throw new RuntimeException(error); }
        context.waitTicks(40);
    }

    private static String kickReason(Minecraft client) {
        if (client.screen instanceof DisconnectedScreen screen) return ((DisconnectedScreenAccessor) screen).donut$details().reason().getString();
        return client.level == null ? "not connected" : null;
    }

    private void resetModules(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ClientConfig config = DonutClient.config();
            if (FlyController.getInstance() != null && FlyController.getInstance().isActive()) FlyController.getInstance().setEnabled(false);
            if (FreecamController.getInstance() != null && FreecamController.getInstance().isActive()) FreecamController.getInstance().setEnabled(false);
            if (AutoBuilderController.get() != null && AutoBuilderController.get().isRunning()) AutoBuilderController.get().cancel();
            config.autoMiner.enabled = config.autoEat.enabled = config.autoReconnect.enabled = config.staffDetector.enabled = false;
            config.fullbright.enabled = config.blockEsp.enabled = config.storageEsp.enabled = config.search.enabled = false;
            config.playerEsp.enabled = config.newChunks.enabled = config.stashFinder.enabled = config.hudWidgets.enabled = false;
            config.coordinates.enabled = config.waypoints.enabled = config.lightDebug.enabled = config.soundDetector.enabled = false;
            config.amethyst.enabled = config.chunkFinder.enabled = config.progressBar.enabled = config.nameHider.enabled = false;
            config.durabilityAlert.enabled = config.zoom.enabled = config.freelook.enabled = config.hotbar.enabled = false;
        });
    }

    private void ensureConnected(ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.level != null && client.player != null)) return;
        console("pardon " + name, "pardon-ip 127.0.0.1");
        context.waitTicks(40);
        connect(context);
    }

    private void connect(ClientGameTestContext context) {
        for (int attempt = 0; attempt < 5; attempt++) {
            context.setScreen(() -> new JoinMultiplayerScreen(new TitleScreen()));
            context.runOnClient(client -> ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString(ADDRESS),
                    new ServerData("Anti-cheat probe", ADDRESS, ServerData.Type.OTHER), false, null));
            boolean joined = until(context, client -> client.level != null && client.player != null, 600);
            // A player saved dead joins on the death screen: respawn, as anyone would.
            if (joined) for (int i = 0; i < 100 && context.computeOnClient(client -> client.player != null && client.player.isDeadOrDying()); i++) {
                context.runOnClient(client -> client.player.respawn());
                context.waitTicks(10);
            }
            if (joined && until(context, client -> client.level != null && client.player != null && !client.player.isDeadOrDying()
                    && (client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.DeathScreen) , 200)) {
                context.runOnClient(client -> client.setScreen(null));
                context.waitTicks(100); // most anti-cheats ignore the first seconds after a join
                return;
            }
            LOGGER.warn("Join attempt {} failed: {}", attempt + 1, context.computeOnClient(AntiCheatProbeGameTest::kickReason));
            context.waitTicks(100);
        }
        throw new AssertionError("Could not join the anti-cheat server at " + ADDRESS);
    }

    private static void console(String... commands) {
        try {
            Files.writeString(CONSOLE, String.join("\n", commands) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new RuntimeException("Cannot write the server console at " + CONSOLE, error);
        }
    }

    private static boolean until(ClientGameTestContext context, Predicate<Minecraft> condition, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (context.computeOnClient(condition::test)) return true;
            context.waitTick();
        }
        return false;
    }

    private static void walk(ClientGameTestContext context, KeyMapping key, int ticks) {
        context.getInput().holdKey(key);
        context.waitTicks(ticks);
        context.getInput().releaseKey(key);
    }

    private static void aim(ClientGameTestContext context, Vec3 target) {
        context.runOnClient(client -> {
            Vec3 delta = target.subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        });
        context.waitTicks(4);
    }

    private static KeyMapping key(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> Arrays.stream(client.options.keyMappings).filter(key -> key.getName().equals(name)).findFirst().orElse(null));
    }

    @SuppressWarnings("unused")
    private static String blockId(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }
}
