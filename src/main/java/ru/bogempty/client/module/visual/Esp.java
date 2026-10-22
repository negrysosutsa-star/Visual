package ru.bogempty.client.module.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.Projector;

/** 2D-рамки вокруг игроков и мобов, проекция по настоящей камере. */
public class Esp extends Module {
    private final Setting.Mode style = add(new Setting.Mode("Стиль", "Уголки", "Рамка"), "Вид рамки.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"), "Кого подсвечивать.");
    private final Setting.Bool health = add(new Setting.Bool("Здоровье", true), "Полоска здоровья слева от рамки.");
    private final Setting.Bool names = add(new Setting.Bool("Имя", true), "Ник и расстояние над рамкой.");
    private final Setting.Bool glow = add(new Setting.Bool("Заливка", true), "Лёгкая цветная заливка внутри.");
    private final Setting.Bool visibleOnly = add(new Setting.Bool("Только видимые", false), "Не показывать тех, кто за стеной.");
    private final Setting.Num range = add(new Setting.Num("Дальность", 64, 16, 128, 4), "Макс. расстояние.");

    private final Projector proj = new Projector();

    public Esp() {
        super("ESP", "Рамки вокруг игроков и мобов", Category.VISUAL);
    }

    @Override
    public void onRender2D(DrawContext c, float td) {
        if (!proj.begin(mc, c, td)) return;
        double maxD = range.get();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive() || le instanceof ArmorStandEntity) continue;
            boolean isPlayer = le instanceof PlayerEntity;
            if (!isPlayer && targets.is("Игроки")) continue;
            double dist = mc.player.distanceTo(le);
            if (dist > maxD) continue;
            if (visibleOnly.get() && !mc.player.canSee(le)) continue;

            Vec3d lp = le.getLerpedPos(td);
            Box b = le.getBoundingBox().offset(lp.subtract(le.getPos()));
            double[] xs = {b.minX, b.maxX};
            double[] ys = {b.minY, b.maxY};
            double[] zs = {b.minZ, b.maxZ};

            float x1 = Float.MAX_VALUE, y1 = Float.MAX_VALUE, x2 = -Float.MAX_VALUE, y2 = -Float.MAX_VALUE;
            boolean ok = true;
            for (double x : xs) {
                for (double y : ys) {
                    for (double z : zs) {
                        float[] s = proj.project(x, y, z);
                        if (s == null) { ok = false; break; }
                        x1 = Math.min(x1, s[0]); x2 = Math.max(x2, s[0]);
                        y1 = Math.min(y1, s[1]); y2 = Math.max(y2, s[1]);
                    }
                    if (!ok) break;
                }
                if (!ok) break;
            }
            if (!ok) continue;
            if (x2 < -40 || x1 > proj.sw + 40 || y2 < -40 || y1 > proj.sh + 40) continue;
            if (x2 - x1 < 3 || y2 - y1 < 5) continue;

            // чуть уменьшаем коробку, 3D-бокс шире тела на экране
            float padX = (x2 - x1) * 0.08f;
            x1 += padX; x2 -= padX;

            boolean friend = isPlayer && FriendManager.isFriend(le.getName().getString());
            int col = friend ? 0xFF4CE08A : (isPlayer ? 0xFF7C6CFF : 0xFFE0A050);

            int ix1 = Math.round(x1), iy1 = Math.round(y1), ix2 = Math.round(x2), iy2 = Math.round(y2);
            if (glow.get()) c.fill(ix1, iy1, ix2, iy2, (col & 0xFFFFFF) | 0x1C000000);

            if (style.is("Рамка")) {
                box(c, ix1 - 1, iy1 - 1, ix2 + 1, iy2 + 1, 0xAA000000);
                box(c, ix1, iy1, ix2, iy2, col);
            } else {
                corners(c, ix1 - 1, iy1 - 1, ix2 + 1, iy2 + 1, 0xAA000000, 3);
                corners(c, ix1, iy1, ix2, iy2, col, 1);
            }

            if (health.get()) {
                float ratio = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
                int hcol = RenderUtils.lerp(0xFFE5484D, 0xFF4CE08A, ratio);
                int bx = ix1 - 5;
                c.fill(bx - 1, iy1 - 1, bx + 2, iy2 + 1, 0xAA000000);
                int top = iy2 - Math.round((iy2 - iy1) * ratio);
                c.fill(bx, top, bx + 1, iy2, hcol);
            }

            if (names.get()) {
                String label = le.getName().getString() + "  " + (int) dist + "м";
                int tw = Fonts.width(label);
                int tx = (ix1 + ix2) / 2 - tw / 2;
                int ty = iy1 - 13;
                RenderUtils.drawRoundedRect(c, tx - 4, ty - 2, tx + tw + 4, ty + 10, 3, 0xAA0A0A12);
                Fonts.draw(c, label, tx, ty, friend ? 0xFF4CE08A : 0xFFFFFFFF);
            }
        }
    }

    private static void box(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1, y1, x2, y1 + 1, col);
        c.fill(x1, y2 - 1, x2, y2, col);
        c.fill(x1, y1, x1 + 1, y2, col);
        c.fill(x2 - 1, y1, x2, y2, col);
    }

    private static void corners(DrawContext c, int x1, int y1, int x2, int y2, int col, int th) {
        int lw = Math.max(4, (x2 - x1) / 4);
        int lh = Math.max(4, (y2 - y1) / 4);
        c.fill(x1, y1, x1 + lw, y1 + th, col);
        c.fill(x1, y1, x1 + th, y1 + lh, col);
        c.fill(x2 - lw, y1, x2, y1 + th, col);
        c.fill(x2 - th, y1, x2, y1 + lh, col);
        c.fill(x1, y2 - th, x1 + lw, y2, col);
        c.fill(x1, y2 - lh, x1 + th, y2, col);
        c.fill(x2 - lw, y2 - th, x2, y2, col);
        c.fill(x2 - th, y2 - lh, x2, y2, col);
    }
}