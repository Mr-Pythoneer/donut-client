/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.durability;

import dev.donut.client.config.ClientConfig;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Watches your own held items and armor for low durability. Never touches the inventory or sends anything. */
public final class DurabilityAlertController {
    private static DurabilityAlertController instance;
    private final ClientConfig config;
    private List<DurabilityCheck.Low> low = List.of();
    /** Slot+item pairs already warned about, so the chime plays once per crossing. */
    private final Set<String> warned = new HashSet<>();
    private int age;

    public DurabilityAlertController(ClientConfig config) {
        this.config = config;
        instance = this;
    }

    public static DurabilityAlertController get() { return instance; }
    public List<DurabilityCheck.Low> low() { return low; }

    public void tick(Minecraft client) {
        var settings = config.durabilityAlert;
        if (!settings.enabled || client.player == null) { clear(); return; }
        if (++age < 5) return;
        age = 0;
        var player = client.player;
        var slots = new LinkedHashMap<String, ItemStack>();
        slots.put("Main hand", player.getMainHandItem());
        if (settings.includeOffhand) slots.put("Offhand", player.getOffhandItem());
        if (settings.includeArmor) {
            slots.put("Helmet", player.getItemBySlot(EquipmentSlot.HEAD));
            slots.put("Chestplate", player.getItemBySlot(EquipmentSlot.CHEST));
            slots.put("Leggings", player.getItemBySlot(EquipmentSlot.LEGS));
            slots.put("Boots", player.getItemBySlot(EquipmentSlot.FEET));
        }
        low = DurabilityCheck.scan(slots, settings.thresholdPercent);
        Set<String> now = new HashSet<>();
        boolean fresh = false;
        for (var entry : low) {
            String key = entry.slot() + "|" + entry.stack().getItem() + "|" + entry.max();
            now.add(key);
            if (warned.add(key)) fresh = true;
        }
        warned.retainAll(now); // Repaired or swapped items can warn again later.
        if (fresh && settings.sound)
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.6f));
    }

    public void clear() {
        low = List.of();
        warned.clear();
        age = 0;
    }
}
