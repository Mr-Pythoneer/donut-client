package dev.donut.client.config;

import dev.donut.client.guard.BlatantGuard;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Multiplayer guard for the Blatant modules (Fly, Auto Miner, AutoBuilder, Auto Eat). They always run in
 * single-player; on a server they run only if that server is in {@link #allowedServers}, unless {@link #blockUnlisted} is off.
 */
public final class BlatantGuardSettings {
    public static final int MAX_SERVERS = 64;

    /**
     * On by default: anti-cheats kick or ban for these modules within seconds (docs/anticheat-results.md). Not named
     * {@code enabled}: settings profiles treat that as a module switch and clear it on import.
     */
    public boolean blockUnlisted = true;
    /** Normalized server addresses (see {@link BlatantGuard#normalize}) where the user said these modules are allowed. */
    public List<String> allowedServers = new ArrayList<>();

    public boolean allows(String address) {
        String key = BlatantGuard.normalize(address);
        return key != null && allowedServers.contains(key);
    }

    public void allow(String address, boolean allowed) {
        String key = BlatantGuard.normalize(address);
        if (key == null) return;
        allowedServers.remove(key);
        if (allowed && allowedServers.size() < MAX_SERVERS) allowedServers.add(key);
    }

    public void sanitize() {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        if (allowedServers != null) for (String value : allowedServers) {
            String key = BlatantGuard.normalize(value);
            if (key != null && clean.size() < MAX_SERVERS) clean.add(key);
        }
        allowedServers = new ArrayList<>(clean);
    }
}
