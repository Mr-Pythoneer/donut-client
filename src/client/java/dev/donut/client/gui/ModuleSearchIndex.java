package dev.donut.client.gui;

import dev.donut.client.chunkfinder.SuspectObjects;
import dev.donut.client.storageesp.StorageKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Setting names shown in each module's inspector, so the click GUI search can find a module by one of its
 * settings (e.g. "tracers"). Keep in sync with DonutScreen's draw*Settings methods; ModuleSearchIndexTest
 * fails when a toggle, slider, color row, button or section label is missing here.
 */
public final class ModuleSearchIndex {
    private static final Map<Integer, List<String>> SETTINGS = new HashMap<>();

    static {
        put(2, "Camera speed", "Sprint boost", "Smoothing", "Scroll wheel adjusts speed", "Speed / status readout", "Resume last camera position", "Follow nearest player", "Follow range", "Look at target", "Set target ", "Movement", "Camera helpers", "Your controls");
        put(3, "Spectre Creative engine", "Open AutoBuilder", "Schematics");
        put(4, "Simulation distance", "Sensitivity", "Bee clue points", "Object / inspect range", "Look-to-inspect panel", "Panel opacity", "Outline opacity", "Include short / immature growth", "Minimum kelp length", "Minimum vine length", "Minimum cave vine length", "Minimum bamboo height", "Minimum mature crystals", "Minimum mature crystal %", "Kelp", "Cave vines", "Vines", "Amethyst", "Bamboo", "Cocoa", "Bee nests / hives", "Panel top color", "Panel bottom color", "Detection", "Discovery panel", "Object outlines", "Growth and geodes", "Clue filters", "Objects and colors");
        put(5, "Brightness");
        put(6, "Vanilla flight", "Your controls");
        put(7, "Overlay size", "Shiny outline", "Move overlay", "Reset progress", "Overlay style", "Base counter", "Countdowns", "Manual progress");
        put(8, "Blur entire scoreboard", "Sidebar privacy");
        put(9, "Hide held items", "Blur amount", "Hotbar privacy");
        put(10, "Min Cells", "Tracer", "Amethyst detection");
        put(11, "Render radius", "Minimum light", "Denoise", "Opacity", "Highlight color", "Underground light");
        put(12, "Received-event range", "Warning threshold", "Critical threshold", "Require identified player", "Filter own actions", "Filter probable own actions", "Filter known mobs", "Include client-derived sounds", "Warning cards", "Local warning chime", "Chat notices", "World markers", "Card duration", "Visible cards", "Source cooldown", "Show while in menus", "Respect global HUD toggle", "Card scale", "Background opacity", "Chime volume", "Warning accent", "Critical accent", "Card top", "Card bottom", "Detection", "Sources", "Warning delivery", "Preview and appearance", "Rules, history and profiles", "Diagnostics");
        put(13, "Stone variants", "Deepslate variants", "Minimum confidence to show", "Fade by confidence", "Prediction radius", "Opacity", "Tracers", "Coordinates / distance HUD", "Edit world seed", "Rescan predictions", "World seed", "Targets", "Variants", "Confidence", "Display", "Predictions");
        // Ore toggles and their colour rows are drawn from OreMarker.Kind in a loop.
        for (var kind : dev.donut.client.blockesp.OreMarker.Kind.values()) put(13, kind.label, kind.label + " color");
        put(14, "Spectator alerts", "Invisible nearby alerts", "Nearby range", "Check /msg completions", "Check interval", "Matching samples", "Roster removal alerts", "Presence HUD", "Chat alerts", "HUD staff rows", "Hint lifetime", "Alert cooldown", "Staff list / evidence", "Staff identity", "Observations", "Display");
        put(15, "Range", "Nearest targets shown", "Opacity", "Tracers", "Count / nearest HUD", "Rescan loaded chunks", "Targets", "Display", "Scan");
        put(16, "Facing direction", "Chunk position", "Nether / Overworld coordinates", "Biome", "Freecam camera position", "Bottom-right corner", "Lines", "Placement");
        put(17, "Beams", "Name labels", "Distance in labels", "Show Nether / Overworld waypoints converted", "Opacity", "Label range", "Manage waypoints", "Add waypoint here", "Waypoints", "Display");
        put(18, "Zoom level", "Smooth transition", "Scroll to adjust while held", "Remember scrolled level", "Lower mouse sensitivity", "Zoom", "Your controls");
        put(19, "Warn at or below", "Include armor", "Include offhand", "Local warning chime", "Warning");
        put(20, "Stone variants", "Deepslate variants", "Silk Touch for deepslate ores", "Silk Touch for stone ores", "Prefer Fortune", "Pick up drops", "Tools", "Include buried ores", "Range", "Tunnel limit", "Pause at health", "Protect low-durability tools", "Tool reserve (uses left)", "Turn speed", "Show HUD", "Ores", "Search", "Safety", "Display");
        put(22, "Players", "Hostile mobs", "Animals / passive mobs", "Dropped items", "Through-wall boxes", "Tracers", "Color players by distance", "Player color", "Team colors", "Friend color", "Hostile mob color", "Animal color", "Item color", "Nametags", "Health", "Health in hearts", "Held item", "Armor icons", "Distance", "Respect Name Hider", "Range", "Max shown", "Opacity", "Add friend", "Remove", "Targets", "Boxes and colors", "Friends");
        put(23, "Add block", "Add looked-at block", "Remove", "Range", "Max results", "Opacity", "Merge adjacent blocks", "Tracers", "Count / nearest HUD", "Rescan loaded chunks", "Blocks", "Display", "Scan");
        put(21, "Toggle instead of hold", "Perspective: ", "Invert pitch", "Camera distance", "Camera", "Your controls");
        put(27, "Delay", "Max attempts (0 = unlimited)", "Wait longer after each failure", "Don't retry bans and whitelists", "Reconnect");
        put(28, "Eat at or below hunger", "Eat when health is low", "Low health at or below", "Show HUD while eating", "When to eat", "Never eat", "Display");
        for (var food : dev.donut.client.config.AutoEatSettings.FOODS) put(28, food.label());
        put(24, "Widgets", "Details", "24-hour clock", "In-game day and time", "Memory percentage", "Corner");
        for (var widget : dev.donut.client.hudwidgets.HudWidget.values()) put(24, widget.label);
        put(25, "Clear marks", "Show new chunks", "New chunk color", "Show old chunks", "Old chunk color", "Range", "Overlay height (Y)", "Opacity", "Outlines", "Through walls", "Detection", "Display");
        put(26, "Open stash list", "Minimum storage", "Action-bar notice", "Toast", "Rescan loaded chunks", "Stashes", "Detection", "Weights", "Notices");
        for (StorageKind kind : StorageKind.values()) if (dev.donut.client.config.StashFinderSettings.weighted(kind)) put(26, kind.label);
        // Labels generated from enums at runtime in the inspector.
        List<String> storage = new ArrayList<>(SETTINGS.getOrDefault(15, List.of()));
        for (StorageKind kind : StorageKind.values()) { storage.add(kind.label); storage.add(kind.label + " color"); }
        SETTINGS.put(15, List.copyOf(storage));
        List<String> miner = new ArrayList<>(SETTINGS.getOrDefault(20, List.of()));
        for (dev.donut.client.automine.OreKind kind : dev.donut.client.automine.OreKind.values()) miner.add(kind.label);
        SETTINGS.put(20, List.copyOf(miner));
        List<String> finder = new ArrayList<>(SETTINGS.getOrDefault(4, List.of()));
        for (SuspectObjects.Category category : SuspectObjects.Category.values()) { finder.add(category.label); finder.add(category.label + " color"); }
        SETTINGS.put(4, List.copyOf(finder));
    }

    private ModuleSearchIndex() { }

    private static void put(int module, String... labels) {
        List<String> merged = new ArrayList<>(SETTINGS.getOrDefault(module, List.of()));
        for (String label : labels) if (!merged.contains(label)) merged.add(label);
        SETTINGS.put(module, List.copyOf(merged));
    }

    public static List<String> settings(int module) { return SETTINGS.getOrDefault(module, List.of()); }

    /** The first setting label containing the query (case-insensitive), or null. */
    public static String matchedSetting(int module, String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) return null;
        for (String label : settings(module)) if (label.toLowerCase(Locale.ROOT).contains(needle)) return label;
        return null;
    }
}
