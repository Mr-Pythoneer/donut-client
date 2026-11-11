package dev.donut.client.config;

/** Low-durability warning preferences. Reads only your own held items and armor. */
public final class DurabilityAlertSettings {
    public boolean enabled = false;
    public boolean favorite = false;
    /** Warn at or below this percentage of durability remaining. */
    public int thresholdPercent = 10;
    public boolean includeArmor = true;
    public boolean includeOffhand = true;
    /** A local chime when an item first drops below the threshold. Played only on this client. */
    public boolean sound = true;

    public void sanitize() {
        thresholdPercent = Math.clamp(thresholdPercent, 1, 50);
    }
}
