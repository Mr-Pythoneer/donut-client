package dev.donut.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModuleSearchIndexTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void settingNamesFindTheirModules() {
        assertEquals("Tracers", ModuleSearchIndex.matchedSetting(15, "tracers"));
        assertEquals("Tracers", ModuleSearchIndex.matchedSetting(13, "TRACER"));
        assertNotNull(ModuleSearchIndex.matchedSetting(2, "smoothing"));
        assertNotNull(ModuleSearchIndex.matchedSetting(15, "shulker"), "Storage kinds are indexed from the enum");
        assertNull(ModuleSearchIndex.matchedSetting(5, "tracers"));
        assertNull(ModuleSearchIndex.matchedSetting(15, "  "));
        assertNull(ModuleSearchIndex.matchedSetting(999, "tracers"));
    }

    /** Guards the hand-maintained index: every literal inspector label in DonutScreen must be searchable. */
    @Test void everyInspectorLabelIsIndexed() throws Exception {
        String source = Files.readString(Path.of("src/client/java/dev/donut/client/gui/DonutScreen.java"));
        Matcher dispatch = Pattern.compile("index == (\\d+)\\) (draw\\w+)\\(").matcher(
                source.substring(source.indexOf("private void drawModuleSettings("), source.indexOf("private void help(")));
        List<String> missing = new ArrayList<>();
        int modules = 0;
        while (dispatch.find()) {
            modules++;
            int module = Integer.parseInt(dispatch.group(1));
            String method = dispatch.group(2);
            int start = source.indexOf("private void " + method + "(GuiGraphics g");
            assertTrue(start >= 0, "Missing settings method " + method);
            int end = source.indexOf("\n    }\n", start);
            String body = source.substring(start, end);
            Matcher label = Pattern.compile("(?:settingToggle|slider)\\(g,\\s*\"[^\"]*\",\\s*\"([^\"]+)\"|colorRow\\(g,\\s*\"([^\"]+)\""
                    + "|button\\(g,\\s*\"[^\"]*\",\\s*\"([^\"]+)\"|section\\(g,\\s*\"([^\"]+)\"").matcher(body);
            while (label.find()) {
                String text = label.group(1) != null ? label.group(1) : label.group(2) != null ? label.group(2)
                        : label.group(3) != null ? label.group(3) : label.group(4);
                if (!ModuleSearchIndex.settings(module).contains(text)) missing.add(module + ": " + text);
            }
        }
        assertTrue(modules >= 13, "Expected to find the module settings dispatch, found " + modules);
        assertTrue(missing.isEmpty(), "Add these inspector labels to ModuleSearchIndex: " + missing);
    }
}
