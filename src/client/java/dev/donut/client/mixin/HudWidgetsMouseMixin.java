package dev.donut.client.mixin;

import dev.donut.client.hudwidgets.ClickCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts your own mouse presses for the CPS widget. Observes only; the click itself is untouched. */
@Mixin(MouseHandler.class)
abstract class HudWidgetsMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void donut$countClick(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || window != client.getWindow().handle()) return;
        var config = dev.donut.client.DonutClient.config();
        if (config == null || !config.hudWidgets.enabled) return;
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) ClickCounter.get().click(ClickCounter.LEFT);
        else if (button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) ClickCounter.get().click(ClickCounter.RIGHT);
    }
}
