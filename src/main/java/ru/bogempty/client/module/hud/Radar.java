package ru.bogempty.client.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import ru.bogempty.client.friend.FriendManager;
import ru.bogempty.client.module.Category;
import ru.bogempty.client.module.Module;
import ru.bogempty.client.render.Fonts;
import ru.bogempty.client.render.RenderUtils;
import ru.bogempty.client.setting.Setting;
import ru.bogempty.client.util.Draw;

/** Круглый радар: ты в центре, верх = куда смотришь. */
public class Radar extends Module {
    private final Setting.Mode pos = add(new Setting.Mode("Позиция", "Справа снизу", "Справа сверху", "Слева снизу"),
            "Где показывать радар.");
    private final Setting.Mode targets = add(new Setting.Mode("Цели", "Игроки", "Все"), "Кого показывать.");
    private final Setting.Num size = add(new Setting.Num("Размер", 64, 40, 110, 2), "Радиус радара.");
    private final Setting.Num range = add(new Setting.Num("Дальность", 48, 16, 128, 4), "Сколько блоков вокруг видно.");
    private final Setting.Bool cone = add(new Setting.Bool("Конус взгляда", true), "Показывать, куда ты смотришь.");

    public Radar() {
        super("Радар", "Круглый радар игроков вокруг", Category.HUD);
    }

    @Override
    public void onRender2D(DrawContext c, float td) {
        if (mc.player == null || mc.world == null) return;
        int r = (int) size.get();
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int cx, cy;
        if (pos.is("Справа сверху")) { cx = sw - r - 12; cy = r + 40; }
        else if (pos.is("Слева снизу")) { cx = r + 12; cy = sh - r - 40; }
        else { cx = sw - r - 12; cy = sh - r - 48; }

        RenderUtils.drawRoundedRect(c, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, r + 1, 0x30FFFFFF);
        RenderUtils.drawRoundedRect(c, cx - r, cy - r, cx + r, cy + r, r, 0xB00A0A12);
        Draw.circle(c, cx, cy, r * 0.5f, 1, 0x20FFFFFF);
        Draw.circle(c, cx, cy, r - 1, 1, 0x557C6CFF);
        c.fill(cx - r + 4, cy, cx + r - 4, cy + 1, 0x18FFFFFF);
        c.fill(cx, cy - r + 4, cx + 1, cy + r - 4, 0x18FFFFFF);

        double yaw = Math.toRadians(mc.player.getYaw(td));
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        double maxD = range.get();

        if (cone.get()) {
            double half = Math.toRadians(35);
            for (int s = -1; s <= 1; s += 2) {
                int ex = cx + (int) Math.round(Math.sin(half * s) * (r - 3));
                int ey = cy - (int) Math.round(Math.cos(half * s) * (r - 3));
                Draw.line(c, cx, cy, ex, ey, 1, 0x30FFFFFF);
            }
        }

        double px = mc.player.getLerpedPos(td).x;
        double pz = mc.player.getLerpedPos(td).z;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive() || le instanceof ArmorStandEntity) continue;
            boolean isPlayer = le instanceof PlayerEntity;
            if (!isPlayer && targets.is("Игроки")) continue;
            double dx = le.getLerpedPos(td).x - px;
            double dz = le.getLerpedPos(td).z - pz;
            double right = dx * -cs + dz * -sn;
            double fwd = dx * -sn + dz * cs;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > maxD * 1.5) continue;
            double k = Math.min(1.0, maxD / Math.max(d, 0.001));
            double sx = right * k / maxD * (r - 6);
            double sy = -fwd * k / maxD * (r - 6);
            if (d > maxD) { sx = right / d * (r - 6); sy = -fwd / d * (r - 6); }
            boolean friend = isPlayer && FriendManager.isFriend(le.getName().getString());
            int col = friend ? 0xFF4CE08A : (isPlayer ? 0xFFFF5A5F : 0xFFE0A050);
            int x = cx + (int) Math.round(sx), y = cy + (int) Math.round(sy);
            c.fill(x - 2, y - 2, x + 3, y + 3, 0xAA000000);
            c.fill(x - 1, y - 1, x + 2, y + 2, d > maxD ? (col & 0xFFFFFF) | 0x88000000 : col);
        }

        // ты
        Draw.arrow(c, cx, cy, 0f, 5f, 0xFFFFFFFF);
        String rr = (int) maxD + "м";
        Fonts.draw(c, rr, cx - Fonts.width(rr) / 2, cy + r - 12, 0xFF6E6E86);
    }
}