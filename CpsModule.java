package com.pvpclient.module.hud;

import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.util.ClickTracker;
import net.minecraft.client.gui.DrawContext;

public final class CpsModule extends HudModule {
    private final BooleanSetting showRight = add(new BooleanSetting("Show Right Click", true));

    public CpsModule() {
        super("CPS Counter", "Clicks per second", 1.5, 1.5);
    }

    @Override
    protected void draw(DrawContext c) {
        String t = "CPS: " + ClickTracker.leftCps() + (showRight.get() ? " | " + ClickTracker.rightCps() : "");
        textBox(c, t);
    }
}
