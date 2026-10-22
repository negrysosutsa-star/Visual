package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.bogempty.client.gps.GpsManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.Draw;

import java.util.Locale;

public class GpsHud extends Module {

    private final Setting.Bool arrow = add(new Setting.Bool("Стрелка", true), "Стрелка направления на точку.");
    private final Setting.Bool panel = add(new Setting.Bool("Панель", true), "Имя точки и расстояние.");
    private final Setting.Bool coords = add(new Setting.Bool("Координаты", true), "Координаты точки и разница по высоте.");
    private final Setting.Num scale = add(new Setting.Num("Масштаб", 1.0, 0.7, 1.6, 0.05), "Размер блока GPS.");

    private float shown = 0f;
    private long lastNanos = 0L;

    private static final int ACCENT = 0xFF7C6CFF;
    private static final int ACCENT2 = 0xFF6CD0FF;

    public GpsHud() {
        super("GpsHud", "Стрелка и дистанция до GPS-точки", Category.HUD);
        enabled = true;
    }

    @Override
    public void onRender2D(DrawContext c, float tickDelta) {
        ClientPlayerEntity p = mc.player;
        if (!GpsManager.active || p == null) {
            lastNanos = 0L;
            return;
        }

        long nowN = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (nowN - lastNanos) / 1.0e9f);
        lastNanos = nowN;

        double dist = GpsManager.distanceTo(p.getPos());
        float target = GpsManager.arrowAngle(p.getYaw(tickDelta), p.getPos());
        // плавный поворот по кратчайшему пути
        shown += MathHelper.wrapDegrees(target - shown) * Math.min(1f, dt * 14f);

        float sc = (float) scale.get();
        int sw = c.getScaledWindowWidth();

        String name = GpsManager.name == null ? "Точка" : GpsManager.name;
        String distStr = GpsManager.formatDist(dist);
        double dy = GpsManager.y - p.getY();
        String hStr = Math.abs(dy) < 2 ? "на уровне" : (dy > 0 ? "выше на " : "ниже на ") + (int) Math.abs(dy);
        String cStr = (int) GpsManager.x + "  " + (int) GpsManager.y + "  " + (int) GpsManager.z;

        net.minecraft.client.util.math.MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(sw / 2f, 8, 0);
        ms.scale(sc, sc, 1f);

        int badge = arrow.get() ? 46 : 0;
        int textW = Math.max(Math.max(Fonts.width(name), Fonts.width(distStr) * 2), coords.get() ? Math.max(Fonts.width(cStr), Fonts.width(hStr)) : 0);
        int w = (panel.get() ? textW + 24 : 0) + badge + (arrow.get() && panel.get() ? 6 : 0);
        w = Math.max(w, badge + 4);
        int h = panel.get() ? (coords.get() ? 60 : 40) : 46;
        int x0 = -w / 2;

        RenderUtils.drawRoundedRect(c, x0 - 1, -1, x0 + w + 1, h + 1, 10, 0x30FFFFFF);
        RenderUtils.drawRoundedRect(c, x0, 0, x0 + w, h, 9, 0xD00B0B14);

        int tx = x0 + 8;
        if (arrow.get()) {
            int bx = x0 + 6 + 17;
            int by = h / 2;
            // свечение и круг
            RenderUtils.drawRoundedRect(c, bx - 21, by - 21, bx + 21, by + 21, 21, 0x187C6CFF);
            RenderUtils.drawRoundedRect(c, bx - 18, by - 18, bx + 18, by + 18, 18, 0xFF12121E);
            Draw.circle(c, bx, by, 18, 1, 0x557C6CFF);
            // тень стрелки и сама стрелка
            Draw.arrow(c, bx + 1, by + 2, shown, 12.5f, 0x66000000);
            int col = dist < 8 ? 0xFF4CE08A : ACCENT;
            Draw.arrow(c, bx, by, shown, 12f, col);
            tx = x0 + badge + 8;
        }

        if (panel.get()) {
            Fonts.draw(c, name, tx, 6, ACCENT);
            net.minecraft.client.util.math.MatrixStack ms2 = c.getMatrices();
            ms2.push();
            ms2.translate(tx, 17, 0);
            ms2.scale(1.6f, 1.6f, 1f);
            Fonts.draw(c, distStr, 0, 0, 0xFFFFFFFF);
            ms2.pop();
            if (coords.get()) {
                Fonts.draw(c, cStr, tx, 37, 0xFF8A8AA6);
                int hc = Math.abs(dy) < 2 ? 0xFF8A8AA6 : (dy > 0 ? ACCENT2 : 0xFFE0A050);
                Fonts.draw(c, hStr, tx, 48, hc);
            }
        }
        ms.pop();
    }

    @SuppressWarnings("unused")
    private static String f(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }
}