package dev.donut.client.config;

/** NewChunks overlay preferences. Detection reads only packets the client already receives. */
public final class NewChunksSettings {
    public static final int MIN_RANGE = 2, MAX_RANGE = 64, MIN_HEIGHT = -64, MAX_HEIGHT = 320;
    public boolean enabled = false;
    public boolean favorite = false;
    /** Overlay radius around the player, in chunks. */
    public int range = 16;
    /** World Y of the flat overlay. */
    public int height = 0;
    public int opacity = 70;
    public boolean showNew = true;
    public boolean showOld = false;
    public boolean outlines = true;
    public boolean throughWalls = true;
    public int newColor = 0xFF5A4E;
    public int oldColor = 0x58C98B;

    public void sanitize() {
        range = Math.clamp(range, MIN_RANGE, MAX_RANGE);
        height = Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        opacity = Math.clamp(opacity, 0, 255);
        newColor &= 0xFFFFFF;
        oldColor &= 0xFFFFFF;
    }
}
