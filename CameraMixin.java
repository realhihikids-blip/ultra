package com.pvpclient.mixin;

import com.pvpclient.module.visual.FreecamModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Freecam runs, the camera uses the freecam position and rotation instead of the player's. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void pvpclient$freecam(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                   boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!FreecamModule.isRunning()) return;
        FreecamModule.step();
        setPos(FreecamModule.camX(), FreecamModule.camY(), FreecamModule.camZ());
        setRotation(FreecamModule.camYaw(), FreecamModule.camPitch());
        this.thirdPerson = true; // draws your character and hides the first-person hand
    }
}
