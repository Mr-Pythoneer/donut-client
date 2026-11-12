package dev.donut.client.config;

import dev.donut.client.automine.OreKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Auto Miner preferences. Ores are keyed by {@link OreKind#id}, like Storage ESP's kinds. The module never
 * resumes by itself: {@code enabled} is cleared when the client starts and whenever the miner stops.
 */
public final class AutoMinerSettings {
    public static final int MIN_RANGE = 8, MAX_RANGE = 64;
    public static final int MIN_HEALTH = 1, MAX_HEALTH = 19;
    public static final int MIN_TOOL_RESERVE = 1, MAX_TOOL_RESERVE = 200;
    public static final int MIN_TUNNEL = 4, MAX_TUNNEL = 128;
    public static final double MIN_TURN = 5, MAX_TURN = 45, DEFAULT_TURN = 18;

    public boolean enabled = false;
    public boolean favorite = false;
    public Map<String, Boolean> ores = defaultOres();
    /** Off: only ores with at least one face open to air or a see-through block. */
    public boolean includeBuried = false;
    /** Search radius in blocks around the player. Only chunks the client already has are read. */
    public int range = 24;
    /** Pause while health (in half-hearts) is at or below this. */
    public int minHealth = 6;
    /** Never mine with a damageable tool that has this many uses or fewer left. */
    public boolean protectTools = true;
    public int toolReserve = 8;
    /** Most blocks dug for one buried target before giving up on it. */
    public int tunnelLimit = 32;
    /** Largest yaw/pitch change per tick, in degrees, so turning looks like a person. */
    public double turnSpeed = DEFAULT_TURN;
    public boolean showHud = true;
    /** Ore forms to mine: the normal (stone, or Nether) form and the deepslate form. */
    public boolean stoneVariants = true;
    public boolean deepslateVariants = true;
    /** Use a Silk Touch tool from the hotbar when there is one, so the ore block itself drops. */
    public boolean silkDeepslate = true;
    public boolean silkStone = false;
    /** When Silk Touch isn't used for an ore Fortune multiplies, use the hotbar pickaxe with the most Fortune. */
    public boolean preferFortune = true;
    /** After each ore, walk over its drops for up to five seconds. */
    public boolean collectDrops = true;

    public static Map<String, Boolean> defaultOres() {
        Map<String, Boolean> ores = new LinkedHashMap<>();
        for (OreKind kind : OreKind.values()) ores.put(kind.id, kind.defaultEnabled);
        return ores;
    }

    public boolean oreEnabled(OreKind kind) {
        if (kind == null) return false;
        Boolean value = ores == null ? null : ores.get(kind.id);
        return value == null ? kind.defaultEnabled : value;
    }

    /** A selected ore in a selected form. */
    public boolean wants(net.minecraft.world.level.block.state.BlockState state) {
        OreKind kind = OreKind.of(state);
        if (kind == null || !oreEnabled(kind)) return false;
        return OreKind.deepslate(state) ? deepslateVariants : stoneVariants;
    }

    public boolean wantsSilk(net.minecraft.world.level.block.state.BlockState state) {
        return OreKind.deepslate(state) ? silkDeepslate : silkStone;
    }

    public void sanitize() {
        range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
        minHealth = Math.clamp(minHealth, MIN_HEALTH, MAX_HEALTH);
        toolReserve = Math.clamp(toolReserve, MIN_TOOL_RESERVE, MAX_TOOL_RESERVE);
        tunnelLimit = Math.clamp(tunnelLimit, MIN_TUNNEL, MAX_TUNNEL);
        turnSpeed = Double.isFinite(turnSpeed) ? Math.clamp(turnSpeed, MIN_TURN, MAX_TURN) : DEFAULT_TURN;
        if (ores == null) ores = new LinkedHashMap<>();
        ores.keySet().removeIf(id -> OreKind.byId(id) == null);
        ores.replaceAll((id, value) -> value == null ? OreKind.byId(id).defaultEnabled : value);
        for (OreKind kind : OreKind.values()) ores.putIfAbsent(kind.id, kind.defaultEnabled);
    }
}
