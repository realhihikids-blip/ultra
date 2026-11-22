package com.pvpclient.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public final class RenderUtil {
    private RenderUtil() {}

    public static void roundedRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { c.fill(x, y, x + w, y + h, color); return; }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Rounded rectangle with a 1px border. */
    public static void panel(DrawContext c, int x, int y, int w, int h, int r, int bg, int border) {
        roundedRect(c, x - 1, y - 1, w + 2, h + 2, r + 1, border);
        roundedRect(c, x, y, w, h, r, bg);
    }

    public static void hGradient(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i += 2) {
            int sw = Math.min(2, w - i);
            c.fill(x + i, y, x + i + sw, y + h, lerpColor(c1, c2, w <= 1 ? 0 : (double) i / (w - 1)));
        }
    }

    public static int lerpColor(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int al = (int) (((a >>> 24) & 255) + ((((b >>> 24) & 255) - ((a >>> 24) & 255)) * t));
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Minecraft's text renderer treats alpha 1..3 as opaque, so alpha never drops below 4. */
    public static int withAlpha(int rgb, double alpha) {
        int al = Math.max(4, (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int mulAlpha(int argb, double m) {
        int al = (int) Math.round(((argb >>> 24) & 255) * Math.max(0, Math.min(1, m)));
        return (Math.max(4, al) << 24) | (argb & 0xFFFFFF);
    }

    public static void text(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int color, boolean shadow) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(scale, scale, 1f);
        c.drawText(tr, s, 0, 0, color, shadow);
        m.pop();
    }

    public static void text(DrawContext c, TextRenderer tr, Text t, float x, float y, float scale, int color, boolean shadow) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(scale, scale, 1f);
        c.drawText(tr, t, 0, 0, color, shadow);
        m.pop();
    }
}
