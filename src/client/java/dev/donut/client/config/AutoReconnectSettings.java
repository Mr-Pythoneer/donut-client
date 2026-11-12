package dev.donut.client.config;

/** Auto Reconnect preferences. Only multiplayer disconnects are retried, through vanilla's own connect screen. */
public final class AutoReconnectSettings {
    public static final int MIN_DELAY = 1, MAX_DELAY = 60, DEFAULT_DELAY = 5;
    public static final int MAX_ATTEMPTS = 50, DEFAULT_ATTEMPTS = 5;

    public boolean enabled = false;
    public boolean favorite = false;
    /** Seconds to wait before the first attempt. */
    public int delaySeconds = DEFAULT_DELAY;
    /** Attempts before giving up; 0 keeps trying until you leave the disconnect screen. */
    public int maxAttempts = DEFAULT_ATTEMPTS;
    /** Double the wait after each failed attempt (capped at a minute), so a restarting server has time to come back. */
    public boolean backoff = true;
    /** Don't retry when the kick reason looks like a ban, a whitelist, a duplicate login or a version mismatch. */
    public boolean skipBans = true;

    public void sanitize() {
        delaySeconds = Math.clamp(delaySeconds, MIN_DELAY, MAX_DELAY);
        maxAttempts = Math.clamp(maxAttempts, 0, MAX_ATTEMPTS);
    }
}
