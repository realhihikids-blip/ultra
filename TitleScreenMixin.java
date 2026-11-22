package com.pvpclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Blip Client brand mark without replacing or moving vanilla title-screen controls. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    private static final Identifier BLIP_LOGO = Identifier.of("pvpclient", "textures/gui/blip_logo.png");
    private static final int LOGO_WIDTH = 150;
    private static final int LOGO_HEIGHT = 100;
    private static final int TEXTURE_WIDTH = 1200;
    private static final int TEXTURE_HEIGHT = 802;

    @Inject(method = "render", at = @At("TAIL"))
    private void blip$renderLogo(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = Math.max(8, context.getScaledWindowWidth() - LOGO_WIDTH - 12);
        context.drawTexture(RenderLayer::getGuiTextured, BLIP_LOGO,
                x, 8, 0.0F, 0.0F, LOGO_WIDTH, LOGO_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
