package com.pvpclient.gui;

import com.pvpclient.util.Lang;
import com.pvpclient.config.ConfigManager;
import com.pvpclient.module.ClientModule;
import com.pvpclient.util.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Settings page card listing every module's keybind, plus a manual save button. */
final class KeybindsCard implements GuiEntry {
    private static final int HEAD = 26, ROW = 20, FOOT = 40;

    private final ClickGuiScreen screen;
    private final List<ClientModule> targets = new ArrayList<>();
    private final TextRenderer tr = MinecraftClient.getInstance().textRenderer;
    private long savedAt;

    KeybindsCard(ClickGuiScreen screen, List<ClientModule> all) {
        this.screen = screen;
        for (ClientModule m : all) if (!m.isAlwaysOn()) targets.add(m);
    }

    @Override
    public int height() { return HEAD + targets.size() * ROW + FOOT; }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(DrawContext c, int x, int y, int w, int mx, int my) {
        RenderUtil.roundedRect(c, x, y, w, height(), 6, Theme.f(Theme.CARD));
        c.drawText(tr, Lang.tr("Keybinds"), x + 12, y + 6, Theme.f(Theme.TEXT), false);
        RenderUtil.text(c, tr, Lang.tr("Click a button, then press a key. Backspace clears."), x + 12, y + 17, 0.72f, Theme.f(Theme.DIM), false);

        int yy = y + HEAD;
        for (ClientModule m : targets) {
            c.drawText(tr, Lang.tr(m.getName()), x + 12, yy + 6, Theme.f(0xFFB4B4CC), false);
            int bw = 84, bx = x + w - bw - 10;
            boolean listening = screen.bindTarget() == m;
            boolean hov = hit(mx, my, bx, yy + 2, bw, 16);
            int bg = listening ? RenderUtil.withAlpha(Theme.a1() & 0xFFFFFF, 0.35 * Theme.fade) : Theme.f(hov ? 0xFF2A2A44 : 0xFF222238);
            RenderUtil.roundedRect(c, bx, yy + 2, bw, 16, 4, bg);
            String t = listening ? Lang.tr("Press a key...") : ModuleCard.keyName(m.getKeyCode());
            c.drawText(tr, t, bx + (bw - tr.getWidth(t)) / 2, yy + 6, Theme.f(Theme.TEXT), false);
            yy += ROW;
        }

        RenderUtil.text(c, tr, Lang.tr("Menu key: = (change in Options > Controls)"), x + 12, yy + 5, 0.72f, Theme.f(Theme.DIM), false);

        boolean saved = System.currentTimeMillis() - savedAt < 1500;
        int bw = 96, bx = x + 10, by = yy + 18;
        boolean hov = hit(mx, my, bx, by, bw, 16);
        RenderUtil.roundedRect(c, bx, by, bw, 16, 4, Theme.f(hov ? 0xFF2A2A44 : 0xFF222238));
        String label = saved ? Lang.tr("Saved!") : Lang.tr("Save config");
        c.drawText(tr, label, bx + (bw - tr.getWidth(label)) / 2, by + 4, Theme.f(saved ? Theme.a2() : Theme.TEXT), false);
    }

    @Override
    public boolean click(int x, int y, int w, int mx, int my, int button) {
        if (!hit(mx, my, x, y, w, height())) return false;
        int yy = y + HEAD;
        for (ClientModule m : targets) {
            int bw = 84, bx = x + w - bw - 10;
            if (hit(mx, my, bx, yy + 2, bw, 16)) {
                if (button == 1) { m.setKeyCode(GLFW.GLFW_KEY_UNKNOWN); screen.setBindTarget(null); }
                else screen.setBindTarget(m);
                return true;
            }
            yy += ROW;
        }
        if (hit(mx, my, x + 10, yy + 18, 96, 16)) {
            ConfigManager.save(screen.manager());
            savedAt = System.currentTimeMillis();
        }
        return true;
    }
}
