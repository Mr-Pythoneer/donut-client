package dev.donut.client.mixin;

import dev.donut.client.freelook.FreelookController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Freelook: while active, the camera reads Freelook's own angles instead of the player's view rotation, and vanilla's
 * third-person code then places it (front view flip, wall collision) exactly as usual. The player is never rotated.
 */
@Mixin(Camera.class)
abstract class FreelookCameraMixin {
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float donut$freelookYaw(Entity entity, float partialTick) {
        return FreelookController.overridesCamera(entity) ? FreelookController.get().yaw() : entity.getViewYRot(partialTick);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float donut$freelookPitch(Entity entity, float partialTick) {
        return FreelookController.overridesCamera(entity) ? FreelookController.get().pitch() : entity.getViewXRot(partialTick);
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float donut$freelookDistance(float distance) {
        FreelookController freelook = FreelookController.get();
        return freelook == null ? distance : freelook.distance(distance);
    }
}
