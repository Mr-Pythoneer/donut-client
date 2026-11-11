package dev.donut.client.mixin;

import dev.donut.client.automine.AutoMinerController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Auto Miner drives the player, vanilla's own attack handling would call stopDestroyBlock every tick (the
 * attack button is not held) and abort the break. Suspend it, like AutoBuilder does, so the miner's
 * continueDestroyBlock calls proceed at normal speed. Nothing is sent from here.
 */
@Mixin(Minecraft.class)
abstract class AutoMinerInputMixin {
    @Inject(method = {"startUseItem", "pickBlock", "continueAttack"}, at = @At("HEAD"), cancellable = true)
    private void donut$autoMinerInteractions(CallbackInfo ci) {
        if (AutoMinerController.controlsInput()) ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void donut$autoMinerAttack(CallbackInfoReturnable<Boolean> cir) {
        if (AutoMinerController.controlsInput()) cir.setReturnValue(false);
    }
}
