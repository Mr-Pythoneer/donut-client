package dev.donut.client.gametest.mixin;

import dev.donut.client.gametest.AntiCheatProbeGameTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts server position corrections (anti-cheat setbacks), once each, on the client thread. */
@Mixin(ClientPacketListener.class)
abstract class AntiCheatProbeMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void donut$probeSetback(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        if (Minecraft.getInstance().isSameThread()) AntiCheatProbeGameTest.setback();
    }
}
