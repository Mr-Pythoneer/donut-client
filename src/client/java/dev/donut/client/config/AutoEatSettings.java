package dev.donut.client.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto Eat preferences. Food is chosen from the hotbar only and eaten with ordinary item use. {@code blocked} maps a
 * food's item id (without the {@code minecraft:} namespace) to whether Auto Eat must never eat it.
 */
public final class AutoEatSettings {
    public static final int MIN_HUNGER = 1, MAX_HUNGER = 19, DEFAULT_HUNGER = 14;
    public static final int MIN_HEALTH = 1, MAX_HEALTH = 19, DEFAULT_HEALTH = 10;
    /** At or below this hunger Auto Eat interrupts Auto Miner instead of waiting for it to finish a target. */
    public static final int CRITICAL_HUNGER = 6;

    /** A food with a "never eat" toggle in the inspector. */
    public record Food(String id, String label, boolean blockedByDefault) { }

    public static final List<Food> FOODS = List.of(
            new Food("rotten_flesh", "Rotten flesh", true),
            new Food("spider_eye", "Spider eye", true),
            new Food("poisonous_potato", "Poisonous potato", true),
            new Food("pufferfish", "Pufferfish", true),
            new Food("chorus_fruit", "Chorus fruit", true),
            new Food("suspicious_stew", "Suspicious stew", true),
            new Food("golden_apple", "Golden apple", true),
            new Food("enchanted_golden_apple", "Enchanted golden apple", true),
            new Food("chicken", "Raw chicken", false));

    public boolean enabled = false;
    public boolean favorite = false;
    /** Eat when hunger (0-20) is at or below this. */
    public int hungerThreshold = DEFAULT_HUNGER;
    /** Also eat when health is low and hunger is below full, so natural regeneration can run. */
    public boolean eatWhenHurt = true;
    /** Health (in half-hearts) at or below which "eat when hurt" applies. */
    public int healthThreshold = DEFAULT_HEALTH;
    public boolean showHud = true;
    public Map<String, Boolean> blocked = defaultBlocked();

    public static Map<String, Boolean> defaultBlocked() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (Food food : FOODS) map.put(food.id(), food.blockedByDefault());
        return map;
    }

    public static Food food(String id) {
        for (Food food : FOODS) if (food.id().equals(id)) return food;
        return null;
    }

    /** True when the item id (with or without {@code minecraft:}) must not be eaten. */
    public boolean isBlocked(String id) {
        if (id == null) return true;
        String key = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        Boolean value = blocked == null ? null : blocked.get(key);
        if (value != null) return value;
        Food food = food(key);
        return food != null && food.blockedByDefault();
    }

    public void sanitize() {
        hungerThreshold = Math.clamp(hungerThreshold, MIN_HUNGER, MAX_HUNGER);
        healthThreshold = Math.clamp(healthThreshold, MIN_HEALTH, MAX_HEALTH);
        if (blocked == null) blocked = new LinkedHashMap<>();
        blocked.keySet().removeIf(id -> id == null || food(id) == null);
        blocked.replaceAll((id, value) -> value == null ? food(id).blockedByDefault() : value);
        for (Food food : FOODS) blocked.putIfAbsent(food.id(), food.blockedByDefault());
    }
}
