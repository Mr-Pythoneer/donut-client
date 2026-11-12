package dev.donut.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Local export/import of the whole Donut config plus its {@code key.donut.*} shortcuts.
 * Profiles are plain JSON files in {@code <config>/donut-profiles/}; nothing here touches the network.
 * Importing never switches a module on: every module's {@code enabled} flag is cleared after parsing.
 */
public final class ConfigProfiles {
    public static final int FORMAT = 1;
    public static final String KEY_PREFIX = "key.donut.";
    public static final String DIRECTORY = "donut-profiles";
    public static final String CONFIG_FILE = "donut-client.json";
    /** Profiles are a few KB; anything far larger is not something we wrote. */
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    /** Human names for module config keys, matching the click GUI. Unknown keys fall back to {@link #displayName}'s word split. */
    static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("freecam", "Freecam"), Map.entry("autoBuilder", "AutoBuilder"), Map.entry("chunkFinder", "Base Finder"),
            Map.entry("lightDebug", "Light Debug"), Map.entry("soundDetector", "Sound Detector"),
            Map.entry("staffDetector", "Staff Detector"), Map.entry("blockEsp", "BlockESP"), Map.entry("storageEsp", "Storage ESP"),
            Map.entry("playerEsp", "Player ESP"), Map.entry("search", "Search"),
            Map.entry("coordinates", "Coordinates"), Map.entry("zoom", "Zoom"), Map.entry("durabilityAlert", "Durability Alert"),
            Map.entry("autoMiner", "Auto Miner"), Map.entry("waypoints", "Waypoints"),
            Map.entry("freelook", "Freelook"), Map.entry("autoReconnect", "Auto Reconnect"), Map.entry("autoEat", "Auto Eat"),
            Map.entry("amethyst", "Amethyst Scanner"), Map.entry("fullbright", "Fullbright"), Map.entry("fly", "Fly"),
            Map.entry("progressBar", "Progress Bar"), Map.entry("nameHider", "Name Hider"), Map.entry("hotbar", "Hotbar"));

    /** One parsed profile file. {@code config} is sanitized with every module disabled. */
    public record Profile(ClientConfig config, Map<String, String> keybinds, int format, String exportedAt, String modVersion) { }

    private ConfigProfiles() { }

    // ---- JSON ----

    /** Serializes a sanitized copy of {@code config}; the live object is never touched. */
    public static String toJson(ClientConfig config, Map<String, String> keybinds, String modVersion, Instant now) {
        ClientConfig copy = GSON.fromJson(GSON.toJsonTree(config), ClientConfig.class);
        copy.sanitize();
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("modVersion", modVersion == null || modVersion.isBlank() ? "unknown" : modVersion);
        root.addProperty("exportedAt", DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.SECONDS)));
        JsonObject keys = new JsonObject();
        new TreeMap<>(filterKeybinds(keybinds)).forEach(keys::addProperty);
        root.add("keybinds", keys);
        root.add("config", GSON.toJsonTree(copy));
        return GSON.toJson(root);
    }

    /** Validates and reads a profile; throws {@link IllegalArgumentException} with a readable reason on any problem. */
    public static Profile parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("The profile file is empty.");
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException failure) {
            throw new IllegalArgumentException("The profile is not valid JSON" + reason(failure));
        }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("The profile is not a JSON object.");
        JsonObject root = parsed.getAsJsonObject();
        JsonElement format = root.get("format");
        if (format == null || format.isJsonNull())
            throw new IllegalArgumentException("Not a Donut profile: the \"format\" field is missing.");
        if (!(format instanceof JsonPrimitive primitive) || !primitive.isNumber() || primitive.getAsDouble() != FORMAT)
            throw new IllegalArgumentException("Unsupported profile format " + format + "; this version reads format " + FORMAT + ".");
        JsonElement body = root.get("config");
        if (body == null || body.isJsonNull()) throw new IllegalArgumentException("The profile has no \"config\" section.");
        if (!body.isJsonObject()) throw new IllegalArgumentException("The profile's \"config\" section is not an object.");
        ClientConfig config;
        try {
            config = GSON.fromJson(body, ClientConfig.class);
            config.sanitize();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("The profile's settings are invalid" + reason(failure));
        }
        disableModules(config);
        Map<String, String> keybinds = new LinkedHashMap<>();
        if (root.get("keybinds") instanceof JsonObject keys) {
            for (var entry : keys.entrySet()) {
                if (entry.getValue() instanceof JsonPrimitive value && value.isString()) keybinds.put(entry.getKey(), value.getAsString());
            }
        }
        return new Profile(config, filterKeybinds(keybinds), FORMAT, text(root, "exportedAt"), text(root, "modVersion"));
    }

    private static String text(JsonObject root, String name) {
        return root.get(name) instanceof JsonPrimitive value && value.isString() ? value.getAsString() : null;
    }

    private static String reason(RuntimeException failure) {
        String message = failure.getMessage();
        if ((message == null || message.isBlank()) && failure.getCause() != null) message = failure.getCause().getMessage();
        if (message == null || message.isBlank()) return ".";
        int cut = message.indexOf("\nSee https://");
        return ": " + (cut > 0 ? message.substring(0, cut) : message).trim();
    }

    /** Keeps only {@code key.donut.*} names with non-blank values, trimmed, in their original order. */
    public static Map<String, String> filterKeybinds(Map<String, String> keybinds) {
        Map<String, String> kept = new LinkedHashMap<>();
        if (keybinds == null) return kept;
        keybinds.forEach((name, value) -> {
            if (name != null && name.startsWith(KEY_PREFIX) && name.length() > KEY_PREFIX.length() && value != null && !value.isBlank())
                kept.put(name, value.trim());
        });
        return kept;
    }

    // ---- Applying ----

    /** Module settings objects: public ClientConfig fields whose value has a public non-static, non-transient boolean {@code enabled}. */
    private static Map<String, Object> modules(ClientConfig config) {
        Map<String, Object> modules = new LinkedHashMap<>();
        for (Field field : ClientConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(config);
                if (value != null && enabledField(value.getClass()) != null) modules.put(field.getName(), value);
            } catch (IllegalAccessException ignored) { }
        }
        return modules;
    }

    private static Field enabledField(Class<?> type) {
        try {
            Field field = type.getField("enabled");
            int modifiers = field.getModifiers();
            return field.getType() == boolean.class && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) ? field : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Turns every module off. Transient {@code enabled} flags (Freecam, Fly) are never read from a file
     * but are cleared too, so a profile can never start anything.
     */
    public static void disableModules(ClientConfig config) {
        for (Object module : modules(config).values()) setEnabled(module, false);
    }

    private static boolean enabled(Object module) {
        try {
            Field field = enabledField(module.getClass());
            return field != null && field.getBoolean(module);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static void setEnabled(Object module, boolean value) {
        try {
            Field field = enabledField(module.getClass());
            if (field != null) field.setBoolean(module, value);
        } catch (IllegalAccessException ignored) { }
    }

    /**
     * Copies every public non-static, non-final field of {@code source} into {@code target}, so code holding the
     * live config object keeps working. Module settings objects are adopted by reference (don't reuse
     * {@code source} afterwards). Each module keeps the live {@code enabled} value it had in {@code target}:
     * an import never switches anything on or off mid-session, it only changes how modules are configured.
     */
    public static void copyInto(ClientConfig target, ClientConfig source) {
        Map<String, Boolean> live = new LinkedHashMap<>();
        modules(target).forEach((name, module) -> live.put(name, enabled(module)));
        for (Field field : ClientConfig.class.getFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
            try {
                field.set(target, field.get(source));
            } catch (IllegalAccessException ignored) { }
        }
        target.sanitize();
        modules(target).forEach((name, module) -> setEnabled(module, live.getOrDefault(name, false)));
    }

    // ---- Summary ----

    /** Short human-readable lines describing a profile, for the import preview. */
    public static List<String> summarize(Profile profile) {
        List<String> lines = new ArrayList<>();
        if (profile == null) return lines;
        lines.add("Exported " + shownDate(profile.exportedAt()) + " from Donut Client " + orUnknown(profile.modVersion()));
        ClientConfig config = profile.config();
        Map<String, String> keybinds = profile.keybinds() == null ? Map.of() : profile.keybinds();
        long bound = keybinds.values().stream().filter(value -> !"key.keyboard.unknown".equals(value)).count();
        lines.add("Shortcuts: " + keybinds.size() + " saved, " + bound + " bound to a key");
        if (config == null) {
            lines.add("No settings in this profile");
            return lines;
        }
        List<String> favorites = new ArrayList<>();
        modules(config).forEach((name, module) -> {
            try {
                Field favorite = module.getClass().getField("favorite");
                if (favorite.getType() == boolean.class && favorite.getBoolean(module)) favorites.add(displayName(name));
            } catch (ReflectiveOperationException ignored) { }
        });
        lines.add("Favorites: " + (favorites.isEmpty() ? "none" : String.join(", ", favorites)));
        if (config.freecam != null)
            lines.add(String.format(Locale.ROOT, "Freecam: speed %.1f, boost x%.1f%s", config.freecam.speed, config.freecam.boostMultiplier,
                    config.freecam.smoothing > 0 ? String.format(Locale.ROOT, ", smoothing %.2f", config.freecam.smoothing) : ""));
        if (config.storageEsp != null)
            lines.add("Storage ESP: range " + config.storageEsp.range + " blocks, up to " + config.storageEsp.maxTargets + " targets"
                    + (config.storageEsp.tracers ? ", tracers" : ""));
        if (config.blockEsp != null) lines.add("BlockESP: radius " + config.blockEsp.radius + " chunks, seed " + orUnknown(config.blockEsp.seed));
        if (config.soundDetector != null)
            lines.add(String.format(Locale.ROOT, "Sound Detector: %s, range %.0f, %d rules, %d zones", orUnknown(config.soundDetector.preset),
                    config.soundDetector.range, size(config.soundDetector.rules), size(config.soundDetector.zones)));
        if (config.staffDetector != null && config.staffDetector.rosters != null)
            lines.add("Staff Detector: " + config.staffDetector.rosters.size() + " server roster(s)");
        if (config.chunkFinder != null) lines.add("Base Finder: " + orUnknown(config.chunkFinder.method) + ", sensitivity " + config.chunkFinder.sensitivity);
        String scale = config.clickGuiStyle == null ? "" : String.format(Locale.ROOT, ", GUI scale %.2f", config.clickGuiStyle.scale);
        lines.add(String.format(Locale.ROOT, "Accent #%06X%s, HUD %s", config.accentColor & 0xFFFFFF, scale, config.showHud ? "on" : "off"));
        return lines;
    }

    /**
     * The name a person sees for a module config key: the click GUI's name when known ("durabilityAlert" is
     * "Durability Alert"), otherwise the key split into capitalized words ("someNewModule" is "Some New Module").
     */
    public static String displayName(String key) {
        if (key == null || key.isBlank()) return "Unknown";
        String known = DISPLAY_NAMES.get(key.strip());
        if (known != null) return known;
        String text = key.strip();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean gap = out.isEmpty() || out.charAt(out.length() - 1) == ' ';
            if (c == '_' || c == '-' || Character.isWhitespace(c)) {
                if (!gap) out.append(' ');
                continue;
            }
            if (!gap) {
                char previous = text.charAt(i - 1);
                boolean nextLower = i + 1 < text.length() && Character.isLowerCase(text.charAt(i + 1));
                boolean wordStart = Character.isUpperCase(c)
                        && (Character.isLowerCase(previous) || Character.isDigit(previous) || Character.isUpperCase(previous) && nextLower);
                if (wordStart || Character.isDigit(c) && Character.isLetter(previous)) {
                    out.append(' ');
                    gap = true;
                }
            }
            out.append(gap ? Character.toUpperCase(c) : c);
        }
        String split = out.toString().strip();
        return split.isEmpty() ? "Unknown" : split;
    }

    private static int size(java.util.Collection<?> values) { return values == null ? 0 : values.size(); }

    private static String orUnknown(String value) { return value == null || value.isBlank() ? "unknown" : value; }

    private static String shownDate(String iso) {
        if (iso == null || iso.isBlank()) return "at an unknown time";
        try {
            return SHOWN.format(Instant.parse(iso).atZone(ZoneId.systemDefault()));
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    // ---- Files ----

    public static Path profilesDir() { return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY); }

    public static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE); }

    /** Writes {@code donut-profile-<yyyyMMdd-HHmmss>.json} into {@link #profilesDir()} and returns its path. */
    public static Path export(ClientConfig config, Map<String, String> keybinds, String modVersion) throws IOException {
        return export(profilesDir(), config, keybinds, modVersion, Instant.now());
    }

    public static Path export(Path dir, ClientConfig config, Map<String, String> keybinds, String modVersion, Instant now) throws IOException {
        Files.createDirectories(dir);
        String json = toJson(config, keybinds, modVersion, now);
        String stamp = STAMP.format(now.atZone(ZoneId.systemDefault()));
        Path file = dir.resolve("donut-profile-" + stamp + ".json");
        for (int n = 2; Files.exists(file); n++) file = dir.resolve("donut-profile-" + stamp + "-" + n + ".json");
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    /** Saved profiles in {@link #profilesDir()}, newest first. */
    public static List<Path> list() throws IOException { return list(profilesDir()); }

    public static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(ConfigProfiles::modified).reversed()
                            .thenComparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
        }
    }

    public static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /** Reads and validates one profile file without changing anything. */
    public static Profile read(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) throw new IllegalArgumentException("The profile file is too large to be a Donut profile.");
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalArgumentException("Could not read " + file.getFileName() + ": " + failure.getMessage());
        }
    }

    /**
     * Copies the current config file to {@code <name>.before-import-<yyyyMMdd-HHmmss>} next to it.
     * Returns the backup path, or null when there is no config file yet (nothing to lose).
     */
    public static Path backupCurrent(Path configFile) throws IOException { return backupCurrent(configFile, LocalDateTime.now()); }

    public static Path backupCurrent(Path configFile, LocalDateTime now) throws IOException {
        if (!Files.exists(configFile)) return null;
        String base = configFile.getFileName() + ".before-import-" + STAMP.format(now);
        Path backup = configFile.resolveSibling(base);
        for (int n = 2; ; n++) {
            try {
                return Files.copy(configFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (FileAlreadyExistsException e) {
                backup = configFile.resolveSibling(base + "-" + n);
            }
        }
    }
}
