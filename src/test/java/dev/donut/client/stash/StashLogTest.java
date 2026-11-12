package dev.donut.client.stash;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.StashFinderSettings;
import dev.donut.client.storageesp.StorageKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StashLogTest {
    private static final String SERVER = "mp:play.example.net", OVERWORLD = "minecraft:overworld";
    private final StashFinderSettings settings = new StashFinderSettings();

    private static Map<StorageKind, Integer> counts(Object... pairs) {
        Map<StorageKind, Integer> counts = new EnumMap<>(StorageKind.class);
        for (int i = 0; i < pairs.length; i += 2) counts.put((StorageKind) pairs[i], (Integer) pairs[i + 1]);
        return counts;
    }

    private StashLog.Result observe(StashLog log, String server, int cx, int cz, Map<StorageKind, Integer> counts, long now) {
        return log.observe(server, OVERWORLD, cx, cz, counts, cx * 16 + 8, 64, cz * 16 + 8, now, settings.threshold, settings::weight);
    }

    @Test void weightedThresholdDecidesWhatIsLogged() {
        settings.sanitize();
        assertEquals(10, StashLog.score(counts(StorageKind.CHESTS, 10, StorageKind.HOPPERS, 30), settings::weight),
                "Hoppers weigh 0 by default");
        var log = new StashLog();
        assertEquals(StashLog.Result.IGNORED, observe(log, SERVER, 0, 0, counts(StorageKind.CHESTS, 7), 1));
        assertEquals(StashLog.Result.IGNORED, observe(log, SERVER, 0, 0, counts(StorageKind.FURNACES, 40), 1));
        assertEquals(StashLog.Result.LOGGED, observe(log, SERVER, 0, 0, counts(StorageKind.CHESTS, 5, StorageKind.SHULKER_BOXES, 3), 2));
        settings.weights.put(StorageKind.FURNACES.id, 1);
        assertEquals(StashLog.Result.LOGGED, observe(log, SERVER, 5, 5, counts(StorageKind.FURNACES, 8), 3), "Weights are configurable");
        assertEquals(2, log.size());
    }

    @Test void aChunkIsLoggedOnceThenRefreshed() {
        var log = new StashLog();
        assertEquals(StashLog.Result.LOGGED, observe(log, SERVER, 3, -2, counts(StorageKind.CHESTS, 8), 100));
        assertEquals(StashLog.Result.IGNORED, observe(log, SERVER, 3, -2, counts(StorageKind.CHESTS, 8), 200), "Same contents");
        assertEquals(StashLog.Result.UPDATED, observe(log, SERVER, 3, -2, counts(StorageKind.CHESTS, 10, StorageKind.BARRELS, 2), 300));
        assertEquals(StashLog.Result.IGNORED, observe(log, SERVER, 3, -2, counts(StorageKind.CHESTS, 1), 400),
                "Falling below the threshold never drops a logged stash");
        StashEntry entry = log.find(SERVER, OVERWORLD, 3, -2);
        assertEquals(10, entry.count(StorageKind.CHESTS));
        assertEquals(2, entry.count(StorageKind.BARRELS));
        assertEquals(12, entry.total);
        assertEquals(12, entry.score);
        assertEquals(100, entry.foundAt);
        assertEquals(300, entry.updatedAt);
        assertEquals("10 chests, 2 barrels", entry.summary());
        // Another server or dimension is a separate log entry.
        assertEquals(StashLog.Result.LOGGED, observe(log, "sp:other", 3, -2, counts(StorageKind.CHESTS, 8), 500));
        assertEquals(StashLog.Result.LOGGED, log.observe(SERVER, "minecraft:the_nether", 3, -2, counts(StorageKind.CHESTS, 8),
                0, 0, 0, 600, 8, settings::weight));
        assertEquals(3, log.size());
        assertEquals(2, log.forServer(SERVER).size());
        assertEquals("minecraft:the_nether", log.forServer(SERVER).getFirst().dimension, "Newest first");
        assertTrue(log.remove(entry));
        assertNull(log.find(SERVER, OVERWORLD, 3, -2));
    }

    @Test void theLogIsBounded() {
        var log = new StashLog();
        for (int i = 0; i < StashLog.MAX_ENTRIES + 5; i++) observe(log, SERVER, i, 0, counts(StorageKind.CHESTS, 9), i);
        assertEquals(StashLog.MAX_ENTRIES, log.size());
        assertNull(log.find(SERVER, OVERWORLD, 0, 0), "The oldest entries go first");
        assertNotNull(log.find(SERVER, OVERWORLD, StashLog.MAX_ENTRIES + 4, 0));
    }

    @Test void exportRoundTripsThroughCsvAndJson() {
        var log = new StashLog();
        observe(log, SERVER, 1, 2, counts(StorageKind.CHESTS, 9, StorageKind.ENDER_CHESTS, 1, StorageKind.HOPPERS, 4), 1_700_000_000_123L);
        observe(log, "mp:\"odd\",name", -7, 4, counts(StorageKind.SHULKER_BOXES, 12), 1_700_000_100_000L);
        List<StashEntry> entries = log.all();

        String csv = StashLog.toCsv(entries);
        assertTrue(csv.startsWith("server,dimension,chunk_x,chunk_z,x,y,z,score,total,found_at,chests,"), csv);
        assertTrue(csv.contains("\"mp:\"\"odd\"\",name\""), "Fields with commas and quotes are quoted");
        assertTrue(csv.contains("2023-11-14T22:13:20.123Z"), csv);
        assertEntriesEqual(entries, StashLog.fromCsv(csv));
        assertEntriesEqual(entries, StashLog.fromJson(StashLog.toJson(entries)));
        assertEquals(List.of(), StashLog.fromJson("not json"));

        var reloaded = new StashLog();
        reloaded.load(StashLog.fromJson(StashLog.toJson(List.of(entries.getFirst(), entries.getFirst(), entries.get(1)))));
        assertEquals(2, reloaded.size(), "Duplicates are dropped on load");
    }

    private static void assertEntriesEqual(List<StashEntry> expected, List<StashEntry> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            StashEntry a = expected.get(i), b = actual.get(i);
            assertEquals(a.server, b.server);
            assertEquals(a.dimension, b.dimension);
            assertEquals(List.of(a.chunkX, a.chunkZ, a.x, a.y, a.z, a.score, a.total), List.of(b.chunkX, b.chunkZ, b.x, b.y, b.z, b.score, b.total));
            assertEquals(a.foundAt, b.foundAt);
            assertEquals(a.counts, b.counts);
        }
    }

    @Test void countingSkipsVehicles() {
        var counted = StashLog.count(List.of(StorageKind.CHESTS, StorageKind.CHESTS, StorageKind.STORAGE_ENTITIES, StorageKind.BARRELS));
        assertEquals(Map.of(StorageKind.CHESTS, 2, StorageKind.BARRELS, 1), counted);
    }
}
