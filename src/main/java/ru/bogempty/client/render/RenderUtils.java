package ru.bogempty.client.render;

import net.minecraft.client.gui.DrawContext;

public class RenderUtils {

    public static void drawRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void drawRoundedRect(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color) {
        context.fill(x1 + radius, y1, x2 - radius, y2, color);
        context.fill(x1, y1 + radius, x2, y2 - radius, color);

        // Углы
        context.fill(x1, y1, x1 + radius, y1 + radius, color);
        context.fill(x2 - radius, y1, x2, y1 + radius, color);
        context.fill(x1, y2 - radius, x1 + radius, y2, color);
        context.fill(x2 - radius, y2 - radius, x2, y2, color);
    }
}