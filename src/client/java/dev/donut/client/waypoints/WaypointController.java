package dev.donut.client.waypoints;

import dev.donut.client.chunkfinder.ChunkFinderScanner;
import dev.donut.client.chunkfinder.SuspectObjects;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.WaypointSettings;
import dev.donut.client.storageesp.StorageEspScanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Client glue for waypoints. Reads only what the client already knows (connection address or singleplayer save folder,
 * dimension, player position); it never sends packets, moves the player or changes the world.
 */
public final class WaypointController {
    /** Colors the management screen cycles through; the first is the default for new waypoints. */
    public static final int[] PRESET_COLORS = {0x5BC0EB, 0x9BC53D, 0xFDE74C, 0xFA7921, 0xE55934, 0xC879FF, 0xFF7EB6, 0xF0F5FA};
    private static WaypointController instance;

    private final ClientConfig config;
    private final Runnable save;
    private ChunkFinderScanner chunkFinder;
    private String checkedWorld;
    private WaypointSettings checkedSettings;

    public WaypointController(ClientConfig config, Runnable save) {
        this.config = config;
        this.save = save == null ? () -> { } : save;
        instance = this;
    }

    public static WaypointController get() { return instance; }

    public WaypointSettings settings() {
        if (config.waypoints == null) config.waypoints = new WaypointSettings();
        return config.waypoints;
    }

    /** Optional: lets "Add at Base Finder focus" read the Base Finder markers. */
    public WaypointController chunkFinder(ChunkFinderScanner scanner) {
        chunkFinder = scanner;
        return this;
    }

    /**
     * Key of the current connection, or {@code "unknown"} with no world loaded. Singleplayer worlds are keyed by their
     * save folder; the first time a world is seen, waypoints saved under its old name key are handed over once.
     */
    public String currentServer() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return Waypoints.serverKey(null, null);
        var server = client.getCurrentServer();
        String address = server == null ? null : server.ip;
        var integrated = client.getSingleplayerServer();
        if (address != null || integrated == null) return Waypoints.serverKey(address, null);
        String key = Waypoints.serverKey(null, Waypoints.worldFolder(integrated.getWorldPath(LevelResource.ROOT)));
        claimLegacy(key, integrated.getWorldData().getLevelName());
        return key;
    }

    /** Runs the legacy hand-over once per world key and settings object (an import replaces the settings). */
    private void claimLegacy(String key, String levelName) {
        var settings = settings();
        if (key.equals(checkedWorld) && settings == checkedSettings) return;
        checkedWorld = key;
        checkedSettings = settings;
        if (Waypoints.claimLegacy(settings.waypoints, key, Waypoints.legacyWorldKey(levelName), settings.claimedWorlds,
                WaypointSettings.MAX_WAYPOINTS) > 0) save();
    }

    /** Dimension id such as {@code minecraft:the_nether}, or {@code null} with no world loaded. */
    public String currentDimension() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.level == null ? null : client.level.dimension().identifier().toString();
    }

    /** Visible waypoints for the current server and dimension (plus linked ones when enabled). */
    public List<Waypoints.Shown> shown() {
        String dimension = currentDimension();
        if (dimension == null) return List.of();
        var settings = settings();
        return Waypoints.visibleIn(settings.waypoints, currentServer(), dimension, settings.showLinkedDimension);
    }

    /** Every waypoint of the current server: current dimension first (nearest first), then the rest by dimension and name. */
    public List<Waypoint> forCurrentServer() {
        String server = currentServer(), dimension = currentDimension();
        Minecraft client = Minecraft.getInstance();
        Vec3 from = client == null || client.player == null ? null : client.player.position();
        return Waypoints.listOrder(settings().waypoints, server, dimension, from);
    }

    /** Adds a waypoint in the current dimension; blank or duplicate names get the next free name. Null when full or no world. */
    public Waypoint add(String name, BlockPos pos) {
        String dimension = currentDimension();
        if (dimension == null || pos == null) return null;
        var settings = settings();
        if (!settings.hasRoom()) return null;
        String server = currentServer();
        String clean = Waypoint.cleanName(name);
        Waypoint waypoint = new Waypoint(Waypoints.nextName(settings.waypoints, server, clean == null ? Waypoint.DEFAULT_NAME : clean),
                pos.getX(), pos.getY(), pos.getZ(), dimension, server);
        waypoint.color = PRESET_COLORS[0];
        waypoint.created = System.currentTimeMillis();
        settings.waypoints.add(waypoint);
        save();
        return waypoint;
    }

    /** Adds a waypoint at the player's block position. */
    public Waypoint addHere() { return addHere(""); }

    public Waypoint addHere(String name) {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.player == null ? null : add(name, client.player.blockPosition());
    }

    /** Nearest Storage ESP target, when that module is running and has found something. */
    public StorageEspScanner.Target storageNearest() {
        StorageEspScanner scanner = StorageEspScanner.get();
        return scanner == null || !scanner.settings().enabled ? null : scanner.snapshot().nearest();
    }

    /** The Base Finder marker the camera is looking at, using the same focus rule as the inspect card. */
    public SuspectObjects.Marker baseFinderFocus() {
        Minecraft client = Minecraft.getInstance();
        if (chunkFinder == null || client == null || client.level == null || !config.chunkFinder.enabled) return null;
        var camera = client.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return null;
        var forward = camera.forwardVector();
        var focus = SuspectObjects.focus(chunkFinder.snapshot(), camera.position(),
                new Vec3(forward.x(), forward.y(), forward.z()), config.chunkFinder.objectRange);
        return focus == null ? null : focus.target();
    }

    public void remove(Waypoint waypoint) {
        if (waypoint != null && settings().waypoints.remove(waypoint)) save();
    }

    /** Renames; a blank name falls back to the next free default name. */
    public void rename(Waypoint waypoint, String name) {
        if (waypoint == null) return;
        List<Waypoint> others = new ArrayList<>(settings().waypoints);
        others.remove(waypoint);
        String clean = Waypoint.cleanName(name);
        waypoint.name = clean == null ? Waypoints.nextName(others, waypoint.server, Waypoint.DEFAULT_NAME) : clean;
        save();
    }

    public void toggleVisible(Waypoint waypoint) {
        if (waypoint == null) return;
        waypoint.visible = !waypoint.visible;
        save();
    }

    /** Moves to the next preset color (a custom color restarts the cycle). */
    public void cycleColor(Waypoint waypoint) {
        if (waypoint == null) return;
        waypoint.color = nextPreset(waypoint.color);
        save();
    }

    public static int nextPreset(int color) {
        for (int i = 0; i < PRESET_COLORS.length; i++)
            if (PRESET_COLORS[i] == (color & 0xFFFFFF)) return PRESET_COLORS[(i + 1) % PRESET_COLORS.length];
        return PRESET_COLORS[0];
    }

    /** Distance from the player to the waypoint's block center, or -1 when it is in another dimension or no world. */
    public double distanceTo(Waypoint waypoint) {
        Minecraft client = Minecraft.getInstance();
        if (waypoint == null || client == null || client.player == null || !waypoint.dimension.equals(currentDimension())) return -1;
        return Math.sqrt(Vec3.atCenterOf(waypoint.pos()).distanceToSqr(client.player.position()));
    }

    public void save() {
        settings().sanitize();
        save.run();
    }
}
