package ru.bogempty.client.module.hud;

import ru.bogempty.client.gps.GpsManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class GpsHud extends Module {

    private final Setting.Bool arrow = add(new Setting.Bool("Стрелка", true), "Стрелка направления.");
    private final Setting.Bool panel = add(new Setting.Bool("Панель", true), "Имя + метраж.");

    public GpsHud() {
        super("GpsHud", "Стрелка и дистанция до GPS-точки", Category.HUD);
        enabled = true;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        if (!GpsManager.active || mc.player == null) return;

        double dist = GpsManager.distanceTo(mc.player.getPos());
        float ang = GpsManager.arrowAngle(mc.player.getYaw(), mc.player.getPos());

        int sw = mc.getWindow().getScaledWidth();
        int cx = sw / 2;
        int cy = 28;

        if (panel.get()) {
            String line = "§d" + GpsManager.name + " §f" + GpsManager.formatDist(dist);
            int tw = Fonts.width(line);
            RenderUtils.drawRoundedRect(c, cx - tw / 2 - 8, cy - 4, cx + tw / 2 + 8, cy + 14, 4, 0xAA0C0C14);
            Fonts.draw(c, line, cx - tw / 2, cy, 0xFFFFFFFF);
        }

        if (arrow.get()) {
            // стрелка под панелью, вращается к точке
            int ax = cx;
            int ay = cy + 28;
            drawArrow(c, ax, ay, ang, 0xFF7C6CFF);
        }
    }

    private void drawArrow(DrawContext c, int cx, int cy, float deg, int col) {
        double rad = Math.toRadians(deg);
        // наконечник
        double len = 14;
        int x1 = cx + (int) (Math.sin(rad) * len);
        int y1 = cy - (int) (Math.cos(rad) * len);
        // боковые
        double radL = rad + Math.toRadians(140);
        double radR = rad - Math.toRadians(140);
        int x2 = cx + (int) (Math.sin(radL) * 8);
        int y2 = cy - (int) (Math.cos(radL) * 8);
        int x3 = cx + (int) (Math.sin(radR) * 8);
        int y3 = cy - (int) (Math.cos(radR) * 8);

        // простые линии через fill 2px
        line(c, cx, cy, x1, y1, col);
        line(c, x1, y1, x2, y2, col);
        line(c, x1, y1, x3, y3, col);
    }

    private void line(DrawContext c, int x0, int y0, int x1, int y1, int col) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;
        while (true) {
            c.fill(x, y, x + 2, y + 2, col);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }
}