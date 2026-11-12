package dev.donut.client.autoeat;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.autoeat.FoodChoice.Candidate;
import dev.donut.client.config.AutoEatSettings;
import dev.donut.client.config.ClientConfig;
import dev.donut.client.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FoodChoiceTest {
    @TempDir Path directory;

    @Test void eatsAtTheHungerThresholdOrWhenHurt() {
        var settings = new AutoEatSettings();
        assertTrue(FoodChoice.shouldEat(settings, 14, 20));
        assertFalse(FoodChoice.shouldEat(settings, 15, 20));
        assertTrue(FoodChoice.shouldEat(settings, 19, 10), "Low health with hunger below full");
        assertFalse(FoodChoice.shouldEat(settings, 20, 2), "Never eats at full hunger");
        settings.eatWhenHurt = false;
        assertFalse(FoodChoice.shouldEat(settings, 19, 2));
        assertTrue(FoodChoice.shouldEat(settings, 0, 20));
        assertTrue(FoodChoice.critical(6));
        assertFalse(FoodChoice.critical(7));
    }

    @Test void picksTheMostFillingAllowedHotbarFood() {
        var settings = new AutoEatSettings();
        var hotbar = List.of(
                new Candidate(0, "bread", 5, 6f),
                new Candidate(3, "cooked_beef", 8, 12.8f),
                new Candidate(5, "rotten_flesh", 4, 0.8f),
                new Candidate(6, "golden_carrot", 6, 14.4f));
        assertEquals(3, FoodChoice.best(hotbar, settings, 0), "Steak (8 + 12.8) beats golden carrot (6 + 14.4) and bread");
        assertEquals(6, FoodChoice.best(List.of(hotbar.get(0), hotbar.get(2), hotbar.get(3)), settings, 0),
                "Without steak, the golden carrot");
    }

    @Test void blacklistAndHotbarOnly() {
        var settings = new AutoEatSettings();
        var junk = List.of(new Candidate(1, "rotten_flesh", 4, .8f), new Candidate(2, "spider_eye", 2, 3.2f),
                new Candidate(3, "poisonous_potato", 2, 1.2f), new Candidate(4, "pufferfish", 1, .2f),
                new Candidate(5, "chorus_fruit", 4, 2.4f), new Candidate(6, "suspicious_stew", 6, 7.2f),
                new Candidate(7, "minecraft:golden_apple", 4, 9.6f));
        assertEquals(-1, FoodChoice.best(junk, settings, 0), "Blacklisted food is never chosen");
        settings.blocked.put("rotten_flesh", false);
        assertEquals(1, FoodChoice.best(junk, settings, 0));
        // Inventory slots (9+) and invalid slots are ignored even when they hold the best food.
        var mixed = List.of(new Candidate(12, "cooked_beef", 8, 12.8f), new Candidate(-1, "cooked_beef", 8, 12.8f),
                new Candidate(8, "apple", 4, 2.4f));
        assertEquals(8, FoodChoice.best(mixed, new AutoEatSettings(), 0));
        assertEquals(-1, FoodChoice.best(List.of(new Candidate(20, "bread", 5, 6)), new AutoEatSettings(), 0));
    }

    @Test void tiesPreferTheSelectedSlotThenTheLowest() {
        var settings = new AutoEatSettings();
        var same = List.of(new Candidate(2, "bread", 5, 6f), new Candidate(4, "bread", 5, 6f), new Candidate(7, "bread", 5, 6f));
        assertEquals(4, FoodChoice.best(same, settings, 4));
        assertEquals(2, FoodChoice.best(same, settings, 0));
        assertEquals(-1, FoodChoice.best(List.of(), settings, 0));
    }

    @Test void settingsStartDisabledSurviveJsonAndAreRepaired() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String json : new String[] {"{}", "{\"autoEat\":null}", "{\"autoEat\":{\"blocked\":null}}"}) {
            Files.writeString(file, json);
            var value = new ConfigStore(file).load().autoEat;
            assertFalse(value.enabled, "New modules start disabled: " + json);
            assertFalse(value.favorite);
            assertTrue(value.eatWhenHurt && value.showHud);
            assertEquals(14, value.hungerThreshold);
            assertEquals(10, value.healthThreshold);
            for (String id : List.of("rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chorus_fruit", "suspicious_stew"))
                assertTrue(value.isBlocked(id), id + " is blacklisted by default");
            assertFalse(value.isBlocked("chicken"));
            assertFalse(value.isBlocked("bread"), "Foods without a toggle are allowed");
        }
        var config = new ClientConfig();
        config.autoEat.hungerThreshold = 40;
        config.autoEat.healthThreshold = -1;
        config.autoEat.blocked.put("spider_eye", false);
        config.autoEat.blocked.put("future_food", true);
        var store = new ConfigStore(file);
        store.save(config);
        var loaded = store.load().autoEat;
        assertEquals(AutoEatSettings.MAX_HUNGER, loaded.hungerThreshold);
        assertEquals(AutoEatSettings.MIN_HEALTH, loaded.healthThreshold);
        assertFalse(loaded.isBlocked("spider_eye"));
        assertFalse(loaded.blocked.containsKey("future_food"), "Unknown ids are dropped");
        assertEquals(AutoEatSettings.FOODS.size(), loaded.blocked.size());
        loaded.blocked = new LinkedHashMap<>();
        loaded.blocked.put("pufferfish", null);
        loaded.sanitize();
        assertTrue(loaded.isBlocked("pufferfish"), "A null entry falls back to the default");
        assertTrue(loaded.isBlocked(null));
    }
}
