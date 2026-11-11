package dev.donut.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.donut.client.waypoints.Waypoint;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WaypointSettingsTest {
    private static final Gson GSON = new GsonBuilder().create();

    @Test void newSettingsStartDisabled() {
        var value = new WaypointSettings();
        assertFalse(value.enabled, "New modules start disabled");
        assertFalse(value.favorite);
        assertTrue(value.waypoints.isEmpty());
        assertEquals(220, value.opacity);
        assertTrue(value.beams && value.labels && value.showDistance && value.showLinkedDimension);
        assertEquals(512, value.labelRange);
        for (String json : new String[] {"{}", "{\"waypoints\":null}"}) {
            var loaded = GSON.fromJson(json, WaypointSettings.class);
            loaded.sanitize();
            assertFalse(loaded.enabled, json);
            assertNotNull(loaded.waypoints, json);
            assertTrue(loaded.waypoints.isEmpty(), json);
        }
    }

    @Test void waypointsSurviveJson() {
        var value = new WaypointSettings();
        value.enabled = value.favorite = true;
        value.beams = value.showLinkedDimension = false;
        value.opacity = 90;
        value.labelRange = 1024;
        var waypoint = new Waypoint("Base", 1200, -40, -3500, "minecraft:the_nether", "mp:donutsmp.net");
        waypoint.color = 0xFA7921;
        waypoint.visible = false;
        waypoint.created = 1_762_000_000_000L;
        value.waypoints.add(waypoint);

        var loaded = GSON.fromJson(GSON.toJson(value), WaypointSettings.class);
        loaded.sanitize();
        assertTrue(loaded.enabled && loaded.favorite);
        assertFalse(loaded.beams || loaded.showLinkedDimension);
        assertEquals(90, loaded.opacity);
        assertEquals(1024, loaded.labelRange);
        assertEquals(1, loaded.waypoints.size());
        var back = loaded.waypoints.getFirst();
        assertEquals("Base", back.name);
        assertEquals(1200, back.x);
        assertEquals(-40, back.y);
        assertEquals(-3500, back.z);
        assertEquals("minecraft:the_nether", back.dimension);
        assertEquals("mp:donutsmp.net", back.server);
        assertEquals(0xFA7921, back.color);
        assertFalse(back.visible);
        assertEquals(1_762_000_000_000L, back.created);
        assertEquals(new net.minecraft.core.BlockPos(1200, -40, -3500), back.pos());
    }

    @Test void sanitizeDropsNullsAndCapsTheList() {
        var value = new WaypointSettings();
        value.waypoints = new ArrayList<>(Arrays.asList(null, new Waypoint(), null));
        value.sanitize();
        assertEquals(1, value.waypoints.size());

        value.waypoints = new ArrayList<>();
        for (int i = 0; i < WaypointSettings.MAX_WAYPOINTS + 25; i++) {
            var waypoint = new Waypoint();
            waypoint.name = "W" + i;
            value.waypoints.add(waypoint);
        }
        assertFalse(value.hasRoom());
        value.sanitize();
        assertEquals(WaypointSettings.MAX_WAYPOINTS, value.waypoints.size());
        assertEquals("W0", value.waypoints.getFirst().name, "The first waypoints are kept");
        assertEquals("W" + (WaypointSettings.MAX_WAYPOINTS - 1), value.waypoints.getLast().name);
        value.waypoints.removeLast();
        assertTrue(value.hasRoom());
    }

    @Test void sanitizeClampsDisplayOptions() {
        var value = new WaypointSettings();
        value.opacity = 999;
        value.labelRange = 1;
        value.sanitize();
        assertEquals(255, value.opacity);
        assertEquals(WaypointSettings.MIN_LABEL_RANGE, value.labelRange);
        value.opacity = -5;
        value.labelRange = 99_999;
        value.sanitize();
        assertEquals(0, value.opacity);
        assertEquals(WaypointSettings.MAX_LABEL_RANGE, value.labelRange);
    }

    @Test void waypointSanitizeRepairsLoadedValues() {
        var waypoint = GSON.fromJson("{\"name\":null,\"x\":2147483647,\"y\":-99999,\"z\":-2147483648,"
                + "\"dimension\":\"  \",\"server\":null,\"color\":-1,\"created\":-3}", Waypoint.class);
        waypoint.sanitize();
        assertEquals("Waypoint", waypoint.name);
        assertEquals(Waypoint.MAX_HORIZONTAL, waypoint.x);
        assertEquals(Waypoint.MIN_Y, waypoint.y);
        assertEquals(-Waypoint.MAX_HORIZONTAL, waypoint.z);
        assertEquals("minecraft:overworld", waypoint.dimension);
        assertEquals("", waypoint.server);
        assertEquals(0xFFFFFF, waypoint.color);
        assertEquals(0, waypoint.created);

        waypoint.name = "   Spawn   ";
        waypoint.y = 99_999;
        waypoint.sanitize();
        assertEquals("Spawn", waypoint.name);
        assertEquals(Waypoint.MAX_Y, waypoint.y);

        waypoint.name = "  " + "a".repeat(40);
        waypoint.sanitize();
        assertEquals("a".repeat(Waypoint.MAX_NAME), waypoint.name);

        waypoint.name = "\t \n";
        waypoint.sanitize();
        assertEquals("Waypoint", waypoint.name);

        var fresh = GSON.fromJson("{\"name\":\"Mine\"}", Waypoint.class);
        fresh.sanitize();
        assertEquals("minecraft:overworld", fresh.dimension);
        assertEquals(0x5BC0EB, fresh.color);
        assertTrue(fresh.visible);
    }
}
