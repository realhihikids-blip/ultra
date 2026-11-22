package com.pvpclient.gui;

import com.pvpclient.util.Lang;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.*;
import com.pvpclient.util.PrivateWorldGuard;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.Smooth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Collapsible card for one module: toggle switch in the header, settings and keybind in the body. */
final class ModuleCard implements GuiEntry {
    private static final int HEAD = 30;
    private static final int BIND_ROW = 22;

    private final ClickGuiScreen screen;
    private final ClientModule module;
    private final TextRenderer tr = MinecraftClient.getInstance().textRenderer;
    private final Smooth expand = new Smooth(0);
    private final Smooth hover = new Smooth(0);
    private final Smooth on;
    private final Map<Setting<?>, Smooth> anims = new IdentityHashMap<>();

    private boolean open;
    private NumberSetting dragging;
    private int dragX, dragW;

    ModuleCard(ClickGuiScreen screen, ClientModule module) {
        this.screen = screen;
        this.module = module;
        this.on = new Smooth(module.isEnabled() ? 1 : 0);
    }

    // ---------------------------------------------------------------- layout

    private static int rowHeight(Setting<?> s) {
        if (s instanceof NumberSetting) return 28;
        if (s instanceof ColorSetting) return 24;
        if (s instanceof ModeSetting) return 22;
        return 20;
    }

    private int bodyHeight() {
        int h = 8;
        for (Setting<?> s : module.getSettings()) h += rowHeight(s);
        if (!module.isAlwaysOn()) h += BIND_ROW;
        return h;
    }

    @Override
    public int height() {
        return HEAD + (int) Math.round(expand.get() * bodyHeight());
    }

    private Smooth anim(Setting<?> s, double init) {
        return anims.computeIfAbsent(s, k -> new Smooth(init));
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int x, int y, int w, int mx, int my) {
        boolean hov = hit(mx, my, x, y, w, HEAD);
        hover.to(hov ? 1 : 0);
        double hv = hover.update();
        double ex = expand.update();
        on.to(module.isEnabled() ? 1 : 0);
        double o = on.update();
        int h = height();

        RenderUtil.roundedRect(c, x, y, w, h, 6, Theme.f(RenderUtil.lerpColor(Theme.CARD, Theme.CARD_HOVER, hv)));
        if (o > 0.01) {
            c.fill(x, y + 7, x + 2, y + HEAD - 7, Theme.f(RenderUtil.withAlpha(Theme.a1(), o)));
        }

        int nameColor = RenderUtil.lerpColor(0xFFB4B4CC, Theme.TEXT, Math.max(o, hv * 0.6));
        c.drawText(tr, Lang.tr(module.getName()), x + 12, y + 6, Theme.f(nameColor), false);
        RenderUtil.text(c, tr, Lang.tr(module.getDescription()), x + 12, y + 18, 0.72f, Theme.f(Theme.DIM), false);

        int swX = x + w - 44;
        if (!module.isAlwaysOn()) {
            drawSwitch(c, swX, y + 9, o);
            if (module.isRestricted() && !PrivateWorldGuard.isAuthorized(MinecraftClient.getInstance())) {
                String lock = Lang.tr("PRIVATE WORLDS ONLY");
                float lw = tr.getWidth(lock) * 0.65f;
                RenderUtil.text(c, tr, lock, swX - lw - 8, y + 12, 0.65f, Theme.f(Theme.RED), false);
            }
        }

        // rotating chevron
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w - 10, y + HEAD / 2f, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (ex * 90.0)));
        c.drawText(tr, ">", -2, -4, Theme.f(Theme.DIM), false);
        m.pop();

        if (ex > 0.02) {
            double saved = Theme.fade;
            Theme.fade = saved * ex;
            screen.scissor(c, x, y + HEAD, x + w, y + h);
            renderBody(c, x, y, w, mx, my);
            c.disableScissor();
            Theme.fade = saved;
        }
    }

    private void drawSwitch(DrawContext c, int x, int y, double t) {
        RenderUtil.roundedRect(c, x, y, 26, 12, 6, Theme.f(RenderUtil.lerpColor(Theme.TRACK, Theme.a1(), t)));
        RenderUtil.roundedRect(c, x + 2 + (int) Math.round(t * 14), y + 2, 8, 8, 4, Theme.f(0xFFFFFFFF));
    }

    private void renderBody(DrawContext c, int x, int y, int w, int mx, int my) {
        int rx = x + 10, rw = w - 20, yy = y + HEAD + 4;
        for (Setting<?> s : module.getSettings()) {
            renderRow(c, s, rx, yy, rw, mx, my);
            yy += rowHeight(s);
        }
        if (!module.isAlwaysOn()) renderBind(c, rx, yy, rw, mx, my);
    }

    private void renderRow(DrawContext c, Setting<?> s, int rx, int ry, int rw, int mx, int my) {
        int label = Theme.f(Theme.DIM);
        if (s instanceof BooleanSetting b) {
            c.drawText(tr, Lang.tr(b.getName()), rx, ry + 6, label, false);
            Smooth a = anim(b, b.get() ? 1 : 0);
            a.to(b.get() ? 1 : 0);
            drawSwitch(c, rx + rw - 26, ry + 4, a.update());
        } else if (s instanceof NumberSetting n) {
            c.drawText(tr, Lang.tr(n.getName()), rx, ry + 3, label, false);
            String v = format(n);
            c.drawText(tr, v, rx + rw - tr.getWidth(v), ry + 3, Theme.f(Theme.TEXT), false);
            double ratio = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
            Smooth a = anim(n, ratio);
            a.to(ratio);
            double t = a.update();
            int fw = (int) Math.round(rw * t);
            RenderUtil.roundedRect(c, rx, ry + 17, rw, 4, 2, Theme.f(Theme.TRACK));
            if (fw > 1) RenderUtil.hGradient(c, rx + 1, ry + 17, Math.max(fw - 1, 1), 4, Theme.f(Theme.a1()), Theme.f(Theme.a2()));
            RenderUtil.roundedRect(c, rx + fw - 3, ry + 15, 6, 8, 3, Theme.f(0xFFFFFFFF));
        } else if (s instanceof ColorSetting col) {
            c.drawText(tr, Lang.tr(col.getName()), rx, ry + 8, label, false);
            int[] pal = col.getPalette();
            int sx = rx + rw - pal.length * 13 + 3;
            for (int i = 0; i < pal.length; i++) {
                int px = sx + i * 13;
                if (pal[i] == col.get()) RenderUtil.roundedRect(c, px - 2, ry + 5, 14, 14, 4, Theme.f(0xFFFFFFFF));
                RenderUtil.roundedRect(c, px, ry + 7, 10, 10, 3, Theme.f(0xFF000000 | pal[i]));
            }
        } else if (s instanceof ModeSetting mode) {
            c.drawText(tr, Lang.tr(mode.getName()), rx, ry + 7, label, false);
            int bw = 112;
            int bx = rx + rw - bw;
            boolean hov = hit(mx, my, bx, ry + 3, bw, 16);
            RenderUtil.roundedRect(c, bx, ry + 3, bw, 16, 4, Theme.f(hov ? 0xFF2A2A44 : 0xFF222238));
            String t = Lang.tr(mode.getMode());
            c.drawText(tr, t, bx + (bw - tr.getWidth(t)) / 2, ry + 7, Theme.f(Theme.TEXT), false);
        }
    }

    private void renderBind(DrawContext c, int rx, int ry, int rw, int mx, int my) {
        c.drawText(tr, Lang.tr("Keybind"), rx, ry + 7, Theme.f(Theme.DIM), false);
        int bw = 84, bx = rx + rw - bw;
        boolean listening = screen.bindTarget() == module;
        boolean hov = hit(mx, my, bx, ry + 3, bw, 16);
        int bg = listening ? RenderUtil.withAlpha(Theme.a1() & 0xFFFFFF, 0.35 * Theme.fade) : Theme.f(hov ? 0xFF2A2A44 : 0xFF222238);
        RenderUtil.roundedRect(c, bx, ry + 3, bw, 16, 4, bg);
        String t = listening ? Lang.tr("Press a key...") : keyName(module.getKeyCode());
        c.drawText(tr, t, bx + (bw - tr.getWidth(t)) / 2, ry + 7, Theme.f(Theme.TEXT), false);
    }

    static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return Lang.tr("None");
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    private static String format(NumberSetting n) {
        return n.getStep() >= 1 ? Integer.toString((int) Math.round(n.get())) : String.format(Locale.ROOT, "%.2f", n.get());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean click(int x, int y, int w, int mx, int my, int button) {
        if (!hit(mx, my, x, y, w, height())) return false;

        if (my < y + HEAD) {
            if (!module.isAlwaysOn() && button == 0 && mx >= x + w - 48 && mx < x + w - 14) {
                module.toggle();
            } else {
                open = !open;
                expand.to(open ? 1 : 0);
            }
            return true;
        }
        if (!open || expand.get() < 0.6) return true;

        int rx = x + 10, rw = w - 20, yy = y + HEAD + 4;
        for (Setting<?> s : module.getSettings()) {
            int rh = rowHeight(s);
            if (my >= yy && my < yy + rh) {
                clickRow(s, rx, rw, mx, button);
                return true;
            }
            yy += rh;
        }
        if (!module.isAlwaysOn() && my >= yy && my < yy + BIND_ROW) {
            if (button == 1) {
                module.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                screen.setBindTarget(null);
            } else {
                screen.setBindTarget(module);
            }
        }
        return true;
    }

    private void clickRow(Setting<?> s, int rx, int rw, int mx, int button) {
        if (s instanceof BooleanSetting b) {
            if (button == 0) b.toggle();
        } else if (s instanceof NumberSetting n) {
            if (button == 0) {
                dragging = n;
                dragX = rx;
                dragW = rw;
                drag(mx);
            }
        } else if (s instanceof ColorSetting col) {
            int[] pal = col.getPalette();
            int sx = rx + rw - pal.length * 13 + 3;
            for (int i = 0; i < pal.length; i++) {
                if (mx >= sx + i * 13 - 1 && mx < sx + i * 13 + 11) { col.set(pal[i]); break; }
            }
        } else if (s instanceof ModeSetting mode) {
            mode.cycle(button == 1 ? -1 : 1);
        }
    }

    @Override
    public void drag(int mx) {
        if (dragging == null) return;
        double t = Math.max(0, Math.min(1, (mx - dragX) / (double) dragW));
        dragging.set(dragging.getMin() + t * (dragging.getMax() - dragging.getMin()));
    }

    @Override
    public void release() { dragging = null; }
}
