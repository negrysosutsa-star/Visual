package ru.bogempty.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class Fonts {
    private static final Identifier ID = Identifier.of("bogempty", "main");
    private static Style style;

    private static Style style() {
        if (style == null) {
            boolean custom = MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of("bogempty", "font/main.json")).isPresent();
            style = custom ? Style.EMPTY.withFont(ID) : Style.EMPTY;
        }
        return style;
    }

    public static Text t(String s) {
        return Text.literal(s).setStyle(style());
    }

    public static int width(String s) {
        return MinecraftClient.getInstance().textRenderer.getWidth(t(s));
    }

    public static void draw(DrawContext c, String s, int x, int y, int color) {
        if ((color >>> 24) < 8) return;
        c.drawText(MinecraftClient.getInstance().textRenderer, t(s), x, y, color, false);
    }

    public static void drawShadow(DrawContext c, String s, int x, int y, int color) {
        if ((color >>> 24) < 8) return;
        c.drawText(MinecraftClient.getInstance().textRenderer, t(s), x, y, color, true);
    }
}