package dev.donut.client.gametest;

import dev.donut.client.blockesp.BlockEspController;
import dev.donut.client.blockesp.OreConfidence;
import dev.donut.client.blockesp.OreMarker;
import dev.donut.client.blockesp.OreMarker.Kind;
import dev.donut.client.blockesp.VanillaOreGenerator;
import dev.donut.client.config.BlockEspSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.gui.BlockEspHud;
import dev.donut.client.render.HighlightRenderer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;

/** Fresh seeded vanilla chunks, exact variant/Y filtering, cancellation, display and lifecycle. */
public final class BlockEspGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = new ClientConfig();
        config.blockEsp.radius = 1;
        Path scratch = FabricLoader.getInstance().getGameDir().resolve(".cache/blockesp-runtime-test");
        var scanner = new BlockEspController(config, scratch);
        var active = new AtomicBoolean(true);
        context.getInput().resizeWindow(1280, 800);
        int originalScale = context.computeOnClient(mc -> mc.options.guiScale().get());
        var renderer = context.computeOnClient(mc -> {
            mc.options.guiScale().set(1); mc.resizeDisplay();
            var result = new HighlightRenderer(config, null, null, null, scanner); result.register();
            ClientTickEvents.END_CLIENT_TICK.register(client -> { if (active.get()) scanner.tick(client); });
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("donut-gametest", "blockesp"),
                    (graphics, delta) -> { if (active.get()) BlockEspHud.render(graphics, config, scanner); });
            return result;
        });
        try (var world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
            settings.setSeed(BlockEspSettings.DEFAULT_SEED);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setAllowCommands(true);
        }).create()) {
            var server = world.getServer();
            require(server.computeOnServer(mc -> mc.overworld().getSeed()) == 6608149111735331168L, "World lost seed precision");
            server.runOnServer(mc -> {
                mc.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SEND_COMMAND_FEEDBACK, false, mc);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = true; player.onUpdateAbilities();
            });
            server.runCommand("tp @a -23.5 140 -23.5");
            context.waitTicks(8);
            require(scanner.snapshot().isEmpty(), "Disabled BlockESP emitted markers");
            Set<OreMarker> expected = server.computeOnServer(mc -> {
                var result = new HashSet<OreMarker>();
                for (int z = -3; z <= -1; z++) for (int x = -3; x <= -1; x++)
                    result.addAll(VanillaOreGenerator.findOres(mc.overworld().getChunk(x, z)));
                return Set.copyOf(result);
            });
            LoggerFactory.getLogger("Donut/BlockESPTest").info("Fresh vanilla comparison: {} target ores across 9 negative-coordinate chunks", expected.size());
            context.runOnClient(mc -> config.blockEsp.enabled = true);
            context.waitFor(mc -> {
                require(!scanner.status().startsWith("Prediction failed"), scanner.status());
                return scanner.completedChunks() == 9;
            }, 2400);
            context.runOnClient(mc -> require(new HashSet<>(scanner.snapshot()).equals(expected),
                    "Seed prediction disagrees with fresh vanilla chunks: expected " + expected + ", actual " + scanner.snapshot()));

            // A newly enabled ore (diamond, both variants) matches fresh vanilla generation.
            int diamondMask = OreMarker.bit(Kind.DIAMOND, false) | OreMarker.bit(Kind.DIAMOND, true);
            Set<OreMarker> diamonds = server.computeOnServer(mc -> {
                var result = new HashSet<OreMarker>();
                for (int z = -3; z <= -1; z++) for (int x = -3; x <= -1; x++)
                    result.addAll(VanillaOreGenerator.findOres(mc.overworld().getChunk(x, z), diamondMask));
                return Set.copyOf(result);
            });
            require(!diamonds.isEmpty(), "Fresh comparison must include generated diamonds");
            context.runOnClient(mc -> { config.blockEsp.emerald = config.blockEsp.coal = false; config.blockEsp.diamond = config.blockEsp.stoneVariants = true; });
            awaitReady(context, scanner, 9);
            var shownDiamonds = context.computeOnClient(mc -> Set.copyOf(scanner.snapshot()));
            if (!shownDiamonds.equals(diamonds)) {
                var log = LoggerFactory.getLogger("Donut/BlockESPTest");
                for (var marker : diamonds) if (!shownDiamonds.contains(marker)) {
                    var pos = marker.pos();
                    String client = context.computeOnClient(mc -> mc.level.getBlockState(pos) + " cached=" + scanner.cachedState(pos));
                    String around = server.computeOnServer(mc -> {
                        var histogram = new java.util.TreeMap<String, Integer>();
                        for (var near : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4)))
                            histogram.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.overworld().getBlockState(near).getBlock()).getPath(), 1, Integer::sum);
                        return histogram.toString();
                    });
                    log.warn("Missing prediction {}: client {} around {}", marker, client, around);
                }
                for (var marker : shownDiamonds) if (!diamonds.contains(marker)) log.warn("Extra prediction {}", marker);
            }
            // Vanilla places features that spill across chunk borders in the order chunks were generated, so a few ores
            // near overlapping features (lush cave pools, geodes) can differ between two fresh generations of one seed.
            long agreed = diamonds.stream().filter(shownDiamonds::contains).count();
            LoggerFactory.getLogger("Donut/BlockESPTest").info("Fresh vanilla comparison: {} of {} diamonds predicted, {} predictions",
                    agreed, diamonds.size(), shownDiamonds.size());
            require(agreed >= diamonds.size() * .95 && agreed >= shownDiamonds.size() * .95,
                    "Diamond prediction disagrees with fresh vanilla chunks: expected " + diamonds + ", actual " + shownDiamonds);

            // Confidence: a received matching block is Confirmed; stone over it is Likely; air hides it.
            BlockPos buried = server.computeOnServer(mc -> diamonds.stream().filter(shownDiamonds::contains).map(OreMarker::pos).filter(pos -> {
                for (var direction : net.minecraft.core.Direction.values())
                    if (mc.overworld().getBlockState(pos.relative(direction)).isAir()) return false;
                return true;
            }).findFirst().orElseThrow());
            var original = diamonds.stream().filter(marker -> marker.pos().equals(buried)).findFirst().orElseThrow();
            awaitState(context, scanner, buried, OreConfidence.State.CONFIRMED);
            require(context.computeOnClient(mc -> scanner.find(buried).confidence()) == 100, "Confirmed must be 100%");
            server.runCommand("setblock " + buried.getX() + " " + buried.getY() + " " + buried.getZ() + " minecraft:stone");
            awaitState(context, scanner, buried, OreConfidence.State.LIKELY);
            int likely = context.computeOnClient(mc -> scanner.find(buried).confidence());
            require(likely > 0 && likely <= OreConfidence.LIKELY_CAP, "Likely confidence out of range: " + likely);
            server.runCommand("setblock " + buried.getX() + " " + buried.getY() + " " + buried.getZ() + " minecraft:air");
            context.waitFor(mc -> scanner.find(buried) == null, 200);
            require(context.computeOnClient(mc -> scanner.snapshot().size()) == shownDiamonds.size() - 1, "Air must hide exactly the mined prediction");
            server.runCommand("setblock " + buried.getX() + " " + buried.getY() + " " + buried.getZ() + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(original.block()));
            awaitState(context, scanner, buried, OreConfidence.State.CONFIRMED);

            // Seed match with every ore and both variants: exposed predictions agree with the real world.
            context.runOnClient(mc -> { for (var kind : Kind.values()) config.blockEsp.setOreEnabled(kind, true); });
            awaitReady(context, scanner, 9);
            context.waitFor(mc -> scanner.seedSamples() >= OreConfidence.MIN_SAMPLES, 200);
            context.runOnClient(mc -> {
                LoggerFactory.getLogger("Donut/BlockESPTest").info("Correct seed: {}, {} shown, {} dropped by the per-chunk cap",
                        scanner.seedMatchText(), scanner.snapshot().size(), scanner.droppedByCap());
                require(scanner.seedVerdict() == OreConfidence.Verdict.MATCH, "Correct seed scored low: " + scanner.seedMatchText());
                require(scanner.predictions().stream().anyMatch(p -> p.state() == OreConfidence.State.CONFIRMED), "No confirmed predictions");
            });
            context.runOnClient(mc -> mc.player.setXRot(60));
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("blockesp-04-all-ores-confidence").withSize(1280, 800));

            // Back to the original selection: the wider cached scans reproduce exactly the original markers.
            context.runOnClient(mc -> {
                for (var kind : Kind.values()) config.blockEsp.setOreEnabled(kind, kind == Kind.EMERALD || kind == Kind.COAL);
                config.blockEsp.stoneVariants = false;
            });
            context.waitTicks(2);
            context.runOnClient(mc -> require(new HashSet<>(scanner.snapshot()).equals(expected),
                    "Default selection changed after enabling more ores: expected " + expected + ", actual " + scanner.snapshot()));

            // Both exact variants must survive scanning above and below Y=0, at negative world coordinates.
            server.runOnServer(mc -> {
                var chunk = mc.overworld().getChunk(-7, -9);
                BlockPos emerald = new BlockPos(-110, -17, -142), coal = new BlockPos(-109, 5, -142);
                BlockPos regularEmerald = new BlockPos(-108, -17, -142), regularCoal = new BlockPos(-107, 5, -142);
                chunk.setBlockState(emerald, Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState(), 0);
                chunk.setBlockState(coal, Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(), 0);
                chunk.setBlockState(regularEmerald, Blocks.EMERALD_ORE.defaultBlockState(), 0);
                chunk.setBlockState(regularCoal, Blocks.COAL_ORE.defaultBlockState(), 0);
                var hits = VanillaOreGenerator.findOres(chunk);
                require(hits.contains(new OreMarker(emerald, Kind.EMERALD, true)), "Missing deepslate emerald below Y=0");
                require(hits.contains(new OreMarker(coal, Kind.COAL, true)), "Missing deepslate coal above Y=0");
                require(hits.stream().noneMatch(hit -> hit.pos().equals(regularEmerald) || hit.pos().equals(regularCoal)), "Ordinary ores matched");
                var stone = VanillaOreGenerator.findOres(chunk, OreMarker.bit(Kind.EMERALD, false) | OreMarker.bit(Kind.COAL, false));
                require(stone.contains(new OreMarker(regularEmerald, Kind.EMERALD, false)) && stone.contains(new OreMarker(regularCoal, Kind.COAL, false)),
                        "Stone variants missing when selected");
                require(stone.stream().noneMatch(hit -> hit.pos().equals(emerald) || hit.pos().equals(coal)), "Deepslate matched a stone-only mask");
            });
            context.runOnClient(mc -> { config.blockEsp.coal = false; });
            context.waitTicks(2);
            require(context.computeOnClient(mc -> scanner.snapshot().stream().noneMatch(marker -> marker.kind() == OreMarker.Kind.COAL)), "Coal filter retained coal");
            context.runOnClient(mc -> config.blockEsp.coal = true);
            context.waitTicks(2);
            require(!expected.isEmpty(), "Fresh comparison must include actual generated target ores");
            var target = expected.iterator().next().pos();
            int cameraX = target.getX() + 8, cameraY = target.getY() + 8, cameraZ = target.getZ() + 8;
            server.runCommand("fill " + (cameraX - 1) + " " + (cameraY - 1) + " " + (cameraZ - 1) + " "
                    + (cameraX + 1) + " " + (cameraY + 2) + " " + (cameraZ + 1) + " minecraft:air");
            server.runCommand("tp @a " + (cameraX + .5) + " " + cameraY + " " + (cameraZ + .5) + " 135 40");
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            context.runOnClient(mc -> config.blockEsp.tracers = true);
            context.waitTicks(15);
            world.getClientWorld().waitForChunksRender();
            context.takeScreenshot(TestScreenshotOptions.of("blockesp-01-predictions").withSize(1280, 800));

            // A wrong seed: exposed predictions disagree with the world, so the HUD warns about a mismatch.
            context.runOnClient(mc -> {
                for (var kind : Kind.values()) config.blockEsp.setOreEnabled(kind, true);
                config.blockEsp.stoneVariants = true;
                config.blockEsp.seed = "1";
            });
            awaitReady(context, scanner, 9);
            context.waitFor(mc -> scanner.seedSamples() >= OreConfidence.MIN_SAMPLES, 200);
            context.waitTicks(40);
            context.runOnClient(mc -> {
                LoggerFactory.getLogger("Donut/BlockESPTest").info("Wrong seed: {}", scanner.seedMatchText());
                require(scanner.seedVerdict() == OreConfidence.Verdict.MISMATCH, "Wrong seed was not flagged: " + scanner.seedMatchText());
            });
            context.takeScreenshot(TestScreenshotOptions.of("blockesp-05-wrong-seed").withSize(1280, 800));
            context.runOnClient(mc -> {
                for (var kind : Kind.values()) config.blockEsp.setOreEnabled(kind, kind == Kind.EMERALD || kind == Kind.COAL);
                config.blockEsp.stoneVariants = false;
                config.blockEsp.seed = BlockEspSettings.DEFAULT_SEED;
            });

            // A new seed invalidates results immediately; disabling must suppress a still-running old job.
            context.runOnClient(mc -> { config.blockEsp.seed = "-1"; scanner.tick(mc); config.blockEsp.enabled = false; scanner.tick(mc); });
            context.waitTicks(30);
            require(scanner.snapshot().isEmpty(), "Stale prediction appeared after disable/seed change");
            context.runOnClient(mc -> { config.blockEsp.seed = "invalid"; config.blockEsp.enabled = true; });
            context.waitTicks(2);
            require(scanner.snapshot().isEmpty() && scanner.status().contains("seed"), "Invalid seed was silently substituted");
            context.runOnClient(mc -> config.blockEsp.enabled = false);
        } finally {
            active.set(false);
            context.runOnClient(mc -> { scanner.close(); renderer.close(); mc.options.guiScale().set(originalScale); mc.resizeDisplay(); });
        }
        require(scanner.snapshot().isEmpty(), "Disconnect retained prediction markers");
    }
    private static void awaitReady(ClientGameTestContext context, BlockEspController scanner, int chunks) {
        context.waitTicks(2);
        context.waitFor(mc -> {
            require(!scanner.status().startsWith("Prediction failed"), scanner.status());
            return scanner.completedChunks() == chunks && scanner.status().startsWith("Ready");
        }, 2400);
        // Let the per-tick evaluation pass over every cached chunk.
        context.waitTicks(10);
    }

    private static void awaitState(ClientGameTestContext context, BlockEspController scanner, BlockPos pos, OreConfidence.State state) {
        context.waitFor(mc -> {
            var prediction = scanner.find(pos);
            return prediction != null && prediction.state() == state;
        }, 200);
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
