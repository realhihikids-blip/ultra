package com.pvpclient.gui;

import com.pvpclient.util.Lang;
import com.pvpclient.util.RenderUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Small text field for the menu's search. Hit-testing happens in menu-local coordinates. */
final class SearchBar {
    private static final int MAX_LENGTH = 24;

    private final StringBuilder text = new StringBuilder();
    private boolean focused;
    private int x, y, w, h;

    String query() { return text.toString().trim().toLowerCase(Locale.ROOT); }
    boolean active() { return !query().isEmpty(); }
    boolean focused() { return focused; }

    void clear() {
        text.setLength(0);
        focused = false;
    }

    void render(DrawContext c, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int border = focused ? Theme.a1() : Theme.TRACK;
        RenderUtil.roundedRect(c, x - 1, y - 1, w + 2, h + 2, 5, Theme.f(border));
        RenderUtil.roundedRect(c, x, y, w, h, 4, Theme.f(hov || focused ? 0xFF1E1E31 : 0xFF171726));

        String shown = text.toString();
        boolean caret = focused && (System.currentTimeMillis() / 500) % 2 == 0;
        if (shown.isEmpty() && !focused) {
            c.drawText(tr, Lang.tr("Search..."), x + 6, y + (h - 8) / 2, Theme.f(Theme.DIM), false);
        } else {
            c.drawText(tr, shown, x + 6, y + (h - 8) / 2, Theme.f(Theme.TEXT), false);
            if (caret) c.fill(x + 6 + tr.getWidth(shown), y + 3, x + 7 + tr.getWidth(shown), y + h - 3, Theme.f(Theme.a1()));
        }
    }

    /** Focuses when clicked, unfocuses otherwise. @return true if the click hit the field. */
    boolean click(int mx, int my) {
        boolean hit = mx >= x && mx < x + w && my >= y && my < y + h;
        focused = hit;
        return hit;
    }

    boolean charTyped(char ch) {
        if (!focused || ch < 32 || ch == 127) return false;
        if (text.length() < MAX_LENGTH) text.append(ch);
        return true;
    }

    /** @return true if the key was consumed (everything is swallowed while the field is focused). */
    boolean key(int keyCode) {
        if (!focused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (text.length() > 0) text.setLength(text.length() - 1);
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (text.length() > 0) text.setLength(0); else focused = false;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            focused = false;
        }
        return true;
    }
}
