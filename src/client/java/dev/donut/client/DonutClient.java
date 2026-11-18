package dev.donut.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import dev.donut.client.chunkfinder.ChunkFinderScanner;
import dev.donut.client.amethyst.AmethystScanner;
import dev.donut.client.blockesp.BlockEspController;
import dev.donut.client.gui.BlockEspHud;
import dev.donut.client.gui.BaseFinderPanel;
import dev.donut.client.fullbright.FullbrightController;
import dev.donut.client.freecam.FreecamController;
import dev.donut.client.fly.FlyController;
import dev.donut.client.namehider.NameHiderController;
import dev.donut.client.hotbar.HotbarBlur;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.GlassPanel;
import dev.donut.client.gui.ModuleArrayList;
import dev.donut.client.input.ModuleKeybinds;
import dev.donut.client.render.HighlightRenderer;
import dev.donut.client.autobuild.AutoBuilderController;
import dev.donut.client.gui.AutoBuilderScreen;
import dev.donut.client.gui.AutoBuilderHud;
import dev.donut.client.gui.ProgressBarHud;
import dev.donut.client.progress.ProgressTimers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class DonutClient implements ClientModInitializer {
    public static final String MOD_ID = "donut";
    private static ClientConfig activeConfig;

    /** The live configuration (for runtime tests and helper screens); null before initialization. */
    public static ClientConfig config() { return activeConfig; }
    private static dev.donut.client.killswitch.KillSwitch activeKillSwitch;

    /** This build's expiry clock (see KillSwitch); null before initialization. */
    public static dev.donut.client.killswitch.KillSwitch killSwitch() { return activeKillSwitch; }

    /** True once this test build has run out: every module stays off. */
    public static boolean expired() { return activeKillSwitch != null && activeKillSwitch.expired(System.currentTimeMillis()); }
    private boolean expiryNoticeShown;
    private ConfigStore store;
    private ClientConfig config;
    private ChunkFinderScanner chunkScanner;
    private AmethystScanner amethystScanner;
    private dev.donut.client.light.LightDebugScanner lightDebug;
    private dev.donut.client.sound.SoundDetectorController soundDetector;
    private BlockEspController blockEsp;
    private dev.donut.client.staff.StaffDetectorController staffDetector;
    private dev.donut.client.storageesp.StorageEspScanner storageEsp;
    private dev.donut.client.waypoints.WaypointController waypoints;
    private dev.donut.client.zoom.ZoomController zoom;
    private dev.donut.client.durability.DurabilityAlertController durabilityAlert;
    private dev.donut.client.automine.AutoMinerController autoMiner;
    private dev.donut.client.playeresp.PlayerEspController playerEsp;
    private dev.donut.client.search.SearchScanner search;
    private dev.donut.client.freelook.FreelookController freelook;
    private dev.donut.client.autoreconnect.AutoReconnectController autoReconnect;
    private dev.donut.client.autoeat.AutoEatController autoEat;
    private dev.donut.client.newchunks.NewChunksController newChunks;
    private dev.donut.client.stash.StashFinderController stashFinder;
    private dev.donut.client.structurefinder.StructureFinderController structureFinder;

    @Override
    public void onInitializeClient() {
        GlassPanel.initialize();
        ProgressBarHud.initialize();
        store = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("donut-client.json"));
        config = store.load();
        activeConfig = config;
        new HotbarBlur(config);
        new NameHiderController(config);
        chunkScanner = new ChunkFinderScanner(config);
        amethystScanner = new AmethystScanner(config);
        lightDebug = new dev.donut.client.light.LightDebugScanner(config);
        soundDetector = new dev.donut.client.sound.SoundDetectorController(config);
        blockEsp = new BlockEspController(config);
        storageEsp = new dev.donut.client.storageesp.StorageEspScanner(config);
        playerEsp = new dev.donut.client.playeresp.PlayerEspController(config);
        search = new dev.donut.client.search.SearchScanner(config);
        waypoints = new dev.donut.client.waypoints.WaypointController(config, this::save).chunkFinder(chunkScanner);
        zoom = new dev.donut.client.zoom.ZoomController(config, this::save);
        durabilityAlert = new dev.donut.client.durability.DurabilityAlertController(config);
        autoMiner = new dev.donut.client.automine.AutoMinerController(config, this::save);
        staffDetector = new dev.donut.client.staff.StaffDetectorController(config, this::save);
        freelook = new dev.donut.client.freelook.FreelookController(config);
        autoReconnect = new dev.donut.client.autoreconnect.AutoReconnectController(config);
        autoEat = new dev.donut.client.autoeat.AutoEatController(config);
        newChunks = new dev.donut.client.newchunks.NewChunksController(config);
        stashFinder = new dev.donut.client.stash.StashFinderController(config);
        structureFinder = new dev.donut.client.structurefinder.StructureFinderController(config);
        activeKillSwitch = new dev.donut.client.killswitch.KillSwitch(dev.donut.client.killswitch.KillSwitch.currentBuild(),
                dev.donut.client.killswitch.KillSwitch.defaultFile(), System.currentTimeMillis());
        // Registered before every module's tick, so an expired build never runs a module for even one tick.
        ClientTickEvents.START_CLIENT_TICK.register(this::enforceKillSwitch);
        // Also before every module tick: Blatant modules stop on servers the user has not allowed.
        ClientTickEvents.START_CLIENT_TICK.register(this::enforceBlatantGuard);
        ClientTickEvents.END_CLIENT_TICK.register(auto -> {
            dev.donut.client.spectrebuilder.BuilderPlacement.tick(auto);
            dev.donut.client.spectrebuilder.CreativeBuilder.INSTANCE.tick(auto);
        });
        FullbrightController fullbright = new FullbrightController(config);
        ClientTickEvents.START_CLIENT_TICK.register(fullbright::tick);
        FreecamController freecam = new FreecamController(config, this::save);
        ClientTickEvents.START_CLIENT_TICK.register(freecam::tick);
        FlyController fly = new FlyController(config);
        ClientTickEvents.START_CLIENT_TICK.register(fly::tick);
        ServerTickEvents.START_SERVER_TICK.register(fly::serverTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(fly::serverStopping);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> fly.serverDisconnected(server, handler.player));
        AutoBuilderController autoBuilder = AutoBuilderController.get();
        ClientTickEvents.START_CLIENT_TICK.register(autoBuilder::tick);
        ClientTickEvents.START_CLIENT_TICK.register(autoMiner::tick);
        HighlightRenderer renderer = new HighlightRenderer(config, chunkScanner, amethystScanner, lightDebug, blockEsp);
        renderer.register();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "client"));
        KeyMapping open = bind("open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        KeyMapping camera = bind("toggle_freecam", GLFW.GLFW_KEY_F6, category);
        KeyMapping builder = bind("open_autobuilder", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping chunks = bind("toggle_chunk_finder", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping bright = bind("toggle_fullbright", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping flight = bind("toggle_fly", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping progress = bind("toggle_progress_bar", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping names = bind("toggle_name_hider", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping hotbar = bind("toggle_hotbar", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping amethyst = bind("toggle_amethyst", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping light = bind("toggle_light_debug", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping sounds = bind("toggle_sound_detector", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping blocks = bind("toggle_block_esp", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping staff = bind("toggle_staff_detector", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping storage = bind("toggle_storage_esp", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping coordinates = bind("toggle_coordinates", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping waypointToggle = bind("toggle_waypoints", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping zoomToggle = bind("toggle_zoom", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping durability = bind("toggle_durability_alert", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping miner = bind("toggle_auto_miner", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping playerEspKey = bind("toggle_player_esp", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping searchKey = bind("toggle_search", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping zoomHold = bind("zoom", GLFW.GLFW_KEY_Z, category);
        KeyMapping waypointMenu = bind("waypoints_menu", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping waypointAdd = bind("waypoint_add_here", InputConstants.UNKNOWN.getValue(), category);
        zoom.setKey(zoomHold);
        ModuleKeybinds.setZoomKey(zoomHold);
        KeyMapping acknowledgeSound = bind("acknowledge_sound", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping muteSound = bind("mute_sound", InputConstants.UNKNOWN.getValue(), category);
        KeyMapping soundHistory = bind("sound_history", InputConstants.UNKNOWN.getValue(), category);
        ModuleKeybinds.initialize(camera, builder, chunks, bright, flight, progress, names, hotbar, amethyst, light, sounds, blocks, staff, storage,
                coordinates, waypointToggle, zoomToggle, durability, miner);
        ModuleKeybinds.register(22, playerEspKey);
        ModuleKeybinds.register(23, searchKey);
        ModuleKeybinds.register(21, bind("toggle_freelook", InputConstants.UNKNOWN.getValue(), category));
        ModuleKeybinds.register(27, bind("toggle_auto_reconnect", InputConstants.UNKNOWN.getValue(), category));
        ModuleKeybinds.register(28, bind("toggle_auto_eat", InputConstants.UNKNOWN.getValue(), category));
        ModuleKeybinds.register(29, bind("open_structure_finder", InputConstants.UNKNOWN.getValue(), category));
        freelook.setKey(bind("freelook", GLFW.GLFW_KEY_LEFT_ALT, category));
        ModuleKeybinds.register(24, bind("toggle_hud_widgets", InputConstants.UNKNOWN.getValue(), category));
        ModuleKeybinds.register(25, bind("toggle_new_chunks", InputConstants.UNKNOWN.getValue(), category));
        ModuleKeybinds.register(26, bind("toggle_stash_finder", InputConstants.UNKNOWN.getValue(), category));
        KeyMapping stashMenu = bind("stash_menu", InputConstants.UNKNOWN.getValue(), category);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ProgressTimers.tick(config);
            boolean menuBlocked = ModuleKeybinds.inputBlocked(client);
            while (open.consumeClick()) {
                if (!menuBlocked && client.screen == null) client.setScreen(new DonutScreen(config, this::save));
            }
            // After the kill switch fires the menu still opens (it explains why), but no shortcut switches a module on.
            boolean inputBlocked = menuBlocked || expired();
            if (ModuleKeybinds.consumePress(client, 2)) {
                if (!inputBlocked && client.screen == null && client.player != null) freecam.setEnabled(!freecam.isActive());
            }
            if (ModuleKeybinds.consumePress(client, 3)) {
                if (!inputBlocked && client.screen == null && client.player != null)
                    client.setScreen(new AutoBuilderScreen(null, config, this::save));
            }
            if (ModuleKeybinds.consumePress(client, 4)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    config.chunkFinder.enabled = !config.chunkFinder.enabled;
                    save();
                }
            }
            if (ModuleKeybinds.consumePress(client, 5)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    config.fullbright.enabled = !config.fullbright.enabled;
                    save();
                }
            }
            if (ModuleKeybinds.consumePress(client, 6)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    if (fly.isActive()) fly.setEnabled(false);
                    else if (!guardRefuses(client, "Fly")) fly.setEnabled(true);
                }
            }
            if (ModuleKeybinds.consumePress(client, 7)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    config.progressBar.enabled = !config.progressBar.enabled;
                    save();
                }
            }
            if (ModuleKeybinds.consumePress(client, 8)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    config.nameHider.enabled = !config.nameHider.enabled;
                    save();
                }
            }
            if (ModuleKeybinds.consumePress(client, 9)) {
                if (!inputBlocked && client.screen == null && client.player != null) {
                    config.hotbar.enabled = !config.hotbar.enabled;
                    save();
                }
            }
            fly.tick(client);
            chunkScanner.tick(client);
            if (ModuleKeybinds.consumePress(client, 10) && !inputBlocked && client.screen == null && client.player != null) {
                config.amethyst.enabled = !config.amethyst.enabled;
                save();
            }
            amethystScanner.tick(client);
            if (ModuleKeybinds.consumePress(client, 11) && !inputBlocked && client.screen == null && client.player != null) {
                config.lightDebug.enabled = !config.lightDebug.enabled; save();
            }
            lightDebug.tick(client);
            if (ModuleKeybinds.consumePress(client, 12) && !inputBlocked && client.screen == null && client.player != null) {
                config.soundDetector.enabled = !config.soundDetector.enabled; save();
            }
            while (acknowledgeSound.consumeClick()) if (!inputBlocked) soundDetector.acknowledge();
            while (muteSound.consumeClick()) if (!inputBlocked) soundDetector.mute(60);
            while (soundHistory.consumeClick()) if (!inputBlocked && client.screen == null && client.player != null)
                client.setScreen(new dev.donut.client.gui.SoundHistoryScreen(null, config));
            soundDetector.tick(client);
            if (ModuleKeybinds.consumePress(client, 13) && !inputBlocked && client.screen == null && client.player != null) {
                config.blockEsp.enabled = !config.blockEsp.enabled; save();
            }
            blockEsp.tick(client);
            if (ModuleKeybinds.consumePress(client, 14) && !inputBlocked && client.screen == null && client.player != null) {
                config.staffDetector.enabled = !config.staffDetector.enabled; save();
            }
            staffDetector.tick(client);
            if (ModuleKeybinds.consumePress(client, 15) && !inputBlocked && client.screen == null && client.player != null) {
                config.storageEsp.enabled = !config.storageEsp.enabled; save();
            }
            storageEsp.tick(client);
            for (int module = 16; module <= 19; module++) {
                if (!ModuleKeybinds.consumePress(client, module) || inputBlocked || client.screen != null || client.player == null) continue;
                switch (module) {
                    case 16 -> config.coordinates.enabled = !config.coordinates.enabled;
                    case 17 -> config.waypoints.enabled = !config.waypoints.enabled;
                    case 18 -> config.zoom.enabled = !config.zoom.enabled;
                    default -> config.durabilityAlert.enabled = !config.durabilityAlert.enabled;
                }
                save();
            }
            while (waypointMenu.consumeClick()) if (!inputBlocked && client.screen == null && client.player != null)
                client.setScreen(new dev.donut.client.gui.WaypointScreen(null, config, this::save));
            while (waypointAdd.consumeClick()) if (!inputBlocked && client.screen == null && client.player != null) {
                var added = waypoints.addHere();
                // A local action-bar notice only; nothing is sent to the server.
                client.player.displayClientMessage(Component.literal(added == null ? "Waypoint limit reached"
                        : "Waypoint added: " + added.name + " (" + added.x + ", " + added.y + ", " + added.z + ")"), true);
            }
            if (ModuleKeybinds.consumePress(client, 20) && !inputBlocked && client.screen == null && client.player != null) {
                if (config.autoMiner.enabled || !guardRefuses(client, "Auto Miner")) { config.autoMiner.enabled = !config.autoMiner.enabled; save(); }
            }
            zoom.tick(client);
            durabilityAlert.tick(client);
            if (ModuleKeybinds.consumePress(client, 22) && !inputBlocked && client.screen == null && client.player != null) {
                config.playerEsp.enabled = !config.playerEsp.enabled; save();
            }
            playerEsp.tick(client);
            if (ModuleKeybinds.consumePress(client, 23) && !inputBlocked && client.screen == null && client.player != null) {
                config.search.enabled = !config.search.enabled; save();
            }
            search.tick(client);
            for (int module : new int[] {21, 27, 28}) {
                if (!ModuleKeybinds.consumePress(client, module) || inputBlocked || client.screen != null || client.player == null) continue;
                switch (module) {
                    case 21 -> config.freelook.enabled = !config.freelook.enabled;
                    case 27 -> config.autoReconnect.enabled = !config.autoReconnect.enabled;
                    default -> { if (config.autoEat.enabled || !guardRefuses(client, "Auto Eat")) config.autoEat.enabled = !config.autoEat.enabled; }
                }
                save();
            }
            freelook.tick(client);
            autoEat.tick(client);
            for (int module = 24; module <= 26; module++) {
                if (!ModuleKeybinds.consumePress(client, module) || inputBlocked || client.screen != null || client.player == null) continue;
                switch (module) {
                    case 24 -> config.hudWidgets.enabled = !config.hudWidgets.enabled;
                    case 25 -> config.newChunks.enabled = !config.newChunks.enabled;
                    default -> config.stashFinder.enabled = !config.stashFinder.enabled;
                }
                save();
            }
            if (ModuleKeybinds.consumePress(client, 29) && !inputBlocked && client.screen == null && client.player != null)
                client.setScreen(new dev.donut.client.gui.StructureFinderScreen(null, config, this::save));
            while (stashMenu.consumeClick()) if (!inputBlocked && client.screen == null && client.player != null)
                client.setScreen(new dev.donut.client.gui.StashScreen(null, config, this::save));
            newChunks.tick(client);
            stashFinder.tick(client);
        });
        ScreenEvents.AFTER_INIT.register(autoReconnect::screenInit);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> autoReconnect.joined(client));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Donut"), button ->
                        client.setScreen(new DonutScreen(config, this::save)))
                        .bounds(width - 66, height - 28, 58, 20).build());
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "status"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            dev.donut.client.gui.HudPanel.newFrame();
            if (!config.showHud || client.player == null || client.screen != null || client.options.hideGui) {
                ModuleArrayList.reset();
                return;
            }
            ModuleArrayList.render(graphics, client.font, client.getWindow().getGuiScaledWidth(), config, freecam.isActive());
            AutoBuilderHud.render(graphics, client.font, client.getWindow().getGuiScaledHeight(), config);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "freecam_readout"), (graphics, delta) ->
                dev.donut.client.gui.FreecamHud.render(graphics, config, freecam));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "progress_bar"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!config.progressBar.enabled || client.player == null || client.screen != null || client.options.hideGui) return;
            ProgressBarHud.render(graphics, config, config.progressBar.completedStages);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "base_finder_inspect"),
                (graphics, delta) -> BaseFinderPanel.render(graphics, config, chunkScanner));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "sound_alerts"), (graphics, delta) ->
                dev.donut.client.gui.SoundAlertHud.render(graphics, config, soundDetector.alerts()));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "block_esp"), (graphics, delta) ->
                BlockEspHud.render(graphics, config, blockEsp));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "coordinates"), (graphics, delta) ->
                dev.donut.client.gui.CoordinatesHud.render(graphics, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "waypoint_labels"), (graphics, delta) ->
                dev.donut.client.gui.WaypointLabels.render(graphics, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "durability_alert"), (graphics, delta) ->
                dev.donut.client.gui.DurabilityAlertHud.render(graphics, config, durabilityAlert));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "auto_miner"), (graphics, delta) ->
                dev.donut.client.gui.AutoMinerHud.render(graphics, config, autoMiner));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "auto_eat"), (graphics, delta) ->
                dev.donut.client.gui.AutoEatHud.render(graphics, config, autoEat));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "storage_esp"), (graphics, delta) ->
                dev.donut.client.gui.StorageEspHud.render(graphics, config, storageEsp));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "staff_detector"), (graphics, delta) ->
                dev.donut.client.gui.StaffDetectorHud.render(graphics, config, staffDetector));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "search"), (graphics, delta) ->
                dev.donut.client.gui.SearchHud.render(graphics, config, search));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "player_esp_tags"), (graphics, delta) ->
                dev.donut.client.gui.PlayerEspLabels.render(graphics, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud_widgets"), (graphics, delta) ->
                dev.donut.client.gui.HudWidgetsHud.render(graphics, config));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { playerEsp.disconnect(); search.disconnect(); staffDetector.disconnect(); blockEsp.disconnect(); storageEsp.disconnect(); zoom.reset(); durabilityAlert.clear(); soundDetector.disconnect(); chunkScanner.clear(); amethystScanner.clear(); lightDebug.clear(); freecam.disconnect(); fly.clientDisconnected(handler, client); autoBuilder.disconnect(); autoMiner.disconnect(); freelook.disconnect(); autoEat.disconnect(); newChunks.disconnect(); stashFinder.disconnect(); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ProgressTimers.captureAndPause(config);
            if (structureFinder != null) structureFinder.close();
            fullbright.close();
            freecam.disable();
            fly.disable();
            autoBuilder.disconnect();
            autoMiner.stop(client, null);
            freelook.stop(client);
            save();
            lightDebug.close();
            blockEsp.close();
            storageEsp.clear();
            playerEsp.clear();
            search.clear();
            newChunks.clear();
            stashFinder.disconnect();
            soundDetector.disconnect();
            staffDetector.disconnect();
            renderer.close();
        });
    }

    private KeyMapping bind(String name, int key, KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.donut." + name, InputConstants.Type.KEYSYM, key, category));
    }

    private void save() { store.save(config); }

    /** Stops the Blatant modules where they are not allowed (see BlatantGuard), saying which one and why. */
    private void enforceBlatantGuard(net.minecraft.client.Minecraft client) {
        var place = dev.donut.client.guard.BlatantGuard.current(client);
        if (dev.donut.client.guard.BlatantGuard.allowed(config.blatantGuard, place)) return;
        String stopped = null;
        FlyController fly = FlyController.getInstance();
        if (fly != null && fly.isActive()) { fly.setEnabled(false); stopped = "Fly"; }
        if (config.autoMiner.enabled) { config.autoMiner.enabled = false; stopped = "Auto Miner"; }
        if (config.autoEat.enabled) { config.autoEat.enabled = false; stopped = "Auto Eat"; }
        AutoBuilderController builder = AutoBuilderController.get();
        if (builder != null && builder.isRunning()) { builder.block(dev.donut.client.guard.BlatantGuard.blockedMessage("AutoBuilder", place)); stopped = "AutoBuilder"; }
        if (stopped == null) return;
        save();
        if (client.player != null)
            client.player.displayClientMessage(Component.literal(dev.donut.client.guard.BlatantGuard.blockedMessage(stopped, place)), true);
    }

    /** For a shortcut that would switch a Blatant module on: true (with a local notice) when the guard says no. */
    private boolean guardRefuses(net.minecraft.client.Minecraft client, String module) {
        var place = dev.donut.client.guard.BlatantGuard.current(client);
        if (dev.donut.client.guard.BlatantGuard.allowed(config.blatantGuard, place)) return false;
        if (client.player != null)
            client.player.displayClientMessage(Component.literal(dev.donut.client.guard.BlatantGuard.blockedMessage(module, place)), true);
        return true;
    }

    private void enforceKillSwitch(net.minecraft.client.Minecraft client) {
        if (!expired()) return;
        if (disableEverything()) save();
        if (!expiryNoticeShown && client.player != null) {
            expiryNoticeShown = true;
            // Shown only on this screen; nothing is sent to the server.
            client.player.displayClientMessage(Component.literal("Donut Client: this test build has expired ("
                    + activeKillSwitch.build().hours() / 24 + " days after its first run). Every module is off; ask for a newer build."), false);
        }
    }

    /** Switches every module off. Returns whether anything was on. */
    private boolean disableEverything() {
        boolean changed = false;
        FreecamController freecam = FreecamController.getInstance();
        if (freecam != null && freecam.isActive()) { freecam.setEnabled(false); changed = true; }
        FlyController fly = FlyController.getInstance();
        if (fly != null && fly.isActive()) { fly.setEnabled(false); changed = true; }
        AutoBuilderController builder = AutoBuilderController.get();
        if (builder != null && (builder.isRunning() || builder.status() == AutoBuilderController.Status.PAUSED)) { builder.cancel(); changed = true; }
        if (dev.donut.client.spectrebuilder.CreativeBuilder.INSTANCE.running()) { dev.donut.client.spectrebuilder.CreativeBuilder.INSTANCE.stop(); changed = true; }
        ClientConfig c = config;
        boolean[] on = {c.chunkFinder.enabled, c.fullbright.enabled, c.progressBar.enabled, c.nameHider.enabled, c.hotbar.enabled,
                c.amethyst.enabled, c.lightDebug.enabled, c.soundDetector.enabled, c.blockEsp.enabled, c.staffDetector.enabled,
                c.storageEsp.enabled, c.coordinates.enabled, c.waypoints.enabled, c.zoom.enabled, c.durabilityAlert.enabled,
                c.autoMiner.enabled, c.freelook.enabled, c.playerEsp.enabled, c.search.enabled, c.hudWidgets.enabled,
                c.newChunks.enabled, c.stashFinder.enabled, c.autoReconnect.enabled, c.autoEat.enabled};
        for (boolean value : on) changed |= value;
        c.chunkFinder.enabled = c.fullbright.enabled = c.progressBar.enabled = c.nameHider.enabled = c.hotbar.enabled = false;
        c.amethyst.enabled = c.lightDebug.enabled = c.soundDetector.enabled = c.blockEsp.enabled = c.staffDetector.enabled = false;
        c.storageEsp.enabled = c.coordinates.enabled = c.waypoints.enabled = c.zoom.enabled = c.durabilityAlert.enabled = false;
        c.autoMiner.enabled = c.freelook.enabled = c.playerEsp.enabled = c.search.enabled = c.hudWidgets.enabled = false;
        c.newChunks.enabled = c.stashFinder.enabled = c.autoReconnect.enabled = c.autoEat.enabled = false;
        return changed;
    }
}
