package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.automine.AutoMinerController;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.waypoints.WaypointController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Developer tool, not a test: plays a scripted tour of Donut's features in the testing grounds and records it.
 * One frame is captured per client tick (20 fps of game time), each scene is encoded with ffmpeg as it finishes so
 * the PNG frames never pile up, and the scenes are joined into {@code demo/donut-client-demo.mp4}.
 * Runs only with {@code -PdemoReelOnly=true}.
 */
public final class DemoReelGameTest implements FabricClientGameTest {
    private static final int Y = -60;
    private static final String FFMPEG = System.getProperty("donut.demo.ffmpeg", "/opt/homebrew/bin/ffmpeg");
    private static volatile String title = "", subtitle = "";
    private static boolean captions;

    private final List<Path> frames = new ArrayList<>();
    private final List<Path> clips = new ArrayList<>();
    private Path out;
    private double cursorX = 640, cursorY = 360;
    /** Waits for the current client world's chunks to render. */
    private Runnable waitChunks = () -> { };

    @Override public void runTest(ClientGameTestContext context) {
        out = Path.of(System.getProperty("donut.demo.out", "../../../demo")).toAbsolutePath().normalize();
        try { Files.createDirectories(out.resolve("clips")); } catch (IOException error) { throw new AssertionError(error); }
        if (!captions) {
            captions = true;
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("donut_demo", "caption"), (g, delta) -> caption(g));
            // HUD elements are hidden behind menus; draw the caption over screens as well.
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((shown, g, mx, my, delta) -> caption(g)));
        }
        var config = DonutClient.config();
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(true);
            // Far enough to see the Zoom tower 145 blocks away.
            mc.options.renderDistance().set(14);
            mc.resizeDisplay();
            config.showHud = true;
            config.coordinates.enabled = false;
            config.waypoints.enabled = false;
            config.storageEsp.enabled = false;
            config.zoom.enabled = false;
            config.durabilityAlert.enabled = false;
            config.autoMiner.enabled = false;
            config.blockEsp.enabled = false;
            config.clickGuiParticles = false;
            // Larger panels so they stay readable in a 720p video.
            config.clickGuiStyle.hudScale = 1.35;
        });
        try (var world = context.worldBuilder().adjustSettings(TestingGroundsBuilder::settings).create()) {
            TestServerContext server = world.getServer();
            TestingGroundsBuilder.build(context, server);
            TestingGroundsBuilder.run(server, "gamerule send_command_feedback false", "gamerule show_death_messages false");
            world.getClientWorld().waitForChunksRender();

            intro(context, server);
            menu(context);
            storage(context, server);
            waypoints(context, server);
            freecam(context, server);
            zoom(context, server);
            durability(context, server);
            miner(context, server);
        }
        try (var world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(creator -> {
            creator.setSeed(dev.donut.client.config.BlockEspSettings.DEFAULT_SEED);
            creator.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
        }).create()) {
            TestServerContext server = world.getServer();
            waitChunks = () -> world.getClientWorld().waitForChunksRender();
            predictor(context, server);
        } finally {
            context.runOnClient(mc -> {
                title = subtitle = "";
                config.autoMiner.enabled = false;
                if (FreecamController.getInstance() != null) FreecamController.getInstance().setEnabled(false);
            });
        }
        join();
    }

    // ---------------------------------------------------------------- scenes

    private void intro(ClientGameTestContext context, TestServerContext server) {
        TestingGroundsBuilder.run(server, "tp @a 0.5 " + Y + " 2.5 180 4");
        context.waitTicks(10);
        caption("Donut Client", "A client-side Fabric mod for Minecraft 1.21.11");
        context.runOnClient(mc -> DonutClient.config().coordinates.enabled = true);
        film(context, 70, t -> turnPlayer(context, -0.35f, 0));
        scene("01-intro");
    }

    private void menu(ClientGameTestContext context) {
        var config = DonutClient.config();
        caption("The menu", "Right Shift opens it. Categories, search and per-module settings");
        context.setScreen(() -> new DonutScreen(config, () -> { }));
        film(context, 24, null);
        glideClick(context, "tab0", 14, 12);
        glideClick(context, "module15", 16, 24);
        caption("Settings follow your click", "Hovering doesn't change the inspector; clicking a module does");
        glideTo(context, "module17", 14);
        film(context, 10, null);
        glideClick(context, "module16", 14, 22);
        caption("Search covers settings too", "\"tracer\" finds every module with a tracer option");
        glideClick(context, "search", 14, 6);
        for (char c : "tracer".toCharArray()) {
            context.runOnClient(mc -> mc.screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0)));
            film(context, 3, null);
        }
        film(context, 22, null);
        caption("HUD and appearance", "One compact style for every in-game panel, with a size slider");
        glideClick(context, "tab6", 16, 30);
        glideClick(context, "tab2", 16, 30);
        glideClick(context, "workspaceModules", 16, 8);
        caption("Auto Miner", "Ores, stone or deepslate forms, Silk Touch, pickup and safety limits");
        glideClick(context, "tab3", 14, 10);
        glideClick(context, "module20", 14, 40);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        film(context, 14, null);
        context.setScreen(() -> null);
        scene("02-menu");
    }

    private void storage(ClientGameTestContext context, TestServerContext server) {
        TestingGroundsBuilder.run(server, "tp @a 30.5 " + (Y + 1) + " -14.5 0 22");
        context.runOnClient(mc -> mc.player.setPos(30.5, Y + 1, -14.5));
        context.waitTicks(30);
        caption("Storage ESP", "Every container type in its own colour, through walls, with a count panel");
        context.runOnClient(mc -> DonutClient.config().storageEsp.enabled = true);
        film(context, 50, t -> turnPlayer(context, t < 25 ? -0.8f : 0.8f, 0));
        caption("Storage ESP", "Double chests merge into one box; the hut and a buried stash show too");
        TestingGroundsBuilder.run(server, "tp @a 36.5 " + (Y + 3) + " 14.5 150 30");
        context.waitTicks(10);
        film(context, 80, t -> turnPlayer(context, 0.45f, 0));
        context.runOnClient(mc -> DonutClient.config().storageEsp.enabled = false);
        scene("03-storage-esp");
    }

    private void waypoints(ClientGameTestContext context, TestServerContext server) {
        context.runOnClient(mc -> {
            var waypoints = WaypointController.get();
            waypoints.add("Storage area", new BlockPos(31, Y, 0)).color = WaypointController.PRESET_COLORS[2];
            waypoints.add("Freecam tower", new BlockPos(-37, Y, -3)).color = WaypointController.PRESET_COLORS[0];
            waypoints.add("Ore field", new BlockPos(0, Y + 7, 31)).color = WaypointController.PRESET_COLORS[1];
            waypoints.add("Zoom tower", new BlockPos(0, Y + 31, -147)).color = WaypointController.PRESET_COLORS[4];
            waypoints.add("Nether portal", new BlockPos(0, Y + 1, -20)).color = WaypointController.PRESET_COLORS[5];
            DonutClient.config().waypoints.enabled = true;
        });
        TestingGroundsBuilder.run(server, "tp @a 0.5 " + Y + " 2.5 180 0");
        context.waitTicks(10);
        caption("Waypoints", "Saved places with beams and name / distance labels, visible through walls");
        film(context, 140, t -> turnPlayer(context, 2.4f, 0));
        scene("04-waypoints");
    }

    private void freecam(ClientGameTestContext context, TestServerContext server) {
        TestingGroundsBuilder.run(server, "tp @a -16.5 " + Y + " 6.5 90 0");
        context.waitTicks(10);
        caption("Freecam", "A detached camera: your player stays put, the server sees nothing move");
        var settings = DonutClient.config().freecam;
        context.runOnClient(mc -> {
            settings.speed = 0.35;
            settings.smoothing = 0.6;
            // Keep the player in frame while backing away and rising.
            settings.lookAtTarget = true;
            settings.targetX = -17; settings.targetY = Y + 1; settings.targetZ = 6;
            FreecamController.getInstance().setEnabled(true);
        });
        film(context, 10, null);
        context.getInput().holdKey(options -> options.keyDown);
        context.getInput().holdKey(options -> options.keyJump);
        film(context, 45, null);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().releaseKey(options -> options.keyDown);
        caption("Freecam", "It flies through walls: here into a sealed room, looking at the beacon");
        context.runOnClient(mc -> { settings.targetX = -27; settings.targetY = Y + 1; settings.targetZ = 7; });
        film(context, 25, null);
        context.getInput().holdKey(options -> options.keyUp);
        film(context, 70, null);
        context.getInput().releaseKey(options -> options.keyUp);
        film(context, 30, null);
        caption("Freecam", "Turning it off snaps the view back to your player");
        context.runOnClient(mc -> { settings.lookAtTarget = false; FreecamController.getInstance().setEnabled(false); });
        film(context, 25, null);
        scene("05-freecam");
    }

    private void zoom(ClientGameTestContext context, TestServerContext server) {
        // Beside the portal, so the tower 145 blocks north is in clear view.
        TestingGroundsBuilder.run(server, "tp @a 6.5 " + Y + " -1.5 177.4 -5");
        context.waitTicks(10);
        caption("Zoom", "Hold Z to zoom smoothly; scroll while holding to adjust");
        context.runOnClient(mc -> DonutClient.config().zoom.enabled = true);
        film(context, 15, null);
        context.getInput().holdKey(ModuleKeybinds.zoomKey());
        film(context, 30, null);
        context.getInput().scroll(3);
        film(context, 35, null);
        context.getInput().releaseKey(ModuleKeybinds.zoomKey());
        film(context, 20, null);
        context.runOnClient(mc -> DonutClient.config().zoom.enabled = false);
        scene("06-zoom");
    }

    private void durability(ClientGameTestContext context, TestServerContext server) {
        TestingGroundsBuilder.run(server, "gamemode survival @a", "tp @a 0.5 " + Y + " 2.5 0 10");
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        context.waitTicks(10);
        caption("Durability Alert", "Warns once, with a chime, before a tool or armour piece breaks");
        context.runOnClient(mc -> DonutClient.config().durabilityAlert.enabled = true);
        film(context, 60, null);
        context.runOnClient(mc -> DonutClient.config().durabilityAlert.enabled = false);
        scene("07-durability");
    }

    private void miner(ClientGameTestContext context, TestServerContext server) {
        TestingGroundsBuilder.run(server, "tp @a 0.5 " + (Y + 7) + " 22.5 0 30",
                "item replace entity @a hotbar.0 with minecraft:diamond_pickaxe");
        context.waitTicks(20);
        caption("Auto Miner", "Walks to visible ores, mines them with vanilla controls and picks up the drops");
        context.runOnClient(mc -> {
            var settings = DonutClient.config().autoMiner;
            settings.ores.replaceAll((id, on) -> true);
            settings.includeBuried = false;
            settings.stoneVariants = settings.deepslateVariants = true;
            settings.silkDeepslate = true;
            settings.collectDrops = true;
            settings.range = 24;
            settings.enabled = true;
        });
        int[] shownSilk = {0};
        film(context, 640, t -> {
            if (t == 320) caption("Auto Miner", "Deepslate ores use the Silk Touch pickaxe, so the ore block itself drops");
            if (shownSilk[0] == 0 && context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot() == 2)) {
                shownSilk[0] = 1;
                caption("Auto Miner", "Deepslate ore: switching to the Silk Touch pickaxe");
            }
        });
        context.runOnClient(mc -> DonutClient.config().autoMiner.enabled = false);
        film(context, 10, null);
        scene("08-auto-miner");
    }

    private void predictor(ClientGameTestContext context, TestServerContext server) {
        var config = DonutClient.config();
        var blockEsp = dev.donut.client.blockesp.BlockEspController.get();
        TestingGroundsBuilder.run(server, "gamerule send_command_feedback false", "gamemode spectator @a", "time set noon",
                "tp @a 8.5 -24 8.5 45 20");
        context.waitTicks(40);
        waitChunks.run();
        context.runOnClient(mc -> {
            var settings = config.blockEsp;
            settings.seed = dev.donut.client.config.BlockEspSettings.DEFAULT_SEED;
            for (var kind : dev.donut.client.blockesp.OreMarker.Kind.values()) settings.setOreEnabled(kind, false);
            for (var kind : List.of(dev.donut.client.blockesp.OreMarker.Kind.DIAMOND, dev.donut.client.blockesp.OreMarker.Kind.GOLD,
                    dev.donut.client.blockesp.OreMarker.Kind.LAPIS, dev.donut.client.blockesp.OreMarker.Kind.REDSTONE,
                    dev.donut.client.blockesp.OreMarker.Kind.EMERALD)) settings.setOreEnabled(kind, true);
            settings.stoneVariants = settings.deepslateVariants = true;
            settings.radius = 2;
            settings.showHud = true;
            config.coordinates.enabled = true;
            settings.enabled = true;
        });
        // Prediction runs a private vanilla generator for the seed; wait until the area is done before filming.
        context.waitFor(mc -> blockEsp.totalChunks() > 0 && blockEsp.completedChunks() >= blockEsp.totalChunks(), 2400);
        context.waitTicks(40);
        caption("BlockESP seed predictor", "Rebuilds original generation from the world seed and marks every selected ore");
        film(context, 80, t -> turnPlayer(context, 0.9f, 0));
        caption("Confidence for every marker", "Confirmed when you can see it, likely when hidden, plus a seed match score");
        film(context, 90, t -> turnPlayer(context, 0.9f, t < 45 ? 0.15f : -0.15f));
        caption("Client-side only", "Everything reads data your game already has. No extra packets, no teleports.");
        film(context, 70, t -> turnPlayer(context, 0.9f, 0));
        context.runOnClient(mc -> { config.blockEsp.enabled = false; config.coordinates.enabled = false; });
        scene("09-predictor");
    }

    // ---------------------------------------------------------------- capture

    private void film(ClientGameTestContext context, int ticks, IntConsumer perTick) {
        for (int t = 0; t < ticks; t++) {
            if (perTick != null) perTick.accept(t);
            context.waitTick();
            context.runOnClient(mc -> { mc.getToastManager().clear(); mc.gui.getChat().clearMessages(false); });
            frames.add(context.takeScreenshot(TestScreenshotOptions.of("reel").withSize(1280, 720)));
        }
    }

    private static void turnPlayer(ClientGameTestContext context, float yaw, float pitch) {
        context.runOnClient(mc -> {
            if (mc.player == null) return;
            mc.player.setYRot(mc.player.getYRot() + yaw);
            mc.player.setXRot(Math.clamp(mc.player.getXRot() + pitch, -90, 90));
        });
    }

    /** Moves the real cursor to a control over a few frames, like a hand on a mouse, scrolling it into view first. */
    private void glideTo(ClientGameTestContext context, String id, int ticks) {
        if (!GuiTestInput.shown(context, id)) {
            boolean inspector = !id.matches("module\\d+|enable\\d+");
            glideToPoint(context, GuiTestInput.areaCenter(context, inspector), 10);
            for (int i = 0; i < 40 && !GuiTestInput.shown(context, id); i++) {
                context.getInput().scroll(-1);
                film(context, 2, null);
            }
            film(context, 6, null);
        }
        glideToPoint(context, GuiTestInput.windowCenter(context, id), ticks);
    }

    private void glideToPoint(ClientGameTestContext context, double[] target, int ticks) {
        if (target == null) throw new AssertionError("Demo control not visible");
        double startX = cursorX, startY = cursorY;
        for (int t = 1; t <= ticks; t++) {
            double k = t / (double) ticks, eased = k * k * (3 - 2 * k);
            cursorX = startX + (target[0] - startX) * eased;
            cursorY = startY + (target[1] - startY) * eased;
            context.getInput().setCursorPos(cursorX, cursorY);
            film(context, 1, null);
        }
    }

    private void glideClick(ClientGameTestContext context, String id, int glideTicks, int holdTicks) {
        glideTo(context, id, glideTicks);
        GuiTestInput.clickNow(context, id);
        film(context, holdTicks, null);
    }

    private static void caption(String newTitle, String newSubtitle) {
        title = newTitle;
        subtitle = newSubtitle;
    }

    private static void caption(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        String main = title, sub = subtitle;
        if (main.isEmpty() || mc.options.hideGui) return;
        var font = mc.font;
        float big = 1.5f;
        int titleW = Math.round(font.width(main) * big), subW = font.width(sub);
        int w = Math.max(titleW, subW) + 24, h = Math.round(9 * big) + 9 + 20;
        // Below an open menu, otherwise above the hotbar.
        int x = (g.guiWidth() - w) / 2, y = mc.screen != null ? g.guiHeight() - h - 3 : g.guiHeight() - 58 - h;
        g.fill(x, y, x + w, y + h, 0xC80D1015);
        g.fill(x, y, x + 2, y + h, 0xFF56DCCB);
        g.pose().pushMatrix();
        g.pose().translate(x + (w - titleW) / 2f, y + 7);
        g.pose().scale(big, big);
        g.drawString(font, main, 0, 0, 0xFFF2F4F7, false);
        g.pose().popMatrix();
        g.drawString(font, sub, x + (w - subW) / 2, y + 7 + Math.round(9 * big) + 4, 0xFFC7CCD6, false);
    }

    // ---------------------------------------------------------------- encoding

    /** Encodes the frames captured since the last scene into one clip, then deletes them. */
    private void scene(String name) {
        if (frames.isEmpty()) return;
        try {
            Path list = out.resolve("clips").resolve(name + ".txt");
            StringBuilder text = new StringBuilder();
            for (Path frame : frames) text.append("file '").append(frame.toAbsolutePath()).append("'\nduration 0.05\n");
            text.append("file '").append(frames.getLast().toAbsolutePath()).append("'\n");
            Files.writeString(list, text);
            Path clip = out.resolve("clips").resolve(name + ".mp4");
            ffmpeg("-f", "concat", "-safe", "0", "-i", list.toString(), "-vf", "fps=20,format=yuv420p",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "18", clip.toString());
            clips.add(clip);
            for (Path frame : frames) Files.deleteIfExists(frame);
            Files.deleteIfExists(list);
            frames.clear();
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private void join() {
        try {
            Path list = out.resolve("clips").resolve("all.txt");
            StringBuilder text = new StringBuilder();
            for (Path clip : clips) text.append("file '").append(clip.toAbsolutePath()).append("'\n");
            Files.writeString(list, text);
            ffmpeg("-f", "concat", "-safe", "0", "-i", list.toString(), "-c", "copy", "-movflags", "+faststart",
                    out.resolve("donut-client-demo.mp4").toString());
            System.out.println("[DemoReel] Saved " + out.resolve("donut-client-demo.mp4"));
        } catch (IOException error) { throw new AssertionError(error); }
    }

    private static void ffmpeg(String... args) {
        List<String> command = new ArrayList<>(List.of(FFMPEG, "-y", "-loglevel", "error"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            if (process.waitFor() != 0) throw new AssertionError("ffmpeg failed: " + command);
        } catch (IOException | InterruptedException error) { throw new AssertionError(error); }
    }
}
