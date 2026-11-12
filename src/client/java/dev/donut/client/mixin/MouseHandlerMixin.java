package dev.donut.client.mixin;

import dev.donut.client.freecam.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void donut$turnCamera(LocalPlayer player, double dx, double dy) {
        if (FreecamController.enabled()) FreecamController.getInstance().turn(dx, dy);
        else {
            // Zoom lowers local mouse sensitivity only; the turn itself is ordinary vanilla look input.
            var zoom = dev.donut.client.zoom.ZoomController.get();
            double scale = zoom == null ? 1 : zoom.sensitivityScale();
            // Freelook turns only its camera; the player keeps its rotation.
            if (dev.donut.client.freelook.FreelookController.isActive())
                dev.donut.client.freelook.FreelookController.get().turn(dx * scale, dy * scale);
            else player.turn(dx * scale, dy * scale);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void donut$preventHotbarScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        var zoom = dev.donut.client.zoom.ZoomController.get();
        if (zoom != null && Minecraft.getInstance().screen == null && zoom.scroll(vertical)) { ci.cancel(); return; }
        if (!FreecamController.enabled() || Minecraft.getInstance().screen != null) return;
        // The wheel adjusts camera speed instead of changing the player's hotbar slot.
        FreecamController.getInstance().scroll(vertical);
        ci.cancel();
    }
}
