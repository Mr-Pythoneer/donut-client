package dev.donut.client.mixin;

import dev.donut.client.autoreconnect.AutoReconnectController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the server each multiplayer connection goes to, so Auto Reconnect rejoins the same address. */
@Mixin(ConnectScreen.class)
abstract class AutoReconnectConnectMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void donut$rememberServer(Screen parent, Minecraft client, ServerAddress address, ServerData data,
                                             boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        AutoReconnectController reconnect = AutoReconnectController.get();
        if (reconnect != null) reconnect.connecting(address, data);
    }
}
