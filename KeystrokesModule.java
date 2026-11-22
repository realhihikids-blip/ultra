package com.pvpclient.module.hud;

import com.pvpclient.util.Lang;
import com.pvpclient.PvpClient;
import com.pvpclient.util.ClickTracker;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.Smooth;
import net.minecraft.client.gui.DrawContext;

public final class KeystrokesModule extends HudModule {
    private final Smooth[] pressed = new Smooth[7];

    public KeystrokesModule() {
        super("Keystrokes", "WASD, mouse buttons and space", 1.5, 22.0);
        for (int i = 0; i < pressed.length; i++) pressed[i] = new Smooth(0);
    }

    @Override
    protected void draw(DrawContext c) {
        key(c, 0, 24, 0, 22, 22, "W", null, mc.options.forwardKey.isPressed());
        key(c, 1, 0, 24, 22, 22, "A", null, mc.options.leftKey.isPressed());
        key(c, 2, 24, 24, 22, 22, "S", null, mc.options.backKey.isPressed());
        key(c, 3, 48, 24, 22, 22, "D", null, mc.options.rightKey.isPressed());
        key(c, 4, 0, 48, 34, 22, Lang.tr("LMB"), ClickTracker.leftCps() + Lang.tr(" cps"), mc.options.attackKey.isPressed());
        key(c, 5, 36, 48, 34, 22, Lang.tr("RMB"), ClickTracker.rightCps() + Lang.tr(" cps"), mc.options.useKey.isPressed());
        key(c, 6, 0, 72, 70, 14, "", null, mc.options.jumpKey.isPressed());
    }

    private void key(DrawContext c, int i, int x, int y, int w, int h, String label, String sub, boolean down) {
        Smooth s = pressed[i];
        s.to(down ? 1 : 0);
        double t = s.update();
        int accent = 0xE0000000 | PvpClient.settings().accentPrimary.get();
        int bg = RenderUtil.lerpColor(0xB00A0A14, accent, t);
        RenderUtil.roundedRect(c, x, y, w, h, 4, bg);
        int fg = RenderUtil.lerpColor(0xFF9A9AB8, 0xFFFFFFFF, t);

        if (i == 6) {
            c.fill(x + 16, y + 6, x + w - 16, y + 8, fg);
        } else if (sub == null) {
            c.drawText(mc.textRenderer, label, x + (w - mc.textRenderer.getWidth(label)) / 2, y + 7, fg, false);
        } else {
            c.drawText(mc.textRenderer, label, x + (w - mc.textRenderer.getWidth(label)) / 2, y + 3, fg, false);
            float sw = mc.textRenderer.getWidth(sub) * 0.7f;
            RenderUtil.text(c, mc.textRenderer, sub, x + (w - sw) / 2f, y + 13, 0.7f, fg, false);
        }
    }
}
