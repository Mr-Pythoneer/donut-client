package dev.donut.client.hudwidgets;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HudWidgetTextTest {
    @Test void fpsAndPing() {
        assertEquals("144", HudWidgetText.fps(144));
        assertEquals("0", HudWidgetText.fps(-3));
        assertEquals("singleplayer", HudWidgetText.ping(42, true));
        assertEquals("singleplayer", HudWidgetText.ping(null, true));
        assertEquals("37 ms", HudWidgetText.ping(37, false));
        assertEquals("-", HudWidgetText.ping(null, false), "No tab-list entry yet");
        assertEquals(0, HudWidgetText.pingLevel(99));
        assertEquals(1, HudWidgetText.pingLevel(100));
        assertEquals(2, HudWidgetText.pingLevel(250));
    }

    @Test void clicksPerSecondUseASlidingOneSecondWindow() {
        AtomicLong now = new AtomicLong(10_000);
        ClickCounter counter = new ClickCounter(now::get);
        for (int i = 0; i < 5; i++) { counter.click(ClickCounter.LEFT); now.addAndGet(100); } // 10000..10400
        counter.click(ClickCounter.RIGHT);
        assertEquals(5, counter.cps(ClickCounter.LEFT));
        assertEquals(1, counter.cps(ClickCounter.RIGHT));
        assertEquals("5 | 1", HudWidgetText.cps(counter.cps(ClickCounter.LEFT), counter.cps(ClickCounter.RIGHT)));
        now.set(11_050);
        assertEquals(4, counter.cps(ClickCounter.LEFT), "10000 is more than a second old");
        now.set(11_350);
        assertEquals(1, counter.cps(ClickCounter.LEFT));
        now.set(12_000);
        assertEquals(0, counter.cps(ClickCounter.LEFT));
        assertEquals(0, counter.cps(ClickCounter.RIGHT));
        assertEquals(5, counter.total(ClickCounter.LEFT));
        assertEquals(0, counter.cps(7), "Unknown buttons are ignored");
        for (int i = 0; i < 500; i++) counter.click(ClickCounter.LEFT);
        assertEquals(200, counter.cps(ClickCounter.LEFT), "Tracking is bounded");
        counter.reset();
        assertEquals(0, counter.total(ClickCounter.LEFT));
    }

    @Test void potionTimeAndLevel() {
        assertEquals("0:00", HudWidgetText.effectTime(0, false));
        assertEquals("0:01", HudWidgetText.effectTime(39, false));
        assertEquals("1:30", HudWidgetText.effectTime(90 * 20, false));
        assertEquals("8:00", HudWidgetText.effectTime(9600, false));
        assertEquals("1:00:05", HudWidgetText.effectTime(3605 * 20, false));
        assertEquals("∞", HudWidgetText.effectTime(-1, true));
        assertEquals("Speed", HudWidgetText.effectName("Speed", 0));
        assertEquals("Speed II", HudWidgetText.effectName("Speed", 1));
        assertEquals("Haste X", HudWidgetText.effectName("Haste", 9));
        assertEquals("Haste 11", HudWidgetText.effectName("Haste", 10));
    }

    @Test void compassUsesBearingsWhereNorthIsZero() {
        // Minecraft yaw: 0 faces south (+Z), 90 west, 180 / -180 north, -90 east.
        assertEquals("N 0°", HudWidgetText.compass(180));
        assertEquals("N 0°", HudWidgetText.compass(-180));
        assertEquals("E 90°", HudWidgetText.compass(-90));
        assertEquals("S 180°", HudWidgetText.compass(0));
        assertEquals("W 270°", HudWidgetText.compass(90));
        assertEquals("NE 45°", HudWidgetText.compass(-135));
        assertEquals(12, HudWidgetText.bearing(-168));
        assertEquals("N", HudWidgetText.cardinal(12));
        assertEquals("N", HudWidgetText.cardinal(350));
        assertEquals(359, HudWidgetText.bearing(179 + 720));
    }

    @Test void clocks() {
        assertEquals("14:05", HudWidgetText.clock(LocalTime.of(14, 5), true));
        assertEquals("2:05 PM", HudWidgetText.clock(LocalTime.of(14, 5), false));
        assertEquals("12:00 AM", HudWidgetText.clock(LocalTime.MIDNIGHT, false));
        assertEquals("12:30 PM", HudWidgetText.clock(LocalTime.of(12, 30), false));
        assertEquals("Day 1  06:00", HudWidgetText.gameTime(0));
        assertEquals("Day 1  12:00", HudWidgetText.gameTime(6000));
        assertEquals("Day 3  06:00", HudWidgetText.gameTime(48000));
    }

    @Test void memoryAndKeyCaps() {
        long mb = 1024 * 1024;
        assertEquals("512 / 2048 MB  25%", HudWidgetText.memory(512 * mb, 2048 * mb, true));
        assertEquals("512 / 2048 MB", HudWidgetText.memory(512 * mb, 2048 * mb, false));
        assertEquals("W", HudWidgetText.keyCap("w"));
        assertEquals("Lef", HudWidgetText.keyCap("Left Arrow"));
        assertEquals("?", HudWidgetText.keyCap(" "));
    }
}
