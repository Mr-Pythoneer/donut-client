package dev.donut.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Player ESP preferences. Only entities the server has already sent to this client can be shown. */
public final class PlayerEspSettings {
    public static final int MIN_RANGE = 8, MAX_RANGE = 512, MIN_SHOWN = 1, MAX_SHOWN = 256, MAX_FRIENDS = 200;
    public static final int PLAYER_COLOR = 0xFF5A4E, FRIEND_COLOR = 0x7FCB8E, HOSTILE_COLOR = 0xF0B45A,
            PASSIVE_COLOR = 0x7FA7FF, ITEM_COLOR = 0xC2D86B;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public boolean enabled = false;
    public boolean favorite = false;
    // Targets: players only by default.
    public boolean players = true;
    public boolean hostileMobs = false;
    public boolean passiveMobs = false;
    public boolean items = false;
    // Range and limits.
    public int range = 128;
    public int maxShown = 64;
    public int opacity = 220;
    // Boxes and colours.
    public boolean boxes = true;
    public boolean tracers = false;
    /** Players fade from red (near) to green (far); off uses {@link #playerColor}. */
    public boolean colorByDistance = true;
    public boolean teamColors = false;
    public int playerColor = PLAYER_COLOR;
    public int friendColor = FRIEND_COLOR;
    public int hostileColor = HOSTILE_COLOR;
    public int passiveColor = PASSIVE_COLOR;
    public int itemColor = ITEM_COLOR;
    // Nametags.
    public boolean nametags = true;
    public boolean showHealth = true;
    public boolean healthAsHearts = true;
    public boolean showArmor = true;
    public boolean showHeldItem = true;
    public boolean showDistance = true;
    /** With Name Hider on, player names are replaced by "Player" / "Friend". */
    public boolean respectNameHider = true;
    public List<String> friends = new ArrayList<>();

    /** A trimmed Minecraft username, or an exception explaining why it is not one. */
    public static String friendName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Type a username first.");
        if (!NAME.matcher(name).matches())
            throw new IllegalArgumentException("Usernames are 1-16 letters, digits or underscores.");
        return name;
    }

    public boolean isFriend(String name) {
        if (name == null || friends == null) return false;
        for (String friend : friends) if (friend.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Adds a friend; false when already listed. Throws for invalid names or a full list. */
    public boolean addFriend(String raw) {
        String name = friendName(raw);
        if (friends == null) friends = new ArrayList<>();
        if (isFriend(name)) return false;
        if (friends.size() >= MAX_FRIENDS) throw new IllegalArgumentException("The friends list is full (" + MAX_FRIENDS + ").");
        friends.add(name);
        return true;
    }

    public boolean removeFriend(String name) {
        return friends != null && name != null && friends.removeIf(friend -> friend.equalsIgnoreCase(name));
    }

    public void sanitize() {
        range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
        maxShown = Math.clamp(maxShown, MIN_SHOWN, MAX_SHOWN);
        opacity = Math.clamp(opacity, 0, 255);
        playerColor &= 0xFFFFFF;
        friendColor &= 0xFFFFFF;
        hostileColor &= 0xFFFFFF;
        passiveColor &= 0xFFFFFF;
        itemColor &= 0xFFFFFF;
        List<String> clean = new ArrayList<>();
        if (friends != null) for (String friend : friends) {
            if (friend == null || clean.size() >= MAX_FRIENDS) continue;
            String name = friend.strip();
            if (!NAME.matcher(name).matches()) continue;
            if (clean.stream().noneMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))) clean.add(name);
        }
        friends = clean;
    }
}
