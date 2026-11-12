package dev.donut.client.gametest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-only: vanilla's own hurt tilt and view bob, to check that waypoint labels use the same transform. */
@Mixin(GameRenderer.class)
public interface GameRendererBobInvoker {
    @Invoker("bobHurt") void donut$bobHurt(PoseStack pose, float partialTick);
    @Invoker("bobView") void donut$bobView(PoseStack pose, float partialTick);
}
