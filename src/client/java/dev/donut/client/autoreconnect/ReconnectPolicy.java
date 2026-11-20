/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.autoreconnect;

import java.util.List;
import java.util.Locale;

/**
 * Pure Auto Reconnect rules: how long to wait, whether attempts are left, and which kick reasons are not worth
 * retrying.
 *
 * <p>Kick heuristics. A reason is "final" when either
 * <ul>
 *   <li>its vanilla translation key is one of {@link #FINAL_KEYS} (banned, IP-banned, not whitelisted, logged in from
 *   another location, outdated client or server, incompatible), or</li>
 *   <li>its displayed text (lower case) contains one of {@link #FINAL_WORDS}. Plugins usually send plain text, so the
 *   words cover "You are banned", "temporarily banned", "not whitelisted", "white-listed", "blacklisted" and similar.</li>
 * </ul>
 * Anything else (timeouts, "Server closed", restarts, "Connection refused", generic kicks) is retried. False negatives
 * only cost the configured number of attempts; a word list can't know every server's wording.
 */
public final class ReconnectPolicy {
    public static final int MAX_WAIT_SECONDS = 60;
    public static final List<String> FINAL_KEYS = List.of(
            "multiplayer.disconnect.banned", "multiplayer.disconnect.banned.reason", "multiplayer.disconnect.banned.expiration",
            "multiplayer.disconnect.banned_ip.reason", "multiplayer.disconnect.banned_ip.expiration",
            "multiplayer.disconnect.not_whitelisted", "multiplayer.disconnect.duplicate_login",
            "multiplayer.disconnect.outdated_client", "multiplayer.disconnect.outdated_server",
            "multiplayer.disconnect.incompatible");
    public static final List<String> FINAL_WORDS = List.of(
            "banned", "you are banned", "ban expires", "whitelist", "white-list", "white list", "whitelisted",
            "blacklist", "logged in from another location", "outdated client", "outdated server", "incompatible");

    private ReconnectPolicy() { }

    /** Seconds before attempt {@code attempt} (1 = first). With backoff the wait doubles each time, up to a minute. */
    public static int delaySeconds(int base, int attempt, boolean backoff) {
        int delay = Math.max(1, base);
        if (!backoff || attempt <= 1) return delay;
        long grown = (long) delay << Math.min(attempt - 1, 16);
        return (int) Math.min(grown, Math.max(delay, MAX_WAIT_SECONDS));
    }

    /** True while another attempt is allowed; {@code max} 0 means unlimited. */
    public static boolean attemptsLeft(int made, int max) { return max <= 0 || made < max; }

    /** "attempt 2/5", or "attempt 2" when unlimited. */
    public static String attemptLabel(int attempt, int max) {
        return "attempt " + attempt + (max > 0 ? "/" + max : "");
    }

    /**
     * A short reason ("banned", "whitelist", ...) when the disconnect should not be retried, or null.
     * {@code translationKey} may be null for plain-text reasons.
     */
    public static String finalReason(String translationKey, String text) {
        if (translationKey != null) {
            for (String key : FINAL_KEYS) if (translationKey.equals(key)) return describe(key);
        }
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : FINAL_WORDS) if (lower.contains(word)) return describe(word);
        return null;
    }

    private static String describe(String match) {
        if (match.contains("black")) return "blacklisted";
        if (match.contains("ban")) return "banned";
        if (match.contains("white")) return "not whitelisted";
        if (match.contains("duplicate") || match.contains("another location")) return "logged in elsewhere";
        if (match.contains("outdated") || match.contains("incompatible")) return "version mismatch";
        return match;
    }
}
