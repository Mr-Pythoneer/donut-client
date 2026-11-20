/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.autoeat;

import dev.donut.client.config.AutoEatSettings;
import java.util.List;

/** Pure Auto Eat decisions: when to eat, which hotbar food, and whether Auto Miner should be interrupted. */
public final class FoodChoice {
    /** One hotbar slot holding food. {@code id} is the item id without namespace; saturation is the added amount. */
    public record Candidate(int slot, String id, int nutrition, float saturation) { }

    private FoodChoice() { }

    /** Hunger at or below the threshold, or (when enabled) low health with hunger below full. */
    public static boolean shouldEat(AutoEatSettings settings, int food, float health) {
        if (food >= 20) return false;
        if (food <= settings.hungerThreshold) return true;
        return settings.eatWhenHurt && health <= settings.healthThreshold;
    }

    /** At or below this hunger Auto Eat doesn't wait for Auto Miner to finish its target. */
    public static boolean critical(int food) { return food <= AutoEatSettings.CRITICAL_HUNGER; }

    /**
     * The best allowed hotbar slot, or -1. Best = most nutrition plus saturation; ties go to the slot already selected,
     * then the lowest slot. Slots outside 0-8 (inventory) are never chosen.
     */
    public static int best(List<Candidate> candidates, AutoEatSettings settings, int selected) {
        int bestSlot = -1;
        float bestScore = -1;
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.slot() < 0 || candidate.slot() > 8) continue;
            if (settings.isBlocked(candidate.id()) || candidate.nutrition() <= 0 && candidate.saturation() <= 0) continue;
            float score = candidate.nutrition() + candidate.saturation();
            boolean better = score > bestScore
                    || score == bestScore && (candidate.slot() == selected || bestSlot != selected && candidate.slot() < bestSlot);
            if (better) { bestScore = score; bestSlot = candidate.slot(); }
        }
        return bestSlot;
    }
}
