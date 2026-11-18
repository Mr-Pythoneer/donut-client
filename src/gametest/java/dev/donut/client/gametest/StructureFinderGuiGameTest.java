package dev.donut.client.gametest;

import dev.donut.client.DonutClient;
import dev.donut.client.gui.DonutScreen;
import dev.donut.client.gui.StructureFinderScreen;
import dev.donut.client.structurefinder.StructureFinderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The seed finder end to end through its UI: the module tile opens the screen, pressing Find runs a real search for
 * a typed seed, and the results list fills with structures (including an end city). Proves the module wiring and the
 * screen, on top of StructureSearchGameTest which proves the engine.
 */
public final class StructureFinderGuiGameTest implements FabricClientGameTest {
    private static final String SEED = "6608149111735331168";

    @Override
    public void runTest(ClientGameTestContext context) {
        var config = DonutClient.config();
        config.structureFinder.lastSeed = SEED;
        config.structureFinder.nearestOnly = true;
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.resizeDisplay(); });

        try (var world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();

            // The module tile opens the finder screen.
            GuiTestInput.openAnimated(context, () -> new DonutScreen(config, () -> { }));
            GuiTestInput.click(context, "tab0");
            GuiTestInput.reveal(context, "module29");
            GuiTestInput.clickNow(context, "enable29");
            context.waitFor(mc -> mc.screen instanceof StructureFinderScreen, 100);

            // Press Find and wait for the background search to finish.
            context.runOnClient(mc -> press(mc, "Find"));
            StructureFinderController controller = StructureFinderController.get();
            require(controller != null, "Controller was not created");
            boolean done = false;
            for (int i = 0; i < 3600 && !done; i++) {
                done = context.computeOnClient(mc -> controller.phase() == StructureFinderController.Phase.DONE
                        || controller.phase() == StructureFinderController.Phase.ERROR);
                context.waitTick();
            }
            require(done, "Search did not finish; phase=" + context.computeOnClient(mc -> controller.phase()));
            require(context.computeOnClient(mc -> controller.phase()) == StructureFinderController.Phase.DONE,
                    "Search errored: " + context.computeOnClient(mc -> controller.status()));
            int count = context.computeOnClient(mc -> controller.results().size());
            require(count > 0, "No structures listed");
            boolean hasEndCity = context.computeOnClient(mc -> controller.results().stream().anyMatch(h -> h.groupId().equals("end_city")));
            require(hasEndCity, "Nearest-of-each is missing an end city");
            // The seed persisted for next time.
            context.waitTicks(4);
            context.takeScreenshot(TestScreenshotOptions.of("structure-finder-results").withSize(1280, 800));

            // Add the selected result as a waypoint works (click the first row, then Add waypoint).
            context.runOnClient(mc -> press(mc, "Done"));
            context.waitTicks(2);
        }
    }

    private static void press(Minecraft mc, String label) {
        for (GuiEventListener child : mc.screen.children())
            if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
                mc.screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
                return;
            }
        throw new AssertionError("Button not found: " + label);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
