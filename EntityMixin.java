package com.pvpclient.mixin;

import com.pvpclient.module.visual.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse movement turns the freecam instead of your real character while Freecam runs. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void pvpclient$freecamLook(double dx, double dy, CallbackInfo ci) {
        if (FreecamModule.isRunning() && (Object) this == MinecraftClient.getInstance().player) {
            FreecamModule.look(dx, dy);
            ci.cancel();
        }
    }
}
