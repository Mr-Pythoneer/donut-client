package dev.donut.client.mixin;

import dev.donut.client.freecam.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void donut$hideHand(CallbackInfo ci) {
        if (FreecamController.enabled()) ci.cancel();
    }

    /** Hold-to-zoom: scales only the world field of view (the hand keeps its normal FOV). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void donut$zoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting,
                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        var zoom = dev.donut.client.zoom.ZoomController.get();
        if (zoom == null || !useFovSetting) return;
        double multiplier = zoom.fovMultiplier(partialTick);
        if (multiplier < 1) cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void donut$clearTarget(CallbackInfo ci) {
        if (!FreecamController.enabled()) return;
        var client = Minecraft.getInstance();
        var pos = FreecamController.getInstance().position();
        client.hitResult = BlockHitResult.miss(pos, Direction.DOWN, BlockPos.containing(pos));
        client.crosshairPickEntity = null;
        ci.cancel();
    }
}
