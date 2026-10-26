package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

/** Компактный неоновый HUD: координаты, FPS и скорость перемещения. */
public final class NeonHud extends Module {
    private final Setting.Bool coords = add(new Setting.Bool("Координаты", true));
    private final Setting.Bool fps = add(new Setting.Bool("FPS", true));
    private final Setting.Bool speed = add(new Setting.Bool("Скорость", true));

    public NeonHud() {
        super("NeonHud", "Неоновая панель FPS, координат и скорости", Category.HUD);
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        int x = 8;
        int y = 8;
        int line = 12;
        int rows = (coords.get() ? 1 : 0) + (fps.get() ? 1 : 0) + (speed.get() ? 1 : 0);
        if (rows == 0) return;

        int width = 142;
        int height = 25 + rows * line;
        RenderUtils.drawRoundedRect(c, x, y, x + width, y + height, 6, 0xB80A0B18);
        RenderUtils.drawRoundedRect(c, x, y, x + 3, y + height, 2, 0xFF8A5CFF);
        c.drawText(mc.textRenderer, Text.literal("VISUAL  /  STATUS"), x + 10, y + 6, 0xFFB9A3FF, false);

        int yy = y + 20;
        if (fps.get()) {
            c.drawText(mc.textRenderer, Text.literal("FPS"), x + 10, yy, 0xFF858AA8, false);
            c.drawText(mc.textRenderer, Text.literal(Integer.toString(mc.getCurrentFps())), x + 88, yy, 0xFFFFFFFF, false);
            yy += line;
        }
        if (coords.get()) {
            String pos = String.format(java.util.Locale.ROOT, "%d %d %d",
                    (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
            c.drawText(mc.textRenderer, Text.literal("XYZ"), x + 10, yy, 0xFF858AA8, false);
            c.drawText(mc.textRenderer, Text.literal(pos), x + 38, yy, 0xFFFFFFFF, false);
            yy += line;
        }
        if (speed.get()) {
            double dx = mc.player.getX() - mc.player.prevX;
            double dz = mc.player.getZ() - mc.player.prevZ;
            double blocksPerSecond = Math.sqrt(dx * dx + dz * dz) * 20.0;
            c.drawText(mc.textRenderer, Text.literal("SPEED"), x + 10, yy, 0xFF858AA8, false);
            c.drawText(mc.textRenderer, Text.literal(String.format(java.util.Locale.ROOT, "%.1f m/s", blocksPerSecond)),
                    x + 55, yy, 0xFFFFFFFF, false);
        }
    }
}
