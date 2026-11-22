package com.pvpclient.module.hud;

import net.minecraft.client.gui.DrawContext;

public final class FpsModule extends HudModule {
    public FpsModule() {
        super("FPS Counter", "Frames per second", 1.5, 7.5);
    }

    @Override
    protected void draw(DrawContext c) {
        textBox(c, "FPS: " + mc.getCurrentFps());
    }
}
