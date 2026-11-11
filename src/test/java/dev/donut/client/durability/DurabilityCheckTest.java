package dev.donut.client.durability;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.config.ConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurabilityCheckTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static ItemStack worn(net.minecraft.world.item.Item item, int left) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() - left);
        return stack;
    }

    @Test void percentLeftIgnoresItemsWithoutDurability() {
        assertEquals(-1, DurabilityCheck.percentLeft(new ItemStack(Items.DIRT)));
        assertEquals(-1, DurabilityCheck.percentLeft(ItemStack.EMPTY));
        assertEquals(-1, DurabilityCheck.percentLeft(null));
        assertEquals(100, DurabilityCheck.percentLeft(new ItemStack(Items.DIAMOND_PICKAXE)), 1.0e-9);
        ItemStack pick = worn(Items.DIAMOND_PICKAXE, 156); // 1561 max: about 10%
        assertEquals(100.0 * 156 / 1561, DurabilityCheck.percentLeft(pick), 1.0e-9);
    }

    @Test void scanReportsOnlyDamageableItemsAtOrBelowThreshold() {
        var slots = new LinkedHashMap<String, ItemStack>();
        slots.put("Main hand", worn(Items.DIAMOND_PICKAXE, 100));   // ~6%
        slots.put("Offhand", new ItemStack(Items.TORCH));            // no durability
        slots.put("Helmet", worn(Items.IRON_HELMET, 165));          // exactly 100%
        slots.put("Boots", worn(Items.IRON_BOOTS, 19));             // 195 max: ~9.7%
        slots.put("Chestplate", worn(Items.IRON_CHESTPLATE, 24));   // 240 max: exactly 10%
        var low = DurabilityCheck.scan(slots, 10);
        assertEquals(java.util.List.of("Main hand", "Boots", "Chestplate"), low.stream().map(DurabilityCheck.Low::slot).toList());
        assertEquals(100, low.getFirst().left());
        assertEquals(1561, low.getFirst().max());
        assertTrue(DurabilityCheck.scan(slots, 1).isEmpty());
    }

    @Test void settingsStartDisabledAndClampThreshold() throws Exception {
        Path file = directory.resolve("settings.json");
        Files.writeString(file, "{\"durabilityAlert\":{\"thresholdPercent\":90,\"enabled\":false}}");
        var loaded = new ConfigStore(file).load().durabilityAlert;
        assertFalse(loaded.enabled);
        assertEquals(50, loaded.thresholdPercent);
        Files.writeString(file, "{}");
        loaded = new ConfigStore(file).load().durabilityAlert;
        assertEquals(10, loaded.thresholdPercent);
        assertTrue(loaded.includeArmor && loaded.includeOffhand && loaded.sound);
    }
}
