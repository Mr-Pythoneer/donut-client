package dev.donut.client.durability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Pure low-durability checks over item stacks. */
public final class DurabilityCheck {
    public record Low(String slot, ItemStack stack, int left, int max, double percent) { }

    private DurabilityCheck() { }

    /** Percentage of durability left, or -1 for items without durability. */
    public static double percentLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return -1;
        return 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
    }

    /** Slots (in iteration order) whose item is damageable and at or below the threshold. */
    public static List<Low> scan(Map<String, ItemStack> slots, int thresholdPercent) {
        var low = new ArrayList<Low>();
        for (var entry : slots.entrySet()) {
            ItemStack stack = entry.getValue();
            double percent = percentLeft(stack);
            if (percent < 0 || percent > thresholdPercent) continue;
            low.add(new Low(entry.getKey(), stack, stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage(), percent));
        }
        return List.copyOf(low);
    }
}
