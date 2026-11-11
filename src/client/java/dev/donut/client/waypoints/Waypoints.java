package dev.donut.client.waypoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure waypoint math: server keys, Nether/Overworld linking and filtering. Nothing here touches the game. */
public final class Waypoints {
    public static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether", END = "minecraft:the_end";
    public static final double NETHER_SCALE = 8;

    private Waypoints() { }

    /**
     * One waypoint as it should appear in the current dimension. {@code x/y/z} are block coordinates (converted when
     * {@code linked}); {@link #center()} is the middle of that block.
     */
    public record Shown(Waypoint waypoint, double x, double y, double z, boolean linked) {
        public Vec3 center() { return new Vec3(x + 0.5, y + 0.5, z + 0.5); }
        public BlockPos blockPos() { return BlockPos.containing(x, y, z); }
        public double distanceSq(Vec3 from) { return center().distanceToSqr(from); }
    }

    /** {@code "mp:<address>"} in multiplayer, {@code "sp:<level>"} in singleplayer, otherwise {@code "unknown"}. */
    public static String serverKey(String multiplayerAddress, String singleplayerLevelName) {
        if (multiplayerAddress != null && !multiplayerAddress.isBlank())
            return "mp:" + multiplayerAddress.strip().toLowerCase(Locale.ROOT);
        if (singleplayerLevelName != null && !singleplayerLevelName.isBlank()) return "sp:" + singleplayerLevelName.strip();
        return "unknown";
    }

    /** The Overworld and the Nether are linked by portals; every other dimension has no partner. */
    public static String linkedDimension(String dimension) {
        if (OVERWORLD.equals(dimension)) return NETHER;
        if (NETHER.equals(dimension)) return OVERWORLD;
        return null;
    }

    /** Converts a horizontal coordinate between dimensions: /8 into the Nether, x8 out of it, unchanged otherwise. */
    public static double convert(String from, String to, double coordinate) {
        if (OVERWORLD.equals(from) && NETHER.equals(to)) return coordinate / NETHER_SCALE;
        if (NETHER.equals(from) && OVERWORLD.equals(to)) return coordinate * NETHER_SCALE;
        return coordinate;
    }

    /** Short dimension label for UI, e.g. "Nether". Unknown ids show their path. */
    public static String dimensionLabel(String dimension) {
        if (dimension == null) return "?";
        return switch (dimension) {
            case OVERWORLD -> "Overworld";
            case NETHER -> "Nether";
            case END -> "End";
            default -> {
                int colon = dimension.indexOf(':');
                yield colon >= 0 ? dimension.substring(colon + 1) : dimension;
            }
        };
    }

    /**
     * Visible waypoints of {@code server} for a player in {@code dimension}: own-dimension ones at their coordinates,
     * then (if {@code includeLinked}) linked-dimension ones converted horizontally, with Y kept.
     */
    public static List<Shown> visibleIn(List<Waypoint> all, String server, String dimension, boolean includeLinked) {
        List<Shown> shown = new ArrayList<>();
        if (all == null || server == null || dimension == null) return shown;
        String linked = includeLinked ? linkedDimension(dimension) : null;
        for (Waypoint waypoint : all) {
            if (waypoint == null || !waypoint.visible || !server.equals(waypoint.server)) continue;
            if (dimension.equals(waypoint.dimension))
                shown.add(new Shown(waypoint, waypoint.x, waypoint.y, waypoint.z, false));
            else if (linked != null && linked.equals(waypoint.dimension))
                shown.add(new Shown(waypoint, convert(linked, dimension, waypoint.x), waypoint.y,
                        convert(linked, dimension, waypoint.z), true));
        }
        return shown;
    }

    /** {@code base} if no waypoint on {@code server} uses it (case-insensitive), else "base 2", "base 3", ... */
    public static String nextName(List<Waypoint> all, String server, String base) {
        String clean = Waypoint.cleanName(base);
        if (clean == null) clean = Waypoint.DEFAULT_NAME;
        Set<String> used = new HashSet<>();
        if (all != null) for (Waypoint waypoint : all)
            if (waypoint != null && waypoint.name != null && (server == null || server.equals(waypoint.server)))
                used.add(waypoint.name.strip().toLowerCase(Locale.ROOT));
        if (!used.contains(clean.toLowerCase(Locale.ROOT))) return clean;
        for (int n = 2; ; n++) {
            String suffix = " " + n;
            String stem = clean.length() + suffix.length() > Waypoint.MAX_NAME
                    ? clean.substring(0, Waypoint.MAX_NAME - suffix.length()).strip() : clean;
            String candidate = stem + suffix;
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
    }

    /** A sorted copy, nearest block center first. */
    public static List<Shown> nearestFirst(List<Shown> shown, Vec3 from) {
        List<Shown> sorted = new ArrayList<>(shown);
        sorted.sort(Comparator.comparingDouble(s -> s.distanceSq(from)));
        return sorted;
    }

    /**
     * Every waypoint of {@code server} (visible or not) for the management list: {@code dimension} first, nearest to
     * {@code from} first when it is known, then other dimensions grouped by id, each by name.
     */
    public static List<Waypoint> listOrder(List<Waypoint> all, String server, String dimension, Vec3 from) {
        List<Waypoint> list = new ArrayList<>();
        if (all == null || server == null) return list;
        for (Waypoint waypoint : all) if (waypoint != null && server.equals(waypoint.server)) list.add(waypoint);
        Comparator<Waypoint> order = Comparator.comparingInt(w -> Objects.equals(w.dimension, dimension) ? 0 : 1);
        order = order.thenComparing(w -> Objects.equals(w.dimension, dimension) ? "" : String.valueOf(w.dimension));
        if (from != null) order = order.thenComparingDouble(w -> Objects.equals(w.dimension, dimension)
                ? Vec3.atCenterOf(w.pos()).distanceToSqr(from) : 0);
        list.sort(order.thenComparing(w -> String.valueOf(w.name).toLowerCase(Locale.ROOT)));
        return list;
    }

    /** A one-block-wide column through the waypoint from {@code minY} to {@code maxY}, for beam rendering. */
    public static AABB beam(Shown shown, double minY, double maxY) {
        double x = Math.floor(shown.x()), z = Math.floor(shown.z());
        return new AABB(x + 0.3, minY, z + 0.3, x + 0.7, maxY, z + 0.7);
    }

    /** ARGB from a 0xRRGGBB color and a 0..255 opacity. */
    public static int argb(int rgb, int opacity) {
        return Math.clamp(opacity, 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}
