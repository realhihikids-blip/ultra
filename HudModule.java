package com.pvpclient.module.hud;

import com.pvpclient.PvpClient;
import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Shared position/scale/background settings for HUD widgets. Positions are percentages of the screen. */
public abstract class HudModule extends ClientModule {
    protected final NumberSetting posX;
    protected final NumberSetting posY;
    protected final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.05));
    protected final BooleanSetting background = add(new BooleanSetting("Background", true));
    protected final ColorSetting textColor = add(new ColorSetting("Text Color", 0xFFFFFF));

    protected HudModule(String name, String description, double x, double y) {
        super(name, description, Category.HUD);
        posX = add(new NumberSetting("Position X %", x, 0, 100, 0.5));
        posY = add(new NumberSetting("Position Y %", y, 0, 100, 0.5));
        enabledByDefault();
    }

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        int px = (int) (mc.getWindow().getScaledWidth() * posX.get() / 100.0);
        int py = (int) (mc.getWindow().getScaledHeight() * posY.get() / 100.0);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, py, 0f);
        m.scale(scale.getFloat(), scale.getFloat(), 1f);
        draw(c);
        m.pop();
    }

    protected abstract void draw(DrawContext c);

    /** A small pill with a gradient underline. */
    protected void textBox(DrawContext c, String text) {
        int w = mc.textRenderer.getWidth(text) + 12;
        if (background.get()) {
            RenderUtil.roundedRect(c, 0, 0, w, 16, 4, 0xB00A0A14);
            RenderUtil.hGradient(c, 3, 14, w - 6, 1, 0xFF000000 | PvpClient.settings().accentPrimary.get(),
                    0xFF000000 | PvpClient.settings().accentSecondary.get());
        }
        c.drawText(mc.textRenderer, text, 6, 4, 0xFF000000 | textColor.get(), true);
    }
}
