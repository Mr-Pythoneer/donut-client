package dev.donut.client.config;

/** Hold-to-zoom preferences. Zoom only changes this client's field of view and mouse sensitivity. */
public final class ZoomSettings {
    public static final double MIN_FACTOR = 1.5, MAX_FACTOR = 50;
    public boolean enabled = false;
    public boolean favorite = false;
    /** How many times closer the view appears; FOV is divided by this. */
    public double factor = 4.0;
    public boolean smooth = true;
    public boolean scrollAdjusts = true;
    /** Scale mouse sensitivity down with the zoom so aiming stays controllable. */
    public boolean reduceSensitivity = true;
    /** Scroll changes last for the current zoom only, unless this is on. */
    public boolean rememberScroll = false;

    public void sanitize() {
        factor = Double.isFinite(factor) ? Math.clamp(factor, MIN_FACTOR, MAX_FACTOR) : 4.0;
    }
}
