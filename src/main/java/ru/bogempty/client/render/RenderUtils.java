package ru.bogempty.client.render;

import net.minecraft.client.gui.DrawContext;

public class RenderUtils {

    public static void drawRect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1, y1, x2, y2, color);
    }

    public static void drawRoundedRect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x1, y1, x2, y2, color);
            return;
        }
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Multiplies the alpha channel of color by a (0..1). */
    public static int alpha(int color, float a) {
        a = Math.max(0f, Math.min(1f, a));
        int al = (int) (((color >>> 24) & 0xFF) * a);
        return (al << 24) | (color & 0xFFFFFF);
    }

    /** Linear blend of two ARGB colors. */
    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int a = (c1 >>> shift) & 0xFF;
            int b = (c2 >>> shift) & 0xFF;
            out |= ((int) (a + (b - a) * t) & 0xFF) << shift;
        }
        return out;
    }
}