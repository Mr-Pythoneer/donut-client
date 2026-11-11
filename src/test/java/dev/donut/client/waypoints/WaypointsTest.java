package dev.donut.client.waypoints;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WaypointsTest {
    private static final String MP = "mp:donutsmp.net", OTHER = "mp:other.net";

    private static Waypoint at(String name, int x, int y, int z, String dimension, String server) {
        return new Waypoint(name, x, y, z, dimension, server);
    }

    @Test void serverKeysPreferTheMultiplayerAddress() {
        assertEquals("mp:donutsmp.net", Waypoints.serverKey("  DonutSMP.net ", "My World"));
        assertEquals("mp:play.example.com:25566", Waypoints.serverKey("Play.Example.com:25566", null));
        assertEquals("sp:My World", Waypoints.serverKey("", " My World "));
        assertEquals("sp:My World", Waypoints.serverKey(null, "My World"));
        assertEquals("unknown", Waypoints.serverKey(" ", " "));
        assertEquals("unknown", Waypoints.serverKey(null, null));
    }

    @Test void onlyTheOverworldAndNetherAreLinked() {
        assertEquals(Waypoints.NETHER, Waypoints.linkedDimension(Waypoints.OVERWORLD));
        assertEquals(Waypoints.OVERWORLD, Waypoints.linkedDimension(Waypoints.NETHER));
        assertNull(Waypoints.linkedDimension(Waypoints.END));
        assertNull(Waypoints.linkedDimension("custom:mining"));
        assertNull(Waypoints.linkedDimension(null));
    }

    @Test void horizontalCoordinatesScaleByEightBothWays() {
        assertEquals(12.5, Waypoints.convert(Waypoints.OVERWORLD, Waypoints.NETHER, 100));
        assertEquals(-12.5, Waypoints.convert(Waypoints.OVERWORLD, Waypoints.NETHER, -100));
        assertEquals(800, Waypoints.convert(Waypoints.NETHER, Waypoints.OVERWORLD, 100));
        assertEquals(-8008, Waypoints.convert(Waypoints.NETHER, Waypoints.OVERWORLD, -1001));
        assertEquals(-1001, Waypoints.convert(Waypoints.OVERWORLD, Waypoints.OVERWORLD, -1001));
        assertEquals(64, Waypoints.convert(Waypoints.END, Waypoints.NETHER, 64));
        assertEquals(-7, Waypoints.convert(Waypoints.OVERWORLD, Waypoints.END, -7));
    }

    @Test void visibleInFiltersByServerDimensionAndVisibility() {
        var home = at("Home", 800, 70, -1600, Waypoints.OVERWORLD, MP);
        var hidden = at("Hidden", 0, 64, 0, Waypoints.OVERWORLD, MP);
        hidden.visible = false;
        var hub = at("Hub", 10, 40, -20, Waypoints.NETHER, MP);
        var end = at("City", 1000, 60, 1000, Waypoints.END, MP);
        var elsewhere = at("Elsewhere", 5, 5, 5, Waypoints.OVERWORLD, OTHER);
        List<Waypoint> all = new ArrayList<>(List.of(home, hidden, hub, end, elsewhere));
        all.add(null);

        var overworld = Waypoints.visibleIn(all, MP, Waypoints.OVERWORLD, false);
        assertEquals(List.of(new Waypoints.Shown(home, 800, 70, -1600, false)), overworld);

        var linked = Waypoints.visibleIn(all, MP, Waypoints.OVERWORLD, true);
        assertEquals(2, linked.size());
        assertEquals(new Waypoints.Shown(hub, 80, 40, -160, true), linked.get(1));

        var nether = Waypoints.visibleIn(all, MP, Waypoints.NETHER, true);
        assertEquals(List.of(new Waypoints.Shown(home, 100, 70, -200, true), new Waypoints.Shown(hub, 10, 40, -20, false)), nether);

        var inEnd = Waypoints.visibleIn(all, MP, Waypoints.END, true);
        assertEquals(List.of(new Waypoints.Shown(end, 1000, 60, 1000, false)), inEnd);

        assertTrue(Waypoints.visibleIn(all, "mp:nowhere", Waypoints.OVERWORLD, true).isEmpty());
        assertTrue(Waypoints.visibleIn(null, MP, Waypoints.OVERWORLD, true).isEmpty());
        assertTrue(Waypoints.visibleIn(all, MP, null, true).isEmpty());
    }

    @Test void nextNameCountsUpPerServer() {
        List<Waypoint> all = new ArrayList<>();
        assertEquals("Waypoint", Waypoints.nextName(all, MP, "Waypoint"));
        assertEquals("Waypoint", Waypoints.nextName(all, MP, "  "));
        assertEquals("Waypoint", Waypoints.nextName(all, MP, null));
        all.add(at("Waypoint", 0, 0, 0, Waypoints.OVERWORLD, MP));
        assertEquals("Waypoint 2", Waypoints.nextName(all, MP, "Waypoint"));
        assertEquals("waypoint 2", Waypoints.nextName(all, MP, "waypoint"), "Duplicates are case-insensitive; casing is kept");
        assertEquals("Waypoint", Waypoints.nextName(all, OTHER, "Waypoint"), "Names are only unique per server");
        all.add(at("Waypoint 2", 0, 0, 0, Waypoints.NETHER, MP));
        all.add(at("Waypoint 3", 0, 0, 0, Waypoints.END, MP));
        assertEquals("Waypoint 4", Waypoints.nextName(all, MP, "Waypoint"));
        assertEquals("Base", Waypoints.nextName(all, MP, " Base "));

        String longName = "x".repeat(Waypoint.MAX_NAME);
        all.add(at(longName, 0, 0, 0, Waypoints.OVERWORLD, MP));
        String next = Waypoints.nextName(all, MP, longName);
        assertEquals(Waypoint.MAX_NAME, next.length());
        assertTrue(next.endsWith(" 2"));
    }

    @Test void nearestFirstSortsACopy() {
        var far = new Waypoints.Shown(at("Far", 100, 64, 0, Waypoints.OVERWORLD, MP), 100, 64, 0, false);
        var near = new Waypoints.Shown(at("Near", 3, 64, 0, Waypoints.OVERWORLD, MP), 3, 64, 0, false);
        var mid = new Waypoints.Shown(at("Mid", -20, 64, 0, Waypoints.OVERWORLD, MP), -20, 64, 0, true);
        List<Waypoints.Shown> input = List.of(far, near, mid);
        assertEquals(List.of(near, mid, far), Waypoints.nearestFirst(input, new Vec3(0, 64, 0)));
        assertEquals(List.of(far, near, mid), input);
        assertEquals(List.of(far, near, mid), Waypoints.nearestFirst(input, new Vec3(90, 64, 0)));
    }

    @Test void listOrderPutsTheCurrentDimensionFirst() {
        var netherA = at("Alpha", 0, 64, 0, Waypoints.NETHER, MP);
        var far = at("Far", 500, 64, 0, Waypoints.OVERWORLD, MP);
        var near = at("Near", 5, 64, 0, Waypoints.OVERWORLD, MP);
        var end = at("Zeta", 0, 64, 0, Waypoints.END, MP);
        var other = at("Other", 0, 64, 0, Waypoints.OVERWORLD, OTHER);
        var hidden = at("Hidden", 50, 64, 0, Waypoints.OVERWORLD, MP);
        hidden.visible = false;
        var all = List.of(netherA, far, near, end, other, hidden);
        assertEquals(List.of(near, hidden, far, end, netherA), Waypoints.listOrder(all, MP, Waypoints.OVERWORLD, Vec3.ZERO));
        assertEquals(List.of(far, hidden, near, end, netherA), Waypoints.listOrder(all, MP, Waypoints.OVERWORLD, null));
    }

    @Test void presetColorsCycleAndCustomColorsRestart() {
        int[] presets = WaypointController.PRESET_COLORS;
        assertEquals(presets[1], WaypointController.nextPreset(presets[0]));
        assertEquals(presets[0], WaypointController.nextPreset(presets[presets.length - 1]));
        assertEquals(presets[0], WaypointController.nextPreset(0x123456));
        assertEquals(0x80FF0000, Waypoints.argb(0xABFF0000, 0x80));
        assertEquals(0xFF5BC0EB, Waypoints.argb(0x5BC0EB, 999));
    }

    @Test void dimensionLabelsAreShort() {
        assertEquals("Overworld", Waypoints.dimensionLabel(Waypoints.OVERWORLD));
        assertEquals("Nether", Waypoints.dimensionLabel(Waypoints.NETHER));
        assertEquals("End", Waypoints.dimensionLabel(Waypoints.END));
        assertEquals("mining", Waypoints.dimensionLabel("custom:mining"));
    }
}
