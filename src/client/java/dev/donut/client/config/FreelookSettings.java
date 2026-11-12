package dev.donut.client.config;

import java.util.List;

/**
 * Freelook preferences. Freelook turns only the rendered camera around your player; the player's own yaw, pitch and
 * movement are never changed and nothing is sent to the server.
 */
public final class FreelookSettings {
    public static final double MIN_DISTANCE = 1, MAX_DISTANCE = 12, DEFAULT_DISTANCE = 4;
    /** Perspective while Freelook is active. */
    public static final String BACK = "Back", FRONT = "Front", KEEP = "Keep current";
    public static final List<String> PERSPECTIVES = List.of(BACK, FRONT, KEEP);

    public boolean enabled = false;
    public boolean favorite = false;
    /** Off: Freelook lasts while the key is held. On: each press turns it on or off. */
    public boolean toggleMode = false;
    public String perspective = BACK;
    public boolean invertPitch = false;
    /** Third-person camera distance while Freelook is active, in blocks (vanilla uses 4). Walls still pull it in. */
    public double distance = DEFAULT_DISTANCE;

    /** The next perspective in the settings button's cycle. */
    public static String next(String current) {
        int index = current == null ? -1 : PERSPECTIVES.indexOf(current);
        return PERSPECTIVES.get((index + 1) % PERSPECTIVES.size());
    }

    public void sanitize() {
        if (perspective == null || !PERSPECTIVES.contains(perspective)) perspective = BACK;
        distance = Double.isFinite(distance) ? Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE) : DEFAULT_DISTANCE;
    }
}
