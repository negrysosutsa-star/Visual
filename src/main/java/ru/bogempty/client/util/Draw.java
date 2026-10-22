package ru.bogempty.client.util;

import net.minecraft.client.gui.DrawContext;

/** Линии, многоугольники и стрелки из прямоугольников. */
public final class Draw {

    public static void line(DrawContext c, int x0, int y0, int x1, int y1, int th, int col) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        for (int i = 0; i < 4000; i++) {
            c.fill(x, y, x + th, y + th, col);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    /** Закрашивает выпуклый многоугольник горизонтальными полосками. */
    public static void fillPoly(DrawContext c, float[] xs, float[] ys, int col) {
        int n = xs.length;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float y : ys) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        for (int y = (int) Math.floor(minY); y < (int) Math.ceil(maxY); y++) {
            float yc = y + 0.5f;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float yi = ys[i], yj = ys[j];
                if ((yc >= yi && yc <= yj) || (yc >= yj && yc <= yi)) {
                    if (Math.abs(yj - yi) < 1e-6f) continue;
                    float x = xs[i] + (yc - yi) * (xs[j] - xs[i]) / (yj - yi);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            if (hi > lo) {
                int a = Math.round(lo), b = Math.round(hi);
                if (b <= a) b = a + 1;
                c.fill(a, y, b, y + 1, col);
            }
        }
    }

    /** Стрелка-шеврон. deg: 0 = вверх, по часовой. size — длина от центра до острия. */
    public static void arrow(DrawContext c, float cx, float cy, float deg, float size, int col) {
        double r = Math.toRadians(deg);
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
        float[][] p = {
                {0f, -1.0f},    // остриё
                {0.72f, 0.78f}, // правое крыло
                {0f, 0.34f},    // выемка
                {-0.72f, 0.78f} // левое крыло
        };
        float[] x = new float[4], y = new float[4];
        for (int i = 0; i < 4; i++) {
            float px = p[i][0] * size, py = p[i][1] * size;
            x[i] = cx + px * cs - py * sn;
            y[i] = cy + px * sn + py * cs;
        }
        fillPoly(c, new float[]{x[0], x[1], x[2]}, new float[]{y[0], y[1], y[2]}, col);
        fillPoly(c, new float[]{x[0], x[2], x[3]}, new float[]{y[0], y[2], y[3]}, col);
    }

    public static void circle(DrawContext c, float cx, float cy, float r, int th, int col) {
        int steps = Math.max(24, (int) (r * 3));
        for (int i = 0; i < steps; i++) {
            double a = i * (Math.PI * 2.0 / steps);
            int x = Math.round(cx + (float) Math.cos(a) * r);
            int y = Math.round(cy + (float) Math.sin(a) * r);
            c.fill(x, y, x + th, y + th, col);
        }
    }

    private Draw() {}
}