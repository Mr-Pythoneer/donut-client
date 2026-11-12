package dev.donut.client.config;

import dev.donut.client.hudwidgets.HudWidget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HUD Widgets preferences. Widget switches and corners are keyed by {@link HudWidget#id}. */
public final class HudWidgetsSettings {
    public static final List<String> CORNERS = List.of("Top left", "Top right", "Bottom left", "Bottom right");
    public boolean enabled = false;
    public boolean favorite = false;
    public Map<String, Boolean> widgets = new LinkedHashMap<>();
    public Map<String, String> corners = new LinkedHashMap<>();
    /** Clock: 24-hour time, otherwise 12-hour with AM/PM. */
    public boolean clock24h = true;
    /** Clock: also show the in-game day and time. */
    public boolean showGameTime = false;
    /** Memory: also show the used percentage. */
    public boolean memoryPercent = true;

    public boolean widgetEnabled(HudWidget widget) {
        Boolean saved = widgets == null ? null : widgets.get(widget.id);
        return saved == null ? widget.defaultOn : saved;
    }

    public void setWidgetEnabled(HudWidget widget, boolean on) {
        if (widgets == null) widgets = new LinkedHashMap<>();
        widgets.put(widget.id, on);
    }

    public String corner(HudWidget widget) {
        String saved = corners == null ? null : corners.get(widget.id);
        return saved != null && CORNERS.contains(saved) ? saved : widget.defaultCorner;
    }

    /** Moves the widget to the next corner (top left, top right, bottom left, bottom right). */
    public void cycleCorner(HudWidget widget) {
        if (corners == null) corners = new LinkedHashMap<>();
        corners.put(widget.id, CORNERS.get((CORNERS.indexOf(corner(widget)) + 1) % CORNERS.size()));
    }

    public void sanitize() {
        if (widgets == null) widgets = new LinkedHashMap<>();
        if (corners == null) corners = new LinkedHashMap<>();
        widgets.keySet().removeIf(id -> HudWidget.byId(id) == null);
        corners.keySet().removeIf(id -> HudWidget.byId(id) == null);
        for (HudWidget widget : HudWidget.values()) {
            widgets.compute(widget.id, (id, value) -> value == null ? widget.defaultOn : value);
            corners.compute(widget.id, (id, value) -> value == null || !CORNERS.contains(value) ? widget.defaultCorner : value);
        }
    }
}
